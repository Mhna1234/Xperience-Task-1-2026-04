package com.xperience.hero.service;

import com.xperience.hero.dto.RsvpOutcome;
import com.xperience.hero.entity.*;
import com.xperience.hero.exception.BusinessException;
import com.xperience.hero.repository.EventRepository;
import com.xperience.hero.repository.InvitationRepository;
import com.xperience.hero.repository.RsvpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RsvpService {

    private final InvitationRepository invitationRepository;
    private final EventRepository eventRepository;
    private final RsvpRepository rsvpRepository;

    /**
     * Submits or changes an RSVP for the given invite token.
     *
     * Correctness sequence (matches §12 — Concurrency, Scenario 1):
     *  1. Resolve token → invitation (no event entity loaded yet)
     *  2. Acquire pessimistic WRITE lock on the event row (SELECT ... FOR UPDATE)
     *  3. Check event state AFTER the lock is held (I5, I10)
     *  4. Derive the outcome status from the intent (I6, I7 — never trust client-supplied status)
     *  5. Detect whether a confirmed seat was freed
     *  6. Write RSVP; flush to ensure the write is visible to the promotion query
     *  7. If a seat was freed: promote the next waitlisted RSVP in FIFO order,
     *     inside the same transaction (I8, prevents double-promotion — R-C2)
     */
    @Transactional
    public RsvpOutcome submitRsvp(String token, RsvpIntent intent) {
        if (intent == null) {
            throw BusinessException.validationError("intent is required (YES, NO, or MAYBE)");
        }

        // Step 1: Resolve token to invitation.
        Invitation invitation = invitationRepository.findByInviteToken(token)
                .orElseThrow(BusinessException::invitationNotFound);

        // Step 2: Acquire pessimistic row-level lock on the event.
        // Use invitation.getEventId() (raw Long column) to avoid loading a stale Event entity
        // into the JPA first-level cache before the locked query runs (R-D2).
        Event event = eventRepository.findByIdWithPessimisticLock(invitation.getEventId())
                .orElseThrow(BusinessException::eventNotFound);

        // Step 3: All state checks happen AFTER the lock is held.
        // A concurrent close/cancel cannot commit between these checks and the RSVP write.
        if (event.getStatus() == EventStatus.CANCELED) {
            throw BusinessException.eventCanceled();
        }
        if (event.getStatus() == EventStatus.CLOSED) {
            throw BusinessException.eventClosed();
        }
        if (!Instant.now().isBefore(event.getStartTime())) {
            throw BusinessException.eventLocked();
        }

        // Step 4: Load existing RSVP (always present — created when invitation was created).
        Rsvp rsvp = rsvpRepository.findByInvitation_Id(invitation.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "RSVP record missing for invitation " + invitation.getId()));

        RsvpStatus previousStatus = rsvp.getStatus();

        // Step 5: Derive the outcome status from the client's intent.
        // The client sends an intent (YES/NO/MAYBE); we derive the stored status (I6, I7).
        RsvpStatus newStatus = deriveOutcomeStatus(intent, previousStatus, event);

        // Step 6: Detect whether a confirmed seat was just freed.
        boolean seatFreed = previousStatus == RsvpStatus.YES_CONFIRMED
                && (newStatus == RsvpStatus.NO || newStatus == RsvpStatus.MAYBE);

        // Step 7: Write and flush so the promotion query sees the updated status.
        rsvp.setStatus(newStatus);
        // Only update respondedAt when the status genuinely changes.
        // For a WAITLISTED invitee re-submitting YES (no-op), preserving respondedAt
        // maintains their FIFO queue position (invariant I8).
        if (newStatus != previousStatus) {
            rsvp.setRespondedAt(Instant.now());
        } else if (rsvp.getRespondedAt() == null) {
            rsvp.setRespondedAt(Instant.now());
        }
        rsvpRepository.saveAndFlush(rsvp);

        // Step 8: Promote the next waitlisted attendee if a confirmed seat was freed.
        // Synchronous and inside this same transaction — prevents double-promotion (R-C2).
        if (seatFreed) {
            rsvpRepository
                    .findFirstByInvitation_EventIdAndStatusOrderByRespondedAtAsc(
                            event.getId(), RsvpStatus.YES_WAITLISTED)
                    .ifPresent(waitlisted -> {
                        waitlisted.setStatus(RsvpStatus.YES_CONFIRMED);
                        waitlisted.setRespondedAt(Instant.now());
                        rsvpRepository.save(waitlisted);
                    });
        }

        return new RsvpOutcome(newStatus.name(), rsvp.getRespondedAt());    }

    /**
     * Derives the RSVP outcome status from the invitee's intent and current event state.
     *
     * Rules:
     * - NO  → always NO (no capacity check)
     * - MAYBE → always MAYBE (does not consume a seat, I9)
     * - YES + already CONFIRMED → stay CONFIRMED (idempotent, no re-check needed)
     * - YES + no capacity limit  → CONFIRMED
     * - YES + capacity available → CONFIRMED
     * - YES + capacity full      → WAITLISTED (I7)
     */
    private RsvpStatus deriveOutcomeStatus(RsvpIntent intent, RsvpStatus currentStatus, Event event) {
        return switch (intent) {
            case NO -> RsvpStatus.NO;
            case MAYBE -> RsvpStatus.MAYBE;
            case YES -> {
                if (currentStatus == RsvpStatus.YES_CONFIRMED) {
                    yield RsvpStatus.YES_CONFIRMED; // idempotent — seat already held
                }
                if (event.getMaxCapacity() == null) {
                    yield RsvpStatus.YES_CONFIRMED; // unlimited capacity
                }
                // Count confirmed seats inside the locked transaction for an accurate snapshot.
                long confirmed = rsvpRepository.countConfirmedByEventId(event.getId());
                yield confirmed < event.getMaxCapacity()
                        ? RsvpStatus.YES_CONFIRMED
                        : RsvpStatus.YES_WAITLISTED;
            }
        };
    }
}
