package io.github.humphreymahlangu.yourvoice.dto;

import io.github.humphreymahlangu.yourvoice.entity.LocationCorrectionRequest;
import io.github.humphreymahlangu.yourvoice.entity.CorrectionStatus;
import java.time.Instant;
import java.util.UUID;
public record LocationCorrectionResponse(UUID id, String voterName, RegisteredLocationResponse requestedLocation,
        String reason, CorrectionStatus status, Instant requestedAt, Instant reviewedAt,
        String reviewReason, String sourceReference, Instant effectiveAt) {
    public static LocationCorrectionResponse from(LocationCorrectionRequest request) {
        return new LocationCorrectionResponse(request.getId(), request.getVoterProfile().getFullName(),
                RegisteredLocationResponse.from(request.getLocation()), request.getRequestReason(), request.getStatus(),
                request.getRequestedAt(), request.getReviewedAt(), request.getReviewReason(), request.getSourceReference(), request.getEffectiveAt());
    }
}
