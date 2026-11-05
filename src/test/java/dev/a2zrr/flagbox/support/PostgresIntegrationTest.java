package dev.a2zrr.flagbox.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for integration tests.
 *
 * <p>The container is started once per test JVM and is deliberately not stopped
 * after each test class: Spring caches application contexts across classes, and
 * a cached context keeps pointing at this container's connection details.
 * Testcontainers' Ryuk reaper removes it when the JVM exits.
 */
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }
}
