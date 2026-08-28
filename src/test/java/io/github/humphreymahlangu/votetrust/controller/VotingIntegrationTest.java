package io.github.humphreymahlangu.votetrust.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.humphreymahlangu.votetrust.support.PostgreSqlTestContainerSupport;
import io.github.humphreymahlangu.votetrust.entity.AccountRole;
import io.github.humphreymahlangu.votetrust.entity.AnonymousVotingCredential;
import io.github.humphreymahlangu.votetrust.entity.BallotLedgerEntry;
import io.github.humphreymahlangu.votetrust.entity.Contest;
import io.github.humphreymahlangu.votetrust.entity.ContestOption;
import io.github.humphreymahlangu.votetrust.entity.ContestOptionType;
import io.github.humphreymahlangu.votetrust.entity.ContestStatus;
import io.github.humphreymahlangu.votetrust.entity.ContestType;
import io.github.humphreymahlangu.votetrust.entity.Election;
import io.github.humphreymahlangu.votetrust.entity.ElectionRegistration;
import io.github.humphreymahlangu.votetrust.entity.ElectionStatus;
import io.github.humphreymahlangu.votetrust.entity.ElectionType;
import io.github.humphreymahlangu.votetrust.entity.IdDocumentType;
import io.github.humphreymahlangu.votetrust.entity.LedgerState;
import io.github.humphreymahlangu.votetrust.entity.RegistrationStatus;
import io.github.humphreymahlangu.votetrust.entity.UserAccount;
import io.github.humphreymahlangu.votetrust.entity.VoterProfile;
import io.github.humphreymahlangu.votetrust.entity.VotingDistrict;
import io.github.humphreymahlangu.votetrust.repository.AnonymousVotingCredentialRepository;
import io.github.humphreymahlangu.votetrust.repository.BallotLedgerEntryRepository;
import io.github.humphreymahlangu.votetrust.repository.ContestOptionRepository;
import io.github.humphreymahlangu.votetrust.repository.ContestRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionLifecycleEventRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRegistrationRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRepository;
import io.github.humphreymahlangu.votetrust.repository.LedgerStateRepository;
import io.github.humphreymahlangu.votetrust.repository.UserAccountRepository;
import io.github.humphreymahlangu.votetrust.repository.VoterProfileRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingRightRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingDistrictRepository;
import io.github.humphreymahlangu.votetrust.security.IdentityHashService;
import io.github.humphreymahlangu.votetrust.security.JwtService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
class VotingIntegrationTest extends PostgreSqlTestContainerSupport {

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
    private ElectionRegistrationRepository electionRegistrationRepository;

    @Autowired
    private VoterProfileRepository voterProfileRepository;

    @Autowired
    private ElectionRepository electionRepository;

    @Autowired
    private ElectionLifecycleEventRepository electionLifecycleEventRepository;

    @Autowired
    private VotingDistrictRepository votingDistrictRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private IdentityHashService identityHashService;

