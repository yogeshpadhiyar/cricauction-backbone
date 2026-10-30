# CricAuction-Backbone: Comprehensive Security Audit Report

**Target Repository:** `CricAuction-backbone`  
**Audit Scope:** Full Application Stack (`com.cricauction.*`), REST APIs, Authentication, RBAC, File Storage, Concurrency Boundaries  
**Audit Standard:** Cloudflare `security-audit` Standard / OWASP Top 10 API Security 2023  
**Audit Mode:** Source-First Defensive Review & Architectural Risk Assessment  
**Test Suite State:** 83/83 Automated Tests Passing (0 Failures, 0 Errors, 0 Skipped)  

---

## 1. Executive Summary

A comprehensive, source-grounded security review was conducted across the `CricAuction-backbone` codebase. The audit inspected trust boundaries, authentication flows, role-based authorization (RBAC), multi-tenant isolation, file upload pipelines, token lifecycles, and database concurrency controls.

The audit confirmed that earlier security fixes (such as credentialed CORS containment, team invite code redaction in roster listings, user profile contact PII masking, rate limiting anti-spoofing, and database unique constraints) remain active and unregressed.

However, deep-dive source tracing revealed **5 new confirmed vulnerabilities**:
1. **HIGH Severity (`SEC-AUTH-DEACTIVATED-USER-LOGIN-BYPASS`)**: Deactivated/suspended users can bypass account suspension and obtain valid JWT tokens via `/api/v1/auth/otp/verify`.
2. **HIGH Severity (`SEC-AUTH-DEACTIVATED-USER-ACTIVE-JWT-RETENTION`)**: `JwtAuthenticationFilter` omits `userDetails.isEnabled()` validation, allowing suspended users to continue making privileged API calls with existing 24-hour tokens.
3. **MEDIUM Severity (`SEC-TEAM-MULTI-CAPTAIN-COLLUSION`)**: Missing check in `validateJoinTeam` allows a single user to become captain of multiple competing teams in the same tournament via invite code.
4. **MEDIUM Severity (`SEC-AUTH-PASSWORD-RESET-NULL-MOBILE-DOS`)**: `forgotPassword` crashes with an unhandled exception for email-registered users due to unchecked `user.getMobile()` nullability.
5. **LOW Severity (`SEC-STORAGE-UPLOAD-UNCONFINED-ROOT`)**: `LocalFileStorageStrategy.uploadFile` lacks directory root containment assertion (`targetDir.startsWith(baseRoot)`).

---

## 2. Findings Matrix

| Severity | Identifier | Title | Category | CWE / OWASP | Exploitable Preconditions |
|:---:|:---|:---|:---|:---|:---|
| **HIGH** | `SEC-AUTH-DEACTIVATED-USER-LOGIN-BYPASS` | Suspended Account Bypass via OTP Verification | Authentication / RBAC | CWE-284, API2:2023 | User has been marked `isActive = false` |
| **HIGH** | `SEC-AUTH-DEACTIVATED-USER-ACTIVE-JWT-RETENTION` | Deactivated Users Retain Full API Access via Active JWTs | Broken Access Control | CWE-613, API1:2023 | User has valid unexpired JWT token |
| **MEDIUM** | `SEC-TEAM-MULTI-CAPTAIN-COLLUSION` | Multi-Team Captain Assignment & Auction Collusion | Business Logic / BOLA | CWE-840, API5:2023 | User possesses invite codes for 2+ teams |
| **MEDIUM** | `SEC-AUTH-PASSWORD-RESET-NULL-MOBILE-DOS` | Password Reset DoS for Email-Only Accounts | Exception Handling / Auth | CWE-476, API4:2023 | User registered with email and null mobile |
| **LOW** | `SEC-STORAGE-UPLOAD-UNCONFINED-ROOT` | Missing Directory Root Containment in `uploadFile` | Path Traversal / Hardening | CWE-22, CWE-73 | Subdirectory string contains path traversal |

