package dev.a2zrr.flagbox.common.web;

import org.springframework.security.core.Authentication;

/** Resolves the name recorded in audit entries for the current caller. */
public final class Actors {

    private Actors() {
    }

    public static String nameOf(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
