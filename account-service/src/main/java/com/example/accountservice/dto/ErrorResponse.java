package com.example.accountservice.dto;

import java.time.Instant;
import java.util.List;

public class ErrorResponse {
    private Instant timestamp;
    private String traceId;
    private int status;
    private String errorCode;
    private String message;
    private List<String> details;

    public ErrorResponse() {}

    public ErrorResponse(String traceId, int status, String errorCode, String message, List<String> details) {
        this.timestamp = Instant.now();
        this.traceId = traceId;
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
        this.details = details;
    }

    public Instant getTimestamp() { return timestamp; }
    public String getTraceId() { return traceId; }
    public int getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public List<String> getDetails() { return details; }
}

