package com.xperience.hero.dto;

import com.xperience.hero.entity.Event;

import java.time.Instant;

public record EventResponse(
        Long id,
        String hostId,
        String title,
        String description,
        Instant startTime,
        String location,
        Integer maxCapacity,
        String status,
        Instant createdAt
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getHostId(),
                event.getTitle(),
                event.getDescription(),
                event.getStartTime(),
                event.getLocation(),
                event.getMaxCapacity(),
                event.getEffectiveStatus().name(),
                event.getCreatedAt()
        );
    }
}
