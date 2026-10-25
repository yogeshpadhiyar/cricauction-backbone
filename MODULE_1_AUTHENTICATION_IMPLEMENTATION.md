# Module 1: Authentication & OTP Verification Implementation Document

## 1. Executive Summary & Tech Stack Alignment

This document details the architectural design, architectural reasoning, and implementation of **Module 1: Authentication & Authorization (10 APIs)** for the CricAuction platform.

### Technology Stack
- **Language**: Java 25 (OpenJDK 25 LTS)
- **Framework**: Spring Boot 4.1.1 (Spring Framework 7.x, Spring Security 7.x)
- **Database**: PostgreSQL 16+ (tested with Flyway migrations & H2 test compatibility)
- **Security & Authorization**: Spring Security + Stateless JWT (JJWT 0.12.6) + Role-Based Access Control (RBAC)
- **Design Methodology**: Clean Architecture, Domain-Driven Design (DDD) modularity, SOLID principles, and enterprise design patterns.

---

## 2. Standard Spring Boot Project Structure

The project adheres to enterprise Spring Boot conventions with a modular package layout:

```
cricauction-backbone/
├── pom.xml
├── mvnw / mvnw.cmd
├── src/
│   ├── main/
│   │   ├── java/com/cricauction/
│   │   │   ├── CricauctionBackboneApplication.java   # Spring Boot entry point with Virtual Threads
│   │   │   ├── common/
│   │   │   │   ├── api/
│   │   │   │   │   ├── ApiResponse.java             # Standard response envelope (success, message, data, timestamp)
│   │   │   │   │   ├── ApiError.java                # Standard error envelope (code, message, details)
│   │   │   │   │   └── ErrorDetail.java             # Field-level validation detail
│   │   │   │   └── exception/
│   │   │   │       ├── AppException.java            # Base application exception
│   │   │   │       ├── BadRequestException.java     # HTTP 400
│   │   │   │       ├── ResourceNotFoundException.java # HTTP 404
│   │   │   │       ├── UnauthorizedException.java   # HTTP 401
│   │   │   │       ├── ForbiddenException.java      # HTTP 403
│   │   │   │       ├── ConflictException.java       # HTTP 409
│   │   │   │       └── GlobalExceptionHandler.java  # @RestControllerAdvice with uniform error mapping
│   │   │   ├── config/
│   │   │   │   ├── ApplicationConfig.java           # PasswordEncoder, AuthenticationProvider, ObjectMapper
│   │   │   │   ├── JwtConfig.java                   # JWT secret and TTL properties
│   │   │   │   └── SecurityConfig.java              # Spring Security 7 filter chain, CORS, stateless session
│   │   │   ├── security/
│   │   │   │   ├── CustomUserDetailsService.java    # Loads user by email, mobile, or ID
│   │   │   │   ├── JwtAuthenticationFilter.java     # OncePerRequestFilter for Bearer token validation
│   │   │   │   ├── JwtTokenProvider.java            # HMAC-SHA256 access token minting and validation
│   │   │   │   └── UserPrincipal.java               # Spring Security UserDetails implementation
│   │   │   └── modules/
│   │   │       └── auth/
│   │   │           ├── controller/
│   │   │           │   └── AuthController.java      # 10 REST endpoints matching API specification
│   │   │           ├── dto/
│   │   │           │   ├── request/                 # Validated request DTOs (Jakarta Validation)
│   │   │           │   │   ├── SignUpRequest.java
│   │   │           │   │   ├── SignInRequest.java
│   │   │           │   │   ├── SendOtpRequest.java
│   │   │           │   │   ├── VerifyOtpRequest.java
│   │   │           │   │   ├── ForgotPasswordRequest.java
│   │   │           │   │   ├── ResetPasswordRequest.java
│   │   │           │   │   ├── RefreshTokenRequest.java
│   │   │           │   │   ├── LogoutRequest.java
│   │   │           │   │   └── RoleSelectionRequest.java
│   │   │           │   └── response/                # Response DTOs
│   │   │           │       ├── AuthResponse.java
│   │   │           │       ├── SignUpResponse.java
│   │   │           │       ├── SendOtpResponse.java
│   │   │           │       ├── VerifyOtpResponse.java
│   │   │           │       ├── ForgotPasswordResponse.java
│   │   │           │       ├── TokenRefreshResponse.java
│   │   │           │       ├── UserProfileResponse.java
│   │   │           │       └── RoleResponse.java
│   │   │           ├── entity/
│   │   │           │   ├── User.java                # JPA Entity for `users` table
│   │   │           │   ├── OtpVerification.java     # JPA Entity for `otp_verifications` table
│   │   │           │   ├── OtpVerificationId.java   # Composite key (mobile + purpose)
│   │   │           │   ├── PasswordResetToken.java  # Entity for password reset tokens
│   │   │           │   ├── RefreshToken.java        # Entity for refresh token rotation
│   │   │           │   └── enums/
│   │   │           │       ├── UserRole.java        # ORGANIZER, CAPTAIN, PLAYER
│   │   │           │       └── OtpPurpose.java      # signup_verification, password_reset
│   │   │           ├── repository/
│   │   │           │   ├── UserRepository.java
│   │   │           │   ├── OtpVerificationRepository.java
│   │   │           │   ├── PasswordResetTokenRepository.java
│   │   │           │   └── RefreshTokenRepository.java
│   │   │           ├── service/
│   │   │           │   ├── AuthService.java         # Business workflow contract
│   │   │           │   ├── OtpService.java          # OTP generation & verification contract
│   │   │           │   ├── TokenService.java        # Refresh & reset token lifecycle contract
│   │   │           │   └── impl/
│   │   │           │       ├── AuthServiceImpl.java
│   │   │           │       ├── OtpServiceImpl.java
│   │   │           │       └── TokenServiceImpl.java
│   │   │           └── strategy/
│   │   │               ├── OtpDeliveryStrategy.java # Strategy interface for multi-channel OTP delivery
│   │   │               ├── SmsOtpDeliveryStrategy.java
│   │   │               └── EmailOtpDeliveryStrategy.java
│   │   └── resources/
│   │       ├── application.yml                      # Production PostgreSQL & Virtual Thread configuration
│   │       └── db/migration/
│   │           └── V1__init_auth_schema.sql         # Flyway DDL migration
│   └── test/
│       ├── java/com/cricauction/
│       │   ├── CricauctionBackboneApplicationTests.java
│       │   └── modules/auth/controller/
│       │       └── AuthControllerTest.java          # 12 Integration tests covering all 10 endpoints
│       └── resources/
│           └── application.yml                      # In-memory H2 configuration for fast test execution
```

