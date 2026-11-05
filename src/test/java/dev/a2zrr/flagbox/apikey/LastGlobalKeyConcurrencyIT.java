package dev.a2zrr.flagbox.apikey;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.a2zrr.flagbox.common.error.ConflictException;
import dev.a2zrr.flagbox.support.PostgresIntegrationTest;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two threads revoke the last two active global admin keys at the same moment,
 * against the real database. The pessimistic lock in revokeGlobalAdminKey must
 * let exactly one revocation through. Without the lock both would see two active
 * keys and both would succeed, leaving no way in.
 */
@SpringBootTest
class LastGlobalKeyConcurrencyIT extends PostgresIntegrationTest {

    private static final int ROUNDS = 5;

    @Autowired
    private ApiKeyService apiKeys;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void resetState() {
        jdbc.execute("DELETE FROM audit_logs; DELETE FROM projects");
        jdbc.update("DELETE FROM api_keys WHERE project_id IS NULL AND name <> 'bootstrap-admin'");
        jdbc.update("UPDATE api_keys SET revoked_at = NULL WHERE project_id IS NULL");
    }

    @Test
    void twoSimultaneousRevocationsOfTheLastTwoKeysLeaveExactlyOneActive() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            // Deactivate whatever is left from the previous round, then arrange exactly two active keys.
            jdbc.update("UPDATE api_keys SET revoked_at = now() WHERE project_id IS NULL AND revoked_at IS NULL");
            Long first = apiKeys.createGlobalAdminKey("round-" + round + "-a", "test").key().getId();
            Long second = apiKeys.createGlobalAdminKey("round-" + round + "-b", "test").key().getId();
            assertThat(activeGlobalKeys()).as("precondition, round %d", round).isEqualTo(2);

            List<Outcome> outcomes = raceToRevoke(first, second);

            assertThat(outcomes).as("round %d", round)
                .containsExactlyInAnyOrder(Outcome.REVOKED, Outcome.REFUSED);
            assertThat(activeGlobalKeys()).as("round %d", round).isEqualTo(1);
        }
    }

    private List<Outcome> raceToRevoke(Long first, Long second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Outcome> a = pool.submit(revokeWhenReleased(first, start));
            Future<Outcome> b = pool.submit(revokeWhenReleased(second, start));
            start.countDown();
            return List.of(a.get(30, SECONDS), b.get(30, SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Outcome> revokeWhenReleased(Long id, CountDownLatch start) {
        return () -> {
            start.await();
            try {
                apiKeys.revokeGlobalAdminKey(id, "race-test");
                return Outcome.REVOKED;
            } catch (ConflictException refused) {
                return Outcome.REFUSED;
            }
        };
    }

    private long activeGlobalKeys() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM api_keys WHERE project_id IS NULL AND type = 'ADMIN' AND revoked_at IS NULL",
            Long.class);
        return count;
    }

    private enum Outcome {
        REVOKED,
        REFUSED
    }
}
