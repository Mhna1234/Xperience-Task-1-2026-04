package com.xperience.hero.service;

import com.xperience.hero.dto.CreateEventRequest;
import com.xperience.hero.dto.EventResponse;
import com.xperience.hero.entity.Event;
import com.xperience.hero.entity.EventStatus;
import com.xperience.hero.exception.BusinessException;
import com.xperience.hero.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    @Transactional
    public EventResponse createEvent(String hostId, CreateEventRequest req) {
        requireHostId(hostId);
        if (req.title() == null || req.title().isBlank()) {
            throw BusinessException.validationError("title is required");
        }
        if (req.startTime() == null) {
            throw BusinessException.validationError("startTime is required");
        }

        Event event = new Event();
        event.setHostId(hostId.strip());
        event.setTitle(req.title().strip());
        event.setDescription(req.description());
        event.setStartTime(req.startTime());
        event.setLocation(req.location());
        event.setMaxCapacity(req.maxCapacity());
        event.setStatus(EventStatus.OPEN);

        return EventResponse.from(eventRepository.save(event));
    }

    @Transactional
    public EventResponse closeEvent(String hostId, Long eventId) {
        requireHostId(hostId);
        Event event = getOwnedEvent(hostId, eventId);
        if (event.getStatus() == EventStatus.CANCELED) {
            throw BusinessException.eventCanceled();
        }
        event.setStatus(EventStatus.CLOSED);
        return EventResponse.from(eventRepository.save(event));
    }

    @Transactional
    public EventResponse cancelEvent(String hostId, Long eventId) {
        requireHostId(hostId);
        Event event = getOwnedEvent(hostId, eventId);
        event.setStatus(EventStatus.CANCELED);
        return EventResponse.from(eventRepository.save(event));
    }

    private Event getOwnedEvent(String hostId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(BusinessException::eventNotFound);
        if (!event.getHostId().equals(hostId.strip())) {
            throw BusinessException.forbidden();
        }
        return event;
    }

    private void requireHostId(String hostId) {
        if (hostId == null || hostId.isBlank()) {
            throw BusinessException.unauthorized();
        }
    }
}
