# CricAuction-Backbone: Ponytail Audit Complete Fixes Report (Issues 1–10)

**Date:** October 30, 2026  
**Auditor / Engineer:** Antigravity Autonomous Coding Agent  
**Target Application:** CricAuction-Backbone Enterprise REST API  
**Environment Assumed:** 5,000+ Concurrent Virtual Thread Users, PostgreSQL Master + Read Replica Pool  

---

## 1. Executive Summary

This report documents the architectural fixes implemented for all 10 issues identified during the `/ponytail-audit` code review across the `CricAuction-backbone` repository. All 10 issues have been resolved directly in the codebase with zero added third-party dependencies, and verified through both comprehensive regression tests and targeted unit tests (**78/78 tests passing**).

| # | Issue Identified | Category | Root Cause | Fix Implemented | Status |
|---|---|---|---|---|---|
| **1** | **Circuit Breaker DoS Vulnerability on Google OAuth** | Reliability / Security | Single global breaker state for all services; 4xx client auth errors (`UnauthorizedException`) counted toward breaker failure threshold. | Partitioned breakers per service using `ConcurrentHashMap`; bypassed 4xx client errors so only downstream 5xx/network errors record failures. | ✅ **RESOLVED** |
| **2** | **Google Token Verifier Retried Bad Tokens 3 Times** | Performance / Latency | `ResilienceRetry` default predicate retried all exceptions (`ex -> true`), causing invalid/expired tokens to trigger 3 HTTP calls to Google with backoff sleep. | Updated retry predicate to bypass non-transient client exceptions (`UnauthorizedException`, `BadRequestException`); differentiated Google 4xx vs 5xx. | ✅ **RESOLVED** |
| **3** | **Concurrent Tournament Slug Collisions Crashed with 500** | Concurrency / Error Handling | Read replica check had a TOCTOU race window under concurrent creation; duplicate master insert threw `DataIntegrityViolationException` converted to generic 500. | Added `@ExceptionHandler(DataIntegrityViolationException.class)` returning 409 Conflict; implemented atomic retry with unique salt in `TournamentServiceImpl`. | ✅ **RESOLVED** |
| **4** | **Concurrent Captain Claims Created Duplicate Captains / 500** | Data Integrity / Concurrency | No database unique constraint on `squad_members (team_id, is_captain)`; `OptimisticLockException` unhandled in `GlobalExceptionHandler` returning 500. | Added Flyway migration `V7` (partial unique index `ON squad_members(team_id) WHERE is_captain = true`); mapped optimistic lock failures to 409 Conflict; added master-side claim validation. | ✅ **RESOLVED** |
| **5** | **Unbounded In-Memory Public Tournament Cache Leaked Heap** | Scalability / Memory | `publicTournamentCache` in `TournamentServiceImpl` was an unbounded `ConcurrentHashMap` with no max size or TTL expiry. | Replaced with thread-safe `BoundedLruCache` capping at 500 items with a 10-minute TTL and access-order LRU eviction. | ✅ **RESOLVED** |
| **6** | **Refresh Token Read from Replica Causing False 401s** | Auth / Consistency | `TokenServiceImpl.refreshToken` was marked `@Transactional(readOnly = true)`, routing to the Read Replica where replication delay caused missing token lookups. | Removed `readOnly = true`, directing token refresh directly to Master DB for immediate read-your-writes consistency. | ✅ **RESOLVED** |
| **7** | **Dead Database Records for Abandoned OTPs and Expired Tokens** | Database / Cleanup | Repositories defined delete queries, but no background scheduler ever invoked them, leaving abandoned OTPs and revoked tokens in the DB forever. | Created `AuthTokenCleanupScheduler` annotated with `@EnableScheduling` running hourly `@Scheduled` purges on Master DB. | ✅ **RESOLVED** |
| **8** | **Hibernate DDL Update Active Alongside Flyway Migrations** | Schema Management | `spring.jpa.hibernate.ddl-auto` was configured as `update` in `application.yml` despite Flyway being enabled, risking uncontrolled schema drift on boot. | Set `spring.jpa.hibernate.ddl-auto: validate`, ensuring schema evolution is strictly governed by Flyway migration scripts. | ✅ **RESOLVED** |
| **9** | **Redundant Read Query in Role Switching Burned Replica Pool** | Performance / Resource Waste | `AuthServiceImpl.switchRole` queried `authQueryService.findById()` and discarded the return value immediately before updating on Master. | Deleted the redundant replica query, eliminating wasted database connections and roundtrips. | ✅ **RESOLVED** |
| **10** | **Over-Engineered DTO Struct Wrappers in Team Creation** | Lean Code / Architecture | `validateTeamCreation` wrapped 7 parameters into an artificial inner class `TeamCreationData` just to unpack them in `TeamPersistenceService`. | Eliminated `TeamCreationData` struct; `validateTeamCreation` directly returns the built `Team` entity, saving memory allocations and boilerplate. | ✅ **RESOLVED** |

