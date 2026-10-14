package io.github.humphreymahlangu.votetrust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.humphreymahlangu.votetrust.entity.Contest;
import io.github.humphreymahlangu.votetrust.entity.ContestStatus;
import io.github.humphreymahlangu.votetrust.entity.ContestType;
import io.github.humphreymahlangu.votetrust.entity.Election;
import io.github.humphreymahlangu.votetrust.entity.ElectionLifecycleEvent;
import io.github.humphreymahlangu.votetrust.entity.ElectionLifecycleOutcome;
import io.github.humphreymahlangu.votetrust.entity.ElectionLifecycleTrigger;
import io.github.humphreymahlangu.votetrust.entity.ElectionStatus;
import io.github.humphreymahlangu.votetrust.entity.ElectionType;
import io.github.humphreymahlangu.votetrust.repository.ContestOptionRepository;
import io.github.humphreymahlangu.votetrust.repository.ContestRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionLifecycleEventRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElectionLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");

    @Mock
    private ElectionRepository electionRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestOptionRepository contestOptionRepository;

    @Mock
    private ElectionLifecycleEventRepository lifecycleEventRepository;

    @Mock
    private LedgerPublicationService ledgerPublicationService;

    @Mock
    private BallotSigningKeyService ballotSigningKeyService;

    private ElectionLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        lifecycleService = new ElectionLifecycleService(
                electionRepository,
                contestRepository,
                contestOptionRepository,
                lifecycleEventRepository,
                ledgerPublicationService,
                ballotSigningKeyService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void catchesUpAllDueTransitionsAndClosesEveryContest() throws Exception {
        UUID electionId = UUID.randomUUID();
        Election election = election(
                electionId,
                ElectionStatus.DRAFT,
                NOW.minusSeconds(400),
                NOW.minusSeconds(300),
                NOW.minusSeconds(200),
                NOW.minusSeconds(100)
        );
        Contest contest = contest(election, UUID.randomUUID(), ContestStatus.DRAFT);

        when(electionRepository.findByIdForUpdate(electionId)).thenReturn(Optional.of(election));
        when(contestRepository.findByElectionId(electionId)).thenReturn(List.of(contest));
        when(contestOptionRepository.countByContestIdAndOptionTypeIn(any(UUID.class), anyCollection()))
                .thenReturn(2L);

        lifecycleService.advanceElection(electionId);

        assertThat(election.getStatus()).isEqualTo(ElectionStatus.COMPLETED);
        assertThat(contest.getStatus()).isEqualTo(ContestStatus.CLOSED);
        verify(lifecycleEventRepository, times(4)).save(any(ElectionLifecycleEvent.class));
        verify(ballotSigningKeyService).getOrCreate(contest.getId());
        verify(ledgerPublicationService).ensurePublished(contest);
    }

    @Test
    void opensRegistrationAtTheExactBoundary() throws Exception {
        UUID electionId = UUID.randomUUID();
        Election election = election(
                electionId,
                ElectionStatus.DRAFT,
                NOW,
                NOW.plusSeconds(100),
                NOW.plusSeconds(200),
                NOW.plusSeconds(300)
        );
        Contest contest = contest(election, UUID.randomUUID(), ContestStatus.DRAFT);

        when(electionRepository.findByIdForUpdate(electionId)).thenReturn(Optional.of(election));
        when(contestRepository.findByElectionId(electionId)).thenReturn(List.of(contest));
        when(contestOptionRepository.countByContestIdAndOptionTypeIn(any(UUID.class), anyCollection()))
                .thenReturn(2L);

        lifecycleService.advanceElection(electionId);

        assertThat(election.getStatus()).isEqualTo(ElectionStatus.REGISTRATION_OPEN);
        assertThat(contest.getStatus()).isEqualTo(ContestStatus.DRAFT);
    }

    @Test
    void invalidElectionFailsClosedAndRecordsOnlyOneFailureForTheStatus() throws Exception {
        UUID electionId = UUID.randomUUID();
        Election election = election(
                electionId,
                ElectionStatus.DRAFT,
                NOW.minusSeconds(1),
                NOW.plusSeconds(100),
                NOW.plusSeconds(200),
                NOW.plusSeconds(300)
        );

        when(electionRepository.findByIdForUpdate(electionId)).thenReturn(Optional.of(election));
        when(contestRepository.findByElectionId(electionId)).thenReturn(List.of());
        when(lifecycleEventRepository.existsByElectionIdAndPreviousStatusAndOutcome(
                electionId,
                ElectionStatus.DRAFT,
                ElectionLifecycleOutcome.FAILURE
        )).thenReturn(false, true);

        lifecycleService.advanceElection(electionId);
        lifecycleService.advanceElection(electionId);

        assertThat(election.getStatus()).isEqualTo(ElectionStatus.DRAFT);
        ArgumentCaptor<ElectionLifecycleEvent> eventCaptor = ArgumentCaptor.forClass(ElectionLifecycleEvent.class);
        verify(lifecycleEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOutcome()).isEqualTo(ElectionLifecycleOutcome.FAILURE);
        assertThat(eventCaptor.getValue().getDetail())
                .isEqualTo("Election must have at least one contest before registration opens");
    }

    @Test
    void administratorCancellationClosesOpenContestAndRecordsActor() throws Exception {
        UUID electionId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        Election election = election(
                electionId,
                ElectionStatus.VOTING_OPEN,
                NOW.minusSeconds(400),
                NOW.minusSeconds(300),
                NOW.minusSeconds(200),
                NOW.plusSeconds(100)
        );
        Contest contest = contest(election, UUID.randomUUID(), ContestStatus.OPEN);

        when(electionRepository.findByIdForUpdate(electionId)).thenReturn(Optional.of(election));
        when(contestRepository.findByElectionId(electionId)).thenReturn(List.of(contest));

        lifecycleService.cancelElection(electionId, actorUserId, "admin@example.com");

        assertThat(election.getStatus()).isEqualTo(ElectionStatus.CANCELLED);
        assertThat(contest.getStatus()).isEqualTo(ContestStatus.CLOSED);
        ArgumentCaptor<ElectionLifecycleEvent> eventCaptor = ArgumentCaptor.forClass(ElectionLifecycleEvent.class);
        verify(lifecycleEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getTrigger()).isEqualTo(ElectionLifecycleTrigger.ADMINISTRATOR);
        assertThat(eventCaptor.getValue().getActorUserId()).isEqualTo(actorUserId);
        assertThat(eventCaptor.getValue().getActorEmail()).isEqualTo("admin@example.com");
    }

    private Election election(
            UUID id,
            ElectionStatus status,
            Instant registrationStart,
            Instant registrationEnd,
            Instant votingStart,
            Instant votingEnd
    ) throws Exception {
        Election election = new Election(
                "Automatic Lifecycle Election",
                ElectionType.MUNICIPAL,
                registrationStart,
                registrationEnd,
                votingStart,
                votingEnd,
                status
        );
        setId(election, id);
        return election;
    }

    private Contest contest(Election election, UUID id, ContestStatus status) throws Exception {
        Contest contest = new Contest(
                election,
                "Ward 1 Councillor",
                ContestType.MUNICIPAL_WARD,
                status,
                1,
                "Western Cape",
                "City of Cape Town",
                1
        );
        setId(contest, id);
        return contest;
    }

    private void setId(Object entity, UUID id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
