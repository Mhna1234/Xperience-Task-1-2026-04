package com.xperience.hero.repository;

import com.xperience.hero.entity.Rsvp;
import com.xperience.hero.entity.RsvpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RsvpRepository extends JpaRepository<Rsvp, Long> {

    Optional<Rsvp> findByInvitation_Id(Long invitationId);

    List<Rsvp> findByInvitation_IdIn(List<Long> invitationIds);

    /**
     * Counts confirmed seats for a given event.
     * Runs inside the locked transaction in RsvpService to ensure the count is accurate
     * at the moment the lock is held (invariant I6).
     */
    @Query("SELECT COUNT(r) FROM Rsvp r WHERE r.invitation.eventId = :eventId AND r.status = com.xperience.hero.entity.RsvpStatus.YES_CONFIRMED")
    long countConfirmedByEventId(@Param("eventId") Long eventId);

    /**
     * Returns the next waitlisted RSVP for an event in FIFO order by respondedAt (I8).
     * Used by the promotion step inside the seat-freeing transaction.
     */
    Optional<Rsvp> findFirstByInvitation_EventIdAndStatusOrderByRespondedAtAsc(
            Long eventId, RsvpStatus status);
}
