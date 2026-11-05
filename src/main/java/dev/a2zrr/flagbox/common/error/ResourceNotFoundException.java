package dev.a2zrr.flagbox.common.error;

/** Thrown when a project, environment, flag, config or key does not exist. Maps to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
