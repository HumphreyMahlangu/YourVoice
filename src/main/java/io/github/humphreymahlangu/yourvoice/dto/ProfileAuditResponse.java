package io.github.humphreymahlangu.yourvoice.dto;

import io.github.humphreymahlangu.yourvoice.entity.ProfileVerificationAudit;
import java.time.Instant;
import java.util.UUID;
public record ProfileAuditResponse(UUID id, UUID voterProfileId, UUID actorUserId, UUID correctionRequestId,
        String action, String sourceReference, String reason, Instant occurredAt, Instant effectiveAt) {
    public static ProfileAuditResponse from(ProfileVerificationAudit entry) {
        return new ProfileAuditResponse(entry.getId(), entry.getVoterProfileId(), entry.getActorUserId(), entry.getCorrectionRequestId(),
                entry.getAction(), entry.getSourceReference(), entry.getReason(), entry.getOccurredAt(), entry.getEffectiveAt());
    }
}
