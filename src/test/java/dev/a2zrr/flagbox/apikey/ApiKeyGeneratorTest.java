package dev.a2zrr.flagbox.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiKeyGeneratorTest {

    @Test
    void rawKeyCarriesTheTypePrefixAndAHighEntropySecret() {
        ApiKeyGenerator.GeneratedKey key = ApiKeyGenerator.generate(ApiKeyType.ADMIN);

        assertThat(key.raw()).startsWith("fbx_admin_");
        // 32 random bytes encode to 43 base64url characters.
        assertThat(key.raw().substring("fbx_admin_".length())).hasSize(43);
    }

    @Test
    void displayPrefixIsAShortPrefixOfTheRawKey() {
        ApiKeyGenerator.GeneratedKey key = ApiKeyGenerator.generate(ApiKeyType.CLIENT);

        assertThat(key.prefix()).startsWith("fbx_client_");
        assertThat(key.raw()).startsWith(key.prefix());
        assertThat(key.prefix().length()).isLessThan(key.raw().length());
    }

    @Test
    void keysAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(ApiKeyGenerator.generate(ApiKeyType.CLIENT).raw());
        }
        assertThat(seen).hasSize(1000);
    }
}
