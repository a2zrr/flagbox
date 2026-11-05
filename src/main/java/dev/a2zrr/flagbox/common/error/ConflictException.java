package dev.a2zrr.flagbox.common.error;

/** Thrown when a request would duplicate an existing key. Maps to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
