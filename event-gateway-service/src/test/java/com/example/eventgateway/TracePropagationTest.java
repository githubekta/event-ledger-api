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

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TracePropagationTest {

    static final WireMockServer wm = new WireMockServer(wireMockConfig().dynamicPort());

    static { wm.start(); }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @AfterAll void stop() { wm.stop(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("account-service.base-url", () -> "http://localhost:" + wm.port());
    }

    @Test
    void incomingTraceIdIsPropagatedDownstream() throws Exception {
        wm.resetAll();
        wm.stubFor(WireMock.post(WireMock.urlPathMatching("/accounts/.+/transactions"))
                .willReturn(WireMock.aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json").withBody("{}")));

        EventRequest r = new EventRequest();
        r.setEventId("trace-evt-1");
        r.setAccountId("acct-trace");
        r.setType(EventType.CREDIT);
        r.setAmount(new BigDecimal("5"));
        r.setCurrency("USD");
        r.setEventTimestamp(Instant.parse("2026-05-15T10:00:00Z"));

        mvc.perform(MockMvcRequestBuilders.post("/events").header("X-Trace-Id", "trace-abc-123")
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(r)))
                .andExpect(status().isCreated());

        wm.verify(WireMock.postRequestedFor(WireMock.urlPathMatching("/accounts/acct-trace/transactions"))
                .withHeader("X-Trace-Id", WireMock.equalTo("trace-abc-123")));
    }
}

