package com.xperience.hero.controller;

import com.xperience.hero.dto.DashboardResponse;
import com.xperience.hero.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/{eventId}/dashboard")
    @Operation(summary = "Get event dashboard",
            description = "Returns event status, RSVP counts (total/confirmed/waitlisted/no/maybe/pending), and full attendee list.")
    public DashboardResponse getDashboard(
            @Parameter(description = "Host identifier (required)", required = true)
            @RequestHeader(value = "X-Host-Id", required = false) String hostId,
            @PathVariable Long eventId) {
        return dashboardService.getDashboard(hostId, eventId);
    }
}
