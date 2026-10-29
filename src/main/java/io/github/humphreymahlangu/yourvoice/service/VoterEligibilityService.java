package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.entity.Contest;
import io.github.humphreymahlangu.yourvoice.entity.Election;
import io.github.humphreymahlangu.yourvoice.entity.ElectionRegistration;
import io.github.humphreymahlangu.yourvoice.entity.RegisteredLocation;
import io.github.humphreymahlangu.yourvoice.entity.VerificationStatus;
import io.github.humphreymahlangu.yourvoice.entity.VoterVerification;
import io.github.humphreymahlangu.yourvoice.exception.EligibilityException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
@Service
public class VoterEligibilityService {
    private final VoterProfileVerificationService profiles;
    public VoterEligibilityService(VoterProfileVerificationService profiles) { this.profiles = profiles; }
    public String identityProblem(VoterVerification state, Election election) {
        if (!profiles.sourceAvailable() || state.getIdentityStatus() == VerificationStatus.UNAVAILABLE) return "The fictional verification source is unavailable. Try again later.";
        if (state.getIdentityStatus() != VerificationStatus.VERIFIED) return "Complete your fictional identity check in Your profile before registering or voting.";
        LocalDate votingDay = LocalDate.ofInstant(election.getVotingStartAt(), ZoneOffset.UTC);
        if (state.getVoterProfile().getDateOfBirth().plusYears(18).isAfter(votingDay)) return "You must be 18 by voting day for this election.";
        return null;
    }
    public void requireIdentity(VoterVerification state, Election election) {
        profiles.requireSource();
        if (state.getIdentityStatus() == VerificationStatus.UNAVAILABLE) throw new io.github.humphreymahlangu.yourvoice.exception.VerificationUnavailableException();
        String problem = identityProblem(state, election);
        if (problem != null) throw new EligibilityException(problem);
    }
    public String contestProblem(Contest contest, VoterVerification state, ElectionRegistration registration) {
        String identity = identityProblem(state, contest.getElection());
        if (identity != null) return identity;
        if (registration != null && registration.getStatus() != io.github.humphreymahlangu.yourvoice.entity.RegistrationStatus.ACTIVE)
            return "This election registration is not active.";
        if (registration != null && !registration.isIdentityVerified())
            return "Confirm this existing registration against your fictional verification while registration is open.";
        boolean matches = registration == null
                ? ContestEligibility.isEligible(contest, RegistrationLocationPolicy.forRegistration(state, null))
                : ContestEligibility.isEligible(contest, registration);
        if (matches) return null;
        return switch (contest.getType()) {
            case NATIONAL -> "Complete your fictional identity check.";
            case PROVINCIAL -> "This ballot requires a verified matching province. Review your simulation profile.";
            case MUNICIPAL_PR -> "This ballot requires a verified matching province and municipality. Request an area correction if needed.";
            case MUNICIPAL_WARD -> "This ballot requires a verified matching province, municipality and ward. Request an area correction if needed.";
        };
    }
}
