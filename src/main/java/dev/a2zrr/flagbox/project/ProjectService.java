package dev.a2zrr.flagbox.project;

import static dev.a2zrr.flagbox.audit.AuditService.fields;

import dev.a2zrr.flagbox.audit.AuditAction;
import dev.a2zrr.flagbox.audit.AuditEntityType;
import dev.a2zrr.flagbox.audit.AuditService;
import dev.a2zrr.flagbox.common.Slug;
import dev.a2zrr.flagbox.common.error.ConflictException;
import dev.a2zrr.flagbox.common.error.ResourceNotFoundException;
import dev.a2zrr.flagbox.environment.Environment;
import dev.a2zrr.flagbox.environment.EnvironmentRepository;
import dev.a2zrr.flagbox.evaluation.FlagSnapshotCache;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private static final String[][] DEFAULT_ENVIRONMENTS = {
        {"development", "Development"},
        {"production", "Production"}
    };

    private final ProjectRepository projects;
    private final EnvironmentRepository environments;
    private final AuditService audit;
    private final FlagSnapshotCache snapshots;

    public ProjectService(ProjectRepository projects, EnvironmentRepository environments, AuditService audit,
                          FlagSnapshotCache snapshots) {
        this.projects = projects;
        this.environments = environments;
        this.audit = audit;
        this.snapshots = snapshots;
    }

    /**
     * Creates a project with the default "development" and "production" environments.
     *
     * @throws IllegalArgumentException if the key or name is invalid
     * @throws ConflictException        if a project with this key already exists
     */
    @Transactional
    public Project create(String key, String name, String description, String actor) {
        Slug.requireValid(key, "key");
        if (projects.existsByKey(key)) {
            throw new ConflictException("A project with key '" + key + "' already exists");
        }
        Project project = projects.save(new Project(key, name, description));
        audit.record(AuditEntityType.PROJECT, project.getId(), null, key, AuditAction.CREATE, actor,
            null, snapshot(project));
        for (String[] defaults : DEFAULT_ENVIRONMENTS) {
            Environment env = environments.save(new Environment(project, defaults[0], defaults[1]));
            audit.record(AuditEntityType.ENVIRONMENT, project.getId(), env.getId(), env.getKey(),
                AuditAction.CREATE, actor, null, fields("name", env.getName()));
        }
        return project;
    }

    @Transactional(readOnly = true)
    public Page<Project> list(Pageable pageable) {
        return projects.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Project get(String key) {
        return projects.findByKey(key)
            .orElseThrow(() -> new ResourceNotFoundException("Project '" + key + "' not found"));
    }

    @Transactional
    public Project update(String key, String name, String description, String actor) {
        Project project = get(key);
        Map<String, Object> before = snapshot(project);
        project.updateDetails(name, description);
        Map<String, Object> after = snapshot(project);
        if (!before.equals(after)) {
            audit.record(AuditEntityType.PROJECT, project.getId(), null, key, AuditAction.UPDATE, actor,
                before, after);
        }
        return project;
    }

    @Transactional
    public void delete(String key, String actor) {
        Project project = get(key);
        audit.record(AuditEntityType.PROJECT, project.getId(), null, key, AuditAction.DELETE, actor,
            snapshot(project), null);
        projects.delete(project);
        snapshots.evictAfterCommit(project.getId());
    }

    private static Map<String, Object> snapshot(Project project) {
        return fields("name", project.getName(), "description", project.getDescription());
    }
}
