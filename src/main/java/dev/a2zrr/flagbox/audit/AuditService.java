package dev.a2zrr.flagbox.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    /** Appends one entry. Called inside the same transaction as the change it describes. */
    public void record(AuditEntityType entityType, Long projectId, Long environmentId, String entityKey,
                       AuditAction action, String actor, Map<String, Object> oldValue,
                       Map<String, Object> newValue) {
        logs.save(new AuditLog(projectId, environmentId, entityType, entityKey, action, actor,
            oldValue, newValue, Instant.now()));
    }

    /** History of a flag: its own changes and the changes to its per-environment configs, newest first. */
    @Transactional(readOnly = true)
    public Page<AuditLog> flagHistory(Long projectId, String flagKey, Pageable pageable) {
        return logs.findByProjectIdAndEntityKeyAndEntityTypeIn(
            projectId, flagKey, List.of(AuditEntityType.FLAG, AuditEntityType.FLAG_CONFIG), pageable);
    }

    /** Builds an ordered map from alternating key/value arguments. Null values are allowed. */
    public static Map<String, Object> fields(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("fields() needs key/value pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
