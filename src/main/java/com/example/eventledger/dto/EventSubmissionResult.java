package com.example.eventledger.dto;

/**
 * Wrapper class to indicate whether the submitted event was a duplicate.
 */
public class EventSubmissionResult {

    private final EventResponse event;
    private final boolean duplicate;

    public EventSubmissionResult(EventResponse event, boolean duplicate) {
        this.event = event;
        this.duplicate = duplicate;
    }

    public EventResponse getEvent() {
        return event;
    }

    public boolean isDuplicate() {
        return duplicate;
    }
}

