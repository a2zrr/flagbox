package dev.a2zrr.flagbox.apikey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On first startup, registers the global admin key given in
 * {@code FLAGBOX_BOOTSTRAP_ADMIN_KEY}. Later startups do nothing: once a global
 * admin key exists, the variable is ignored. See the README for rotation.
 */
@Component
public class BootstrapAdminKey implements ApplicationRunner {

    static final String NAME = "bootstrap-admin";
    static final int MIN_LENGTH = 40;

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminKey.class);

    private final ApiKeyRepository keys;
    private final ApiKeyService apiKeys;
    private final String configuredKey;

    public BootstrapAdminKey(ApiKeyRepository keys, ApiKeyService apiKeys,
                             @Value("${flagbox.bootstrap.admin-key:}") String configuredKey) {
        this.keys = keys;
        this.apiKeys = apiKeys;
        this.configuredKey = configuredKey == null ? "" : configuredKey.strip();
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureGlobalAdminKey();
    }

    /** Idempotent: registers the key only when no global admin key exists yet. */
    public void ensureGlobalAdminKey() {
        if (keys.existsByProjectIsNullAndType(ApiKeyType.ADMIN)) {
            if (!configuredKey.isEmpty()) {
                log.info("A global admin key already exists; FLAGBOX_BOOTSTRAP_ADMIN_KEY is ignored");
            }
            return;
        }
        if (configuredKey.isEmpty()) {
            log.warn("No global admin key exists and FLAGBOX_BOOTSTRAP_ADMIN_KEY is not set; "
                + "the management API cannot be used until it is");
            return;
        }
        if (!configuredKey.startsWith(ApiKeyType.ADMIN.prefix()) || configuredKey.length() < MIN_LENGTH) {
            throw new IllegalStateException("FLAGBOX_BOOTSTRAP_ADMIN_KEY must start with '"
                + ApiKeyType.ADMIN.prefix() + "' and be at least " + MIN_LENGTH + " characters");
        }
        apiKeys.createGlobalAdmin(configuredKey, NAME);
        log.info("Registered the bootstrap global admin key '{}'", NAME);
    }
}
