package com.example.eventledger.service;

import com.example.eventledger.dto.BalanceResponse;
import com.example.eventledger.entity.EventType;
import com.example.eventledger.entity.LedgerEvent;
import com.example.eventledger.repository.LedgerEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BalanceService using Mockito.
 */
@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private LedgerEventRepository eventRepository;

    @InjectMocks
    private BalanceService balanceService;

    @Nested
    @DisplayName("getBalance")
    class GetBalance {

        @Test
        @DisplayName("returns zero balance for empty account")
        void emptyAccount_returnsZeroBalance() {
            when(eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-empty"))
                    .thenReturn(Collections.emptyList());

            BalanceResponse response = balanceService.getBalance("acct-empty");

            assertThat(response.getAccountId()).isEqualTo("acct-empty");
            assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getCurrency()).isNull();
        }

        @Test
        @DisplayName("only CREDIT events produce positive balance")
        void onlyCreditEvents_positiveBalance() {
            List<LedgerEvent> events = List.of(
                    createEvent("evt-001", EventType.CREDIT, "100.00"),
                    createEvent("evt-002", EventType.CREDIT, "50.00")
            );
            when(eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-credit"))
                    .thenReturn(events);

            BalanceResponse response = balanceService.getBalance("acct-credit");

            assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(response.getCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("only DEBIT events produce negative balance")
        void onlyDebitEvents_negativeBalance() {
            List<LedgerEvent> events = List.of(
                    createEvent("evt-001", EventType.DEBIT, "100.00"),
                    createEvent("evt-002", EventType.DEBIT, "50.00")
            );
            when(eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-debit"))
                    .thenReturn(events);

            BalanceResponse response = balanceService.getBalance("acct-debit");

            assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("-150.00"));
        }

        @Test
        @DisplayName("mixed CREDIT and DEBIT returns net balance")
        void mixedCreditAndDebit_returnsNetBalance() {
            List<LedgerEvent> events = List.of(
                    createEvent("evt-001", EventType.CREDIT, "150.00"),
                    createEvent("evt-002", EventType.DEBIT, "40.00"),
                    createEvent("evt-003", EventType.CREDIT, "10.00")
            );
            when(eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-mixed"))
                    .thenReturn(events);

            BalanceResponse response = balanceService.getBalance("acct-mixed");

            // 150 - 40 + 10 = 120
            assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("120.00"));
        }

        @Test
        @DisplayName("balance calculation is scale-safe with different decimal scales")
        void differentDecimalScales_correctBalance() {
            List<LedgerEvent> events = List.of(
                    createEventWithScale("evt-001", EventType.CREDIT, "100.5"),
                    createEventWithScale("evt-002", EventType.CREDIT, "100.50"),
                    createEventWithScale("evt-003", EventType.DEBIT, "50.500")
            );
            when(eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-scale"))
                    .thenReturn(events);

            BalanceResponse response = balanceService.getBalance("acct-scale");

            // 100.5 + 100.50 - 50.500 = 150.5
            assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("150.5"));
        }

        @Test
        @DisplayName("single event returns correct balance")
        void singleEvent_returnsCorrectBalance() {
            List<LedgerEvent> events = List.of(
                    createEvent("evt-001", EventType.CREDIT, "123.45")
            );
            when(eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-single"))
                    .thenReturn(events);

            BalanceResponse response = balanceService.getBalance("acct-single");

            assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("123.45"));
            assertThat(response.getCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("large amounts calculate correctly")
        void largeAmounts_correctCalculation() {
            List<LedgerEvent> events = List.of(
                    createEvent("evt-001", EventType.CREDIT, "999999999.99"),
                    createEvent("evt-002", EventType.DEBIT, "1.01")
            );
            when(eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-large"))
                    .thenReturn(events);

            BalanceResponse response = balanceService.getBalance("acct-large");

            assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("999999998.98"));
        }

        @Test
        @DisplayName("many small transactions accumulate correctly")
        void manySmallTransactions_correctAccumulation() {
            // Create 100 credit events of 0.01 each
            List<LedgerEvent> events = new java.util.ArrayList<>();
            for (int i = 0; i < 100; i++) {
                events.add(createEvent("evt-" + String.format("%03d", i), EventType.CREDIT, "0.01"));
            }
            when(eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-many"))
                    .thenReturn(events);

            BalanceResponse response = balanceService.getBalance("acct-many");

            // 100 * 0.01 = 1.00
            assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("1.00"));
        }

        @Test
        @DisplayName("balance zero when credits equal debits")
        void creditsEqualDebits_zeroBalance() {
            List<LedgerEvent> events = List.of(
                    createEvent("evt-001", EventType.CREDIT, "100.00"),
                    createEvent("evt-002", EventType.DEBIT, "100.00")
            );
            when(eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-zero"))
                    .thenReturn(events);

            BalanceResponse response = balanceService.getBalance("acct-zero");

            assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("returns currency from first event")
        void returnsCurrencyFromFirstEvent() {
            LedgerEvent event = new LedgerEvent(
                    "evt-001",
                    "acct-eur",
                    EventType.CREDIT,
                    new BigDecimal("100.00"),
                    "EUR",
                    Instant.parse("2026-05-15T10:00:00Z"),
                    null
            );
            event.setReceivedAt(Instant.now());

            when(eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-eur"))
                    .thenReturn(List.of(event));

            BalanceResponse response = balanceService.getBalance("acct-eur");

            assertThat(response.getCurrency()).isEqualTo("EUR");
        }
    }

    // ==================== Helper Methods ====================

    private LedgerEvent createEvent(String eventId, EventType type, String amount) {
        LedgerEvent event = new LedgerEvent(
                eventId,
                "acct-test",
                type,
                new BigDecimal(amount),
                "USD",
                Instant.parse("2026-05-15T10:00:00Z"),
                null
        );
        event.setReceivedAt(Instant.now());
        return event;
    }

    private LedgerEvent createEventWithScale(String eventId, EventType type, String amount) {
        LedgerEvent event = new LedgerEvent(
                eventId,
                "acct-test",
                type,
                new BigDecimal(amount),
                "USD",
                Instant.parse("2026-05-15T10:00:00Z"),
                null
        );
        event.setReceivedAt(Instant.now());
        return event;
    }
}

