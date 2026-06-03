# Event Ledger API

A production-quality **multi-service** take-home solution for ingesting financial
transaction events with **idempotency**, **out-of-order tolerance**,
**resilient inter-service communication**, **trace propagation**, and
**structured JSON logging**.

---

## Compliance Checklist

| # | Requirement | Where |
|---|---|---|
| 1 | Two independently runnable services | `account-service/`, `event-gateway-service/` |
| 2 | Separate H2 DB per service | `gatewaydb` / `accountdb` in `application.yml` |
| 3 | Gateway idempotency by `eventId` | `EventService.submit` + unique column on `EventEntity.eventId` |
| 4 | Account-service idempotency | `AccountService.recordTransaction` + unique column |
| 5 | Duplicates don't alter balance | `AccountControllerTest.duplicateEventIdReturns200AndIsNotAppliedTwice`, `AccountIntegrationTest` |
| 6 | Listings sorted by `eventTimestamp` ASC | `findByAccountIdOrderByEventTimestampAscEventIdAsc` |
| 7 | Balance = CREDIT − DEBIT (BigDecimal) | `AccountService.getBalance` |
| 8 | 503 when downstream unavailable | `AccountServiceUnavailableException` → `GlobalExceptionHandler` |
| 9 | Gateway GETs work when downstream is down | `ResiliencyTest.accountServiceDownReturns503AndKeepsGetEndpointsWorking` |
| 10 | `X-Trace-Id` generated + propagated | `TraceFilter` (both services) + `AccountServiceClient` |
| 11 | Both services log `traceId` in JSON | `logback-spring.xml` + `LogstashEncoder` |
| 12 | Custom metrics exposed | `/metrics/custom` on both services |
| 13 | Health endpoints | `/health` + `/actuator/health` on both |
| 14 | Resilience4j timeout + retry + CB | `application.yml` instance `accountService` |
| 15 | Tests for all areas | 8 test classes (unit / integration / repo / resiliency / trace) |
| 16 | Docker Compose | `docker-compose.yml` with `depends_on: service_healthy` |
| 17 | Production-quality README | this file |

---

## Architecture Overview

Two **independently runnable** Spring Boot services, each with its own H2 database
and its own lifecycle. The gateway is the only public-facing service; the account
service is internal.

```
                                  X-Trace-Id (propagated)
                                  ──────────────────────▶
   ┌─────────┐    HTTPS    ┌────────────────────────┐    HTTP (sync)    ┌────────────────────────┐
   │ client  │ ──────────▶ │ event-gateway-service  │ ────────────────▶ │   account-service      │
   │ (curl)  │             │   :8080                │                   │   :8081                │
   └─────────┘             │   H2: gatewaydb        │                   │   H2: accountdb        │
                           │                        │                   │                        │
                           │ • validate request     │                   │ • idempotent insert    │
                           │ • dedupe by eventId    │                   │   (unique eventId)     │
                           │ • persist locally      │                   │ • balance =            │
                           │ • forward to account   │                   │     ΣCREDIT − ΣDEBIT   │
                           │ • Resilience4j:        │                   │ • ordered by           │
                           │   timeout/retry/CB     │                   │   eventTimestamp ASC   │
                           └────────────────────────┘                   └────────────────────────┘
                                ▲                                            ▲
                                │ /health  /metrics/custom                   │ /health  /metrics/custom
```

| Service                 | Port | Public? | Database  | Responsibility                                                    |
|-------------------------|------|---------|-----------|-------------------------------------------------------------------|
| `event-gateway-service` | 8080 | yes     | gatewaydb | Validate, persist, deduplicate events; forward to Account Service |
| `account-service`       | 8081 | internal| accountdb | Persist transactions idempotently; compute balance                |

> The top-level `src/` directory is an earlier **single-module reference** kept
> only for history. The canonical implementation lives in `account-service/` and
> `event-gateway-service/`.

---

## Technology Stack

- **Java 21**, **Spring Boot 3.2.5** (Web, Data JPA, Validation, Actuator, AOP)
- **H2** in-memory database (one per service, no sharing)
- **Resilience4j 2.2** — `@CircuitBreaker`, `@Retry`, time-limiter
- **Logback** + `logstash-logback-encoder` — JSON logs with `traceId` MDC
- **JUnit 5**, **Mockito**, **MockMvc**, **WireMock 3.x**
- **Maven multi-module** build
- Multi-stage **Dockerfile** per service + **docker-compose**

