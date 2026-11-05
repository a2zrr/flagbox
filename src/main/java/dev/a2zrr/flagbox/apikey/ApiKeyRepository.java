package dev.a2zrr.flagbox.apikey;

import dev.a2zrr.flagbox.environment.Environment;
import dev.a2zrr.flagbox.project.Project;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    Page<ApiKey> findByProjectAndEnvironment(Project project, Environment environment, Pageable pageable);

    Optional<ApiKey> findByIdAndProjectAndEnvironment(Long id, Project project, Environment environment);
}
