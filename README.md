# Event Ledger API

A production-quality REST API for managing financial transaction events with built-in idempotency, out-of-order event handling, and balance computation.

## Problem Summary

This API receives financial transaction events (credits and debits) from upstream systems. Events may arrive **out of order** and may be delivered **more than once**. The API ensures:

- **Idempotency**: Duplicate events are safely rejected without affecting balances
- **Chronological ordering**: Events are always returned in timestamp order
- **Accurate balance calculation**: Balance is computed correctly regardless of event arrival order
- **Input validation**: Comprehensive validation with clear error messages

---

## Tech Stack

| Technology | Purpose |
|------------|---------|
| Java 21 | Runtime |
| Spring Boot 3.2.5 | Framework |
| Spring Web | REST API |
| Spring Data JPA | Data persistence |
| H2 Database | In-memory storage |
| Bean Validation | Input validation |
| SpringDoc OpenAPI | API documentation |
| JUnit 5 + MockMvc | Testing |
| Docker | Containerization |

---

## Quick Start

### Prerequisites
- Java 21
- Maven 3.8+

### Run the Application
```bash
mvn spring-boot:run
```
The API starts at `http://localhost:8080`

### Run Tests
```bash
mvn test
```

### Build and Run JAR
```bash
mvn clean package -DskipTests
java -jar target/event-ledger-api-1.0.0-SNAPSHOT.jar
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/events` | Submit a transaction event (idempotent) |
| GET | `/events/{id}` | Retrieve a single event |
| GET | `/events?account={accountId}` | List events for account (chronological) |
| GET | `/accounts/{accountId}/balance` | Get computed balance |

### Pagination (optional)
```
GET /events?account=acct-123&page=0&size=10
```

---

## Example curl Commands

```bash
# Submit a CREDIT event
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z"
  }'

# Get event by ID
curl http://localhost:8080/events/evt-001

# List events for account
curl "http://localhost:8080/events?account=acct-123"

# Get account balance
curl http://localhost:8080/accounts/acct-123/balance
```

---

## Design Decisions

### 1. Idempotency via Primary Key
- **`eventId` is the database primary key** with a unique constraint
- First POST → `201 Created`
- Duplicate POST → `200 OK` (returns original event, no balance impact)
- **Race condition protection**: If two concurrent requests try to insert the same eventId, the database constraint prevents duplicates. The service catches `DataIntegrityViolationException` and returns the existing event.

### 2. Out-of-Order Event Handling
- Events are stored with their original `eventTimestamp`
- Queries always use `ORDER BY eventTimestamp ASC, eventId ASC`
- Balance calculation iterates over events in timestamp order
- **Result**: Events submitted in any order are always returned chronologically

### 3. Balance Calculation
```
Balance = Σ(CREDIT amounts) - Σ(DEBIT amounts)
```
- Uses `BigDecimal` for precise decimal arithmetic (not `double`)
- Fetches all events for account and computes balance in Java
- Currency consistency is enforced (mixed currencies rejected with `400 Bad Request`)

### 4. Validation with Global Exception Handler
- Bean Validation annotations on DTOs (`@NotBlank`, `@NotNull`, `@DecimalMin`)
- `GlobalExceptionHandler` provides consistent error responses with custom error codes
- Invalid requests return `400 Bad Request` with descriptive messages

### 5. Clean Architecture
```
controller/  → REST endpoints (thin layer)
service/     → Business logic
repository/  → Data access
dto/         → Request/Response objects
entity/      → JPA entities
exception/   → Custom exceptions + global handler
```

---

## Error Codes

| Code | Description |
|------|-------------|
| ERR-001 | Event not found |
| ERR-003 | Validation failed |
| ERR-005 | Invalid event type (must be CREDIT or DEBIT) |
| ERR-006 | Invalid timestamp format (must be ISO-8601) |
| ERR-007 | Currency mismatch (account already has different currency) |

---

## Test Coverage

Run all tests with: `mvn test`

### Test Classes

| Class | Type | Description |
|-------|------|-------------|
| `EventControllerIntegrationTest` | MockMvc Integration | POST/GET /events endpoints |
| `AccountControllerIntegrationTest` | MockMvc Integration | GET /accounts/{id}/balance |
| `EventServiceTest` | Unit (Mockito) | Service layer with mocked repository |
| `BalanceServiceTest` | Unit (Mockito) | Balance calculation logic |
| `LedgerEventRepositoryTest` | @DataJpaTest | JPA repository with H2 |
| `EventLedgerApiTests` | End-to-End | Full workflows + concurrency |

### Covered Scenarios

| Category | Tests |
|----------|-------|
| **Idempotency** | First POST → 201, duplicate → 200, balance counted once |
| **Out-of-order** | Events returned chronologically regardless of insertion order |
| **Balance** | CREDIT - DEBIT, zero balance, negative balance, large amounts |
| **Validation** | Missing/blank fields, zero/negative amounts, invalid types, bad timestamps |
| **GET endpoints** | Existing event → 200, unknown → 404, pagination |
| **Concurrency** | 10 concurrent duplicate POSTs → only 1 event, only 1x balance |
| **Currency** | Single currency per account, mismatch → 400 |
| **Edge cases** | Large amounts, small amounts, Unicode metadata, special chars |

---

## Bonus Features

- ✅ **Pagination**: `?page=0&size=10` with default size 20
- ✅ **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- ✅ **OpenAPI Spec**: `http://localhost:8080/api-docs`
- ✅ **Dockerfile**: Multi-stage build for production deployment
- ✅ **Currency validation**: Mixed currencies rejected per account

---

## H2 Console

Access the database at: `http://localhost:8080/h2-console`

| Setting | Value |
|---------|-------|
| JDBC URL | `jdbc:h2:mem:ledgerdb` |
| Username | `sa` |
| Password | *(empty)* |

---

## Docker

```bash
# Build
docker build -t event-ledger-api .

# Run
docker run -p 8080:8080 event-ledger-api
```

---

## Project Structure

```
src/
├── main/java/com/example/eventledger/
│   ├── EventLedgerApplication.java
│   ├── controller/
│   │   ├── EventController.java
│   │   └── AccountController.java
│   ├── dto/
│   │   ├── EventRequest.java
│   │   ├── EventResponse.java
│   │   ├── BalanceResponse.java
│   │   └── ErrorResponse.java
│   ├── entity/
│   │   ├── LedgerEvent.java          # eventId as @Id (primary key)
│   │   └── EventType.java            # CREDIT, DEBIT enum
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── EventNotFoundException.java
│   │   └── CurrencyMismatchException.java
│   ├── repository/
│   │   └── LedgerEventRepository.java
│   └── service/
│       ├── EventService.java         # Idempotency + concurrency handling
│       └── BalanceService.java       # Balance computation
│
└── test/java/com/example/eventledger/
    ├── EventLedgerApiTests.java               # E2E + concurrency tests
    ├── controller/
    │   ├── EventControllerIntegrationTest.java
    │   └── AccountControllerIntegrationTest.java
    ├── service/
    │   ├── EventServiceTest.java
    │   └── BalanceServiceTest.java
    └── repository/
        └── LedgerEventRepositoryTest.java
```

---

## License

MIT License
