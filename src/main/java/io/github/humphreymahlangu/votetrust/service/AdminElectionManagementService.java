package io.github.humphreymahlangu.votetrust.service;

import io.github.humphreymahlangu.votetrust.dto.ContestOptionResponse;
import io.github.humphreymahlangu.votetrust.dto.ContestResponse;
import io.github.humphreymahlangu.votetrust.dto.ContestStatusUpdateRequest;
import io.github.humphreymahlangu.votetrust.dto.CreateContestOptionRequest;
import io.github.humphreymahlangu.votetrust.dto.CreateContestRequest;
import io.github.humphreymahlangu.votetrust.dto.CreateElectionRequest;
import io.github.humphreymahlangu.votetrust.dto.CreateVotingDistrictRequest;
import io.github.humphreymahlangu.votetrust.dto.ElectionResponse;
import io.github.humphreymahlangu.votetrust.dto.ElectionStatusUpdateRequest;
import io.github.humphreymahlangu.votetrust.dto.VotingDistrictResponse;
import io.github.humphreymahlangu.votetrust.entity.Contest;
import io.github.humphreymahlangu.votetrust.entity.ContestOption;
import io.github.humphreymahlangu.votetrust.entity.ContestStatus;
import io.github.humphreymahlangu.votetrust.entity.ContestType;
import io.github.humphreymahlangu.votetrust.entity.Election;
import io.github.humphreymahlangu.votetrust.entity.ElectionStatus;
import io.github.humphreymahlangu.votetrust.entity.ElectionType;
import io.github.humphreymahlangu.votetrust.entity.VotingDistrict;
import io.github.humphreymahlangu.votetrust.exception.DuplicateResourceException;
import io.github.humphreymahlangu.votetrust.exception.ElectionLifecycleException;
import io.github.humphreymahlangu.votetrust.exception.ResourceNotFoundException;
import io.github.humphreymahlangu.votetrust.repository.ContestOptionRepository;
import io.github.humphreymahlangu.votetrust.repository.ContestRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingDistrictRepository;
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
    private final Clock clock;

    public AdminElectionManagementService(
            VotingDistrictRepository votingDistrictRepository,
            ElectionRepository electionRepository,
            ContestRepository contestRepository,
            ContestOptionRepository contestOptionRepository,
            ElectionLifecycleService electionLifecycleService,
            Clock clock
    ) {
        this.votingDistrictRepository = votingDistrictRepository;
        this.electionRepository = electionRepository;
        this.contestRepository = contestRepository;
        this.contestOptionRepository = contestOptionRepository;
        this.electionLifecycleService = electionLifecycleService;
        this.clock = clock;
    }

    @Transactional
    public VotingDistrictResponse createVotingDistrict(CreateVotingDistrictRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (votingDistrictRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("A voting district with this code already exists");
        }

        VotingDistrict votingDistrict = votingDistrictRepository.save(new VotingDistrict(
                code,
                request.name().trim(),
                request.province().trim(),
                request.municipality().trim(),
                request.wardNumber()
        ));
        return toVotingDistrictResponse(votingDistrict);
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
        return toElectionResponse(election);
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
        return toElectionResponse(election);
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
        if (contest.getStatus() != ContestStatus.DRAFT) {
            throw new ElectionLifecycleException("Contest ballot options can be changed only while the contest is DRAFT");
        }

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
        return toOptionResponse(contestOption);
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

    private void validateElectionWindows(CreateElectionRequest request) {
        if (!request.registrationStartAt().isBefore(request.registrationEndAt())) {
            throw new ElectionLifecycleException("Registration window must start before it ends");
        }
        if (!request.votingStartAt().isBefore(request.votingEndAt())) {
            throw new ElectionLifecycleException("Voting window must start before it ends");
        }
        if (request.registrationEndAt().isAfter(request.votingStartAt())) {
            throw new ElectionLifecycleException("Registration must end before voting starts");
        }
    }

    private void validateContestTypeForElection(ElectionType electionType, ContestType contestType) {
        boolean allowed = switch (electionType) {
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

    private VotingDistrictResponse toVotingDistrictResponse(VotingDistrict votingDistrict) {
        return new VotingDistrictResponse(
                votingDistrict.getId(),
                votingDistrict.getCode(),
                votingDistrict.getName(),
                votingDistrict.getProvince(),
                votingDistrict.getMunicipality(),
                votingDistrict.getWardNumber()
        );
    }

    private ElectionResponse toElectionResponse(Election election) {
        return new ElectionResponse(
                election.getId(),
                election.getName(),
                election.getType().name(),
                election.getStatus().name(),
                election.getRegistrationStartAt(),
                election.getRegistrationEndAt(),
                election.getVotingStartAt(),
                election.getVotingEndAt()
        );
    }

    private ContestResponse toContestResponse(Contest contest) {
        return new ContestResponse(
                contest.getId(),
                contest.getElection().getId(),
                contest.getName(),
                contest.getType().name(),
                contest.getStatus().name(),
                contest.getDisplayOrder(),
                contest.getScopeProvince(),
                contest.getScopeMunicipality(),
                contest.getScopeWardNumber(),
                contestOptionRepository.findByContestIdOrderByDisplayOrderAscNameAsc(contest.getId())
                        .stream()
                        .map(this::toOptionResponse)
                        .toList()
        );
    }

    private ContestOptionResponse toOptionResponse(ContestOption contestOption) {
        return new ContestOptionResponse(
                contestOption.getId(),
                contestOption.getName(),
                contestOption.getOptionType().name(),
                contestOption.getDisplayOrder()
        );
    }
}
