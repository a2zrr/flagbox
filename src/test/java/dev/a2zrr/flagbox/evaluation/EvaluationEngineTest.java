package dev.a2zrr.flagbox.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.a2zrr.flagbox.flag.AttributeOperator;
import dev.a2zrr.flagbox.flag.AttributeRule;
import dev.a2zrr.flagbox.flag.TargetingRule;
import dev.a2zrr.flagbox.flag.UserListRule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvaluationEngineTest {

    private static final String FLAG = "new-checkout";

    private static EvaluationContext user(String id, Map<String, String> attributes) {
        return new EvaluationContext(id, attributes);
    }

    private static EvaluationContext user(String id) {
        return user(id, Map.of());
    }

    private static FlagDefinition flag(boolean enabled, int rollout, TargetingRule... rules) {
        return new FlagDefinition(FLAG, enabled, rollout, List.of(rules));
    }

    private static Evaluation evaluate(FlagDefinition flag, EvaluationContext context) {
        return EvaluationEngine.evaluate(FLAG, flag, context);
    }

    // Reason: FLAG_NOT_FOUND

    @Test
    void missingFlagReturnsNullValueAndFlagNotFound() {
        Evaluation result = EvaluationEngine.evaluate(FLAG, null, user("user-1"));

        assertThat(result.value()).isNull();
        assertThat(result.reason()).isEqualTo(Reason.FLAG_NOT_FOUND);
        assertThat(result.flagKey()).isEqualTo(FLAG);
    }

    // Reason: FLAG_DISABLED

    @Test
    void disabledFlagServesFalseEvenWhenItsRulesWouldMatch() {
        FlagDefinition disabled = flag(false, 100, new UserListRule(List.of("user-1"), true));

        Evaluation result = evaluate(disabled, user("user-1"));

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(Reason.FLAG_DISABLED);
    }

    // Reason: USER_ALLOWED and USER_DENIED

    @Test
    void userInOnListIsAllowed() {
        FlagDefinition on = flag(true, 0, new UserListRule(List.of("beta-1", "beta-2"), true));

        Evaluation result = evaluate(on, user("beta-2"));

        assertThat(result.value()).isTrue();
        assertThat(result.reason()).isEqualTo(Reason.USER_ALLOWED);
    }

    @Test
    void userInOffListIsDeniedEvenWhenRolloutWouldInclude() {
        FlagDefinition off = flag(true, 100, new UserListRule(List.of("blocked-1"), false));

        Evaluation result = evaluate(off, user("blocked-1"));

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(Reason.USER_DENIED);
    }

    // Reason: RULE_MATCH (the rule's own value)

    @Test
    void attributeRuleServingTrueReportsRuleMatch() {
        FlagDefinition rule = flag(true, 0,
            new AttributeRule("country", AttributeOperator.IN, List.of("FR", "DZ"), true));

        Evaluation result = evaluate(rule, user("user-1", Map.of("country", "FR")));

        assertThat(result.value()).isTrue();
        assertThat(result.reason()).isEqualTo(Reason.RULE_MATCH);
    }

    @Test
    void attributeRuleServingFalseReportsRuleMatchWithFalse() {
        FlagDefinition rule = flag(true, 100,
            new AttributeRule("country", AttributeOperator.IN, List.of("DE"), false));

        Evaluation result = evaluate(rule, user("user-1", Map.of("country", "DE")));

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(Reason.RULE_MATCH);
    }

    @Test
    void attributeEqualsMatchesOnlyTheExactValue() {
        FlagDefinition rule = flag(true, 0,
            new AttributeRule("plan", AttributeOperator.EQUALS, List.of("premium"), true));

        assertThat(evaluate(rule, user("u", Map.of("plan", "premium"))).reason()).isEqualTo(Reason.RULE_MATCH);
        assertThat(evaluate(rule, user("u", Map.of("plan", "free"))).reason()).isEqualTo(Reason.ROLLOUT_OUT);
    }

    @Test
    void attributeMatchingIsCaseSensitive() {
        FlagDefinition rule = flag(true, 0,
            new AttributeRule("country", AttributeOperator.IN, List.of("FR"), true));

        Evaluation result = evaluate(rule, user("u", Map.of("country", "fr")));

        assertThat(result.reason()).isEqualTo(Reason.ROLLOUT_OUT);
    }

    @Test
    void missingAttributeNeverMatches() {
        FlagDefinition rule = flag(true, 0,
            new AttributeRule("country", AttributeOperator.IN, List.of("FR"), true));

        assertThat(evaluate(rule, user("u")).reason()).isEqualTo(Reason.ROLLOUT_OUT);
    }

    // Rule ordering: first match wins

    @Test
    void offRuleBeforeOnRuleWinsForUsersInBothLists() {
        FlagDefinition offFirst = flag(true, 100,
            new UserListRule(List.of("user-1"), false),
            new UserListRule(List.of("user-1"), true));

        Evaluation result = evaluate(offFirst, user("user-1"));

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(Reason.USER_DENIED);
    }

    @Test
    void onRuleBeforeOffRuleWinsForUsersInBothLists() {
        FlagDefinition onFirst = flag(true, 0,
            new UserListRule(List.of("user-1"), true),
            new UserListRule(List.of("user-1"), false));

        Evaluation result = evaluate(onFirst, user("user-1"));

        assertThat(result.value()).isTrue();
        assertThat(result.reason()).isEqualTo(Reason.USER_ALLOWED);
    }

    @Test
    void attributeRuleOrderDecidesWhenSeveralRulesMatch() {
        FlagDefinition attributeOffFirst = flag(true, 0,
            new AttributeRule("country", AttributeOperator.IN, List.of("DE"), false),
            new AttributeRule("plan", AttributeOperator.EQUALS, List.of("premium"), true));

        Evaluation result = evaluate(attributeOffFirst, user("u", Map.of("country", "DE", "plan", "premium")));

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(Reason.RULE_MATCH);
    }

    @Test
    void rulesThatDoNotMatchAreSkipped() {
        FlagDefinition rules = flag(true, 0,
            new UserListRule(List.of("someone-else"), false),
            new AttributeRule("plan", AttributeOperator.EQUALS, List.of("premium"), true));

        Evaluation result = evaluate(rules, user("user-1", Map.of("plan", "premium")));

        assertThat(result.reason()).isEqualTo(Reason.RULE_MATCH);
        assertThat(result.value()).isTrue();
    }

    // Reason: ROLLOUT_IN and ROLLOUT_OUT

    @Test
    void userInsideRolloutIsIn() {
        Evaluation result = evaluate(flag(true, 100), user("user-1"));

        assertThat(result.value()).isTrue();
        assertThat(result.reason()).isEqualTo(Reason.ROLLOUT_IN);
    }

    @Test
    void userOutsideRolloutIsOut() {
        Evaluation result = evaluate(flag(true, 0), user("user-1"));

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(Reason.ROLLOUT_OUT);
    }

    @Test
    void rolloutDecisionMatchesTheBucket() {
        // user-1 has bucket 31: in at 40%, out at 30%.
        assertThat(evaluate(flag(true, 40), user("user-1")).reason()).isEqualTo(Reason.ROLLOUT_IN);
        assertThat(evaluate(flag(true, 30), user("user-1")).reason()).isEqualTo(Reason.ROLLOUT_OUT);
    }

    // Context validation

    @Test
    void contextRequiresAUserId() {
        assertThatThrownBy(() -> new EvaluationContext(" ", Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvaluationContext(null, Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contextAttributesAreCopiedAndNullBecomesEmpty() {
        assertThat(new EvaluationContext("user-1", null).attributes()).isEmpty();
    }
}
