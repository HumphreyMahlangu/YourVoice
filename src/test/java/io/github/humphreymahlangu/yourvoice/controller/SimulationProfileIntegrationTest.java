package io.github.humphreymahlangu.yourvoice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.humphreymahlangu.yourvoice.entity.AccountRole;
import io.github.humphreymahlangu.yourvoice.entity.IdDocumentType;
import io.github.humphreymahlangu.yourvoice.entity.UserAccount;
import io.github.humphreymahlangu.yourvoice.entity.VoterProfile;
import io.github.humphreymahlangu.yourvoice.repository.UserAccountRepository;
import io.github.humphreymahlangu.yourvoice.repository.VoterProfileRepository;
import io.github.humphreymahlangu.yourvoice.security.IdentityHashService;
import io.github.humphreymahlangu.yourvoice.security.JwtService;
import io.github.humphreymahlangu.yourvoice.service.RegistrySimulationVerificationSource;
import io.github.humphreymahlangu.yourvoice.service.VerificationRetentionService;
import io.github.humphreymahlangu.yourvoice.support.DatabaseCleaner;
import io.github.humphreymahlangu.yourvoice.support.PostgreSqlTestContainerSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SimulationProfileIntegrationTest extends PostgreSqlTestContainerSupport {
    static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountRepository users;
    @Autowired VoterProfileRepository profiles;
    @Autowired IdentityHashService hashes;
    @Autowired JwtService jwt;
    @Autowired MutableClock clock;
    @Autowired VerificationRetentionService retention;
    @SpyBean RegistrySimulationVerificationSource source;
    String admin;
    String voter;
    UUID userId;
    String district;
    String secondDistrict;

    @BeforeEach void setup() throws Exception {
        DatabaseCleaner.clean(jdbc); clock.now = NOW; reset(source);
        admin = token(AccountRole.ADMIN, "admin@example.test");
        voter = token(AccountRole.VOTER, "voter@example.test");
        profiles.save(new VoterProfile(users.findById(userId).orElseThrow(), "Fictional Voter", hashes.hashSouthAfricanIdNumber("8001015009087"),
                IdDocumentType.SMART_ID_CARD, LocalDate.of(1980, 1, 1), null));
        district = area("SIM-A-1", "Demo A", "Demo Town", 1);
        secondDistrict = area("SIM-A-2", "Demo A", "Demo Town", 2);
    }
    String token(AccountRole role, String email) {
        UserAccount user = users.save(new UserAccount(email, "unused-test-hash", role, true));
        userId = user.getId(); return jwt.generateAccessToken(user).token();
    }
    String area(String code, String province, String municipality, int ward) throws Exception {
        return body(mvc.perform(post("/api/v1/admin/voting-districts").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("code", code, "name", "Fictional " + code,
                        "province", province, "municipality", municipality, "wardNumber", ward)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
    }
    JsonNode body(String content) throws Exception { return json.readTree(content); }
    void record(String reference, String area) throws Exception {
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("reference", reference); fields.put("fictional", true); fields.put("fullName", "Fictional Voter");
        fields.put("southAfricanIdNumber", "8001015009087"); fields.put("identityStatus", "VERIFIED");
        fields.put("locationStatus", area == null ? "UNVERIFIED" : "VERIFIED");
        fields.put("location", area == null ? null : Map.of("votingDistrictId", area)); fields.put("reason", "Explicit fictional source check");
        mvc.perform(post("/api/v1/admin/profile-verification/records").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(fields))).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FICTIONAL_SIMULATION"))
                .andExpect(jsonPath("$.southAfricanIdNumber").doesNotExist()).andExpect(jsonPath("$.idNumberHash").doesNotExist());
    }
    void verify() throws Exception {
        mvc.perform(post("/api/v1/me/profile/verification").header("Authorization", "Bearer " + voter)).andExpect(status().isOk())
                .andExpect(jsonPath("$.identityStatus").value("VERIFIED"));
    }
    String correction(String area) throws Exception {
        return body(mvc.perform(post("/api/v1/me/profile/corrections").header("Authorization", "Bearer " + voter)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("location", Map.of("votingDistrictId", area), "reason", "Fictional area needs correction"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText();
    }
    @Test void existingProfileNeedsAnExplicitCheckAndNationalIdentityNeedsNoArea() throws Exception {
        mvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + voter)).andExpect(status().isOk())
                .andExpect(jsonPath("$.identityStatus").value("UNVERIFIED"));
        mvc.perform(post("/api/v1/me/profile/verification").header("Authorization", "Bearer " + voter))
                .andExpect(jsonPath("$.identityStatus").value("PENDING"));
        record("SIM-NATIONAL", null);
        mvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + voter)).andExpect(jsonPath("$.identityStatus").value("PENDING"));
        verify();
        mvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + voter)).andExpect(jsonPath("$.location.provinceId").isEmpty());
    }
    @Test void sourceFailureIsHonestAndVotersCannotConfigureOrReviewTheSource() throws Exception {
        doReturn(false).when(source).available();
        mvc.perform(post("/api/v1/me/profile/verification").header("Authorization", "Bearer " + voter)).andExpect(status().isOk())
                .andExpect(jsonPath("$.identityStatus").value("UNAVAILABLE")).andExpect(jsonPath("$.sourceAvailable").value(false));
        mvc.perform(get("/api/v1/admin/profile-verification/records").header("Authorization", "Bearer " + voter)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/profile-verification/audit")).andExpect(status().isUnauthorized());
    }
    @Test void correctionRequiresMatchingSourceAndDoesNotBecomeEffectiveEarly() throws Exception {
        record("SIM-INITIAL", district); verify();
        String request = correction(secondDistrict);
        mvc.perform(post("/api/v1/admin/profile-verification/corrections/" + request + "/review").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("approve", true, "reason", "Reviewed fictional source", "sourceReference", "SIM-INITIAL", "effectiveAt", NOW.plusSeconds(60).toString()))))
                .andExpect(status().isUnprocessableEntity());
        record("SIM-CORRECTED", secondDistrict);
        mvc.perform(post("/api/v1/me/profile/verification").header("Authorization", "Bearer " + voter)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/profile-verification/corrections/" + request + "/review").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("approve", true, "reason", "Reviewed fictional source", "sourceReference", "SIM-CORRECTED", "effectiveAt", NOW.plusSeconds(60).toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + voter)).andExpect(jsonPath("$.location.votingDistrictId").value(district));
        clock.now = NOW.plusSeconds(60);
        mvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + voter)).andExpect(jsonPath("$.location.votingDistrictId").value(secondDistrict));
        mvc.perform(get("/api/v1/me/profile/corrections").header("Authorization", "Bearer " + voter)).andExpect(jsonPath("$[0].status").value("APPLIED"));
        clock.now = NOW.plusSeconds(91L * 86400); retention.redactOldReasons();
        assertThat(jdbc.queryForObject("select request_reason from location_correction_requests where id = ?", String.class, UUID.fromString(request))).isEqualTo("[redacted]");
    }
    @Test void rejectionIsVisibleOnlyToOwnerAndAllowsAnotherRequest() throws Exception {
        record("SIM-INITIAL", district); verify(); String request = correction(secondDistrict);
        mvc.perform(post("/api/v1/admin/profile-verification/corrections/" + request + "/review").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":false,\"reason\":\"Fictional evidence does not match\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(get("/api/v1/me/profile/corrections").header("Authorization", "Bearer " + voter)).andExpect(jsonPath("$[0].reviewReason").value("Fictional evidence does not match"));
        String other = token(AccountRole.VOTER, "other@example.test");
        profiles.save(new VoterProfile(users.findById(userId).orElseThrow(), "Other Fictional Voter", "0".repeat(64), IdDocumentType.SMART_ID_CARD, LocalDate.of(1980, 1, 1), null));
        mvc.perform(get("/api/v1/me/profile/corrections").header("Authorization", "Bearer " + other)).andExpect(jsonPath("$.length()").value(0));
        correction(secondDistrict);
    }
    @Test void concurrentRequestsQueueOnlyOneCorrectionAndReferencedAreaCannotChange() throws Exception {
        record("SIM-INITIAL", district); verify();
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> action = () -> mvc.perform(post("/api/v1/me/profile/corrections").header("Authorization", "Bearer " + voter)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("location", Map.of("votingDistrictId", secondDistrict), "reason", "Fictional correction"))))
                    .andReturn().getResponse().getStatus();
            var outcomes = pool.invokeAll(List.of(action, action)).stream().map(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } }).toList();
            assertThat(outcomes).containsExactlyInAnyOrder(200, 409);
        }
        mvc.perform(put("/api/v1/admin/voting-districts/" + district).header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"SIM-A-1\",\"name\":\"Fictional area\",\"province\":\"Demo B\",\"municipality\":\"Demo Town\",\"wardNumber\":1}"))
                .andExpect(status().isConflict());
    }
    static class MutableClock extends Clock {
        volatile Instant now = NOW;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary MutableClock verificationClock() { return new MutableClock(); }
    }
}
