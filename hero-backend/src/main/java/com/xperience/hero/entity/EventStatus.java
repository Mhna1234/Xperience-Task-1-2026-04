package com.xperience.hero.entity;

public enum EventStatus {
    OPEN, CLOSED, CANCELED,
    /** Derived at read time when {@code currentTime >= startTime}. Never persisted. */
    LOCKED
}
