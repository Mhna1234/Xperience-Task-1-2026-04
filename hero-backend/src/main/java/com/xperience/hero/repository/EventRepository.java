package com.xperience.hero.repository;

import com.xperience.hero.entity.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Loads the event row with a PostgreSQL {@code SELECT ... FOR UPDATE} lock.
     * All RSVP writes must acquire this lock first to serialise seat allocation
     * and status checks (see §12 — Concurrency).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdWithPessimisticLock(@Param("id") Long id);
}
