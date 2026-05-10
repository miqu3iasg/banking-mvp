package com.miqu3iasg.banking.auth.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Testcontainers configuration used by integration and E2E tests. */
@TestConfiguration
public class TestContainersConfig {

    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("banking_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        POSTGRESQL_CONTAINER.start();
        REDIS_CONTAINER.start();
    }

    @Bean
    public PostgreSQLContainer<?> postgresqlContainer() {
        return POSTGRESQL_CONTAINER;
    }

    @Bean
    public GenericContainer<?> redisContainer() {
        return REDIS_CONTAINER;
    }
}
