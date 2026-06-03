package com.example.eventgateway.controller;

import com.example.eventgateway.dto.EventRequest;
import com.example.eventgateway.dto.EventResponse;
import com.example.eventgateway.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EventResponse> submit(@Valid @RequestBody EventRequest req) {
        EventService.SubmissionResult r = service.submit(req);
        return ResponseEntity
                .status(r.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(r.response());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getOne(@PathVariable String id) {
        return service.findByEventId(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<EventResponse> list(@RequestParam("account") String accountId) {
        return service.listByAccount(accountId);
    }
}

