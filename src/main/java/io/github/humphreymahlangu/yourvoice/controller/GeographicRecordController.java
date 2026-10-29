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

import io.github.humphreymahlangu.yourvoice.dto.CreateGeographyRequest;
import io.github.humphreymahlangu.yourvoice.dto.GeographicProvinceResponse;
import io.github.humphreymahlangu.yourvoice.dto.GeographicMunicipalityResponse;
import io.github.humphreymahlangu.yourvoice.service.GeographicRecordService;
import java.util.List;

@RestController
@Tag(name = "Geographic hierarchy", description = "Stable fictional/admin-configured province and municipality identifiers; not an official roll")
@ApiResponses({@ApiResponse(responseCode = "200", description = "Request completed"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Sign in required"),
        @ApiResponse(responseCode = "403", description = "Role not allowed"),
        @ApiResponse(responseCode = "409", description = "Conflicts with an existing decision"),
        @ApiResponse(responseCode = "422", description = "Verification or location requirements not met"),
        @ApiResponse(responseCode = "503", description = "Fictional verification source unavailable")})
public class GeographicRecordController {
    private final GeographicRecordService geography;
    public GeographicRecordController(GeographicRecordService geography) { this.geography = geography; }
    @GetMapping("/api/v1/geography/provinces")
    @Operation(summary = "List configured provinces", description = "Stable province identifiers; no municipality or ward is required for national-only identity verification.")
    public List<GeographicProvinceResponse> provinces() { return geography.provinces(); }
    @GetMapping("/api/v1/geography/municipalities")
    @Operation(summary = "List configured municipalities", description = "Includes parent province identifiers to validate and filter correction and verification geography.")
    public List<GeographicMunicipalityResponse> municipalities() { return geography.municipalities(); }
    @PostMapping("/api/v1/admin/geography/provinces")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a configured province", description = "Admin-only idempotent creation. Does not grant any voter verification.", security = @SecurityRequirement(name = "bearerAuth"))
    public GeographicProvinceResponse createProvince(@Valid @RequestBody CreateGeographyRequest request) { return geography.createProvince(request); }
    @PostMapping("/api/v1/admin/geography/municipalities")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a configured municipality", description = "Admin-only idempotent creation with a required valid parent province.", security = @SecurityRequirement(name = "bearerAuth"))
    public GeographicMunicipalityResponse createMunicipality(@Valid @RequestBody CreateGeographyRequest request) { return geography.createMunicipality(request); }
}
