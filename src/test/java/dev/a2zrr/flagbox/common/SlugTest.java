package dev.a2zrr.flagbox.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SlugTest {

    @ParameterizedTest
    @ValueSource(strings = {"flag", "new-checkout", "v2", "a1-b2-c3"})
    void acceptsValidSlugs(String value) {
        assertThat(Slug.isValid(value)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("boundaryLengths")
    void enforcesMaximumLength(String value, boolean expected) {
        assertThat(Slug.isValid(value)).isEqualTo(expected);
    }

    static Stream<Arguments> boundaryLengths() {
        return Stream.of(
            Arguments.of("x".repeat(64), true),
            Arguments.of("x".repeat(65), false));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Flag", "new_checkout", "-start", "end-", "double--dash", "has space"})
    void rejectsInvalidSlugs(String value) {
        assertThat(Slug.isValid(value)).isFalse();
    }

    @Test
    void requireValidNamesTheField() {
        assertThatThrownBy(() -> Slug.requireValid("Bad Key", "flag key"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageStartingWith("flag key must be a slug");
    }
}
