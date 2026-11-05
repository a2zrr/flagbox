package dev.a2zrr.flagbox.audit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.a2zrr.flagbox.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class AuditLogPersistenceIT extends PostgresIntegrationTest {

    @Autowired
    private AuditLogRepository auditLogs;

    @Autowired
    private EntityManager entityManager;

    @Test
    void storesOldAndNewValuesAsJsonAndReturnsHistoryNewestFirst() {
        auditLogs.saveAndFlush(new AuditLog(1L, 2L, AuditEntityType.FLAG, "new-checkout", AuditAction.CREATE,
            "admin-key", null, Map.of("enabled", false), Instant.parse("2026-03-01T09:00:00Z")));
        auditLogs.saveAndFlush(new AuditLog(1L, 2L, AuditEntityType.FLAG, "new-checkout", AuditAction.UPDATE,
            "admin-key", Map.of("enabled", false), Map.of("enabled", true, "rolloutPercentage", 30),
            Instant.parse("2026-03-02T09:00:00Z")));
        auditLogs.saveAndFlush(new AuditLog(1L, 2L, AuditEntityType.FLAG, "other-flag", AuditAction.CREATE,
            "admin-key", null, Map.of(), Instant.parse("2026-03-03T09:00:00Z")));

        entityManager.clear();

        List<AuditLog> history = auditLogs.findByProjectIdAndEntityTypeAndEntityKeyOrderByOccurredAtDesc(
            1L, AuditEntityType.FLAG, "new-checkout");

        assertThat(history).extracting(AuditLog::getAction)
            .containsExactly(AuditAction.UPDATE, AuditAction.CREATE);
        AuditLog update = history.get(0);
        assertThat(update.getOldValue()).containsEntry("enabled", false);
        assertThat(update.getNewValue()).containsEntry("enabled", true).containsEntry("rolloutPercentage", 30);
        assertThat(update.getActor()).isEqualTo("admin-key");
    }
}
