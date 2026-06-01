package com.example.eventledger.service;

import com.example.eventledger.dto.EventRequest;
import com.example.eventledger.dto.EventResponse;
import com.example.eventledger.dto.EventSubmissionResult;
import com.example.eventledger.entity.EventType;
import com.example.eventledger.entity.LedgerEvent;
import com.example.eventledger.exception.CurrencyMismatchException;
import com.example.eventledger.exception.EventNotFoundException;
import com.example.eventledger.repository.LedgerEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventService using Mockito.
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private LedgerEventRepository eventRepository;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private EventService eventService;

    private EventRequest validRequest;
    private LedgerEvent existingEvent;

    @BeforeEach
    void setUp() {
        validRequest = new EventRequest();
        validRequest.setEventId("evt-001");
        validRequest.setAccountId("acct-123");
        validRequest.setType(EventType.CREDIT);
        validRequest.setAmount(new BigDecimal("100.00"));
        validRequest.setCurrency("USD");
        validRequest.setEventTimestamp(Instant.parse("2026-05-15T10:00:00Z"));

        existingEvent = new LedgerEvent(
                "evt-001",
                "acct-123",
                EventType.CREDIT,
                new BigDecimal("100.00"),
                "USD",
                Instant.parse("2026-05-15T10:00:00Z"),
                null
        );
        existingEvent.setReceivedAt(Instant.now());
    }

    @Nested
    @DisplayName("submitEvent")
    class SubmitEvent {

        @Test
        @DisplayName("saves a new event when eventId does not exist")
        void savesNewEvent_whenEventIdDoesNotExist() {
            when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.empty());
            when(eventRepository.existsByAccountIdAndCurrencyNot(anyString(), anyString())).thenReturn(false);
            when(eventRepository.save(any(LedgerEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

            EventSubmissionResult result = eventService.submitEvent(validRequest);

            assertThat(result.isDuplicate()).isFalse();
            assertThat(result.getEvent().getEventId()).isEqualTo("evt-001");
            verify(eventRepository, times(1)).save(any(LedgerEvent.class));
        }

        @Test
        @DisplayName("returns existing event when duplicate eventId exists")
        void returnsExistingEvent_whenDuplicateEventIdExists() {
            when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.of(existingEvent));

            EventSubmissionResult result = eventService.submitEvent(validRequest);

            assertThat(result.isDuplicate()).isTrue();
            assertThat(result.getEvent().getEventId()).isEqualTo("evt-001");
            verify(eventRepository, never()).save(any(LedgerEvent.class));
        }

        @Test
        @DisplayName("duplicate event does not call save again")
        void duplicateEvent_doesNotCallSave() {
            when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.of(existingEvent));

            eventService.submitEvent(validRequest);

            verify(eventRepository, never()).save(any(LedgerEvent.class));
        }

        @Test
        @DisplayName("handles DataIntegrityViolationException by fetching existing event")
        void handlesDataIntegrityViolation_fetchesExistingEvent() {
            // First call returns empty (simulating race condition)
            when(eventRepository.findByEventId("evt-001"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(existingEvent));
            when(eventRepository.existsByAccountIdAndCurrencyNot(anyString(), anyString())).thenReturn(false);
            when(eventRepository.save(any(LedgerEvent.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate key"));

            EventSubmissionResult result = eventService.submitEvent(validRequest);

            assertThat(result.isDuplicate()).isTrue();
            assertThat(result.getEvent().getEventId()).isEqualTo("evt-001");
            verify(eventRepository, times(2)).findByEventId("evt-001");
        }

        @Test
        @DisplayName("throws CurrencyMismatchException when account has different currency")
        void throwsCurrencyMismatchException_whenDifferentCurrency() {
            when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.empty());
            when(eventRepository.existsByAccountIdAndCurrencyNot("acct-123", "USD")).thenReturn(true);
            when(eventRepository.findDistinctCurrenciesByAccountId("acct-123"))
                    .thenReturn(List.of("EUR"));

            assertThatThrownBy(() -> eventService.submitEvent(validRequest))
                    .isInstanceOf(CurrencyMismatchException.class)
                    .hasMessageContaining("EUR");
        }

        @Test
        @DisplayName("saves event with correct fields")
        void savesEventWithCorrectFields() {
            when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.empty());
            when(eventRepository.existsByAccountIdAndCurrencyNot(anyString(), anyString())).thenReturn(false);
            when(eventRepository.save(any(LedgerEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

            eventService.submitEvent(validRequest);

            ArgumentCaptor<LedgerEvent> captor = ArgumentCaptor.forClass(LedgerEvent.class);
            verify(eventRepository).save(captor.capture());

            LedgerEvent saved = captor.getValue();
            assertThat(saved.getEventId()).isEqualTo("evt-001");
            assertThat(saved.getAccountId()).isEqualTo("acct-123");
            assertThat(saved.getType()).isEqualTo(EventType.CREDIT);
            assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(saved.getCurrency()).isEqualTo("USD");
            assertThat(saved.getReceivedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getEvent")
    class GetEvent {

        @Test
        @DisplayName("returns event when found")
        void returnsEvent_whenFound() {
            when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.of(existingEvent));

            EventResponse response = eventService.getEvent("evt-001");

            assertThat(response.getEventId()).isEqualTo("evt-001");
            assertThat(response.getAccountId()).isEqualTo("acct-123");
        }

        @Test
        @DisplayName("throws EventNotFoundException when not found")
        void throwsEventNotFoundException_whenNotFound() {
            when(eventRepository.findByEventId("evt-999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.getEvent("evt-999"))
                    .isInstanceOf(EventNotFoundException.class)
                    .hasMessageContaining("evt-999");
        }
    }

    @Nested
    @DisplayName("getEventsByAccount")
    class GetEventsByAccount {

        @Test
        @DisplayName("returns events for account ordered by timestamp")
        void returnsEventsOrderedByTimestamp() {
            LedgerEvent event1 = createEvent("evt-001", "acct-123", "2026-05-15T09:00:00Z");
            LedgerEvent event2 = createEvent("evt-002", "acct-123", "2026-05-15T10:00:00Z");

            when(eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-123"))
                    .thenReturn(List.of(event1, event2));

            List<EventResponse> events = eventService.getEventsByAccount("acct-123");

            assertThat(events).hasSize(2);
            assertThat(events.get(0).getEventId()).isEqualTo("evt-001");
            assertThat(events.get(1).getEventId()).isEqualTo("evt-002");
        }

        @Test
        @DisplayName("returns empty list for account with no events")
        void returnsEmptyList_whenNoEvents() {
            when(eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-empty"))
                    .thenReturn(Collections.emptyList());

            List<EventResponse> events = eventService.getEventsByAccount("acct-empty");

            assertThat(events).isEmpty();
        }
    }

    @Nested
    @DisplayName("getEventsByAccountPaginated")
    class GetEventsByAccountPaginated {

        @Test
        @DisplayName("returns paginated events")
        void returnsPaginatedEvents() {
            LedgerEvent event1 = createEvent("evt-001", "acct-123", "2026-05-15T09:00:00Z");
            Page<LedgerEvent> page = new PageImpl<>(List.of(event1));

            when(eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc(
                    eq("acct-123"), any(Pageable.class)))
                    .thenReturn(page);

            Page<EventResponse> result = eventService.getEventsByAccountPaginated("acct-123", 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getEventId()).isEqualTo("evt-001");
        }
    }

    // ==================== Helper Methods ====================

    private LedgerEvent createEvent(String eventId, String accountId, String timestamp) {
        LedgerEvent event = new LedgerEvent(
                eventId,
                accountId,
                EventType.CREDIT,
                new BigDecimal("100.00"),
                "USD",
                Instant.parse(timestamp),
                null
        );
        event.setReceivedAt(Instant.now());
        return event;
    }
}

