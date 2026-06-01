package com.example.eventledger.controller;

import com.example.eventledger.dto.BalanceResponse;
import com.example.eventledger.service.BalanceService;
import com.example.eventledger.spec.AccountApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller implementation for Account-related endpoints.
 * Implements the {@link AccountApi} interface to ensure API contract consistency.
 * All endpoint mappings and OpenAPI documentation are defined in the interface.
 */
@RestController
public class AccountController implements AccountApi {

    private final BalanceService balanceService;

    public AccountController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @Override
    public ResponseEntity<BalanceResponse> getAccountBalance(String accountId) {
        BalanceResponse balance = balanceService.getBalance(accountId);
        return ResponseEntity.ok(balance);
    }
}

