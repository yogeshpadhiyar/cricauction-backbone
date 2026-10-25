/**
 * CricAuction Concurrency Load Test Script (k6)
 * Simulates 2,000 Concurrent Virtual Users (VUs) hitting CricAuction Authentication Endpoints
 *
 * How to run:
 *   brew install k6 (if not installed)
 *   k6 run load-test-2000-users.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    concurrent_users_2000: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 500 },   // Warm-up ramp to 500 users
        { duration: '15s', target: 2000 },  // Ramp to 2,000 concurrent users
        { duration: '30s', target: 2000 },  // Sustained load at 2,000 concurrent users
        { duration: '10s', target: 0 },     // Ramp-down
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],         // Error rate must be less than 1%
    http_req_duration: ['p(95)<300'],       // 95% of requests should be below 300ms
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1/auth';

export default function () {
  const vuId = __VU;
  const iteration = __ITER;

  // Scenario 1: Google OAuth Instant Authentication
  const googlePayload = JSON.stringify({
    idToken: `test-google-token-player_${vuId}@cricauction.test`,
    role: 'player',
    city: 'Mumbai',
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const googleRes = http.post(`${BASE_URL}/google`, googlePayload, params);

  const isSuccess = check(googleRes, {
    'status is 200': (r) => r.status === 200,
    'has access token': (r) => {
      const body = JSON.parse(r.body);
      return body.data && body.data.accessToken !== undefined;
    },
  });

  if (isSuccess) {
    const authData = JSON.parse(googleRes.body).data;
    const token = authData.accessToken;

    // Scenario 2: Authenticated /me request using Bearer JWT
    const authHeaders = {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
    };

    const meRes = http.get(`${BASE_URL}/me`, authHeaders);
    check(meRes, {
      'me status is 200': (r) => r.status === 200,
      'user email matches': (r) => {
        const body = JSON.parse(r.body);
        return body.data && body.data.email === `player_${vuId}@cricauction.test`;
      },
    });
  }

  sleep(0.5); // 500ms pacing between actions
}
