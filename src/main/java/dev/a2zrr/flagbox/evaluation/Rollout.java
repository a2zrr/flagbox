package dev.a2zrr.flagbox.evaluation;

import java.nio.charset.StandardCharsets;

/**
 * Deterministic percentage rollout. A user's bucket is
 * {@code murmur3_32(flagKey + ":" + userId) mod 100}, so the same user always
 * lands in the same bucket for a given flag. Raising the percentage only adds
 * buckets: users inside a 20% rollout stay inside when it goes to 30%.
 */
public final class Rollout {

    public static final int BUCKETS = 100;

    private Rollout() {
    }

    /** Returns the bucket (0 to 99) for this user and flag. */
    public static int bucket(String flagKey, String userId) {
        byte[] input = (flagKey + ":" + userId).getBytes(StandardCharsets.UTF_8);
        // Take the modulo on the unsigned value; casting first would make large hashes negative.
        return (int) (Integer.toUnsignedLong(Murmur3.hash32(input, 0)) % BUCKETS);
    }

    /** True when the user falls inside a rollout of {@code percentage} percent. */
    public static boolean isIn(String flagKey, String userId, int percentage) {
        return bucket(flagKey, userId) < percentage;
    }
}
