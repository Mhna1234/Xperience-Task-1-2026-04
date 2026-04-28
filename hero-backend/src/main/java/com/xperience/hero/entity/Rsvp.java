package com.xperience.hero.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "rsvps", schema = "hero")
@Getter
@Setter
@NoArgsConstructor
public class Rsvp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitation_id", nullable = false)
    private Invitation invitation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RsvpStatus status = RsvpStatus.PENDING;

    @Column(name = "responded_at")
    private Instant respondedAt;

    /** Optimistic locking version. Extra safeguard on top of the event-row pessimistic lock. */
    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
