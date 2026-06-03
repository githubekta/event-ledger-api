package com.example.eventgateway;

import com.example.eventgateway.client.AccountServiceClient;
import com.example.eventgateway.dto.EventRequest;
import com.example.eventgateway.entity.EventEntity;
import com.example.eventgateway.enums.EventType;
import com.example.eventgateway.exception.AccountServiceUnavailableException;
import com.example.eventgateway.repository.EventRepository;
import com.example.eventgateway.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock EventRepository repo;
    @Mock AccountServiceClient client;

    private final ObjectMapper mapper = new ObjectMapper();

    private EventService service() {
        return new EventService(repo, client, mapper);
    }

    private EventRequest req(String id) {
        EventRequest r = new EventRequest();
        r.setEventId(id);
        r.setAccountId("a1");
        r.setType(EventType.CREDIT);
        r.setAmount(new BigDecimal("10"));
        r.setCurrency("USD");
        r.setEventTimestamp(Instant.parse("2026-05-15T10:00:00Z"));
        return r;
    }

    @Test
    void duplicateDoesNotCallClient() {
        EventEntity existing = new EventEntity("e1", "a1", EventType.CREDIT,
                BigDecimal.TEN, "USD", Instant.parse("2026-05-15T10:00:00Z"), null, "PROCESSED");
        when(repo.findByEventId("e1")).thenReturn(Optional.of(existing));

        EventService.SubmissionResult r = service().submit(req("e1"));

        assertThat(r.duplicate()).isTrue();
        assertThat(r.response().getStatus()).isEqualTo(EventService.STATUS_DUPLICATE);
        verify(client, never()).postTransaction(any(), any());
        verify(repo, never()).save(any());
    }

    @Test
    void newEventSavesAndCallsClient() {
        when(repo.findByEventId("e2")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        EventService.SubmissionResult r = service().submit(req("e2"));

        assertThat(r.duplicate()).isFalse();
        assertThat(r.response().getStatus()).isEqualTo(EventService.STATUS_PROCESSED);
        verify(client).postTransaction(any(), any());
    }

    @Test
    void downstreamFailureMarksEventAndRethrows() {
        when(repo.findByEventId("e3")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        doThrow(new AccountServiceUnavailableException("down"))
                .when(client).postTransaction(any(), any());

        EventService svc = service();
        assertThatThrownBy(() -> svc.submit(req("e3")))
                .isInstanceOf(AccountServiceUnavailableException.class);

        assertThat(svc.getFailedAccountCalls()).isEqualTo(1);
    }
}

