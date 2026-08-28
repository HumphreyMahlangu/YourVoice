package io.github.humphreymahlangu.votetrust.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.humphreymahlangu.votetrust.support.PostgreSqlTestContainerSupport;
import io.github.humphreymahlangu.votetrust.entity.VotingDistrict;
import io.github.humphreymahlangu.votetrust.repository.AnonymousVotingCredentialRepository;
import io.github.humphreymahlangu.votetrust.repository.BallotLedgerEntryRepository;
import io.github.humphreymahlangu.votetrust.repository.ContestOptionRepository;
import io.github.humphreymahlangu.votetrust.repository.ContestRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRegistrationRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionLifecycleEventRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRepository;
import io.github.humphreymahlangu.votetrust.repository.LedgerStateRepository;
import io.github.humphreymahlangu.votetrust.repository.UserAccountRepository;
import io.github.humphreymahlangu.votetrust.repository.VoterProfileRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingDistrictRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingRightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "votetrust.admin.bootstrap.enabled=true",
        "votetrust.admin.bootstrap.token=test-admin-bootstrap-token-at-least-32-characters"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AdminLifecycleIntegrationTest extends PostgreSqlTestContainerSupport {

    private static final String BOOTSTRAP_TOKEN = "test-admin-bootstrap-token-at-least-32-characters";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BallotLedgerEntryRepository ballotLedgerEntryRepository;

    @Autowired
    private LedgerStateRepository ledgerStateRepository;

    @Autowired
    private AnonymousVotingCredentialRepository anonymousVotingCredentialRepository;

    @Autowired
    private VotingRightRepository votingRightRepository;

    @Autowired
    private ContestOptionRepository contestOptionRepository;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private ElectionRepository electionRepository;

    @Autowired
    private ElectionLifecycleEventRepository electionLifecycleEventRepository;

    @Autowired
    private VotingDistrictRepository votingDistrictRepository;

    @Autowired
    private ElectionRegistrationRepository electionRegistrationRepository;

    @Autowired
    private VoterProfileRepository voterProfileRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @BeforeEach
    void cleanDatabase() {
        ballotLedgerEntryRepository.deleteAll();
        ledgerStateRepository.deleteAll();
        anonymousVotingCredentialRepository.deleteAll();
        votingRightRepository.deleteAll();
        electionLifecycleEventRepository.deleteAll();
        contestOptionRepository.deleteAll();
        contestRepository.deleteAll();
        electionRegistrationRepository.deleteAll();
        voterProfileRepository.deleteAll();
        electionRepository.deleteAll();
        votingDistrictRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    @Test
    void bootstrapFirstAdminRequiresTokenAndRunsOnlyOnce() throws Exception {
        mockMvc.perform(post("/api/v1/admin/bootstrap")
                        .header(AdminBootstrapController.BOOTSTRAP_TOKEN_HEADER, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBootstrapBody("admin.invalid@example.com")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid admin bootstrap token"));

        mockMvc.perform(post("/api/v1/admin/bootstrap")
                        .header(AdminBootstrapController.BOOTSTRAP_TOKEN_HEADER, BOOTSTRAP_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBootstrapBody("admin@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(post("/api/v1/admin/bootstrap")
                        .header(AdminBootstrapController.BOOTSTRAP_TOKEN_HEADER, BOOTSTRAP_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBootstrapBody("second.admin@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An admin account already exists"));
    }

    @Test
    void voterCannotUseAdminLifecycleEndpoints() throws Exception {
        String voterToken = registerVoterAndReturnToken("voter.admin.denied@example.com");

        mockMvc.perform(post("/api/v1/admin/voting-districts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + voterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(votingDistrictBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void adminCanConfigureDraftElectionAndPerformAuditedEmergencyCancellation() throws Exception {
        String adminToken = bootstrapAdminAndReturnToken("admin.lifecycle@example.com");

        mockMvc.perform(post("/api/v1/admin/voting-districts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(votingDistrictBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("WC001-0001"))
                .andExpect(jsonPath("$.name").value("Cape Town Ward 1 Station"));

        MvcResult electionResult = mockMvc.perform(post("/api/v1/admin/elections")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(electionBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("2024 Local Government Simulation"))
                .andExpect(jsonPath("$.type").value("MUNICIPAL"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        String electionId = JsonPath.read(electionResult.getResponse().getContentAsString(), "$.id");

        MvcResult contestResult = mockMvc.perform(post("/api/v1/admin/elections/{electionId}/contests", electionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ward 1 Councillor"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.scopeProvince").value("Western Cape"))
                .andExpect(jsonPath("$.scopeMunicipality").value("City of Cape Town"))
                .andExpect(jsonPath("$.scopeWardNumber").value(1))
                .andReturn();
        String contestId = JsonPath.read(contestResult.getResponse().getContentAsString(), "$.id");

        createContestOption(adminToken, electionId, contestId, "Ubuntu Civic Movement", "PARTY", 1);
        createContestOption(adminToken, electionId, contestId, "Independent Candidate", "INDEPENDENT_CANDIDATE", 2);

        mockMvc.perform(patch("/api/v1/admin/elections/{electionId}/status", electionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("REGISTRATION_OPEN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Election lifecycle transitions are automatic; administrators may only cancel an active election"
                ));

        mockMvc.perform(patch("/api/v1/admin/elections/{electionId}/status", electionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("CANCELLED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/admin/elections/{electionId}/lifecycle-events", electionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].previousStatus").value("DRAFT"))
                .andExpect(jsonPath("$[0].newStatus").value("CANCELLED"))
                .andExpect(jsonPath("$[0].trigger").value("ADMINISTRATOR"))
                .andExpect(jsonPath("$[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$[0].actorEmail").value("admin.lifecycle@example.com"));
    }

    @Test
    void adminCannotAdvanceElectionOrContestLifecycleManually() throws Exception {
        String adminToken = bootstrapAdminAndReturnToken("admin.lifecycle.invalid@example.com");
        String electionId = createElectionAndReturnId(adminToken);
        String contestId = createContestAndReturnId(adminToken, electionId);

        mockMvc.perform(patch("/api/v1/admin/elections/{electionId}/status", electionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("VOTING_OPEN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Election lifecycle transitions are automatic; administrators may only cancel an active election"
                ));

        mockMvc.perform(patch("/api/v1/admin/elections/{electionId}/contests/{contestId}/status", electionId, contestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("OPEN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Contest lifecycle transitions are automatic and cannot be performed by an administrator"
                ));
    }

    @Test
    void adminCannotCreateContestWithWrongElectionTypeOrMissingScope() throws Exception {
        String adminToken = bootstrapAdminAndReturnToken("admin.scope.invalid@example.com");
        String electionId = createElectionAndReturnId(adminToken);

        String nationalContestBody = """
                {
                  "name": "National Assembly",
                  "type": "NATIONAL",
                  "displayOrder": 2
                }
                """;
        mockMvc.perform(post("/api/v1/admin/elections/{electionId}/contests", electionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nationalContestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Contest type NATIONAL is not valid for a MUNICIPAL election"));

        String unscopedWardContestBody = """
                {
                  "name": "Unscoped Ward Contest",
                  "type": "MUNICIPAL_WARD",
                  "displayOrder": 3
                }
                """;
        mockMvc.perform(post("/api/v1/admin/elections/{electionId}/contests", electionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unscopedWardContestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Municipal ward contests require scopeProvince, scopeMunicipality, and scopeWardNumber"));
    }

    private String bootstrapAdminAndReturnToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/bootstrap")
                        .header(AdminBootstrapController.BOOTSTRAP_TOKEN_HEADER, BOOTSTRAP_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBootstrapBody(email)))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String registerVoterAndReturnToken(String email) throws Exception {
        VotingDistrict onboardingDistrict = votingDistrictRepository.save(new VotingDistrict(
                "TEST-ONBOARDING",
                "Test Onboarding Station",
                "Western Cape",
                "City of Cape Town",
                99
        ));
        String body = """
                {
                  "fullName": "Admin Access Test Voter",
                  "email": "%s",
                  "password": "VeryStrongPassword1",
                  "southAfricanIdNumber": "8001015000086",
                  "idDocumentType": "SMART_ID_CARD",
                  "votingDistrictId": "%s"
                }
                """.formatted(email, onboardingDistrict.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String createElectionAndReturnId(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/elections")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(electionBody()))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createContestAndReturnId(String adminToken, String electionId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/elections/{electionId}/contests", electionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contestBody()))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void createContestOption(
            String adminToken,
            String electionId,
            String contestId,
            String name,
            String optionType,
            int displayOrder
    ) throws Exception {
        String body = """
                {
                  "name": "%s",
                  "optionType": "%s",
                  "displayOrder": %d
                }
                """.formatted(name, optionType, displayOrder);

        mockMvc.perform(post("/api/v1/admin/elections/{electionId}/contests/{contestId}/options", electionId, contestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.optionType").value(optionType))
                .andExpect(jsonPath("$.displayOrder").value(displayOrder));
    }

    private String adminBootstrapBody(String email) {
        return """
                {
                  "email": "%s",
                  "password": "VeryStrongPassword1"
                }
                """.formatted(email);
    }

    private String votingDistrictBody() {
        return """
                {
                  "code": "wc001-0001",
                  "name": "Cape Town Ward 1 Station",
                  "province": "Western Cape",
                  "municipality": "City of Cape Town",
                  "wardNumber": 1
                }
                """;
    }

    private String electionBody() {
        return """
                {
                  "name": "2024 Local Government Simulation",
                  "type": "MUNICIPAL",
                  "registrationStartAt": "2099-01-01T00:00:00Z",
                  "registrationEndAt": "2099-02-01T00:00:00Z",
                  "votingStartAt": "2099-03-01T07:00:00Z",
                  "votingEndAt": "2099-03-01T21:00:00Z"
                }
                """;
    }

    private String contestBody() {
        return """
                {
                  "name": "Ward 1 Councillor",
                  "type": "MUNICIPAL_WARD",
                  "displayOrder": 1,
                  "scopeProvince": "Western Cape",
                  "scopeMunicipality": "City of Cape Town",
                  "scopeWardNumber": 1
                }
                """;
    }

    private String statusBody(String status) {
        return """
                {
                  "status": "%s"
                }
                """.formatted(status);
    }
}
