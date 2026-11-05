package dev.a2zrr.flagbox.evaluation;

/**
 * MurmurHash3 x86 32-bit. Used for rollout buckets because it is fast, well
 * distributed, and produces the same output on every platform and language, so
 * a user's bucket can be reproduced outside the server.
 */
final class Murmur3 {

    private static final int C1 = 0xcc9e2d51;
    private static final int C2 = 0x1b873593;

    private Murmur3() {
    }

    static int hash32(byte[] data, int seed) {
        int h1 = seed;
        int length = data.length;
        int blocks = length / 4;

        for (int i = 0; i < blocks; i++) {
            int offset = i * 4;
            int k1 = (data[offset] & 0xff)
                | (data[offset + 1] & 0xff) << 8
                | (data[offset + 2] & 0xff) << 16
                | (data[offset + 3] & 0xff) << 24;
            h1 ^= mixK1(k1);
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        int tail = blocks * 4;
        int k1 = 0;
        switch (length & 3) {
            case 3:
                k1 ^= (data[tail + 2] & 0xff) << 16;
                // fall through
            case 2:
                k1 ^= (data[tail + 1] & 0xff) << 8;
                // fall through
            case 1:
                k1 ^= data[tail] & 0xff;
                h1 ^= mixK1(k1);
                break;
            default:
                break;
        }

        h1 ^= length;
        return fmix32(h1);
    }

    private static int mixK1(int k1) {
        k1 *= C1;
        k1 = Integer.rotateLeft(k1, 15);
        return k1 * C2;
    }

    private static int fmix32(int h) {
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }
}
