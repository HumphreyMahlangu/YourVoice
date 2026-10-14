package io.github.humphreymahlangu.votetrust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.humphreymahlangu.votetrust.dto.ContestResponse;
import io.github.humphreymahlangu.votetrust.dto.ContestStatusUpdateRequest;
import io.github.humphreymahlangu.votetrust.dto.CreateContestOptionRequest;
import io.github.humphreymahlangu.votetrust.dto.CreateContestRequest;
import io.github.humphreymahlangu.votetrust.entity.Contest;
import io.github.humphreymahlangu.votetrust.entity.ContestOption;
import io.github.humphreymahlangu.votetrust.entity.ContestOptionType;
import io.github.humphreymahlangu.votetrust.entity.ContestStatus;
import io.github.humphreymahlangu.votetrust.entity.ContestType;
import io.github.humphreymahlangu.votetrust.entity.Election;
import io.github.humphreymahlangu.votetrust.entity.ElectionStatus;
import io.github.humphreymahlangu.votetrust.entity.ElectionType;
import io.github.humphreymahlangu.votetrust.exception.DuplicateResourceException;
import io.github.humphreymahlangu.votetrust.exception.ElectionLifecycleException;
import io.github.humphreymahlangu.votetrust.repository.ContestOptionRepository;
import io.github.humphreymahlangu.votetrust.repository.ContestRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingDistrictRepository;
import java.time.Clock;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminElectionManagementServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Mock
    private VotingDistrictRepository votingDistrictRepository;

    @Mock
    private ElectionRepository electionRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestOptionRepository contestOptionRepository;

    @Mock
    private ElectionLifecycleService electionLifecycleService;

    private AdminElectionManagementService adminElectionManagementService;

    @BeforeEach
    void setUp() {
        adminElectionManagementService = new AdminElectionManagementService(
                votingDistrictRepository,
                electionRepository,
                contestRepository,
                contestOptionRepository,
                electionLifecycleService,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void administratorCannotOpenContestManually() throws Exception {
        UUID electionId = UUID.randomUUID();
        UUID contestId = UUID.randomUUID();
        Contest contest = contest(ElectionStatus.REGISTRATION_CLOSED, ContestStatus.DRAFT);
        setId(contest, contestId);

        when(electionRepository.findByIdForUpdate(electionId)).thenReturn(Optional.of(contest.getElection()));
        when(contestRepository.findByIdAndElectionId(contestId, electionId)).thenReturn(Optional.of(contest));

        assertThatThrownBy(() -> adminElectionManagementService.updateContestStatus(
                electionId,
                contestId,
                new ContestStatusUpdateRequest(ContestStatus.OPEN)
        ))
                .isInstanceOf(ElectionLifecycleException.class)
                .hasMessage("Contest lifecycle transitions are automatic and cannot be performed by an administrator");
    }

    @Test
    void draftElectionPastRegistrationStartCanStillBeConfiguredSoItIsNotStuck() {
        UUID electionId = UUID.randomUUID();
        Election election = draftElection(FIXED_NOW.minusSeconds(60), FIXED_NOW.plusSeconds(3600));
        when(electionRepository.findByIdForUpdate(electionId)).thenReturn(Optional.of(election));
        when(contestRepository.save(any(Contest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContestResponse response = adminElectionManagementService.createContest(electionId, wardContestRequest());

        assertThat(response.name()).isEqualTo("Ward 1 Councillor");
        assertThat(response.status()).isEqualTo(ContestStatus.DRAFT.name());
    }

    @Test
    void ballotConfigurationClosesOnceTheRegistrationWindowHasEnded() {
        UUID electionId = UUID.randomUUID();
        Election election = draftElection(FIXED_NOW.minusSeconds(3600), FIXED_NOW);
        when(electionRepository.findByIdForUpdate(electionId)).thenReturn(Optional.of(election));

        assertThatThrownBy(() -> adminElectionManagementService.createContest(electionId, wardContestRequest()))
                .isInstanceOf(ElectionLifecycleException.class)
                .hasMessage("Ballot configuration is closed because the registration window has ended; cancel this election and create a new one");
        verify(contestRepository, never()).save(any(Contest.class));
    }

    @Test
    void ballotConfigurationIsLockedOnceRegistrationHasOpened() {
        UUID electionId = UUID.randomUUID();
        Election election = new Election(
                "Open Election",
                ElectionType.MUNICIPAL,
                FIXED_NOW.minusSeconds(60),
                FIXED_NOW.plusSeconds(3600),
                FIXED_NOW.plusSeconds(7200),
                FIXED_NOW.plusSeconds(10800),
                ElectionStatus.REGISTRATION_OPEN
        );
        when(electionRepository.findByIdForUpdate(electionId)).thenReturn(Optional.of(election));

        assertThatThrownBy(() -> adminElectionManagementService.createContest(electionId, wardContestRequest()))
                .isInstanceOf(ElectionLifecycleException.class)
                .hasMessage("Ballot configuration is locked once registration opens");
        verify(contestRepository, never()).save(any(Contest.class));
    }

    @Test
    void duplicateBlankBallotOptionIsRejectedBeforeDatabaseConstraint() throws Exception {
        UUID electionId = UUID.randomUUID();
        UUID contestId = UUID.randomUUID();
        Contest contest = contest(ElectionStatus.DRAFT, ContestStatus.DRAFT);
        setId(contest, contestId);

        when(electionRepository.findByIdForUpdate(electionId)).thenReturn(Optional.of(contest.getElection()));
        when(contestRepository.findByIdAndElectionId(contestId, electionId)).thenReturn(Optional.of(contest));
        when(contestOptionRepository.existsByContestIdAndNameIgnoreCase(contestId, "Blank ballot"))
                .thenReturn(false);
        when(contestOptionRepository.existsByContestIdAndDisplayOrder(contestId, 98)).thenReturn(false);
        when(contestOptionRepository.existsByContestIdAndOptionType(contestId, ContestOptionType.BLANK_BALLOT))
                .thenReturn(true);

        assertThatThrownBy(() -> adminElectionManagementService.createContestOption(
                electionId,
                contestId,
                new CreateContestOptionRequest("Blank ballot", ContestOptionType.BLANK_BALLOT, 98)
        ))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("A BLANK_BALLOT option already exists for this contest");
        verify(contestOptionRepository, never()).save(any(ContestOption.class));
    }

    private Election draftElection(Instant registrationStartAt, Instant registrationEndAt) {
        return new Election(
                "Boundary Election",
                ElectionType.MUNICIPAL,
                registrationStartAt,
                registrationEndAt,
                registrationEndAt.plusSeconds(3600),
                registrationEndAt.plusSeconds(7200),
                ElectionStatus.DRAFT
        );
    }

    private CreateContestRequest wardContestRequest() {
        return new CreateContestRequest(
                "Ward 1 Councillor",
                ContestType.MUNICIPAL_WARD,
                1,
                "Western Cape",
                "City of Cape Town",
                1
        );
    }

    private Contest contest(ElectionStatus electionStatus, ContestStatus contestStatus) {
        Election election = new Election(
                "2026 Local Government Election",
                ElectionType.MUNICIPAL,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-07T07:00:00Z"),
                Instant.parse("2026-08-07T21:00:00Z"),
                electionStatus
        );
        return new Contest(
                election,
                "Municipal Ward Councillor",
                ContestType.MUNICIPAL_WARD,
                contestStatus,
                1,
                "Western Cape",
                "City of Cape Town",
                1
        );
    }

    private void setId(Contest contest, UUID contestId) throws Exception {
        Field idField = Contest.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(contest, contestId);
    }
}
