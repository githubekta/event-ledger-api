package com.example.accountservice.repository;

import com.example.accountservice.entity.AccountTransactionEntity;
import com.example.accountservice.enums.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AccountTransactionRepositoryTest {

    @Autowired
    AccountTransactionRepository repo;

    private AccountTransactionEntity tx(String eventId, String acct, TransactionType type,
                                        String amt, String iso) {
        return new AccountTransactionEntity(eventId, acct, type,
                new BigDecimal(amt), "USD", Instant.parse(iso));
    }

    @Test
    void saveAndFindByEventIdRoundTripsBigDecimalAndEnum() {
        repo.save(tx("e-1", "acct-rep", TransactionType.CREDIT, "150.0000", "2026-05-15T10:00:00Z"));

        AccountTransactionEntity loaded = repo.findByEventId("e-1").orElseThrow();

        assertThat(loaded.getType()).isEqualTo(TransactionType.CREDIT);
        assertThat(loaded.getAmount()).isEqualByComparingTo("150.00");
        assertThat(loaded.getCurrency()).isEqualTo("USD");
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void uniqueConstraintOnEventIdPreventsDuplicates() {
        repo.saveAndFlush(tx("dup-1", "acct-rep", TransactionType.CREDIT, "10", "2026-05-15T10:00:00Z"));

        assertThatThrownBy(() ->
                repo.saveAndFlush(tx("dup-1", "acct-rep", TransactionType.DEBIT, "99", "2026-05-15T11:00:00Z")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByAccountIdIsScopedAndOrderedByEventTimestampAsc() {
        repo.save(tx("ord-late",  "acct-A", TransactionType.CREDIT, "1", "2026-05-15T12:00:00Z"));
        repo.save(tx("ord-early", "acct-A", TransactionType.CREDIT, "1", "2026-05-15T10:00:00Z"));
        repo.save(tx("ord-mid",   "acct-A", TransactionType.CREDIT, "1", "2026-05-15T11:00:00Z"));
        repo.save(tx("other",     "acct-B", TransactionType.CREDIT, "1", "2026-05-15T09:00:00Z"));

        List<AccountTransactionEntity> rows =
                repo.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-A");

        assertThat(rows).extracting(AccountTransactionEntity::getEventId)
                .containsExactly("ord-early", "ord-mid", "ord-late");
    }

    @Test
    void existsByEventIdReflectsState() {
        assertThat(repo.existsByEventId("nope")).isFalse();
        repo.save(tx("present", "acct-A", TransactionType.CREDIT, "1", "2026-05-15T10:00:00Z"));
        assertThat(repo.existsByEventId("present")).isTrue();
    }
}

