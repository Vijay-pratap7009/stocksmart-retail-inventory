package com.stocksmart.controller;

import com.stocksmart.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verification-only endpoints proving authentication + RBAC work.
 * NOT business features — they stay minimal by design.
 */
@RestController
@RequestMapping("/api/v1/test")
@Tag(name = "Security verification", description = "Day 1 RBAC probe endpoints")
@SecurityRequirement(name = "bearerAuth")
public class TestController {

    @GetMapping("/protected")
    @Operation(summary = "Requires any authenticated user")
    public ResponseEntity<ApiResponse> protectedEndpoint(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Authenticated access granted",
                java.util.Map.of("user", authentication.getName())));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole(T(com.stocksmart.security.authorization.Roles).ADMIN)")
    @Operation(summary = "Requires ADMIN role")
    public ResponseEntity<ApiResponse> admin(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Admin access granted",
                java.util.Map.of("user", authentication.getName())));
    }

    @GetMapping("/management")
    @PreAuthorize("hasRole(T(com.stocksmart.security.authorization.Roles).MANAGEMENT)")
    @Operation(summary = "Requires MANAGEMENT role")
    public ResponseEntity<ApiResponse> management(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Management access granted",
                java.util.Map.of("user", authentication.getName())));
    }
}
