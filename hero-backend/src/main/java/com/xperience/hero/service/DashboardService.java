package com.xperience.hero.service;

import com.xperience.hero.dto.DashboardResponse;
import com.xperience.hero.entity.*;
import com.xperience.hero.exception.BusinessException;
import com.xperience.hero.repository.EventRepository;
import com.xperience.hero.repository.InvitationRepository;
import com.xperience.hero.repository.RsvpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EventRepository eventRepository;
    private final InvitationRepository invitationRepository;
    private final RsvpRepository rsvpRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(String hostId, Long eventId) {
        if (hostId == null || hostId.isBlank()) {
            throw BusinessException.unauthorized();
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(BusinessException::eventNotFound);
        if (!event.getHostId().equals(hostId.strip())) {
            throw BusinessException.forbidden();
        }

        List<Invitation> invitations = invitationRepository.findByEventId(eventId);
        List<Long> invitationIds = invitations.stream().map(Invitation::getId).toList();

        // Load all RSVPs for these invitations in one query, keyed by invitationId.
        Map<Long, Rsvp> rsvpByInvitationId = rsvpRepository.findByInvitation_IdIn(invitationIds)
                .stream()
                .collect(Collectors.toMap(r -> r.getInvitation().getId(), r -> r));

        Map<RsvpStatus, Long> statusCounts = rsvpByInvitationId.values().stream()
                .collect(Collectors.groupingBy(Rsvp::getStatus, Collectors.counting()));

        List<DashboardResponse.AttendeeDetail> attendees = invitations.stream()
                .map(inv -> {
                    Rsvp rsvp = rsvpByInvitationId.get(inv.getId());
                    RsvpStatus status = rsvp != null ? rsvp.getStatus() : RsvpStatus.PENDING;
                    return new DashboardResponse.AttendeeDetail(
                            inv.getId(), inv.getEmail(), status.name(),
                            rsvp != null ? rsvp.getRespondedAt() : null);
                })
                .toList();

        DashboardResponse.Counts counts = new DashboardResponse.Counts(
                statusCounts.getOrDefault(RsvpStatus.YES_CONFIRMED, 0L),
                statusCounts.getOrDefault(RsvpStatus.YES_WAITLISTED, 0L),
                statusCounts.getOrDefault(RsvpStatus.NO, 0L),
                statusCounts.getOrDefault(RsvpStatus.MAYBE, 0L),
                statusCounts.getOrDefault(RsvpStatus.PENDING, 0L),
                (long) invitations.size());

        return new DashboardResponse(
                event.getId(), event.getTitle(), event.getEffectiveStatus().name(),
                event.getStartTime(), counts, attendees);
    }
}
