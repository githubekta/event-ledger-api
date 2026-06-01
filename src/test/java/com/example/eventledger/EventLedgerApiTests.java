package com.example.eventledger;

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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Main integration tests for Event Ledger API.
 * Covers end-to-end scenarios and concurrency tests.
 * Uses MockMvc and H2 in-memory database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventLedgerApiTests {

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

    @Test
    @DisplayName("Application context loads successfully")
    void contextLoads() {
        // Verifies Spring context starts properly
    }

    @Nested
    @DisplayName("End-to-End Scenarios")
    class EndToEndScenarios {

        @Test
        @DisplayName("Complete workflow: create events, list, check balance")
        void completeWorkflow() throws Exception {
            String accountId = "acct-e2e-001";

            // 1. Create CREDIT event
            EventRequest credit = createRequest("evt-e2e-001", accountId, EventType.CREDIT, "150.00");
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(credit)))
                    .andExpect(status().isCreated());

            // 2. Create DEBIT event
            EventRequest debit = createRequest("evt-e2e-002", accountId, EventType.DEBIT, "40.00");
            debit.setEventTimestamp(Instant.parse("2026-05-15T11:00:00Z"));
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(debit)))
                    .andExpect(status().isCreated());

            // 3. Verify event retrieval
            mockMvc.perform(get("/events/{id}", "evt-e2e-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventId").value("evt-e2e-001"));

            // 4. Verify events list (ordered)
            mockMvc.perform(get("/events").param("account", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].eventId").value("evt-e2e-001"))
                    .andExpect(jsonPath("$[1].eventId").value("evt-e2e-002"));

            // 5. Verify balance
            mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(110.00));
        }

        @Test
        @DisplayName("Out-of-order events processed correctly")
        void outOfOrderEventsProcessedCorrectly() throws Exception {
            String accountId = "acct-ooo-test";

            // Submit events out of order
            submitEvent("evt-ooo-3", accountId, EventType.CREDIT, "30.00", "2026-05-15T12:00:00Z");
            submitEvent("evt-ooo-1", accountId, EventType.CREDIT, "10.00", "2026-05-15T10:00:00Z");
            submitEvent("evt-ooo-2", accountId, EventType.CREDIT, "20.00", "2026-05-15T11:00:00Z");

            // Verify chronological order
            mockMvc.perform(get("/events").param("account", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].eventId").value("evt-ooo-1"))
                    .andExpect(jsonPath("$[1].eventId").value("evt-ooo-2"))
                    .andExpect(jsonPath("$[2].eventId").value("evt-ooo-3"));

            // Balance should be 60 regardless of insertion order
            mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(60.00));
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent duplicate submissions result in only one event")
        void concurrentDuplicates_onlyOneEvent() throws Exception {
            String eventId = "evt-concurrent-001";
            String accountId = "acct-concurrent-test";
            int threadCount = 10;

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger created201 = new AtomicInteger(0);
            AtomicInteger duplicate200 = new AtomicInteger(0);
            List<Throwable> errors = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        EventRequest request = createRequest(eventId, accountId, EventType.CREDIT, "100.00");
                        MvcResult result = mockMvc.perform(post("/events")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                .andReturn();

                        int status = result.getResponse().getStatus();
                        if (status == 201) {
                            created201.incrementAndGet();
                        } else if (status == 200) {
                            duplicate200.incrementAndGet();
                        }
                    } catch (Throwable e) {
                        errors.add(e);
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            endLatch.await();
            executor.shutdown();

            // Verify no errors occurred
            assertThat(errors).isEmpty();

            // Verify exactly one 201 Created
            assertThat(created201.get()).isEqualTo(1);

            // Verify remaining are 200 OK
            assertThat(duplicate200.get()).isEqualTo(threadCount - 1);

            // Verify only one event in database
            assertThat(eventRepository.findByEventId(eventId)).isPresent();
            assertThat(eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc(accountId)).hasSize(1);

            // Verify balance is 100, not 100 * threadCount
            mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(100.00));
        }

        @Test
        @DisplayName("Concurrent different events for same account")
        void concurrentDifferentEvents_allProcessed() throws Exception {
            String accountId = "acct-concurrent-diff";
            int threadCount = 5;

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        EventRequest request = createRequest("evt-diff-" + index, accountId,
                                EventType.CREDIT, "100.00");
                        request.setEventTimestamp(Instant.parse("2026-05-15T" +
                                String.format("%02d", 10 + index) + ":00:00Z"));

                        mockMvc.perform(post("/events")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());

                        successCount.incrementAndGet();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            endLatch.await();
            executor.shutdown();

            // All events should be created
            assertThat(successCount.get()).isEqualTo(threadCount);

            // Balance should be 500 (5 * 100)
            mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(500.00));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Very large amount")
        void veryLargeAmount() throws Exception {
            EventRequest request = createRequest("evt-large-001", "acct-large", EventType.CREDIT, "999999999.99");

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/accounts/{accountId}/balance", "acct-large"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(999999999.99));
        }

        @Test
        @DisplayName("Very small amount")
        void verySmallAmount() throws Exception {
            EventRequest request = createRequest("evt-small-001", "acct-small", EventType.CREDIT, "0.01");

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/accounts/{accountId}/balance", "acct-small"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(0.01));
        }

        @Test
        @DisplayName("Timestamps at millisecond precision")
        void millisecondsTimestamp() throws Exception {
            EventRequest request = createRequest("evt-ms-001", "acct-ms", EventType.CREDIT, "100.00");
            request.setEventTimestamp(Instant.parse("2026-05-15T14:02:11.123Z"));

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.eventTimestamp").isNotEmpty());
        }

        @Test
        @DisplayName("Special characters in eventId")
        void specialCharsInEventId() throws Exception {
            EventRequest request = createRequest("evt-special_123-abc", "acct-special",
                    EventType.CREDIT, "100.00");

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/events/{id}", "evt-special_123-abc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventId").value("evt-special_123-abc"));
        }

        @Test
        @DisplayName("Unicode characters in metadata")
        void unicodeInMetadata() throws Exception {
            String json = """
                {
                    "eventId": "evt-unicode-001",
                    "accountId": "acct-unicode",
                    "type": "CREDIT",
                    "amount": 100.00,
                    "currency": "USD",
                    "eventTimestamp": "2026-05-15T14:02:11Z",
                    "metadata": {
                        "description": "日本語テスト",
                        "emoji": "💰"
                    }
                }
                """;

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.metadata.description").value("日本語テスト"))
                    .andExpect(jsonPath("$.metadata.emoji").value("💰"));
        }
    }

    // ==================== Helper Methods ====================

    private EventRequest createRequest(String eventId, String accountId, EventType type, String amount) {
        EventRequest request = new EventRequest();
        request.setEventId(eventId);
        request.setAccountId(accountId);
        request.setType(type);
        request.setAmount(new BigDecimal(amount));
        request.setCurrency("USD");
        request.setEventTimestamp(Instant.parse("2026-05-15T10:00:00Z"));
        return request;
    }

    private void submitEvent(String eventId, String accountId, EventType type,
                            String amount, String timestamp) throws Exception {
        EventRequest request = createRequest(eventId, accountId, type, amount);
        request.setEventTimestamp(Instant.parse(timestamp));
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}

