# CricAuction: 5,000 Concurrent Users Full Flow Automation & Resilience Report

## 1. Executive Summary

This report documents the implementation, latency optimization, and benchmark validation of:
1. **Omni-Channel Login Capabilities**:
   - Google Direct OAuth Sign-In & Sign-Up (`POST /api/v1/auth/google`)
   - Mobile Number + OTP Passwordless Login (`POST /api/v1/auth/otp/send` + `POST /api/v1/auth/otp/login`)
   - Email ID + OTP Passwordless Login (`POST /api/v1/auth/otp/send` + `POST /api/v1/auth/otp/login`)
   - Standard Credentials Login (`POST /api/v1/auth/signin`)
2. **Resilience Fundamentals**:
   - Sliding Window Token Bucket **Rate Limiter** (`RateLimiter.java`, `RateLimitingFilter.java`) protecting against DDoS & brute-force attacks (`HTTP 429 Too Many Requests`).
   - Thread-safe **Circuit Breaker** (`CircuitBreaker.java`) with `CLOSED`, `OPEN`, and `HALF_OPEN` state transitions and automatic fallback.
   - Exponential **Retry Mechanism with Randomized Jitter** (`ResilienceRetry.java`) for external SMS/Email gateways and Google token verification.
3. **Real-Time User Test Scenarios** (`RealTimeUserJourneyTest.java`): 5 complete end-to-end user journeys passing with 100% success.
4. **5,000 Concurrent Dummy Users Full Flow Automation Task** (`FullFlow5000UsersActualDbTest.java`, `./run-5000-users-db-automation.sh`):
   - Executed against the **actual local PostgreSQL database** (`jdbc:postgresql://localhost:5432/cricauction`).
   - Latency optimized from **30.12s down to 4.72s** (Phase 1 Latency Optimization).
   - **Database was NOT cleared** after execution as requested, preserving all **21,486 users** and **21,486 refresh tokens** for cross-verification.

---

## 2. All Login Types & Endpoints

| Login Type | Step 1 (Dispatch) | Step 2 (Verify & Issue Tokens) | Auth Provider | Verified Field |
|---|---|---|---|---|
| **Google Direct OAuth** | Client SDK generates ID Token | `POST /api/v1/auth/google` | `GOOGLE` | `is_email_verified = true` |
| **Mobile + OTP** | `POST /api/v1/auth/otp/send` (`purpose="login"`) | `POST /api/v1/auth/otp/login` | `LOCAL` | `is_mobile_verified = true` |
| **Email ID + OTP** | `POST /api/v1/auth/otp/send` (`purpose="login"`) | `POST /api/v1/auth/otp/login` | `LOCAL` | `is_email_verified = true` |
| **Password Login** | User signs up (`POST /api/v1/auth/signup`) | `POST /api/v1/auth/signin` | `LOCAL` | Verified via OTP flow |

### Request & Response Examples

#### Mobile + OTP Login
```http
POST /api/v1/auth/otp/login
Content-Type: application/json

{
  "identifier": "+919876543210",
  "otp": "482910",
  "role": "player",
  "city": "Ahmedabad"
}
```

#### Email ID + OTP Login
```http
POST /api/v1/auth/otp/login
Content-Type: application/json

{
  "identifier": "shubman@cricauction.test",
  "otp": "754946",
  "role": "captain",
  "city": "Chandigarh"
}
```

#### Response (`ApiResponse<AuthResponse>`)
```json
{
  "success": true,
  "message": "Logged in successfully via OTP",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "d8208a04-58eb-4b2a-bf3b-a6beba64cbb0",
    "expiresIn": 86400,
    "user": {
      "id": "usr_6486",
      "fullName": "Shubman Gill #1250",
      "email": "shubman@cricauction.test",
      "role": "captain",
      "authProvider": "local",
      "city": "Chandigarh",
      "isVerified": true
    }
  }
}
```

---

## 3. Resilience Fundamentals Architecture

### 3.1 DDoS Protection: Sliding Window Rate Limiter
- **Components**: `RateLimiter.java`, `RateLimitingFilter.java`
- **Tiers**:
  - General Endpoints: 300 requests/minute per IP
  - Sensitive Auth Endpoints (`/signin`, `/otp/**`, `/login/otp`): 60 requests/minute per IP
- **Response when tripped**:
  - Status: `HTTP 429 Too Many Requests`
  - Headers: `Retry-After: <seconds>`, `X-Rate-Limit-Remaining: 0`
  - Body: `{"success": false, "error": {"code": "RATE_LIMIT_EXCEEDED", "message": "Too many requests..."}}`

### 3.2 Circuit Breaker Pattern
- **Component**: `CircuitBreaker.java`
- **States**:
  - `CLOSED`: Normal operation.
  - `OPEN`: Tripped after 5 consecutive downstream failures; rejects calls or executes fallbacks immediately.
  - `HALF_OPEN`: Automatically transitions after 5,000ms cooldown to evaluate downstream health.

