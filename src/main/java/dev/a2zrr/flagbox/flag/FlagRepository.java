package dev.a2zrr.flagbox.flag;

import dev.a2zrr.flagbox.project.Project;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagRepository extends JpaRepository<Flag, Long> {

    Optional<Flag> findByProjectAndKey(Project project, String key);

    boolean existsByProjectAndKey(Project project, String key);
}
