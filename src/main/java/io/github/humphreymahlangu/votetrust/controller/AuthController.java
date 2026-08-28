package io.github.humphreymahlangu.votetrust.controller;

import io.github.humphreymahlangu.votetrust.dto.ApiErrorResponse;
import io.github.humphreymahlangu.votetrust.dto.AuthResponse;
import io.github.humphreymahlangu.votetrust.dto.LoginRequest;
import io.github.humphreymahlangu.votetrust.dto.RegisterRequest;
import io.github.humphreymahlangu.votetrust.dto.UserAccountResponse;
import io.github.humphreymahlangu.votetrust.security.SecurityAuditMetadata;
import io.github.humphreymahlangu.votetrust.security.UserPrincipal;
import io.github.humphreymahlangu.votetrust.service.AuthService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Account registration, login, and current authenticated user APIs")
@ApiResponses({
        @ApiResponse(
                responseCode = "400",
                description = "Request validation failed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "401",
                description = "Authentication failed or is required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "409",
                description = "Account already exists",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "429",
                description = "Sensitive endpoint rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
})
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a platform account",
            description = "Creates a voter account and protected voter profile, then returns a short-lived JWT."
    )
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        return authService.register(request, SecurityAuditMetadata.from(servletRequest));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Authenticate and issue a JWT",
            description = "Authenticates an enabled account and returns a short-lived bearer token."
    )
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return authService.login(request, SecurityAuditMetadata.from(servletRequest));
    }

    @GetMapping("/me")
    @Operation(
            summary = "Return the authenticated account",
            description = "Returns the account represented by the bearer token.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public UserAccountResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return new UserAccountResponse(principal.id(), principal.email(), principal.role(), principal.isEnabled());
    }
}
