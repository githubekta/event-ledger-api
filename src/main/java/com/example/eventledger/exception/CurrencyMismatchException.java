package com.example.eventledger.exception;

public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String accountId, String existingCurrency, String newCurrency) {
        super(String.format("Account %s has existing events in %s, cannot add event with %s",
                accountId, existingCurrency, newCurrency));
    }
}

