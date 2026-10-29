package io.github.humphreymahlangu.yourvoice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.humphreymahlangu.yourvoice.support.BlindBallotClient;
import io.github.humphreymahlangu.yourvoice.support.DatabaseCleaner;
import io.github.humphreymahlangu.yourvoice.support.TrustedTestProfiles;
import io.github.humphreymahlangu.yourvoice.support.PostgreSqlTestContainerSupport;
import io.github.humphreymahlangu.yourvoice.entity.AccountRole;
import io.github.humphreymahlangu.yourvoice.entity.AnonymousVotingCredential;
import io.github.humphreymahlangu.yourvoice.entity.BallotLedgerEntry;
import io.github.humphreymahlangu.yourvoice.entity.Contest;
import io.github.humphreymahlangu.yourvoice.entity.ContestOption;
import io.github.humphreymahlangu.yourvoice.entity.ContestOptionType;
import io.github.humphreymahlangu.yourvoice.entity.ContestStatus;
import io.github.humphreymahlangu.yourvoice.entity.ContestType;
import io.github.humphreymahlangu.yourvoice.entity.Election;
import io.github.humphreymahlangu.yourvoice.entity.ElectionRegistration;
import io.github.humphreymahlangu.yourvoice.entity.ElectionStatus;
import io.github.humphreymahlangu.yourvoice.entity.ElectionType;
import io.github.humphreymahlangu.yourvoice.entity.IdDocumentType;
import io.github.humphreymahlangu.yourvoice.entity.LedgerState;
import io.github.humphreymahlangu.yourvoice.entity.RegistrationStatus;
import io.github.humphreymahlangu.yourvoice.entity.UserAccount;
import io.github.humphreymahlangu.yourvoice.entity.VoterProfile;
import io.github.humphreymahlangu.yourvoice.entity.VotingDistrict;
import io.github.humphreymahlangu.yourvoice.repository.AnonymousVotingCredentialRepository;
import io.github.humphreymahlangu.yourvoice.repository.BallotLedgerEntryRepository;
import io.github.humphreymahlangu.yourvoice.repository.ContestOptionRepository;
import io.github.humphreymahlangu.yourvoice.repository.ContestRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionLifecycleEventRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRegistrationRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRepository;
import io.github.humphreymahlangu.yourvoice.repository.LedgerStateRepository;
import io.github.humphreymahlangu.yourvoice.repository.UserAccountRepository;
import io.github.humphreymahlangu.yourvoice.repository.VoterProfileRepository;
import io.github.humphreymahlangu.yourvoice.repository.VotingRightRepository;
import io.github.humphreymahlangu.yourvoice.repository.VotingDistrictRepository;
import io.github.humphreymahlangu.yourvoice.security.IdentityHashService;
import io.github.humphreymahlangu.yourvoice.security.JwtService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class VotingIntegrationTest extends PostgreSqlTestContainerSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Passes request validation; used where eligibility checks reject the request before the token is examined.
    private static final String PLACEHOLDER_BLINDED_TOKEN = "AQID";
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
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void issueCredentialAndCastAnonymousBallot() throws Exception {
        VotingFixture fixture = createVotingFixture("voter.cast@example.com", LocalDate.of(1980, 1, 1));
        BlindBallotClient.BlindedBallot ballot = blindBallot(fixture);

        MvcResult credentialResult = requestCredential(fixture, ballot.blindedToken())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contestId").value(fixture.contest().getId().toString()))
                .andExpect(jsonPath("$.blindSignature").isNotEmpty())
                .andExpect(jsonPath("$.keyFingerprint").isNotEmpty())
                .andExpect(jsonPath("$.votingCredential").doesNotExist())
                .andReturn();
        String signature = ballot.unblind(JsonPath.read(credentialResult.getResponse().getContentAsString(), "$.blindSignature"));

        mockMvc.perform(post("/api/v1/ballots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ballotBody(fixture.contest(), fixture.optionA(), ballot.ballotToken(), signature)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contestId").value(fixture.contest().getId().toString()))
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.message").value("Ballot accepted"));

        List<AnonymousVotingCredential> spentTokens = anonymousVotingCredentialRepository.findAll();
        assertThat(spentTokens).hasSize(1);
        assertThat(spentTokens.getFirst().isUsed()).isTrue();

        List<BallotLedgerEntry> entries = ballotLedgerEntryRepository
                .findByContestIdOrderByLedgerIndexAsc(fixture.contest().getId());
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getContestOption().getId()).isEqualTo(fixture.optionA().getId());
    }

    @Test
    void credentialIssuanceStoresNothingThatMatchesTheSpentBallotToken() throws Exception {
        VotingFixture fixture = createVotingFixture("voter.unlinkable@example.com", LocalDate.of(1980, 1, 1));
        IssuedBallot issued = issueCredential(fixture);
        castBallot(fixture.contest(), fixture.optionA(), issued).andExpect(status().isCreated());

        String storedBlindedHash = jdbcTemplate.queryForObject(
                "select blinded_token_hash from voting_rights", String.class);
        String storedSpentHash = jdbcTemplate.queryForObject(
                "select credential_hash from anonymous_voting_credentials", String.class);

        assertThat(storedBlindedHash).isNotNull();
        assertThat(storedBlindedHash).isNotEqualTo(storedSpentHash);
        assertThat(storedBlindedHash).doesNotContain(issued.ballotToken());
    }

    @Test
    void credentialCanOnlyBeIssuedOncePerContestAndUsedOnce() throws Exception {
        VotingFixture fixture = createVotingFixture("voter.once@example.com", LocalDate.of(1980, 1, 1));
        IssuedBallot issued = issueCredential(fixture);

        requestCredential(fixture, blindBallot(fixture).blindedToken())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A voting credential has already been issued for this contest"));

        castBallot(fixture.contest(), fixture.optionA(), issued).andExpect(status().isCreated());

        castBallot(fixture.contest(), fixture.optionB(), issued)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Voting credential has already been used"));
    }

    @Test
    void retryingIssuanceWithTheSameBlindedTokenReturnsTheSameSignature() throws Exception {
        VotingFixture fixture = createVotingFixture("voter.retry@example.com", LocalDate.of(1980, 1, 1));
        BlindBallotClient.BlindedBallot ballot = blindBallot(fixture);

        String first = JsonPath.read(requestCredential(fixture, ballot.blindedToken())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.blindSignature");
        String retried = JsonPath.read(requestCredential(fixture, ballot.blindedToken())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.blindSignature");

        assertThat(retried).isEqualTo(first);
        assertThat(votingRightRepository.count()).isEqualTo(1);
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

        IssuedBallot firstBallot = issueCredential(firstFixture);
        IssuedBallot secondBallot = issueCredential(secondFixture);

        castBallot(firstFixture.contest(), firstFixture.optionA(), firstBallot).andExpect(status().isCreated());
        castBallot(firstFixture.contest(), firstFixture.optionB(), secondBallot).andExpect(status().isCreated());

        List<BallotLedgerEntry> entries = ballotLedgerEntryRepository
                .findByContestIdOrderByLedgerIndexAsc(firstFixture.contest().getId());
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getPreviousHash()).isEqualTo(LedgerState.GENESIS_HASH);
        assertThat(entries.get(1).getPreviousHash()).isEqualTo(entries.get(0).getCurrentHash());
    }

    @Test
    void credentialRequiresRegisteredVoterWhoIsAtLeastEighteenOnVotingDay() throws Exception {
        VotingFixture underageFixture = createVotingFixture("voter.underage.vote@example.com", LocalDate.of(2010, 1, 1));

        requestCredential(underageFixture, PLACEHOLDER_BLINDED_TOKEN)
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

        requestCredential(fixture, PLACEHOLDER_BLINDED_TOKEN)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("This ballot requires a verified matching province, municipality and ward. Request an area correction if needed."));
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

        requestCredential(fixture, PLACEHOLDER_BLINDED_TOKEN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Election voting period is closed"));
    }

    @Test
    void credentialRejectsBlindedTokenOutsideTheKeyRange() throws Exception {
        VotingFixture fixture = createVotingFixture("voter.bad.blinding@example.com", LocalDate.of(1980, 1, 1));

        requestCredential(fixture, "AQ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Blinded ballot token is not valid for this contest's ballot key"));
        assertThat(votingRightRepository.count()).isZero();
    }

    @Test
    void anonymousBallotRejectsInvalidSignature() throws Exception {
        VotingFixture fixture = createVotingFixture("voter.invalid.credential@example.com", LocalDate.of(1980, 1, 1));
        IssuedBallot forged = new IssuedBallot(blindBallot(fixture).ballotToken(), "AQID");

        castBallot(fixture.contest(), fixture.optionA(), forged)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired voting credential"));
    }

    @Test
    void signatureForOneContestCannotBeUsedInAnother() throws Exception {
        VotingFixture fixture = createVotingFixture("voter.cross.contest@example.com", LocalDate.of(1980, 1, 1));
        Contest otherContest = contestRepository.save(new Contest(
                fixture.election(),
                "Municipal PR Councillors",
                ContestType.MUNICIPAL_PR,
                ContestStatus.OPEN,
                2,
                "Western Cape",
                "City of Cape Town",
                null
        ));
        ContestOption otherOption = contestOptionRepository.save(new ContestOption(
                otherContest,
                "Ubuntu Civic Party",
                ContestOptionType.PARTY,
                1
        ));
        mockMvc.perform(get("/api/v1/elections/{electionId}/contests/{contestId}/ballot-key",
                        fixture.election().getId(), otherContest.getId()))
                .andExpect(status().isOk());
        IssuedBallot issued = issueCredential(fixture);

        castBallot(otherContest, otherOption, issued).andExpect(status().isUnauthorized());
    }

    @Test
    void ballotKeyIsPublishedOnceVotingOpensAndStaysTheSame() throws Exception {
        VotingFixture fixture = createVotingFixture("voter.ballot.key@example.com", LocalDate.of(1980, 1, 1));

        MvcResult first = mockMvc.perform(get("/api/v1/elections/{electionId}/contests/{contestId}/ballot-key",
                        fixture.election().getId(), fixture.contest().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicExponent").value("AQAB"))
                .andExpect(jsonPath("$.tokenDomain").value("votetrust-ballot-token-v1"))
                .andReturn();
        MvcResult second = mockMvc.perform(get("/api/v1/elections/{electionId}/contests/{contestId}/ballot-key",
                        fixture.election().getId(), fixture.contest().getId()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat((String) JsonPath.read(second.getResponse().getContentAsString(), "$.keyFingerprint"))
                .isEqualTo(JsonPath.read(first.getResponse().getContentAsString(), "$.keyFingerprint"));
    }

    @Test
    void ballotKeyIsNotPublishedBeforeVotingOpens() throws Exception {
        VotingFixture fixture = createVotingFixture(
                "voter.draft.key@example.com",
                LocalDate.of(1980, 1, 1),
                ElectionStatus.REGISTRATION_CLOSED,
                ContestStatus.DRAFT,
                Instant.parse("2026-08-08T07:00:00Z"),
                Instant.parse("2026-08-08T21:00:00Z")
        );

        mockMvc.perform(get("/api/v1/elections/{electionId}/contests/{contestId}/ballot-key",
                        fixture.election().getId(), fixture.contest().getId()))
                .andExpect(status().isConflict());
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
        IssuedBallot issued = issueCredential(fixture);

        castBallot(fixture.contest(), blankOption, issued)
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

    private BlindBallotClient.BlindedBallot blindBallot(VotingFixture fixture) throws Exception {
        MvcResult keyResult = mockMvc.perform(get(
                        "/api/v1/elections/{electionId}/contests/{contestId}/ballot-key",
                        fixture.election().getId(),
                        fixture.contest().getId()
                ))
                .andExpect(status().isOk())
                .andReturn();
        String keyJson = keyResult.getResponse().getContentAsString();
        return BlindBallotClient.blind(
                fixture.contest().getId(),
                JsonPath.read(keyJson, "$.modulus"),
                JsonPath.read(keyJson, "$.publicExponent")
        );
    }

    private ResultActions requestCredential(VotingFixture fixture, String blindedToken) throws Exception {
        return mockMvc.perform(post(
                        "/api/v1/elections/{electionId}/contests/{contestId}/credentials",
                        fixture.election().getId(),
                        fixture.contest().getId()
                )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blindedToken\": \"%s\"}".formatted(blindedToken)));
    }

    private IssuedBallot issueCredential(VotingFixture fixture) throws Exception {
        BlindBallotClient.BlindedBallot ballot = blindBallot(fixture);
        MvcResult credentialResult = requestCredential(fixture, ballot.blindedToken())
                .andExpect(status().isCreated())
                .andReturn();
        String blindSignature = JsonPath.read(credentialResult.getResponse().getContentAsString(), "$.blindSignature");
        return new IssuedBallot(ballot.ballotToken(), ballot.unblind(blindSignature));
    }

    private ResultActions castBallot(Contest contest, ContestOption option, IssuedBallot issued) throws Exception {
        return mockMvc.perform(post("/api/v1/ballots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ballotBody(contest, option, issued.ballotToken(), issued.signature())));
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

        TrustedTestProfiles.verify(jdbcTemplate);
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
        TrustedTestProfiles.verify(jdbcTemplate);
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

    private String ballotBody(Contest contest, ContestOption contestOption, String ballotToken, String signature) {
        return """
                {
                  "contestId": "%s",
                  "contestOptionId": "%s",
                  "ballotToken": "%s",
                  "signature": "%s"
                }
                """.formatted(contest.getId(), contestOption.getId(), ballotToken, signature);
    }

    private record IssuedBallot(String ballotToken, String signature) {
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
