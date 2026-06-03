package com.example.accountservice.service;

import com.example.accountservice.dto.BalanceResponse;
import com.example.accountservice.dto.TransactionRequest;
import com.example.accountservice.dto.TransactionResponse;
import com.example.accountservice.entity.AccountTransactionEntity;
import com.example.accountservice.enums.TransactionType;
import com.example.accountservice.repository.AccountTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountTransactionRepository repository;

    private final AtomicLong totalTransactions = new AtomicLong();
    private final AtomicLong duplicateTransactions = new AtomicLong();
    private final AtomicLong balanceQueries = new AtomicLong();

    public AccountService(AccountTransactionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TransactionResponse recordTransaction(String accountId, TransactionRequest req) {
        totalTransactions.incrementAndGet();

        Optional<AccountTransactionEntity> existing = repository.findByEventId(req.getEventId());
        if (existing.isPresent()) {
            duplicateTransactions.incrementAndGet();
            log.info("Duplicate transaction eventId={} accountId={}", req.getEventId(), accountId);
            return toResponse(existing.get(), true);
        }

        AccountTransactionEntity entity = new AccountTransactionEntity(
                req.getEventId(), accountId, req.getType(),
                req.getAmount(), req.getCurrency(), req.getEventTimestamp());

        try {
            AccountTransactionEntity saved = repository.save(entity);
            log.info("Recorded transaction eventId={} accountId={} type={} amount={}",
                    saved.getEventId(), saved.getAccountId(), saved.getType(), saved.getAmount());
            return toResponse(saved, false);
        } catch (DataIntegrityViolationException e) {
            duplicateTransactions.incrementAndGet();
            AccountTransactionEntity winner = repository.findByEventId(req.getEventId())
                    .orElseThrow(() -> e);
            return toResponse(winner, true);
        }
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountId) {
        balanceQueries.incrementAndGet();
        List<AccountTransactionEntity> txs =
                repository.findByAccountIdOrderByEventTimestampAscEventIdAsc(accountId);

        if (txs.isEmpty()) {
            return new BalanceResponse(accountId, BigDecimal.ZERO, null);
        }

        String currency = txs.get(0).getCurrency();
        BigDecimal balance = BigDecimal.ZERO;
        for (AccountTransactionEntity t : txs) {
            balance = t.getType() == TransactionType.CREDIT
                    ? balance.add(t.getAmount())
                    : balance.subtract(t.getAmount());
        }
        return new BalanceResponse(accountId, balance, currency);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listTransactions(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampAscEventIdAsc(accountId)
                .stream().map(t -> toResponse(t, false)).toList();
    }

    public long getTotalTransactions() { return totalTransactions.get(); }
    public long getDuplicateTransactions() { return duplicateTransactions.get(); }
    public long getBalanceQueries() { return balanceQueries.get(); }

    private TransactionResponse toResponse(AccountTransactionEntity t, boolean duplicate) {
        return new TransactionResponse(t.getEventId(), t.getAccountId(), t.getType(),
                t.getAmount(), t.getCurrency(), t.getEventTimestamp(), duplicate);
    }
}

