package com.example.eventledger.controller;

import com.example.eventledger.dto.EventRequest;
import com.example.eventledger.entity.EventType;
import com.example.eventledger.repository.LedgerEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for EventController using MockMvc and H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LedgerEventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    // ==================== POST /events ====================

    @Nested
    @DisplayName("POST /events")
    class PostEvents {

        @Test
        @DisplayName("returns 201 Created for a valid new event")
        void validNewEvent_returns201() throws Exception {
            EventRequest request = createValidEventRequest("evt-new-001", "acct-100");

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.eventId").value("evt-new-001"))
                    .andExpect(jsonPath("$.accountId").value("acct-100"))
                    .andExpect(jsonPath("$.type").value("CREDIT"))
                    .andExpect(jsonPath("$.amount").value(150.00))
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.eventTimestamp").isNotEmpty())
                    .andExpect(jsonPath("$.receivedAt").isNotEmpty());
        }

        @Test
        @DisplayName("returns 200 OK for duplicate eventId")
        void duplicateEventId_returns200() throws Exception {
            EventRequest request = createValidEventRequest("evt-dup-001", "acct-100");

            // First submission - 201
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // Second submission - 200
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventId").value("evt-dup-001"));
        }

        @Test
        @DisplayName("duplicate POST does not change balance")
        void duplicatePost_doesNotChangeBalance() throws Exception {
            String accountId = "acct-balance-dup";
            EventRequest request = createValidEventRequest("evt-bal-dup-001", accountId);
            request.setAmount(new BigDecimal("100.00"));

            // Submit same event 3 times
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Balance should be 100, not 300
            mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(100.00));
        }

        @Test
        @DisplayName("duplicate POST returns original event even with different payload")
        void duplicatePost_returnsOriginalEvent() throws Exception {
            EventRequest original = createValidEventRequest("evt-orig-001", "acct-100");
            original.setAmount(new BigDecimal("150.00"));

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(original)))
                    .andExpect(status().isCreated());

            // Submit with different payload but same eventId
            EventRequest modified = new EventRequest();
            modified.setEventId("evt-orig-001");
            modified.setAccountId("acct-999");
            modified.setType(EventType.DEBIT);
            modified.setAmount(new BigDecimal("999.00"));
            modified.setCurrency("EUR");
            modified.setEventTimestamp(Instant.parse("2030-01-01T00:00:00Z"));

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(modified)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amount").value(150.00))
                    .andExpect(jsonPath("$.accountId").value("acct-100"))
                    .andExpect(jsonPath("$.type").value("CREDIT"));
        }

        @Test
        @DisplayName("stores and returns metadata correctly")
        void storesMetadata() throws Exception {
            EventRequest request = createValidEventRequest("evt-meta-001", "acct-100");
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "mainframe-batch");
            metadata.put("batchId", "B-9042");
            request.setMetadata(metadata);

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.metadata.source").value("mainframe-batch"))
                    .andExpect(jsonPath("$.metadata.batchId").value("B-9042"));
        }
    }

    // ==================== GET /events/{id} ====================

    @Nested
    @DisplayName("GET /events/{id}")
    class GetEventById {

        @Test
        @DisplayName("returns 200 for existing event")
        void existingEvent_returns200() throws Exception {
            EventRequest request = createValidEventRequest("evt-get-001", "acct-100");
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/events/{id}", "evt-get-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventId").value("evt-get-001"))
                    .andExpect(jsonPath("$.accountId").value("acct-100"));
        }

        @Test
        @DisplayName("returns 404 for unknown event")
        void unknownEvent_returns404() throws Exception {
            mockMvc.perform(get("/events/{id}", "non-existent-event-id"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("ERR-001"));
        }
    }

    // ==================== GET /events?account={accountId} ====================

    @Nested
    @DisplayName("GET /events?account={accountId}")
    class GetEventsByAccount {

        @Test
        @DisplayName("returns events ordered by eventTimestamp ascending")
        void returnsEventsInChronologicalOrder() throws Exception {
            String accountId = "acct-order-test";

            // Submit out of order
            submitEvent("evt-order-c", accountId, "2026-05-15T11:00:00Z");
            submitEvent("evt-order-a", accountId, "2026-05-15T09:00:00Z");
            submitEvent("evt-order-b", accountId, "2026-05-15T10:00:00Z");

            mockMvc.perform(get("/events")
                            .param("account", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].eventId").value("evt-order-a"))
                    .andExpect(jsonPath("$[1].eventId").value("evt-order-b"))
                    .andExpect(jsonPath("$[2].eventId").value("evt-order-c"));
        }

        @Test
        @DisplayName("returns empty array for account with no events")
        void noEvents_returnsEmptyArray() throws Exception {
            mockMvc.perform(get("/events")
                            .param("account", "acct-no-events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("supports pagination with page and size")
        void pagination_worksCorrectly() throws Exception {
            String accountId = "acct-page-test";

            // Create 5 events
            for (int i = 1; i <= 5; i++) {
                submitEvent("evt-page-" + String.format("%03d", i), accountId,
                        String.format("2026-05-15T%02d:00:00Z", i));
            }

            // Get page 0, size 2
            mockMvc.perform(get("/events")
                            .param("account", accountId)
                            .param("page", "0")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].eventId").value("evt-page-001"))
                    .andExpect(jsonPath("$.content[1].eventId").value("evt-page-002"))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3));

            // Get page 1, size 2
            mockMvc.perform(get("/events")
                            .param("account", accountId)
                            .param("page", "1")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].eventId").value("evt-page-003"))
                    .andExpect(jsonPath("$.content[1].eventId").value("evt-page-004"));

            // Get last page
            mockMvc.perform(get("/events")
                            .param("account", accountId)
                            .param("page", "2")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].eventId").value("evt-page-005"));
        }

        @Test
        @DisplayName("returns only events for the specified account")
        void returnsOnlyEventsForSpecifiedAccount() throws Exception {
            submitEvent("evt-acct1-001", "acct-1", "2026-05-15T10:00:00Z");
            submitEvent("evt-acct2-001", "acct-2", "2026-05-15T10:00:00Z");
            submitEvent("evt-acct1-002", "acct-1", "2026-05-15T11:00:00Z");

            mockMvc.perform(get("/events")
                            .param("account", "acct-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].accountId").value("acct-1"))
                    .andExpect(jsonPath("$[1].accountId").value("acct-1"));
        }
    }

    // ==================== Validation Tests ====================

    @Nested
    @DisplayName("POST /events - Validation")
    class ValidationTests {

        @Test
        @DisplayName("missing eventId returns 400")
        void missingEventId_returns400() throws Exception {
            String json = """
                {
                    "accountId": "acct-123",
                    "type": "CREDIT",
                    "amount": 100.00,
                    "currency": "USD",
                    "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ERR-003"));
        }

        @Test
        @DisplayName("blank eventId returns 400")
        void blankEventId_returns400() throws Exception {
            String json = """
                {
                    "eventId": "",
                    "accountId": "acct-123",
                    "type": "CREDIT",
                    "amount": 100.00,
                    "currency": "USD",
                    "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ERR-003"));
        }

        @Test
        @DisplayName("missing accountId returns 400")
        void missingAccountId_returns400() throws Exception {
            String json = """
                {
                    "eventId": "evt-val-001",
                    "type": "CREDIT",
                    "amount": 100.00,
                    "currency": "USD",
                    "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ERR-003"));
        }

        @Test
        @DisplayName("invalid type returns 400")
        void invalidType_returns400() throws Exception {
            String json = """
                {
                    "eventId": "evt-val-002",
                    "accountId": "acct-123",
                    "type": "INVALID_TYPE",
                    "amount": 100.00,
                    "currency": "USD",
                    "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ERR-005"));
        }

        @Test
        @DisplayName("missing type returns 400")
        void missingType_returns400() throws Exception {
            String json = """
                {
                    "eventId": "evt-val-003",
                    "accountId": "acct-123",
                    "amount": 100.00,
                    "currency": "USD",
                    "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ERR-003"));
        }

        @Test
        @DisplayName("zero amount returns 400")
        void zeroAmount_returns400() throws Exception {
            String json = """
                {
                    "eventId": "evt-val-004",
                    "accountId": "acct-123",
                    "type": "CREDIT",
                    "amount": 0,
                    "currency": "USD",
                    "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ERR-003"));
        }

        @Test
        @DisplayName("negative amount returns 400")
        void negativeAmount_returns400() throws Exception {
            String json = """
                {
                    "eventId": "evt-val-005",
                    "accountId": "acct-123",
                    "type": "CREDIT",
                    "amount": -50.00,
                    "currency": "USD",
                    "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ERR-003"));
        }

        @Test
        @DisplayName("missing currency returns 400")
        void missingCurrency_returns400() throws Exception {
            String json = """
                {
                    "eventId": "evt-val-006",
                    "accountId": "acct-123",
                    "type": "CREDIT",
                    "amount": 100.00,
                    "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ERR-003"));
        }

        @Test
        @DisplayName("missing eventTimestamp returns 400")
        void missingEventTimestamp_returns400() throws Exception {
            String json = """
                {
                    "eventId": "evt-val-007",
                    "accountId": "acct-123",
                    "type": "CREDIT",
                    "amount": 100.00,
                    "currency": "USD"
                }
                """;

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ERR-003"));
        }

        @Test
        @DisplayName("invalid timestamp format returns 400")
        void invalidTimestampFormat_returns400() throws Exception {
            String json = """
                {
                    "eventId": "evt-val-008",
                    "accountId": "acct-123",
                    "type": "CREDIT",
                    "amount": 100.00,
                    "currency": "USD",
                    "eventTimestamp": "not-a-valid-timestamp"
                }
                """;

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ERR-006"));
        }

        @Test
        @DisplayName("malformed JSON returns 400")
        void malformedJson_returns400() throws Exception {
            String json = "{ invalid json }";

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("currency mismatch for same account returns 400")
        void currencyMismatch_returns400() throws Exception {
            // First event in USD
            EventRequest usdEvent = createValidEventRequest("evt-curr-001", "acct-currency");
            usdEvent.setCurrency("USD");
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(usdEvent)))
                    .andExpect(status().isCreated());

            // Second event in EUR - should fail
            EventRequest eurEvent = createValidEventRequest("evt-curr-002", "acct-currency");
            eurEvent.setCurrency("EUR");
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(eurEvent)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ERR-007"));
        }
    }

    // ==================== Helper Methods ====================

    private EventRequest createValidEventRequest(String eventId, String accountId) {
        EventRequest request = new EventRequest();
        request.setEventId(eventId);
        request.setAccountId(accountId);
        request.setType(EventType.CREDIT);
        request.setAmount(new BigDecimal("150.00"));
        request.setCurrency("USD");
        request.setEventTimestamp(Instant.parse("2026-05-15T14:02:11Z"));
        return request;
    }

    private void submitEvent(String eventId, String accountId, String timestamp) throws Exception {
        EventRequest request = createValidEventRequest(eventId, accountId);
        request.setEventTimestamp(Instant.parse(timestamp));
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}

