package com.example.eventgateway.exception;

public class AccountServiceUnavailableException extends RuntimeException {
    public AccountServiceUnavailableException(String msg, Throwable cause) { super(msg, cause); }
    public AccountServiceUnavailableException(String msg) { super(msg); }
}

