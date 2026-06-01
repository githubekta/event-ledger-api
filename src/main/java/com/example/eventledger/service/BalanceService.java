package com.example.eventledger.service;

import com.example.eventledger.dto.BalanceResponse;
import com.example.eventledger.entity.EventType;
import com.example.eventledger.entity.LedgerEvent;
import com.example.eventledger.repository.LedgerEventRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BalanceService {

    private final LedgerEventRepository eventRepository;

    public BalanceService(LedgerEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * Calculate the balance for an account.
     * Balance = sum(CREDIT amounts) - sum(DEBIT amounts)
     * 
     * @param accountId the account ID
     * @return balance response with accountId, balance, and currency
     */
    public BalanceResponse getBalance(String accountId) {
        List<LedgerEvent> events = eventRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc(accountId);

        if (events.isEmpty()) {
            return new BalanceResponse(accountId, BigDecimal.ZERO, null);
        }

        BigDecimal balance = BigDecimal.ZERO;
        String currency = null;

        for (LedgerEvent event : events) {
            if (currency == null) {
                currency = event.getCurrency();
            }

            if (event.getType() == EventType.CREDIT) {
                balance = balance.add(event.getAmount());
            } else if (event.getType() == EventType.DEBIT) {
                balance = balance.subtract(event.getAmount());
            }
        }

        return new BalanceResponse(accountId, balance, currency);
    }
}

