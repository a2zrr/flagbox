package dev.a2zrr.flagbox.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RolloutTest {

    @Test
    void bucketsMatchTheReferenceImplementation() {
        // Values produced by an independent MurmurHash3 implementation over "flag:user".
        assertThat(Rollout.bucket("new-checkout", "user-1")).isEqualTo(31);
        assertThat(Rollout.bucket("new-checkout", "user-2")).isEqualTo(89);
        assertThat(Rollout.bucket("new-checkout", "user-3")).isEqualTo(6);
        assertThat(Rollout.bucket("new-checkout", "alice")).isEqualTo(40);
        assertThat(Rollout.bucket("new-checkout", "bob")).isEqualTo(88);
    }

    @Test
    void sameUserAndFlagAlwaysGetTheSameBucket() {
        int first = Rollout.bucket("new-checkout", "user-42");
        for (int i = 0; i < 1000; i++) {
            assertThat(Rollout.bucket("new-checkout", "user-42")).isEqualTo(first);
        }
    }

    @Test
    void bucketIsAlwaysBetweenZeroAndNinetyNine() {
        for (int i = 0; i < 10_000; i++) {
            assertThat(Rollout.bucket("flag", "user-" + i)).isBetween(0, 99);
        }
    }

    @Test
    void tenThousandUsersAtThirtyPercentLandNearThirtyPercent() {
        int inside = 0;
        for (int i = 0; i < 10_000; i++) {
            if (Rollout.isIn("new-checkout", "user-" + i, 30)) {
                inside++;
            }
        }
        double share = inside / 10_000.0;
        assertThat(share).isBetween(0.28, 0.32);
    }

    @Test
    void raisingTheRolloutKeepsEarlierUsersIn() {
        for (int i = 0; i < 10_000; i++) {
            String user = "user-" + i;
            if (Rollout.isIn("new-checkout", user, 20)) {
                assertThat(Rollout.isIn("new-checkout", user, 30)).as(user).isTrue();
            }
        }
    }

    @Test
    void zeroPercentNeverMatchesAndHundredPercentAlwaysMatches() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(Rollout.isIn("flag", "user-" + i, 0)).isFalse();
            assertThat(Rollout.isIn("flag", "user-" + i, 100)).isTrue();
        }
    }

    @Test
    void bucketDependsOnTheFlagKeyToo() {
        // Different flags split the same users differently, so one flag's rollout
        // does not select the same group for every feature.
        int differing = 0;
        for (int i = 0; i < 200; i++) {
            if (Rollout.bucket("flag-a", "user-" + i) != Rollout.bucket("flag-b", "user-" + i)) {
                differing++;
            }
        }
        assertThat(differing).isGreaterThan(150);
    }
}
