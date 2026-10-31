package com.cricauction.modules.dashboard.controller;

import com.cricauction.common.api.ApiResponse;
import com.cricauction.modules.dashboard.dto.response.CaptainDashboardResponse;
import com.cricauction.modules.dashboard.dto.response.OrganizerDashboardResponse;
import com.cricauction.modules.dashboard.dto.response.PlayerDashboardResponse;
import com.cricauction.modules.dashboard.service.DashboardService;
import com.cricauction.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing Module 3: Role-Specific Dashboard feeds.
 * APIs 15, 16, and 17.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Module 3: Role-Specific Dashboards", description = "Aggregated home screens tailored to the active role (Organizer, Captain, Player) with metrics, live auction alerts, and quick actions")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * API 15: Organizer Dashboard Feed
     */
    @Operation(summary = "Organizer Dashboard Feed (API 15)", description = "Returns active drafts, tournament metrics, player counts, upcoming auction timers, and recent activity")
    @GetMapping("/organizer")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<ApiResponse<OrganizerDashboardResponse>> getOrganizerDashboard(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        OrganizerDashboardResponse response = dashboardService.getOrganizerDashboard(currentUser);
        return ResponseEntity.ok(ApiResponse.success("Organizer dashboard loaded successfully", response));
    }

    /**
     * API 16: Team Captain Dashboard Feed
     */
    @Operation(summary = "Team Captain Dashboard Feed (API 16)", description = "Displays team purse budget, squad fill count, upcoming auction countdown, and match schedule")
    @GetMapping("/captain")
    @PreAuthorize("hasAnyRole('CAPTAIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<CaptainDashboardResponse>> getCaptainDashboard(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        CaptainDashboardResponse response = dashboardService.getCaptainDashboard(currentUser);
        return ResponseEntity.ok(ApiResponse.success("Captain dashboard loaded successfully", response));
    }

    /**
     * API 17: Player Dashboard Feed
     */
    @Operation(summary = "Player Dashboard Feed (API 17)", description = "Shows active tournament registration status, target squad placement, and match schedule")
    @GetMapping("/player")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PlayerDashboardResponse>> getPlayerDashboard(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        PlayerDashboardResponse response = dashboardService.getPlayerDashboard(currentUser);
        return ResponseEntity.ok(ApiResponse.success("Player dashboard loaded successfully", response));
    }
}
