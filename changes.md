# Comprehensive Security Audit & Remediation Report

**Target Scope**: Every package under `com.cricauction` (`src/main/java/com/cricauction/`)  
**Skill Tooling**: [Cloudflare Security Audit Skill](https://github.com/cloudflare/security-audit-skill.git) (`security-audit`)  
**Automation Script**: [`scripts/post-codegen-security-audit.sh`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/scripts/post-codegen-security-audit.sh)  
**Automated Subagent Job**: `security-audit-fixer`  
**Test Suite Status**: 83/83 Unit & Integration Tests Passed (0 failures, 0 regressions)

---

## Executive Summary

A comprehensive, package-by-package security audit was conducted across the entire CricAuction backbone application (`com.cricauction.*`). The audit adhered strictly to the Cloudflare `security-audit-skill` standards and OWASP Top 10 API Security Risks (2023). Every package—including configuration, security filters, resilience filters, exception handling, authentication, profiles, tournaments, and team management—was analyzed for security weaknesses, authorization flaws, backdoors, input validation bypasses, and information leaks.

All identified vulnerabilities have been remediated, validated against schema ledgers, and verified through automated test suites with **zero regressions**.

---

## Package-by-Package Audit & Remediation Matrix

| Package | Component / File | Vulnerability / Issue | Severity | CWE / OWASP | Remediation Applied |
|:---|:---|:---|:---:|:---|:---|
| `com.cricauction.config` | [`SecurityConfig.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/config/SecurityConfig.java) | Insecure CORS: Wildcard `*` pattern combined with `allowCredentials(true)` | **HIGH** | CWE-942, OWASP API7:2023 | Replaced wildcard origin patterns with explicit origin allowlist (`cricauction.security.cors.allowed-origins`), and restricted allowed headers to trusted standard headers. |
| `com.cricauction.common.resilience` | [`RateLimitingFilter.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/common/resilience/RateLimitingFilter.java) | Rate-limit bypass via unauthenticated `X-Bypass-Rate-Limit: true` client header | **HIGH** | CWE-290, OWASP API4:2023 | Gated bypass check behind `@Value("${cricauction.security.rate-limit.bypass-enabled:false}")`. Ignored completely in production; enabled only in test profiles. |
| `com.cricauction.common.exception` | [`GlobalExceptionHandler.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/common/exception/GlobalExceptionHandler.java) | Missing handler for `ConstraintViolationException` causing HTTP 500 on validation failure | **MEDIUM** | CWE-703, CWE-209 | Added `@ExceptionHandler(ConstraintViolationException.class)` and `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` returning standardized HTTP 400 Bad Request responses. |
| `com.cricauction.modules.auth.service.impl` | [`OtpServiceImpl.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/service/impl/OtpServiceImpl.java) | Use of cryptographically weak PRNG (`ThreadLocalRandom`) for OTP code generation | **HIGH** | CWE-338 | Replaced `ThreadLocalRandom` with `java.security.SecureRandom` for unpredictable 6-digit OTP generation. |
| `com.cricauction.modules.auth.service.impl` | [`GoogleTokenVerifierImpl.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/service/impl/GoogleTokenVerifierImpl.java) | Authentication bypass: Mock token prefixes (`test-google-token-`) enabled in production | **CRITICAL** | CWE-287, OWASP API2:2023 | Gated mock tokens strictly behind `@Value("${cricauction.security.google.allow-mock-tokens:false}")`. Defaults to `false` in production; enabled only in test environments. |
| `com.cricauction.modules.auth.controller` | [`AuthController.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/controller/AuthController.java) | Unsanitized user identifier reflection in response messages & missing method security | **MEDIUM** | CWE-79, CWE-285 | Sanitized OTP response messages; added `@Validated` and `@PreAuthorize("isAuthenticated()")` defense-in-depth on `/logout`, `/me`, and `/role`. |
| `com.cricauction.modules.profile.controller` & `service` | [`UserProfileController.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/profile/controller/UserProfileController.java), [`ProfileServiceImpl.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/profile/service/impl/ProfileServiceImpl.java) | PII exposure: Email & mobile returned when querying other users' public profiles | **HIGH** | CWE-200, CWE-359 | Enforced PII masking in `getUserProfileById`: `email` and `mobile` are set to `null` for non-owners. Added `@Validated` and `@Positive` constraints on `userId`. |
| `com.cricauction.modules.profile.storage.impl` | [`LocalFileStorageStrategy.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/profile/storage/impl/LocalFileStorageStrategy.java) | File extension spoofing / Stored XSS in profile & logo uploads | **MEDIUM** | CWE-434 | Enforced explicit extension allowlist (`.jpg`, `.jpeg`, `.png`, `.webp`), rejecting unauthorized extensions before writing to disk. |
| `com.cricauction.modules.tournament.controller` | [`TournamentController.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/tournament/controller/TournamentController.java) | Missing method-level security and unconstrained path variables | **LOW** | CWE-285, CWE-20 | Added `@Validated`, `@PreAuthorize("isAuthenticated()")`, and `@Size(max = 100)` constraints on `id` and `slug` path variables. |
| `com.cricauction.modules.team.controller` & `service` | [`TeamController.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/team/controller/TeamController.java), [`TeamServiceImpl.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/team/service/impl/TeamServiceImpl.java) | BOLA: Secret team `inviteCode` & captain mobile leaked in public team listings | **HIGH** | CWE-200, OWASP API1:2023 | Masked `inviteCode` and `captainMobile` so only verified tournament organizers receive raw values. Added `@Valid` and bean validation on profile update and invite requests. |
| `com.cricauction.modules.auth.service.impl` | [`AuthServiceImpl.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/service/impl/AuthServiceImpl.java) | Deactivated/suspended accounts bypass lock via OTP verification (`verifyOtp` & `loginWithOtp`) | **HIGH** | CWE-284, CWE-287 | Added `!user.isActive()` checks throwing `UnauthorizedException("ACCOUNT_SUSPENDED")`. |
| `com.cricauction.security` | [`JwtAuthenticationFilter.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/security/JwtAuthenticationFilter.java) | Suspended users retain access with active 24-hr JWT tokens | **HIGH** | CWE-613, OWASP API1:2023 | Enforced `userDetails.isEnabled() && userDetails.isAccountNonLocked()` before populating SecurityContext. |
| `com.cricauction.modules.team.service` | [`TeamQueryService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/team/service/TeamQueryService.java), [`TeamPersistenceService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/team/service/TeamPersistenceService.java) | Multi-team captain assignment & live auction price collusion via invite code | **MEDIUM** | CWE-284, OWASP API5:2023 | Asserted `existsByTournamentIdAndCaptainId` in `validateJoinTeam` & `executeJoinTeam`, backed by DB index `idx_teams_tournament_captain`. |
| `com.cricauction.modules.auth.service.impl` | [`AuthServiceImpl.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/service/impl/AuthServiceImpl.java) | Password reset DoS for Google / email-only accounts with null mobile | **MEDIUM** | CWE-476, OWASP API4:2023 | Added automatic fallback to email OTP dispatch with `maskEmail()` PII masking. |
| `com.cricauction.modules.profile.storage.impl` | [`LocalFileStorageStrategy.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/profile/storage/impl/LocalFileStorageStrategy.java) | Missing directory root containment in `uploadFile` | **LOW** | CWE-22, CWE-73 | Added `targetDir.startsWith(baseRoot)` assertion throwing `BadRequestException("INVALID_PATH")`. |
| `com.cricauction.common.cache` | [`BoundedLruCache.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/common/cache/BoundedLruCache.java) | Unbounded L1 memory cache memory leak risk in tournament landing pages | **MEDIUM** | CWE-400 | Replaced unbounded map with bounded LRU cache (max 500 items, 10-minute TTL). |
| `com.cricauction.common.resilience` | [`CircuitBreaker.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/common/resilience/CircuitBreaker.java) | Circuit breaker tripped globally across unrelated services on client 4xx errors | **MEDIUM** | CWE-703 | Isolated circuit breakers per service name and bypassed client 4xx HTTP exceptions. |
| `com.cricauction.modules.auth.scheduler` | [`AuthTokenCleanupScheduler.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/scheduler/AuthTokenCleanupScheduler.java) | Database table bloat from expired OTP and revoked refresh tokens | **LOW** | CWE-400 | Added `@Scheduled` hourly background cleanup purging expired records from Master DB. |

---

## Detailed Vulnerability & Remediation Breakdown

### 1. Insecure CORS Configuration with Credentials Enabled
- **Vulnerability**: In [`SecurityConfig.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/config/SecurityConfig.java), `setAllowedOriginPatterns(List.of("*"))` was combined with `setAllowCredentials(true)`. This allows any malicious website on the internet to issue credentialed AJAX requests to the backend, enabling cross-origin data theft (CWE-942).
- **Remediation**:
  - Bound allowed origins to application property `cricauction.security.cors.allowed-origins` with safe defaults (`http://localhost:3000`, `http://localhost:5173`, `https://*.cricauction.app`, `https://*.cricauction.com`).
  - Restricted `allowedHeaders` from wildcard `*` to specific trusted headers (`Authorization`, `Content-Type`, `X-Requested-With`, `Accept`, etc.).

---

### 2. Unauthenticated Rate Limiting Header Bypass
- **Vulnerability**: In [`RateLimitingFilter.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/common/resilience/RateLimitingFilter.java), checking `request.getHeader("X-Bypass-Rate-Limit")` without authentication allowed any client to evade rate limits by appending `X-Bypass-Rate-Limit: true` (CWE-290).
- **Remediation**:
  - Injected `@Value("${cricauction.security.rate-limit.bypass-enabled:false}") private boolean bypassEnabled;`.
  - In production, `bypassEnabled` is `false`, completely ignoring client-supplied bypass headers.
  - In test profiles (`src/test/resources/application.yml`), `bypass-enabled` is set to `true` to allow concurrency benchmark testing.

---

### 3. Google OAuth Authentication Bypass via Test Tokens in Production
- **Vulnerability**: In [`GoogleTokenVerifierImpl.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/service/impl/GoogleTokenVerifierImpl.java), `idToken.startsWith("test-google-token-")` was evaluated unconditionally in the main verification path. An attacker could supply `test-google-token-victim@gmail.com` and authenticate as any victim without contacting Google servers (CWE-287).
- **Remediation**:
  - Injected `@Value("${cricauction.security.google.allow-mock-tokens:false}") private boolean allowMockTokens;`.
  - Production defaults to `false`, rejecting all mock tokens and enforcing authentic Google OAuth2 validation.
  - Test profile enables `allow-mock-tokens: true` for automated unit and load testing.

---

### 4. Weak Pseudo-Random Number Generator for One-Time Passwords
- **Vulnerability**: In [`OtpServiceImpl.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/service/impl/OtpServiceImpl.java), 6-digit OTP codes were generated using `ThreadLocalRandom.current().nextInt(1_000_000)`. Because `ThreadLocalRandom` is not cryptographically secure, internal state can be deduced from observed OTPs, allowing attackers to predict subsequent OTP codes (CWE-338).
- **Remediation**:
  - Replaced `ThreadLocalRandom` with `java.security.SecureRandom`.

---

### 5. Unhandled Constraint Violations Resulting in HTTP 500 Responses
- **Vulnerability**: Adding `@Validated` on controllers causes invalid `@PathVariable` and `@RequestParam` values to throw `jakarta.validation.ConstraintViolationException`. In [`GlobalExceptionHandler.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/common/exception/GlobalExceptionHandler.java), this was not explicitly handled, causing Spring to fall back to `handleGenericException` and return HTTP 500 Internal Server Error (CWE-703).
- **Remediation**:
  - Added `@ExceptionHandler(ConstraintViolationException.class)` extracting field violation messages into standardized `ErrorDetail` objects and returning HTTP 400 Bad Request.
  - Added `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` to cleanly handle type conversion mismatches.

---

### 6. Broken Object Level Authorization: Team Invite Code Leak
- **Vulnerability**: In [`TeamController.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/team/controller/TeamController.java), `GET /api/v1/tournaments/{tournamentId}/teams` returned `TeamSummaryResponse` containing secret `inviteCode` and `captainMobile` to any authenticated player. Players could steal invite codes to hijack team captaincy (CWE-200, OWASP API1:2023).
- **Remediation**:
  - In [`TeamSummaryResponse.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/team/dto/response/TeamSummaryResponse.java), nullified `inviteCode` and empty-stringed `captainMobile` unless the requesting user is the verified tournament organizer.
  - Added test case `testNonOrganizerCannotViewInviteCode()` in [`TeamControllerTest.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/test/java/com/cricauction/modules/team/controller/TeamControllerTest.java).

---

### 7. User Profile PII Exposure
- **Vulnerability**: In [`UserProfileController.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/profile/controller/UserProfileController.java), calling `GET /api/v1/users/{userId}/profile` returned the user's private email address and mobile phone number to other users (CWE-200, CWE-359).
- **Remediation**:
  - In [`UserProfileResponse.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/dto/response/UserProfileResponse.java), introduced `fromEntity(User user, boolean isOwnerOrSelf)`.
  - In [`ProfileServiceImpl.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/profile/service/impl/ProfileServiceImpl.java), email and mobile are stripped when `currentUser.getId() != targetUser.getId()`.

---

### 8. File Upload Extension Spoofing
- **Vulnerability**: In [`LocalFileStorageStrategy.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/profile/storage/impl/LocalFileStorageStrategy.java), file extensions were extracted directly from the user-supplied filename without checking against an allowed extension list, creating Stored XSS risks if an SVG or HTML file was uploaded (CWE-434).
- **Remediation**:
  - Enforced validation against allowlist `ALLOWED_IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".webp")`.

---

## Audit Run 2 Remediations & Hardening

| Component | Vulnerability / Issue | Severity | CWE / OWASP | Remediation Applied |
|:---|:---|:---:|:---|:---|
| [`OtpVerification.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/entity/OtpVerification.java) & [`OtpServiceImpl.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/service/impl/OtpServiceImpl.java) | OTP Cooldown Bypass via Stale `@CreationTimestamp` (`FPR-AUTH-OTP-COOLDOWN-BYPASS`) | **HIGH** | CWE-799, API4:2023 | Added `last_sent_at` timestamp column (with Flyway migration `V5`), refreshed on every OTP dispatch and evaluated for the 45-second throttle. |
| [`RateLimitingFilter.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/common/resilience/RateLimitingFilter.java) & [`RateLimiter.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/common/resilience/RateLimiter.java) | Rate limit evasion via `X-Forwarded-For` spoofing & unbounded heap allocation (`FPR-RESILIENCE-IP-SPOOF-RATE-LIMIT-BYPASS`) | **MEDIUM** | CWE-290, CWE-400 | Gated proxy headers behind `trust-forwarded-for: false` by default, defaulting to `getRemoteAddr()`; bounded `RateLimiter` map capacity and added expired window eviction. |
| [`LocalFileStorageStrategy.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/profile/storage/impl/LocalFileStorageStrategy.java) | Missing root containment check in `deleteFile` (`FPR-STORAGE-UNCONFINED-PATH-DELETION`) | **LEAD** | CWE-22, CWE-73 | Verified normalized path starts with base directory root (`filePath.startsWith(baseRoot)`), rejecting traversal attempts. |
| [`CreateTournamentRequest.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/tournament/dto/request/CreateTournamentRequest.java), [`UpdateTournamentRequest.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/tournament/dto/request/UpdateTournamentRequest.java) | Unconstrained string lengths and missing URL scheme validation (`FPR-TOURNAMENT-UNCONSTRAINED-INPUT-SIZE`) | **LOW** | CWE-20, CWE-400 | Enforced `@Size` bounds on all text fields and `@Pattern` URL format validation on `bannerUrl`. |

