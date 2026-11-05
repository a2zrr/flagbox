package dev.a2zrr.flagbox.flag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TargetingRuleTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void userListRuleRequiresUserIds() {
        assertThatThrownBy(() -> new UserListRule(List.of(), true))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserListRule(null, true))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserListRule(List.of(" "), false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userListRuleCopiesItsInput() {
        List<String> ids = new ArrayList<>(List.of("user-1"));
        UserListRule rule = new UserListRule(ids, true);
        ids.add("user-2");
        assertThat(rule.userIds()).containsExactly("user-1");
    }

    @Test
    void attributeRuleRejectsBadAttributeNames() {
        assertThatThrownBy(() -> new AttributeRule("1country", AttributeOperator.IN, List.of("FR"), true))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AttributeRule("country-code", AttributeOperator.IN, List.of("FR"), true))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalsTakesExactlyOneValue() {
        assertThatThrownBy(() -> new AttributeRule("plan", AttributeOperator.EQUALS, List.of("pro", "free"), true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("EQUALS takes exactly one value");
        AttributeRule ok = new AttributeRule("plan", AttributeOperator.EQUALS, List.of("premium"), true);
        assertThat(ok.values()).containsExactly("premium");
    }

    @Test
    void inRequiresAtLeastOneValue() {
        assertThatThrownBy(() -> new AttributeRule("country", AttributeOperator.IN, List.of(), true))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rulesRoundTripThroughJsonWithTypeDiscriminator() throws Exception {
        List<TargetingRule> rules = List.of(
            new UserListRule(List.of("user-1", "user-2"), true),
            new AttributeRule("country", AttributeOperator.IN, List.of("FR", "DZ"), false),
            new AttributeRule("plan", AttributeOperator.EQUALS, List.of("premium"), true));

        // Serialize with the declared element type, as the JPA mapping does for FlagConfig.rules.
        String json = mapper.writerFor(new TypeReference<List<TargetingRule>>() {}).writeValueAsString(rules);

        assertThat(json)
            .contains("\"type\":\"USER_LIST\"")
            .contains("\"type\":\"ATTRIBUTE\"")
            .contains("\"operator\":\"IN\"");

        List<TargetingRule> back = mapper.readValue(json, new TypeReference<List<TargetingRule>>() {});
        assertThat(back).isEqualTo(rules);
    }
}
