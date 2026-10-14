package io.github.humphreymahlangu.votetrust.service;

import io.github.humphreymahlangu.votetrust.dto.ElectionLifecycleEventResponse;
import io.github.humphreymahlangu.votetrust.entity.Contest;
import io.github.humphreymahlangu.votetrust.entity.ContestOptionType;
import io.github.humphreymahlangu.votetrust.entity.ContestStatus;
import io.github.humphreymahlangu.votetrust.entity.Election;
import io.github.humphreymahlangu.votetrust.entity.ElectionLifecycleEvent;
import io.github.humphreymahlangu.votetrust.entity.ElectionLifecycleOutcome;
import io.github.humphreymahlangu.votetrust.entity.ElectionLifecycleTrigger;
import io.github.humphreymahlangu.votetrust.entity.ElectionStatus;
import io.github.humphreymahlangu.votetrust.exception.ElectionLifecycleException;
import io.github.humphreymahlangu.votetrust.exception.ResourceNotFoundException;
import io.github.humphreymahlangu.votetrust.repository.ContestOptionRepository;
import io.github.humphreymahlangu.votetrust.repository.ContestRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionLifecycleEventRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ElectionLifecycleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElectionLifecycleService.class);
    private static final List<ContestOptionType> VALID_VOTE_OPTION_TYPES = List.of(
            ContestOptionType.PARTY,
            ContestOptionType.INDEPENDENT_CANDIDATE
    );

    private final ElectionRepository electionRepository;
    private final ContestRepository contestRepository;
    private final ContestOptionRepository contestOptionRepository;
    private final ElectionLifecycleEventRepository lifecycleEventRepository;
    private final LedgerPublicationService ledgerPublicationService;
    private final BallotSigningKeyService ballotSigningKeyService;
    private final Clock clock;

    public ElectionLifecycleService(
            ElectionRepository electionRepository,
            ContestRepository contestRepository,
            ContestOptionRepository contestOptionRepository,
            ElectionLifecycleEventRepository lifecycleEventRepository,
            LedgerPublicationService ledgerPublicationService,
            BallotSigningKeyService ballotSigningKeyService,
            Clock clock
    ) {
        this.electionRepository = electionRepository;
        this.contestRepository = contestRepository;
        this.contestOptionRepository = contestOptionRepository;
        this.lifecycleEventRepository = lifecycleEventRepository;
        this.ledgerPublicationService = ledgerPublicationService;
        this.ballotSigningKeyService = ballotSigningKeyService;
        this.clock = clock;
    }

    @Transactional
    public void advanceElection(UUID electionId) {
        Election election = getElectionForUpdateOrThrow(electionId);
        Instant now = Instant.now(clock);

        try {
            advanceDueTransitions(election, now);
        } catch (ElectionLifecycleException exception) {
            if (recordFailureOnce(election, exception.getMessage(), now)) {
                LOGGER.warn("Election {} lifecycle transition failed closed: {}", electionId, exception.getMessage());
            }
        }
    }

    @Transactional
    public Election cancelElection(UUID electionId, UUID actorUserId, String actorEmail) {
        Election election = getElectionForUpdateOrThrow(electionId);
        ElectionStatus previousStatus = election.getStatus();

        if (previousStatus == ElectionStatus.CANCELLED) {
            return election;
        }
        if (previousStatus == ElectionStatus.COMPLETED) {
            throw new ElectionLifecycleException("A completed election cannot be cancelled");
        }

        closeOpenContests(electionId);
        election.transitionTo(ElectionStatus.CANCELLED);
        lifecycleEventRepository.save(new ElectionLifecycleEvent(
                electionId,
                previousStatus,
                ElectionStatus.CANCELLED,
                ElectionLifecycleTrigger.ADMINISTRATOR,
                ElectionLifecycleOutcome.SUCCESS,
                actorUserId,
                actorEmail,
                "Election cancelled by an administrator",
                Instant.now(clock)
        ));
        return election;
    }

    @Transactional(readOnly = true)
    public List<ElectionLifecycleEventResponse> listEvents(UUID electionId) {
        if (!electionRepository.existsById(electionId)) {
            throw new ResourceNotFoundException("Election not found");
        }
        return lifecycleEventRepository.findByElectionIdOrderByEventSequenceAsc(electionId)
                .stream()
                .map(ElectionLifecycleEventResponse::from)
                .toList();
    }

    private void advanceDueTransitions(Election election, Instant now) {
        if (election.getStatus() == ElectionStatus.DRAFT && hasReached(now, election.getRegistrationStartAt())) {
            validateBallotReady(election.getId());
            transition(
                    election,
                    ElectionStatus.REGISTRATION_OPEN,
                    "Registration opened automatically",
                    now
            );
        }

        if (election.getStatus() == ElectionStatus.REGISTRATION_OPEN
                && hasReached(now, election.getRegistrationEndAt())) {
            transition(
                    election,
                    ElectionStatus.REGISTRATION_CLOSED,
                    "Registration closed automatically",
                    now
            );
        }

        if (election.getStatus() == ElectionStatus.REGISTRATION_CLOSED
                && hasReached(now, election.getVotingStartAt())) {
            int openedContests = openContests(election.getId());
            transition(
                    election,
                    ElectionStatus.VOTING_OPEN,
                    "Voting opened automatically and " + openedContests + " contests were opened",
                    now
            );
        }

        if (election.getStatus() == ElectionStatus.VOTING_OPEN && hasReached(now, election.getVotingEndAt())) {
            int closedContests = closeAllContests(election.getId());
            transition(
                    election,
                    ElectionStatus.COMPLETED,
                    "Voting closed automatically and " + closedContests + " contests were closed",
                    now
            );
        }
    }

    private int openContests(UUID electionId) {
        List<Contest> contests = validateBallotReady(electionId);
        int opened = 0;
        for (Contest contest : contests) {
            if (contest.getStatus() == ContestStatus.CLOSED) {
                throw new ElectionLifecycleException("A closed contest prevents voting from opening");
            }
            if (contest.getStatus() == ContestStatus.DRAFT) {
                contest.transitionTo(ContestStatus.OPEN);
                // Generate the ballot key up front so the first voter does not pay for RSA key generation.
                ballotSigningKeyService.getOrCreate(contest.getId());
                opened++;
            }
        }
        return opened;
    }

    private int closeAllContests(UUID electionId) {
        List<Contest> contests = contestRepository.findByElectionId(electionId);
        if (contests.isEmpty()) {
            throw new ElectionLifecycleException("Election cannot complete without contests");
        }
        int closed = 0;
        for (Contest contest : contests) {
            if (contest.getStatus() != ContestStatus.CLOSED) {
                contest.transitionTo(ContestStatus.CLOSED);
                closed++;
            }
            ledgerPublicationService.ensurePublished(contest);
        }
        return closed;
    }

    private void closeOpenContests(UUID electionId) {
        contestRepository.findByElectionId(electionId).stream()
                .filter(contest -> contest.getStatus() == ContestStatus.OPEN)
                .forEach(contest -> contest.transitionTo(ContestStatus.CLOSED));
    }

    private List<Contest> validateBallotReady(UUID electionId) {
        List<Contest> contests = contestRepository.findByElectionId(electionId);
        if (contests.isEmpty()) {
            throw new ElectionLifecycleException("Election must have at least one contest before registration opens");
        }

        for (Contest contest : contests) {
            long validOptions = contestOptionRepository.countByContestIdAndOptionTypeIn(
                    contest.getId(),
                    VALID_VOTE_OPTION_TYPES
            );
            if (validOptions < 2) {
                throw new ElectionLifecycleException(
                        "Contest '" + contest.getName() + "' must have at least two valid vote options"
                );
            }
        }
        return contests;
    }

    private void transition(Election election, ElectionStatus newStatus, String detail, Instant occurredAt) {
        ElectionStatus previousStatus = election.getStatus();
        election.transitionTo(newStatus);
        lifecycleEventRepository.save(new ElectionLifecycleEvent(
                election.getId(),
                previousStatus,
                newStatus,
                ElectionLifecycleTrigger.AUTOMATIC,
                ElectionLifecycleOutcome.SUCCESS,
                null,
                null,
                detail,
                occurredAt
        ));
    }

    private boolean recordFailureOnce(Election election, String detail, Instant occurredAt) {
        ElectionStatus previousStatus = election.getStatus();
        if (lifecycleEventRepository.existsByElectionIdAndPreviousStatusAndOutcome(
                election.getId(),
                previousStatus,
                ElectionLifecycleOutcome.FAILURE
        )) {
            return false;
        }

        lifecycleEventRepository.save(new ElectionLifecycleEvent(
                election.getId(),
                previousStatus,
                nextStatus(previousStatus),
                ElectionLifecycleTrigger.AUTOMATIC,
                ElectionLifecycleOutcome.FAILURE,
                null,
                null,
                detail,
                occurredAt
        ));
        return true;
    }

    private ElectionStatus nextStatus(ElectionStatus status) {
        return switch (status) {
            case DRAFT -> ElectionStatus.REGISTRATION_OPEN;
            case REGISTRATION_OPEN -> ElectionStatus.REGISTRATION_CLOSED;
            case REGISTRATION_CLOSED -> ElectionStatus.VOTING_OPEN;
            case VOTING_OPEN -> ElectionStatus.COMPLETED;
            case COMPLETED, CANCELLED -> null;
        };
    }

    private boolean hasReached(Instant now, Instant boundary) {
        return !now.isBefore(boundary);
    }

    private Election getElectionForUpdateOrThrow(UUID electionId) {
        return electionRepository.findByIdForUpdate(electionId)
                .orElseThrow(() -> new ResourceNotFoundException("Election not found"));
    }
}
