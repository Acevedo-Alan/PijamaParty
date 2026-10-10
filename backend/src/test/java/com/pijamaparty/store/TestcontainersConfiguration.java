package com.pijamaparty.store;

import java.nio.file.Path;
import org.springframework.boot.autoconfigure.flyway.FlywayConnectionDetails;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL as in production: the same image, the RNF-21 limits and the real role script
 * (deploy/db/init/01-roles.sh). Flyway migrates as {@code tienda_owner} and the application connects as
 * {@code tienda_app}, so RLS and privileges are tested as they will run (RNF-27, RNF-29.2). The connection details
 * are beans that depend on the container, so they resolve whenever they are first needed.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String OWNER_PASSWORD = "owner-test-only";
    public static final String APP_PASSWORD = "app-test-only";
    public static final String BACKUP_PASSWORD = "backup-test-only";

    /** shared_buffers is left out: it is sizing, and the test JVM may run several containers at once. */
    @Bean
    PostgreSQLContainer<?> postgresContainer() {
        var container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withCommand("postgres",
                "-c", "max_connections=30",
                "-c", "statement_timeout=10s",
                "-c", "idle_in_transaction_session_timeout=30s")
            .withEnv("DB_OWNER_PASSWORD", OWNER_PASSWORD)
            .withEnv("DB_APP_PASSWORD", APP_PASSWORD)
            .withEnv("DB_BACKUP_PASSWORD", BACKUP_PASSWORD)
            .withCopyFileToContainer(
                MountableFile.forHostPath(Path.of("..", "deploy", "db", "init", "01-roles.sh"), 0755),
                "/docker-entrypoint-initdb.d/01-roles.sh");
        container.start();
        return container;
    }

    @Bean
    JdbcConnectionDetails applicationConnection(PostgreSQLContainer<?> postgres) {
        return new AppConnection(postgres.getJdbcUrl(), "tienda_app", APP_PASSWORD);
    }

    @Bean
    FlywayConnectionDetails migrationConnection(PostgreSQLContainer<?> postgres) {
        return new MigrationConnection(postgres.getJdbcUrl(), "tienda_owner", OWNER_PASSWORD);
    }

    private record AppConnection(String getJdbcUrl, String getUsername, String getPassword)
        implements JdbcConnectionDetails {
    }

    private record MigrationConnection(String getJdbcUrl, String getUsername, String getPassword)
        implements FlywayConnectionDetails {
    }
}
