package io.github.humphreymahlangu.yourvoice.dto;

import io.github.humphreymahlangu.yourvoice.entity.Election;
import io.github.humphreymahlangu.yourvoice.entity.ElectionRegistration;
import io.github.humphreymahlangu.yourvoice.entity.VotingDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Election registration summary for an authenticated voter")
public record ElectionRegistrationResponse(
        @Schema(description = "Registration identifier", example = "11111111-1111-1111-1111-111111111111")
        UUID id,

        @Schema(description = "Election identifier", example = "22222222-2222-2222-2222-222222222222")
        UUID electionId,

        @Schema(description = "Election display name", example = "2026 Local Government Election")
        String electionName,

        @Schema(description = "Election type", example = "LOCAL_GOVERNMENT")
        String electionType,

        @Schema(description = "Registration status", example = "REGISTERED")
        String status,

        @Schema(description = "UTC timestamp when registration was accepted", example = "2026-08-02T10:15:30Z")
        Instant registeredAt,

        @Schema(description = "Registered voting district identifier", example = "33333333-3333-3333-3333-333333333333")
        UUID votingDistrictId,

        @Schema(description = "Registered voting district code", example = "WC-CPT-12")
        String votingDistrictCode,

        @Schema(description = "Registered voting district name", example = "Cape Town Ward 12")
        String votingDistrictName,

        @Schema(description = "Registered province", example = "Western Cape")
        String province
) {

    public static ElectionRegistrationResponse from(ElectionRegistration registration) {
        Election election = registration.getElection();
        VotingDistrict votingDistrict = registration.getVotingDistrict();
        return new ElectionRegistrationResponse(
                registration.getId(),
                election.getId(),
                election.getName(),
                election.getType().name(),
                registration.getStatus().name(),
                registration.getRegisteredAt(),
                votingDistrict.getId(),
                votingDistrict.getCode(),
                votingDistrict.getName(),
                votingDistrict.getProvince()
        );
    }
}
