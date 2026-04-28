package com.xperience.hero.controller;

import com.xperience.hero.dto.CreateEventRequest;
import com.xperience.hero.dto.EventResponse;
import com.xperience.hero.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Events")
public class EventController {

    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create event", description = "Creates a new OPEN event. The caller becomes the host.")
    public EventResponse createEvent(
            @Parameter(description = "Host identifier (required)", required = true)
            @RequestHeader(value = "X-Host-Id", required = false) String hostId,
            @RequestBody CreateEventRequest request) {
        return eventService.createEvent(hostId, request);
    }

    @PostMapping("/{eventId}/close")
    @Operation(summary = "Close event", description = "Moves the event to CLOSED. No new RSVPs accepted.")
    public EventResponse closeEvent(
            @Parameter(description = "Host identifier (required)", required = true)
            @RequestHeader(value = "X-Host-Id", required = false) String hostId,
            @PathVariable Long eventId) {
        return eventService.closeEvent(hostId, eventId);
    }

    @PostMapping("/{eventId}/cancel")
    @Operation(summary = "Cancel event", description = "Moves the event to CANCELED. Irreversible.")
    public EventResponse cancelEvent(
            @Parameter(description = "Host identifier (required)", required = true)
            @RequestHeader(value = "X-Host-Id", required = false) String hostId,
            @PathVariable Long eventId) {
        return eventService.cancelEvent(hostId, eventId);
    }
}
