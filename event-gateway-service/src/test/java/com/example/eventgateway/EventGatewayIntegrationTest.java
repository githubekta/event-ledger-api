package com.example.eventgateway;

import com.example.eventgateway.dto.EventRequest;
import com.example.eventgateway.enums.EventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventGatewayIntegrationTest {

    static final WireMockServer wm = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        wm.start();
        WireMock.configureFor("localhost", wm.port());
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @AfterAll
    void stop() { wm.stop(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("account-service.base-url", () -> "http://localhost:" + wm.port());
    }

    private EventRequest req(String id, String acct, EventType t, String amt, String iso) {
        EventRequest r = new EventRequest();
        r.setEventId(id);
        r.setAccountId(acct);
        r.setType(t);
        r.setAmount(new BigDecimal(amt));
        r.setCurrency("USD");
        r.setEventTimestamp(Instant.parse(iso));
        r.setMetadata(Map.of("source", "test"));
        return r;
    }

    private void stubAccountServiceOk() {
        wm.stubFor(WireMock.post(WireMock.urlPathMatching("/accounts/.+/transactions"))
                .willReturn(WireMock.aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json").withBody("{}")));
    }

    @Test
    void postValidEventReturns201AndPropagatesTrace() throws Exception {
        wm.resetAll();
        stubAccountServiceOk();

        mvc.perform(MockMvcRequestBuilders.post("/events").header("X-Trace-Id", "trace-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req("g-1", "acct-1", EventType.CREDIT, "150", "2026-05-15T10:00:00Z"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "trace-1"))
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        wm.verify(WireMock.postRequestedFor(WireMock.urlPathMatching("/accounts/acct-1/transactions"))
                .withHeader("X-Trace-Id", WireMock.equalTo("trace-1")));
    }

    @Test
    void duplicateReturns200AndDoesNotCallAccountServiceAgain() throws Exception {
        wm.resetAll();
        stubAccountServiceOk();

        EventRequest r = req("g-dup", "acct-2", EventType.CREDIT, "10", "2026-05-15T10:00:00Z");
        mvc.perform(MockMvcRequestBuilders.post("/events").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(r)))
                .andExpect(status().isCreated());
        mvc.perform(MockMvcRequestBuilders.post("/events").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(r)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE_ALREADY_PROCESSED"));

        wm.verify(1, WireMock.postRequestedFor(WireMock.urlPathMatching("/accounts/acct-2/transactions")));
    }

    @Test
    void validationErrorsReturn400() throws Exception {
        String bad = "{\"eventId\":\"\",\"accountId\":\"\",\"type\":\"CREDIT\",\"amount\":-1,\"currency\":\"\",\"eventTimestamp\":null}";
        mvc.perform(MockMvcRequestBuilders.post("/events").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownTypeReturns400() throws Exception {
        String bad = "{\"eventId\":\"x\",\"accountId\":\"a\",\"type\":\"FOO\",\"amount\":1,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T10:00:00Z\"}";
        mvc.perform(MockMvcRequestBuilders.post("/events").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void listingReturnsEventsOrderedByTimestampAsc() throws Exception {
        wm.resetAll();
        stubAccountServiceOk();

        mvc.perform(MockMvcRequestBuilders.post("/events").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req("ord-late", "acct-ord", EventType.CREDIT, "1", "2026-05-15T11:00:00Z"))))
                .andExpect(status().isCreated());
        mvc.perform(MockMvcRequestBuilders.post("/events").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req("ord-early", "acct-ord", EventType.CREDIT, "1", "2026-05-15T10:00:00Z"))))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.get("/events").param("account", "acct-ord"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("ord-early"))
                .andExpect(jsonPath("$[1].eventId").value("ord-late"));
    }

    @Test
    void getByIdReturns200And404() throws Exception {
        wm.resetAll();
        stubAccountServiceOk();

        mvc.perform(MockMvcRequestBuilders.post("/events").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req("get-1", "a", EventType.CREDIT, "1", "2026-05-15T10:00:00Z"))))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.get("/events/{id}", "get-1")).andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.get("/events/{id}", "missing")).andExpect(status().isNotFound());
    }
}

