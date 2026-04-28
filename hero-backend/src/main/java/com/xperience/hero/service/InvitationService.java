package com.xperience.hero.service;

import com.xperience.hero.dto.InvitationViewResponse;
import com.xperience.hero.dto.InviteRequest;
import com.xperience.hero.dto.InviteResponse;
import com.xperience.hero.entity.*;
import com.xperience.hero.exception.BusinessException;
import com.xperience.hero.repository.EventRepository;
import com.xperience.hero.repository.InvitationRepository;
import com.xperience.hero.repository.RsvpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final EventRepository eventRepository;
    private final InvitationRepository invitationRepository;
    private final RsvpRepository rsvpRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public InviteResponse invitePeople(String hostId, Long eventId, InviteRequest request) {
        if (hostId == null || hostId.isBlank()) {
            throw BusinessException.unauthorized();
        }
        if (request.emails() == null || request.emails().isEmpty()) {
            throw BusinessException.validationError("At least one email address is required");
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(BusinessException::eventNotFound);
        if (!event.getHostId().equals(hostId.strip())) {
            throw BusinessException.forbidden();
        }

        List<InviteResponse.InvitationDetail> created = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();

        for (String raw : request.emails()) {
            String email = raw == null ? null : raw.strip().toLowerCase();
            if (email == null || email.isBlank()) continue;

            if (invitationRepository.existsByEventIdAndEmail(eventId, email)) {
                duplicates.add(email);
                continue;
            }

            Invitation invitation = new Invitation();
            invitation.setEventId(eventId);
            invitation.setEmail(email);
            invitation.setInviteToken(generateToken());
            invitationRepository.save(invitation);

            // Create the initial PENDING RSVP record alongside the invitation.
            // This ensures every invitation always has exactly one RSVP record.
            Rsvp rsvp = new Rsvp();
            rsvp.setInvitation(invitation);
            rsvp.setStatus(RsvpStatus.PENDING);
            rsvpRepository.save(rsvp);

            created.add(new InviteResponse.InvitationDetail(
                    invitation.getId(), invitation.getEmail(), invitation.getInviteToken()));
        }

        return new InviteResponse(created, duplicates);
    }

    @Transactional(readOnly = true)
    public InvitationViewResponse getInvitationView(String token) {
        Invitation invitation = invitationRepository.findByInviteToken(token)
                .orElseThrow(BusinessException::invitationNotFound);

        Event event = eventRepository.findById(invitation.getEventId())
                .orElseThrow(BusinessException::eventNotFound);

        Rsvp rsvp = rsvpRepository.findByInvitation_Id(invitation.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "RSVP record missing for invitation " + invitation.getId()));

        return new InvitationViewResponse(
                invitation.getId(),
                invitation.getEmail(),
                new InvitationViewResponse.EventSummary(
                        event.getId(), event.getTitle(), event.getDescription(),
                        event.getStartTime(), event.getLocation(), event.getEffectiveStatus().name()),
                new InvitationViewResponse.RsvpInfo(
                        rsvp.getStatus().name(), rsvp.getRespondedAt()));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
