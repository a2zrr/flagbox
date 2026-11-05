package dev.a2zrr.flagbox.evaluation;

import dev.a2zrr.flagbox.flag.TargetingRule;
import java.util.List;

/**
 * The part of a flag config the engine needs, detached from JPA: the flag key,
 * the enabled switch, the rollout percentage and the ordered rules.
 */
public record FlagDefinition(String key, boolean enabled, int rolloutPercentage, List<TargetingRule> rules) {

    public FlagDefinition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException("rolloutPercentage must be between 0 and 100");
        }
        rules = List.copyOf(rules);
    }
}
