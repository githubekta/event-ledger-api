package com.example.eventgateway.client;

import com.example.eventgateway.config.TraceFilter;
import com.example.eventgateway.dto.AccountTransactionRequest;
import com.example.eventgateway.exception.AccountServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AccountServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceClient.class);
    public static final String CB_NAME = "accountService";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AccountServiceClient(RestTemplate accountServiceRestTemplate,
                                @Value("${account-service.base-url}") String baseUrl) {
        this.restTemplate = accountServiceRestTemplate;
        this.baseUrl = baseUrl;
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallback")
    @Retry(name = CB_NAME)
    public void postTransaction(String accountId, AccountTransactionRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String trace = MDC.get(TraceFilter.MDC_KEY);
        if (trace != null) {
            headers.add(TraceFilter.HEADER, trace);
        }
        String url = baseUrl + "/accounts/" + accountId + "/transactions";
        log.info("Calling Account Service POST {}", url);
        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);
    }

    @SuppressWarnings("unused")
    private void fallback(String accountId, AccountTransactionRequest body, Throwable t) {
        log.warn("Account Service unavailable for accountId={} eventId={} cause={}",
                accountId, body.getEventId(), t.toString());
        if (t instanceof ResourceAccessException
                || t instanceof CallNotPermittedException
                || t instanceof RestClientException) {
            throw new AccountServiceUnavailableException(
                    "Account Service is currently unavailable", t);
        }
        throw new AccountServiceUnavailableException("Account Service call failed: " + t.getMessage(), t);
    }
}

