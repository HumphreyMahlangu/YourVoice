package io.github.humphreymahlangu.yourvoice.dto;

import io.github.humphreymahlangu.yourvoice.entity.SimulationVoterRecord;
import io.github.humphreymahlangu.yourvoice.entity.VerificationStatus;
import java.time.Instant;
import java.util.UUID;
/** Never exposes an identity number, hash or voter account identifier. */
public record SimulationVoterResponse(UUID id, String reference, String fictionalName, String source,
        VerificationStatus identityStatus, VerificationStatus locationStatus, RegisteredLocationResponse location,
        String reason, Instant recordedAt) {
    public static SimulationVoterResponse from(SimulationVoterRecord record) {
        return new SimulationVoterResponse(record.getId(), record.getReference(), record.getFullName(), "FICTIONAL_SIMULATION",
                record.getIdentityStatus(), record.getLocationStatus(), RegisteredLocationResponse.from(record.getLocation()),
                record.getReason(), record.getRecordedAt());
    }
}