---

## 3. SOLID Principles Applied

| Principle | Implementation Detail |
| :--- | :--- |
| **Single Responsibility Principle (SRP)** | - `AuthController` only handles HTTP routing, payload validation, and HTTP status mappings.<br>- `AuthService` handles authentication logic coordination.<br>- `OtpService` exclusively handles OTP generation, attempts tracking, cooldown checks, and expiry verification.<br>- `TokenService` manages refresh tokens and single-use reset tokens.<br>- `JwtTokenProvider` solely generates and validates cryptographic JWT tokens.<br>- `GlobalExceptionHandler` centralizes error mapping across the whole application. |
| **Open/Closed Principle (OCP)** | - `OtpDeliveryStrategy` allows adding new delivery mechanisms (SMS via AWS SNS, Twilio, MSG91, WhatsApp, or Email via SendGrid) without modifying any existing OTP verification logic. |
| **Liskov Substitution Principle (LSP)** | - Any `OtpDeliveryStrategy` implementation can replace another seamlessly.<br>- `UserPrincipal` adheres to Spring Security's `UserDetails` contract. |
| **Interface Segregation Principle (ISP)** | - Separated granular interfaces (`AuthService`, `OtpService`, `TokenService`) instead of one giant God-service interface. Controllers or internal modules only inject what they require. |
| **Dependency Inversion Principle (DIP)** | - Controllers and services depend strictly on interfaces (`AuthService`, `OtpService`, `TokenService`, `PasswordEncoder`, `UserRepository`), injected via constructor injection (`@RequiredArgsConstructor`). |

---

## 4. Design Patterns Applied

1. **Strategy Pattern (`OtpDeliveryStrategy`)**:
   - Encapsulates how OTPs are dispatched.
   - `SmsOtpDeliveryStrategy` handles SMS delivery; `EmailOtpDeliveryStrategy` handles email delivery.
   - New providers (e.g. WhatsApp, Voice Call) can be registered as Spring `@Component`s without editing existing code.
2. **Standard Envelope Pattern (`ApiResponse<T>` & `ApiError`)**:
   - Ensures 100% consistent JSON contracts across successful responses and error states, exactly matching the specification.
3. **Repository Pattern (Spring Data JPA)**:
   - Decouples business logic from persistence storage operations.
4. **Filter / Interceptor Pattern (`JwtAuthenticationFilter`)**:
   - Extends Spring's `OncePerRequestFilter` to intercept incoming HTTP requests, extract Bearer tokens, validate signatures, and populate the `SecurityContextHolder`.
5. **Builder & Factory Pattern**:
   - Static factory methods `ApiResponse.success()`, `ApiResponse.created()`, `ApiResponse.error()` for clean, expressive response instantiation.

---

## 5. Pure-PostgreSQL Architecture (Zero-Redis Strategy)

In alignment with `cricauction-database-architecture.md`:
- **Redis TTL Replacement**: Mobile OTPs are stored in `otp_verifications` with composite key `(mobile, purpose)`.
  - Expiry is verified natively by comparing `expires_at > CURRENT_TIMESTAMP`.
  - Attempt counter (`attempts`) prevents brute-force attacks (limited to 5 attempts).
  - Cooldown timer (`retryAfterSeconds = 45`) prevents spamming.
