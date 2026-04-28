package com.xperience.hero.dto;

import java.time.Instant;

public record RsvpOutcome(String rsvpStatus, Instant respondedAt) {}
