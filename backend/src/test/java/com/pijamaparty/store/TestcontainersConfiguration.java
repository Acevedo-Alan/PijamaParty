package com.pijamaparty.store;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * Same image and RNF-21 behavior settings as deploy/docker-compose.dev.yml. shared_buffers is left out: it is
     * sizing, and the test JVM may run several containers at once.
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withCommand("postgres",
                "-c", "max_connections=30",
                "-c", "statement_timeout=10s",
                "-c", "idle_in_transaction_session_timeout=30s");
    }
}
