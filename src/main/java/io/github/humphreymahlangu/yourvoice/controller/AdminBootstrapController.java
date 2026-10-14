package io.github.humphreymahlangu.votetrust.controller;

import io.github.humphreymahlangu.votetrust.dto.AdminBootstrapRequest;
import io.github.humphreymahlangu.votetrust.dto.ApiErrorResponse;
import io.github.humphreymahlangu.votetrust.dto.AuthResponse;
import io.github.humphreymahlangu.votetrust.security.SecurityAuditMetadata;
import io.github.humphreymahlangu.votetrust.service.AdminBootstrapService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/bootstrap")
@Tag(name = "Admin Bootstrap", description = "Disabled-by-default first-admin bootstrap API")
@ApiResponses({
        @ApiResponse(
                responseCode = "400",
                description = "Request validation failed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "401",
                description = "Bootstrap token is missing or invalid",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "403",
                description = "Admin bootstrap is disabled",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "409",
                description = "An admin account or email already exists",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "429",
                description = "Bootstrap rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
})
public class AdminBootstrapController {

    public static final String BOOTSTRAP_TOKEN_HEADER = "X-VoteTrust-Bootstrap-Token";

    private final AdminBootstrapService adminBootstrapService;

    public AdminBootstrapController(AdminBootstrapService adminBootstrapService) {
        this.adminBootstrapService = adminBootstrapService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Bootstrap the first admin account",
            description = "Creates the first admin only when bootstrap is enabled, the one-time token matches, and no admin exists."
    )
    public AuthResponse bootstrapFirstAdmin(
            @RequestHeader(name = BOOTSTRAP_TOKEN_HEADER, required = false) String bootstrapToken,
            @Valid @RequestBody AdminBootstrapRequest request,
            HttpServletRequest servletRequest
    ) {
        return adminBootstrapService.bootstrapFirstAdmin(
                request,
                bootstrapToken,
                SecurityAuditMetadata.from(servletRequest)
        );
    }
}
