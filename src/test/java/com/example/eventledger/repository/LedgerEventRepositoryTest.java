package com.example.eventledger.repository;

import com.example.eventledger.entity.EventType;
import com.example.eventledger.entity.LedgerEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository tests using @DataJpaTest with H2.
 */
@DataJpaTest
class LedgerEventRepositoryTest {

    @Autowired
    private LedgerEventRepository repository;

    @Nested
    @DisplayName("save and findByEventId")
    class SaveAndFind {

        @Test
        @DisplayName("saves and retrieves event by eventId")
        void savesAndRetrieves() {
            LedgerEvent event = createEvent("evt-001", "acct-123", "2026-05-15T10:00:00Z");
            repository.save(event);

            Optional<LedgerEvent> found = repository.findByEventId("evt-001");

            assertThat(found).isPresent();
            assertThat(found.get().getEventId()).isEqualTo("evt-001");
            assertThat(found.get().getAccountId()).isEqualTo("acct-123");
        }

        @Test
        @DisplayName("returns empty optional for non-existent eventId")
        void returnsEmpty_whenNotFound() {
            Optional<LedgerEvent> found = repository.findByEventId("non-existent");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("Primary Key Constraint")
    class PrimaryKeyConstraint {

        @Test
        @DisplayName("eventId primary key prevents duplicates")
        void primaryKeyPreventsDuplicates() {
            LedgerEvent event1 = createEvent("evt-dup", "acct-123", "2026-05-15T10:00:00Z");
            repository.save(event1);

            LedgerEvent event2 = createEvent("evt-dup", "acct-456", "2026-05-15T11:00:00Z");

            assertThatThrownBy(() -> {
                repository.save(event2);
                repository.flush();  // Force the constraint check
            }).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("findByAccountId")
    class FindByAccountId {

        @Test
        @DisplayName("returns only events for specified account")
        void returnsOnlyEventsForAccount() {
            repository.save(createEvent("evt-001", "acct-1", "2026-05-15T10:00:00Z"));
            repository.save(createEvent("evt-002", "acct-2", "2026-05-15T10:00:00Z"));
            repository.save(createEvent("evt-003", "acct-1", "2026-05-15T11:00:00Z"));

            List<LedgerEvent> events = repository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-1");

            assertThat(events).hasSize(2);
            assertThat(events).allMatch(e -> e.getAccountId().equals("acct-1"));
        }

        @Test
        @DisplayName("returns empty list for account with no events")
        void returnsEmptyList_whenNoEvents() {
            List<LedgerEvent> events = repository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-empty");

            assertThat(events).isEmpty();
        }
    }

    @Nested
    @DisplayName("Sorting")
    class Sorting {

        @Test
        @DisplayName("events retrieved sorted by eventTimestamp ascending")
        void sortedByTimestampAscending() {
            repository.save(createEvent("evt-c", "acct-1", "2026-05-15T12:00:00Z"));
            repository.save(createEvent("evt-a", "acct-1", "2026-05-15T10:00:00Z"));
            repository.save(createEvent("evt-b", "acct-1", "2026-05-15T11:00:00Z"));

            List<LedgerEvent> events = repository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-1");

            assertThat(events).hasSize(3);
            assertThat(events.get(0).getEventId()).isEqualTo("evt-a");  // 10:00
            assertThat(events.get(1).getEventId()).isEqualTo("evt-b");  // 11:00
            assertThat(events.get(2).getEventId()).isEqualTo("evt-c");  // 12:00
        }

        @Test
        @DisplayName("events with same timestamp sorted by eventId")
        void sameTimestamp_sortedByEventId() {
            repository.save(createEvent("evt-c", "acct-1", "2026-05-15T10:00:00Z"));
            repository.save(createEvent("evt-a", "acct-1", "2026-05-15T10:00:00Z"));
            repository.save(createEvent("evt-b", "acct-1", "2026-05-15T10:00:00Z"));

            List<LedgerEvent> events = repository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-1");

            assertThat(events).hasSize(3);
            assertThat(events.get(0).getEventId()).isEqualTo("evt-a");
            assertThat(events.get(1).getEventId()).isEqualTo("evt-b");
            assertThat(events.get(2).getEventId()).isEqualTo("evt-c");
        }
    }

    @Nested
    @DisplayName("Pagination")
    class Pagination {

        @Test
        @DisplayName("pagination works correctly")
        void paginationWorks() {
            // Create 5 events
            for (int i = 1; i <= 5; i++) {
                repository.save(createEvent("evt-" + String.format("%03d", i), "acct-1",
                        String.format("2026-05-15T%02d:00:00Z", i)));
            }

            // Get first page (size 2)
            Page<LedgerEvent> page0 = repository.findByAccountIdOrderByEventTimestampAscEventIdAsc(
                    "acct-1", PageRequest.of(0, 2));

            assertThat(page0.getContent()).hasSize(2);
            assertThat(page0.getTotalElements()).isEqualTo(5);
            assertThat(page0.getTotalPages()).isEqualTo(3);
            assertThat(page0.getContent().get(0).getEventId()).isEqualTo("evt-001");
            assertThat(page0.getContent().get(1).getEventId()).isEqualTo("evt-002");

            // Get second page
            Page<LedgerEvent> page1 = repository.findByAccountIdOrderByEventTimestampAscEventIdAsc(
                    "acct-1", PageRequest.of(1, 2));

            assertThat(page1.getContent()).hasSize(2);
            assertThat(page1.getContent().get(0).getEventId()).isEqualTo("evt-003");
            assertThat(page1.getContent().get(1).getEventId()).isEqualTo("evt-004");

            // Get last page
            Page<LedgerEvent> page2 = repository.findByAccountIdOrderByEventTimestampAscEventIdAsc(
                    "acct-1", PageRequest.of(2, 2));

            assertThat(page2.getContent()).hasSize(1);
            assertThat(page2.getContent().get(0).getEventId()).isEqualTo("evt-005");
        }

        @Test
        @DisplayName("empty page for out of range request")
        void emptyPage_outOfRange() {
            repository.save(createEvent("evt-001", "acct-1", "2026-05-15T10:00:00Z"));

            Page<LedgerEvent> page = repository.findByAccountIdOrderByEventTimestampAscEventIdAsc(
                    "acct-1", PageRequest.of(10, 2));

            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("BigDecimal Handling")
    class BigDecimalHandling {

        @Test
        @DisplayName("stored amount uses BigDecimal correctly")
        void bigDecimalStoredCorrectly() {
            LedgerEvent event = createEvent("evt-001", "acct-1", "2026-05-15T10:00:00Z");
            event.setAmount(new BigDecimal("123.4567"));
            repository.save(event);

            LedgerEvent found = repository.findByEventId("evt-001").orElseThrow();

            assertThat(found.getAmount()).isEqualByComparingTo(new BigDecimal("123.4567"));
        }

        @Test
        @DisplayName("large decimal values stored correctly")
        void largeBigDecimalStoredCorrectly() {
            LedgerEvent event = createEvent("evt-001", "acct-1", "2026-05-15T10:00:00Z");
            event.setAmount(new BigDecimal("999999999999.9999"));
            repository.save(event);

            LedgerEvent found = repository.findByEventId("evt-001").orElseThrow();

            assertThat(found.getAmount()).isEqualByComparingTo(new BigDecimal("999999999999.9999"));
        }

        @Test
        @DisplayName("small decimal values stored correctly")
        void smallBigDecimalStoredCorrectly() {
            LedgerEvent event = createEvent("evt-001", "acct-1", "2026-05-15T10:00:00Z");
            event.setAmount(new BigDecimal("0.0001"));
            repository.save(event);

            LedgerEvent found = repository.findByEventId("evt-001").orElseThrow();

            assertThat(found.getAmount()).isEqualByComparingTo(new BigDecimal("0.0001"));
        }
    }

    @Nested
    @DisplayName("EventType Enum")
    class EventTypeEnum {

        @Test
        @DisplayName("CREDIT type persists and loads correctly")
        void creditTypePersistsCorrectly() {
            LedgerEvent event = createEvent("evt-credit", "acct-1", "2026-05-15T10:00:00Z");
            event.setType(EventType.CREDIT);
            repository.save(event);

            LedgerEvent found = repository.findByEventId("evt-credit").orElseThrow();

            assertThat(found.getType()).isEqualTo(EventType.CREDIT);
        }

        @Test
        @DisplayName("DEBIT type persists and loads correctly")
        void debitTypePersistsCorrectly() {
            LedgerEvent event = createEvent("evt-debit", "acct-1", "2026-05-15T10:00:00Z");
            event.setType(EventType.DEBIT);
            repository.save(event);

            LedgerEvent found = repository.findByEventId("evt-debit").orElseThrow();

            assertThat(found.getType()).isEqualTo(EventType.DEBIT);
        }
    }

    @Nested
    @DisplayName("Currency Methods")
    class CurrencyMethods {

        @Test
        @DisplayName("existsByAccountIdAndCurrencyNot returns true when different currency exists")
        void existsByDifferentCurrency_returnsTrue() {
            LedgerEvent event = createEvent("evt-001", "acct-1", "2026-05-15T10:00:00Z");
            event.setCurrency("EUR");
            repository.save(event);

            boolean exists = repository.existsByAccountIdAndCurrencyNot("acct-1", "USD");

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("existsByAccountIdAndCurrencyNot returns false when same currency")
        void existsBySameCurrency_returnsFalse() {
            LedgerEvent event = createEvent("evt-001", "acct-1", "2026-05-15T10:00:00Z");
            event.setCurrency("USD");
            repository.save(event);

            boolean exists = repository.existsByAccountIdAndCurrencyNot("acct-1", "USD");

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("findDistinctCurrenciesByAccountId returns currencies")
        void findDistinctCurrencies_returnsCurrencies() {
            LedgerEvent event = createEvent("evt-001", "acct-1", "2026-05-15T10:00:00Z");
            event.setCurrency("EUR");
            repository.save(event);

            List<String> currencies = repository.findDistinctCurrenciesByAccountId("acct-1");

            assertThat(currencies).containsExactly("EUR");
        }
    }

    @Nested
    @DisplayName("Metadata Storage")
    class MetadataStorage {

        @Test
        @DisplayName("null metadata is stored correctly")
        void nullMetadataStoredCorrectly() {
            LedgerEvent event = createEvent("evt-001", "acct-1", "2026-05-15T10:00:00Z");
            event.setMetadata(null);
            repository.save(event);

            LedgerEvent found = repository.findByEventId("evt-001").orElseThrow();

            assertThat(found.getMetadata()).isNull();
        }

        @Test
        @DisplayName("JSON metadata is stored correctly")
        void jsonMetadataStoredCorrectly() {
            LedgerEvent event = createEvent("evt-001", "acct-1", "2026-05-15T10:00:00Z");
            event.setMetadata("{\"source\":\"test\",\"batchId\":\"B-001\"}");
            repository.save(event);

            LedgerEvent found = repository.findByEventId("evt-001").orElseThrow();

            assertThat(found.getMetadata()).isEqualTo("{\"source\":\"test\",\"batchId\":\"B-001\"}");
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

