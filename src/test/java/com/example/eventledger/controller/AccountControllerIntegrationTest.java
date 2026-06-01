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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AccountController using MockMvc and H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIntegrationTest {

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

    @Nested
    @DisplayName("GET /accounts/{accountId}/balance")
    class GetAccountBalance {

        @Test
        @DisplayName("returns 0 for account with no events")
        void noEvents_returnsZeroBalance() throws Exception {
            mockMvc.perform(get("/accounts/{accountId}/balance", "acct-empty"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value("acct-empty"))
                    .andExpect(jsonPath("$.balance").value(0.00))
                    .andExpect(jsonPath("$.currency").isEmpty());
        }

        @Test
        @DisplayName("CREDIT increases balance")
        void credit_increasesBalance() throws Exception {
            String accountId = "acct-credit-test";
            submitEvent("evt-credit-001", accountId, EventType.CREDIT, "100.00", "2026-05-15T10:00:00Z");

            mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value(accountId))
                    .andExpect(jsonPath("$.balance").value(100.00))
                    .andExpect(jsonPath("$.currency").value("USD"));
        }

        @Test
        @DisplayName("DEBIT decreases balance (can go negative)")
        void debit_decreasesBalance() throws Exception {
            String accountId = "acct-debit-test";
            submitEvent("evt-debit-001", accountId, EventType.DEBIT, "50.00", "2026-05-15T10:00:00Z");

            mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value(accountId))
                    .andExpect(jsonPath("$.balance").value(-50.00))
                    .andExpect(jsonPath("$.currency").value("USD"));
        }

        @Test
        @DisplayName("multiple CREDIT and DEBIT events return correct net balance")
        void multipleCreditAndDebit_returnsNetBalance() throws Exception {
            String accountId = "acct-net-test";

            // CREDIT 150
            submitEvent("evt-net-001", accountId, EventType.CREDIT, "150.00", "2026-05-15T10:00:00Z");
            // DEBIT 40
            submitEvent("evt-net-002", accountId, EventType.DEBIT, "40.00", "2026-05-15T11:00:00Z");
            // CREDIT 10
            submitEvent("evt-net-003", accountId, EventType.CREDIT, "10.00", "2026-05-15T12:00:00Z");

            // Balance = 150 - 40 + 10 = 120
            mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value(accountId))
                    .andExpect(jsonPath("$.balance").value(120.00))
                    .andExpect(jsonPath("$.currency").value("USD"));
        }

        @Test
        @DisplayName("balance is correct even when events arrive out of timestamp order")
        void outOfOrderEvents_correctBalance() throws Exception {
            String accountId = "acct-outoforder-test";

            // Submit events out of timestamp order
            // Event at 12:00 (latest) submitted first
            submitEvent("evt-ooo-003", accountId, EventType.CREDIT, "10.00", "2026-05-15T12:00:00Z");
            // Event at 10:00 (earliest) submitted second
            submitEvent("evt-ooo-001", accountId, EventType.CREDIT, "150.00", "2026-05-15T10:00:00Z");
            // Event at 11:00 (middle) submitted last
            submitEvent("evt-ooo-002", accountId, EventType.DEBIT, "40.00", "2026-05-15T11:00:00Z");

            // Balance = 150 - 40 + 10 = 120 (same as if submitted in order)
            mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(120.00));
        }

        @Test
        @DisplayName("duplicate events are counted only once")
        void duplicateEvents_countedOnce() throws Exception {
            String accountId = "acct-dupcount-test";

            // Submit same event 3 times
            for (int i = 0; i < 3; i++) {
                EventRequest request = createEventRequest("evt-dupcount-001", accountId,
                        EventType.CREDIT, "100.00", "2026-05-15T10:00:00Z");
                mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)));
            }

            // Balance should be 100, not 300
            mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(100.00));
        }

        @Test
        @DisplayName("large numbers of events calculate correctly")
        void manyEvents_correctBalance() throws Exception {
            String accountId = "acct-many-test";

            // Add 10 credits of 100 each
            for (int i = 1; i <= 10; i++) {
                submitEvent("evt-many-c-" + String.format("%03d", i), accountId,
                        EventType.CREDIT, "100.00",
                        String.format("2026-05-15T%02d:00:00Z", i));
            }

            // Add 5 debits of 50 each
            for (int i = 1; i <= 5; i++) {
                submitEvent("evt-many-d-" + String.format("%03d", i), accountId,
                        EventType.DEBIT, "50.00",
                        String.format("2026-05-15T%02d:30:00Z", i));
            }

            // Balance = (10 * 100) - (5 * 50) = 1000 - 250 = 750
            mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(750.00));
        }

        @Test
        @DisplayName("balance with decimal amounts")
        void decimalAmounts_correctBalance() throws Exception {
            String accountId = "acct-decimal-test";

            submitEvent("evt-dec-001", accountId, EventType.CREDIT, "100.50", "2026-05-15T10:00:00Z");
            submitEvent("evt-dec-002", accountId, EventType.DEBIT, "25.25", "2026-05-15T11:00:00Z");

            // Balance = 100.50 - 25.25 = 75.25
            mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(75.25));
        }

        @Test
        @DisplayName("balance returns correct currency from events")
        void returnsCorrectCurrency() throws Exception {
            String accountId = "acct-curr-test";

            EventRequest request = createEventRequest("evt-curr-001", accountId,
                    EventType.CREDIT, "100.00", "2026-05-15T10:00:00Z");
            request.setCurrency("EUR");

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currency").value("EUR"));
        }
    }

    // ==================== Helper Methods ====================

    private EventRequest createEventRequest(String eventId, String accountId,
                                            EventType type, String amount, String timestamp) {
        EventRequest request = new EventRequest();
        request.setEventId(eventId);
        request.setAccountId(accountId);
        request.setType(type);
        request.setAmount(new BigDecimal(amount));
        request.setCurrency("USD");
        request.setEventTimestamp(Instant.parse(timestamp));
        return request;
    }

    private void submitEvent(String eventId, String accountId, EventType type,
                            String amount, String timestamp) throws Exception {
        EventRequest request = createEventRequest(eventId, accountId, type, amount, timestamp);
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}

