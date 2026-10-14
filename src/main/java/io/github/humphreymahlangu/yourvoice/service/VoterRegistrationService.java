package io.github.humphreymahlangu.votetrust.service;

import io.github.humphreymahlangu.votetrust.dto.ElectionRegistrationRequest;
import io.github.humphreymahlangu.votetrust.dto.ElectionRegistrationResponse;
import io.github.humphreymahlangu.votetrust.entity.Election;
import io.github.humphreymahlangu.votetrust.entity.ElectionRegistration;
import io.github.humphreymahlangu.votetrust.entity.ElectionStatus;
import io.github.humphreymahlangu.votetrust.entity.RegistrationStatus;
import io.github.humphreymahlangu.votetrust.entity.VoterProfile;
import io.github.humphreymahlangu.votetrust.entity.VotingDistrict;
import io.github.humphreymahlangu.votetrust.exception.DuplicateResourceException;
import io.github.humphreymahlangu.votetrust.exception.EligibilityException;
import io.github.humphreymahlangu.votetrust.exception.RegistrationClosedException;
import io.github.humphreymahlangu.votetrust.exception.ResourceNotFoundException;
import io.github.humphreymahlangu.votetrust.repository.ElectionRegistrationRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRepository;
import io.github.humphreymahlangu.votetrust.repository.VoterProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoterRegistrationService {

    private final ElectionRepository electionRepository;
    private final VoterProfileRepository voterProfileRepository;
    private final ElectionRegistrationRepository electionRegistrationRepository;
    private final Clock clock;

    public VoterRegistrationService(
            ElectionRepository electionRepository,
            VoterProfileRepository voterProfileRepository,
            ElectionRegistrationRepository electionRegistrationRepository,
            Clock clock
    ) {
        this.electionRepository = electionRepository;
        this.voterProfileRepository = voterProfileRepository;
        this.electionRegistrationRepository = electionRegistrationRepository;
        this.clock = clock;
    }

    @Transactional
    public ElectionRegistrationResponse registerForElection(
            UUID userAccountId,
            UUID electionId,
            ElectionRegistrationRequest request
    ) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new ResourceNotFoundException("Election not found"));
        assertRegistrationPeriodOpen(election);

        VoterProfile voterProfile = voterProfileRepository.findByUserAccountId(userAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Voter profile not found"));
        VotingDistrict votingDistrict = voterProfile.getVotingDistrict();
        UUID confirmedDistrictId = request == null ? null : request.votingDistrictId();
        if (confirmedDistrictId != null && !confirmedDistrictId.equals(votingDistrict.getId())) {
            throw new EligibilityException("Voting district must match the district on your voter profile");
        }

        if (electionRegistrationRepository.existsByVoterProfileIdAndElectionId(voterProfile.getId(), electionId)) {
            throw new DuplicateResourceException("Voter is already registered for this election");
        }

        ElectionRegistration registration = new ElectionRegistration(
                voterProfile,
                election,
                votingDistrict,
                RegistrationStatus.ACTIVE,
                Instant.now(clock)
        );

        return ElectionRegistrationResponse.from(electionRegistrationRepository.save(registration));
    }

    @Transactional(readOnly = true)
    public Page<ElectionRegistrationResponse> listMyRegistrations(UUID userAccountId, Pageable pageable) {
        return electionRegistrationRepository.findByVoterProfileUserAccountIdOrderByRegisteredAtDesc(userAccountId, pageable)
                .map(ElectionRegistrationResponse::from);
    }

    private void assertRegistrationPeriodOpen(Election election) {
        Instant now = Instant.now(clock);
        boolean withinRegistrationWindow = !now.isBefore(election.getRegistrationStartAt())
                && !now.isAfter(election.getRegistrationEndAt());

        if (election.getStatus() != ElectionStatus.REGISTRATION_OPEN || !withinRegistrationWindow) {
            throw new RegistrationClosedException("Election registration period is closed");
        }
    }
}
