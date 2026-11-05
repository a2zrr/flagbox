package dev.a2zrr.flagbox.common;

import java.util.regex.Pattern;

/**
 * Validation for URL-safe keys such as project, environment and flag keys:
 * lowercase letters and digits, single hyphens between parts, at most 64 characters.
 */
public final class Slug {

    public static final int MAX_LENGTH = 64;
    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private Slug() {
    }

    public static boolean isValid(String value) {
        return value != null && value.length() <= MAX_LENGTH && PATTERN.matcher(value).matches();
    }

    public static void requireValid(String value, String field) {
        if (!isValid(value)) {
            throw new IllegalArgumentException(
                field + " must be a slug: lowercase letters, digits and single hyphens, at most "
                    + MAX_LENGTH + " characters");
        }
    }
}
