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

import io.github.humphreymahlangu.yourvoice.dto.ProfileVerificationResponse;
import io.github.humphreymahlangu.yourvoice.dto.LocationCorrectionResponse;
import io.github.humphreymahlangu.yourvoice.dto.RequestLocationCorrection;
import io.github.humphreymahlangu.yourvoice.security.UserPrincipal;
import io.github.humphreymahlangu.yourvoice.service.VoterProfileVerificationService;
import java.util.List;

@RestController
@RequestMapping("/api/v1/me/profile")
@PreAuthorize("hasRole('VOTER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Simulation profile", description = "Own fictional verification and area corrections; never official voter-roll verification")
@ApiResponses({@ApiResponse(responseCode = "200", description = "Request completed"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Sign in required"),
        @ApiResponse(responseCode = "403", description = "Role not allowed"),
        @ApiResponse(responseCode = "409", description = "Conflicts with an existing decision"),
        @ApiResponse(responseCode = "422", description = "Verification or location requirements not met"),
        @ApiResponse(responseCode = "503", description = "Fictional verification source unavailable")})
public class ProfileVerificationController {
    private final VoterProfileVerificationService profiles;
    public ProfileVerificationController(VoterProfileVerificationService profiles) { this.profiles = profiles; }
    @GetMapping
    @Operation(summary = "Read own simulation verification", description = "Separates identity and location verification, applies due approved corrections, and reports fictional source availability.")
    public ProfileVerificationResponse profile(@AuthenticationPrincipal UserPrincipal principal) { return profiles.profile(principal.id()); }
    @PostMapping("/verification")
    @Operation(summary = "Check fictional registry", description = "Matches the account's protected identity and name to an administrator-configured fictional record. Does not change an established area without correction review.")
    public ProfileVerificationResponse verify(@AuthenticationPrincipal UserPrincipal principal) { return profiles.requestVerification(principal.id()); }
    @GetMapping("/corrections")
    @Operation(summary = "Read own corrections", description = "Returns only the signed-in voter's requests and review reasons. Requests do not grant eligibility.")
    public List<LocationCorrectionResponse> corrections(@AuthenticationPrincipal UserPrincipal principal) { return profiles.myCorrections(principal.id()); }
    @PostMapping("/corrections")
    @Operation(summary = "Request an area correction", description = "Validates geographic hierarchy and queues private administrator review. Existing election registrations remain frozen.")
    public LocationCorrectionResponse request(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody RequestLocationCorrection request) {
        return profiles.requestCorrection(principal.id(), request);
    }
}
