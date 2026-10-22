package io.github.humphreymahlangu.yourvoice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.humphreymahlangu.yourvoice.support.DatabaseCleaner;
import io.github.humphreymahlangu.yourvoice.support.PostgreSqlTestContainerSupport;
import io.github.humphreymahlangu.yourvoice.entity.Election;
import io.github.humphreymahlangu.yourvoice.entity.ElectionStatus;
import io.github.humphreymahlangu.yourvoice.entity.ElectionType;
import io.github.humphreymahlangu.yourvoice.entity.VotingDistrict;
import io.github.humphreymahlangu.yourvoice.repository.ElectionLifecycleEventRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRegistrationRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRepository;
import io.github.humphreymahlangu.yourvoice.repository.AnonymousVotingCredentialRepository;
import io.github.humphreymahlangu.yourvoice.repository.BallotLedgerEntryRepository;
import io.github.humphreymahlangu.yourvoice.repository.ContestOptionRepository;
import io.github.humphreymahlangu.yourvoice.repository.ContestRepository;
import io.github.humphreymahlangu.yourvoice.repository.LedgerStateRepository;
import io.github.humphreymahlangu.yourvoice.repository.UserAccountRepository;
import io.github.humphreymahlangu.yourvoice.repository.VoterProfileRepository;
import io.github.humphreymahlangu.yourvoice.repository.VotingRightRepository;
import io.github.humphreymahlangu.yourvoice.repository.VotingDistrictRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class VoterRegistrationIntegrationTest extends PostgreSqlTestContainerSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Instant FIXED_NOW = Instant.parse("2026-08-07T10:00:00Z");

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
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void registerForElectionSucceedsDuringOpenRegistrationPeriod() throws Exception {
        VotingDistrict district = createVotingDistrict();
        Election election = createElection(
                ElectionStatus.REGISTRATION_OPEN,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-10T23:59:59Z")
        );
        String token = registerAccountAndReturnToken("registration.success@example.com", district);

        mockMvc.perform(post("/api/v1/elections/{electionId}/registrations", election.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(district)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.electionId").value(election.getId().toString()))
                .andExpect(jsonPath("$.electionName").value("2026 Local Government Election"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.votingDistrictId").value(district.getId().toString()))
                .andExpect(jsonPath("$.votingDistrictCode").value("WC001-0001"));

        mockMvc.perform(get("/api/v1/me/registrations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].electionId").value(election.getId().toString()))
                .andExpect(jsonPath("$[0].votingDistrictName").value("Cape Town Ward 1 Station"));
    }

    @Test
    void registerForElectionUsesProfileDistrictWhenNoDistrictIsSupplied() throws Exception {
        VotingDistrict district = createVotingDistrict();
        Election election = createOpenElection();
        String token = registerAccountAndReturnToken("registration.profile-district@example.com", district);

        mockMvc.perform(post("/api/v1/elections/{electionId}/registrations", election.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.votingDistrictId").value(district.getId().toString()));
    }

    @Test
    void registerForElectionAcceptsMissingRequestBody() throws Exception {
        VotingDistrict district = createVotingDistrict();
        Election election = createOpenElection();
        String token = registerAccountAndReturnToken("registration.no-body@example.com", district);

        mockMvc.perform(post("/api/v1/elections/{electionId}/registrations", election.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.votingDistrictId").value(district.getId().toString()));
    }

    @Test
    void registerForElectionRejectsDistrictThatDiffersFromVoterProfile() throws Exception {
        VotingDistrict profileDistrict = createVotingDistrict();
        VotingDistrict otherDistrict = votingDistrictRepository.save(new VotingDistrict(
                "GP001-0042",
                "Johannesburg Ward 42 Station",
                "Gauteng",
                "City of Johannesburg",
                42
        ));
        Election election = createOpenElection();
        String token = registerAccountAndReturnToken("registration.other-district@example.com", profileDistrict);

        mockMvc.perform(post("/api/v1/elections/{electionId}/registrations", election.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(otherDistrict)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Voting district must match the district on your voter profile"));

        assertThat(electionRegistrationRepository.count()).isZero();
    }

    @Test
    void registerForElectionRejectsClosedRegistrationPeriod() throws Exception {
        VotingDistrict district = createVotingDistrict();
        Election election = createElection(
                ElectionStatus.REGISTRATION_OPEN,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-31T23:59:59Z")
        );
        String token = registerAccountAndReturnToken("registration.closed@example.com", district);

        mockMvc.perform(post("/api/v1/elections/{electionId}/registrations", election.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(district)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Election registration period is closed"));
    }

    @Test
    void registerForElectionRejectsDuplicateRegistration() throws Exception {
        VotingDistrict district = createVotingDistrict();
        Election election = createOpenElection();
        String token = registerAccountAndReturnToken("registration.duplicate@example.com", district);

        mockMvc.perform(post("/api/v1/elections/{electionId}/registrations", election.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(district)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/elections/{electionId}/registrations", election.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(district)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Voter is already registered for this election"));
    }

    @Test
    void registerForElectionRejectsMalformedElectionIdWithoutMaskingAsUnauthorizedError() throws Exception {
        VotingDistrict district = createVotingDistrict();
        String token = registerAccountAndReturnToken("registration.bad-election-id@example.com", district);

        mockMvc.perform(post("/api/v1/elections/not-a-uuid/registrations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(district)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid UUID value for 'electionId'"))
                .andExpect(jsonPath("$.path").value("/api/v1/elections/not-a-uuid/registrations"));
    }

    @Test
    void registerForElectionRejectsMalformedVotingDistrictIdWithoutMaskingAsUnauthorizedError() throws Exception {
        VotingDistrict onboardingDistrict = createVotingDistrict();
        Election election = createOpenElection();
        String token = registerAccountAndReturnToken(
                "registration.bad-district-id@example.com",
                onboardingDistrict
        );

        mockMvc.perform(post("/api/v1/elections/{electionId}/registrations", election.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "votingDistrictId": "not-a-uuid"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'votingDistrictId'"))
                .andExpect(jsonPath("$.path").value("/api/v1/elections/%s/registrations".formatted(election.getId())));
    }

    @Test
    void listElectionsAndVotingDistrictsArePublicReadOnlyEndpoints() throws Exception {
        VotingDistrict district = createVotingDistrict();
        Election election = createOpenElection();

        mockMvc.perform(get("/api/v1/elections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(election.getId().toString()));

        mockMvc.perform(get("/api/v1/voting-districts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(district.getId().toString()));
    }

    @Test
    void listEndpointsArePaginatedWithTheTotalInAHeader() throws Exception {
        createOpenElection();
        electionRepository.save(new Election(
                "2026 By-Election",
                ElectionType.MUNICIPAL,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-10T00:00:00Z"),
                Instant.parse("2026-10-01T07:00:00Z"),
                Instant.parse("2026-10-01T21:00:00Z"),
                ElectionStatus.DRAFT
        ));

        mockMvc.perform(get("/api/v1/elections").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("2026 Local Government Election"));
        mockMvc.perform(get("/api/v1/elections").param("page", "1").param("size", "1"))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/elections").param("size", "501"))
                .andExpect(status().isBadRequest());
    }

    private Election createOpenElection() {
        return createElection(
                ElectionStatus.REGISTRATION_OPEN,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-10T23:59:59Z")
        );
    }

    private Election createElection(ElectionStatus status, Instant registrationStartAt, Instant registrationEndAt) {
        return electionRepository.save(new Election(
                "2026 Local Government Election",
                ElectionType.MUNICIPAL,
                registrationStartAt,
                registrationEndAt,
                Instant.parse("2026-10-01T07:00:00Z"),
                Instant.parse("2026-10-01T21:00:00Z"),
                status
        ));
    }

    private VotingDistrict createVotingDistrict() {
        return votingDistrictRepository.save(new VotingDistrict(
                "WC001-0001",
                "Cape Town Ward 1 Station",
                "Western Cape",
                "City of Cape Town",
                1
        ));
    }

    private String registerAccountAndReturnToken(String email, VotingDistrict votingDistrict) throws Exception {
        String body = """
                {
                  "fullName": "Registration Test Voter",
                  "email": "%s",
                  "password": "VeryStrongPassword1",
                  "southAfricanIdNumber": "1001015000083",
                  "idDocumentType": "SMART_ID_CARD",
                  "votingDistrictId": "%s"
                }
                """.formatted(email, votingDistrict.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String registrationBody(VotingDistrict votingDistrict) {
        return """
                {
                  "votingDistrictId": "%s"
                }
                """.formatted(votingDistrict.getId());
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }
}
