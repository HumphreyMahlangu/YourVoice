package io.github.humphreymahlangu.yourvoice.dto;

import io.github.humphreymahlangu.yourvoice.entity.VerificationStatus;
import io.github.humphreymahlangu.yourvoice.entity.VoterVerification;
import java.time.Instant;
public record ProfileVerificationResponse(String fullName, String source, String sourceReference,
        VerificationStatus identityStatus, VerificationStatus locationStatus, boolean sourceAvailable,
        RegisteredLocationResponse location, String message, Instant checkedAt, Instant effectiveAt) {
    public static ProfileVerificationResponse from(VoterVerification state, boolean available) {
        return new ProfileVerificationResponse(state.getVoterProfile().getFullName(), "FICTIONAL_SIMULATION", state.getSourceReference(),
                available ? state.getIdentityStatus() : VerificationStatus.UNAVAILABLE,
                available ? state.getLocationStatus() : VerificationStatus.UNAVAILABLE, available,
                RegisteredLocationResponse.from(state.getLocation()),
                available ? state.getMessage() : "The fictional verification source is unavailable. Try again later; your existing registrations keep their saved area.",
                state.getCheckedAt(), state.getEffectiveAt());
    }
}
