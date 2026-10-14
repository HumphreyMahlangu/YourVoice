package io.github.humphreymahlangu.votetrust.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.humphreymahlangu.votetrust.dto.BallotCastRequest;
import io.github.humphreymahlangu.votetrust.dto.VotingCredentialRequest;
import io.github.humphreymahlangu.votetrust.dto.VotingCredentialResponse;
import io.github.humphreymahlangu.votetrust.entity.AccountRole;
import io.github.humphreymahlangu.votetrust.entity.BallotLedgerEntry;
import io.github.humphreymahlangu.votetrust.entity.Contest;
import io.github.humphreymahlangu.votetrust.entity.ContestOption;
import io.github.humphreymahlangu.votetrust.entity.ContestOptionType;
import io.github.humphreymahlangu.votetrust.entity.ContestSigningKey;
import io.github.humphreymahlangu.votetrust.entity.ContestStatus;
import io.github.humphreymahlangu.votetrust.entity.ContestType;
import io.github.humphreymahlangu.votetrust.entity.Election;
import io.github.humphreymahlangu.votetrust.entity.ElectionLifecycleEvent;
import io.github.humphreymahlangu.votetrust.entity.ElectionLifecycleOutcome;
import io.github.humphreymahlangu.votetrust.entity.ElectionRegistration;
import io.github.humphreymahlangu.votetrust.entity.ElectionStatus;
import io.github.humphreymahlangu.votetrust.entity.ElectionType;
import io.github.humphreymahlangu.votetrust.entity.IdDocumentType;
import io.github.humphreymahlangu.votetrust.entity.LedgerState;
import io.github.humphreymahlangu.votetrust.entity.RegistrationStatus;
import io.github.humphreymahlangu.votetrust.entity.UserAccount;
import io.github.humphreymahlangu.votetrust.entity.VoterProfile;
import io.github.humphreymahlangu.votetrust.entity.VotingDistrict;
import io.github.humphreymahlangu.votetrust.exception.DuplicateResourceException;
import io.github.humphreymahlangu.votetrust.exception.ElectionLifecycleException;
import io.github.humphreymahlangu.votetrust.repository.BallotLedgerEntryRepository;
import io.github.humphreymahlangu.votetrust.repository.ContestOptionRepository;
import io.github.humphreymahlangu.votetrust.repository.ContestRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionLifecycleEventRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRegistrationRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRepository;
import io.github.humphreymahlangu.votetrust.repository.LedgerStateRepository;
import io.github.humphreymahlangu.votetrust.repository.PublishedBallotEntryRepository;
import io.github.humphreymahlangu.votetrust.repository.UserAccountRepository;
import io.github.humphreymahlangu.votetrust.repository.VoterProfileRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingDistrictRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingRightRepository;
import io.github.humphreymahlangu.votetrust.security.IdentityHashService;
import io.github.humphreymahlangu.votetrust.support.BlindBallotClient;
import io.github.humphreymahlangu.votetrust.support.DatabaseCleaner;
import io.github.humphreymahlangu.votetrust.support.PostgreSqlTestContainerSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises the locking that enforces one person, one vote: many threads are released at the same instant against
 * a real PostgreSQL database, and the outcome must be exactly what a serial execution would produce.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ConcurrentVotingIntegrationTest extends PostgreSqlTestContainerSupport {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-07T10:00:00Z");
    private static final int THREADS = 8;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VotingService votingService;

    @Autowired
    private BallotSigningKeyService ballotSigningKeyService;

    @Autowired
    private LedgerPublicationService ledgerPublicationService;

    @Autowired
    private ElectionLifecycleService electionLifecycleService;

    @Autowired
    private VoteHashChainService voteHashChainService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private VoterProfileRepository voterProfileRepository;

    @Autowired
    private VotingDistrictRepository votingDistrictRepository;

    @Autowired
    private ElectionRepository electionRepository;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private ContestOptionRepository contestOptionRepository;

    @Autowired
    private ElectionRegistrationRepository electionRegistrationRepository;

    @Autowired
    private VotingRightRepository votingRightRepository;

    @Autowired
    private BallotLedgerEntryRepository ballotLedgerEntryRepository;

    @Autowired
    private LedgerStateRepository ledgerStateRepository;

    @Autowired
    private PublishedBallotEntryRepository publishedBallotEntryRepository;

    @Autowired
    private ElectionLifecycleEventRepository lifecycleEventRepository;

    @Autowired
    private IdentityHashService identityHashService;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void theSameBallotTokenCastConcurrentlyIsCountedExactlyOnce() throws Exception {
        Fixture fixture = createOpenContest();
        UUID voter = registerVoter(fixture, "concurrent.same.token@example.com");
        IssuedBallot ballot = issue(fixture, voter);

        List<Outcome> outcomes = runConcurrently(THREADS, attempt -> () ->
                votingService.castBallot(new BallotCastRequest(
                        fixture.contest().getId(),
                        fixture.option().getId(),
                        ballot.token(),
                        ballot.signature()
                )));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allSatisfy(outcome -> assertThat(outcome.error()).isInstanceOf(DuplicateResourceException.class));
        assertThat(ballotLedgerEntryRepository.countByContestId(fixture.contest().getId())).isEqualTo(1);
    }

    @Test
    void concurrentIssuanceForOneVoterSignsOnlyOneBlindedToken() throws Exception {
        Fixture fixture = createOpenContest();
        UUID voter = registerVoter(fixture, "concurrent.issuance@example.com");
        List<BlindBallotClient.BlindedBallot> blindedBallots = new ArrayList<>();
        for (int index = 0; index < THREADS; index++) {
            blindedBallots.add(blind(fixture));
        }

        List<Outcome> outcomes = runConcurrently(THREADS, attempt -> () ->
                votingService.issueVotingCredential(
                        voter,
                        fixture.election().getId(),
                        fixture.contest().getId(),
                        new VotingCredentialRequest(blindedBallots.get(attempt).blindedToken())
                ));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allSatisfy(outcome -> assertThat(outcome.error())
                        .isInstanceOf(DuplicateResourceException.class)
                        .hasMessage("A voting credential has already been issued for this contest"));
        assertThat(votingRightRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentFirstBallotsFromManyVotersFormOneUnbrokenChain() throws Exception {
        Fixture fixture = createOpenContest();
        List<IssuedBallot> ballots = new ArrayList<>();
        for (int index = 0; index < THREADS; index++) {
            UUID voter = registerVoter(fixture, "concurrent.voter." + index + "@example.com");
            ballots.add(issue(fixture, voter));
        }

        List<Outcome> outcomes = runConcurrently(THREADS, attempt -> () ->
                votingService.castBallot(new BallotCastRequest(
                        fixture.contest().getId(),
                        fixture.option().getId(),
                        ballots.get(attempt).token(),
                        ballots.get(attempt).signature()
                )));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        List<BallotLedgerEntry> entries = ballotLedgerEntryRepository
                .findByContestIdOrderByLedgerIndexAsc(fixture.contest().getId());
        assertThat(entries).extracting(BallotLedgerEntry::getLedgerIndex)
                .containsExactly(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L);
        String previousHash = LedgerState.GENESIS_HASH;
        for (BallotLedgerEntry entry : entries) {
            assertThat(entry.getPreviousHash()).isEqualTo(previousHash);
            assertThat(voteHashChainService.calculateHash(entry)).isEqualTo(entry.getCurrentHash());
            previousHash = entry.getCurrentHash();
        }
        LedgerState ledgerState = ledgerStateRepository.findByContestId(fixture.contest().getId()).orElseThrow();
        assertThat(ledgerState.getCurrentHash()).isEqualTo(previousHash);
        assertThat(ledgerState.getNextLedgerIndex()).isEqualTo(THREADS);
    }

    @Test
    void concurrentPublicationBuildsTheLedgerOnce() throws Exception {
        Fixture fixture = createOpenContest();
        for (int index = 0; index < 5; index++) {
            voteHashChainService.appendVote(fixture.contest(), fixture.option(), FIXED_NOW);
        }

        List<Outcome> outcomes = runConcurrently(THREADS, attempt -> () ->
                ledgerPublicationService.ensurePublished(fixture.contest()));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(publishedBallotEntryRepository.findByContestIdOrderByPositionAsc(fixture.contest().getId()))
                .hasSize(5);
    }

    @Test
    void schedulerCompletionAndAdministratorCancellationNeverBothSucceed() throws Exception {
        Fixture fixture = createOpenContest(FIXED_NOW.minusSeconds(7200), FIXED_NOW.minusSeconds(60));
        UUID electionId = fixture.election().getId();

        List<Outcome> outcomes = runConcurrently(2, attempt -> () -> {
            if (attempt == 0) {
                electionLifecycleService.advanceElection(electionId);
            } else {
                electionLifecycleService.cancelElection(electionId, UUID.randomUUID(), "admin@example.com");
            }
            return null;
        });

        ElectionStatus finalStatus = electionRepository.findById(electionId).orElseThrow().getStatus();
        assertThat(finalStatus).isIn(ElectionStatus.COMPLETED, ElectionStatus.CANCELLED);
        List<ElectionLifecycleEvent> terminalEvents = lifecycleEventRepository.findByElectionIdOrderByEventSequenceAsc(electionId)
                .stream()
                .filter(event -> event.getOutcome() == ElectionLifecycleOutcome.SUCCESS)
                .filter(event -> event.getNewStatus() == ElectionStatus.COMPLETED
                        || event.getNewStatus() == ElectionStatus.CANCELLED)
                .toList();
        assertThat(terminalEvents).singleElement()
                .extracting(ElectionLifecycleEvent::getNewStatus)
                .isEqualTo(finalStatus);
        if (finalStatus == ElectionStatus.COMPLETED) {
            assertThat(outcomes.get(1).error()).isInstanceOf(ElectionLifecycleException.class);
        }
    }

    private List<Outcome> runConcurrently(int threads, AttemptFactory attemptFactory) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int attempt = 0; attempt < threads; attempt++) {
                Callable<?> work = attemptFactory.create(attempt);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        work.call();
                        return new Outcome(null);
                    } catch (Exception exception) {
                        return new Outcome(exception);
                    }
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                try {
                    outcomes.add(future.get(60, TimeUnit.SECONDS));
                } catch (ExecutionException exception) {
                    throw new IllegalStateException(exception.getCause());
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private Fixture createOpenContest() {
        return createOpenContest(FIXED_NOW.minusSeconds(3600), FIXED_NOW.plusSeconds(3600));
    }

    private Fixture createOpenContest(Instant votingStartAt, Instant votingEndAt) {
        VotingDistrict district = votingDistrictRepository.save(new VotingDistrict(
                "WC001-7001",
                "Cape Town Concurrency Station",
                "Western Cape",
                "City of Cape Town",
                1
        ));
        Election election = electionRepository.save(new Election(
                "Concurrency Election",
                ElectionType.NATIONAL,
                votingStartAt.minusSeconds(86_400),
                votingStartAt.minusSeconds(3600),
                votingStartAt,
                votingEndAt,
                ElectionStatus.VOTING_OPEN
        ));
        Contest contest = contestRepository.save(new Contest(
                election,
                "National Assembly",
                ContestType.NATIONAL,
                ContestStatus.OPEN,
                1
        ));
        ContestOption option = contestOptionRepository.save(new ContestOption(contest, "Ubuntu Civic Party", ContestOptionType.PARTY, 1));
        contestOptionRepository.save(new ContestOption(contest, "Future Youth Movement", ContestOptionType.PARTY, 2));
        return new Fixture(election, district, contest, option);
    }

    private UUID registerVoter(Fixture fixture, String email) {
        UserAccount account = userAccountRepository.save(new UserAccount(email, "unused-password-hash", AccountRole.VOTER, true));
        VoterProfile profile = voterProfileRepository.save(new VoterProfile(
                account,
                "Concurrency Voter",
                identityHashService.hashSouthAfricanIdNumber(email),
                IdDocumentType.SMART_ID_CARD,
                LocalDate.of(1980, 1, 1),
                fixture.district()
        ));
        electionRegistrationRepository.save(new ElectionRegistration(
                profile,
                fixture.election(),
                fixture.district(),
                RegistrationStatus.ACTIVE,
                FIXED_NOW.minusSeconds(86_400)
        ));
        return account.getId();
    }

    private BlindBallotClient.BlindedBallot blind(Fixture fixture) {
        ContestSigningKey key = ballotSigningKeyService.getOrCreate(fixture.contest().getId());
        return BlindBallotClient.blind(fixture.contest().getId(), key.getModulus(), key.getPublicExponent());
    }

    private IssuedBallot issue(Fixture fixture, UUID voter) {
        BlindBallotClient.BlindedBallot blinded = blind(fixture);
        VotingCredentialResponse response = votingService.issueVotingCredential(
                voter,
                fixture.election().getId(),
                fixture.contest().getId(),
                new VotingCredentialRequest(blinded.blindedToken())
        );
        return new IssuedBallot(blinded.ballotToken(), blinded.unblind(response.blindSignature()));
    }

    @FunctionalInterface
    private interface AttemptFactory {
        Callable<?> create(int attempt);
    }

    private record Outcome(Exception error) {
        boolean succeeded() {
            return error == null;
        }
    }

    private record Fixture(Election election, VotingDistrict district, Contest contest, ContestOption option) {
    }

    private record IssuedBallot(String token, String signature) {
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
