package dev.a2zrr.flagbox.evaluation;

/** Why a flag evaluated to the value it did. */
public enum Reason {
    /** No flag with this key exists in the environment (or it is archived). The client uses its own fallback. */
    FLAG_NOT_FOUND,
    /** The flag is disabled in this environment; the value is false. */
    FLAG_DISABLED,
    /** An "off" user-list rule matched; the value is false. */
    USER_DENIED,
    /** An "on" user-list rule matched; the value is true. */
    USER_ALLOWED,
    /** An attribute rule matched; the value is that rule's value. */
    RULE_MATCH,
    /** The user's rollout bucket is inside the rollout percentage; the value is true. */
    ROLLOUT_IN,
    /** The user's rollout bucket is outside the rollout percentage; the value is false. */
    ROLLOUT_OUT
}
