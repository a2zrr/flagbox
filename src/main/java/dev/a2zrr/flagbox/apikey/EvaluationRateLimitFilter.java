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
 * Limits evaluation requests to {@value #REQUESTS_PER_SECOND} per second for each
 * API key. Management endpoints are not limited. Over the limit the response is
 * 429 with a {@code Retry-After} header in seconds.
 *
 * <p>Buckets are kept in memory per instance. With several instances each one
 * enforces the limit separately; a shared store would be needed for an exact global limit.
 */
public class EvaluationRateLimitFilter extends OncePerRequestFilter {

    public static final int REQUESTS_PER_SECOND = 100;
    private static final String EVALUATE_PATH = "/api/v1/evaluate";

    private final Cache<Long, Bucket> buckets;
    private final ObjectMapper mapper;
    private final TimeMeter clock;

    public EvaluationRateLimitFilter(ObjectMapper mapper) {
        this(mapper, TimeMeter.SYSTEM_NANOTIME);
    }

    /** The clock is injectable so tests can move time forward deterministically. */
    EvaluationRateLimitFilter(ObjectMapper mapper, TimeMeter clock) {
        this.mapper = mapper;
        this.clock = clock;
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

        Bucket bucket = buckets.get(apiKey.getPrincipal().keyId(), id -> newBucket(clock));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        ProblemJson.write(response, mapper, HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
            "This API key is limited to " + REQUESTS_PER_SECOND + " evaluation requests per second");
    }

    static Bucket newBucket(TimeMeter clock) {
        return Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(REQUESTS_PER_SECOND)
                .refillGreedy(REQUESTS_PER_SECOND, Duration.ofSeconds(1))
                .build())
            .withCustomTimePrecision(clock)
            .build();
    }
}
