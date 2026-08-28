package io.github.humphreymahlangu.votetrust.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.humphreymahlangu.votetrust.support.PostgreSqlTestContainerSupport;
import io.github.humphreymahlangu.votetrust.entity.Election;
import io.github.humphreymahlangu.votetrust.entity.ElectionStatus;
import io.github.humphreymahlangu.votetrust.entity.ElectionType;
import io.github.humphreymahlangu.votetrust.entity.VotingDistrict;
import io.github.humphreymahlangu.votetrust.repository.ElectionLifecycleEventRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRegistrationRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRepository;
import io.github.humphreymahlangu.votetrust.repository.AnonymousVotingCredentialRepository;
import io.github.humphreymahlangu.votetrust.repository.BallotLedgerEntryRepository;
import io.github.humphreymahlangu.votetrust.repository.ContestOptionRepository;
import io.github.humphreymahlangu.votetrust.repository.ContestRepository;
import io.github.humphreymahlangu.votetrust.repository.LedgerStateRepository;
import io.github.humphreymahlangu.votetrust.repository.UserAccountRepository;
import io.github.humphreymahlangu.votetrust.repository.VoterProfileRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingRightRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingDistrictRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class VoterRegistrationIntegrationTest extends PostgreSqlTestContainerSupport {

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
