package dev.a2zrr.flagbox.support;

import io.github.bucket4j.TimeMeter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Freezes the rate limiter's clock at zero. Buckets then never refill during the
 * test, so the request count decides the outcome and no sleep is needed.
 */
@TestConfiguration
public class FrozenRateLimitClockConfig {

    @Bean
    @Primary
    TimeMeter frozenRateLimitClock() {
        return new TimeMeter() {
            @Override
            public long currentTimeNanos() {
                return 0L;
            }

            @Override
            public boolean isWallClockBased() {
                return false;
            }
        };
    }
}
