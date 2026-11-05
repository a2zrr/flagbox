package dev.a2zrr.flagbox.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByProjectIdAndEntityTypeAndEntityKeyOrderByOccurredAtDesc(
        Long projectId, AuditEntityType entityType, String entityKey);
}
