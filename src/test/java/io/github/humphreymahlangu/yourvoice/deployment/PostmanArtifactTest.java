package io.github.humphreymahlangu.yourvoice.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.humphreymahlangu.yourvoice.entity.IdDocumentType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PostmanArtifactTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void postmanCollectionIsValidAndCoversCoreVotingFlow() throws IOException {
        JsonNode collection = objectMapper.readTree(Path.of("postman/YourVoice.postman_collection.json").toFile());
        String collectionJson = collection.toString();

        assertThat(collection.at("/info/schema").asText())
                .isEqualTo("https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        assertThat(collection.at("/info/name").asText()).isEqualTo("YourVoice API");
        assertThat(collectionJson).contains(
                "/api/v1/admin/bootstrap",
                "/api/v1/admin/voting-districts",
                "/api/v1/elections/{{electionId}}/registrations",
                "/api/v1/elections/{{electionId}}/contests/{{contestId}}/credentials",
                "/api/v1/ballots",
                "/api/v1/elections/{{electionId}}/contests/{{contestId}}/audit",
                "/api/v1/admin/security-audit-events?limit=25"
        );
        assertThat(collectionJson).contains(
                "BLANK_BALLOT",
                "SPOILT_BALLOT",
                "{{idDocumentType}}",
                "{{adminToken}}",
                "{{voterToken}}",
                "{{votingCredential}}"
        );
        assertThat(collectionJson).doesNotContain("SOUTH_AFRICAN_ID");
    }

    @Test
    void postmanEnvironmentContainsRequiredVariables() throws IOException {
        JsonNode environment = objectMapper.readTree(Path.of("postman/YourVoice.local.postman_environment.json").toFile());
        Set<String> keys = new HashSet<>();
        environment.get("values").forEach(value -> keys.add(value.get("key").asText()));

        assertThat(environment.get("name").asText()).isEqualTo("YourVoice Local");
        assertThat(keys).contains(
                "baseUrl",
                "adminBootstrapToken",
                "adminEmail",
                "adminPassword",
                "voterEmail",
                "voterPassword",
                "voterSouthAfricanIdNumber",
                "votingDistrictId",
                "electionId",
                "contestId",
                "optionAId",
                "blankOptionId",
                "spoiltOptionId",
                "adminToken",
                "voterToken",
                "votingCredential"
        );
    }

    @Test
    void postmanCollectionVariablesExistInEnvironment() throws IOException {
        String collection = read("postman/YourVoice.postman_collection.json");
        JsonNode environment = objectMapper.readTree(Path.of("postman/YourVoice.local.postman_environment.json").toFile());

        assertThat(environmentKeys(environment)).containsAll(referencedVariables(collection));
    }

    @Test
    void verifiedPostmanCollectionIsValidAndCoversEndToEndVotingFlow() throws IOException {
        JsonNode collection = objectMapper.readTree(Path.of("postman/YourVoice.verified.postman_collection.json").toFile());
        String collectionJson = collection.toString();

        assertThat(collection.at("/info/schema").asText())
                .isEqualTo("https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        assertThat(collection.at("/info/name").asText()).isEqualTo("YourVoice API - Verified Local Flow");
        assertThat(collectionJson).contains(
                "/actuator/health",
                "/api-docs",
                "/api/v1/admin/bootstrap",
                "/api/v1/auth/login",
                "/api/v1/auth/me",
                "/api/v1/admin/voting-districts",
                "/api/v1/admin/elections",
                "/api/v1/elections/{{electionId}}/registrations",
                "/api/v1/elections/{{electionId}}/contests/{{contestId}}/ballot-key",
                "/api/v1/elections/{{electionId}}/contests/{{contestId}}/credentials",
                "/api/v1/ballots",
                "/api/v1/elections/{{electionId}}/contests/{{contestId}}/results",
                "/api/v1/elections/{{electionId}}/contests/{{contestId}}/audit",
                "/api/v1/elections/{{electionId}}/contests/{{contestId}}/ledger",
                "/api/v1/admin/security-audit-events?limit=25",
                "/api/v1/admin/elections/{{electionId}}/lifecycle-events"
        );
        assertThat(collectionJson).contains(
                "MUNICIPAL",
                "MUNICIPAL_WARD",
                "PARTY",
                "BLANK_BALLOT",
                "SPOILT_BALLOT",
                "{{idDocumentType}}",
                "Timing Check - Registration Window Open",
                "Timing Check - Voting Window Open",
                "Timing Check - Voting Window Closed",
                "Confirm Election Is REGISTRATION_OPEN",
                "Confirm Election Is VOTING_OPEN",
                "Confirm Election Is COMPLETED",
                "{{blindedToken}}",
                "{{votingCredentialSignature}}",
                "Unblinded signature verifies against the contest key"
        );
        assertThat(collectionJson).doesNotContain(
                "SOUTH_AFRICAN_ID",
                "Transition Election to REGISTRATION_OPEN",
                "Transition Election to VOTING_OPEN",
                "Transition Election to COMPLETED"
        );
    }

    @Test
    void verifiedPostmanEnvironmentContainsRequiredVariables() throws IOException {
        JsonNode environment = objectMapper.readTree(Path.of("postman/YourVoice.verified-local.postman_environment.json").toFile());

        assertThat(environment.get("name").asText()).isEqualTo("YourVoice Verified Local");
        assertThat(environmentKeys(environment)).contains(
                "baseUrl",
                "adminBootstrapToken",
                "adminEmail",
                "adminPassword",
                "voterEmail",
                "voterPassword",
                "voterSouthAfricanIdNumber",
                "configurationGraceSeconds",
                "registrationGraceSeconds",
                "votingDurationSeconds",
                "idDocumentType",
                "votingDistrictId",
                "electionId",
                "contestId",
                "optionAId",
                "optionBId",
                "blankOptionId",
                "spoiltOptionId",
                "adminToken",
                "voterToken",
                "votingCredential",
                "votingCredentialSignature",
                "ballotKeyModulus",
                "ballotKeyExponent",
                "ballotTokenDomain",
                "blindingFactor",
                "blindedToken"
        );
        assertThat(environment.get("values"))
                .anySatisfy(value -> {
                    assertThat(value.get("key").asText()).isEqualTo("idDocumentType");
                    assertThat(value.get("value").asText()).isEqualTo(IdDocumentType.SMART_ID_CARD.name());
                });
    }

    @Test
    void verifiedPostmanCollectionVariablesExistInEnvironment() throws IOException {
        String collection = read("postman/YourVoice.verified.postman_collection.json");
        JsonNode environment = objectMapper.readTree(Path.of("postman/YourVoice.verified-local.postman_environment.json").toFile());

        assertThat(environmentKeys(environment)).containsAll(referencedVariables(collection));
    }

    private Set<String> environmentKeys(JsonNode environment) {
        Set<String> environmentKeys = new HashSet<>();
        environment.get("values").forEach(value -> environmentKeys.add(value.get("key").asText()));
        return environmentKeys;
    }

    private Set<String> referencedVariables(String collection) {
        Set<String> referencedVariables = new HashSet<>();
        Matcher matcher = Pattern.compile("\\{\\{([^}]+)}}").matcher(collection);
        while (matcher.find()) {
            referencedVariables.add(matcher.group(1));
        }
        return referencedVariables;
    }

    private String read(String relativePath) throws IOException {
        return java.nio.file.Files.readString(Path.of(relativePath));
    }
}
