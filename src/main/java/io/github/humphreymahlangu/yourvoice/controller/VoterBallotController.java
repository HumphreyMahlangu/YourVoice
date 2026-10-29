package io.github.humphreymahlangu.yourvoice.controller;

import io.github.humphreymahlangu.yourvoice.dto.VoterBallotResponse;
import io.github.humphreymahlangu.yourvoice.security.UserPrincipal;
import io.github.humphreymahlangu.yourvoice.service.VoterBallotDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/v1/me/ballots")
@PreAuthorize("hasRole('VOTER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Own eligible ballots", description = "Server-derived own contest eligibility, registration and availability")
@ApiResponses({@ApiResponse(responseCode = "200", description = "Own decisions with pagination total"),
        @ApiResponse(responseCode = "401", description = "Sign in required"), @ApiResponse(responseCode = "403", description = "Voter role required")})
public class VoterBallotController {
    private final VoterBallotDiscoveryService discovery;
    public VoterBallotController(VoterBallotDiscoveryService discovery) { this.discovery = discovery; }
    @GetMapping
    @Operation(summary = "Discover own eligible ballots", description = "Uses fictional verification and immutable registration snapshots. Includes actionable reasons for unavailable contests; public discovery is never an authorisation decision.")
    public ResponseEntity<List<VoterBallotResponse>> ballots(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID electionId, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "200") @Min(1) @Max(500) int size) {
        return PageRequestParameters.toResponse(discovery.ballots(principal.id(), electionId, PageRequest.of(page, size)));
    }
}
