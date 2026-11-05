package dev.a2zrr.flagbox.apikey;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Scope checks for management endpoints. The role check (ADMIN) happens in the
 * security filter chain; these methods check which project and environment the
 * key may touch.
 *
 * <ul>
 *   <li>global key: everything</li>
 *   <li>{@link #requireProjectAccess}: reads of a project, for keys of that project</li>
 *   <li>{@link #requireEnvironmentAccess}: reads and writes inside one environment</li>
 *   <li>{@link #requireGlobal}: creating or deleting projects and environments, and flag definitions</li>
 * </ul>
 */
@Component
public class AccessGuard {

    public void requireGlobal(Authentication authentication) {
        if (!principal(authentication).isGlobal()) {
            throw denied("This operation needs a global admin key");
        }
    }

    public void requireProjectAccess(Authentication authentication, String projectKey) {
        ApiKeyPrincipal principal = principal(authentication);
        if (principal.isGlobal() || projectKey.equals(principal.projectKey())) {
            return;
        }
        throw denied("This key is not allowed to access project '" + projectKey + "'");
    }

    public void requireEnvironmentAccess(Authentication authentication, String projectKey, String environmentKey) {
        ApiKeyPrincipal principal = principal(authentication);
        if (principal.isGlobal()
                || (projectKey.equals(principal.projectKey()) && environmentKey.equals(principal.environmentKey()))) {
            return;
        }
        throw denied("This key is not allowed to access environment '" + environmentKey + "'");
    }

    private static ApiKeyPrincipal principal(Authentication authentication) {
        if (authentication instanceof ApiKeyAuthentication apiKey) {
            return apiKey.getPrincipal();
        }
        throw denied("An API key is required");
    }

    private static AccessDeniedException denied(String message) {
        return new AccessDeniedException(message);
    }
}
