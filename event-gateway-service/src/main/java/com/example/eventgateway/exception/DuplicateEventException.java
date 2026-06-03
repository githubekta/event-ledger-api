package com.example.eventgateway.exception;

public class DuplicateEventException extends RuntimeException {
    public DuplicateEventException(String msg) { super(msg); }
}

