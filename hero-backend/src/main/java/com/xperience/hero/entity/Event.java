package com.xperience.hero.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "events", schema = "hero")
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "host_id", nullable = false)
    private String hostId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(length = 500)
    private String location;

    @Column(name = "max_capacity")
    private Integer maxCapacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Returns the effective event status visible to API consumers.
     * When the persisted status is OPEN but {@code currentTime >= startTime}, the effective
     * status is LOCKED. LOCKED is never written to the database — it is derived at read time.
     */
    public EventStatus getEffectiveStatus() {
        if (status == EventStatus.OPEN && !Instant.now().isBefore(startTime)) {
            return EventStatus.LOCKED;
        }
        return status;
    }
}
