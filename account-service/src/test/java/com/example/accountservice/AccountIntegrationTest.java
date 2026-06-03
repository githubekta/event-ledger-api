package com.example.accountservice;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Single end-to-end scenario combining the four account-service invariants:
 *   1. CREDIT/DEBIT math
 *   2. Duplicate eventId is idempotent
 *   3. Out-of-order arrival still produces correct chronological listing
 *   4. Balance is independent of insert order
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountIntegrationTest {

    private static final String ACCT = "acct-e2e";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private TransactionRequest req(String id, TransactionType type, String amt, String iso) {
        TransactionRequest r = new TransactionRequest();
        r.setEventId(id);
        r.setType(type);
        r.setAmount(new BigDecimal(amt));
        r.setCurrency("USD");
        r.setEventTimestamp(Instant.parse(iso));
        return r;
    }

    private void submit(TransactionRequest r, int expectedStatus) throws Exception {
        mvc.perform(post("/accounts/{a}/transactions", ACCT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(r)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void endToEndCreditDebitDuplicateOutOfOrder() throws Exception {
        // arrive LATE first (out-of-order)
        submit(req("e2e-debit",   TransactionType.DEBIT,  "50",  "2026-05-15T11:00:00Z"), 201);
        // then EARLY
        submit(req("e2e-credit1", TransactionType.CREDIT, "150", "2026-05-15T10:00:00Z"), 201);
        // duplicate of e2e-debit -> 200, NO balance change
        submit(req("e2e-debit",   TransactionType.DEBIT,  "50",  "2026-05-15T11:00:00Z"), 200);
        // another credit later
        submit(req("e2e-credit2", TransactionType.CREDIT, "100", "2026-05-15T12:00:00Z"), 201);

        // balance = 150 + 100 - 50 = 200 (debit counted once)
        mvc.perform(get("/accounts/{a}/balance", ACCT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(200.0))
                .andExpect(jsonPath("$.currency").value("USD"));

        // listing must be in eventTimestamp ASC order: credit1 (10:00), debit (11:00), credit2 (12:00)
        mvc.perform(get("/accounts/{a}", ACCT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(200.0))
                .andExpect(jsonPath("$.transactions[0].eventId").value("e2e-credit1"))
                .andExpect(jsonPath("$.transactions[1].eventId").value("e2e-debit"))
                .andExpect(jsonPath("$.transactions[2].eventId").value("e2e-credit2"))
                // duplicate stored only once
                .andExpect(jsonPath("$.transactions.length()").value(3));

        // custom metrics reflect the duplicate
        mvc.perform(get("/metrics/custom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateTransactions").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }
}

