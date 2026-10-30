# CricAuction Backbone

High-performance, ultra-low-latency backend infrastructure powering the **CricAuction** real-time tournament, team, player, and live cricket auction platform. Built with **Spring Boot 4.1.1**, **Java 23 Virtual Threads (Project Loom)**, and **Pure PostgreSQL 16+** with dynamic Master-Replica connection routing.

---

## Technical Highlights

- **Pure PostgreSQL Strategy**: Leverages transactional advisory locks (`pg_advisory_xact_lock`), partial indexes, and optimistic locking (`@Version`) without external Redis dependencies.
- **Master-Replica Routing**: Custom `DynamicDataSourceRouter` that automatically routes `@Transactional(readOnly = true)` queries to the Read Replica while directing micro-writes and locked queries to the Primary Master DB.
- **CQRS Read-Before-Write Pattern**: Separates read verification from micro-write transactions (`AuthQueryService` vs `AuthPersistenceService`, `TeamQueryService` vs `TeamPersistenceService`, etc.) to minimize Master connection hold times to under 2 ms.
- **Resilience Engine**: Token-bucket sliding window rate limiting, service-isolated `CircuitBreaker`, and exponential retry with jitter.
- **Enterprise Security (OWASP Top 10 API)**: Stateless HMAC-SHA256 JWT, immediate deactivated session revocation in `JwtAuthenticationFilter`, PII masking, and directory root traversal defenses.
- **Automated Housekeeping**: `@Scheduled` hourly cleanup of expired OTPs and revoked refresh tokens via `AuthTokenCleanupScheduler`.

---

## Architectural Modules

| Module | Scope | Master Documentation Guide |
|---|---|---|
| **Module 1** | Authentication, RBAC, OAuth2, OTP & Security | [MODULE_1_AUTHENTICATION_AND_SECURITY_MASTER_GUIDE.md](MODULE_1_AUTHENTICATION_AND_SECURITY_MASTER_GUIDE.md) |
| **Module 2** | Profile, Player Cricket Stats & Storage | [MODULE_2_PROFILE_MANAGEMENT_MASTER_GUIDE.md](MODULE_2_PROFILE_MANAGEMENT_MASTER_GUIDE.md) |
| **Module 4** | Tournament Lifecycle, Purse & Rules Wizard | [MODULE_4_TOURNAMENT_MANAGEMENT_MASTER_GUIDE.md](MODULE_4_TOURNAMENT_MANAGEMENT_MASTER_GUIDE.md) |
| **Module 5** | Team Provisioning, Captain Claims & Squads | [MODULE_5_TEAM_AND_CAPTAIN_MANAGEMENT_MASTER_GUIDE.md](MODULE_5_TEAM_AND_CAPTAIN_MANAGEMENT_MASTER_GUIDE.md) |

---

## Technical Architecture & Reports

- **Database Architecture**: [cricauction-database-architecture.md](cricauction-database-architecture.md)
- **API Specification**: [cricauction-api-specification.md](cricauction-api-specification.md)
- **Read-Before-Write Guide**: [STRATEGY_1_READ_BEFORE_WRITE_IMPLEMENTATION_GUIDE.md](STRATEGY_1_READ_BEFORE_WRITE_IMPLEMENTATION_GUIDE.md)
- **Security Audit Report**: [SECURITY_AUDIT_REPORT.md](SECURITY_AUDIT_REPORT.md)
- **Ponytail Audit Report**: [PONYTAIL_AUDIT_FIXES_REPORT.md](PONYTAIL_AUDIT_FIXES_REPORT.md)
- **5,000 Concurrent Users Benchmark**: [CONCURRENCY_5000_USERS_FULL_FLOW_TEST_REPORT.md](CONCURRENCY_5000_USERS_FULL_FLOW_TEST_REPORT.md)

---

## Getting Started

### Prerequisites
- **JDK 23+** (OpenJDK 23 recommended)
- **Maven 3.9+** (or project `./mvnw` wrapper)
- **PostgreSQL 16+** (for production/live profiles; H2 in PostgreSQL compatibility mode used for test suite)

### Running the Application
```bash
./mvnw spring-boot:run
```

### Running the Automated Test Suite (83 Tests)
```bash
./mvnw test
```

All 83 automated unit, integration, and high-concurrency tests (including 5,000-user actual DB benchmarks) run in ~60 seconds with **0 failures, 0 errors, 0 skipped**.