---

## Project Layout

```
event-ledger-api/
├── pom.xml                       parent (modules: account-service, event-gateway-service)
├── docker-compose.yml
├── account-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/{main,test}/java/com/example/accountservice/
│       ├── AccountServiceApplication.java
│       ├── config/        TraceFilter
│       ├── controller/    AccountController, HealthController, MetricController
│       ├── dto/           TransactionRequest, TransactionResponse, BalanceResponse, ErrorResponse
│       ├── entity/        AccountTransactionEntity
│       ├── enums/         TransactionType
│       ├── exception/     GlobalExceptionHandler
│       ├── repository/    AccountTransactionRepository
│       └── service/       AccountService
└── event-gateway-service/
    ├── Dockerfile
    ├── pom.xml
    └── src/{main,test}/java/com/example/eventgateway/
        ├── EventGatewayApplication.java
        ├── client/        AccountServiceClient        (@CircuitBreaker + @Retry)
        ├── config/        TraceFilter, LoggingFilter, RestClientConfig, ResilienceConfig
        ├── controller/    EventController, HealthController, MetricController
        ├── dto/           EventRequest, EventResponse, AccountTransactionRequest, ErrorResponse
        ├── entity/        EventEntity
        ├── enums/         EventType
        ├── exception/     GlobalExceptionHandler, AccountServiceUnavailableException, DuplicateEventException
        ├── repository/    EventRepository
        └── service/       EventService
```

---

## Quick Start

### Docker Compose (recommended)

```bash
docker compose up --build
```

`docker-compose.yml` builds both images, starts `account-service` first, then
starts `event-gateway-service` with `ACCOUNT_SERVICE_BASE_URL=http://account-service:8081`.

```bash
curl http://localhost:8080/health       # gateway
curl http://localhost:8081/health       # account-service
```

### Run manually (two terminals)

```bash
# terminal 1
cd account-service       && mvn spring-boot:run

# terminal 2
cd event-gateway-service && mvn spring-boot:run
```

Override the downstream URL with the env var if needed:

```bash
ACCOUNT_SERVICE_BASE_URL=http://localhost:8081 mvn spring-boot:run
```

### Run tests

```bash
mvn test                                # both modules via parent pom
mvn -pl account-service        test
mvn -pl event-gateway-service  test
```

---

## API Reference

### Gateway — `:8080`

| Method | Path                              | Status codes                              |
|--------|-----------------------------------|-------------------------------------------|
| POST   | `/events`                         | 201 new · 200 duplicate · 400 invalid · 503 downstream down |
| GET    | `/events/{id}`                    | 200 · 404                                 |
| GET    | `/events?account={accountId}`     | 200 (sorted by `eventTimestamp` ASC)      |
| GET    | `/health`                         | 200                                       |
| GET    | `/metrics/custom`                 | 200                                       |

### Account Service — `:8081`

| Method | Path                                       | Status codes      |
|--------|--------------------------------------------|-------------------|
| POST   | `/accounts/{accountId}/transactions`       | 201 · 200 · 400   |
| GET    | `/accounts/{accountId}/balance`            | 200               |
| GET    | `/accounts/{accountId}`                    | 200 (balance + recent transactions) |
| GET    | `/health`                                  | 200               |
| GET    | `/metrics/custom`                          | 200               |

### Sample curl

```bash
# Submit (first time → 201, status PROCESSED)
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: demo-trace-1" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z",
    "metadata": { "source": "mainframe-batch", "batchId": "B-9042" }
  }'

# Re-submit (idempotent → 200, status DUPLICATE_ALREADY_PROCESSED)
curl -X POST http://localhost:8080/events -H "Content-Type: application/json" \
  -d '{ "eventId":"evt-001", "accountId":"acct-123", "type":"CREDIT",
        "amount":150.00, "currency":"USD", "eventTimestamp":"2026-05-15T14:02:11Z" }'

# Lookups
curl http://localhost:8080/events/evt-001
curl "http://localhost:8080/events?account=acct-123"

# Balance
curl http://localhost:8081/accounts/acct-123/balance
# → { "accountId":"acct-123", "balance":150.0000, "currency":"USD" }

# Metrics
curl http://localhost:8080/metrics/custom
curl http://localhost:8081/metrics/custom
```

---

## Design Decisions

### 1. Idempotency
Both services treat `eventId` as a **unique column**:

