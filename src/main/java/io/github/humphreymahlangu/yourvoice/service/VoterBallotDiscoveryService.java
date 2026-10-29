package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.dto.ContestResponse;
import io.github.humphreymahlangu.yourvoice.dto.ElectionResponse;
import io.github.humphreymahlangu.yourvoice.dto.VoterBallotResponse;
import io.github.humphreymahlangu.yourvoice.entity.ContestStatus;
import io.github.humphreymahlangu.yourvoice.entity.ElectionStatus;
import io.github.humphreymahlangu.yourvoice.repository.ContestRepository;
import io.github.humphreymahlangu.yourvoice.repository.ContestOptionRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRegistrationRepository;
import io.github.humphreymahlangu.yourvoice.repository.VotingRightRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class VoterBallotDiscoveryService {
    private final VoterProfileVerificationService profiles;
    private final VoterEligibilityService eligibility;
    private final ElectionRepository elections;
    private final ContestRepository contests;
    private final ContestOptionRepository options;
    private final ElectionRegistrationRepository registrations;
    private final VotingRightRepository rights;
    private final Clock clock;
    public VoterBallotDiscoveryService(VoterProfileVerificationService profiles, VoterEligibilityService eligibility,
            ElectionRepository elections, ContestRepository contests, ContestOptionRepository options,
            ElectionRegistrationRepository registrations, VotingRightRepository rights, Clock clock) {
        this.profiles = profiles; this.eligibility = eligibility; this.elections = elections; this.contests = contests;
        this.options = options; this.registrations = registrations; this.rights = rights; this.clock = clock;
    }
    @Transactional
    public Page<VoterBallotResponse> ballots(UUID userId, UUID electionId, Pageable pageable) {
        var state = profiles.lockState(userId);
        var rows = new ArrayList<VoterBallotResponse>(); Instant now = Instant.now(clock);
        for (var election : elections.findByStatusInOrderByRegistrationStartAtDesc(ElectionVisibility.PUBLIC_STATUSES)) {
            if (electionId != null && !electionId.equals(election.getId())) continue;
            var registration = registrations.findByVoterProfileUserAccountIdAndElectionId(userId, election.getId()).orElse(null);
            boolean registrationOpen = election.getStatus() == ElectionStatus.REGISTRATION_OPEN
                    && !now.isBefore(election.getRegistrationStartAt()) && now.isBefore(election.getRegistrationEndAt());
            for (var contest : contests.findByElectionIdOrderByDisplayOrderAscNameAsc(election.getId())) {
                String problem = eligibility.contestProblem(contest, state, registration);
                boolean issued = rights.existsByVoterProfileIdAndContestIdAndCredentialIssuedTrue(state.getVoterProfile().getId(), contest.getId());
                boolean canRegister = registrationOpen && (registration == null || !registration.isIdentityVerified())
                        && eligibility.identityProblem(state, election) == null
                        && ContestEligibility.isEligible(contest, RegistrationLocationPolicy.forRegistration(state, registration));
                boolean votingOpen = election.getStatus() == ElectionStatus.VOTING_OPEN && contest.getStatus() == ContestStatus.OPEN
                        && !now.isBefore(election.getVotingStartAt()) && now.isBefore(election.getVotingEndAt());
                boolean canVote = problem == null && registration != null && votingOpen;
                String message = problem;
                if (message == null) {
                    if (registration == null) message = registrationOpen ? "Register for this election to access this ballot." : "Registration is closed; you are not registered for this election.";
                    else if (issued) message = "A credential was already issued. Continue on the page holding it; this does not confirm whether a ballot was cast.";
                    else if (canVote) message = "Your verified registration allows this ballot. Review your choice before casting online.";
                    else message = "Voting is not open. Check this election's dates.";
                }
                rows.add(new VoterBallotResponse(ElectionResponse.from(election), ContestResponse.from(contest, options.findByContestIdOrderByDisplayOrderAscNameAsc(contest.getId())),
                        problem == null, registration != null, registration != null && registration.isIdentityVerified(), canRegister, canVote, issued, message,
                        registration == null ? null : registration.getRegisteredProvince(), registration == null ? null : registration.getRegisteredMunicipality(),
                        registration == null ? null : registration.getRegisteredWardNumber()));
            }
        }
        int from = (int)Math.min(pageable.getOffset(), rows.size()); int to = Math.min(from + pageable.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(from, to), pageable, rows.size());
    }
}
