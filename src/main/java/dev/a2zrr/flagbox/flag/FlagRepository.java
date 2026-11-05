package dev.a2zrr.flagbox.flag;

import dev.a2zrr.flagbox.project.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagRepository extends JpaRepository<Flag, Long> {

    Optional<Flag> findByProjectAndKey(Project project, String key);

    boolean existsByProjectAndKey(Project project, String key);

    List<Flag> findAllByProject(Project project);

    Page<Flag> findByProject(Project project, Pageable pageable);

    Page<Flag> findByProjectAndArchived(Project project, boolean archived, Pageable pageable);
}
