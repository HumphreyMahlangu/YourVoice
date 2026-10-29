package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.dto.ContestAuditResponse;
import io.github.humphreymahlangu.yourvoice.dto.ContestLedgerEntryResponse;
import io.github.humphreymahlangu.yourvoice.dto.ContestOptionResultResponse;
import io.github.humphreymahlangu.yourvoice.dto.ContestOptionTallyRow;
import io.github.humphreymahlangu.yourvoice.dto.ContestResultResponse;
import io.github.humphreymahlangu.yourvoice.dto.DistrictRegistrationCount;
import io.github.humphreymahlangu.yourvoice.entity.BallotLedgerEntry;
import io.github.humphreymahlangu.yourvoice.entity.Contest;
import io.github.humphreymahlangu.yourvoice.entity.ContestOptionType;
import io.github.humphreymahlangu.yourvoice.entity.ContestStatus;
import io.github.humphreymahlangu.yourvoice.entity.Election;
import io.github.humphreymahlangu.yourvoice.entity.ElectionStatus;
import io.github.humphreymahlangu.yourvoice.entity.LedgerState;
import io.github.humphreymahlangu.yourvoice.entity.PublishedBallotEntry;
import io.github.humphreymahlangu.yourvoice.entity.RegistrationStatus;
import io.github.humphreymahlangu.yourvoice.exception.ResourceNotFoundException;
import io.github.humphreymahlangu.yourvoice.exception.ResultsUnavailableException;
import io.github.humphreymahlangu.yourvoice.repository.BallotLedgerEntryRepository;
import io.github.humphreymahlangu.yourvoice.repository.ContestRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRegistrationRepository;
import io.github.humphreymahlangu.yourvoice.repository.PublishedBallotEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TallyAuditService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final ContestRepository contestRepository;
    private final BallotLedgerEntryRepository ballotLedgerEntryRepository;
    private final PublishedBallotEntryRepository publishedBallotEntryRepository;
    private final ElectionRegistrationRepository electionRegistrationRepository;
    private final VoteHashChainService voteHashChainService;
    private final LedgerPublicationService ledgerPublicationService;
    private final Clock clock;

    public TallyAuditService(
            ContestRepository contestRepository,
            BallotLedgerEntryRepository ballotLedgerEntryRepository,
            PublishedBallotEntryRepository publishedBallotEntryRepository,
            ElectionRegistrationRepository electionRegistrationRepository,
            VoteHashChainService voteHashChainService,
            LedgerPublicationService ledgerPublicationService,
            Clock clock
    ) {
        this.contestRepository = contestRepository;
        this.ballotLedgerEntryRepository = ballotLedgerEntryRepository;
        this.publishedBallotEntryRepository = publishedBallotEntryRepository;
        this.electionRegistrationRepository = electionRegistrationRepository;
        this.voteHashChainService = voteHashChainService;
        this.ledgerPublicationService = ledgerPublicationService;
        this.clock = clock;
    }

    // Not read-only: the first read after a contest closes publishes its ledger if the lifecycle has not yet.
    @Transactional
    public ContestResultResponse getContestResult(UUID electionId, UUID contestId) {
        Contest contest = findClosedContest(electionId, contestId);
        LedgerState ledgerState = ledgerPublicationService.ensurePublished(contest);

        List<ContestOptionTallyRow> tallyRows = ballotLedgerEntryRepository.tallyContestOptions(contestId);
        long validVotes = tallyRows.stream()
                .filter(tallyRow -> tallyRow.optionType().isValidVote())
                .mapToLong(ContestOptionTallyRow::voteCount)
                .sum();
        long blankBallots = countVotesByOptionType(tallyRows, ContestOptionType.BLANK_BALLOT);
        long spoiltBallots = countVotesByOptionType(tallyRows, ContestOptionType.SPOILT_BALLOT);
        long ballotsCast = ballotLedgerEntryRepository.countByContestId(contestId);
        long registeredVoterCount = countEligibleRegisteredVoters(contest);
        long unverifiedRegistrationCount = electionRegistrationRepository.countByElectionIdAndStatusAndIdentityVerifiedFalse(electionId, RegistrationStatus.ACTIVE);
        long highestVoteCount = tallyRows.stream()
                .filter(tallyRow -> tallyRow.optionType().isValidVote())
                .mapToLong(ContestOptionTallyRow::voteCount)
                .max()
                .orElse(0);
        List<ContestOptionResultResponse> optionResults = tallyRows.stream()
                .filter(tallyRow -> tallyRow.optionType().isValidVote())
                .map(tallyRow -> toOptionResult(tallyRow, validVotes, highestVoteCount))
                .toList();

        return new ContestResultResponse(
                electionId,
                contestId,
                contest.getName(),
                contest.getType(),
                registeredVoterCount,
                unverifiedRegistrationCount,
                ballotsCast,
                validVotes,
                blankBallots,
                spoiltBallots,
                unverifiedRegistrationCount == 0 ? percentage(ballotsCast, registeredVoterCount) : null,
                ledgerState.getPublishedHeadHash(),
                Instant.now(clock),
                optionResults
        );
    }

    @Transactional
    public ContestAuditResponse verifyContestLedger(UUID electionId, UUID contestId) {
        Contest contest = findClosedContest(electionId, contestId);
        LedgerState ledgerState = ledgerPublicationService.ensurePublished(contest);
        List<String> violations = new ArrayList<>();

        List<BallotLedgerEntry> entries = ballotLedgerEntryRepository.findByContestIdOrderByLedgerIndexAsc(contestId);
        String computedHeadHash = verifyInternalChain(entries, violations);
        long expectedNextIndex = entries.size();
        if (!Objects.equals(ledgerState.getCurrentHash(), computedHeadHash)) {
            violations.add("Stored ledger head does not match the computed chain head");
        }
        if (!Objects.equals(ledgerState.getNextLedgerIndex(), expectedNextIndex)) {
            violations.add("Stored next ledger index does not match the number of ledger entries");
        }

        List<PublishedBallotEntry> publishedEntries = publishedBallotEntryRepository.findByContestIdOrderByPositionAsc(contestId);
        String computedPublishedHeadHash = verifyPublishedChain(contestId, publishedEntries, violations);
        if (!Objects.equals(ledgerState.getPublishedHeadHash(), computedPublishedHeadHash)) {
            violations.add("Stored published ledger head does not match the computed published chain head");
        }
        if (!Objects.equals(ledgerState.getPublishedEntryCount(), (long) publishedEntries.size())) {
            violations.add("Stored published entry count does not match the published ledger");
        }
        if (!countSelections(entries).equals(countPublishedSelections(publishedEntries))) {
            violations.add("Published ledger does not contain the same ballots as the internal ledger");
        }

        return new ContestAuditResponse(
                electionId,
                contestId,
                violations.isEmpty(),
                entries.size(),
                LedgerState.GENESIS_HASH,
                computedHeadHash,
                ledgerState.getCurrentHash(),
                ledgerState.getNextLedgerIndex(),
                publishedEntries.size(),
                computedPublishedHeadHash,
                ledgerState.getPublishedHeadHash(),
                Instant.now(clock),
                List.copyOf(violations)
        );
    }

    @Transactional
    public List<ContestLedgerEntryResponse> listContestLedger(UUID electionId, UUID contestId) {
        Contest contest = findClosedContest(electionId, contestId);
        ledgerPublicationService.ensurePublished(contest);

        return publishedBallotEntryRepository.findByContestIdOrderByPositionAsc(contestId).stream()
                .map(entry -> new ContestLedgerEntryResponse(
                        entry.getPosition(),
                        entry.getContestOption().getId(),
                        entry.getPreviousHash(),
                        entry.getCurrentHash(),
                        entry.getNonce()
                ))
                .toList();
    }

    private String verifyInternalChain(List<BallotLedgerEntry> entries, List<String> violations) {
        String computedHeadHash = LedgerState.GENESIS_HASH;
        long expectedLedgerIndex = 0;
        for (BallotLedgerEntry entry : entries) {
            if (!Objects.equals(entry.getLedgerIndex(), expectedLedgerIndex)) {
                violations.add("Ledger index " + expectedLedgerIndex + " is missing or out of order");
            }
            if (!Objects.equals(entry.getPreviousHash(), computedHeadHash)) {
                violations.add("Ledger entry " + entry.getLedgerIndex() + " previous hash does not match the computed chain head");
            }

            String recalculatedHash = voteHashChainService.calculateHash(entry);
            if (!Objects.equals(entry.getCurrentHash(), recalculatedHash)) {
                violations.add("Ledger entry " + entry.getLedgerIndex() + " current hash does not match its payload");
            }

            computedHeadHash = recalculatedHash;
            expectedLedgerIndex++;
        }
        return computedHeadHash;
    }

    private String verifyPublishedChain(UUID contestId, List<PublishedBallotEntry> entries, List<String> violations) {
        String computedHeadHash = LedgerState.GENESIS_HASH;
        long expectedPosition = 0;
        for (PublishedBallotEntry entry : entries) {
            if (!Objects.equals(entry.getPosition(), expectedPosition)) {
                violations.add("Published position " + expectedPosition + " is missing or out of order");
            }
            if (!Objects.equals(entry.getPreviousHash(), computedHeadHash)) {
                violations.add("Published entry " + entry.getPosition() + " previous hash does not match the computed chain head");
            }

            String recalculatedHash = ledgerPublicationService.calculatePublicHash(
                    contestId,
                    entry.getContestOption().getId(),
                    entry.getPosition(),
                    entry.getPreviousHash(),
                    entry.getNonce()
            );
            if (!Objects.equals(entry.getCurrentHash(), recalculatedHash)) {
                violations.add("Published entry " + entry.getPosition() + " current hash does not match its payload");
            }

            computedHeadHash = recalculatedHash;
            expectedPosition++;
        }
        return computedHeadHash;
    }

    private Map<UUID, Long> countSelections(List<BallotLedgerEntry> entries) {
        return entries.stream()
                .collect(Collectors.groupingBy(entry -> entry.getContestOption().getId(), Collectors.counting()));
    }

    private Map<UUID, Long> countPublishedSelections(List<PublishedBallotEntry> entries) {
        return entries.stream()
                .collect(Collectors.groupingBy(entry -> entry.getContestOption().getId(), Collectors.counting()));
    }

    private long countEligibleRegisteredVoters(Contest contest) {
        return electionRegistrationRepository.countByDistrict(contest.getElection().getId(), RegistrationStatus.ACTIVE)
                .stream()
                .filter(district -> ContestEligibility.isEligible(
                        contest,
                        district.province(),
                        district.municipality(),
                        district.wardNumber()
                ))
                .mapToLong(DistrictRegistrationCount::registrationCount)
                .sum();
    }

    private long countVotesByOptionType(List<ContestOptionTallyRow> tallyRows, ContestOptionType optionType) {
        return tallyRows.stream()
                .filter(tallyRow -> tallyRow.optionType() == optionType)
                .mapToLong(ContestOptionTallyRow::voteCount)
                .sum();
    }

    private Contest findClosedContest(UUID electionId, UUID contestId) {
        Contest contest = contestRepository.findByIdAndElectionId(contestId, electionId)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found"));
        assertResultsAvailable(contest);
        return contest;
    }

    private void assertResultsAvailable(Contest contest) {
        Election election = contest.getElection();
        boolean votingWindowEnded = Instant.now(clock).isAfter(election.getVotingEndAt());
        if (election.getStatus() != ElectionStatus.COMPLETED
                || contest.getStatus() != ContestStatus.CLOSED
                || !votingWindowEnded) {
            throw new ResultsUnavailableException("Contest results are available only after voting has closed");
        }
    }

    private ContestOptionResultResponse toOptionResult(
            ContestOptionTallyRow tallyRow,
            long validVotes,
            long highestVoteCount
    ) {
        return new ContestOptionResultResponse(
                tallyRow.contestOptionId(),
                tallyRow.name(),
                tallyRow.optionType(),
                tallyRow.displayOrder(),
                tallyRow.voteCount(),
                percentage(tallyRow.voteCount(), validVotes),
                highestVoteCount > 0 && tallyRow.voteCount() == highestVoteCount
        );
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
