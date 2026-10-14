package io.github.humphreymahlangu.votetrust.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class PostgreSqlTestContainerSupport {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
    private static final String TEST_DATASOURCE_URL = "VOTETRUST_TEST_DATASOURCE_URL";
    private static final String TEST_DATASOURCE_USERNAME = "VOTETRUST_TEST_DATASOURCE_USERNAME";
    private static final String TEST_DATASOURCE_PASSWORD = "VOTETRUST_TEST_DATASOURCE_PASSWORD";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("votetrust_test")
            .withUsername("votetrust")
            .withPassword("votetrust");

    @DynamicPropertySource
    static void configurePostgreSql(DynamicPropertyRegistry registry) {
        String ciDatasourceUrl = System.getenv(TEST_DATASOURCE_URL);
        if (hasText(ciDatasourceUrl)) {
            registry.add("spring.datasource.url", () -> ciDatasourceUrl);
            registry.add("spring.datasource.username", () -> requiredEnv(TEST_DATASOURCE_USERNAME));
            registry.add("spring.datasource.password", () -> requiredEnv(TEST_DATASOURCE_PASSWORD));
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        } else {
            startPostgresContainer();
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        }

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    private static synchronized void startPostgresContainer() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (!hasText(value)) {
            throw new IllegalStateException("Missing required test database environment variable: " + name);
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
