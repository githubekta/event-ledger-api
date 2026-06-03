package com.example.accountservice.dto;

import com.example.accountservice.enums.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public class TransactionResponse {
    private String eventId;
    private String accountId;
    private TransactionType type;
    private BigDecimal amount;
    private String currency;
    private Instant eventTimestamp;
    private boolean duplicate;

    public TransactionResponse() {}

    public TransactionResponse(String eventId, String accountId, TransactionType type,
                               BigDecimal amount, String currency, Instant eventTimestamp,
                               boolean duplicate) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
        this.duplicate = duplicate;
    }

    public String getEventId() { return eventId; }
    public String getAccountId() { return accountId; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getEventTimestamp() { return eventTimestamp; }
    public boolean isDuplicate() { return duplicate; }
    public void setDuplicate(boolean duplicate) { this.duplicate = duplicate; }
}

