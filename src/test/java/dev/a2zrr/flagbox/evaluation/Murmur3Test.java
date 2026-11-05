package dev.a2zrr.flagbox.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Murmur3Test {

    @Test
    void matchesPublishedReferenceVectors() {
        assertThat(Murmur3.hash32(new byte[0], 0)).isEqualTo(0x00000000);
        assertThat(Murmur3.hash32("hello".getBytes(StandardCharsets.UTF_8), 0)).isEqualTo(0x248bfa47);
        assertThat(Murmur3.hash32("The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8), 0))
            .isEqualTo(0x2e4ff723);
    }

    @Test
    void coversEveryTailLength() {
        // Inputs of length 1, 2 and 3 exercise the tail branches; these values were
        // produced by an independent reference implementation.
        assertThat(Murmur3.hash32("a".getBytes(StandardCharsets.UTF_8), 0)).isEqualTo(0x3c2569b2);
        assertThat(Murmur3.hash32("ab".getBytes(StandardCharsets.UTF_8), 0)).isEqualTo(0x9bbfd75f);
        assertThat(Murmur3.hash32("abc".getBytes(StandardCharsets.UTF_8), 0)).isEqualTo(0xb3dd93fa);
    }
}
