package dev.a2zrr.flagbox.flag;

import dev.a2zrr.flagbox.environment.Environment;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagConfigRepository extends JpaRepository<FlagConfig, Long> {

    Optional<FlagConfig> findByFlagAndEnvironment(Flag flag, Environment environment);

    Page<FlagConfig> findByEnvironment(Environment environment, Pageable pageable);
}
