package com.xperience.hero.controller;

import com.xperience.hero.dto.*;
import com.xperience.hero.service.InvitationService;
import com.xperience.hero.service.RsvpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;
    private final RsvpService rsvpService;

    @Tag(name = "Invitations")
    @Operation(summary = "Send invitations",
            description = "Host sends a list of emails. Returns created invitation tokens and any duplicate emails skipped.")
    @PostMapping("/api/events/{eventId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse invitePeople(
            @Parameter(description = "Host identifier (required)", required = true)
            @RequestHeader(value = "X-Host-Id", required = false) String hostId,
            @PathVariable Long eventId,
            @RequestBody InviteRequest request) {
        return invitationService.invitePeople(hostId, eventId, request);
    }

    @Tag(name = "Invitations")
    @Operation(summary = "Get invitation view",
            description = "Invitee fetches event details and their current RSVP status using their unique token.")
    @GetMapping("/api/invitations/{token}")
    public InvitationViewResponse getInvitation(
            @Parameter(description = "Invite token from the invitation link", required = true)
            @PathVariable String token) {
        return invitationService.getInvitationView(token);
    }

    @Tag(name = "RSVP")
    @Operation(summary = "Submit RSVP",
            description = "Invitee submits YES / NO / MAYBE intent. Backend derives the outcome (CONFIRMED / WAITLISTED / NO / MAYBE).")
    @PutMapping("/api/invitations/{token}/rsvp")
    public RsvpOutcome submitRsvp(
            @Parameter(description = "Invite token from the invitation link", required = true)
            @PathVariable String token,
            @RequestBody RsvpRequest request) {
        return rsvpService.submitRsvp(token, request.intent());
    }
}
