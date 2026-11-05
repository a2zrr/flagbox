package dev.a2zrr.flagbox.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class EvaluationRateLimitFilterTest {

    /** A clock the test moves by hand, so the refill is exact. */
    static final class ManualClock implements TimeMeter {
        private long nanos;

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }

    private final ManualClock clock = new ManualClock();
    private final EvaluationRateLimitFilter filter = new EvaluationRateLimitFilter(new ObjectMapper(), clock);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsOneHundredEvaluationsPerSecondThenRejectsWithRetryAfter() throws Exception {
        for (int i = 0; i < EvaluationRateLimitFilter.REQUESTS_PER_SECOND; i++) {
            assertThat(evaluate(1L).getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse rejected = evaluate(1L);

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isNotNull();
        assertThat(Integer.parseInt(rejected.getHeader("Retry-After"))).isGreaterThanOrEqualTo(1);
        assertThat(rejected.getContentAsString()).contains("100 evaluation requests per second");
    }

    @Test
    void capacityReturnsAfterTheWindowPasses() throws Exception {
        for (int i = 0; i < EvaluationRateLimitFilter.REQUESTS_PER_SECOND; i++) {
            evaluate(1L);
        }
        assertThat(evaluate(1L).getStatus()).isEqualTo(429);

        clock.advance(Duration.ofSeconds(1));

        assertThat(evaluate(1L).getStatus()).isEqualTo(200);
    }

    @Test
    void eachKeyHasItsOwnBucket() throws Exception {
        for (int i = 0; i < EvaluationRateLimitFilter.REQUESTS_PER_SECOND; i++) {
            evaluate(1L);
        }
        assertThat(evaluate(1L).getStatus()).isEqualTo(429);

        assertThat(evaluate(2L).getStatus()).isEqualTo(200);
    }

    @Test
    void managementEndpointsAreNotLimited() throws Exception {
        authenticate(1L);
        for (int i = 0; i < EvaluationRateLimitFilter.REQUESTS_PER_SECOND * 2; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/projects"), response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void requestsWithoutAKeyPassThrough() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/evaluate"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse evaluate(long keyId) throws Exception {
        authenticate(keyId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/evaluate"), response, new MockFilterChain());
        return response;
    }

    private static void authenticate(long keyId) {
        SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthentication(
            new ApiKeyPrincipal(keyId, "client", ApiKeyType.CLIENT, 1L, "checkout", 2L, "production")));
    }
}
