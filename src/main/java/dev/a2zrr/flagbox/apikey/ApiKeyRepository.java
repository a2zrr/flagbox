package dev.a2zrr.flagbox.apikey;

import dev.a2zrr.flagbox.environment.Environment;
import dev.a2zrr.flagbox.project.Project;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    Page<ApiKey> findByProjectAndEnvironment(Project project, Environment environment, Pageable pageable);

    Optional<ApiKey> findByIdAndProjectAndEnvironment(Long id, Project project, Environment environment);

    boolean existsByProjectIsNullAndType(ApiKeyType type);

    Page<ApiKey> findByProjectIsNullAndType(ApiKeyType type, Pageable pageable);

    Optional<ApiKey> findByIdAndProjectIsNullAndType(Long id, ApiKeyType type);

    /**
     * Locks the active global admin keys for the rest of the transaction, so two
     * concurrent revocations cannot both see another active key and leave none.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from ApiKey k where k.project is null and k.type = :type and k.revokedAt is null")
    List<ApiKey> lockActiveGlobalKeys(@Param("type") ApiKeyType type);
}