---

## Verification & Test Results

1. **Audit Trigger Script Execution**:
   ```bash
   ./scripts/post-codegen-security-audit.sh --full
   ```
   **Result**: `[SUCCESS] Full Codebase Security Audit Completed with 0 Regressions!`
   - Validated Units: 12
   - Remediated Findings: 8
   - Findings & Ledger schema verification: **PASS**

2. **Full Maven Test Suite**:
   ```bash
   mvn test
   ```
   **Result**: `Tests run: 113, Failures: 0, Errors: 0, Skipped: 0`
   - All tests across Auth, Tournament, Profile, Team, and Player Registration modules passed cleanly on OpenJDK 23 / Spring Boot 4.1.1.

---

## Module 6: Player Registration & Capacity Management (APIs 34–40)

Implemented end-to-end player enrollment, capacity tracking, approval workflows, base price customization, and withdrawal handling:
- **API 34**: `POST /api/v1/tournaments/{tournamentId}/players/register` — Player registration with dynamic base price inheritance and atomic capacity checks.
- **API 35**: `GET /api/v1/tournaments/{tournamentId}/players/my-status` — Player registration status tracking and auction journey.
- **API 36**: `DELETE /api/v1/tournaments/{tournamentId}/players/withdraw` — Player voluntary registration withdrawal with atomic capacity decrement.
- **API 37**: `GET /api/v1/tournaments/{tournamentId}/players` — Organizer player directory with filtered pagination, search, and role breakdown.
- **API 38**: `GET /api/v1/tournaments/{tournamentId}/players/stats` — Real-time registration capacity and role breakdown statistics.
- **API 39**: `PATCH /api/v1/tournaments/{tournamentId}/players/{playerId}/status` — Organizer vetting, approval, and rejection workflow.
- **API 40**: `PATCH /api/v1/tournaments/{tournamentId}/players/{playerId}/base-price` — Organizer custom base price adjustment.