    @Autowired
    private JwtService jwtService;

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
    void issueCredentialAndCastAnonymousBallot() throws Exception {
        VotingFixture fixture = createVotingFixture("voter.cast@example.com", LocalDate.of(1980, 1, 1));

        MvcResult credentialResult = mockMvc.perform(post(
                        "/api/v1/elections/{electionId}/contests/{contestId}/credentials",
                        fixture.election().getId(),
                        fixture.contest().getId()
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.jwt()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contestId").value(fixture.contest().getId().toString()))
                .andExpect(jsonPath("$.votingCredential").isNotEmpty())
                .andReturn();
        String credential = JsonPath.read(credentialResult.getResponse().getContentAsString(), "$.votingCredential");

        mockMvc.perform(post("/api/v1/ballots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ballotBody(fixture.contest(), fixture.optionA(), credential)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contestId").value(fixture.contest().getId().toString()))
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.message").value("Ballot accepted"));

        List<AnonymousVotingCredential> credentials = anonymousVotingCredentialRepository.findAll();
        assertThat(credentials).hasSize(1);
        assertThat(credentials.getFirst().isUsed()).isTrue();

        List<BallotLedgerEntry> entries = ballotLedgerEntryRepository
                .findByContestIdOrderByLedgerIndexAsc(fixture.contest().getId());
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getContestOption().getId()).isEqualTo(fixture.optionA().getId());
    }

    @Test
    void credentialCanOnlyBeIssuedOncePerContestAndUsedOnce() throws Exception {
        VotingFixture fixture = createVotingFixture("voter.once@example.com", LocalDate.of(1980, 1, 1));
        String credential = issueCredential(fixture);

        mockMvc.perform(post(
                        "/api/v1/elections/{electionId}/contests/{contestId}/credentials",
                        fixture.election().getId(),
                        fixture.contest().getId()
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A voting credential has already been issued for this contest"));

        mockMvc.perform(post("/api/v1/ballots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ballotBody(fixture.contest(), fixture.optionA(), credential)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/ballots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ballotBody(fixture.contest(), fixture.optionB(), credential)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Voting credential has already been used"));
    }

    @Test
    void hashChainLinksEachVoteToThePreviousVote() throws Exception {
        VotingFixture firstFixture = createVotingFixture("voter.chain.one@example.com", LocalDate.of(1980, 1, 1));
        VotingFixture secondFixture = addRegisteredVoterToExistingContest(
                firstFixture.election(),
                firstFixture.district(),
                firstFixture.contest(),
                "voter.chain.two@example.com",
                LocalDate.of(1981, 1, 1)
        );

        String firstCredential = issueCredential(firstFixture);
        String secondCredential = issueCredential(secondFixture);

        mockMvc.perform(post("/api/v1/ballots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ballotBody(firstFixture.contest(), firstFixture.optionA(), firstCredential)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/ballots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ballotBody(firstFixture.contest(), firstFixture.optionB(), secondCredential)))
                .andExpect(status().isCreated());

        List<BallotLedgerEntry> entries = ballotLedgerEntryRepository
                .findByContestIdOrderByLedgerIndexAsc(firstFixture.contest().getId());
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getPreviousHash()).isEqualTo(LedgerState.GENESIS_HASH);
        assertThat(entries.get(1).getPreviousHash()).isEqualTo(entries.get(0).getCurrentHash());
    }

    @Test
    void credentialRequiresRegisteredVoterWhoIsAtLeastEighteenOnVotingDay() throws Exception {
        VotingFixture underageFixture = createVotingFixture("voter.underage.vote@example.com", LocalDate.of(2010, 1, 1));

        mockMvc.perform(post(
                        "/api/v1/elections/{electionId}/contests/{contestId}/credentials",
                        underageFixture.election().getId(),
                        underageFixture.contest().getId()
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + underageFixture.jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Voters must be at least 18 years old to vote"));
    }

    @Test
    void credentialRequiresContestMatchingRegisteredVotingDistrict() throws Exception {
        VotingFixture fixture = createVotingFixture(
                "voter.wrong.ward@example.com",
                LocalDate.of(1980, 1, 1),
                ElectionStatus.VOTING_OPEN,
                ContestStatus.OPEN,
                Instant.parse("2026-08-07T07:00:00Z"),
                Instant.parse("2026-08-07T21:00:00Z"),
                2
        );

        mockMvc.perform(post(
                        "/api/v1/elections/{electionId}/contests/{contestId}/credentials",
                        fixture.election().getId(),
                        fixture.contest().getId()
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Voter is not eligible for this contest based on registered voting district"));
    }

    @Test
    void credentialRequiresOpenVotingPeriod() throws Exception {
        VotingFixture fixture = createVotingFixture(
                "voter.closed.vote@example.com",
                LocalDate.of(1980, 1, 1),
                ElectionStatus.REGISTRATION_CLOSED,
                ContestStatus.OPEN,
                Instant.parse("2026-08-01T07:00:00Z"),
                Instant.parse("2026-08-01T21:00:00Z")
        );

        mockMvc.perform(post(
                        "/api/v1/elections/{electionId}/contests/{contestId}/credentials",
                        fixture.election().getId(),
                        fixture.contest().getId()
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Election voting period is closed"));
    }

    @Test
    void anonymousBallotRejectsInvalidCredential() throws Exception {
        VotingFixture fixture = createVotingFixture("voter.invalid.credential@example.com", LocalDate.of(1980, 1, 1));

        mockMvc.perform(post("/api/v1/ballots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ballotBody(fixture.contest(), fixture.optionA(), "not-a-real-credential")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired voting credential"));
    }

    @Test
    void anonymousBallotCanCastExplicitBlankBallotOption() throws Exception {
        VotingFixture fixture = createVotingFixture("voter.blank.ballot@example.com", LocalDate.of(1980, 1, 1));
        ContestOption blankOption = contestOptionRepository.save(new ContestOption(
                fixture.contest(),
                "Blank ballot",
                ContestOptionType.BLANK_BALLOT,
                98
        ));
        String credential = issueCredential(fixture);

        mockMvc.perform(post("/api/v1/ballots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ballotBody(fixture.contest(), blankOption, credential)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accepted").value(true));

        List<BallotLedgerEntry> entries = ballotLedgerEntryRepository
                .findByContestIdOrderByLedgerIndexAsc(fixture.contest().getId());
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getContestOption().getOptionType()).isEqualTo(ContestOptionType.BLANK_BALLOT);
    }

    @Test
    void contestsCanBeListedPubliclyWithOptions() throws Exception {
        VotingFixture fixture = createVotingFixture("voter.contests@example.com", LocalDate.of(1980, 1, 1));

        mockMvc.perform(get("/api/v1/elections/{electionId}/contests", fixture.election().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(fixture.contest().getId().toString()))
                .andExpect(jsonPath("$[0].scopeProvince").value("Western Cape"))
                .andExpect(jsonPath("$[0].scopeMunicipality").value("City of Cape Town"))
                .andExpect(jsonPath("$[0].scopeWardNumber").value(1))
                .andExpect(jsonPath("$[0].options[0].id").value(fixture.optionA().getId().toString()))
                .andExpect(jsonPath("$[0].options[1].id").value(fixture.optionB().getId().toString()));
    }

    private String issueCredential(VotingFixture fixture) throws Exception {
        MvcResult credentialResult = mockMvc.perform(post(
                        "/api/v1/elections/{electionId}/contests/{contestId}/credentials",
                        fixture.election().getId(),
                        fixture.contest().getId()
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.jwt()))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(credentialResult.getResponse().getContentAsString(), "$.votingCredential");
    }

    private VotingFixture createVotingFixture(String email, LocalDate dateOfBirth) throws Exception {
        return createVotingFixture(
                email,
                dateOfBirth,
                ElectionStatus.VOTING_OPEN,
                ContestStatus.OPEN,
                Instant.parse("2026-08-07T07:00:00Z"),
                Instant.parse("2026-08-07T21:00:00Z"),
                1
        );
    }

    private VotingFixture createVotingFixture(
            String email,
            LocalDate dateOfBirth,
            ElectionStatus electionStatus,
            ContestStatus contestStatus,
            Instant votingStartAt,
            Instant votingEndAt
    ) throws Exception {
        return createVotingFixture(
                email,
                dateOfBirth,
                electionStatus,
                contestStatus,
                votingStartAt,
                votingEndAt,
                1
        );
    }

    private VotingFixture createVotingFixture(
            String email,
            LocalDate dateOfBirth,
            ElectionStatus electionStatus,
            ContestStatus contestStatus,
            Instant votingStartAt,
            Instant votingEndAt,
            Integer contestScopeWardNumber
    ) throws Exception {
        VotingDistrict district = votingDistrictRepository.save(new VotingDistrict(
                "WC001-0001",
                "Cape Town Ward 1 Station",
                "Western Cape",
                "City of Cape Town",
                1
        ));
        Election election = electionRepository.save(new Election(
                "2026 Local Government Election",
                ElectionType.MUNICIPAL,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                votingStartAt,
                votingEndAt,
                electionStatus
        ));
        Contest contest = contestRepository.save(new Contest(
                election,
                "Municipal Ward Councillor",
                ContestType.MUNICIPAL_WARD,
                contestStatus,
                1,
                "Western Cape",
                "City of Cape Town",
                contestScopeWardNumber
        ));
        ContestOption optionA = contestOptionRepository.save(new ContestOption(
                contest,
                "Ubuntu Civic Party",
                ContestOptionType.PARTY,
                1
        ));
        ContestOption optionB = contestOptionRepository.save(new ContestOption(
                contest,
                "Future Youth Movement",
                ContestOptionType.PARTY,
                2
        ));

        String jwt = registerAccountAndReturnToken(email);
        UserAccount account = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
        VoterProfile voterProfile = voterProfileRepository.save(new VoterProfile(
                account,
                "Voting Test Voter",
                identityHashService.hashSouthAfricanIdNumber(email),
                IdDocumentType.SMART_ID_CARD,
                dateOfBirth,
                district
        ));
        electionRegistrationRepository.save(new ElectionRegistration(
                voterProfile,
                election,
                district,
                RegistrationStatus.ACTIVE,
                FIXED_NOW.minusSeconds(3600)
        ));

        return new VotingFixture(election, district, contest, optionA, optionB, jwt);
    }

    private VotingFixture addRegisteredVoterToExistingContest(
            Election election,
            VotingDistrict district,
            Contest contest,
            String email,
            LocalDate dateOfBirth
    ) throws Exception {
        String jwt = registerAccountAndReturnToken(email);
        UserAccount account = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
        VoterProfile voterProfile = voterProfileRepository.save(new VoterProfile(
                account,
                "Voting Test Voter",
                identityHashService.hashSouthAfricanIdNumber(email),
                IdDocumentType.SMART_ID_CARD,
                dateOfBirth,
                district
        ));
        electionRegistrationRepository.save(new ElectionRegistration(
                voterProfile,
                election,
                district,
                RegistrationStatus.ACTIVE,
                FIXED_NOW.minusSeconds(3600)
        ));

        List<ContestOption> options = contestOptionRepository.findByContestIdOrderByDisplayOrderAscNameAsc(contest.getId());
        return new VotingFixture(election, district, contest, options.get(0), options.get(1), jwt);
    }

    private String registerAccountAndReturnToken(String email) {
        UserAccount account = userAccountRepository.save(new UserAccount(
                email,
                "unused-password-hash",
                AccountRole.VOTER,
                true
        ));
        return jwtService.generateAccessToken(account).token();
    }

    private String ballotBody(Contest contest, ContestOption contestOption, String credential) {
        return """
                {
                  "contestId": "%s",
                  "contestOptionId": "%s",
                  "votingCredential": "%s"
                }
                """.formatted(contest.getId(), contestOption.getId(), credential);
    }

    private record VotingFixture(
            Election election,
            VotingDistrict district,
            Contest contest,
            ContestOption optionA,
            ContestOption optionB,
            String jwt
    ) {
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