- **Token Rotation**: Refresh tokens are stored in `refresh_tokens` with cryptographic uniqueness, expiry timestamps, and revocation flags.

---

## 6. Detailed API Catalogue: Module 1 (10 APIs)

### 1. User Sign Up
- **Endpoint**: `POST /api/v1/auth/signup`
- **Access**: Public
- **Validation**:
  - `fullName`: 2 to 100 chars
  - `email`: Valid format, must be unique
  - `mobile`: E.164 pattern (`+919876543210`), must be unique
  - `password`: Min 8 chars, must match `confirmPassword`
  - `agreedToTerms`: Must be `true`
- **Behavior**: Creates user with default role `PLAYER`, `isMobileVerified = false`, dispatches 6-digit OTP to mobile.
- **HTTP Status**: `201 Created`

### 2. User Sign In (Login)
- **Endpoint**: `POST /api/v1/auth/signin`
- **Access**: Public
- **Identifier**: Accepts either email or mobile number.
- **Behavior**: Verifies BCrypt password hash. Generates JWT Access Token (24h expiry) and Refresh Token (7 days).
- **HTTP Status**: `200 OK`

### 3. Send / Resend OTP
- **Endpoint**: `POST /api/v1/auth/otp/send`
- **Access**: Public
- **Behavior**: Throttled by 45s cooldown. Generates fresh 6-digit OTP with 300s (5-minute) expiry.
- **HTTP Status**: `200 OK`

### 4. Verify OTP
- **Endpoint**: `POST /api/v1/auth/otp/verify`
- **Access**: Public
- **Behavior**:
  - If `purpose = "signup_verification"`: Sets `is_mobile_verified = true` on user and returns immediate `accessToken`.
  - If `purpose = "password_reset"`: Generates single-use `resetToken` (15 min validity) for step 6.
- **HTTP Status**: `200 OK`

### 5. Forgot Password
- **Endpoint**: `POST /api/v1/auth/password/forgot`
- **Access**: Public
- **Behavior**: Finds user by email or mobile, triggers OTP for `password_reset`, returns masked contact (e.g. `+91 98765 *****`).
- **HTTP Status**: `200 OK`

### 6. Reset Password
- **Endpoint**: `POST /api/v1/auth/password/reset`
- **Access**: Public (Requires Reset Token)
- **Behavior**: Validates and consumes single-use reset token. Updates password with BCrypt hash.
- **HTTP Status**: `200 OK`

### 7. Refresh Access Token
- **Endpoint**: `POST /api/v1/auth/refresh-token`
- **Access**: Public
- **Behavior**: Validates refresh token from DB (checks not expired, not revoked). Returns fresh JWT access token.
- **HTTP Status**: `200 OK`

### 8. User Logout
- **Endpoint**: `POST /api/v1/auth/logout`
- **Access**: Authenticated (`Bearer <token>`)
- **Behavior**: Revokes submitted refresh token and clears active session.
- **HTTP Status**: `200 OK`

### 9. Get Current User Profile
- **Endpoint**: `GET /api/v1/auth/me`
- **Access**: Authenticated (`Bearer <token>`)
- **Behavior**: Returns current user entity from database via security context principal ID.
- **HTTP Status**: `200 OK`

### 10. Switch / Set Active Role
- **Endpoint**: `PATCH /api/v1/auth/role`
- **Access**: Authenticated (`Bearer <token>`)
- **Payload**: `{"role": "organizer"}` (Accepts `organizer`, `captain`, `player`, case-insensitive)
- **Behavior**: Switches user's active role.
- **HTTP Status**: `200 OK`

---

## 7. Verification & Automated Test Suite

A comprehensive test suite in `AuthControllerTest.java` validates every endpoint and error pathway:
- `signUp_Success`: 201 Created with standard envelope and public user ID prefix `usr_`.
- `signUp_PasswordsMismatch`: 400 Bad Request with `PASSWORDS_DO_NOT_MATCH`.
- `signIn_Success`: 200 OK with `accessToken`, `refreshToken`, and user details.
- `signIn_InvalidCredentials`: 401 Unauthorized with `INVALID_CREDENTIALS`.
- `sendOtp_Success`: 200 OK with cooldown and expiry timers.
- `verifyOtp_SignupVerification_Success`: 200 OK with `isVerified: true` and `accessToken`.
- `forgotPassword_Success`: 200 OK with masked phone and SMS delivery.
- `resetPassword_Success`: 200 OK consuming single-use reset token.
- `refreshToken_Success`: 200 OK rotating access token.
- `logout_Success`: 200 OK revoking refresh token.
- `getCurrentUser_Success`: 200 OK returning authenticated profile.
- `switchRole_Success`: 200 OK switching role to organizer.

### Build and Test Command:
```bash
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/zulu25.28.85-ca-jdk25.0.0-macosx_aarch64
mvn clean test
```
**Results**: `13 tests run, 0 failures, 0 errors, 100% passing`.
