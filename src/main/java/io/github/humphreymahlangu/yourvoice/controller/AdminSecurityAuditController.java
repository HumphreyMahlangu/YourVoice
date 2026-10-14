package io.github.humphreymahlangu.votetrust.controller;

import io.github.humphreymahlangu.votetrust.dto.ApiErrorResponse;
import io.github.humphreymahlangu.votetrust.dto.SecurityAuditEventResponse;
import io.github.humphreymahlangu.votetrust.service.SecurityAuditService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/security-audit-events")
@Tag(name = "Admin Security Audit", description = "Admin-only security audit event inspection")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(
                responseCode = "401",
                description = "Authentication is required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "403",
                description = "Admin role is required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
})
public class AdminSecurityAuditController {

    private final SecurityAuditService securityAuditService;

    public AdminSecurityAuditController(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    @GetMapping
    @Operation(
            summary = "Return recent security audit events",
            description = "Lists recent authentication, bootstrap, and rate-limit audit events without voting credential or ballot identifiers.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public List<SecurityAuditEventResponse> listRecentSecurityAuditEvents(
            @Parameter(description = "Maximum number of events to return. Values above 100 are capped.", example = "50")
            @RequestParam(defaultValue = "50") int limit
    ) {
        return securityAuditService.listLatest(limit);
    }
}