---

## 2. Detailed Technical Fixes

### Issue 1: Circuit Breaker DoS Vulnerability on Google OAuth
- **Affected Files:**
  - `src/main/java/com/cricauction/common/resilience/CircuitBreaker.java`
  - `src/main/java/com/cricauction/modules/auth/service/impl/GoogleTokenVerifierImpl.java`
- **Root Cause:**
  1. `CircuitBreaker` previously maintained a single `AtomicReference<State> state` shared across all downstream dependencies (Google OAuth, SMS, Email). An outage in SMS could trip Google OAuth.
  2. The execute block recorded failure for *any* exception, including `UnauthorizedException`. Five users entering invalid or expired Google tokens tripped the circuit breaker to `OPEN`, locking out all legitimate users for 5 seconds.
- **Solution:**
  1. Created an isolated `ServiceBreaker` struct and mapped state by service name: `ConcurrentMap<String, ServiceBreaker> serviceBreakers = new ConcurrentHashMap<>()`.
  2. Added failure filter: `defaultShouldRecordFailure` ignores client authentication exceptions (`UnauthorizedException`, `BadRequestException`, `ForbiddenException`).
  3. Preserved backward-compatible methods (`getState()`, `reset()`, `trip()`) to support existing integration tests.

---

### Issue 2: ResilienceRetry Hammering Downstream on Bad Client Tokens
- **Affected Files:**
  - `src/main/java/com/cricauction/common/resilience/ResilienceRetry.java`
  - `src/main/java/com/cricauction/modules/auth/service/impl/GoogleTokenVerifierImpl.java`
- **Root Cause:**
  `ResilienceRetry` ran `ex -> true` by default. When a client supplied an expired token, Google returned 400 Bad Request. The server retried the call 3 times with exponential backoff and jitter, adding hundreds of milliseconds of unnecessary latency and burning thread pool capacity.
- **Solution:**
  1. Updated `ResilienceRetry.executeWithRetry` to bypass retries on client auth errors: `ex -> !(ex instanceof UnauthorizedException || ex instanceof BadRequestException)`.
  2. In `GoogleTokenVerifierImpl`, caught Spring's `HttpClientErrorException` (Google 4xx) and translated it directly into `UnauthorizedException` without retrying. Real infrastructure failures (5xx / IO / timeouts) continue to retry and trip the breaker.

---

### Issue 3: Concurrent Slug Collision Crashing with HTTP 500
- **Affected Files:**
  - `src/main/java/com/cricauction/common/exception/GlobalExceptionHandler.java`
  - `src/main/java/com/cricauction/modules/tournament/service/impl/TournamentServiceImpl.java`
- **Root Cause:**
  When two organizers registered tournaments with the same name simultaneously, both read replica checks (`existsBySlug`) returned `false`. Both threads attempted to insert the same `slug` on the master database, causing a PostgreSQL unique constraint violation (`DataIntegrityViolationException`). Because `GlobalExceptionHandler` lacked a dedicated handler, it fell through to `handleGenericException` and returned an unhandled HTTP 500.
- **Solution:**
  1. Added `@ExceptionHandler(DataIntegrityViolationException.class)` in `GlobalExceptionHandler`, returning HTTP 409 Conflict with code `RESOURCE_CONFLICT`.
  2. In `TournamentServiceImpl.createTournament`, added an automatic fallback retry on `DataIntegrityViolationException` that appends a unique random salt (e.g. `mumbai-premier-league-a1b2`) to seamlessly create the tournament without failing the organizer's request.

---

### Issue 4: Concurrent Captain Claims & Optimistic Locking Crashes
- **Affected Files:**
  - `src/main/resources/db/migration/V7__add_unique_captain_per_team_constraint.sql`
  - `src/main/java/com/cricauction/common/exception/GlobalExceptionHandler.java`
  - `src/main/java/com/cricauction/modules/team/service/TeamPersistenceService.java`
- **Root Cause:**
  1. Table `squad_members` lacked a database-level uniqueness constraint ensuring only one captain exists per team.
  2. When two users simultaneously claimed a team via invite code, detached entities with identical JPA version stamps clashed, throwing `OptimisticLockException` / `ObjectOptimisticLockingFailureException`. These were unhandled in `GlobalExceptionHandler` and returned HTTP 500.
