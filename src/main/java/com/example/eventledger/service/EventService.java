package com.example.eventledger.service;

import com.example.eventledger.dto.EventRequest;
import com.example.eventledger.dto.EventResponse;
import com.example.eventledger.dto.EventSubmissionResult;
import com.example.eventledger.entity.EventType;
import com.example.eventledger.entity.LedgerEvent;
import com.example.eventledger.exception.CurrencyMismatchException;
import com.example.eventledger.exception.EventNotFoundException;
import com.example.eventledger.repository.LedgerEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EventService {

    private final LedgerEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public EventService(LedgerEventRepository eventRepository, ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Submit a new event with idempotency support.
     * If the event already exists, returns the existing event with duplicate=true.
     * Handles race conditions by catching DataIntegrityViolationException.
     */
    @Transactional
    public EventSubmissionResult submitEvent(EventRequest request) {
        // Check if event already exists
        Optional<LedgerEvent> existingEvent = eventRepository.findByEventId(request.getEventId());
        if (existingEvent.isPresent()) {
            return new EventSubmissionResult(toEventResponse(existingEvent.get()), true);
        }

        // Check for currency mismatch
        if (eventRepository.existsByAccountIdAndCurrencyNot(request.getAccountId(), request.getCurrency())) {
            List<String> currencies = eventRepository.findDistinctCurrenciesByAccountId(request.getAccountId());
            String existingCurrency = currencies.isEmpty() ? "UNKNOWN" : currencies.get(0);
            throw new CurrencyMismatchException(request.getAccountId(), existingCurrency, request.getCurrency());
        }

        // Create new event
        LedgerEvent event = new LedgerEvent(
                request.getEventId(),
                request.getAccountId(),
                request.getType(),
                request.getAmount(),
                request.getCurrency(),
                request.getEventTimestamp(),
                serializeMetadata(request.getMetadata())
        );
        event.setReceivedAt(Instant.now());

        try {
            LedgerEvent savedEvent = eventRepository.save(event);
            return new EventSubmissionResult(toEventResponse(savedEvent), false);
        } catch (DataIntegrityViolationException ex) {
            // Race condition: another thread inserted the event
            LedgerEvent existing = eventRepository.findByEventId(request.getEventId())
                    .orElseThrow(() -> new RuntimeException("Unexpected state: event not found after constraint violation"));
            return new EventSubmissionResult(toEventResponse(existing), true);
        }
    }

    /**
     * Get a single event by eventId.
     */
    public EventResponse getEvent(String eventId) {
        LedgerEvent event = eventRepository.findByEventId(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        return toEventResponse(event);
    }

    /**
     * Get events for an account, ordered by eventTimestamp ASC, then eventId ASC.
     */
    public List<EventResponse> getEventsByAccount(String accountId) {
        return eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc(accountId)
                .stream()
                .map(this::toEventResponse)
                .toList();
    }

    /**
     * Get paginated events for an account, ordered by eventTimestamp ASC, then eventId ASC.
     */
    public Page<EventResponse> getEventsByAccountPaginated(String accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc(accountId, pageable)
                .map(this::toEventResponse);
    }

    /**
     * Convert entity to response DTO.
     */
    private EventResponse toEventResponse(LedgerEvent event) {
        return new EventResponse(
                event.getEventId(),
                event.getAccountId(),
                event.getType(),
                event.getAmount(),
                event.getCurrency(),
                event.getEventTimestamp(),
                deserializeMetadata(event.getMetadata()),
                event.getReceivedAt()
        );
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Map<String, Object> deserializeMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}

