package dev.a2zrr.flagbox.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One recorded change: who changed what, when, and the value before and after.
 * Rows are never updated or deleted by the application.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "environment_id", updatable = false)
    private Long environmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 32)
    private AuditEntityType entityType;

    @Column(name = "entity_key", nullable = false, updatable = false, length = 128)
    private String entityKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private AuditAction action;

    @Column(nullable = false, updatable = false, length = 120)
    private String actor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", updatable = false)
    private Map<String, Object> oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", updatable = false)
    private Map<String, Object> newValue;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public AuditLog(Long projectId, Long environmentId, AuditEntityType entityType, String entityKey,
                    AuditAction action, String actor, Map<String, Object> oldValue,
                    Map<String, Object> newValue, Instant occurredAt) {
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.entityType = entityType;
        this.entityKey = entityKey;
        this.action = action;
        this.actor = actor;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.occurredAt = occurredAt;
    }
}
