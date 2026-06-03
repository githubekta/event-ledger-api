package com.example.accountservice.service;

import com.example.accountservice.dto.BalanceResponse;
import com.example.accountservice.dto.TransactionRequest;
import com.example.accountservice.dto.TransactionResponse;
import com.example.accountservice.entity.AccountTransactionEntity;
import com.example.accountservice.enums.TransactionType;
import com.example.accountservice.repository.AccountTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock AccountTransactionRepository repo;
    @InjectMocks AccountService service;

    private TransactionRequest req(String id, TransactionType type, String amt) {
        TransactionRequest r = new TransactionRequest();
        r.setEventId(id);
        r.setType(type);
        r.setAmount(new BigDecimal(amt));
        r.setCurrency("USD");
        r.setEventTimestamp(Instant.parse("2026-05-15T10:00:00Z"));
        return r;
    }

    private AccountTransactionEntity entity(String id, TransactionType type, String amt) {
        return new AccountTransactionEntity(id, "acct-1", type,
                new BigDecimal(amt), "USD", Instant.parse("2026-05-15T10:00:00Z"));
    }

    @Test
    void newTransactionIsSaved() {
        when(repo.findByEventId("e1")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        TransactionResponse r = service.recordTransaction("acct-1", req("e1", TransactionType.CREDIT, "100"));

        assertThat(r.isDuplicate()).isFalse();
        verify(repo).save(any());
    }

    @Test
    void duplicateReturnsExistingAndDoesNotSave() {
        when(repo.findByEventId("e1")).thenReturn(Optional.of(entity("e1", TransactionType.CREDIT, "100")));

        TransactionResponse r = service.recordTransaction("acct-1", req("e1", TransactionType.CREDIT, "100"));

        assertThat(r.isDuplicate()).isTrue();
        verify(repo, never()).save(any());
    }

    @Test
    void raceConditionRecoversByRefetch() {
        when(repo.findByEventId("e1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(entity("e1", TransactionType.CREDIT, "100")));
        when(repo.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        TransactionResponse r = service.recordTransaction("acct-1", req("e1", TransactionType.CREDIT, "100"));

        assertThat(r.isDuplicate()).isTrue();
    }

    @Test
    void balanceIsCreditMinusDebit() {
        when(repo.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-1"))
                .thenReturn(List.of(
                        entity("e1", TransactionType.CREDIT, "150"),
                        entity("e2", TransactionType.DEBIT, "40"),
                        entity("e3", TransactionType.CREDIT, "10")));

        BalanceResponse b = service.getBalance("acct-1");

        assertThat(b.getBalance()).isEqualByComparingTo("120");
        assertThat(b.getCurrency()).isEqualTo("USD");
    }

    @Test
    void emptyAccountReturnsZero() {
        when(repo.findByAccountIdOrderByEventTimestampAscEventIdAsc(eq("x"))).thenReturn(List.of());
        BalanceResponse b = service.getBalance("x");
        assertThat(b.getBalance()).isEqualByComparingTo("0");
        assertThat(b.getCurrency()).isNull();
    }
}

