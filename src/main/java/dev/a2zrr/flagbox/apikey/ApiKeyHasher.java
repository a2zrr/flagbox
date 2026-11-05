package dev.a2zrr.flagbox.apikey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Hashes raw API keys so that only the hash is ever stored. */
public final class ApiKeyHasher {

    private ApiKeyHasher() {
    }

    /** Returns the lowercase hex SHA-256 digest of the raw key (64 characters). */
    public static String sha256Hex(String rawKey) {
        Objects.requireNonNull(rawKey, "rawKey is required");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }
}
