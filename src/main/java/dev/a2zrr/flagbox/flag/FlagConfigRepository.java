package dev.a2zrr.flagbox.flag;

import dev.a2zrr.flagbox.environment.Environment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagConfigRepository extends JpaRepository<FlagConfig, Long> {

    Optional<FlagConfig> findByFlagAndEnvironment(Flag flag, Environment environment);

    List<FlagConfig> findByEnvironment(Environment environment);
}
