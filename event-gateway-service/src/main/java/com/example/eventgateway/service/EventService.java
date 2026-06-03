package com.example.eventgateway.service;

import com.example.eventgateway.client.AccountServiceClient;
import com.example.eventgateway.dto.AccountTransactionRequest;
import com.example.eventgateway.dto.EventRequest;
import com.example.eventgateway.dto.EventResponse;
import com.example.eventgateway.entity.EventEntity;
import com.example.eventgateway.exception.AccountServiceUnavailableException;
import com.example.eventgateway.repository.EventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates incoming event submission.
 *
 * <p>Idempotency: looks up by {@code eventId} first; if present, returns the original
 * stored event (the new payload is ignored). On a race a {@link DataIntegrityViolationException}
 * is caught and the winning row is re-read.
 *
 * <p>Note on transactions: Spring Data's {@code save/findById/findByEventId} each run in
 * their own short transaction, which is enough here. We deliberately do <strong>not</strong>
 * wrap {@link #submit} in {@code @Transactional} because it performs a synchronous HTTP
 * call to Account Service, and holding a DB transaction across a network call is an anti-pattern.
 */
@Service
public class EventService {

    public static final String STATUS_PROCESSED = "PROCESSED";
    public static final String STATUS_ACCOUNT_FAILED = "ACCOUNT_SERVICE_FAILED";
    public static final String STATUS_DUPLICATE = "DUPLICATE_ALREADY_PROCESSED";
    public static final String STATUS_RECEIVED = "RECEIVED";

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository repository;
    private final AccountServiceClient accountClient;
    private final ObjectMapper objectMapper;

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong successfulEvents = new AtomicLong();
    private final AtomicLong duplicateEvents = new AtomicLong();
    private final AtomicLong failedAccountCalls = new AtomicLong();

    public EventService(EventRepository repository, AccountServiceClient accountClient,
                        ObjectMapper objectMapper) {
        this.repository = repository;
        this.accountClient = accountClient;
        this.objectMapper = objectMapper;
    }

    public SubmissionResult submit(EventRequest req) {
        totalRequests.incrementAndGet();

        Optional<EventEntity> existing = repository.findByEventId(req.getEventId());
        if (existing.isPresent()) {
            duplicateEvents.incrementAndGet();
            log.info("Duplicate event eventId={}", req.getEventId());
            return new SubmissionResult(toResponse(existing.get(), STATUS_DUPLICATE), true);
        }

        EventEntity saved = saveNew(req);

        try {
            accountClient.postTransaction(req.getAccountId(),
                    new AccountTransactionRequest(req.getEventId(), req.getType(),
                            req.getAmount(), req.getCurrency(), req.getEventTimestamp()));
            updateStatus(saved, STATUS_PROCESSED);
            successfulEvents.incrementAndGet();
            log.info("Event accepted eventId={} accountId={}", saved.getEventId(), saved.getAccountId());
            return new SubmissionResult(toResponse(saved, STATUS_PROCESSED), false);
        } catch (AccountServiceUnavailableException e) {
            failedAccountCalls.incrementAndGet();
            updateStatus(saved, STATUS_ACCOUNT_FAILED);
            log.warn("Event eventId={} marked {} due to downstream failure",
                    saved.getEventId(), STATUS_ACCOUNT_FAILED);
            throw e;
        }
    }

    private EventEntity saveNew(EventRequest req) {
        try {
            String meta = req.getMetadata() == null
                    ? null
                    : objectMapper.writeValueAsString(req.getMetadata());
            EventEntity e = new EventEntity(req.getEventId(), req.getAccountId(), req.getType(),
                    req.getAmount(), req.getCurrency(), req.getEventTimestamp(), meta, STATUS_RECEIVED);
            return repository.save(e);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid metadata: " + ex.getMessage(), ex);
        } catch (DataIntegrityViolationException ex) {
            // race: another thread inserted the same eventId
            return repository.findByEventId(req.getEventId()).orElseThrow(() -> ex);
        }
    }

    private void updateStatus(EventEntity entity, String status) {
        entity.setStatus(status);
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public Optional<EventResponse> findByEventId(String eventId) {
        return repository.findByEventId(eventId).map(e -> toResponse(e, e.getStatus()));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listByAccount(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampAscEventIdAsc(accountId)
                .stream().map(e -> toResponse(e, e.getStatus())).toList();
    }

    private EventResponse toResponse(EventEntity e, String status) {
        String msg = STATUS_DUPLICATE.equals(status) ? "Event already processed" : null;
        return new EventResponse(e.getEventId(), e.getAccountId(), e.getType(), e.getAmount(),
                e.getCurrency(), e.getEventTimestamp(), status, e.getCreatedAt(), msg);
    }

    public long getTotalRequests() { return totalRequests.get(); }
    public long getSuccessfulEvents() { return successfulEvents.get(); }
    public long getDuplicateEvents() { return duplicateEvents.get(); }
    public long getFailedAccountCalls() { return failedAccountCalls.get(); }

    public record SubmissionResult(EventResponse response, boolean duplicate) {}
}

