package com.example.eventledger.controller;

import com.example.eventledger.dto.EventRequest;
import com.example.eventledger.dto.EventResponse;
import com.example.eventledger.dto.EventSubmissionResult;
import com.example.eventledger.service.EventService;
import com.example.eventledger.spec.EventApi;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller implementation for Event-related endpoints.
 * Implements the {@link EventApi} interface to ensure API contract consistency.
 * All endpoint mappings and OpenAPI documentation are defined in the interface.
 */
@RestController
public class EventController implements EventApi {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    public ResponseEntity<EventResponse> submitEvent(EventRequest request) {
        EventSubmissionResult result = eventService.submitEvent(request);
        
        if (result.isDuplicate()) {
            return ResponseEntity.ok(result.getEvent());
        } else {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.getEvent());
        }
    }

    @Override
    public ResponseEntity<EventResponse> getEventById(String eventId) {
        EventResponse event = eventService.getEvent(eventId);
        return ResponseEntity.ok(event);
    }

    @Override
    public ResponseEntity<?> getEventsByAccount(String accountId, Integer page, Integer size) {
        if (page != null) {
            // Paginated response
            Page<EventResponse> events = eventService.getEventsByAccountPaginated(accountId, page, size);
            return ResponseEntity.ok(events);
        } else {
            // Non-paginated response (all events)
            List<EventResponse> events = eventService.getEventsByAccount(accountId);
            return ResponseEntity.ok(events);
        }
    }
}
