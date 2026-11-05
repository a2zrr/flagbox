package dev.a2zrr.flagbox.apikey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import dev.a2zrr.flagbox.common.security.ProblemJson;

/**
 * Limits evaluation requests per second for each API key (the limit is configured by
 * flagbox.rate-limit.evaluations-per-second, default 100). Management endpoints are not limited. Over the limit the response is
 * 429 with a {@code Retry-After} header in seconds.
 *
 * <p>Buckets are kept in memory per instance. With several instances each one
 * enforces the limit separately; a shared store would be needed for an exact global limit.
 */
public class EvaluationRateLimitFilter extends OncePerRequestFilter {

    public static final int DEFAULT_REQUESTS_PER_SECOND = 100;
    private static final String EVALUATE_PATH = "/api/v1/evaluate";

    private final Cache<Long, Bucket> buckets;
    private final ObjectMapper mapper;
    private final TimeMeter clock;
    private final int requestsPerSecond;

    /**
     * @param clock             the time source; injectable so tests can control refill without sleeping
     * @param requestsPerSecond the per-key limit (flagbox.rate-limit.evaluations-per-second)
     */
    public EvaluationRateLimitFilter(ObjectMapper mapper, TimeMeter clock, int requestsPerSecond) {
        if (requestsPerSecond < 1) {
            throw new IllegalArgumentException("requestsPerSecond must be at least 1");
        }
        this.mapper = mapper;
        this.clock = clock;
        this.requestsPerSecond = requestsPerSecond;
        this.buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(100_000)
            .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!request.getRequestURI().startsWith(EVALUATE_PATH)
                || !(authentication instanceof ApiKeyAuthentication apiKey)) {
            chain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.get(apiKey.getPrincipal().keyId(), id -> newBucket(clock, requestsPerSecond));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        ProblemJson.write(response, mapper, HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
            "This API key is limited to " + requestsPerSecond + " evaluation requests per second");
    }

    static Bucket newBucket(TimeMeter clock, int requestsPerSecond) {
        return Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(requestsPerSecond)
                .refillGreedy(requestsPerSecond, Duration.ofSeconds(1))
                .build())
            .withCustomTimePrecision(clock)
            .build();
    }
}
