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
    @Autowired io.github.humphreymahlangu.yourvoice.service.ElectionLifecycleService lifecycle;
    String mixedElection() throws Exception {
        if (jdbc.queryForObject("select count(*) from voting_districts where code='SIM-OTHER-1'", Integer.class) == 0) area("SIM-OTHER-1", "Demo A", "Demo Other Town", 1);
        if (jdbc.queryForObject("select count(*) from voting_districts where code='SIM-B-1'", Integer.class) == 0) area("SIM-B-1", "Demo B", "Demo Third Town", 1);
        Instant at = clock.now;
        String id = body(mvc.perform(post("/api/v1/admin/elections").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Fictional Mixed " + UUID.randomUUID(), "type", "MIXED",
                        "registrationStartAt", at.minusSeconds(60).toString(), "registrationEndAt", at.plusSeconds(60).toString(),
                        "votingStartAt", at.plusSeconds(120).toString(), "votingEndAt", at.plusSeconds(3600).toString()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String[][] specs = {{"National", "NATIONAL", "", "", ""}, {"Province A", "PROVINCIAL", "Demo A", "", ""},
                {"Municipal A", "MUNICIPAL_PR", "Demo A", "Demo Town", ""}, {"Ward A1", "MUNICIPAL_WARD", "Demo A", "Demo Town", "1"},
                {"Ward A2", "MUNICIPAL_WARD", "Demo A", "Demo Town", "2"}, {"Ward Other Municipality", "MUNICIPAL_WARD", "Demo A", "Demo Other Town", "1"},
                {"Ward Other Province", "MUNICIPAL_WARD", "Demo B", "Demo Third Town", "1"}};
        int order = 0;
        for (String[] spec : specs) {
            Map<String,Object> fields = new java.util.LinkedHashMap<>(); fields.put("name", spec[0]); fields.put("type", spec[1]); fields.put("displayOrder", ++order);
            fields.put("scopeProvince", spec[2].isEmpty() ? null : spec[2]); fields.put("scopeMunicipality", spec[3].isEmpty() ? null : spec[3]);
            fields.put("scopeWardNumber", spec[4].isEmpty() ? null : Integer.valueOf(spec[4]));
            String c = body(mvc.perform(post("/api/v1/admin/elections/" + id + "/contests").header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(fields))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
            for (int i = 1; i <= 2; i++) mvc.perform(post("/api/v1/admin/elections/" + id + "/contests/" + c + "/options")
                    .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("name", "Fictional Party " + i, "optionType", "PARTY", "displayOrder", i)))).andExpect(status().isCreated());
        }
        lifecycle.advanceElection(UUID.fromString(id)); return id;
    }
    JsonNode ballots(String election) throws Exception {
        return body(mvc.perform(get("/api/v1/me/ballots").param("electionId", election).header("Authorization", "Bearer " + voter))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    void register(String election) throws Exception {
        mvc.perform(post("/api/v1/elections/" + election + "/registrations").header("Authorization", "Bearer " + voter)
                .contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isCreated()).andExpect(jsonPath("$.identityVerified").value(true));
    }
    void approve(String request, String reference) throws Exception {
        mvc.perform(post("/api/v1/admin/profile-verification/corrections/" + request + "/review").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("approve", true, "reason", "Matched fictional source",
                        "sourceReference", reference, "effectiveAt", clock.now.toString())))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + voter)).andExpect(status().isOk());
    }
    @Test void nationalOnlyOnboardingAndMixedRegistrationNeedNoLocalAttributes() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Fictional National Account","email":"national@example.test","password":"FictionalPassword2026!",
                 "southAfricanIdNumber":"1001015000083","idDocumentType":"SMART_ID_CARD","verified":true}
                """)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from voter_profiles where voting_district_id is null", Integer.class)).isEqualTo(2);
        record("SIM-NATIONAL", null); verify(); String election = mixedElection();
        JsonNode found = ballots(election); assertThat(found.size()).isEqualTo(7);
        for (JsonNode row : found) assertThat(row.get("eligible").asBoolean()).isEqualTo(row.get("contest").get("type").asText().equals("NATIONAL"));
        register(election);
        assertThat(jdbc.queryForObject("select registered_province from election_registrations", String.class)).isNull();
        clock.now = NOW.plusSeconds(121); lifecycle.advanceElection(UUID.fromString(election));
        found = ballots(election);
        String national = found.get(0).get("contest").get("id").asText();
        mvc.perform(post("/api/v1/elections/" + election + "/contests/" + national + "/credentials").header("Authorization", "Bearer " + voter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blindedToken\":\"Ag\"}")).andExpect(status().isCreated());
        String provincial = found.get(1).get("contest").get("id").asText();
        mvc.perform(post("/api/v1/elections/" + election + "/contests/" + provincial + "/credentials").header("Authorization", "Bearer " + voter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blindedToken\":\"Ag\"}")).andExpect(status().isUnprocessableEntity());
    }
    @Test void unverifiedAndUnavailableProfilesCannotRegisterOrObtainCredentials() throws Exception {
        String election = mixedElection();
        mvc.perform(post("/api/v1/elections/" + election + "/registrations").header("Authorization", "Bearer " + voter)
                .contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isUnprocessableEntity());
        for (JsonNode row : ballots(election)) assertThat(row.get("eligible").asBoolean()).isFalse();
        record("SIM-IDENTITY", district); verify(); register(election);
        doReturn(false).when(source).available();
        for (JsonNode row : ballots(election)) assertThat(row.get("canVote").asBoolean()).isFalse();
        clock.now = NOW.plusSeconds(121); lifecycle.advanceElection(UUID.fromString(election));
        String contest = ballots(election).get(0).get("contest").get("id").asText();
        mvc.perform(post("/api/v1/elections/" + election + "/contests/" + contest + "/credentials").header("Authorization", "Bearer " + voter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blindedToken\":\"Ag\"}")).andExpect(status().isServiceUnavailable());
    }
    @Test void correctionsBeforeAndAfterIssuanceNeverRewriteAnExistingElectionSnapshot() throws Exception {
        String other = area("SIM-OTHER-1", "Demo A", "Demo Other Town", 1);
        area("SIM-B-1", "Demo B", "Demo Third Town", 1);
        record("SIM-OLD", district); verify(); String election = mixedElection(); register(election);
        record("SIM-A2", secondDistrict); String correction = correction(secondDistrict); approve(correction, "SIM-A2");
        JsonNode rows = ballots(election);
        assertThat(rows.get(3).get("eligible").asBoolean()).isTrue();
        assertThat(rows.get(4).get("eligible").asBoolean()).isFalse();
        String nextElection = mixedElection(); register(nextElection);
        assertThat(ballots(nextElection).get(4).get("eligible").asBoolean()).isTrue();
        clock.now = NOW.plusSeconds(121); lifecycle.advanceElection(UUID.fromString(election));
        String oldWard = ballots(election).get(3).get("contest").get("id").asText();
        String newWard = ballots(election).get(4).get("contest").get("id").asText();
        mvc.perform(post("/api/v1/elections/" + election + "/contests/" + oldWard + "/credentials").header("Authorization", "Bearer " + voter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blindedToken\":\"Ag\"}")).andExpect(status().isCreated());
        record("SIM-OTHER", other); approve(correction(other), "SIM-OTHER");
        mvc.perform(post("/api/v1/elections/" + election + "/contests/" + oldWard + "/credentials").header("Authorization", "Bearer " + voter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blindedToken\":\"Ag\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/elections/" + election + "/contests/" + oldWard + "/credentials").header("Authorization", "Bearer " + voter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blindedToken\":\"Aw\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/elections/" + election + "/contests/" + newWard + "/credentials").header("Authorization", "Bearer " + voter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blindedToken\":\"Ag\"}")).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select registered_ward_number from election_registrations where election_id=?", Integer.class, UUID.fromString(election))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from voting_rights where contest_id=? and credential_issued", Integer.class, UUID.fromString(oldWard))).isEqualTo(1);
    }
    @Test void repeatedWardNumbersStayScopedAndSuppliedAreaCannotOverrideVerification() throws Exception {
        area("SIM-OTHER-1", "Demo A", "Demo Other Town", 1); area("SIM-B-1", "Demo B", "Demo Third Town", 1);
        record("SIM-A1", district); verify(); String election = mixedElection();
        mvc.perform(post("/api/v1/elections/" + election + "/registrations").header("Authorization", "Bearer " + voter)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("votingDistrictId", secondDistrict))))
                .andExpect(status().isUnprocessableEntity());
        register(election); JsonNode rows = ballots(election);
        for (int i = 0; i < rows.size(); i++) assertThat(rows.get(i).get("eligible").asBoolean()).isEqualTo(i < 4);
    }

    @Test void provinceOnlyAndMunicipalityOnlyVerificationGrantOnlyTheirOwnTiers() throws Exception {
        String province = jdbc.queryForObject("select id::text from geographic_provinces where name='Demo A'", String.class);
        String municipality = jdbc.queryForObject("select id::text from geographic_municipalities where name='Demo Town'", String.class);
        recordAt("SIM-PROVINCE", Map.of("provinceId", province), "VERIFIED", "VERIFIED"); verify();
        String election = mixedElection();
        JsonNode rows = ballots(election);
        for (int n = 0; n < rows.size(); n++) assertThat(rows.get(n).get("eligible").asBoolean()).isEqualTo(n < 2);
        recordAt("SIM-MUNICIPALITY", Map.of("provinceId", province, "municipalityId", municipality), "VERIFIED", "VERIFIED");
        String request = body(mvc.perform(post("/api/v1/me/profile/corrections").header("Authorization", "Bearer " + voter)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("location", Map.of("municipalityId", municipality), "reason", "Fictional municipality evidence now available"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText();
        approve(request, "SIM-MUNICIPALITY");
        rows = ballots(election);
        for (int n = 0; n < rows.size(); n++) assertThat(rows.get(n).get("eligible").asBoolean()).isEqualTo(n < 3);
        register(election);
        clock.now = NOW.plusSeconds(121); lifecycle.advanceElection(UUID.fromString(election));
        String municipal = rows.get(2).get("contest").get("id").asText(), ward = rows.get(3).get("contest").get("id").asText();
        mvc.perform(post("/api/v1/elections/" + election + "/contests/" + municipal + "/credentials").header("Authorization", "Bearer " + voter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blindedToken\":\"Ag\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/elections/" + election + "/contests/" + ward + "/credentials").header("Authorization", "Bearer " + voter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blindedToken\":\"Ag\"}")).andExpect(status().isUnprocessableEntity());
    }
    void recordAt(String reference, Map<String,String> area, String identity, String location) throws Exception {
        mvc.perform(post("/api/v1/admin/profile-verification/records").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("reference", reference, "fictional", true,
                        "fullName", "Fictional Voter", "southAfricanIdNumber", "8001015009087", "identityStatus", identity,
                        "locationStatus", location, "location", area, "reason", "Fictional test source decision")))).andExpect(status().isOk());
    }
    @Test void pendingRejectedAndFailedSourceChecksNeverGrantIdentity() throws Exception {
        String election = mixedElection();
        for (String decision : List.of("PENDING", "REJECTED")) {
            recordAt("SIM-" + decision, Map.of(), decision, "UNVERIFIED");
            mvc.perform(post("/api/v1/me/profile/verification").header("Authorization", "Bearer " + voter)).andExpect(status().isOk()).andExpect(jsonPath("$.identityStatus").value(decision));
            mvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + voter)).andExpect(jsonPath("$.identityStatus").value(decision));
            mvc.perform(post("/api/v1/elections/" + election + "/registrations").header("Authorization", "Bearer " + voter).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnprocessableEntity());
        }
        org.mockito.Mockito.doThrow(new io.github.humphreymahlangu.yourvoice.exception.VerificationUnavailableException()).when(source).find(org.mockito.ArgumentMatchers.anyString());
        mvc.perform(post("/api/v1/me/profile/verification").header("Authorization", "Bearer " + voter))
                .andExpect(status().isOk()).andExpect(jsonPath("$.identityStatus").value("UNAVAILABLE")).andExpect(jsonPath("$.sourceAvailable").value(false));
        assertThat(jdbc.queryForObject("select identity_status from voter_verifications", String.class)).isEqualTo("UNAVAILABLE");
        mvc.perform(post("/api/v1/elections/" + election + "/registrations").header("Authorization", "Bearer " + voter).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable());
    }
    @Test void legacyRegistrationConfirmationCanOnlyNarrowItsOriginalClaimAndClosesAtTheCutoff() throws Exception {
        record("SIM-CURRENT", secondDistrict); verify(); String election = mixedElection();
        UUID profileId = jdbc.queryForObject("select id from voter_profiles", UUID.class);
        jdbc.update("insert into election_registrations(id,voter_profile_id,election_id,voting_district_id,status,registered_at) values (?,?,?,?,'ACTIVE',now())", UUID.randomUUID(), profileId, UUID.fromString(election), UUID.fromString(district));
        assertThat(ballots(election).get(3).get("eligible").asBoolean()).isFalse();
        register(election);
        JsonNode rows = ballots(election);
        assertThat(rows.get(2).get("eligible").asBoolean()).isTrue();
        assertThat(rows.get(3).get("eligible").asBoolean()).isFalse(); assertThat(rows.get(4).get("eligible").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select registered_ward_number from election_registrations", Integer.class)).isNull();
        String next = mixedElection();
        jdbc.update("insert into election_registrations(id,voter_profile_id,election_id,voting_district_id,status,registered_at) values (?,?,?,?,'ACTIVE',now())", UUID.randomUUID(), profileId, UUID.fromString(next), UUID.fromString(district));
        clock.now = NOW.plusSeconds(60);
        mvc.perform(post("/api/v1/elections/" + next + "/registrations").header("Authorization", "Bearer " + voter).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select identity_verified from election_registrations where election_id=?", Boolean.class, UUID.fromString(next))).isFalse();
    }
    @Test void approvedCorrectionAndSourceOutageDoNotInvalidateAnExistingAnonymousSignature() throws Exception {
        record("SIM-CAST-OLD", district); verify(); String election = mixedElection(); register(election);
        clock.now = NOW.plusSeconds(121); lifecycle.advanceElection(UUID.fromString(election));
        JsonNode contest = ballots(election).get(3).get("contest"); String contestId = contest.get("id").asText();
        JsonNode key = body(mvc.perform(get("/api/v1/elections/" + election + "/contests/" + contestId + "/ballot-key"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var blinded = io.github.humphreymahlangu.yourvoice.support.BlindBallotClient.blind(UUID.fromString(contestId), key.get("modulus").asText(), key.get("publicExponent").asText());
        JsonNode credential = body(mvc.perform(post("/api/v1/elections/" + election + "/contests/" + contestId + "/credentials").header("Authorization", "Bearer " + voter)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("blindedToken", blinded.blindedToken()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        record("SIM-CAST-NEW", secondDistrict); approve(correction(secondDistrict), "SIM-CAST-NEW");
        doReturn(false).when(source).available();
        String ballot = json.writeValueAsString(Map.of("contestId", contestId, "contestOptionId", contest.get("options").get(0).get("id").asText(),
                "ballotToken", blinded.ballotToken(), "signature", blinded.unblind(credential.get("blindSignature").asText())));
        mvc.perform(post("/api/v1/ballots").contentType(MediaType.APPLICATION_JSON).content(ballot)).andExpect(status().isCreated()).andExpect(jsonPath("$.accepted").value(true));
        mvc.perform(post("/api/v1/ballots").contentType(MediaType.APPLICATION_JSON).content(ballot)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from ballot_ledger_entries", Integer.class)).isEqualTo(1);
    }
    @Test void concurrentReviewAndRegistrationAlwaysFreezeOneConsistentArea() throws Exception {
        record("SIM-RACE-OLD", district); verify(); String election = mixedElection();
        record("SIM-RACE-NEW", secondDistrict); String request = correction(secondDistrict);
        var ready = new java.util.concurrent.CountDownLatch(2); var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var join = executor.submit(() -> { ready.countDown(); start.await(); register(election); return true; });
            var review = executor.submit(() -> { ready.countDown(); start.await(); approve(request, "SIM-RACE-NEW"); return true; });
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(join.get(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); assertThat(review.get(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        Integer ward = jdbc.queryForObject("select registered_ward_number from election_registrations", Integer.class);
        assertThat(ward).isIn(1,2);
        JsonNode rows = ballots(election);
        assertThat(rows.get(3).get("eligible").asBoolean()).isEqualTo(ward == 1);
        assertThat(rows.get(4).get("eligible").asBoolean()).isEqualTo(ward == 2);
    }
    @Test void contestAdministrationRejectsForgedParentsAndUnconfiguredWards() throws Exception {
        String election = mixedElection();
        for (Map<String,Object> scope : List.<Map<String,Object>>of(
                Map.of("scopeProvince", "Unknown", "scopeMunicipality", "Demo Town", "scopeWardNumber", 1),
                Map.of("scopeProvince", "Demo B", "scopeMunicipality", "Demo Town", "scopeWardNumber", 1),
                Map.of("scopeProvince", "Demo A", "scopeMunicipality", "Demo Town", "scopeWardNumber", 999))) {
            // A separate future draft is needed because the lifecycle has opened the complete fixture.
            jdbc.update("update elections set status='DRAFT' where id=?", UUID.fromString(election));
            Map<String,Object> fields = new java.util.HashMap<>(scope); fields.put("name", "Forged Scope"); fields.put("type", "MUNICIPAL_WARD"); fields.put("displayOrder", 90);
            mvc.perform(post("/api/v1/admin/elections/" + election + "/contests").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(fields)))
                    .andExpect(status().isUnprocessableEntity());
        }
        mvc.perform(get("/profile")).andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("/index.html"));
        mvc.perform(get("/api/v1/me/profile")).andExpect(status().isUnauthorized());
    }

    @Test void scheduledLocationApprovalDoesNotReverseALaterIdentityRejection() throws Exception {
        record("SIM-SCHEDULE-OLD", district); verify();
        record("SIM-SCHEDULE-NEW", secondDistrict); String request = correction(secondDistrict);
        mvc.perform(post("/api/v1/admin/profile-verification/corrections/" + request + "/review").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("approve", true, "reason", "Fictional future area", "sourceReference", "SIM-SCHEDULE-NEW", "effectiveAt", NOW.plusSeconds(30).toString())))).andExpect(status().isOk());
        recordAt("SIM-IDENTITY-REJECTED", Map.of(), "REJECTED", "UNVERIFIED");
        mvc.perform(post("/api/v1/me/profile/verification").header("Authorization", "Bearer " + voter)).andExpect(jsonPath("$.identityStatus").value("REJECTED"));
        clock.now = NOW.plusSeconds(31);
        mvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + voter)).andExpect(status().isOk())
                .andExpect(jsonPath("$.identityStatus").value("REJECTED")).andExpect(jsonPath("$.location.wardNumber").value(2));
        String election = mixedElection();
        mvc.perform(post("/api/v1/elections/" + election + "/registrations").header("Authorization", "Bearer " + voter).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
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
