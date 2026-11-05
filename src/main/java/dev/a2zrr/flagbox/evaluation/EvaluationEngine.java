package dev.a2zrr.flagbox.evaluation;

import dev.a2zrr.flagbox.flag.AttributeRule;
import dev.a2zrr.flagbox.flag.AttributeOperator;
import dev.a2zrr.flagbox.flag.TargetingRule;
import dev.a2zrr.flagbox.flag.UserListRule;
import java.util.Map;

/**
 * Decides the value of one flag for one user. Pure Java: no Spring, no database.
 *
 * <p><b>Evaluation order</b> (this is the contract clients rely on):
 * <ol>
 *   <li>Flag not found (or archived): value {@code null}, {@link Reason#FLAG_NOT_FOUND}.
 *       The client uses its own fallback.</li>
 *   <li>Flag disabled: value {@code false}, {@link Reason#FLAG_DISABLED}.</li>
 *   <li>Rules, in order. The first matching rule decides:
 *     <ul>
 *       <li>user in an off-list: {@code false}, {@link Reason#USER_DENIED}</li>
 *       <li>user in an on-list: {@code true}, {@link Reason#USER_ALLOWED}</li>
 *       <li>attribute rule matches: the rule's own value (true or false),
 *           {@link Reason#RULE_MATCH}</li>
 *     </ul>
 *     Order matters: an off-rule placed before an on-rule wins for users that
 *     match both.</li>
 *   <li>Rollout: if the user's bucket is below the rollout percentage, value
 *       {@code true}, {@link Reason#ROLLOUT_IN}; otherwise {@code false},
 *       {@link Reason#ROLLOUT_OUT}.</li>
 * </ol>
 */
public final class EvaluationEngine {

    private EvaluationEngine() {
    }

    /**
     * @param flagKey the key the client asked for; used even when the flag is missing
     * @param flag    the flag definition, or {@code null} if no such flag is active
     */
    public static Evaluation evaluate(String flagKey, FlagDefinition flag, EvaluationContext context) {
        if (flag == null) {
            return new Evaluation(flagKey, null, Reason.FLAG_NOT_FOUND);
        }
        if (!flag.enabled()) {
            return new Evaluation(flagKey, false, Reason.FLAG_DISABLED);
        }

        String userId = context.userId();
        for (TargetingRule rule : flag.rules()) {
            if (rule instanceof UserListRule list) {
                if (list.userIds().contains(userId)) {
                    return list.value()
                        ? new Evaluation(flagKey, true, Reason.USER_ALLOWED)
                        : new Evaluation(flagKey, false, Reason.USER_DENIED);
                }
            } else if (rule instanceof AttributeRule attribute) {
                if (matches(attribute, context.attributes())) {
                    return new Evaluation(flagKey, attribute.value(), Reason.RULE_MATCH);
                }
            }
        }

        boolean inRollout = Rollout.isIn(flagKey, userId, flag.rolloutPercentage());
        return inRollout
            ? new Evaluation(flagKey, true, Reason.ROLLOUT_IN)
            : new Evaluation(flagKey, false, Reason.ROLLOUT_OUT);
    }

    private static boolean matches(AttributeRule rule, Map<String, String> attributes) {
        String actual = attributes.get(rule.attribute());
        if (actual == null) {
            return false;
        }
        return rule.operator() == AttributeOperator.EQUALS
            ? rule.values().get(0).equals(actual)
            : rule.values().contains(actual);
    }
}
