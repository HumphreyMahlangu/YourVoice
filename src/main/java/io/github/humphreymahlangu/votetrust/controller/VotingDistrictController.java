package io.github.humphreymahlangu.votetrust.controller;

import io.github.humphreymahlangu.votetrust.dto.ApiErrorResponse;
import io.github.humphreymahlangu.votetrust.dto.VotingDistrictResponse;
import io.github.humphreymahlangu.votetrust.service.VotingDistrictService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/voting-districts")
@Tag(name = "Voting Districts", description = "Public South African voting district lookup")
@ApiResponses({
        @ApiResponse(
                responseCode = "500",
                description = "Unexpected server error",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
})
public class VotingDistrictController {

    private final VotingDistrictService votingDistrictService;

    public VotingDistrictController(VotingDistrictService votingDistrictService) {
        this.votingDistrictService = votingDistrictService;
    }

    @GetMapping
    @Operation(summary = "List voting districts", description = "Returns voting districts used during election registration and contest eligibility checks.")
    public ResponseEntity<List<VotingDistrictResponse>> listVotingDistricts(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = PageRequestParameters.DEFAULT_PAGE) @Min(0) int page,
            @Parameter(description = "Page size (1-500). The total is returned in the X-Total-Count header.", example = "200")
            @RequestParam(defaultValue = PageRequestParameters.DEFAULT_SIZE) @Min(1) @Max(PageRequestParameters.MAX_SIZE) int size
    ) {
        return PageRequestParameters.toResponse(votingDistrictService.listVotingDistricts(PageRequest.of(page, size)));
    }
}
