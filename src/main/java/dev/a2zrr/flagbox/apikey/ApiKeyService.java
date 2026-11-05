package dev.a2zrr.flagbox.apikey;

import static dev.a2zrr.flagbox.audit.AuditService.fields;

import dev.a2zrr.flagbox.audit.AuditAction;
import dev.a2zrr.flagbox.audit.AuditEntityType;
import dev.a2zrr.flagbox.audit.AuditService;
import dev.a2zrr.flagbox.common.error.ResourceNotFoundException;
import dev.a2zrr.flagbox.environment.Environment;
import dev.a2zrr.flagbox.project.Project;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyService {

    /** A newly created key together with its raw value, which is only available at this point. */
    public record CreatedApiKey(ApiKey key, String rawKey) {
    }

    private final ApiKeyRepository keys;
    private final AuditService audit;

    public ApiKeyService(ApiKeyRepository keys, AuditService audit) {
        this.keys = keys;
        this.audit = audit;
    }

    @Transactional
    public CreatedApiKey create(Project project, Environment environment, String name, ApiKeyType type,
                                String actor) {
        ApiKeyGenerator.GeneratedKey generated = ApiKeyGenerator.generate(type);
        ApiKey key = keys.save(new ApiKey(project, environment, type, name, generated.prefix(),
            ApiKeyHasher.sha256Hex(generated.raw())));
        audit.record(AuditEntityType.API_KEY, project.getId(), environment.getId(),
            String.valueOf(key.getId()), AuditAction.CREATE, actor, null,
            fields("name", key.getName(), "type", type.name(), "keyPrefix", key.getKeyPrefix()));
        return new CreatedApiKey(key, generated.raw());
    }

    @Transactional(readOnly = true)
    public Page<ApiKey> list(Project project, Environment environment, Pageable pageable) {
        return keys.findByProjectAndEnvironment(project, environment, pageable);
    }

    /** Revokes a key. Revoking an already revoked key does nothing. */
    @Transactional
    public ApiKey revoke(Project project, Environment environment, Long id, String actor) {
        ApiKey key = keys.findByIdAndProjectAndEnvironment(id, project, environment)
            .orElseThrow(() -> new ResourceNotFoundException("API key " + id + " not found"));
        if (!key.isActive()) {
            return key;
        }
        key.revoke(Instant.now());
        audit.record(AuditEntityType.API_KEY, project.getId(), environment.getId(), String.valueOf(id),
            AuditAction.UPDATE, actor, fields("revoked", false), fields("revoked", true));
        return key;
    }
}
