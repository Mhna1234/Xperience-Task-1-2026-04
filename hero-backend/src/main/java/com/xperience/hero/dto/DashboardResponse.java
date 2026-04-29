package com.xperience.hero.dto;

import java.time.Instant;
import java.util.List;

public record DashboardResponse(
        Long eventId,
        String title,
        String status,
        Instant startTime,
        Counts counts,
        List<AttendeeDetail> attendees
) {
    public record Counts(
            long confirmed,
            long waitlisted,
            long no,
            long maybe,
            long pending,
            long total
    ) {}

    public record AttendeeDetail(
            Long invitationId,
            String email,
            String inviteToken,
            String rsvpStatus,
            Instant respondedAt
    ) {}
}
