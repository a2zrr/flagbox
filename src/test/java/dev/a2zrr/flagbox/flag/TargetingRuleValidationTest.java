package dev.a2zrr.flagbox.flag;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/** Boundary cases for rule validation. The happy paths are covered in TargetingRuleTest. */
class TargetingRuleValidationTest {

    @Test
    void attributeRuleNeedsAnOperatorAndValues() {
        assertThatThrownBy(() -> new AttributeRule("plan", null, List.of("pro"), true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("operator is required");
        assertThatThrownBy(() -> new AttributeRule("plan", AttributeOperator.IN, null, true))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void attributeRuleRejectsMissingNames(String attribute) {
        assertThatThrownBy(() -> new AttributeRule(attribute, AttributeOperator.IN, List.of("x"), true))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void attributeRuleRejectsTooManyOrBlankValues() {
        List<String> tooMany = Collections.nCopies(AttributeRule.MAX_VALUES + 1, "x");
        assertThatThrownBy(() -> new AttributeRule("country", AttributeOperator.IN, tooMany, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at most 100 entries");
        assertThatThrownBy(() -> new AttributeRule("country", AttributeOperator.IN, List.of(" "), true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1 to 128 characters");
    }

    @Test
    void attributeRuleRejectsOverlongAttributeNames() {
        assertThatThrownBy(() -> new AttributeRule("a".repeat(65), AttributeOperator.IN, List.of("x"), true))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userListRuleRejectsTooManyIdsAndBadIds() {
        List<String> tooMany = Collections.nCopies(UserListRule.MAX_USER_IDS + 1, "u");
        assertThatThrownBy(() -> new UserListRule(tooMany, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at most 1000 entries");
        assertThatThrownBy(() -> new UserListRule(List.of("x".repeat(129)), true))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserListRule(java.util.Arrays.asList("ok", null), true))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
