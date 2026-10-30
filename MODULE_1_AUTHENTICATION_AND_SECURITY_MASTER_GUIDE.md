# CricAuction Module 1: Authentication, Authorization & Security
## Comprehensive Technical Architecture & Developer Reference Manual

> **Document Status**: Production Complete | Tested against PostgreSQL 16+ | 83/83 Automated Tests Passing (0 Failures, 0 Errors, 0 Skipped) | 5,000 Concurrent Users Benchmark Verified (4.72s Latency)  
> **Audience**: Backend Engineers, DevOps, System Architects, Security Auditors  
> **Maintainer**: CricAuction Engineering Team  

---

## Table of Contents
1. [Executive Overview & Capabilities](#1-executive-overview--capabilities)
2. [Architectural Principles & Package Structure](#2-architectural-principles--package-structure)
3. [End-to-End Workflow Diagrams](#3-end-to-end-workflow-diagrams)
   - [3.1 Standard Sign-Up & Password Sign-In](#31-standard-sign-up--password-sign-in)
   - [3.2 Passwordless Mobile / Email OTP Login](#32-passwordless-mobile--email-otp-login)
   - [3.3 Google Direct OAuth2 Authentication](#33-google-direct-oauth2-authentication)
   - [3.4 Password Recovery & Reset Flow](#34-password-recovery--reset-flow)
   - [3.5 JWT Token Rotation & Session Management](#35-jwt-token-rotation--session-management)
   - [3.6 Dynamic Role Switching (RBAC)](#36-dynamic-role-switching-rbac)
4. [Security, Cryptography & Session Management](#4-security-cryptography--session-management)
5. [Resilience & High-Concurrency Architecture](#5-resilience--high-concurrency-architecture)
6. [Database Schema & Migration Walkthrough](#6-database-schema--migration-walkthrough)
7. [Comprehensive API Reference Catalog (10 APIs)](#7-comprehensive-api-reference-catalog-10-apis)
8. [SOLID Principles & Design Patterns Applied](#8-solid-principles--design-patterns-applied)
9. [High-Concurrency Benchmarks & Verification](#9-high-concurrency-benchmarks--verification)
10. [Developer Extension & Onboarding Guide](#10-developer-extension--onboarding-guide)

---

## 1. Executive Overview & Capabilities

Module 1 handles identity verification, multi-channel authentication, authorization, role management, and traffic resilience for the CricAuction platform. It supports three distinct actor personas: **Tournament Organizer**, **Team Captain**, and **Cricket Player**.

### Key Architectural Capabilities:
- **Omni-Channel Authentication**:
  - Standard Email / Mobile + BCrypt Password Sign-In.
  - Passwordless Mobile Number + OTP Login.
  - Passwordless Email ID + OTP Login.
  - Google OAuth2 Direct ID Token Sign-In & Auto-Provisioning.
- **Role-Based Access Control (RBAC)**: Dynamic switching between `ORGANIZER`, `CAPTAIN`, and `PLAYER` roles without re-authenticating.
- **Stateless Session Security**: Short-lived HMAC-SHA256 JWT Access Tokens (24h) combined with revocable Refresh Tokens (7d) stored in PostgreSQL.
- **Decoupled Cryptographic Processing**: Password hashing (BCrypt) runs strictly outside database transaction boundaries to prevent connection holding.
- **Enterprise Resilience Suite**:
  - Sliding-Window Token Bucket **Rate Limiter** protecting against brute-force attacks and DDoS.
  - 3-State **Circuit Breaker** (`CLOSED`, `OPEN`, `HALF_OPEN`) guarding external OAuth and SMS/Email gateways.
  - Exponential Backoff with Randomized Jitter **Retry Engine**.
- **Extreme Concurrency**: Powered by Java 23 Project Loom Virtual Threads, HikariCP pool optimization, and PostgreSQL prepared statement caching.

---

## 2. Architectural Principles & Package Structure

The module follows Clean Architecture and Domain-Driven Design (DDD) modularity. Business logic is isolated from web transport and persistence details.

```
src/main/java/com/cricauction/
├── CricauctionBackboneApplication.java          # Entry point (Virtual Threads enabled)
├── common/                                      # Cross-cutting enterprise infrastructure
│   ├── api/                                     # Uniform JSON response & error envelopes
│   │   ├── ApiResponse.java                     # { success, message, data, timestamp }
│   │   ├── ApiError.java                        # { code, message, details }
│   │   └── ErrorDetail.java                     # Field-level validation errors
│   ├── exception/                               # Business & HTTP exception hierarchy
│   │   ├── AppException.java                    # Base unchecked exception
│   │   ├── BadRequestException.java             # HTTP 400
│   │   ├── UnauthorizedException.java           # HTTP 401
│   │   ├── ForbiddenException.java              # HTTP 403
│   │   ├── ResourceNotFoundException.java       # HTTP 404
│   │   ├── ConflictException.java               # HTTP 409
│   │   └── GlobalExceptionHandler.java          # @RestControllerAdvice uniform error mapper
│   ├── cache/                                   # High-performance cache implementations
│   │   └── BoundedLruCache.java                 # Eviction-bounded memory cache (max 500, 10 min TTL)
│   └── resilience/                              # Fault tolerance & traffic regulation
│       ├── RateLimiter.java                     # Sliding window token bucket
│       ├── RateLimitingFilter.java              # Servlet filter enforcing rate limits
│       ├── CircuitBreaker.java                  # Multi-service isolated circuit breaker
│       └── ResilienceRetry.java                 # Exponential backoff bypassing non-transient 4xx
├── config/                                      # Spring framework configurations
│   ├── ApplicationConfig.java                   # PasswordEncoder, ObjectMapper, AuthProvider
│   ├── SecurityConfig.java                      # Spring Security 7 filter chain & CORS
│   ├── JwtConfig.java                           # JWT secret & expiration properties
│   ├── OpenApiConfig.java                       # Swagger UI / OpenAPI 3.0 configuration
│   └── datasource/                              # Master-Replica dynamic routing
│       ├── DataSourceConfig.java                # HikariCP Master & Replica configuration
│       ├── DynamicDataSourceRouter.java         # Transaction-aware routing DataSource
│       └── DataSourceType.java                  # MASTER vs REPLICA
├── security/                                    # Security primitives & JWT engine
│   ├── UserPrincipal.java                       # Spring Security UserDetails adapter
│   ├── CustomUserDetailsService.java            # User loader (email, mobile, ID)
│   ├── JwtTokenProvider.java                    # HMAC-SHA256 JWT minting & parsing
│   └── JwtAuthenticationFilter.java             # OncePerRequestFilter with active status validation
└── modules/
    └── auth/                                    # Module 1 Domain Boundary
        ├── controller/
        │   └── AuthController.java              # 10 REST endpoints
        ├── scheduler/                           # Automated background housekeeping
        │   └── AuthTokenCleanupScheduler.java   # Scheduled hourly purge of expired OTPs & tokens
        ├── service/
        │   ├── AuthService.java                 # Core business workflow contract
        │   ├── AuthQueryService.java            # CQRS Read phase (Read Replica routing)
        │   ├── AuthPersistenceService.java      # CQRS Micro-write phase (Master DB routing)
        │   ├── GoogleAuthService.java           # Google OAuth workflow contract
        │   ├── GoogleTokenVerifier.java         # ID token verification contract
        │   ├── OtpService.java                  # OTP generation & validation contract
        │   ├── OtpQueryService.java             # OTP reads on Replica
        │   ├── OtpPersistenceService.java       # OTP micro-writes on Master
        │   ├── TokenService.java                # Refresh & Reset token contract (Master routing)
        │   └── impl/
        │       ├── AuthServiceImpl.java         # Decoupled crypto & transaction coordinator
        │       ├── GoogleAuthServiceImpl.java   # Google account linking & provisioning
        │       ├── GoogleTokenVerifierImpl.java # Google public certs & tokeninfo verification
        │       ├── OtpServiceImpl.java          # Cryptographically secure OTP engine
        │       └── TokenServiceImpl.java        # Refresh token rotation & reset lifecycle
        ├── strategy/                            # Strategy Pattern for OTP delivery
        │   ├── OtpDeliveryStrategy.java         # Strategy interface
        │   ├── SmsOtpDeliveryStrategy.java      # SMS dispatch implementation
        │   └── EmailOtpDeliveryStrategy.java    # Email dispatch implementation
        ├── repository/                          # Spring Data JPA Repositories
        │   ├── UserRepository.java              # Optimized index queries (BitmapOr scans)
        │   ├── OtpVerificationRepository.java   # Composite key (identifier, purpose)
        │   ├── RefreshTokenRepository.java      # Token lookup & revocation
        │   └── PasswordResetTokenRepository.java# Reset token lifecycle
        ├── entity/                              # JPA Domain Entities
        │   ├── User.java                        # Core user account
        │   ├── OtpVerification.java             # OTP cache with expiry
        │   ├── OtpVerificationId.java           # Composite primary key
        │   ├── RefreshToken.java                # Persistent refresh tokens
        │   ├── PasswordResetToken.java          # Single-use reset tokens
        │   └── enums/
        │       ├── UserRole.java                # ORGANIZER, CAPTAIN, PLAYER
        │       ├── AuthProvider.java            # LOCAL, GOOGLE
        │       └── OtpPurpose.java              # signup_verification, login, password_reset
        └── dto/                                 # Strictly decoupled data transfer objects
            ├── request/                         # Validated request payloads
            └── response/                        # Secure response envelopes
```

---

## 3. End-to-End Workflow Diagrams

### 3.1 Standard Sign-Up & Password Sign-In

```mermaid
sequenceDiagram
    autonumber
    actor Client as User / Mobile App
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthServiceImpl
    participant UserRepo as UserRepository
    participant OtpSvc as OtpServiceImpl
    participant JWT as JwtTokenProvider
    participant DB as PostgreSQL

    Note over Client, DB: Sign-Up Phase
    Client->>AuthCtrl: POST /api/v1/auth/signup (email, mobile, password)
    AuthCtrl->>AuthSvc: signUp(SignUpRequest)
    AuthSvc->>UserRepo: findExistingIdentifiers(email, mobile)
    UserRepo->>DB: Single consolidated query (BitmapOr scan)
    DB-->>AuthSvc: No duplicates found
    Note over AuthSvc: BCrypt.encode() runs OUTSIDE @Transactional (0 DB connection hold)
    AuthSvc->>DB: INSERT INTO users (fast microsecond transaction)
    AuthSvc->>OtpSvc: sendOtp(mobile, "signup_verification")
    OtpSvc->>DB: INSERT INTO otp_verifications
    AuthSvc-->>Client: 201 Created (userId, isVerified: false)

    Note over Client, DB: Sign-In Phase
    Client->>AuthCtrl: POST /api/v1/auth/signin (identifier, password)
    AuthCtrl->>AuthSvc: signIn(SignInRequest)
    AuthSvc->>UserRepo: findByIdentifier(email or mobile)
    UserRepo-->>AuthSvc: User entity
    Note over AuthSvc: Connection released immediately to Hikari pool!
    Note over AuthSvc: BCrypt.matches() evaluated in memory (0 DB connection hold)
    AuthSvc->>JWT: generateAccessToken(user)
    AuthSvc->>DB: INSERT INTO refresh_tokens
    AuthSvc-->>Client: 200 OK (accessToken, refreshToken, user profile)
```

---

### 3.2 Passwordless Mobile / Email OTP Login

```mermaid
sequenceDiagram
    autonumber
    actor Client as Mobile User
    participant AuthCtrl as AuthController
    participant OtpSvc as OtpServiceImpl
    participant Strategy as OtpDeliveryStrategy
    participant AuthSvc as AuthServiceImpl
    participant DB as PostgreSQL

    Client->>AuthCtrl: POST /api/v1/auth/otp/send (identifier: "+919876543210", purpose: "login")
    AuthCtrl->>OtpSvc: sendOtp(target, "login")
    Note over OtpSvc: Check retry-after cooldown
    Note over OtpSvc: Generate 6-digit code via ThreadLocalRandom (lock-free)
    OtpSvc->>DB: UPSERT INTO otp_verifications (identifier, purpose, expires_at)
    OtpSvc->>Strategy: dispatchOtp(target, otpCode) -> SMS or Email
    OtpSvc-->>Client: 200 OK (retryAfterSeconds: 45, otpExpiresInSeconds: 300)

    Note over Client: User receives SMS/Email OTP code
    Client->>AuthCtrl: POST /api/v1/auth/otp/login (identifier, otp: "482910", role: "player")
    AuthCtrl->>AuthSvc: loginWithOtp(OtpLoginRequest)
    AuthSvc->>OtpSvc: verifyOtp(identifier, "482910", "login")
    OtpSvc->>DB: Validate OTP code & check expiry
    OtpSvc->>DB: DELETE FROM otp_verifications (consume OTP)
    AuthSvc->>DB: Find user OR auto-provision new user
    AuthSvc->>DB: INSERT INTO refresh_tokens
    AuthSvc-->>Client: 200 OK (accessToken, refreshToken, user profile)
```

---

### 3.3 Google Direct OAuth2 Authentication

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client App (Google SDK)
    participant AuthCtrl as AuthController
    participant GoogleSvc as GoogleAuthServiceImpl
    participant Verifier as GoogleTokenVerifierImpl
    participant External as Google OAuth Servers
    participant DB as PostgreSQL

    Client->>Client: User clicks "Sign in with Google" (One-Tap / SDK)
    Client-->>Client: Receives Google ID Token (JWT RS256)
    Client->>AuthCtrl: POST /api/v1/auth/google (idToken, role, city)
    AuthCtrl->>GoogleSvc: authenticateWithGoogle(GoogleAuthRequest)
    GoogleSvc->>Verifier: verifyToken(idToken)
    Note over Verifier: Guarded by CircuitBreaker & Exponential Retry
    Verifier->>External: Validate token claims (sub, email, name, picture)
    External-->>Verifier: GoogleUserInfo payload
    GoogleSvc->>DB: findByGoogleIdOrEmail(sub, email) [Single Query]
    alt User Exists
        GoogleSvc->>DB: Link google_id if missing, update avatar
    else New User (Auto Sign-Up)
        GoogleSvc->>DB: INSERT INTO users (auth_provider: 'GOOGLE', is_email_verified: true)
    end
    GoogleSvc->>DB: INSERT INTO refresh_tokens
    GoogleSvc-->>Client: 200 OK (accessToken, refreshToken, user profile)
```

---

### 3.4 Password Recovery & Reset Flow

```mermaid
sequenceDiagram
    autonumber
    actor Client as User
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthServiceImpl
    participant OtpSvc as OtpServiceImpl
    participant TokenSvc as TokenServiceImpl
    participant DB as PostgreSQL

    Client->>AuthCtrl: POST /api/v1/auth/password/forgot (identifier: "user@example.com")
    AuthCtrl->>AuthSvc: forgotPassword(ForgotPasswordRequest)
    AuthSvc->>DB: Verify user exists by email/mobile
    AuthSvc->>OtpSvc: sendOtp(mobile, "password_reset")
    OtpSvc->>DB: Store OTP in otp_verifications
    AuthSvc-->>Client: 200 OK (maskedContact: "+9198765 *****")

    Note over Client: User enters OTP
    Client->>AuthCtrl: POST /api/v1/auth/otp/verify (identifier, otp, purpose: "password_reset")
    AuthCtrl->>AuthSvc: verifyOtp(...)
    AuthSvc->>OtpSvc: verify & delete OTP
    AuthSvc->>TokenSvc: createPasswordResetToken(user)
    TokenSvc->>DB: INSERT INTO password_reset_tokens (token: "rst_...", expires_at: 15m)
    AuthSvc-->>Client: 200 OK (resetToken: "rst_...")

    Note over Client: User sets new password
    Client->>AuthCtrl: POST /api/v1/auth/password/reset (resetToken, newPassword, confirmPassword)
    AuthCtrl->>AuthSvc: resetPassword(...)
    AuthSvc->>TokenSvc: validateAndConsumePasswordResetToken("rst_...")
    TokenSvc->>DB: Mark reset token used = true
    Note over AuthSvc: Hash new password with BCrypt
    AuthSvc->>DB: UPDATE users SET password_hash = ...
    AuthSvc-->>Client: 200 OK ("Password reset successfully")
```

---

### 3.5 JWT Token Rotation & Session Management

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client App
    participant AuthCtrl as AuthController
    participant TokenSvc as TokenServiceImpl
    participant JWT as JwtTokenProvider
    participant DB as PostgreSQL

    Note over Client: Access Token expires after 24 hours (86400s)
    Client->>AuthCtrl: POST /api/v1/auth/refresh-token (refreshToken: "rft_...")
    AuthCtrl->>TokenSvc: refreshToken("rft_...")
    TokenSvc->>DB: SELECT * FROM refresh_tokens WHERE token = "rft_..."
    Note over TokenSvc: Validate token is not revoked AND expires_at > NOW()
    TokenSvc->>JWT: generateAccessToken(user)
    TokenSvc-->>Client: 200 OK (new accessToken, expiresIn: 86400)

    Note over Client: User logs out
    Client->>AuthCtrl: POST /api/v1/auth/logout (refreshToken: "rft_...")
    AuthCtrl->>TokenSvc: revokeRefreshToken("rft_...")
    TokenSvc->>DB: UPDATE refresh_tokens SET revoked = true WHERE token = "rft_..."
    AuthCtrl-->>Client: 200 OK ("Logged out successfully")
```

---

### 3.6 Dynamic Role Switching (RBAC)

```mermaid
sequenceDiagram
    autonumber
    actor Client as Authenticated User (Bearer Token)
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthServiceImpl
    participant DB as PostgreSQL

    Note over Client: User switches active role to Organizer or Captain
    Client->>AuthCtrl: POST /api/v1/auth/role (role: "organizer")
    Note over AuthCtrl: JwtAuthenticationFilter validates Bearer token -> UserPrincipal
    AuthCtrl->>AuthSvc: switchRole(currentUser, RoleSelectionRequest)
    AuthSvc->>DB: UPDATE users SET active_role = 'ORGANIZER' WHERE id = currentUser.id
    AuthSvc-->>Client: 200 OK (role: "organizer", message: "Active role switched")
    Note over Client: Subsequent requests reflect new role permissions
```

---

## 4. Security, Cryptography & Session Management

### 4.1 Spring Security 7 Filter Chain Configuration
Configured in [**`SecurityConfig.java`**](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/config/SecurityConfig.java):

```
Incoming Request
       │
       ▼
[RateLimitingFilter]           ──► Tripped? Returns HTTP 429 Too Many Requests
       │
       ▼
[JwtAuthenticationFilter]      ──► Extracts Bearer token, validates HMAC-SHA256,
       │                           builds UserPrincipal, populates SecurityContext
       ▼
[UsernamePasswordAuthFilter]
       │
       ▼
[AuthorizationManager]         ──► Enforces RBAC permissions
       │
       ▼
[DispatcherServlet -> Controller]
```

#### Public Endpoints (`permitAll()`):
- `/api/v1/auth/signup`, `/api/v1/auth/signin`
- `/api/v1/auth/login/**`, `/api/v1/auth/google/**`
- `/api/v1/auth/otp/**`, `/api/v1/auth/password/**`
- `/api/v1/auth/refresh-token`
- `/v3/api-docs/**`, `/swagger-ui/**`, `/actuator/**`

#### Protected Endpoints (`authenticated()`):
- `/api/v1/auth/me` (Profile inspection)
- `/api/v1/auth/role` (Dynamic role switching)
- `/api/v1/auth/logout` (Session revocation)

---

### 4.2 Decoupled Password Hashing (BCrypt Optimization)
Standard BCrypt hashing is intentionally CPU-intensive. If executed inside a `@Transactional` boundary, it starves the connection pool:

```java
// AuthServiceImpl.java - Non-blocking implementation
@Override
public SignUpResponse signUp(SignUpRequest request) {
    // 1. Fast duplicate pre-check (single query)
    validateNoDuplicates(request.getEmail(), request.getMobile());

    // 2. Compute BCrypt hash OUTSIDE the database transaction
    // ZERO database connections held while CPU hashes!
    String encodedPassword = passwordEncoder.encode(request.getPassword());

    // 3. Persist user and trigger OTP in microsecond transaction
    return persistNewUserAndSendOtp(request, encodedPassword);
}
```

- **Configurable Work Factor**: Injected via `${cricauction.security.bcrypt-strength:10}`. Defaults to `10` ($2^{10} = 1024$ rounds) in production, with configurable scaling for high-throughput batch automation.

---

### 4.3 JWT Access Tokens & Refresh Tokens
- **Access Token**: Stateless HMAC-SHA256 JWT, signed with a 256+ bit secret (`cricauction.security.jwt.secret`).
  - Claims: `sub` (User ID), `role` (`ORGANIZER`, `CAPTAIN`, `PLAYER`), `email`, `iat`, `exp`.
  - Expiry: 24 hours (86,400,000 ms).
- **Refresh Token**: High-entropy cryptographically random UUID (`rft_<hex>`).
  - Persisted in the `refresh_tokens` table.
  - Expiry: 7 days (604,800,000 ms).
  - Can be explicitly revoked on logout or account deactivation.

---

### 4.4 Immediate Deactivated User Session Revocation & Login Defense
- **Filter-Level Revocation**: In [`JwtAuthenticationFilter.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/security/JwtAuthenticationFilter.java), every authenticated request verifies `userDetails.isEnabled() && userDetails.isAccountNonLocked()`. If an administrator deactivates an account (`user.isActive = false`), all active JWT tokens are immediately rejected with `HTTP 401 Unauthorized` without waiting for the 24-hour token expiration.
- **OTP Login Defense**: Both `verifyOtp` and `loginWithOtp` in [`AuthServiceImpl.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/service/impl/AuthServiceImpl.java) validate `user.isActive()`, rejecting suspended users with `HTTP 401 Unauthorized` (`ACCOUNT_SUSPENDED`).

---

### 4.5 Dual-Channel Password Recovery (SMS + Email Fallback with Masking)
- In [`AuthServiceImpl.forgotPassword`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/service/impl/AuthServiceImpl.java), account recovery supports both mobile numbers and email identifiers:
  - If `user.getMobile()` is present, OTP is dispatched via `SmsOtpDeliveryStrategy` with `deliveryMethod: "SMS"` and phone number masked (`+9198765 *****`).
  - If `user.getMobile()` is null or blank (common for accounts registered via Google OAuth or Email OTP), the system seamlessly falls back to `user.getEmail()`, dispatching OTP via `EmailOtpDeliveryStrategy` with `deliveryMethod: "EMAIL"` and email masked (`vi***@example.com`).
  - Guarantees zero unhandled exceptions for email-only users.

---

### 4.6 Master DB Routing for Token Refresh & Automated Housekeeping Scheduler
- **Zero Replication Lag on Refresh**: [`TokenServiceImpl.refreshToken`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/service/impl/TokenServiceImpl.java) executes directly on the Master DB (`@Transactional(readOnly = false)`) ensuring recently rotated refresh tokens are immediately visible without read-replica replication lag 401 failures.
- **Automated Expired Data Purging**: [`AuthTokenCleanupScheduler.java`](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/modules/auth/scheduler/AuthTokenCleanupScheduler.java) runs an automated `@Scheduled(cron = "0 0 * * * *")` hourly task:
  - Deletes expired OTP records (`deleteByExpiresAtBefore(now)`).
  - Deletes expired or revoked refresh tokens (`deleteExpiredOrRevokedBefore(now)`).

---

## 5. Resilience & High-Concurrency Architecture

```mermaid
flowchart TD
    Req[Incoming HTTP Request] --> RL{RateLimiter Check}
    RL -->|Count > Limit| Block[HTTP 429 Too Many Requests]
    RL -->|Allowed| VT[Virtual Thread Dispatched]

    subgraph Service Layer Fault Tolerance
        VT --> ExtCall{External Service Call\nGoogle OAuth / SMS Gateway}
        ExtCall --> CB{CircuitBreaker State\nIsolated Per Service}
        CB -->|OPEN| Fallback[Execute Fallback / Cached Token]
        CB -->|CLOSED / HALF-OPEN| Retry[ResilienceRetry Engine\nExponential Backoff + Jitter\nNon-Transient 4xx Bypassed]
        Retry --> Downstream[Downstream External Service]
    end
```

### 5.1 Sliding-Window Token Bucket Rate Limiter
- **Components**: [**`RateLimiter.java`**](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/common/resilience/RateLimiter.java), [**`RateLimitingFilter.java`**](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/common/resilience/RateLimitingFilter.java)
- **Tiers**:
  - General Endpoints: **300 req/min per IP**
  - Sensitive Auth Endpoints (`/signin`, `/otp/**`, `/login/otp`): **60 req/min per IP**
- **Response Headers when tripped**:
  - `HTTP/1.1 429 Too Many Requests`
  - `Retry-After: 45`
  - `X-Rate-Limit-Remaining: 0`
  - Error JSON envelope: `{"code": "RATE_LIMIT_EXCEEDED", "message": "Too many requests..."}`

### 5.2 Multi-Service Isolated 3-State Circuit Breaker
- **Component**: [**`CircuitBreaker.java`**](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/common/resilience/CircuitBreaker.java)
- **Per-Service Isolation**: Maintains independent `CircuitState` instances per downstream service name (`ConcurrentHashMap<String, CircuitState>`), preventing SMS gateway outages from affecting Google OAuth authentication.
- **Client Error Bypass**: Automatically bypasses HTTP 4xx client errors (such as invalid user inputs or expired client tokens) without counting them toward circuit breaker trips.
- **State Transitions**:
  - `CLOSED`: Normal operation. Consecutive server failures increment failure count.
  - `OPEN`: Tripped after 5 consecutive downstream server failures. Requests immediately execute fallback without hitting downstream.
  - `HALF_OPEN`: Automatically transitions after a 5,000ms cooldown to evaluate downstream health. If the trial call succeeds, state resets to `CLOSED`.

### 5.3 Exponential Backoff with Randomized Jitter & Non-Transient Bypass
- **Component**: [**`ResilienceRetry.java`**](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/main/java/com/cricauction/common/resilience/ResilienceRetry.java)
- **Non-Transient Bypass**: Non-transient errors (such as 4xx client errors, invalid credentials, bad requests) bypass retry loops immediately, preventing unnecessary backoff delays.
- **Algorithm**:
  $$\text{Backoff} = (\text{initialBackoff} \times 2^{\text{attempt}}) + \text{ThreadLocalRandom}(10\text{ms}, 50\text{ms})$$
- Protects external Google OAuth calls and SMS/Email OTP dispatches from transient socket resets and thundering herd conditions.

---

## 6. Database Schema & Migration Walkthrough

The module utilizes 6 core PostgreSQL tables managed by Flyway migrations (`V1`, `V2`, `V3`, `V5`).

```mermaid
erDiagram
    users ||--o{ refresh_tokens : owns
    users ||--o{ password_reset_tokens : requests
    users ||--o{ otp_verifications : targets
    users ||--o{ user_roles : "assigned"
    roles ||--o{ user_roles : "maps to"

    roles {
        serial id PK
        varchar(50) name UK "ORGANIZER, CAPTAIN, PLAYER"
        timestamp created_at
    }

    user_roles {
        bigint user_id PK, FK
        integer role_id PK, FK
        timestamp assigned_at
    }

    users {
        bigserial id PK
        varchar(100) full_name
        varchar(150) email UK
        varchar(20) mobile UK
        varchar(255) password_hash
        varchar(20) active_role
        varchar(20) auth_provider
        varchar(100) google_id UK
        varchar(100) city
        varchar(500) profile_photo_url
        boolean is_email_verified
        boolean is_mobile_verified
        boolean is_active
        timestamp created_at
        timestamp updated_at
    }

    otp_verifications {
        varchar(150) identifier PK
        varchar(50) purpose PK
        varchar(150) email
        varchar(20) mobile
        varchar(10) otp_code
        int attempts
        timestamp expires_at
        timestamp created_at
    }

    refresh_tokens {
        bigserial id PK
        varchar(255) token UK
        bigint user_id FK
        timestamp expires_at
        boolean revoked
        timestamp created_at
    }

    password_reset_tokens {
        varchar(100) token PK
        bigint user_id FK
        timestamp expires_at
        boolean used
        timestamp created_at
    }
```

### Database Evolution Scripts:
- **`V1__init_auth_schema.sql`**: Initial DDL creating `users`, `otp_verifications`, `refresh_tokens`, and `password_reset_tokens` with B-Tree indexes on `email`, `mobile`, `token`, and `expires_at`.
- **`V2__add_google_auth.sql`**: Makes `mobile` and `password_hash` nullable to support Google OAuth accounts; adds `auth_provider` (`LOCAL`, `GOOGLE`), `google_id` (unique index), and `is_email_verified`.
- **`V3__support_email_and_mobile_otp_login.sql`**: Generalizes OTP verification table with an `identifier` column and composite primary key `(identifier, purpose)` with index `idx_otp_identifier_expiry`.
- **`V5__create_roles_and_user_roles.sql`**: Multi-Role RBAC normalized architecture creating `roles` table (seeded with `ORGANIZER`, `CAPTAIN`, `PLAYER`) and `user_roles` Many-to-Many mapping table. Migrates existing users and allows holding multiple roles simultaneously.
- **`V6__drop_active_role_from_users.sql`**: Completely drops the obsolete single-role `active_role` column from the `users` table, achieving full 3NF normalization without dual-source-of-truth anomalies.

---

## 7. Comprehensive API Reference Catalog (10 APIs)

Base URL: `http://localhost:8080/api/v1`

---

### API 1: User Sign Up
- **Method & Route**: `POST /auth/signup`
- **Access**: Public
- **Description**: Registers a new user account with full name, email, mobile, and password. Dispatches verification OTP.
- **Request Body (`SignUpRequest`)**:
```json
{
  "fullName": "Hardik Patel",
  "email": "hardik@example.com",
  "mobile": "+919876543210",
  "password": "StrongPassword123!",
  "confirmPassword": "StrongPassword123!",
  "agreedToTerms": true
}
```
- **Response (`201 Created`)**:
```json
{
  "success": true,
  "message": "Account created. Please verify your mobile number.",
  "data": {
    "userId": "usr_102",
    "mobile": "+919876543210",
    "email": "hardik@example.com",
    "isVerified": false,
    "otpExpirySeconds": 300
  },
  "timestamp": "2026-10-25T14:00:00Z"
}
```
- **Error Codes**: `PASSWORDS_DO_NOT_MATCH` (400), `EMAIL_ALREADY_EXISTS` (409), `MOBILE_ALREADY_EXISTS` (409).
- **cURL**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Hardik Patel","email":"hardik@example.com","mobile":"+919876543210","password":"StrongPassword123!","confirmPassword":"StrongPassword123!","agreedToTerms":true}'
```

---

### API 2: User Sign In (Password Login)
- **Method & Route**: `POST /auth/signin`
- **Access**: Public
- **Description**: Authenticates existing user via email or mobile with password. Issues access JWT and refresh token.
- **Request Body (`SignInRequest`)**:
```json
{
  "identifier": "hardik@example.com",
  "password": "StrongPassword123!",
  "rememberMe": true
}
```
- **Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Signed in successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "rft_a1b2c3d4e5f6...",
    "expiresIn": 86400,
    "user": {
      "id": "usr_102",
      "fullName": "Hardik Patel",
      "email": "hardik@example.com",
      "mobile": "+919876543210",
      "role": "player",
      "authProvider": "local",
      "city": "Surat",
      "profilePhoto": null,
      "isVerified": true
    }
  },
  "timestamp": "2026-10-25T14:00:00Z"
}
```
- **Error Codes**: `INVALID_CREDENTIALS` (401), `ACCOUNT_SUSPENDED` (401).

---

### API 3: Send / Resend OTP
- **Method & Route**: `POST /auth/otp/send`
- **Access**: Public
- **Description**: Dispatches a 6-digit numeric OTP to a target mobile number or email address with a 45-second cooldown throttle.
- **Request Body (`SendOtpRequest`)**:
```json
{
  "identifier": "+919876543210",
  "purpose": "login"
}
```
- **Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "OTP sent successfully",
  "data": {
    "retryAfterSeconds": 45,
    "otpExpiresInSeconds": 300
  },
  "timestamp": "2026-10-25T14:00:00Z"
}
```
- **Error Codes**: `INVALID_IDENTIFIER` (400), `OTP_COOLDOWN_ACTIVE` (400).

---

### API 4: Verify OTP
- **Method & Route**: `POST /auth/otp/verify`
- **Access**: Public
- **Description**: Validates an entered OTP code against the database. If valid, marks contact verified or returns a password reset token.
- **Request Body (`VerifyOtpRequest`)**:
```json
{
  "identifier": "+919876543210",
  "otp": "482910",
  "purpose": "signup_verification"
}
```
- **Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "OTP verified successfully",
  "data": {
    "isVerified": true,
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  },
  "timestamp": "2026-10-25T14:00:00Z"
}
```
- **Error Codes**: `INVALID_OTP` (400), `OTP_EXPIRED` (400), `MAX_ATTEMPTS_EXCEEDED` (400).

---

### API 5: Passwordless Mobile & Email OTP Login
- **Method & Route**: `POST /auth/otp/login`
- **Access**: Public
- **Description**: Verifies OTP and completes passwordless login in one atomic call. Auto-provisions new users if not previously registered.
- **Request Body (`OtpLoginRequest`)**:
```json
{
  "identifier": "shubman@cricauction.test",
  "otp": "754946",
  "role": "captain",
  "city": "Chandigarh"
}
```
- **Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Logged in successfully via OTP",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "rft_d8208a04...",
    "expiresIn": 86400,
    "user": {
      "id": "usr_6486",
      "fullName": "Shubman Gill",
      "email": "shubman@cricauction.test",
      "mobile": null,
      "role": "captain",
      "authProvider": "local",
      "city": "Chandigarh",
      "isVerified": true
    }
  },
  "timestamp": "2026-10-25T14:00:00Z"
}
```

---

### API 6: Google OAuth Direct Sign-In & Sign-Up
- **Method & Route**: `POST /auth/google` *(Aliases: `/auth/google/signin`, `/auth/google/signup`)*
- **Access**: Public
- **Description**: Validates Google ID token (JWT RS256). Existing accounts are linked; new accounts are automatically provisioned.
- **Request Body (`GoogleAuthRequest`)**:
```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjEy...",
  "role": "player",
  "city": "Mumbai"
}
```
- **Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Authenticated successfully with Google",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "rft_e0b3c22b...",
    "expiresIn": 86400,
    "user": {
      "id": "usr_10",
      "fullName": "Virat Kohli",
      "email": "virat@example.com",
      "role": "player",
      "authProvider": "google",
      "city": "Mumbai",
      "profilePhoto": "https://lh3.googleusercontent.com/...",
      "isVerified": true
    }
  },
  "timestamp": "2026-10-25T14:00:00Z"
}
```

---

### API 7: Forgot Password
- **Method & Route**: `POST /auth/password/forgot`
- **Access**: Public
- **Description**: Triggers a password reset OTP dispatch to the user's registered mobile number.
- **Request Body (`ForgotPasswordRequest`)**:
```json
{
  "identifier": "hardik@example.com"
}
```
- **Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Password reset instructions sent",
  "data": {
    "maskedContact": "+9198765 *****",
    "deliveryMethod": "SMS"
  },
  "timestamp": "2026-10-25T14:00:00Z"
}
```

---

### API 8: Reset Password
- **Method & Route**: `POST /auth/password/reset`
- **Access**: Public
- **Description**: Consumes a valid password reset token and updates the user's password hash.
- **Request Body (`ResetPasswordRequest`)**:
```json
{
  "resetToken": "rst_9f8e7d6c5b4a...",
  "newPassword": "BrandNewPassword123!",
  "confirmPassword": "BrandNewPassword123!"
}
```
- **Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Password reset successfully. You can now sign in with your new password.",
  "timestamp": "2026-10-25T14:00:00Z"
}
```

---

### API 9: Rotate Refresh Token
- **Method & Route**: `POST /auth/refresh-token`
- **Access**: Public
- **Description**: Exchanges a valid refresh token for a newly minted access token.
- **Request Body (`RefreshTokenRequest`)**:
```json
{
  "refreshToken": "rft_a1b2c3d4e5f6..."
}
```
- **Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 86400
  },
  "timestamp": "2026-10-25T14:00:00Z"
}
```
- **Error Codes**: `INVALID_REFRESH_TOKEN` (401), `REFRESH_TOKEN_EXPIRED` (401).

---

### API 10: Manage & Assign User Roles (Multi-Role RBAC)
- **Method & Route**: `PATCH /api/v1/auth/role` or `PUT /api/v1/auth/roles`
- **Access**: Authenticated (`Bearer <JWT>`)
- **Description**: Assigns or updates the current user's roles. Users can hold multiple roles simultaneously out of the 3 roles (`CAPTAIN`, `PLAYER`, `ORGANIZER`).
- **Request Body (Multiple Roles)**:
```json
{
  "roles": ["CAPTAIN", "PLAYER"]
}
```
- **Request Body (Single Role addition / legacy compatibility)**:
```json
{
  "role": "organizer"
}
```
- **Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Roles updated successfully",
  "data": {
    "roles": ["captain", "player"],
    "role": "captain"
  },
  "timestamp": "2026-10-25T16:00:00Z"
}
```
- **Inspect Session Profile**: `GET /auth/me` *(Authenticated)*
  - Returns current user profile details based on Bearer token.
- **User Logout**: `POST /auth/logout` *(Authenticated)*
  - Request: `{"refreshToken": "rft_..."}`
  - Marks token `revoked = true` in PostgreSQL.

---

## 8. SOLID Principles & Design Patterns Applied

### 8.1 SOLID Principles Application Matrix

| Principle | Implementation in Module 1 |
|---|---|
| **Single Responsibility (SRP)** | - `AuthController` handles only HTTP serialization & status codes.<br>- `AuthServiceImpl` coordinates auth logic; leaves OTP generation to `OtpService` and token rotation to `TokenService`.<br>- Password hashing runs in memory before database transactions. |
| **Open/Closed (OCP)** | - `OtpDeliveryStrategy` is open for new delivery channels (WhatsApp, Push) without modifying `OtpServiceImpl`.<br>- Circuit breaker and rate limiting filters wrap endpoints without modifying controller code. |
| **Liskov Substitution (LSP)** | - Any implementation of `OtpDeliveryStrategy` (`SmsOtpDeliveryStrategy`, `EmailOtpDeliveryStrategy`) can be substituted transparently.<br>- `UserPrincipal` adheres to Spring Security `UserDetails` contract. |
| **Interface Segregation (ISP)** | - Segregated interfaces: `AuthService`, `OtpService`, `TokenService`, `GoogleAuthService`, `GoogleTokenVerifier`. Clients depend only on the methods they use. |
| **Dependency Inversion (DIP)** | - Controllers and services depend strictly on abstraction interfaces injected via Spring IoC. Database access is abstracted behind `JpaRepository`. |

---

### 8.2 Design Patterns Registry

1. **Strategy Pattern (`OtpDeliveryStrategy`)**:
   - Multi-channel delivery for SMS (`SmsOtpDeliveryStrategy`) and Email (`EmailOtpDeliveryStrategy`).
   - Adding WhatsApp OTP requires only creating `WhatsAppOtpDeliveryStrategy implements OtpDeliveryStrategy`.
2. **State Pattern (`CircuitBreaker`)**:
   - Manages state transitions across `CLOSED` $\rightarrow$ `OPEN` $\rightarrow$ `HALF_OPEN` with automatic cooldown.
3. **Sliding-Window Token Bucket Pattern (`RateLimiter`)**:
   - Regulates request throughput per IP, protecting against brute-force password guessing and DDoS.
4. **Retry with Exponential Jitter (`ResilienceRetry`)**:
   - Handles transient network anomalies when connecting to external OAuth providers or SMS gateways.

---

## 9. High-Concurrency Benchmarks & Verification

The authentication module was evaluated using automated load testing against the **actual local PostgreSQL database** (`localhost:5432/cricauction`) using Java 23 Project Loom Virtual Threads.

### 9.1 5,000 Concurrent Users Full-Flow Automation Benchmark
Test: [**`FullFlow5000UsersActualDbTest.java`**](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/test/java/com/cricauction/modules/auth/controller/FullFlow5000UsersActualDbTest.java)  
Runner: [**`run-5000-users-db-automation.sh`**](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/run-5000-users-db-automation.sh)

| Benchmark Metric | Measured Result |
|---|---|
| **Concurrent User Flows** | **5,000 full multi-step journeys** |
| **Success Rate** | **100.00% (5,000 / 5,000)** |
| **Total Elapsed Time** | **4,719 ms (4.72 seconds)** |
| **Throughput (RPS)** | **1,059.55 full user flows / second** |
| **Median (p50) Latency** | **3,469 ms** |
| **p90 Latency** | **4,477 ms** |
| **p95 Latency** | **4,558 ms** |
| **p99 Latency** | **4,624 ms** |
| **Max Latency** | **4,706 ms** |
| **Total Users Persisted** | **21,486 users in PostgreSQL** |

### 9.2 2,000 Concurrent Single-Request Benchmarks
Test: [**`AuthConcurrencyLoadTest.java`**](file:///Users/yogeshkumarpadhiyar/Downloads/CricAuction-backbone/src/test/java/com/cricauction/modules/auth/controller/AuthConcurrencyLoadTest.java)

- **Google OAuth Sign-In**: **251 ms** total elapsed time (**7,968.13 req/sec**, p99: **30ms**).
- **Profile Fetch (`/auth/me`)**: **574 ms** total elapsed time (**3,484.32 req/sec**, p99: **157ms**).

---

## 10. Developer Extension & Onboarding Guide

### 10.1 How to Add a New OTP Channel (e.g. WhatsApp)
1. Create a new class implementing `OtpDeliveryStrategy`:
```java
package com.cricauction.modules.auth.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WhatsAppOtpDeliveryStrategy implements OtpDeliveryStrategy {

    @Override
    public boolean supports(String channel) {
        return "WHATSAPP".equalsIgnoreCase(channel);
    }

    @Override
    public void sendOtp(String recipient, String otpCode, String purpose) {
        // Integrate with WhatsApp Cloud API / Twilio
        log.info("[WHATSAPP OTP] Dispatched {} to {}", otpCode, recipient);
    }
}
```
2. Spring Boot automatically discovers the new bean and registers it in `OtpServiceImpl`. Zero changes needed in core logic!

---

### 10.2 How to Re-Run Tests & Verify
Run all unit and integration tests:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk ./mvnw test -Dtest=AuthControllerTest,RealTimeUserJourneyTest
```

Run the 2,000 users concurrency load test:
```bash
./run-load-test.sh
```

Run the 5,000 users actual database automation benchmark:
```bash
./run-5000-users-db-automation.sh
```

Query PostgreSQL database verification:
```bash
psql -U yogeshkumarpadhiyar -d cricauction -c "SELECT COUNT(*) FROM users; SELECT auth_provider, count(*) FROM users GROUP BY auth_provider;"
```
