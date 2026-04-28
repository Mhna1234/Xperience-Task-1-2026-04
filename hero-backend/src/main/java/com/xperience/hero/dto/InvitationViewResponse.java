package com.xperience.hero.dto;

import java.time.Instant;

public record InvitationViewResponse(
        Long invitationId,
        String email,
        EventSummary event,
        RsvpInfo rsvp
) {
    public record EventSummary(
            Long id,
            String title,
            String description,
            Instant startTime,
            String location,
            String status
    ) {}

    public record RsvpInfo(String status, Instant respondedAt) {}
}
