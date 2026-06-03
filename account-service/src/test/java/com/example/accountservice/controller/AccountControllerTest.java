package com.example.accountservice.controller;

import com.example.accountservice.dto.TransactionRequest;
import com.example.accountservice.enums.TransactionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private TransactionRequest tx(String id, TransactionType t, String amt, String iso) {
        TransactionRequest r = new TransactionRequest();
        r.setEventId(id);
        r.setType(t);
        r.setAmount(new BigDecimal(amt));
        r.setCurrency("USD");
        r.setEventTimestamp(Instant.parse(iso));
        return r;
    }

    @Test
    void creditAndDebitProduceCorrectBalance() throws Exception {
        String acct = "acct-ct-1";
        mvc.perform(post("/accounts/{a}/transactions", acct).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(tx("ct-1", TransactionType.CREDIT, "150", "2026-05-15T10:00:00Z"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/accounts/{a}/transactions", acct).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(tx("ct-2", TransactionType.DEBIT, "50", "2026-05-15T11:00:00Z"))))
                .andExpect(status().isCreated());

        mvc.perform(get("/accounts/{a}/balance", acct))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.0))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void duplicateEventIdReturns200AndIsNotAppliedTwice() throws Exception {
        String acct = "acct-ct-2";
        TransactionRequest r = tx("ct-dup", TransactionType.CREDIT, "75", "2026-05-15T10:00:00Z");

        mvc.perform(post("/accounts/{a}/transactions", acct).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(r))).andExpect(status().isCreated());
        mvc.perform(post("/accounts/{a}/transactions", acct).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(r))).andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        mvc.perform(get("/accounts/{a}/balance", acct))
                .andExpect(jsonPath("$.balance").value(75.0));
    }

    @Test
    void outOfOrderArrivalStillCorrect() throws Exception {
        String acct = "acct-ct-3";
        mvc.perform(post("/accounts/{a}/transactions", acct).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(tx("oo-2", TransactionType.DEBIT, "20", "2026-05-15T11:00:00Z"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/accounts/{a}/transactions", acct).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(tx("oo-1", TransactionType.CREDIT, "100", "2026-05-15T10:00:00Z"))))
                .andExpect(status().isCreated());

        mvc.perform(get("/accounts/{a}", acct))
                .andExpect(jsonPath("$.balance").value(80.0))
                .andExpect(jsonPath("$.transactions[0].eventId").value("oo-1"))
                .andExpect(jsonPath("$.transactions[1].eventId").value("oo-2"));
    }

    @Test
    void validationFailureReturns400() throws Exception {
        String body = "{\"eventId\":\"\",\"type\":\"CREDIT\",\"amount\":0,\"currency\":\"\",\"eventTimestamp\":null}";
        mvc.perform(post("/accounts/{a}/transactions", "x").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void traceIdHeaderPropagatedBack() throws Exception {
        mvc.perform(get("/accounts/{a}/balance", "no-such").header("X-Trace-Id", "trace-xyz"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-xyz"));
    }
}

