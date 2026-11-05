package dev.a2zrr.flagbox.flag;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Matches on a context attribute, for example {@code country IN [FR, DZ]} or
 * {@code plan EQUALS premium}. Comparison is case-sensitive.
 */
public record AttributeRule(String attribute, AttributeOperator operator, List<String> values, boolean value)
    implements TargetingRule {

    public static final int MAX_VALUES = 100;
    public static final int MAX_VALUE_LENGTH = 128;
    private static final Pattern ATTRIBUTE_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,63}");

    public AttributeRule {
        if (attribute == null || !ATTRIBUTE_NAME.matcher(attribute).matches()) {
            throw new IllegalArgumentException(
                "attribute must start with a letter and contain only letters, digits and underscores");
        }
        if (operator == null) {
            throw new IllegalArgumentException("operator is required");
        }
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must contain at least one entry");
        }
        if (values.size() > MAX_VALUES) {
            throw new IllegalArgumentException("values may contain at most " + MAX_VALUES + " entries");
        }
        for (String v : values) {
            if (v == null || v.isBlank() || v.length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException(
                    "each value must be 1 to " + MAX_VALUE_LENGTH + " characters");
            }
        }
        if (operator == AttributeOperator.EQUALS && values.size() != 1) {
            throw new IllegalArgumentException("EQUALS takes exactly one value; use IN for several");
        }
        values = List.copyOf(values);
    }
}
