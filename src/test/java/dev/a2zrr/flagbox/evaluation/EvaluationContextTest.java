package dev.a2zrr.flagbox.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvaluationContextTest {

    @Test
    void attributesAreCappedAtFifty() {
        Map<String, String> fifty = new HashMap<>();
        for (int i = 0; i < EvaluationContext.MAX_ATTRIBUTES; i++) {
            fifty.put("attr" + i, "v");
        }
        assertThat(new EvaluationContext("user-1", fifty).attributes()).hasSize(50);

        fifty.put("one-more", "v");
        assertThatThrownBy(() -> new EvaluationContext("user-1", fifty))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at most 50 entries");
    }

    @Test
    void userIdIsCappedAtOneHundredAndTwentyEightCharacters() {
        assertThat(new EvaluationContext("u".repeat(128), null).userId()).hasSize(128);
        assertThatThrownBy(() -> new EvaluationContext("u".repeat(129), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("stable anonymous ID");
    }

    @Test
    void attributesAreImmutableAndDetachedFromTheCallerMap() {
        Map<String, String> source = new HashMap<>(Map.of("plan", "premium"));
        EvaluationContext context = new EvaluationContext("user-1", source);

        source.put("plan", "free");

        assertThat(context.attributes()).containsEntry("plan", "premium");
        assertThatThrownBy(() -> context.attributes().put("x", "y"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
