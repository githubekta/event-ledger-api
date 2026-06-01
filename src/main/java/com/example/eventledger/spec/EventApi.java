package com.example.eventledger.spec;

import com.example.eventledger.dto.EventRequest;
import com.example.eventledger.dto.EventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Event API specification interface.
 * Controllers should implement this interface to ensure API contract compliance.
 */
@Tag(name = "Events", description = "Event Ledger API for financial transaction events")
public interface EventApi {

    /**
     * Submit a transaction event.
     * Idempotent - if eventId already exists, returns existing event.
     *
     * @param request the event request payload
     * @return 201 Created for new events, 200 OK for duplicate events
     */
    @PostMapping
    @Operation(
        summary = "Submit a transaction event",
        description = "Submit a new event. If eventId already exists, returns existing event (idempotent)"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Event created successfully",
            content = @Content(schema = @Schema(implementation = EventResponse.class))
        ),
        @ApiResponse(
            responseCode = "200",
            description = "Duplicate event - returning existing event",
            content = @Content(schema = @Schema(implementation = EventResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request"
        )
    })
    ResponseEntity<EventResponse> submitEvent(@Valid @RequestBody EventRequest request);

    /**
     * Get event by ID.
     *
     * @param eventId the event identifier
     * @return 200 OK with event if found, 404 Not Found otherwise
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get event by ID",
        description = "Retrieve a single event by its eventId"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Event found",
            content = @Content(schema = @Schema(implementation = EventResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Event not found"
        )
    })
    ResponseEntity<EventResponse> getEventById(
        @Parameter(description = "Event ID", required = true)
        @PathVariable("id") String eventId
    );

    /**
     * List events for an account.
     * Events are ordered by eventTimestamp ASC, then eventId ASC.
     *
     * @param accountId the account identifier
     * @param page page number (0-based), optional
     * @param size page size (default 20), optional
     * @return list of events or paginated events
     */
    @GetMapping
    @Operation(
        summary = "List events for an account",
        description = "List events for an account, ordered by eventTimestamp ASC, then eventId ASC"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Events retrieved successfully"
        )
    })
    ResponseEntity<?> getEventsByAccount(
        @Parameter(description = "Account ID", required = true)
        @RequestParam("account") String accountId,
        
        @Parameter(description = "Page number (0-based)")
        @RequestParam(value = "page", required = false) Integer page,
        
        @Parameter(description = "Page size (default 20)")
        @RequestParam(value = "size", required = false, defaultValue = "20") Integer size
    );
}

