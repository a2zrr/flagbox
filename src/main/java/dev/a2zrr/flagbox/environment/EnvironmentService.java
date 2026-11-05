package dev.a2zrr.flagbox.environment;

import static dev.a2zrr.flagbox.audit.AuditService.fields;

import dev.a2zrr.flagbox.audit.AuditAction;
import dev.a2zrr.flagbox.audit.AuditEntityType;
import dev.a2zrr.flagbox.audit.AuditService;
import dev.a2zrr.flagbox.common.Slug;
import dev.a2zrr.flagbox.common.error.ConflictException;
import dev.a2zrr.flagbox.common.error.ResourceNotFoundException;
import dev.a2zrr.flagbox.evaluation.FlagSnapshotCache;
import dev.a2zrr.flagbox.flag.FlagService;
import dev.a2zrr.flagbox.project.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnvironmentService {

    private final EnvironmentRepository environments;
    private final FlagService flags;
    private final AuditService audit;
    private final FlagSnapshotCache snapshots;

    public EnvironmentService(EnvironmentRepository environments, FlagService flags, AuditService audit,
                              FlagSnapshotCache snapshots) {
        this.environments = environments;
        this.flags = flags;
        this.audit = audit;
        this.snapshots = snapshots;
    }

    @Transactional(readOnly = true)
    public Page<Environment> list(Project project, Pageable pageable) {
        return environments.findByProject(project, pageable);
    }

    @Transactional(readOnly = true)
    public Environment get(Project project, String key) {
        return environments.findByProjectAndKey(project, key)
            .orElseThrow(() -> new ResourceNotFoundException("Environment '" + key + "' not found"));
    }

    /** Creates an environment and a disabled config for every existing flag in the project. */
    @Transactional
    public Environment create(Project project, String key, String name, String actor) {
        Slug.requireValid(key, "key");
        if (environments.findByProjectAndKey(project, key).isPresent()) {
            throw new ConflictException("An environment with key '" + key + "' already exists in this project");
        }
        Environment environment = environments.save(new Environment(project, key, name));
        flags.ensureConfigsFor(environment);
        audit.record(AuditEntityType.ENVIRONMENT, project.getId(), environment.getId(), key,
            AuditAction.CREATE, actor, null, fields("name", environment.getName()));
        snapshots.evictAfterCommit(project.getId());
        return environment;
    }

    @Transactional
    public Environment update(Project project, String key, String name, String actor) {
        Environment environment = get(project, key);
        String before = environment.getName();
        if (name != null) {
            environment.rename(name);
        }
        if (!before.equals(environment.getName())) {
            audit.record(AuditEntityType.ENVIRONMENT, project.getId(), environment.getId(), key,
                AuditAction.UPDATE, actor, fields("name", before), fields("name", environment.getName()));
        }
        return environment;
    }

    @Transactional
    public void delete(Project project, String key, String actor) {
        Environment environment = get(project, key);
        audit.record(AuditEntityType.ENVIRONMENT, project.getId(), environment.getId(), key,
            AuditAction.DELETE, actor, fields("name", environment.getName()), null);
        environments.delete(environment);
        snapshots.evictAfterCommit(project.getId());
    }
}
