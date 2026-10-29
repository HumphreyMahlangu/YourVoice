package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.dto.ElectionRegistrationRequest;
import io.github.humphreymahlangu.yourvoice.dto.ElectionRegistrationResponse;
import io.github.humphreymahlangu.yourvoice.entity.Election;
import io.github.humphreymahlangu.yourvoice.entity.ElectionRegistration;
import io.github.humphreymahlangu.yourvoice.entity.ElectionStatus;
import io.github.humphreymahlangu.yourvoice.entity.RegistrationStatus;
import io.github.humphreymahlangu.yourvoice.entity.VoterProfile;
import io.github.humphreymahlangu.yourvoice.entity.VotingDistrict;
import io.github.humphreymahlangu.yourvoice.exception.DuplicateResourceException;
import io.github.humphreymahlangu.yourvoice.exception.EligibilityException;
import io.github.humphreymahlangu.yourvoice.exception.RegistrationClosedException;
import io.github.humphreymahlangu.yourvoice.exception.ResourceNotFoundException;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRegistrationRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRepository;
import io.github.humphreymahlangu.yourvoice.repository.VoterProfileRepository;
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
    private final VoterProfileVerificationService verification;
    private final VoterEligibilityService eligibility;
    private final io.github.humphreymahlangu.yourvoice.repository.ContestRepository contests;

    public VoterRegistrationService(
            ElectionRepository electionRepository,
            VoterProfileRepository voterProfileRepository,
            ElectionRegistrationRepository electionRegistrationRepository,
            Clock clock,
            VoterProfileVerificationService verification,
            VoterEligibilityService eligibility,
            io.github.humphreymahlangu.yourvoice.repository.ContestRepository contests
    ) {
        this.electionRepository = electionRepository;
        this.voterProfileRepository = voterProfileRepository;
        this.electionRegistrationRepository = electionRegistrationRepository;
        this.clock = clock;
        this.verification = verification; this.eligibility = eligibility; this.contests = contests;
    }

    @Transactional
    public ElectionRegistrationResponse registerForElection(
            UUID userAccountId,
            UUID electionId,
            ElectionRegistrationRequest request
    ) {
        var trusted = verification.lockState(userAccountId);
        Election election = electionRepository.findByIdForUpdate(electionId)
                .orElseThrow(() -> new ResourceNotFoundException("Election not found"));
        assertRegistrationPeriodOpen(election);
        eligibility.requireIdentity(trusted, election);
        VoterProfile voterProfile = trusted.getVoterProfile();
        VotingDistrict votingDistrict = trusted.getLocation().getDistrict();
        UUID confirmedDistrictId = request == null ? null : request.votingDistrictId();
        if (confirmedDistrictId != null && (votingDistrict == null || !confirmedDistrictId.equals(votingDistrict.getId())))
            throw new EligibilityException("Voting district must match the district on your verified simulation profile");
        ElectionRegistration registration = electionRegistrationRepository.findByVoterProfileUserAccountIdAndElectionId(userAccountId, electionId).orElse(null);
        if (registration != null && registration.getStatus() != RegistrationStatus.ACTIVE)
            throw new EligibilityException("This election registration is not active");
        if (registration != null && registration.isIdentityVerified()) throw new DuplicateResourceException("Voter is already registered for this election");
        var allowedArea = RegistrationLocationPolicy.forRegistration(trusted, registration);
        if (contests.findByElectionId(electionId).stream().noneMatch(contest -> ContestEligibility.isEligible(contest, allowedArea)))
            throw new EligibilityException("No contest in this election matches your verified attributes. Review your profile; an existing registration's area cannot be changed.");
        if (registration == null) registration = new ElectionRegistration(voterProfile, election,
                votingDistrict, RegistrationStatus.ACTIVE, Instant.now(clock));
        registration.confirmVerification(trusted, allowedArea);

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
                && now.isBefore(election.getRegistrationEndAt());

        if (election.getStatus() != ElectionStatus.REGISTRATION_OPEN || !withinRegistrationWindow) {
            throw new RegistrationClosedException("Election registration period is closed");
        }
    }
}
