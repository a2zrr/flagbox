package dev.a2zrr.flagbox.flag;

import dev.a2zrr.flagbox.environment.Environment;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlagConfigRepository extends JpaRepository<FlagConfig, Long> {

    Optional<FlagConfig> findByFlagAndEnvironment(Flag flag, Environment environment);

    Page<FlagConfig> findByEnvironment(Environment environment, Pageable pageable);

    /** All configs of non-archived flags in a project, loaded in one query for the evaluation cache. */
    @Query("select c from FlagConfig c join fetch c.flag f join fetch c.environment e "
        + "where f.project.id = :projectId and f.archived = false")
    List<FlagConfig> findActiveByProjectId(@Param("projectId") Long projectId);
}
