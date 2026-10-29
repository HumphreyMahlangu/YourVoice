package io.github.humphreymahlangu.yourvoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
public record ReviewLocationCorrection(boolean approve, @NotBlank @Size(max = 500) String reason,
        @Size(max = 80) String sourceReference, Instant effectiveAt) {
    /** Prevent framework debug tracing from serialising sensitive record components into logs. */
    @Override
    public String toString() { return "ReviewLocationCorrection[redacted]"; }
}
