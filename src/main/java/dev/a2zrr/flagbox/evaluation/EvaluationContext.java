package dev.a2zrr.flagbox.evaluation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Who is being evaluated: a required user ID (used for rollout buckets and user
 * lists) and optional attributes (used by attribute rules). Attribute matching
 * is case-sensitive. A missing attribute never matches.
 */
public record EvaluationContext(String userId, Map<String, String> attributes) {

    public static final int MAX_ATTRIBUTES = 50;

    public EvaluationContext {
        if (userId == null || userId.isBlank() || userId.length() > 128) {
            throw new IllegalArgumentException("userId must be 1 to 128 characters. For logged-out users, send a "
                + "stable anonymous ID (a cookie or device ID) so their rollout bucket does not change");
        }
        if (attributes == null) {
            attributes = Collections.emptyMap();
        }
        if (attributes.size() > MAX_ATTRIBUTES) {
            throw new IllegalArgumentException("attributes may contain at most " + MAX_ATTRIBUTES + " entries");
        }
        attributes = Collections.unmodifiableMap(new HashMap<>(attributes));
    }
}
