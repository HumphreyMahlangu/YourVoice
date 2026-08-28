package io.github.humphreymahlangu.votetrust.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.humphreymahlangu.votetrust.support.PostgreSqlTestContainerSupport;
import io.github.humphreymahlangu.votetrust.entity.AccountRole;
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
import io.github.humphreymahlangu.votetrust.repository.ElectionRegistrationRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionLifecycleEventRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRepository;
import io.github.humphreymahlangu.votetrust.repository.LedgerStateRepository;
import io.github.humphreymahlangu.votetrust.repository.UserAccountRepository;
import io.github.humphreymahlangu.votetrust.repository.VoterProfileRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingDistrictRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingRightRepository;
import io.github.humphreymahlangu.votetrust.security.IdentityHashService;
import io.github.humphreymahlangu.votetrust.service.VoteHashChainService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class TallyAuditIntegrationTest extends PostgreSqlTestContainerSupport {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-07T22:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VoteHashChainService voteHashChainService;

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
    void finalResultsReturnClosedContestTallies() throws Exception {
        ClosedContestFixture fixture = createClosedContestFixture();
        appendVote(fixture.contest(), fixture.optionA(), 300);
        appendVote(fixture.contest(), fixture.optionB(), 240);
        BallotLedgerEntry lastEntry = appendVote(fixture.contest(), fixture.optionA(), 180);

        mockMvc.perform(get(
                        "/api/v1/elections/{electionId}/contests/{contestId}/results",
                        fixture.election().getId(),
                        fixture.contest().getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredVoterCount").value(3))
                .andExpect(jsonPath("$.ballotsCast").value(3))
                .andExpect(jsonPath("$.validVotes").value(3))
                .andExpect(jsonPath("$.blankBallots").value(0))
                .andExpect(jsonPath("$.spoiltBallots").value(0))
                .andExpect(jsonPath("$.turnoutPercentage").value(100.0))
                .andExpect(jsonPath("$.ledgerHeadHash").value(lastEntry.getCurrentHash()))
                .andExpect(jsonPath("$.options.length()").value(2))
                .andExpect(jsonPath("$.options[0].contestOptionId").value(fixture.optionA().getId().toString()))
                .andExpect(jsonPath("$.options[0].voteCount").value(2))
                .andExpect(jsonPath("$.options[0].percentageOfValidVotes").value(66.67))
                .andExpect(jsonPath("$.options[0].leading").value(true))
                .andExpect(jsonPath("$.options[1].contestOptionId").value(fixture.optionB().getId().toString()))
                .andExpect(jsonPath("$.options[1].voteCount").value(1))
                .andExpect(jsonPath("$.options[1].percentageOfValidVotes").value(33.33))
                .andExpect(jsonPath("$.options[1].leading").value(false));
    }

    @Test
    void finalResultsSeparateBlankAndSpoiltBallotsFromValidVotes() throws Exception {
        ClosedContestFixture fixture = createClosedContestFixture();
        appendVote(fixture.contest(), fixture.optionA(), 300);
        appendVote(fixture.contest(), fixture.blankOption(), 240);
        appendVote(fixture.contest(), fixture.spoiltOption(), 180);

        mockMvc.perform(get(
                        "/api/v1/elections/{electionId}/contests/{contestId}/results",
                        fixture.election().getId(),
                        fixture.contest().getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredVoterCount").value(3))
                .andExpect(jsonPath("$.ballotsCast").value(3))
                .andExpect(jsonPath("$.validVotes").value(1))
                .andExpect(jsonPath("$.blankBallots").value(1))
                .andExpect(jsonPath("$.spoiltBallots").value(1))
                .andExpect(jsonPath("$.turnoutPercentage").value(100.0))
                .andExpect(jsonPath("$.options.length()").value(2))
                .andExpect(jsonPath("$.options[0].contestOptionId").value(fixture.optionA().getId().toString()))
                .andExpect(jsonPath("$.options[0].voteCount").value(1))
                .andExpect(jsonPath("$.options[0].percentageOfValidVotes").value(100.0))
                .andExpect(jsonPath("$.options[0].leading").value(true))
                .andExpect(jsonPath("$.options[1].contestOptionId").value(fixture.optionB().getId().toString()))
                .andExpect(jsonPath("$.options[1].voteCount").value(0))
                .andExpect(jsonPath("$.options[1].percentageOfValidVotes").value(0.0))
                .andExpect(jsonPath("$.options[1].leading").value(false));
    }

    @Test
    void resultsAreBlockedUntilVotingHasClosed() throws Exception {
        ClosedContestFixture fixture = createContestFixture(
                ElectionStatus.VOTING_OPEN,
                ContestStatus.OPEN,
                FIXED_NOW.minusSeconds(3600),
                FIXED_NOW.plusSeconds(3600)
        );

        mockMvc.perform(get(
                        "/api/v1/elections/{electionId}/contests/{contestId}/results",
                        fixture.election().getId(),
                        fixture.contest().getId()
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Contest results are available only after voting has closed"));
    }

    @Test
    void auditVerifiesClosedContestHashChainAndPublicLedger() throws Exception {
        ClosedContestFixture fixture = createClosedContestFixture();
        BallotLedgerEntry firstEntry = appendVote(fixture.contest(), fixture.optionA(), 300);
        appendVote(fixture.contest(), fixture.optionB(), 240);
        BallotLedgerEntry lastEntry = appendVote(fixture.contest(), fixture.optionA(), 180);

        MvcResult auditResult = mockMvc.perform(get(
                        "/api/v1/elections/{electionId}/contests/{contestId}/audit",
                        fixture.election().getId(),
                        fixture.contest().getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chainValid").value(true))
                .andExpect(jsonPath("$.ledgerEntryCount").value(3))
                .andExpect(jsonPath("$.genesisHash").value(LedgerState.GENESIS_HASH))
                .andExpect(jsonPath("$.computedHeadHash").value(lastEntry.getCurrentHash()))
                .andExpect(jsonPath("$.storedHeadHash").value(lastEntry.getCurrentHash()))
                .andExpect(jsonPath("$.storedNextLedgerIndex").value(3))
                .andReturn();
        List<String> violations = JsonPath.read(auditResult.getResponse().getContentAsString(), "$.violations");
        assertThat(violations).isEmpty();

        mockMvc.perform(get(
                        "/api/v1/elections/{electionId}/contests/{contestId}/ledger",
                        fixture.election().getId(),
                        fixture.contest().getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ledgerIndex").value(0))
                .andExpect(jsonPath("$[0].contestOptionId").value(fixture.optionA().getId().toString()))
                .andExpect(jsonPath("$[0].previousHash").value(LedgerState.GENESIS_HASH))
                .andExpect(jsonPath("$[0].currentHash").value(firstEntry.getCurrentHash()))
                .andExpect(jsonPath("$[0].nonce").isNotEmpty())
                .andExpect(jsonPath("$[0].recordedDate").value("2026-08-07"))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].castAt").doesNotExist());
    }

    @Test
    void auditDetectsTamperedLedgerEntry() throws Exception {
        ClosedContestFixture fixture = createClosedContestFixture();
        appendVote(fixture.contest(), fixture.optionA(), 300);
        appendVote(fixture.contest(), fixture.optionB(), 240);

        jdbcTemplate.update(
                "update ballot_ledger_entries set current_hash = ? where contest_id = ? and ledger_index = ?",
                "f".repeat(64),
                fixture.contest().getId(),
                1L
        );

        MvcResult result = mockMvc.perform(get(
                        "/api/v1/elections/{electionId}/contests/{contestId}/audit",
                        fixture.election().getId(),
                        fixture.contest().getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chainValid").value(false))
                .andReturn();
        List<String> violations = JsonPath.read(result.getResponse().getContentAsString(), "$.violations");
        assertThat(violations).contains("Ledger entry 1 current hash does not match its payload");
    }

    private ClosedContestFixture createClosedContestFixture() {
        return createContestFixture(
                ElectionStatus.COMPLETED,
                ContestStatus.CLOSED,
                FIXED_NOW.minusSeconds(7200),
                FIXED_NOW.minusSeconds(600)
        );
    }

    private ClosedContestFixture createContestFixture(
            ElectionStatus electionStatus,
            ContestStatus contestStatus,
            Instant votingStartAt,
            Instant votingEndAt
    ) {
        VotingDistrict district = votingDistrictRepository.save(new VotingDistrict(
                "WC001-9001",
                "Cape Town Ward 1 Results Station",
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
                1
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
        ContestOption blankOption = contestOptionRepository.save(new ContestOption(
                contest,
                "Blank ballot",
                ContestOptionType.BLANK_BALLOT,
                98
        ));
        ContestOption spoiltOption = contestOptionRepository.save(new ContestOption(
                contest,
                "Spoilt ballot",
                ContestOptionType.SPOILT_BALLOT,
                99
        ));

        seedRegisteredVoter(election, district, "results.voter.one@example.com");
        seedRegisteredVoter(election, district, "results.voter.two@example.com");
        seedRegisteredVoter(election, district, "results.voter.three@example.com");

        return new ClosedContestFixture(election, contest, optionA, optionB, blankOption, spoiltOption);
    }

    private void seedRegisteredVoter(Election election, VotingDistrict district, String email) {
        UserAccount account = userAccountRepository.save(new UserAccount(
                email,
                "hashed-password-for-results-test",
                AccountRole.VOTER,
                true
        ));
        VoterProfile voterProfile = voterProfileRepository.save(new VoterProfile(
                account,
                "Results Test Voter",
                identityHashService.hashSouthAfricanIdNumber(email),
                IdDocumentType.SMART_ID_CARD,
                LocalDate.of(1980, 1, 1),
                district
        ));
        electionRegistrationRepository.save(new ElectionRegistration(
                voterProfile,
                election,
                district,
                RegistrationStatus.ACTIVE,
                FIXED_NOW.minusSeconds(10_000)
        ));
    }

    private BallotLedgerEntry appendVote(Contest contest, ContestOption contestOption, long secondsBeforeNow) {
        return voteHashChainService.appendVote(
                contest,
                contestOption,
                FIXED_NOW.minusSeconds(secondsBeforeNow)
        );
    }

    private record ClosedContestFixture(
            Election election,
            Contest contest,
            ContestOption optionA,
            ContestOption optionB,
            ContestOption blankOption,
            ContestOption spoiltOption
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
