package dev.a2zrr.flagbox.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeyHasherTest {

    @Test
    void matchesKnownSha256Vector() {
        // SHA-256("abc") from FIPS 180-2
        assertThat(ApiKeyHasher.sha256Hex("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void isDeterministicAndNeverEqualsTheRawKey() {
        String raw = "fbx_admin_example-secret";
        String hash = ApiKeyHasher.sha256Hex(raw);

        assertThat(hash).hasSize(64).isEqualTo(ApiKeyHasher.sha256Hex(raw)).isNotEqualTo(raw);
    }

    @Test
    void apiKeyTypesHaveTheDocumentedPrefixes() {
        assertThat(ApiKeyType.ADMIN.prefix()).isEqualTo("fbx_admin_");
        assertThat(ApiKeyType.CLIENT.prefix()).isEqualTo("fbx_client_");
    }
}