1. `findByEventId(eventId)` — if present, return existing record (no side effects).
2. Otherwise `save(...)`.
3. On `DataIntegrityViolationException` (concurrent insert), re-read the winner.

A duplicate POST cannot mutate balance — **10 retries change the balance once**.

### 2. Out-of-order handling
All list queries use `ORDER BY eventTimestamp ASC, eventId ASC`. Balance is a fold
over all stored transactions, so it is independent of arrival order.

Example: receive `evt-2 @ 11:00` first, then `evt-1 @ 10:00`; the listing endpoint
still returns `evt-1, evt-2` and the balance is mathematically correct.

### 3. Balance computation
```
balance = Σ amount where type = CREDIT  −  Σ amount where type = DEBIT
```
`BigDecimal` is used everywhere — **never `double`**.

### 4. Trace propagation
- A servlet `TraceFilter` (highest precedence) reads the `X-Trace-Id` header, or
  generates a UUID if absent.
- The id is placed in the SLF4J **MDC** under key `traceId`, so every log line carries it.
- The same id is echoed back on the response header.
- `AccountServiceClient` pulls the id from MDC and forwards it as `X-Trace-Id` on
  the outbound HTTP call.
- The account-service `TraceFilter` reads it back — **one trace id spans both services**.

### 5. Structured logging
`logback-spring.xml` uses `LogstashEncoder` to emit one JSON object per log line:

```json
{
  "@timestamp": "2026-05-15T14:02:11.123Z",
  "level": "INFO",
  "service": "event-gateway-service",
  "traceId": "demo-trace-1",
  "logger_name": "com.example.eventgateway.service.EventService",
  "message": "Event accepted eventId=evt-001 accountId=acct-123"
}
```

The matching `traceId` across services lets you grep one log query to trace a
single request end-to-end.

### 6. Resiliency (Gateway → Account Service)

Declared in `application.yml` under instance name `accountService`.

| Concern         | Setting                                                              |
|-----------------|----------------------------------------------------------------------|
| Timeout         | 1 s connect, 2 s read on RestTemplate; Resilience4j time-limiter 2 s |
| Retry           | max 3 attempts, exponential backoff 500 ms × 2                       |
| Circuit breaker | sliding window 5, threshold 50 %, open 10 s, 2 half-open calls       |

**Why all three?**
- **Timeout** — bounds tail latency so a hung downstream cannot block gateway threads.
- **Retry** — absorbs transient blips (GC pause, brief network glitch) without surfacing them.
- **Circuit breaker** — once the downstream is *consistently* failing, stops retry storms and fails fast.

### 7. Graceful degradation when Account Service is down

| Behaviour | Result |
|---|---|
| `POST /events` while account-service is unhealthy | **`503 ACCOUNT_SERVICE_UNAVAILABLE`** with a clear JSON body and the same `traceId` |
| Local event row | Still persisted with `status = ACCOUNT_SERVICE_FAILED` (audit trail; replayable later) |
| `GET /events/{id}` and `GET /events?account=...` | **Keep working** — they never touch the downstream |
| `GET /health` | Keeps returning 200 with DB status |
| Repeat failures | Circuit breaker opens after 50 % failure across 5 calls → traffic short-circuits for 10 s before testing recovery (2 half-open probes) |

> A DB transaction is **deliberately not** held across the network call in
> `EventService.submit(...)`. Holding a JPA tx across a synchronous HTTP hop is
> an anti-pattern that ties up connections during slow downstream responses.

### 8. Validation & error model
Bean Validation on every request DTO. A `@RestControllerAdvice` returns:

```json
{
  "timestamp": "2026-06-03T11:22:33Z",
  "traceId": "demo-trace-1",
  "status": 400,
  "errorCode": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "details": ["amount: amount must be greater than 0"]
}
```

| `errorCode`                   | HTTP |
|-------------------------------|------|
| `VALIDATION_FAILED`           | 400  |
| `INVALID_REQUEST_BODY`        | 400  |
| `ACCOUNT_SERVICE_UNAVAILABLE` | 503  |
| `INTERNAL_ERROR`              | 500  |

### 9. Custom metrics
`GET /metrics/custom` exposes plain counters maintained by the service layer:

- **Gateway:** `totalRequests`, `successfulEvents`, `duplicateEvents`, `failedAccountServiceCalls`
- **Account:** `totalTransactions`, `duplicateTransactions`, `balanceQueries`

Spring Boot Actuator is also enabled at `/actuator/health`,
`/actuator/circuitbreakers`, `/actuator/metrics`.

