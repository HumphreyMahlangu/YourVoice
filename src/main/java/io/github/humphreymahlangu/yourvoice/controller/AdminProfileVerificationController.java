package io.github.humphreymahlangu.yourvoice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.humphreymahlangu.yourvoice.dto.CreateSimulationVoterRequest;
import io.github.humphreymahlangu.yourvoice.dto.SimulationVoterResponse;
import io.github.humphreymahlangu.yourvoice.dto.LocationCorrectionResponse;
import io.github.humphreymahlangu.yourvoice.dto.ReviewLocationCorrection;
import io.github.humphreymahlangu.yourvoice.dto.ProfileAuditResponse;
import io.github.humphreymahlangu.yourvoice.repository.SimulationVoterRecordRepository;
import io.github.humphreymahlangu.yourvoice.repository.LocationCorrectionRequestRepository;
import io.github.humphreymahlangu.yourvoice.repository.ProfileVerificationAuditRepository;
import io.github.humphreymahlangu.yourvoice.security.UserPrincipal;
import io.github.humphreymahlangu.yourvoice.service.VoterProfileVerificationService;
import io.github.humphreymahlangu.yourvoice.service.ProfileVerificationAdministrationService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/admin/profile-verification")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Fictional verification administration", description = "Admin-only fictional source, private correction decisions and privacy-conscious audit")
@ApiResponses({@ApiResponse(responseCode = "200", description = "Request completed"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Sign in required"),
        @ApiResponse(responseCode = "403", description = "Role not allowed"),
        @ApiResponse(responseCode = "409", description = "Conflicts with an existing decision"),
        @ApiResponse(responseCode = "422", description = "Verification or location requirements not met"),
        @ApiResponse(responseCode = "503", description = "Fictional verification source unavailable")})
public class AdminProfileVerificationController {
    private final VoterProfileVerificationService profiles;
    private final ProfileVerificationAdministrationService administration;
    public AdminProfileVerificationController(VoterProfileVerificationService profiles, ProfileVerificationAdministrationService administration) {
        this.profiles = profiles; this.administration = administration;
    }
    @PostMapping("/records")
    @Operation(summary = "Configure a fictional source record", description = "Explicitly fictional only. Hashes the synthetic identity immediately, validates configured geography and records the administrator. Does not verify an existing profile automatically.")
    public SimulationVoterResponse create(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreateSimulationVoterRequest request) {
        return profiles.createRecord(principal.id(), request);
    }
    @GetMapping("/records")
    @Operation(summary = "List fictional source records", description = "Paged fictional record summaries, excluding identity numbers, hashes and account identifiers.")
    public ResponseEntity<List<SimulationVoterResponse>> records(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "200") @Min(1) @Max(500) int size) {
        return PageRequestParameters.toResponse(administration.records(page, size));
    }
    @GetMapping("/corrections")
    @Operation(summary = "List correction requests", description = "Paged administrator review queue. Personal reasons are redacted after 90 days once closed.")
    public ResponseEntity<List<LocationCorrectionResponse>> corrections(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "200") @Min(1) @Max(500) int size) {
        return PageRequestParameters.toResponse(administration.corrections(page, size));
    }
    @PostMapping("/corrections/{requestId}/review")
    @Operation(summary = "Review a location correction", description = "Approval requires the latest matching fictional source and an effective date. Reject with a reason. Never changes an existing registration or links to a ballot choice.")
    public LocationCorrectionResponse review(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID requestId,
            @Valid @RequestBody ReviewLocationCorrection request) { return profiles.review(principal.id(), requestId, request); }
    @GetMapping("/audit")
    @Operation(summary = "Read verification decision audit", description = "Private actor, source, reason and effective-date records. Contains no ballots, token material or raw identity numbers.")
    public ResponseEntity<List<ProfileAuditResponse>> audit(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "200") @Min(1) @Max(500) int size) {
        return PageRequestParameters.toResponse(administration.audit(page, size));
    }
}