- **Solution:**
  1. Added Flyway migration `V7__add_unique_captain_per_team_constraint.sql`:
     ```sql
     CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_team_captain
     ON squad_members(team_id)
     WHERE is_captain = true;
     ```
  2. In `GlobalExceptionHandler`, registered explicit handlers for `ObjectOptimisticLockingFailureException` and `OptimisticLockException` returning HTTP 409 Conflict with code `CONCURRENT_MODIFICATION`.
  3. In `TeamPersistenceService.executeJoinTeam`, added a double-check on the master entity to confirm another user has not already claimed the captain role during the read replica lag window, throwing `ConflictException("TEAM_ALREADY_CLAIMED", ...)` if claimed.
  4. Wrapped `injectCaptainSquadMember` to catch `DataIntegrityViolationException` gracefully.

---

### Issue 5: Unbounded In-Memory Public Tournament Cache Leaking JVM Heap
- **Affected Files:**
  - `src/main/java/com/cricauction/common/cache/BoundedLruCache.java` (New)
  - `src/main/java/com/cricauction/modules/tournament/service/impl/TournamentServiceImpl.java`
  - `src/main/java/com/cricauction/modules/tournament/service/TournamentQueryService.java`
- **Root Cause:**
  `TournamentServiceImpl` stored `PublicTournamentResponse` instances in an unbounded `ConcurrentHashMap`. Crawlers or thousands of tournaments caused this map to grow without bound, with no eviction policy or TTL expiration.
- **Solution:**
  1. Implemented `BoundedLruCache<K, V>` using standard `LinkedHashMap` in access-order mode with `ReentrantReadWriteLock`.
  2. Set maximum capacity to 500 entries and TTL to 600 seconds (10 minutes).
  3. Expired or excess entries are evicted automatically on read/write, guaranteeing constant maximum heap usage under 5,000+ concurrent requests.

---

### Issue 6: Refresh Token Lookup Read from Replica Causing False 401 Logouts
- **Affected Files:**
  - `src/main/java/com/cricauction/modules/auth/service/impl/TokenServiceImpl.java`
- **Root Cause:**
  `TokenServiceImpl.refreshToken(String token)` was annotated with `@Transactional(readOnly = true)`. In the dynamic routing architecture, read-only transactions are leased from `CricAuction-Replica-Pool`. If a user signed in on the Master DB and their frontend client immediately requested a token refresh, replication lag between the Master and Replica databases caused the token to not yet exist on the replica, throwing `UnauthorizedException("INVALID_REFRESH_TOKEN")`.
- **Solution:**
  Removed `(readOnly = true)` from `@Transactional` on `refreshToken(String token)`. Lookups are now served with immediate consistency directly from the Master database (`CricAuction-Master-Pool`).

---

### Issue 7: Dead Database Records for Abandoned OTPs and Expired Tokens
- **Affected Files:**
  - `src/main/java/com/cricauction/modules/auth/scheduler/AuthTokenCleanupScheduler.java` (New)
  - `src/main/resources/application.yml`
- **Root Cause:**
  Repository queries (`deleteExpiredOtps`, `deleteExpiredOrRevokedTokens`, `deleteExpiredOrUsedTokens`) existed in `OtpVerificationRepository`, `RefreshTokenRepository`, and `PasswordResetTokenRepository`, but no scheduler or background service ever invoked them. Expired OTPs that users abandoned without entering and revoked refresh tokens accumulated indefinitely.
- **Solution:**
  1. Created `AuthTokenCleanupScheduler` annotated with `@Component`, `@EnableScheduling`, and `@Transactional`.
  2. Configured an hourly purge cron job (`cricauction.auth.cleanup-cron: "0 0 * * * *"`) that invokes all three delete methods in a single master transaction.
  3. Added error isolation so transient database timeouts during cleanup do not crash the application context.

---

### Issue 8: Hibernate DDL Update Active Alongside Flyway Migrations
- **Affected Files:**
  - `src/main/resources/application.yml`
- **Root Cause:**
  `application.yml` had `spring.jpa.hibernate.ddl-auto: update` while `flyway.enabled: true`. This anti-pattern allowed Hibernate to silently alter schema structures on application boot, bypassing Flyway version control and risking startup table locks in multi-instance production environments.
- **Solution:**
  Changed `spring.jpa.hibernate.ddl-auto` from `update` to `validate` in `application.yml`. Hibernate now strictly validates that JPA entity mappings conform to Flyway's versioned DDL migrations without modifying the database on startup.

---

### Issue 9: Redundant Read Query During Role Switching Wasted Replica Connections
- **Affected Files:**
  - `src/main/java/com/cricauction/modules/auth/service/impl/AuthServiceImpl.java`
- **Root Cause:**
  In `AuthServiceImpl.switchRole`, line 253 executed `authQueryService.findById(currentUser.getId())` on the Read Replica pool and completely discarded the return value. The next line immediately called `authPersistenceService.updateUserRoles`, which queried the user again on the Master DB.