---

## Tests

| Module          | Class                          | What it proves                                                          |
|-----------------|--------------------------------|-------------------------------------------------------------------------|
| account-service | `AccountServiceTest`           | new vs duplicate, race recovery, CREDIT − DEBIT math, empty account     |
| account-service | `AccountControllerTest`        | 201/200 idempotency, out-of-order ordering, validation 400, trace echo  |
| account-service | `AccountTransactionRepositoryTest` (`@DataJpaTest`) | unique-eventId constraint, sort, `BigDecimal`+enum mapping |
| account-service | `AccountIntegrationTest`       | combined end-to-end: CREDIT + DEBIT + duplicate + out-of-order → balance & order correct |
| gateway         | `EventServiceTest`             | duplicate skips downstream; new path persists+forwards; 503 path        |
| gateway         | `EventGatewayIntegrationTest`  | end-to-end POST via WireMock, dedup count, ordering, GET 200/404, validation, unknown enum |
| gateway         | `TracePropagationTest`         | incoming `X-Trace-Id` reaches downstream verbatim                       |
| gateway         | `ResiliencyTest`               | downstream 5xx → 503 to client, GET endpoints stay up, retries observed |

```bash
mvn test
```

### Example test scenarios

1. **Idempotent retry** — POST `evt-001` twice; expect `201` then `200`; `GET /accounts/acct-123/balance` includes the amount **once**.
2. **Out-of-order arrival** — POST event with timestamp `11:00`, then with `10:00`; `GET /events?account=...` returns the `10:00` row first.
3. **Balance math** — CREDIT 150, DEBIT 40, CREDIT 10 → balance `120`.
4. **Validation** — missing `eventId`, `amount <= 0`, unknown `type`, missing `accountId` all return `400 VALIDATION_FAILED` (or `400 INVALID_REQUEST_BODY` for bad enum).
5. **Trace propagation** — POST with header `X-Trace-Id: trace-abc-123`; WireMock asserts the downstream call carried the same header.
6. **Downstream outage** — stub account-service to return 500; gateway returns `503 ACCOUNT_SERVICE_UNAVAILABLE`, and `GET /events?account=...` still returns 200.
7. **Retry behaviour** — under a 500 response, the gateway makes ≥ 2 attempts before giving up (verified via WireMock request count).

---

## Example logs with a shared trace id

```json
{"@timestamp":"2026-06-03T11:22:33.456Z","level":"INFO","service":"event-gateway-service","traceId":"demo-trace-1","logger_name":"c.e.eventgateway.service.EventService","message":"Event accepted eventId=evt-001 accountId=acct-123"}
{"@timestamp":"2026-06-03T11:22:33.461Z","level":"INFO","service":"account-service","traceId":"demo-trace-1","logger_name":"c.e.accountservice.service.AccountService","message":"Recorded transaction eventId=evt-001 accountId=acct-123 type=CREDIT amount=150.00"}
```

---

## Suggested commit history

1. `chore: maven multi-module skeleton`
2. `feat(account-service): entity, repo and POST/GET APIs`
3. `feat(gateway): event entity, repo and APIs`
4. `feat(gateway): RestTemplate client to Account Service`
5. `feat: idempotency + out-of-order handling in both services`
6. `feat: X-Trace-Id filter, MDC propagation, JSON logs`
7. `feat(gateway): resilience4j retry, timeout, circuit breaker`
8. `feat: /health and /metrics/custom in both services`
9. `chore: Dockerfiles and docker-compose`
10. `test: unit + integration + WireMock + resiliency tests`
11. `docs: README with architecture, design notes, run instructions`

---

## Future Improvements

- **OpenTelemetry Collector + Jaeger** — replace homegrown `X-Trace-Id` with W3C `traceparent` and visualise spans.
- **Prometheus + Grafana** scraping `/actuator/prometheus` for dashboards and alerts.
- **Async fallback queue** (Kafka outbox / RabbitMQ) so a slow account-service never blocks event ingestion; gateway writes the event + outbox row in one local tx and a worker drains it.
- **Consumer-driven contract tests** (Pact / Spring Cloud Contract) between gateway and account-service.
- **Rate limiting** per source system (Bucket4j or at an API gateway).
- Replace H2 with **Postgres + Flyway** for production parity.
- **mTLS or short-lived service tokens** on the gateway ↔ account hop.
- **Schema versioning** on `EventRequest` (e.g. `schemaVersion` field) for safe payload evolution.

