package dev.a2zrr.flagbox.evaluation;

/**
 * The result of evaluating one flag for one user.
 *
 * @param flagKey the flag that was evaluated
 * @param value   the served value; {@code null} only when the flag was not found
 * @param reason  why this value was served
 */
public record Evaluation(String flagKey, Boolean value, Reason reason) {
}
