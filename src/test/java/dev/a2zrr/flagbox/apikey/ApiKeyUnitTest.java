package dev.a2zrr.flagbox.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.a2zrr.flagbox.environment.Environment;
import dev.a2zrr.flagbox.project.Project;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/** Unit tests for the API key entity, authenticator and scope guard. */
class ApiKeyUnitTest {

    private static final String HASH = ApiKeyHasher.sha256Hex("fbx_client_example");

    private final Project project = new Project("checkout", "Checkout", null);
    private final Environment production = new Environment(project, "production", "Production");

    // ApiKey

    @Test
    void scopedKeyNeedsBothProjectAndEnvironment() {
        assertThatThrownBy(() -> new ApiKey(project, null, ApiKeyType.CLIENT, "web", "fbx_client_x", HASH))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("either global or scoped");
        assertThatThrownBy(() -> new ApiKey(null, production, ApiKeyType.ADMIN, "web", "fbx_admin_x", HASH))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyAdminKeysCanBeGlobal() {
        assertThatThrownBy(() -> new ApiKey(null, null, ApiKeyType.CLIENT, "web", "fbx_client_x", HASH))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("only ADMIN keys can be global");
    }

    @Test
    void typeAndHashAreValidated() {
        assertThatThrownBy(() -> new ApiKey(project, production, null, "web", "fbx_client_x", HASH))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiKey(project, production, ApiKeyType.CLIENT, "web", "fbx_client_x", "short"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("64-character");
        assertThatThrownBy(() -> new ApiKey(project, production, ApiKeyType.CLIENT, " ", "fbx_client_x", HASH))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokingKeepsTheFirstRevocationTimeAndGlobalKeysKnowTheyAreGlobal() {
        ApiKey global = ApiKey.global("bootstrap-admin", "fbx_admin_abcdef", HASH);
        Instant first = Instant.parse("2026-01-01T00:00:00Z");

        global.revoke(first);
        global.revoke(Instant.parse("2026-06-01T00:00:00Z"));

        assertThat(global.isGlobal()).isTrue();
        assertThat(global.isActive()).isFalse();
        assertThat(global.getRevokedAt()).isEqualTo(first);
    }

    // ApiKeyAuthenticator

    @Test
    void malformedAndOversizedKeysAreRejectedWithoutALookup() {
        ApiKeyRepository keys = mock(ApiKeyRepository.class);
        ApiKeyAuthenticator authenticator = new ApiKeyAuthenticator(keys);

        assertThat(authenticator.authenticate(null)).isEmpty();
        assertThat(authenticator.authenticate("sk_live_123")).isEmpty();
        assertThat(authenticator.authenticate("fbx_client_" + "x".repeat(200))).isEmpty();
        verify(keys, never()).findByKeyHash(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void revokedKeysResolveToNothing() {
        ApiKeyRepository keys = mock(ApiKeyRepository.class);
        ApiKey revoked = ApiKey.global("old", "fbx_admin_old", HASH);
        revoked.revoke(Instant.now());
        when(keys.findByKeyHash(HASH)).thenReturn(Optional.of(revoked));

        assertThat(new ApiKeyAuthenticator(keys).authenticate("fbx_admin_" + "x".repeat(40))).isEmpty();
    }

    @Test
    void activeGlobalKeyResolvesToAGlobalPrincipal() {
        ApiKeyRepository keys = mock(ApiKeyRepository.class);
        String raw = "fbx_admin_" + "x".repeat(40);
        when(keys.findByKeyHash(ApiKeyHasher.sha256Hex(raw)))
            .thenReturn(Optional.of(ApiKey.global("bootstrap-admin", "fbx_admin_xxxxxx", HASH)));

        ApiKeyPrincipal principal = new ApiKeyAuthenticator(keys).authenticate(raw).orElseThrow();

        assertThat(principal.isGlobal()).isTrue();
        assertThat(principal.keyName()).isEqualTo("bootstrap-admin");
        assertThat(principal.type()).isEqualTo(ApiKeyType.ADMIN);
    }

    // AccessGuard

    @Test
    void globalKeyPassesEveryScopeCheck() {
        AccessGuard guard = new AccessGuard();
        ApiKeyAuthentication global = authentication(null, null, null, null);

        guard.requireGlobal(global);
        guard.requireProjectAccess(global, "any-project");
        guard.requireEnvironmentAccess(global, "any-project", "any-env");
    }

    @Test
    void scopedKeyPassesOnlyItsOwnProjectAndEnvironment() {
        AccessGuard guard = new AccessGuard();
        ApiKeyAuthentication scoped = authentication(1L, "checkout", 2L, "production");

        guard.requireProjectAccess(scoped, "checkout");
        guard.requireEnvironmentAccess(scoped, "checkout", "production");

        assertThatThrownBy(() -> guard.requireProjectAccess(scoped, "search"))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guard.requireEnvironmentAccess(scoped, "checkout", "staging"))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guard.requireGlobal(scoped))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonKeyAuthenticationIsDenied() {
        AccessGuard guard = new AccessGuard();
        UsernamePasswordAuthenticationToken management = new UsernamePasswordAuthenticationToken("someone", "pw");

        assertThatThrownBy(() -> guard.requireGlobal(management))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("An API key is required");
    }

    private static ApiKeyAuthentication authentication(Long projectId, String projectKey,
                                                       Long environmentId, String environmentKey) {
        return new ApiKeyAuthentication(new ApiKeyPrincipal(1L, "key", ApiKeyType.ADMIN,
            projectId, projectKey, environmentId, environmentKey));
    }
}
