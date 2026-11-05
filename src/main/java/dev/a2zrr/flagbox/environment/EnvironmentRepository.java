package dev.a2zrr.flagbox.environment;

import dev.a2zrr.flagbox.project.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentRepository extends JpaRepository<Environment, Long> {

    List<Environment> findByProject(Project project);

    Optional<Environment> findByProjectAndKey(Project project, String key);
}
