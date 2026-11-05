package dev.a2zrr.flagbox.apikey;

import java.security.SecureRandom;
import java.util.Base64;

/** Generates raw API keys. The raw key is returned once and never stored. */
public final class ApiKeyGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SECRET_BYTES = 32;
    private static final int DISPLAY_CHARS = 6;

    /** Length of the display prefix of an ADMIN key, for example {@code fbx_admin_AbC123}. */
    public static final int PREFIX_LENGTH = ApiKeyType.ADMIN.prefix().length() + DISPLAY_CHARS;

    private ApiKeyGenerator() {
    }

    /**
     * @param raw    the full key, for example {@code fbx_admin_...}; shown to the caller once
     * @param prefix the display prefix stored with the key, for example {@code fbx_admin_AbC123}
     */
    public record GeneratedKey(String raw, String prefix) {
    }

    public static GeneratedKey generate(ApiKeyType type) {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new GeneratedKey(type.prefix() + secret, type.prefix() + secret.substring(0, DISPLAY_CHARS));
    }
}
