package io.github.humphreymahlangu.votetrust.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DeploymentArtifactTest {

    @Test
    void dockerfileBuildsRuntimeImageAsNonRootWithReadinessHealthcheck() throws IOException {
        String dockerfile = read("Dockerfile");

        assertThat(dockerfile).contains("FROM eclipse-temurin:21-jdk-jammy AS build");
        assertThat(dockerfile).contains("FROM eclipse-temurin:21-jre-jammy");
        assertThat(dockerfile).contains("USER votetrust");
        assertThat(dockerfile).contains("HEALTHCHECK");
        assertThat(dockerfile).contains("/actuator/health/readiness");
        assertThat(dockerfile).contains("org.opencontainers.image.title");
    }

    @Test
    void productionPackagingExcludesTestConfigurationAndRepositoryOnlyAssets() throws IOException {
        assertThat(Path.of("src/main/resources/application-test.properties")).doesNotExist();
        assertThat(Path.of("src/test/resources/application-test.properties")).exists();
        assertThat(Files.readAllLines(Path.of(".dockerignore"))).containsExactly(
                "**",
                "!Dockerfile",
                "!.mvn/",
                "!.mvn/**",
                "!mvnw",
                "!pom.xml",
                "!src/",
                "!src/main/",
                "!src/main/**");
    }

    @Test
    void composeRequiresSecretsAndRunsApiWithContainerHardening() throws IOException {
        String compose = read("compose.yaml");

        assertThat(compose).contains("POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}");
        assertThat(compose).contains("VOTETRUST_JWT_SECRET: ${VOTETRUST_JWT_SECRET:?VOTETRUST_JWT_SECRET is required}");
        assertThat(compose).contains("VOTETRUST_ID_HASH_PEPPER: ${VOTETRUST_ID_HASH_PEPPER:?VOTETRUST_ID_HASH_PEPPER is required}");
        assertThat(compose).contains("VOTETRUST_VOTE_CREDENTIAL_PEPPER: ${VOTETRUST_VOTE_CREDENTIAL_PEPPER:?VOTETRUST_VOTE_CREDENTIAL_PEPPER is required}");
        assertThat(compose).contains("read_only: true");
        assertThat(compose).contains("no-new-privileges:true");
        assertThat(compose).contains("cap_drop:");
        assertThat(compose).contains("/actuator/health/readiness");
    }

    @Test
    void applicationConfigurationHasNoHardcodedDatasourceSecretsAndSupportsGracefulShutdown() throws IOException {
        String applicationProperties = read("src/main/resources/application.properties");

        assertThat(applicationProperties).contains(
                "spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://${SPRING_DATASOURCE_HOST:localhost}:${SPRING_DATASOURCE_PORT:5432}/${SPRING_DATASOURCE_NAME:votetrust}}");
        assertThat(applicationProperties).contains("spring.datasource.username=${SPRING_DATASOURCE_USERNAME}");
        assertThat(applicationProperties).contains("spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}");
        assertThat(applicationProperties).doesNotContainPattern("SPRING_DATASOURCE_(USERNAME|PASSWORD):");
        assertThat(applicationProperties).contains("server.shutdown=graceful");
        assertThat(applicationProperties).contains("server.forward-headers-strategy=${SERVER_FORWARD_HEADERS_STRATEGY:native}");
        assertThat(applicationProperties).contains("management.endpoint.health.group.readiness.include=readinessState,db");
    }

    @Test
    void ciVerifiesTestsPackagingComposeAndContainerBuild() throws IOException {
        String ci = read(".github/workflows/ci.yml");

        assertThat(ci).contains("uses: actions/checkout@v7");
        assertThat(ci).contains("uses: actions/setup-java@v5");
        assertThat(ci).contains("postgres:16-alpine");
        assertThat(ci).contains("VOTETRUST_TEST_DATASOURCE_URL: jdbc:postgresql://localhost:5432/votetrust_test");
        assertThat(ci).contains("chmod +x ./mvnw");
        assertThat(ci).contains("./mvnw -B clean verify");
        assertThat(ci).contains("actions/upload-artifact@v7");
        assertThat(ci).contains("docker compose --env-file .env.example config");
        assertThat(ci).contains("docker build -t votetrust-api:ci .");
        assertThat(ci).containsSubsequence(
                "working-directory: frontend",
                "uses: actions/setup-node@v7",
                "npm ci",
                "npm run lint",
                "npm test",
                "npm run build");
    }

    @Test
    void deploymentChecklistDocumentsRequiredInputsAndSmokeChecks() throws IOException {
        String deployment = read("DEPLOYMENT.md");

        assertThat(deployment).contains("SPRING_DATASOURCE_URL");
        assertThat(deployment).contains("VOTETRUST_JWT_SECRET");
        assertThat(deployment).contains("docker compose --env-file .env config");
        assertThat(deployment).contains("Render Backend Deployment");
        assertThat(deployment).contains("VOTETRUST_CORS_ALLOWED_ORIGINS");
        assertThat(deployment).contains("/actuator/health/readiness");
        assertThat(deployment).contains("CI Deployment Gates");
    }

    @Test
    void renderBlueprintDeploysDockerImageWithDatabaseSecretsAndGatedAutoDeploy() throws IOException {
        String blueprint = read("render.yaml");

        assertThat(blueprint).contains("runtime: docker");
        assertThat(blueprint).contains("healthCheckPath: /actuator/health/readiness");
        assertThat(blueprint).contains("autoDeployTrigger: checksPass");
        assertThat(blueprint).contains("fromDatabase:");
        assertThat(blueprint).containsSubsequence(
                "- key: VOTETRUST_JWT_SECRET", "generateValue: true",
                "- key: VOTETRUST_ID_HASH_PEPPER", "generateValue: true",
                "- key: VOTETRUST_VOTE_CREDENTIAL_PEPPER", "generateValue: true");
        assertThat(blueprint).containsSubsequence("- key: VOTETRUST_CORS_ALLOWED_ORIGINS", "sync: false");
        assertThat(blueprint).containsSubsequence("- key: VOTETRUST_ADMIN_BOOTSTRAP_ENABLED", "value: \"false\"");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
