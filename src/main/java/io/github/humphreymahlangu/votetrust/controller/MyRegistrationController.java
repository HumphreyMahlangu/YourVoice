package io.github.humphreymahlangu.votetrust.controller;

import io.github.humphreymahlangu.votetrust.dto.ApiErrorResponse;
import io.github.humphreymahlangu.votetrust.dto.ElectionRegistrationResponse;
import io.github.humphreymahlangu.votetrust.security.UserPrincipal;
import io.github.humphreymahlangu.votetrust.service.VoterRegistrationService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/registrations")
@Tag(name = "My Registrations", description = "Authenticated voter registration lookup")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(
                responseCode = "401",
                description = "Authentication is required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
})
public class MyRegistrationController {

    private final VoterRegistrationService voterRegistrationService;

    public MyRegistrationController(VoterRegistrationService voterRegistrationService) {
        this.voterRegistrationService = voterRegistrationService;
    }

    @GetMapping
    @Operation(
            summary = "List the authenticated voter's election registrations",
            description = "Returns election registrations linked to the bearer-token account.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<List<ElectionRegistrationResponse>> listMyRegistrations(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = PageRequestParameters.DEFAULT_PAGE) @Min(0) int page,
            @Parameter(description = "Page size (1-500). The total is returned in the X-Total-Count header.", example = "200")
            @RequestParam(defaultValue = PageRequestParameters.DEFAULT_SIZE) @Min(1) @Max(PageRequestParameters.MAX_SIZE) int size
    ) {
        return PageRequestParameters.toResponse(
                voterRegistrationService.listMyRegistrations(principal.id(), PageRequest.of(page, size)));
    }
}
