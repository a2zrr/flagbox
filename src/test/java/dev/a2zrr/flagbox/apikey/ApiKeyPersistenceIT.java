package dev.a2zrr.flagbox.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.a2zrr.flagbox.audit.AuditService;
import dev.a2zrr.flagbox.evaluation.FlagSnapshotCache;
import dev.a2zrr.flagbox.environment.Environment;
import dev.a2zrr.flagbox.environment.EnvironmentRepository;
import dev.a2zrr.flagbox.project.Project;
import dev.a2zrr.flagbox.project.ProjectService;
import dev.a2zrr.flagbox.support.PostgresIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({ProjectService.class, AuditService.class, FlagSnapshotCache.class})
class ApiKeyPersistenceIT extends PostgresIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private EnvironmentRepository environments;

    @Autowired
    private ApiKeyRepository apiKeys;

    @Test
    void storesOnlyTheHashAndFindsTheKeyByIt() {
        Project project = projectService.create("checkout", "Checkout", null, "test");
        Environment production = environments.findByProjectAndKey(project, "production").orElseThrow();
        String raw = "fbx_admin_s3cr3t-value";
        String hash = ApiKeyHasher.sha256Hex(raw);

        apiKeys.saveAndFlush(new ApiKey(project, production, ApiKeyType.ADMIN, "CI admin", "fbx_admin_s3cr3t", hash));

        ApiKey found = apiKeys.findByKeyHash(hash).orElseThrow();
        assertThat(found.getType()).isEqualTo(ApiKeyType.ADMIN);
        assertThat(found.getKeyHash()).isEqualTo(hash).isNotEqualTo(raw);
        assertThat(found.isActive()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void revokingAKeyMarksItInactiveAndKeepsTheFirstRevocationTime() {
        Project project = projectService.create("checkout", "Checkout", null, "test");
        Environment production = environments.findByProjectAndKey(project, "production").orElseThrow();
        ApiKey key = apiKeys.saveAndFlush(new ApiKey(project, production, ApiKeyType.CLIENT, "web",
            "fbx_client_abc", ApiKeyHasher.sha256Hex("fbx_client_abc-secret")));

        Instant first = Instant.parse("2026-01-01T10:00:00Z");
        key.revoke(first);
        key.revoke(Instant.parse("2026-02-01T10:00:00Z"));

        assertThat(key.isActive()).isFalse();
        assertThat(key.getRevokedAt()).isEqualTo(first);
    }

    @Test
    void keyHashIsUnique() {
        Project project = projectService.create("checkout", "Checkout", null, "test");
        Environment production = environments.findByProjectAndKey(project, "production").orElseThrow();
        String hash = ApiKeyHasher.sha256Hex("same-raw-key");

        apiKeys.saveAndFlush(new ApiKey(project, production, ApiKeyType.CLIENT, "one", "fbx_client_same", hash));

        assertThatThrownBy(() -> apiKeys.saveAndFlush(
            new ApiKey(project, production, ApiKeyType.CLIENT, "two", "fbx_client_same", hash)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
