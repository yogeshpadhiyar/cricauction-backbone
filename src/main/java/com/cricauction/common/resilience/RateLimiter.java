package com.cricauction.common.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance sliding window token bucket rate limiter to protect against DDoS
 * and brute-force attacks across all public and authenticated endpoints.
 */
@Slf4j
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, ClientWindow> clientWindows = new ConcurrentHashMap<>();

    // Default rate limits
    private static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 300;
    private static final int AUTH_MAX_REQUESTS_PER_MINUTE = 60; // For sensitive auth/OTP endpoints
    private static final long WINDOW_SIZE_MS = 60_000L; // 1 minute sliding window
    private static final int MAX_MAP_CAPACITY = 10_000; // Upper bound to protect JVM heap

    public static class ClientWindow {
        long windowStart;
        AtomicInteger requestCount;

        ClientWindow(long start) {
            this.windowStart = start;
            this.requestCount = new AtomicInteger(1);
        }
    }

    public boolean tryAcquire(String clientIp, String uri) {
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "unknown";
        }

        int limit = isSensitiveEndpoint(uri) ? AUTH_MAX_REQUESTS_PER_MINUTE : DEFAULT_MAX_REQUESTS_PER_MINUTE;
        long now = System.currentTimeMillis();
        String key = clientIp + ":" + (isSensitiveEndpoint(uri) ? "auth" : "gen");

        if (clientWindows.size() > MAX_MAP_CAPACITY) {
            cleanExpiredWindows(now);
        }

        ClientWindow window = clientWindows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart > WINDOW_SIZE_MS) {
                return new ClientWindow(now);
            }
            existing.requestCount.incrementAndGet();
            return existing;
        });

        boolean allowed = window.requestCount.get() <= limit;
        if (!allowed) {
            log.warn("DDoS/Rate Limit Exceeded for IP [{}] on URI [{}]. Count: {}, Limit: {}",
                    clientIp, uri, window.requestCount.get(), limit);
        }
        return allowed;
    }

    public int getRemainingTokens(String clientIp, String uri) {
        int limit = isSensitiveEndpoint(uri) ? AUTH_MAX_REQUESTS_PER_MINUTE : DEFAULT_MAX_REQUESTS_PER_MINUTE;
        String key = clientIp + ":" + (isSensitiveEndpoint(uri) ? "auth" : "gen");
        ClientWindow window = clientWindows.get(key);
        if (window == null) {
            return limit;
        }
        return Math.max(0, limit - window.requestCount.get());
    }

    public long getRetryAfterSeconds(String clientIp, String uri) {
        String key = clientIp + ":" + (isSensitiveEndpoint(uri) ? "auth" : "gen");
        ClientWindow window = clientWindows.get(key);
        if (window == null) {
            return 1L;
        }
        long elapsed = System.currentTimeMillis() - window.windowStart;
        long remainingMs = Math.max(0, WINDOW_SIZE_MS - elapsed);
        return Math.max(1L, remainingMs / 1000L);
    }

    public void reset(String clientIp) {
        clientWindows.entrySet().removeIf(entry -> entry.getKey().startsWith(clientIp));
    }

    public void cleanExpiredWindows(long now) {
        clientWindows.entrySet().removeIf(entry -> (now - entry.getValue().windowStart) > WINDOW_SIZE_MS);
    }

    private boolean isSensitiveEndpoint(String uri) {
        if (uri == null) return false;
        return uri.contains("/auth/signin")
                || uri.contains("/auth/otp")
                || uri.contains("/auth/password")
                || uri.contains("/auth/login/otp");
    }
}
