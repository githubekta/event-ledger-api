package com.example.eventledger.exception;

/**
 * Custom error codes for the Event Ledger API.
 */
public enum ErrorCode {

    // 4xx Client Errors
    EVENT_NOT_FOUND("ERR-001", "Event not found"),
    ACCOUNT_NOT_FOUND("ERR-002", "Account not found"),
    VALIDATION_ERROR("ERR-003", "Validation failed"),
    INVALID_REQUEST_BODY("ERR-004", "Invalid request body"),
    INVALID_EVENT_TYPE("ERR-005", "Invalid event type"),
    INVALID_TIMESTAMP_FORMAT("ERR-006", "Invalid timestamp format"),
    CURRENCY_MISMATCH("ERR-007", "Currency mismatch for account"),

    // 5xx Server Errors
    INTERNAL_ERROR("ERR-500", "Internal server error"),
    DATABASE_ERROR("ERR-501", "Database operation failed");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}

