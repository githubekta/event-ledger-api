package com.example.eventgateway.dto;

import com.example.eventgateway.enums.EventType;
import java.math.BigDecimal;
import java.time.Instant;

public class EventResponse {
    private String eventId;
    private String accountId;
    private EventType type;
    private BigDecimal amount;
    private String currency;
    private Instant eventTimestamp;
    private String status;
    private Instant createdAt;
    private String message;

    public EventResponse() {}

    public EventResponse(String eventId, String accountId, EventType type, BigDecimal amount,
                         String currency, Instant eventTimestamp, String status,
                         Instant createdAt, String message) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
        this.status = status;
        this.createdAt = createdAt;
        this.message = message;
    }

    public String getEventId() { return eventId; }
    public String getAccountId() { return accountId; }
    public EventType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getEventTimestamp() { return eventTimestamp; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getMessage() { return message; }
}

