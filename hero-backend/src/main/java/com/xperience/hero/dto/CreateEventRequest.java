package com.xperience.hero.dto;

import java.time.Instant;

public record CreateEventRequest(
        String title,
        String description,
        Instant startTime,
        String location,
        Integer maxCapacity
) {}
