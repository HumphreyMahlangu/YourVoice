package io.github.humphreymahlangu.yourvoice.controller;

import io.github.humphreymahlangu.yourvoice.dto.ApiErrorResponse;
import io.github.humphreymahlangu.yourvoice.dto.ContestOptionResponse;
import io.github.humphreymahlangu.yourvoice.dto.ContestResponse;
import io.github.humphreymahlangu.yourvoice.dto.ContestStatusUpdateRequest;
import io.github.humphreymahlangu.yourvoice.dto.CreateContestOptionRequest;
import io.github.humphreymahlangu.yourvoice.dto.CreateContestRequest;
import io.github.humphreymahlangu.yourvoice.dto.CreateElectionRequest;
import io.github.humphreymahlangu.yourvoice.dto.CreateVotingDistrictRequest;
import io.github.humphreymahlangu.yourvoice.dto.ElectionResponse;
import io.github.humphreymahlangu.yourvoice.dto.ElectionLifecycleEventResponse;
import io.github.humphreymahlangu.yourvoice.dto.ElectionStatusUpdateRequest;
import io.github.humphreymahlangu.yourvoice.dto.UpdateElectionRequest;
import io.github.humphreymahlangu.yourvoice.dto.VotingDistrictResponse;
import io.github.humphreymahlangu.yourvoice.service.AdminElectionManagementService;
import io.github.humphreymahlangu.yourvoice.service.ElectionLifecycleService;
import io.github.humphreymahlangu.yourvoice.security.UserPrincipal;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Election Management", description = "Admin APIs for districts, elections, contests, options, and lifecycle transitions")
@ApiResponses({
        @ApiResponse(
                responseCode = "400",
                description = "Request validation failed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "401",
                description = "Authentication is required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "403",
                description = "Admin role is required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "404",
                description = "Election, contest, or voting district was not found",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "409",
                description = "Lifecycle transition or uniqueness constraint rejected the request",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
})
public class AdminElectionManagementController {

    private final AdminElectionManagementService adminElectionManagementService;
    private final ElectionLifecycleService electionLifecycleService;

    public AdminElectionManagementController(
            AdminElectionManagementService adminElectionManagementService,
            ElectionLifecycleService electionLifecycleService
    ) {
        this.adminElectionManagementService = adminElectionManagementService;
        this.electionLifecycleService = electionLifecycleService;
    }

    @PostMapping("/voting-districts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a voting district", description = "Creates a district used for voter registration and contest eligibility checks.")
    public VotingDistrictResponse createVotingDistrict(@Valid @RequestBody CreateVotingDistrictRequest request) {
        return adminElectionManagementService.createVotingDistrict(request);
    }

    @PostMapping("/elections")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an election", description = "Creates an election in DRAFT status with registration and voting windows.")
    public ElectionResponse createElection(@Valid @RequestBody CreateElectionRequest request) {
        return adminElectionManagementService.createElection(request);
    }

    @PatchMapping("/elections/{electionId}/status")
    @Operation(summary = "Cancel an active election", description = "Normal lifecycle transitions are automatic. This endpoint permits only an audited emergency cancellation.")
    public ElectionResponse updateElectionStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID electionId,
            @Valid @RequestBody ElectionStatusUpdateRequest request
    ) {
        return adminElectionManagementService.updateElectionStatus(
                electionId,
                request,
                principal.id(),
                principal.email()
        );
    }

    @GetMapping("/elections/{electionId}/lifecycle-events")
    @Operation(summary = "List election lifecycle events", description = "Returns automatic transitions, failed closed transitions, and administrator cancellations in chronological order.")
    public List<ElectionLifecycleEventResponse> listElectionLifecycleEvents(@PathVariable UUID electionId) {
        return electionLifecycleService.listEvents(electionId);
    }

    @PostMapping("/elections/{electionId}/contests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a contest for an election", description = "Creates a contest with South African national, provincial, municipal PR, or municipal ward scope rules.")
    public ContestResponse createContest(
            @PathVariable UUID electionId,
            @Valid @RequestBody CreateContestRequest request
    ) {
        return adminElectionManagementService.createContest(electionId, request);
    }

    @PostMapping("/elections/{electionId}/contests/{contestId}/options")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a ballot option for a contest", description = "Adds a party, independent candidate, explicit blank ballot, or explicit spoilt ballot option to an existing contest.")
    public ContestOptionResponse createContestOption(
            @PathVariable UUID electionId,
            @PathVariable UUID contestId,
            @Valid @RequestBody CreateContestOptionRequest request
    ) {
        return adminElectionManagementService.createContestOption(electionId, contestId, request);
    }

    @PatchMapping("/elections/{electionId}/contests/{contestId}/status")
    @Operation(summary = "Check a contest status transition", description = "Contest transitions are automatic; requests attempting to change the current status are rejected.")
    public ContestResponse updateContestStatus(
            @PathVariable UUID electionId,
            @PathVariable UUID contestId,
            @Valid @RequestBody ContestStatusUpdateRequest request
    ) {
        return adminElectionManagementService.updateContestStatus(electionId, contestId, request);
    }

    @PutMapping("/elections/{electionId}")
    @Operation(summary = "Rename or reschedule a DRAFT election", description = "Replaces the name and windows of an election that has not opened. The registration window must end in the future. Use this to rescue an election whose ballot was not ready when registration was due to open. The change is recorded in the lifecycle history.")
    public ElectionResponse updateElection(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID electionId,
            @Valid @RequestBody UpdateElectionRequest request
    ) {
        return adminElectionManagementService.updateElection(electionId, request, principal.id(), principal.email());
    }

    @PutMapping("/elections/{electionId}/contests/{contestId}")
    @Operation(summary = "Replace a contest", description = "Replaces a DRAFT contest's name, type, display order, and geographic scope while ballot configuration is open.")
    public ContestResponse updateContest(
            @PathVariable UUID electionId,
            @PathVariable UUID contestId,
            @Valid @RequestBody CreateContestRequest request
    ) {
        return adminElectionManagementService.updateContest(electionId, contestId, request);
    }

    @DeleteMapping("/elections/{electionId}/contests/{contestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a contest", description = "Deletes a DRAFT contest and its ballot options while ballot configuration is open.")
    public void deleteContest(@PathVariable UUID electionId, @PathVariable UUID contestId) {
        adminElectionManagementService.deleteContest(electionId, contestId);
    }

    @PutMapping("/elections/{electionId}/contests/{contestId}/options/{optionId}")
    @Operation(summary = "Replace a ballot option", description = "Replaces a ballot option's name, type, and display order while its contest is DRAFT.")
    public ContestOptionResponse updateContestOption(
            @PathVariable UUID electionId,
            @PathVariable UUID contestId,
            @PathVariable UUID optionId,
            @Valid @RequestBody CreateContestOptionRequest request
    ) {
        return adminElectionManagementService.updateContestOption(electionId, contestId, optionId, request);
    }

    @DeleteMapping("/elections/{electionId}/contests/{contestId}/options/{optionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a ballot option", description = "Deletes a ballot option while its contest is DRAFT.")
    public void deleteContestOption(
            @PathVariable UUID electionId,
            @PathVariable UUID contestId,
            @PathVariable UUID optionId
    ) {
        adminElectionManagementService.deleteContestOption(electionId, contestId, optionId);
    }

    @PutMapping("/voting-districts/{votingDistrictId}")
    @Operation(summary = "Replace a voting district", description = "Updates a district. Once voters are registered in it, only the code and name can change, because the province, municipality, and ward decide contest eligibility.")
    public VotingDistrictResponse updateVotingDistrict(
            @PathVariable UUID votingDistrictId,
            @Valid @RequestBody CreateVotingDistrictRequest request
    ) {
        return adminElectionManagementService.updateVotingDistrict(votingDistrictId, request);
    }

    @DeleteMapping("/voting-districts/{votingDistrictId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a voting district", description = "Deletes a district that no voter profile or registration uses.")
    public void deleteVotingDistrict(@PathVariable UUID votingDistrictId) {
        adminElectionManagementService.deleteVotingDistrict(votingDistrictId);
    }
}
