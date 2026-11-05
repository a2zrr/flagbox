package dev.a2zrr.flagbox.apikey;

import dev.a2zrr.flagbox.environment.Environment;
import dev.a2zrr.flagbox.project.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByProjectAndEnvironment(Project project, Environment environment);
}