- **Solution:**
  Deleted the redundant `authQueryService.findById` call. Role switching now immediately executes on the Master DB in a single micro-write transaction without burning replica connections.

---

### Issue 10: Over-Engineered DTO Struct Wrappers in Team Creation
- **Affected Files:**
  - `src/main/java/com/cricauction/modules/team/service/TeamQueryService.java`
  - `src/main/java/com/cricauction/modules/team/service/TeamPersistenceService.java`
  - `src/main/java/com/cricauction/modules/team/service/impl/TeamServiceImpl.java`
- **Root Cause:**
  `TeamQueryService.validateTeamCreation` packed 7 fields into an intermediate nested class `TeamCreationData`, which was passed to `TeamPersistenceService.createTeam` only to unpack each field back into a `Team.builder()`. This introduced unnecessary indirection, duplicate field assignments, and extra object allocations per team creation request.
- **Solution:**
  1. Removed `public static class TeamCreationData` from `TeamQueryService`.
  2. Modified `validateTeamCreation` to construct and return the `Team` entity directly.
  3. Streamlined `TeamPersistenceService.createTeam(Team team)` to directly call `teamRepository.save(team)`.

---

## 3. Verification & Test Results

### 1. Dedicated Unit Test Suites
- **`CircuitBreakerAndCacheTest.java` (5 tests):**
  - Verified `UnauthorizedException` does not trip circuit breaker after 10 consecutive attempts.
  - Verified service partitioning (failure in `SmsGateway` trips it to OPEN while `GoogleOAuth` remains CLOSED).
  - Verified `ResilienceRetry` immediately halts on `UnauthorizedException` without retrying.
  - Verified `BoundedLruCache` max size enforcement, LRU eviction, and TTL expiration.
- **`AuthTokenCleanupSchedulerTest.java` (2 tests):**
  - Verified scheduled cleanup triggers `deleteExpiredOtps`, `deleteExpiredOrRevokedTokens`, and `deleteExpiredOrUsedTokens`.
  - Verified exception handling ensures cleanup failures do not bubble up or terminate scheduler threads.

### 2. Full Regression & Benchmark Test Suite
- **Command:** `./mvnw test`
- **Total Test Classes Executed:** 15 test classes across all modules.
- **High-Concurrency Benchmarks:**
  - `FullFlow5000UsersActualDbTest`: 5,000 concurrent user registrations and OTP logins.
  - `TournamentConcurrency5000UsersActualDbTest`: Concurrent tournament creation and public cache lookups.
  - `ProfileConcurrency5000UsersActualDbTest`: Concurrent player bio and profile mutations.
  - `TeamConcurrency5000UsersActualDbTest`: 5,000 concurrent Virtual Thread team invites and captain claims.
  - `TeamIndividualApiLatencyBenchmarkTest`: Individual sub-millisecond API latency benchmarks.
- **Final Result:** **78/78 Tests Passed, 0 Failures, 0 Errors, 0 Skipped (Total time: 01:04 min)**

---

## 4. Complete Modified Files List

1. `src/main/java/com/cricauction/common/cache/BoundedLruCache.java` (New)
2. `src/main/java/com/cricauction/common/resilience/CircuitBreaker.java` (Modified)
3. `src/main/java/com/cricauction/common/resilience/ResilienceRetry.java` (Modified)
4. `src/main/java/com/cricauction/common/exception/GlobalExceptionHandler.java` (Modified)
5. `src/main/java/com/cricauction/modules/auth/scheduler/AuthTokenCleanupScheduler.java` (New)
6. `src/main/java/com/cricauction/modules/auth/service/impl/GoogleTokenVerifierImpl.java` (Modified)
7. `src/main/java/com/cricauction/modules/auth/service/impl/TokenServiceImpl.java` (Modified)
8. `src/main/java/com/cricauction/modules/auth/service/impl/AuthServiceImpl.java` (Modified)
9. `src/main/java/com/cricauction/modules/tournament/service/TournamentQueryService.java` (Modified)
10. `src/main/java/com/cricauction/modules/tournament/service/impl/TournamentServiceImpl.java` (Modified)
11. `src/main/java/com/cricauction/modules/team/service/TeamQueryService.java` (Modified)
12. `src/main/java/com/cricauction/modules/team/service/TeamPersistenceService.java` (Modified)
13. `src/main/java/com/cricauction/modules/team/service/impl/TeamServiceImpl.java` (Modified)
14. `src/main/resources/application.yml` (Modified)
15. `src/main/resources/db/migration/V7__add_unique_captain_per_team_constraint.sql` (New)
16. `src/test/java/com/cricauction/common/resilience/CircuitBreakerAndCacheTest.java` (New)
17. `src/test/java/com/cricauction/modules/auth/scheduler/AuthTokenCleanupSchedulerTest.java` (New)
