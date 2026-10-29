package io.github.humphreymahlangu.yourvoice.dto;

import io.github.humphreymahlangu.yourvoice.entity.VerificationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record CreateSimulationVoterRequest(
        @NotBlank @Pattern(regexp = "(?:SIM|DEMO)-[A-Z0-9-]+") @Size(max = 80) String reference,
        @AssertTrue(message = "Only explicitly fictional records may be configured") boolean fictional,
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Pattern(regexp = "[0-9]{13}") String southAfricanIdNumber,
        @NotNull VerificationStatus identityStatus, @NotNull VerificationStatus locationStatus,
        @Valid LocationSelection location, @NotBlank @Size(max = 500) String reason) {
    /** Prevent framework debug tracing from serialising sensitive record components into logs. */
    @Override
    public String toString() { return "CreateSimulationVoterRequest[redacted]"; }
}
