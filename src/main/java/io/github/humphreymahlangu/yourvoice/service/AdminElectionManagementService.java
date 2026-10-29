package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.dto.ContestOptionResponse;
import io.github.humphreymahlangu.yourvoice.dto.ContestResponse;
import io.github.humphreymahlangu.yourvoice.dto.ContestStatusUpdateRequest;
import io.github.humphreymahlangu.yourvoice.dto.CreateContestOptionRequest;
import io.github.humphreymahlangu.yourvoice.dto.CreateContestRequest;
import io.github.humphreymahlangu.yourvoice.dto.CreateElectionRequest;
import io.github.humphreymahlangu.yourvoice.dto.CreateVotingDistrictRequest;
import io.github.humphreymahlangu.yourvoice.dto.ElectionResponse;
import io.github.humphreymahlangu.yourvoice.dto.ElectionStatusUpdateRequest;
import io.github.humphreymahlangu.yourvoice.dto.UpdateElectionRequest;
import io.github.humphreymahlangu.yourvoice.dto.VotingDistrictResponse;
import io.github.humphreymahlangu.yourvoice.entity.Contest;
import io.github.humphreymahlangu.yourvoice.entity.ContestOption;
import io.github.humphreymahlangu.yourvoice.entity.ContestStatus;
import io.github.humphreymahlangu.yourvoice.entity.ContestType;
import io.github.humphreymahlangu.yourvoice.entity.Election;
import io.github.humphreymahlangu.yourvoice.entity.ElectionStatus;
import io.github.humphreymahlangu.yourvoice.entity.ElectionType;
import io.github.humphreymahlangu.yourvoice.entity.VotingDistrict;
import io.github.humphreymahlangu.yourvoice.exception.DuplicateResourceException;
import io.github.humphreymahlangu.yourvoice.exception.ElectionLifecycleException;
import io.github.humphreymahlangu.yourvoice.exception.ResourceNotFoundException;
import io.github.humphreymahlangu.yourvoice.repository.ContestOptionRepository;
import io.github.humphreymahlangu.yourvoice.repository.ContestRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRegistrationRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRepository;
import io.github.humphreymahlangu.yourvoice.repository.VoterProfileRepository;
import io.github.humphreymahlangu.yourvoice.repository.VotingDistrictRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminElectionManagementService {

    private final VotingDistrictRepository votingDistrictRepository;
    private final ElectionRepository electionRepository;
    private final ContestRepository contestRepository;
    private final ContestOptionRepository contestOptionRepository;
    private final ElectionLifecycleService electionLifecycleService;
    private final VoterProfileRepository voterProfileRepository;
    private final ElectionRegistrationRepository electionRegistrationRepository;
    private final Clock clock;
    private final GeographicRecordService geography;

    public AdminElectionManagementService(
            VotingDistrictRepository votingDistrictRepository,
            ElectionRepository electionRepository,
            ContestRepository contestRepository,
            ContestOptionRepository contestOptionRepository,
            ElectionLifecycleService electionLifecycleService,
            VoterProfileRepository voterProfileRepository,
            ElectionRegistrationRepository electionRegistrationRepository,
            Clock clock,
            GeographicRecordService geography
    ) {
        this.votingDistrictRepository = votingDistrictRepository;
        this.electionRepository = electionRepository;
        this.contestRepository = contestRepository;
        this.contestOptionRepository = contestOptionRepository;
        this.electionLifecycleService = electionLifecycleService;
        this.voterProfileRepository = voterProfileRepository;
        this.electionRegistrationRepository = electionRegistrationRepository;
        this.clock = clock;
        this.geography = geography;
    }

    @Transactional
    public VotingDistrictResponse createVotingDistrict(CreateVotingDistrictRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (votingDistrictRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("A voting district with this code already exists");
        }

        geography.ensureParents(request.province(), request.municipality());
        VotingDistrict votingDistrict = votingDistrictRepository.save(new VotingDistrict(
                code,
                request.name().trim(),
                request.province().trim(),
                request.municipality().trim(),
                request.wardNumber()
        ));
        return VotingDistrictResponse.from(votingDistrict);
    }

    @Transactional
    public ElectionResponse createElection(CreateElectionRequest request) {
        validateElectionWindows(request);

        String name = request.name().trim();
        if (electionRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("An election with this name already exists");
        }

        Election election = electionRepository.save(new Election(
                name,
                request.type(),
                request.registrationStartAt(),
                request.registrationEndAt(),
                request.votingStartAt(),
                request.votingEndAt(),
                ElectionStatus.DRAFT
        ));
        return ElectionResponse.from(election);
    }

    @Transactional
    public ElectionResponse updateElectionStatus(
            UUID electionId,
            ElectionStatusUpdateRequest request,
            UUID actorUserId,
            String actorEmail
    ) {
        if (request.status() != ElectionStatus.CANCELLED) {
            throw new ElectionLifecycleException(
                    "Election lifecycle transitions are automatic; administrators may only cancel an active election"
            );
        }
        Election election = electionLifecycleService.cancelElection(electionId, actorUserId, actorEmail);
        return ElectionResponse.from(election);
    }

    @Transactional
    public ContestResponse createContest(UUID electionId, CreateContestRequest request) {
        Election election = getElectionForUpdateOrThrow(electionId);
        ensureElectionAllowsBallotConfiguration(election);
        validateContestTypeForElection(election.getType(), request.type());

        String name = request.name().trim();
        String scopeProvince = normalizeOptional(request.scopeProvince());
        String scopeMunicipality = normalizeOptional(request.scopeMunicipality());
        Integer scopeWardNumber = request.scopeWardNumber();
        validateContestScope(request.type(), scopeProvince, scopeMunicipality, scopeWardNumber);

        if (contestRepository.existsByElectionIdAndNameIgnoreCase(electionId, name)) {
            throw new DuplicateResourceException("A contest with this name already exists for this election");
        }
        if (contestRepository.existsByElectionIdAndDisplayOrder(electionId, request.displayOrder())) {
            throw new DuplicateResourceException("A contest with this display order already exists for this election");
        }

        Contest contest = contestRepository.save(new Contest(
                election,
                name,
                request.type(),
                ContestStatus.DRAFT,
                request.displayOrder(),
                scopeProvince,
                scopeMunicipality,
                scopeWardNumber
        ));
        return toContestResponse(contest);
    }

    @Transactional
    public ContestOptionResponse createContestOption(
            UUID electionId,
            UUID contestId,
            CreateContestOptionRequest request
    ) {
        Election election = getElectionForUpdateOrThrow(electionId);
        Contest contest = getContestOrThrow(electionId, contestId);
        ensureElectionAllowsBallotConfiguration(election);
        ensureContestIsDraft(contest);

        String name = request.name().trim();
        if (contestOptionRepository.existsByContestIdAndNameIgnoreCase(contestId, name)) {
            throw new DuplicateResourceException("A ballot option with this name already exists for this contest");
        }
        if (contestOptionRepository.existsByContestIdAndDisplayOrder(contestId, request.displayOrder())) {
            throw new DuplicateResourceException("A ballot option with this display order already exists for this contest");
        }
        if (request.optionType().isNonValidBallot()
                && contestOptionRepository.existsByContestIdAndOptionType(contestId, request.optionType())) {
            throw new DuplicateResourceException(
                    "A " + request.optionType().name() + " option already exists for this contest"
            );
        }

        ContestOption contestOption = contestOptionRepository.save(new ContestOption(
                contest,
                name,
                request.optionType(),
                request.displayOrder()
        ));
        return ContestOptionResponse.from(contestOption);
    }

    @Transactional
    public ContestResponse updateContestStatus(
            UUID electionId,
            UUID contestId,
            ContestStatusUpdateRequest request
    ) {
        getElectionForUpdateOrThrow(electionId);
        Contest contest = getContestOrThrow(electionId, contestId);
        if (contest.getStatus() == request.status()) {
            return toContestResponse(contest);
        }
        throw new ElectionLifecycleException(
                "Contest lifecycle transitions are automatic and cannot be performed by an administrator"
        );
    }

    /**
     * Renames or reschedules an election that has not opened yet. Rescheduling is how an administrator rescues an
     * election whose registration window passed before its ballot was ready.
     */
    @Transactional
    public ElectionResponse updateElection(
            UUID electionId,
            UpdateElectionRequest request,
            UUID actorUserId,
            String actorEmail
    ) {
        Election election = getElectionForUpdateOrThrow(electionId);
        if (election.getStatus() != ElectionStatus.DRAFT) {
            throw new ElectionLifecycleException("Only DRAFT elections can be edited; cancel an election that has already opened");
        }
        validateElectionWindows(
                request.registrationStartAt(),
                request.registrationEndAt(),
                request.votingStartAt(),
                request.votingEndAt()
        );
        if (!Instant.now(clock).isBefore(request.registrationEndAt())) {
            throw new ElectionLifecycleException("The registration window must end in the future");
        }

        String name = request.name().trim();
        if (electionRepository.existsByNameIgnoreCaseAndIdNot(name, electionId)) {
            throw new DuplicateResourceException("An election with this name already exists");
        }

        election.updateDetails(
                name,
                request.registrationStartAt(),
                request.registrationEndAt(),
                request.votingStartAt(),
                request.votingEndAt()
        );
        electionLifecycleService.recordAdministratorUpdate(
                election,
                actorUserId,
                actorEmail,
                "Election name or schedule updated by an administrator"
        );
        return ElectionResponse.from(election);
    }

    @Transactional
    public ContestResponse updateContest(UUID electionId, UUID contestId, CreateContestRequest request) {
        Election election = getElectionForUpdateOrThrow(electionId);
        Contest contest = getContestOrThrow(electionId, contestId);
        ensureElectionAllowsBallotConfiguration(election);
        ensureContestIsDraft(contest);
        validateContestTypeForElection(election.getType(), request.type());

        String name = request.name().trim();
        String scopeProvince = normalizeOptional(request.scopeProvince());
        String scopeMunicipality = normalizeOptional(request.scopeMunicipality());
        Integer scopeWardNumber = request.scopeWardNumber();
        validateContestScope(request.type(), scopeProvince, scopeMunicipality, scopeWardNumber);

        if (contestRepository.existsByElectionIdAndNameIgnoreCaseAndIdNot(electionId, name, contestId)) {
            throw new DuplicateResourceException("A contest with this name already exists for this election");
        }
        if (contestRepository.existsByElectionIdAndDisplayOrderAndIdNot(electionId, request.displayOrder(), contestId)) {
            throw new DuplicateResourceException("A contest with this display order already exists for this election");
        }

        contest.updateDetails(name, request.type(), request.displayOrder(), scopeProvince, scopeMunicipality, scopeWardNumber);
        return toContestResponse(contest);
    }

    @Transactional
    public void deleteContest(UUID electionId, UUID contestId) {
        Election election = getElectionForUpdateOrThrow(electionId);
        Contest contest = getContestOrThrow(electionId, contestId);
        ensureElectionAllowsBallotConfiguration(election);
        ensureContestIsDraft(contest);

        contestOptionRepository.deleteAll(contestOptionRepository.findByContestId(contestId));
        contestRepository.delete(contest);
    }

    @Transactional
    public ContestOptionResponse updateContestOption(
            UUID electionId,
            UUID contestId,
            UUID optionId,
            CreateContestOptionRequest request
    ) {
        Election election = getElectionForUpdateOrThrow(electionId);
        Contest contest = getContestOrThrow(electionId, contestId);
        ensureElectionAllowsBallotConfiguration(election);
        ensureContestIsDraft(contest);
        ContestOption option = getOptionOrThrow(contestId, optionId);

        String name = request.name().trim();
        if (contestOptionRepository.existsByContestIdAndNameIgnoreCaseAndIdNot(contestId, name, optionId)) {
            throw new DuplicateResourceException("A ballot option with this name already exists for this contest");
        }
        if (contestOptionRepository.existsByContestIdAndDisplayOrderAndIdNot(contestId, request.displayOrder(), optionId)) {
            throw new DuplicateResourceException("A ballot option with this display order already exists for this contest");
        }
        if (request.optionType().isNonValidBallot()
                && contestOptionRepository.existsByContestIdAndOptionTypeAndIdNot(contestId, request.optionType(), optionId)) {
            throw new DuplicateResourceException("A " + request.optionType().name() + " option already exists for this contest");
        }

        option.updateDetails(name, request.optionType(), request.displayOrder());
        return ContestOptionResponse.from(option);
    }

    @Transactional
    public void deleteContestOption(UUID electionId, UUID contestId, UUID optionId) {
        Election election = getElectionForUpdateOrThrow(electionId);
        Contest contest = getContestOrThrow(electionId, contestId);
        ensureElectionAllowsBallotConfiguration(election);
        ensureContestIsDraft(contest);
        contestOptionRepository.delete(getOptionOrThrow(contestId, optionId));
    }

    /**
     * Updates a district. Once voters are registered in it, only the code and name may change: the province,
     * municipality and ward decide which contests those voters are eligible for.
     */
    @Transactional
    public VotingDistrictResponse updateVotingDistrict(UUID votingDistrictId, CreateVotingDistrictRequest request) {
        VotingDistrict votingDistrict = votingDistrictRepository.findById(votingDistrictId)
                .orElseThrow(() -> new ResourceNotFoundException("Voting district not found"));
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        String province = request.province().trim();
        String municipality = request.municipality().trim();
        if (votingDistrictRepository.existsByCodeIgnoreCaseAndIdNot(code, votingDistrictId)) {
            throw new DuplicateResourceException("A voting district with this code already exists");
        }

        boolean geographyChanged = !province.equals(votingDistrict.getProvince())
                || !municipality.equals(votingDistrict.getMunicipality())
                || !request.wardNumber().equals(votingDistrict.getWardNumber());
        if (geographyChanged && isVotingDistrictInUse(votingDistrictId)) {
            throw new DuplicateResourceException(
                    "This voting district has registered voters; only its code and name can be changed"
            );
        }

        geography.ensureParents(province, municipality);
        votingDistrict.updateDetails(code, request.name().trim(), province, municipality, request.wardNumber());
        return VotingDistrictResponse.from(votingDistrict);
    }

    @Transactional
    public void deleteVotingDistrict(UUID votingDistrictId) {
        VotingDistrict votingDistrict = votingDistrictRepository.findById(votingDistrictId)
                .orElseThrow(() -> new ResourceNotFoundException("Voting district not found"));
        if (isVotingDistrictInUse(votingDistrictId)) {
            throw new DuplicateResourceException("This voting district has registered voters and cannot be deleted");
        }
        votingDistrictRepository.delete(votingDistrict);
    }

    private boolean isVotingDistrictInUse(UUID votingDistrictId) {
        return votingDistrictRepository.isUsedByVerification(votingDistrictId)
                || voterProfileRepository.existsByVotingDistrictId(votingDistrictId)
                || electionRegistrationRepository.existsByVotingDistrictId(votingDistrictId);
    }

    private void ensureContestIsDraft(Contest contest) {
        if (contest.getStatus() != ContestStatus.DRAFT) {
            throw new ElectionLifecycleException("Contest ballot options can be changed only while the contest is DRAFT");
        }
    }

    private ContestOption getOptionOrThrow(UUID contestId, UUID optionId) {
        return contestOptionRepository.findByIdAndContestId(optionId, contestId)
                .orElseThrow(() -> new ResourceNotFoundException("Contest option not found"));
    }

    private void validateElectionWindows(CreateElectionRequest request) {
        validateElectionWindows(
                request.registrationStartAt(),
                request.registrationEndAt(),
                request.votingStartAt(),
                request.votingEndAt()
        );
    }

    private void validateElectionWindows(
            Instant registrationStartAt,
            Instant registrationEndAt,
            Instant votingStartAt,
            Instant votingEndAt
    ) {
        if (!registrationStartAt.isBefore(registrationEndAt)) {
            throw new ElectionLifecycleException("Registration window must start before it ends");
        }
        if (!votingStartAt.isBefore(votingEndAt)) {
            throw new ElectionLifecycleException("Voting window must start before it ends");
        }
        if (registrationEndAt.isAfter(votingStartAt)) {
            throw new ElectionLifecycleException("Registration must end before voting starts");
        }
    }

    private void validateContestTypeForElection(ElectionType electionType, ContestType contestType) {
        boolean allowed = switch (electionType) {
            case MIXED -> true;
            case NATIONAL -> contestType == ContestType.NATIONAL || contestType == ContestType.PROVINCIAL;
            case PROVINCIAL -> contestType == ContestType.PROVINCIAL;
            case MUNICIPAL -> contestType == ContestType.MUNICIPAL_PR || contestType == ContestType.MUNICIPAL_WARD;
        };

        if (!allowed) {
            throw new ElectionLifecycleException(
                    "Contest type " + contestType + " is not valid for a " + electionType + " election"
            );
        }
    }

    private void validateContestScope(
            ContestType contestType,
            String scopeProvince,
            String scopeMunicipality,
            Integer scopeWardNumber
    ) {
        boolean valid = switch (contestType) {
            case NATIONAL -> scopeProvince == null && scopeMunicipality == null && scopeWardNumber == null;
            case PROVINCIAL -> scopeProvince != null && scopeMunicipality == null && scopeWardNumber == null;
            case MUNICIPAL_PR -> scopeProvince != null && scopeMunicipality != null && scopeWardNumber == null;
            case MUNICIPAL_WARD -> scopeProvince != null && scopeMunicipality != null && scopeWardNumber != null;
        };

        if (!valid) {
            throw new ElectionLifecycleException(switch (contestType) {
                case NATIONAL -> "National contests must not declare a geographic scope";
                case PROVINCIAL -> "Provincial contests require scopeProvince only";
                case MUNICIPAL_PR -> "Municipal PR contests require scopeProvince and scopeMunicipality only";
                case MUNICIPAL_WARD -> "Municipal ward contests require scopeProvince, scopeMunicipality, and scopeWardNumber";
            });
        }
    }

    // A DRAFT election whose registration start has passed has not opened yet (the ballot was not ready),
    // so no voter can be registered. Keep configuration open until the registration window ends so the
    // election can still be completed and opened by the lifecycle scheduler.
    private void ensureElectionAllowsBallotConfiguration(Election election) {
        if (election.getStatus() != ElectionStatus.DRAFT) {
            throw new ElectionLifecycleException("Ballot configuration is locked once registration opens");
        }
        if (!Instant.now(clock).isBefore(election.getRegistrationEndAt())) {
            throw new ElectionLifecycleException(
                    "Ballot configuration is closed because the registration window has ended; cancel this election and create a new one"
            );
        }
    }

    private Election getElectionForUpdateOrThrow(UUID electionId) {
        return electionRepository.findByIdForUpdate(electionId)
                .orElseThrow(() -> new ResourceNotFoundException("Election not found"));
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Contest getContestOrThrow(UUID electionId, UUID contestId) {
        return contestRepository.findByIdAndElectionId(contestId, electionId)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found"));
    }

    private ContestResponse toContestResponse(Contest contest) {
        return ContestResponse.from(
                contest,
                contestOptionRepository.findByContestIdOrderByDisplayOrderAscNameAsc(contest.getId())
        );
    }
}
