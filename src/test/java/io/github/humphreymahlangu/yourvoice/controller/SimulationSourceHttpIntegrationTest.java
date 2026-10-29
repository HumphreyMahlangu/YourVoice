package io.github.humphreymahlangu.yourvoice.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.humphreymahlangu.yourvoice.dto.CreateSimulationVoterRequest;
import io.github.humphreymahlangu.yourvoice.dto.LocationSelection;
import io.github.humphreymahlangu.yourvoice.entity.AccountRole;
import io.github.humphreymahlangu.yourvoice.entity.UserAccount;
import io.github.humphreymahlangu.yourvoice.entity.VerificationStatus;
import io.github.humphreymahlangu.yourvoice.repository.UserAccountRepository;
import io.github.humphreymahlangu.yourvoice.security.JwtService;
import io.github.humphreymahlangu.yourvoice.support.DatabaseCleaner;
import io.github.humphreymahlangu.yourvoice.support.PostgreSqlTestContainerSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SimulationSourceHttpIntegrationTest extends PostgreSqlTestContainerSupport {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountRepository users;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @Test
    void municipalityOnlyRecordIsMappedBeforeTheTransactionCloses() throws Exception {
        DatabaseCleaner.clean(jdbc);
        var admin = users.save(new UserAccount("http-admin@example.test", "unused-test-hash", AccountRole.ADMIN, true));
        var headers = new HttpHeaders(); headers.setBearerAuth(jwt.generateAccessToken(admin).token());
        JsonNode province = json.readTree(http.postForEntity("/api/v1/admin/geography/provinces", new HttpEntity<>(Map.of("name", "Fictional HTTP Province"), headers), String.class).getBody());
        JsonNode municipality = json.readTree(http.postForEntity("/api/v1/admin/geography/municipalities", new HttpEntity<>(Map.of("name", "Fictional HTTP Town", "provinceId", province.get("id").asText()), headers), String.class).getBody());
        var request = new CreateSimulationVoterRequest("SIM-HTTP-MUNICIPALITY", true, "Fictional HTTP Voter", "8001015009087",
                VerificationStatus.VERIFIED, VerificationStatus.VERIFIED,
                new LocationSelection(null, UUID.fromString(municipality.get("id").asText()), null), "Fictional HTTP source fixture");
        var response = http.postForEntity("/api/v1/admin/profile-verification/records", new HttpEntity<>(request, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.path("location").path("province").asText()).isEqualTo("Fictional HTTP Province");
        assertThat(body.path("location").path("municipality").asText()).isEqualTo("Fictional HTTP Town");
        assertThat(body.path("location").path("wardNumber").isNull()).isTrue();
        assertThat(body.has("southAfricanIdNumber")).isFalse(); assertThat(body.has("idNumberHash")).isFalse();
    }
}
