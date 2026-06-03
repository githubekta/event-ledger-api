package com.example.eventgateway;

import com.example.eventgateway.dto.EventRequest;
import com.example.eventgateway.enums.EventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResiliencyTest {

    static final WireMockServer wm = new WireMockServer(wireMockConfig().dynamicPort());

    static { wm.start(); }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @AfterAll void stop() { wm.stop(); }
    @BeforeEach void reset() { wm.resetAll(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("account-service.base-url", () -> "http://localhost:" + wm.port());
    }

    private String body(String id) throws Exception {
        EventRequest r = new EventRequest();
        r.setEventId(id);
        r.setAccountId("acct-r");
        r.setType(EventType.CREDIT);
        r.setAmount(new BigDecimal("10"));
        r.setCurrency("USD");
        r.setEventTimestamp(Instant.parse("2026-05-15T10:00:00Z"));
        return om.writeValueAsString(r);
    }

    @Test
    void accountServiceDownReturns503AndKeepsGetEndpointsWorking() throws Exception {
        wm.stubFor(WireMock.post(WireMock.urlPathMatching("/accounts/.+/transactions"))
                .willReturn(WireMock.aResponse().withStatus(500)));

        mvc.perform(MockMvcRequestBuilders.post("/events")
                        .contentType(MediaType.APPLICATION_JSON).content(body("r-1")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_SERVICE_UNAVAILABLE"));

        // GET endpoints must still work even when downstream is unhealthy
        mvc.perform(MockMvcRequestBuilders.get("/events").param("account", "acct-r"))
                .andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.get("/health")).andExpect(status().isOk());
    }

    @Test
    void retriesOnFailureBeforeGivingUp() throws Exception {
        wm.stubFor(WireMock.post(WireMock.urlPathMatching("/accounts/.+/transactions"))
                .willReturn(WireMock.aResponse().withStatus(500)));

        mvc.perform(MockMvcRequestBuilders.post("/events")
                        .contentType(MediaType.APPLICATION_JSON).content(body("r-retry")))
                .andExpect(status().isServiceUnavailable());

        // Resilience4j retry: expect more than 1 attempt (maxAttempts=3)
        int count = wm.findAll(WireMock.postRequestedFor(
                WireMock.urlPathMatching("/accounts/.+/transactions"))).size();
        org.assertj.core.api.Assertions.assertThat(count).isGreaterThanOrEqualTo(2);
    }
}

