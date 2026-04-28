package com.xperience.hero.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "invitations", schema = "hero")
@Getter
@Setter
@NoArgsConstructor
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Raw event_id column value.
     * Stored as a plain Long so that RsvpService can read the event ID without triggering
     * a lazy load of the Event entity before the pessimistic lock is acquired (avoids the
     * stale-cache problem described in R-D2).
     */
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    /**
     * Navigation association. insertable/updatable = false because the column is already
     * managed by the {@code eventId} field above.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", insertable = false, updatable = false)
    private Event event;

    @Column(nullable = false)
    private String email;

    @Column(name = "invite_token", nullable = false, unique = true)
    private String inviteToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
