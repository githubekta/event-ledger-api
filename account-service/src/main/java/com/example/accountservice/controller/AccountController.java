package com.example.accountservice.controller;

import com.example.accountservice.dto.BalanceResponse;
import com.example.accountservice.dto.TransactionRequest;
import com.example.accountservice.dto.TransactionResponse;
import com.example.accountservice.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> recordTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody TransactionRequest request) {
        TransactionResponse resp = service.recordTransaction(accountId, request);
        return ResponseEntity
                .status(resp.isDuplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(resp);
    }

    @GetMapping("/{accountId}/balance")
    public BalanceResponse balance(@PathVariable String accountId) {
        return service.getBalance(accountId);
    }

    @GetMapping("/{accountId}")
    public Map<String, Object> accountDetails(@PathVariable String accountId) {
        BalanceResponse bal = service.getBalance(accountId);
        List<TransactionResponse> txs = service.listTransactions(accountId);
        return Map.of(
                "accountId", accountId,
                "balance", bal.getBalance(),
                "currency", bal.getCurrency(),
                "transactions", txs
        );
    }
}

