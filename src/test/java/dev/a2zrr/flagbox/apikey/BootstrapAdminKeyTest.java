package dev.a2zrr.flagbox.apikey;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class BootstrapAdminKeyTest {

    private static final String VALID = "fbx_admin_" + "a".repeat(40);

    private final ApiKeyRepository keys = mock(ApiKeyRepository.class);
    private final ApiKeyService service = mock(ApiKeyService.class);

    private BootstrapAdminKey bootstrap(String configured) {
        return new BootstrapAdminKey(keys, service, configured);
    }

    @Test
    void registersTheConfiguredKeyWhenNoGlobalAdminKeyExists() {
        when(keys.existsByProjectIsNullAndType(ApiKeyType.ADMIN)).thenReturn(false);

        bootstrap(VALID).ensureGlobalAdminKey();

        verify(service).createGlobalAdmin(VALID, BootstrapAdminKey.NAME);
    }

    @Test
    void ignoresTheVariableOnceAGlobalAdminKeyExists() {
        when(keys.existsByProjectIsNullAndType(ApiKeyType.ADMIN)).thenReturn(true);

        bootstrap(VALID).ensureGlobalAdminKey();

        verify(service, never()).createGlobalAdmin(anyString(), anyString());
    }

    @Test
    void doesNothingWhenNoKeyIsConfigured() {
        when(keys.existsByProjectIsNullAndType(ApiKeyType.ADMIN)).thenReturn(false);

        bootstrap("   ").ensureGlobalAdminKey();

        verify(service, never()).createGlobalAdmin(anyString(), anyString());
    }

    @Test
    void refusesAKeyWithTheWrongPrefix() {
        when(keys.existsByProjectIsNullAndType(ApiKeyType.ADMIN)).thenReturn(false);
        String clientPrefix = "fbx_client_" + "a".repeat(40);

        assertThatThrownBy(() -> bootstrap(clientPrefix).ensureGlobalAdminKey())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must start with 'fbx_admin_'");
        verify(service, never()).createGlobalAdmin(anyString(), anyString());
    }

    @Test
    void refusesAKeyThatIsTooShort() {
        when(keys.existsByProjectIsNullAndType(ApiKeyType.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> bootstrap("fbx_admin_short").ensureGlobalAdminKey())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 40 characters");
    }
}
