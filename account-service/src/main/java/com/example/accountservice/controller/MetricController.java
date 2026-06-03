package com.example.accountservice.controller;

import com.example.accountservice.service.AccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/metrics")
public class MetricController {

    private final AccountService service;

    public MetricController(AccountService service) {
        this.service = service;
    }

    @GetMapping("/custom")
    public Map<String, Long> custom() {
        return Map.of(
                "totalTransactions", service.getTotalTransactions(),
                "duplicateTransactions", service.getDuplicateTransactions(),
                "balanceQueries", service.getBalanceQueries()
        );
    }
}

