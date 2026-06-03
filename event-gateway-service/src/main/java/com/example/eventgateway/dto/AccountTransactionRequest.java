
    public AccountTransactionRequest() {}

    public AccountTransactionRequest(String eventId, EventType type, BigDecimal amount,
                                     String currency, Instant eventTimestamp) {
        this.eventId = eventId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
    }

    public String getEventId() { return eventId; }
    public EventType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getEventTimestamp() { return eventTimestamp; }
}
package com.example.eventgateway.dto;

import com.example.eventgateway.enums.EventType;
import java.math.BigDecimal;
import java.time.Instant;

public class AccountTransactionRequest {
    private String eventId;
    private EventType type;
    private BigDecimal amount;
    private String currency;
    private Instant eventTimestamp;

