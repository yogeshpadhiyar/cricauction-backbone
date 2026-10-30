package com.cricauction.common.resilience;

import com.cricauction.common.cache.BoundedLruCache;
import com.cricauction.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerAndCacheTest {

    private CircuitBreaker circuitBreaker;
    private ResilienceRetry resilienceRetry;

    @BeforeEach
    void setUp() {
        circuitBreaker = new CircuitBreaker();
        resilienceRetry = new ResilienceRetry();
    }

    @Test
    @DisplayName("Issue 1: Client UnauthorizedException does not trip CircuitBreaker")
    void testUnauthorizedExceptionDoesNotTripBreaker() {
        // Send 10 invalid credentials/tokens
        for (int i = 0; i < 10; i++) {
            assertThrows(UnauthorizedException.class, () ->
                    circuitBreaker.execute("GoogleOAuth", () -> {
                        throw new UnauthorizedException("INVALID_TOKEN", "Bad client credentials");
                    })
            );
        }

        // Breaker should remain CLOSED because client errors are not server outages
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState("GoogleOAuth"));
    }

    @Test
    @DisplayName("Issue 1: Circuit breaker states are partitioned per service")
    void testServicePartitioning() {
        // Trip SMS service with 5 server errors
        for (int i = 0; i < 5; i++) {
            assertThrows(RuntimeException.class, () ->
                    circuitBreaker.execute("SmsGateway", () -> {
                        throw new RuntimeException("503 Gateway Timeout");
                    })
            );
        }

        // SmsGateway should be OPEN
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState("SmsGateway"));

        // GoogleOAuth should remain CLOSED and unaffected
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState("GoogleOAuth"));
        String result = circuitBreaker.execute("GoogleOAuth", () -> "GOOGLE_SUCCESS");
        assertEquals("GOOGLE_SUCCESS", result);
    }

    @Test
    @DisplayName("Issue 2: ResilienceRetry does not retry non-transient UnauthorizedException")
    void testRetryBypassesUnauthorizedException() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(UnauthorizedException.class, () ->
                resilienceRetry.executeWithRetry("TestAuthCall", () -> {
                    attempts.incrementAndGet();
                    throw new UnauthorizedException("BAD_AUTH", "Invalid credentials");
                })
        );

        // Should NOT retry: executed exactly once
        assertEquals(1, attempts.get());
    }

    @Test
    @DisplayName("Issue 5: BoundedLruCache enforces maximum capacity and LRU eviction")
    void testBoundedLruCacheCapacity() {
        BoundedLruCache<String, String> cache = new BoundedLruCache<>(3, 60);

        cache.put("k1", "v1");
        cache.put("k2", "v2");
        cache.put("k3", "v3");

        assertEquals(3, cache.size());
        assertEquals("v1", cache.get("k1"));

        // Adding 4th item should evict k2 (since k1 was just accessed)
        cache.put("k4", "v4");
        assertEquals(3, cache.size());

        assertNotNull(cache.get("k1"));
        assertNull(cache.get("k2")); // k2 was least recently used
        assertNotNull(cache.get("k3"));
        assertNotNull(cache.get("k4"));
    }

    @Test
    @DisplayName("Issue 5: BoundedLruCache expires entries after TTL")
    void testBoundedLruCacheTtl() throws InterruptedException {
        // Cache with 1-second TTL
        BoundedLruCache<String, String> cache = new BoundedLruCache<>(10, 1);
        cache.put("shortLived", "data");

        assertEquals("data", cache.get("shortLived"));
        Thread.sleep(1100);

        // Should return null after expiration
        assertNull(cache.get("shortLived"));
    }
}

