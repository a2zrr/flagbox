package dev.a2zrr.flagbox.project;

import dev.a2zrr.flagbox.common.Slug;
import dev.a2zrr.flagbox.environment.Environment;
import dev.a2zrr.flagbox.environment.EnvironmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final EnvironmentRepository environments;

    public ProjectService(ProjectRepository projects, EnvironmentRepository environments) {
        this.projects = projects;
        this.environments = environments;
    }

    /**
     * Creates a project together with the default "development" and "production"
     * environments, in one transaction.
     *
     * @throws IllegalArgumentException if the key or name is invalid
     * @throws IllegalStateException    if a project with this key already exists
     */
    @Transactional
    public Project create(String key, String name, String description) {
        Slug.requireValid(key, "key");
        if (projects.existsByKey(key)) {
            throw new IllegalStateException("A project with key '" + key + "' already exists");
        }
        Project project = projects.save(new Project(key, name, description));
        environments.save(new Environment(project, "development", "Development"));
        environments.save(new Environment(project, "production", "Production"));
        return project;
    }
}
