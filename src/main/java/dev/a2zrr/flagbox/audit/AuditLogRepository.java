package dev.a2zrr.flagbox.audit;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByProjectIdAndEntityTypeAndEntityKeyOrderByOccurredAtDesc(
        Long projectId, AuditEntityType entityType, String entityKey);

    Page<AuditLog> findByProjectIdAndEntityKeyAndEntityTypeIn(
        Long projectId, String entityKey, Collection<AuditEntityType> entityTypes, Pageable pageable);
}