### 3.3 Exponential Backoff & Jitter Retry
- **Component**: `ResilienceRetry.java`
- **Algorithm**: `backoff = (initialBackoff * 2^attempt) + ThreadLocalRandom.current().nextLong(10, 50) ms`
- Protects external Google OAuth calls and SMS/Email OTP dispatches from transient socket resets.

---

## 4. 5,000 Concurrent Users Actual PostgreSQL Automation Test

### 4.1 Test Design (`FullFlow5000UsersActualDbTest.java`)
The automation executes real database transactions on PostgreSQL (`localhost:5432/cricauction`) concurrently using Java 23 Virtual Threads across 4 distinct cohorts:
1. **Cohort 1 (1,250 Users)**: Google Direct OAuth Sign-In & Sign-Up -> Switch Role to `CAPTAIN`
2. **Cohort 2 (1,250 Users)**: Passwordless Mobile + OTP Login -> Retrieve Profile (`/me`)
3. **Cohort 3 (1,250 Users)**: Passwordless Email ID + OTP Login -> Switch Role to `ORGANIZER`
4. **Cohort 4 (1,250 Users)**: Standard Sign-Up + Password Sign-In -> Rotate Refresh Token

### 4.2 Benchmark Performance Results (After Phase 1 Latency Optimization)

| Metric | Before Optimization | After Phase 1 Optimization | Improvement |
|---|:---:|:---:|:---:|
| **Total Concurrent User Flows** | **5,000** | **5,000** | Full Flow Preserved |
| **Successful Full Flows** | **5,000 (100.00%)** | **5,000 (100.00%)** | **Zero Failures** |
| **Failed Flows** | **0 (0.00%)** | **0 (0.00%)** | 0% error rate |
| **Total Elapsed Time** | **30,117 ms (30.12s)** | **4,719 ms (4.72s)** | **6.4x Faster (84.3% Drop)** |
| **Throughput** | **166.02 flows/sec** | **1,059.55 flows/sec** | **+538% Throughput** |
| **Min Latency** | **396 ms** | **136 ms** | **2.9x Faster** |
| **Median (p50) Latency** | **18,646 ms** | **3,469 ms** | **5.4x Faster** |
| **p90 Latency** | **28,358 ms** | **4,477 ms** | **6.3x Faster** |
| **p95 Latency** | **29,324 ms** | **4,558 ms** | **6.4x Faster** |
| **p99 Latency** | **29,856 ms** | **4,624 ms** | **6.5x Faster** |
| **Max Latency** | **30,019 ms** | **4,706 ms** | **6.4x Faster** |

---

## 5. Actual PostgreSQL Database Verification (Data Preserved)

The database was **NOT cleared** after the test run. You can verify the entries directly in PostgreSQL:

### SQL Query 1: Total Users Count
```sql
SELECT COUNT(*) AS total_persisted_users FROM users;
```
**Result**: `21,486`

### SQL Query 2: Breakdown by Auth Provider
```sql
SELECT auth_provider, count(*) FROM users GROUP BY auth_provider;
```
**Result**:
```
 auth_provider | count 
---------------+-------
 GOOGLE        |  5380
 LOCAL         | 16106
```

### SQL Query 3: Breakdown by Active Role
```sql
SELECT active_role, count(*) FROM users GROUP BY active_role;
```
**Result**:
```
 active_role | count 
-------------+-------
 ORGANIZER   |  5392
 CAPTAIN     |  5377
 PLAYER      | 10717
```

### SQL Query 4: Total Persisted Refresh Tokens
```sql
SELECT COUNT(*) AS total_persisted_refresh_tokens FROM refresh_tokens;
```
**Result**: `21,486`

### SQL Query 5: Sample Persisted Dummy Users
```sql
SELECT id, full_name, email, mobile, auth_provider, active_role, is_email_verified, is_mobile_verified 
FROM users 
ORDER BY id DESC 
LIMIT 5;
```
**Result**:
```
  id   |     full_name     |              email               |    mobile    | auth_provider | active_role | is_email_verified | is_mobile_verified 
-------+-------------------+----------------------------------+--------------+---------------+-------------+-------------------+--------------------
 21486 | User 3149         |                                  | +91936003149 | LOCAL         | PLAYER      | f                 | t
 21485 | player_4436_04818 | player_4436_04818@cricauction.in |              | LOCAL         | ORGANIZER   | t                 | f
 21484 | player_4436_04386 | player_4436_04386@cricauction.in |              | LOCAL         | ORGANIZER   | t                 | f
 21483 | player_4436_04842 | player_4436_04842@cricauction.in |              | LOCAL         | ORGANIZER   | t                 | f
 21482 | Axar Patel #4871  | player_4436_04871@cricauction.in | +91936004871 | LOCAL         | PLAYER      | f                 | f
```

---

## 6. How to Re-Run the Automation Task

You can re-run the 5,000 users database automation task at any time using the automated script:
```bash
./run-5000-users-db-automation.sh
```
Or via Maven:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk mvn test -Dtest=FullFlow5000UsersActualDbTest
```
