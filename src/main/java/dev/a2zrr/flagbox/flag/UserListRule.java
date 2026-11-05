package dev.a2zrr.flagbox.flag;

import java.util.List;

/**
 * Matches a fixed list of user IDs. Used for "on for these users" ({@code value = true})
 * and "off for these users" ({@code value = false}).
 */
public record UserListRule(List<String> userIds, boolean value) implements TargetingRule {

    public static final int MAX_USER_IDS = 1000;
    public static final int MAX_USER_ID_LENGTH = 128;

    public UserListRule {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("userIds must contain at least one user ID");
        }
        if (userIds.size() > MAX_USER_IDS) {
            throw new IllegalArgumentException("userIds may contain at most " + MAX_USER_IDS + " entries");
        }
        for (String id : userIds) {
            if (id == null || id.isBlank() || id.length() > MAX_USER_ID_LENGTH) {
                throw new IllegalArgumentException(
                    "each user ID must be 1 to " + MAX_USER_ID_LENGTH + " characters");
            }
        }
        userIds = List.copyOf(userIds);
    }
}