---

## 3. Vulnerability Deep Dive & Evidence

### 1. `SEC-AUTH-DEACTIVATED-USER-LOGIN-BYPASS` (HIGH)
- **File:** [`AuthServiceImpl.java:L130-140`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/service/impl/AuthServiceImpl.java#L130-L140)
- **Category:** Authentication / Broken Access Control
- **CWE:** CWE-284 (Improper Access Control), CWE-287 (Improper Authentication)
- **Vulnerability Trace:**
  ```java
  // AuthServiceImpl.java
  if ("login".equalsIgnoreCase(purpose)) {
      User user = userOpt.orElseGet(() ->
              authPersistenceService.createOtpUser(target, UserRole.PLAYER, null)
      );

      String accessToken = jwtTokenProvider.generateAccessToken(user);
      return VerifyOtpResponse.builder()
              .isVerified(true)
              .accessToken(accessToken)
              .build();
  }
  ```
- **Mechanics:**
  In all standard login endpoints (`signIn`, `loginWithOtp`, `saveOrUpdateGoogleUser`), `user.isActive()` is strictly verified and throws `UnauthorizedException("ACCOUNT_SUSPENDED")`.
  However, `/api/v1/auth/otp/verify` is an unauthenticated endpoint. When invoked with `purpose="login"`, it verifies the OTP code and immediately signs a JWT token for `user`, completely omitting any check on `user.isActive()`.
- **Impact:**
  A banned, rogue, or suspended user can bypass account deactivation by authenticating through `/api/v1/auth/otp/verify`, allowing full access to all player, organizer, or captain operations.
- **Defensive Patch:**
  ```java
  if ("login".equalsIgnoreCase(purpose)) {
      User user = userOpt.orElseGet(() ->
              authPersistenceService.createOtpUser(target, UserRole.PLAYER, null)
      );

      if (!user.isActive()) {
          throw new UnauthorizedException("ACCOUNT_SUSPENDED", "Your account has been deactivated. Please contact support.");
      }

      String accessToken = jwtTokenProvider.generateAccessToken(user);
      ...
  }
  ```

---

### 2. `SEC-AUTH-DEACTIVATED-USER-ACTIVE-JWT-RETENTION` (HIGH)
- **File:** [`JwtAuthenticationFilter.java:L38-46`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/security/JwtAuthenticationFilter.java#L38-L46)
- **Category:** Broken Access Control / Session Management
- **CWE:** CWE-613 (Insufficient Session Expiration), CWE-284 (Improper Access Control)
- **Vulnerability Trace:**
  ```java
  // JwtAuthenticationFilter.java
  Long userId = Long.parseLong(claims.getSubject());
  UserDetails userDetails = userDetailsService.loadUserById(userId);

  UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
  authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

  SecurityContextHolder.getContext().setAuthentication(authentication);
  ```
- **Mechanics:**
  When a request with a valid JWT token arrives, `loadUserById(userId)` builds a `UserPrincipal`. In `UserPrincipal`:
  `isAccountNonLocked()` and `isEnabled()` return `this.isActive`.
  However, in `JwtAuthenticationFilter`, the `UsernamePasswordAuthenticationToken` is created directly and injected into `SecurityContextHolder` without verifying `userDetails.isEnabled()`. Because this bypasses `AuthenticationManager`, Spring Security never checks whether the account is disabled.
- **Impact:**
  When an administrator deactivates an account in the database (e.g. following a compromise or fraudulent auction bidding), the attacker's existing JWT token remains active and authorized for the full duration of its validity (24 hours).
- **Defensive Patch:**
  ```java
  UserDetails userDetails = userDetailsService.loadUserById(userId);
  if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
      log.warn("[SECURITY] Deactivated/locked user ID [{}] attempted API access with JWT. Rejecting.", userId);
      filterChain.doFilter(request, response);
      return;
  }
  ```

---

### 3. `SEC-TEAM-MULTI-CAPTAIN-COLLUSION` (MEDIUM)
- **File:** [`TeamQueryService.java:L225-231`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/team/service/TeamQueryService.java#L225-L231), [`TeamPersistenceService.java:L82-95`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/team/service/TeamPersistenceService.java#L82-L95)
- **Category:** Business Logic / Auction Integrity
- **CWE:** CWE-840 (Business Logic Errors), CWE-284 (Improper Access Control)
- **Mechanics:**
  In `TeamQueryService.validateSelfRegisterCaptain`, the system enforces a strict business rule:
  ```java
  if (teamRepository.existsByTournamentIdAndCaptainId(tournament.getId(), currentUser.getId())) {
      throw new ConflictException("DUPLICATE_CAPTAIN_REGISTRATION", "You are already registered as a captain for a team in this tournament.");
  }
  ```
  However, in `validateJoinTeam` (`POST /api/v1/teams/join`), the code only checks if the *target team* has a captain. It never checks if `currentUser` is already a captain of *another team* in that same tournament.
- **Impact:**
  A user can accept multiple captain invite codes for different teams within the same tournament. During the live player auction, a single user controls the budget, bidding, and roster choices of multiple competing teams, creating an auction collusion and price-fixing risk.
- **Defensive Patch:**
  In `TeamQueryService.validateJoinTeam`:
  ```java
  if (teamRepository.existsByTournamentIdAndCaptainId(team.getTournament().getId(), currentUser.getId())
          && (team.getCaptainId() == null || !team.getCaptainId().equals(currentUser.getId()))) {
      throw new ConflictException("DUPLICATE_CAPTAIN_REGISTRATION", "You are already registered as a captain for another team in this tournament.");
  }
  ```

---

### 4. `SEC-AUTH-PASSWORD-RESET-NULL-MOBILE-DOS` (MEDIUM)
- **File:** [`AuthServiceImpl.java:L206-211`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/service/impl/AuthServiceImpl.java#L206-L211)
- **Category:** Exception Handling / Availability
- **CWE:** CWE-476 (NULL Pointer Dereference / Unchecked Input)
- **Mechanics:**
  In `AuthServiceImpl.forgotPassword(ForgotPasswordRequest request)`:
  ```java
  User user = authQueryService.findByIdentifierForPasswordReset(identifier);
  otpService.sendOtp(user.getMobile(), "password_reset");
  ```
  When an email-registered user (via Google OAuth or Email OTP) triggers password reset, `user.getMobile()` is `null`. Passing `null` to `otpService.sendOtp` throws `BadRequestException("INVALID_IDENTIFIER")`, permanently blocking password reset for all email-only accounts.
- **Impact:**
  Denial of service for password reset functionality affecting all users without a registered mobile number.
- **Defensive Patch:**
  ```java
  String targetContact = (user.getMobile() != null && !user.getMobile().isBlank()) ? user.getMobile() : user.getEmail();
  boolean isEmail = targetContact.contains("@");
  otpService.sendOtp(targetContact, "password_reset");

  return ForgotPasswordResponse.builder()
          .maskedContact(isEmail ? maskEmail(targetContact) : maskMobile(targetContact))
          .deliveryMethod(isEmail ? "EMAIL" : "SMS")
          .build();
  ```

---

### 5. `SEC-STORAGE-UPLOAD-UNCONFINED-ROOT` (LOW)
- **File:** [`LocalFileStorageStrategy.java:L81-85`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/profile/storage/impl/LocalFileStorageStrategy.java#L81-L85)
- **Category:** Path Traversal / Defensive Hardening
- **CWE:** CWE-22 (Improper Limitation of a Pathname to a Restricted Directory)
- **Mechanics:**
  In `LocalFileStorageStrategy.deleteFile`, the root directory is strictly validated:
  `if (!filePath.startsWith(baseRoot)) return;`
  In `uploadFile(MultipartFile file, String subDirectory)`, `targetDir` is normalized but not checked against `baseRoot`. While current callers pass `"avatars"` and `"teams/" + teamId`, future callers passing unsanitized subdirectories could write files outside `baseDir`.
- **Defensive Patch:**
  ```java
  Path baseRoot = Paths.get(baseDir).toAbsolutePath().normalize();
  Path targetDir = baseRoot.resolve(subDirectory.startsWith("/") ? subDirectory.substring(1) : subDirectory).normalize();
  if (!targetDir.startsWith(baseRoot)) {
      throw new BadRequestException("INVALID_PATH", "Upload path must stay within the uploads directory.");
  }
  Files.createDirectories(targetDir);
  ```

---

## 4. Remediation Plan

1. **Patch `AuthServiceImpl.java`**:
   - Add `user.isActive()` validation in `verifyOtp` when `purpose="login"`.
   - Update `forgotPassword` to fall back to `user.getEmail()` if `user.getMobile()` is null.
2. **Patch `JwtAuthenticationFilter.java`**:
   - Add `if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked())` check before populating `SecurityContextHolder`.
3. **Patch `TeamQueryService.java`**:
   - Check `teamRepository.existsByTournamentIdAndCaptainId(...)` in `validateJoinTeam`.
4. **Patch `LocalFileStorageStrategy.java`**:
   - Assert `targetDir.startsWith(baseRoot)` in `uploadFile`.
5. **Add Regression Tests**:
   - Unit test verifying deactivated users are rejected in `verifyOtp`.
   - Unit test verifying deactivated users are rejected in `JwtAuthenticationFilter`.
   - Unit test verifying a captain cannot claim two teams in the same tournament.
   - Unit test verifying email-only password reset.

---

## 5. Implementation & Verification Summary

### Status: ALL 5 VULNERABILITIES REMEDIATED & VERIFIED
- **Automated Test Suite Status:** `Tests run: 83, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS)

| Finding ID | Status | File Modified | Verification Test |
|---|---|---|---|
| `SEC-AUTH-DEACTIVATED-USER-LOGIN-BYPASS` | **RESOLVED** | [`AuthServiceImpl.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/service/impl/AuthServiceImpl.java) | `AuthControllerTest.verifyOtp_DeactivatedUser_Rejected` (returns 401 `ACCOUNT_SUSPENDED`) |
| `SEC-AUTH-DEACTIVATED-USER-ACTIVE-JWT-RETENTION` | **RESOLVED** | [`JwtAuthenticationFilter.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/security/JwtAuthenticationFilter.java) | `AuthControllerTest.jwtAuth_DeactivatedUser_Rejected` (immediately revokes access, returns 401) |
| `SEC-TEAM-MULTI-CAPTAIN-COLLUSION` | **RESOLVED** | [`TeamQueryService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/team/service/TeamQueryService.java) & [`TeamPersistenceService.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/team/service/TeamPersistenceService.java) | `TeamControllerTest.testCaptainCannotClaimMultipleTeamsInSameTournament` (returns 409 `ALREADY_A_CAPTAIN`) |
| `SEC-AUTH-PASSWORD-RESET-NULL-MOBILE-DOS` | **RESOLVED** | [`AuthServiceImpl.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/service/impl/AuthServiceImpl.java) | `AuthControllerTest.forgotPassword_NullMobile_SuccessViaEmail` (dispatches OTP to email with `maskEmail`) |
| `SEC-STORAGE-UPLOAD-UNCONFINED-ROOT` | **RESOLVED** | [`LocalFileStorageStrategy.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/profile/storage/impl/LocalFileStorageStrategy.java) | `UserProfileControllerTest.testUploadFile_BlocksPathTraversal` (rejects traversal with 400 `INVALID_PATH`) |

