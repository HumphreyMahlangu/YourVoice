package io.github.humphreymahlangu.yourvoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.humphreymahlangu.yourvoice.dto.ContestResponse;
import io.github.humphreymahlangu.yourvoice.dto.ContestStatusUpdateRequest;
import io.github.humphreymahlangu.yourvoice.dto.CreateContestOptionRequest;
import io.github.humphreymahlangu.yourvoice.dto.CreateContestRequest;
import io.github.humphreymahlangu.yourvoice.dto.CreateVotingDistrictRequest;
import io.github.humphreymahlangu.yourvoice.dto.ElectionResponse;
import io.github.humphreymahlangu.yourvoice.dto.UpdateElectionRequest;
import io.github.humphreymahlangu.yourvoice.dto.VotingDistrictResponse;
import io.github.humphreymahlangu.yourvoice.entity.Contest;
import io.github.humphreymahlangu.yourvoice.entity.ContestOption;
import io.github.humphreymahlangu.yourvoice.entity.ContestOptionType;
import io.github.humphreymahlangu.yourvoice.entity.ContestStatus;
import io.github.humphreymahlangu.yourvoice.entity.ContestType;
import io.github.humphreymahlangu.yourvoice.entity.Election;
import io.github.humphreymahlangu.yourvoice.entity.ElectionStatus;
import io.github.humphreymahlangu.yourvoice.entity.ElectionType;
import io.github.humphreymahlangu.yourvoice.entity.VotingDistrict;
import io.github.humphreymahlangu.yourvoice.exception.DuplicateResourceException;
import io.github.humphreymahlangu.yourvoice.exception.ElectionLifecycleException;
import io.github.humphreymahlangu.yourvoice.repository.ContestOptionRepository;
import io.github.humphreymahlangu.yourvoice.repository.ContestRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRegistrationRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRepository;
import io.github.humphreymahlangu.yourvoice.repository.VoterProfileRepository;
import io.github.humphreymahlangu.yourvoice.repository.VotingDistrictRepository;
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

    @Mock
    private VoterProfileRepository voterProfileRepository;

    @Mock
    private ElectionRegistrationRepository electionRegistrationRepository;

    @Mock
    private GeographicRecordService geography;

    private AdminElectionManagementService adminElectionManagementService;

    @BeforeEach
    void setUp() {
        adminElectionManagementService = new AdminElectionManagementService(
                votingDistrictRepository,
                electionRepository,
                contestRepository,
                contestOptionRepository,
                electionLifecycleService,
                voterProfileRepository,
                electionRegistrationRepository,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
                geography
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

    @Test
    void reschedulingADraftElectionUpdatesItAndRecordsTheAdministrator() {
        UUID electionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Election election = draftElection(FIXED_NOW.minusSeconds(7200), FIXED_NOW.minusSeconds(3600));
        when(electionRepository.findByIdForUpdate(electionId)).thenReturn(Optional.of(election));
        UpdateElectionRequest request = new UpdateElectionRequest(
                " Rescheduled Election ",
                FIXED_NOW.plusSeconds(60),
                FIXED_NOW.plusSeconds(3600),
                FIXED_NOW.plusSeconds(7200),
                FIXED_NOW.plusSeconds(10800)
        );

        ElectionResponse response = adminElectionManagementService.updateElection(electionId, request, adminId, "admin@example.com");

        assertThat(response.name()).isEqualTo("Rescheduled Election");
        assertThat(election.getRegistrationEndAt()).isEqualTo(FIXED_NOW.plusSeconds(3600));
        verify(electionLifecycleService).recordAdministratorUpdate(
                election, adminId, "admin@example.com", "Election name or schedule updated by an administrator");
    }

    @Test
    void openedElectionsCannotBeRescheduled() {
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
        UpdateElectionRequest request = new UpdateElectionRequest(
                "Open Election",
                FIXED_NOW.plusSeconds(60),
                FIXED_NOW.plusSeconds(3600),
                FIXED_NOW.plusSeconds(7200),
                FIXED_NOW.plusSeconds(10800)
        );

        assertThatThrownBy(() -> adminElectionManagementService.updateElection(electionId, request, UUID.randomUUID(), "admin@example.com"))
                .isInstanceOf(ElectionLifecycleException.class)
                .hasMessage("Only DRAFT elections can be edited; cancel an election that has already opened");
    }

    @Test
    void districtGeographyCannotChangeOnceVotersUseIt() {
        UUID districtId = UUID.randomUUID();
        VotingDistrict district = new VotingDistrict("WC001-0001", "Ward 1 Station", "Western Cape", "City of Cape Town", 1);
        when(votingDistrictRepository.findById(districtId)).thenReturn(Optional.of(district));
        when(voterProfileRepository.existsByVotingDistrictId(districtId)).thenReturn(true);

        assertThatThrownBy(() -> adminElectionManagementService.updateVotingDistrict(
                districtId,
                new CreateVotingDistrictRequest("WC001-0001", "Ward 1 Station", "Western Cape", "City of Cape Town", 2)
        ))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("This voting district has registered voters; only its code and name can be changed");

        VotingDistrictResponse renamed = adminElectionManagementService.updateVotingDistrict(
                districtId,
                new CreateVotingDistrictRequest("wc001-0001", "Ward 1 Community Hall", "Western Cape", "City of Cape Town", 1)
        );
        assertThat(renamed.name()).isEqualTo("Ward 1 Community Hall");
        assertThat(renamed.code()).isEqualTo("WC001-0001");
    }

    @Test
    void districtsInUseCannotBeDeleted() {
        UUID districtId = UUID.randomUUID();
        VotingDistrict district = new VotingDistrict("WC001-0001", "Ward 1 Station", "Western Cape", "City of Cape Town", 1);
        when(votingDistrictRepository.findById(districtId)).thenReturn(Optional.of(district));
        when(voterProfileRepository.existsByVotingDistrictId(districtId)).thenReturn(false);
        when(electionRegistrationRepository.existsByVotingDistrictId(districtId)).thenReturn(true);

        assertThatThrownBy(() -> adminElectionManagementService.deleteVotingDistrict(districtId))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("This voting district has registered voters and cannot be deleted");
        verify(votingDistrictRepository, never()).delete(any(VotingDistrict.class));
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