---

## Module 6 Ponytail Review Remediations (All 6 Issues Resolved)

| Ref # | Component / File | Issue Identified | Remediation Applied |
|:---|:---|:---|:---|
| **PT-REV-1** | [`PlayerPersistenceService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/PlayerPersistenceService.java) | Double rollback decrement on registration failure | Removed explicit catch block rollback decrement call; relying cleanly on `@Transactional` rollback to undo the slot increment. |
| **PT-REV-2** | [`PlayerPersistenceService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/PlayerPersistenceService.java) | Rejecting a player doesn't free slot; restoring doesn't check capacity | Atomically decrements `registered_players_count` when moving an active player to `"Rejected"`; verifies capacity and atomically increments slot when restoring a rejected player. |
| **PT-REV-3** | [`PlayerQueryService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/PlayerQueryService.java), [`TournamentPlayerRepository.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/repository/TournamentPlayerRepository.java) | Role filtering brittle to hyphen vs space | Added `REPLACE(..., '-', ' ')` normalization in repository query and service to match "Wicket-Keeper" and "All-Rounder" regardless of separator. |
| **PT-REV-4** | [`PlayerQueryService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/PlayerQueryService.java) | Null base price could cause NPE in `String.format` | Formatted base price null-safely: `tp.getBasePrice() != null ? "₹" + String.format(Locale.US, "%,d", tp.getBasePrice()) : "₹0"`. |
| **PT-REV-5** | [`PlayerQueryService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/PlayerQueryService.java) | Phantom `TournamentSettings` fallback masks unconfigured tournament | Removed unsaved dummy object fallback; now throws `ResourceNotFoundException("SETTINGS_NOT_FOUND")` when tournament settings do not exist. |
| **PT-REV-6** | [`TournamentSettingsRepository.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/tournament/repository/TournamentSettingsRepository.java) | Redundant `findByTournamentId` when PK is `tournamentId` | Removed redundant method and replaced calls with standard Spring Data `findById(tournamentId)`. |

---

## Module 6 Security Audit & Remediations (5 Vulnerabilities Fixed)

| Ref # | Component / File | Vulnerability / Threat | Severity | CWE / OWASP | Defensive Remediation Applied |
|:---|:---|:---|:---:|:---|:---|
| **SEC-MOD6-01** | [`PlayerPersistenceService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/PlayerPersistenceService.java#L103) | Double slot decrement on rejected player voluntary withdrawal | **HIGH** | CWE-840, API4:2023 | Guarded slot decrement in `executeWithdraw` with `!"Rejected".equalsIgnoreCase(player.getStatus())`, preventing artificial capacity slot deflation. |
| **SEC-MOD6-02** | [`UpdatePlayerStatusRequest.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/dto/request/UpdatePlayerStatusRequest.java#L17), [`PlayerPersistenceService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/PlayerPersistenceService.java#L113) | State machine bypass & auction integrity tampering | **HIGH** | CWE-20, CWE-840, API8:2023 | Enforced status allowlist pattern (`Registered|Approved|Rejected|Pending`) on DTO; blocked status mutation on `"Sold"` players in persistence layer. |
| **SEC-MOD6-03** | [`PlayerRegistrationController.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/controller/PlayerRegistrationController.java#L93), [`PlayerQueryService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/PlayerQueryService.java#L107) | Unbounded pagination limit causing heap exhaustion DoS | **MEDIUM** | CWE-770, CWE-400, API4:2023 | Added `@Min(1) @Max(100)` validation on `limit`, `@Min(1)` on `page`, and defensive service-level ceiling `Math.min(limit, 100)`. |
| **SEC-MOD6-04** | [`PlayerRegistrationRequest.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/dto/request/PlayerRegistrationRequest.java#L40) | Stored XSS via CricHeroes link protocol injection | **MEDIUM** | CWE-79, CWE-20, API3:2023 | Added `@Pattern(regexp = "^(https?://.*)?$")` to restrict links strictly to valid HTTP/HTTPS protocol schemes. |
| **SEC-MOD6-05** | [`PlayerQueryService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/PlayerQueryService.java#L48-L52) | Player registration permitted on `ONGOING` tournaments | **MEDIUM** | CWE-840, API5:2023 | Added `tournament.getStatus() == TournamentStatus.ONGOING` to the registration closed check, ensuring post-auction enrollment integrity. |

---

## Module 6 Ponytail Review (Round 2 — All 5 Issues Resolved)

| Ref # | Component / File | Issue Identified | Remediation Applied |
|:---|:---|:---|:---|
| **PT-REV2-1** | [`PlayerQueryService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/PlayerQueryService.java#L180-L195) | Role breakdown stats dropped alternate role spacings/hyphens | Normalized aggregation keys with `replaceAll("[-_ ]", "")` and merged counts into `"allrounder"` and `"wicketkeeper"`. |
| **PT-REV2-2** | [`PlayerQueryService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/PlayerQueryService.java#L175-L178) | `totalEnrolled` excluded `"Sold"` and auctioned players | Calculated `totalEnrolled` as all non-rejected players (`!e.getKey().equalsIgnoreCase("rejected")`), keeping capacity stats accurate post-auction. |
| **PT-REV2-3** | [`PlayerRegistrationServiceImpl.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/impl/PlayerRegistrationServiceImpl.java#L70-L75), [`PlayerPersistenceService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/PlayerPersistenceService.java#L94-L105) | Withdrawal threw "Registration Closed" on ongoing tournaments | Removed call to `findTournamentForRegistration` from withdrawal flow; made `executeWithdraw` self-contained using `player.getTournament().getId()`. |
| **PT-REV2-4** | [`PlayerRegistrationServiceImpl.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/impl/PlayerRegistrationServiceImpl.java#L82-L86), [`PlayerQueryService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/PlayerQueryService.java#L107) | Duplicate tournament DB lookup on player directory query | Passed the `Tournament` entity returned by `validateTournamentOwnership` directly into `listTournamentPlayers`, eliminating redundant DB query. |
| **PT-REV2-5** | [`PlayerPersistenceService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/player/service/PlayerPersistenceService.java#L32-L38) | Redundant duplicate ID parameters in `executeRegisterPlayer` | Removed redundant `tournamentId` and `userId` arguments from method signature and used `tournament.getId()` and `user.getId()`. |

---

## Post-Code-Generation Subagent & Hook Automation

1. **Subagent Job Registered**: `security-audit-fixer`
   - Equipped with read tools, write tools, and bash execution tools.
   - Automatically executes the security audit workflow upon code generation tasks.
2. **Git Hook Configured**: `.git/hooks/post-commit`
   - Automatically executes `scripts/post-codegen-security-audit.sh` after code changes to catch security regressions before push.
