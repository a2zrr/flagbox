package dev.a2zrr.flagbox.apikey;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves a raw API key to its principal. Missing, malformed, unknown and revoked keys all resolve to empty. */
@Service
public class ApiKeyAuthenticator {

    private static final int MAX_KEY_LENGTH = 128;

    private final ApiKeyRepository keys;

    public ApiKeyAuthenticator(ApiKeyRepository keys) {
        this.keys = keys;
    }

    @Transactional(readOnly = true)
    public Optional<ApiKeyPrincipal> authenticate(String rawKey) {
        if (rawKey == null || rawKey.length() > MAX_KEY_LENGTH) {
            return Optional.empty();
        }
        boolean wellFormed = rawKey.startsWith(ApiKeyType.ADMIN.prefix())
            || rawKey.startsWith(ApiKeyType.CLIENT.prefix());
        if (!wellFormed) {
            return Optional.empty();
        }
        return keys.findByKeyHash(ApiKeyHasher.sha256Hex(rawKey))
            .filter(ApiKey::isActive)
            .map(ApiKeyAuthenticator::toPrincipal);
    }

    private static ApiKeyPrincipal toPrincipal(ApiKey key) {
        if (key.isGlobal()) {
            return new ApiKeyPrincipal(key.getId(), key.getName(), key.getType(), null, null, null, null);
        }
        return new ApiKeyPrincipal(key.getId(), key.getName(), key.getType(),
            key.getProject().getId(), key.getProject().getKey(),
            key.getEnvironment().getId(), key.getEnvironment().getKey());
    }
}
