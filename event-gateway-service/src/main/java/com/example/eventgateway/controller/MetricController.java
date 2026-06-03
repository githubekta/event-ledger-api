package com.example.eventgateway.controller;

import com.example.eventgateway.service.EventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/metrics")
public class MetricController {

    private final EventService service;

    public MetricController(EventService service) {
        this.service = service;
    }

    @GetMapping("/custom")
    public Map<String, Long> custom() {
        return Map.of(
                "totalRequests", service.getTotalRequests(),
                "successfulEvents", service.getSuccessfulEvents(),
                "duplicateEvents", service.getDuplicateEvents(),
                "failedAccountServiceCalls", service.getFailedAccountCalls()
        );
    }
}

