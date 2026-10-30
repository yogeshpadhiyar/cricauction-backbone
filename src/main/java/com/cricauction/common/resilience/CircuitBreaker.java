package com.cricauction.common.resilience;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.common.exception.ForbiddenException;
import com.cricauction.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Thread-safe, partitioned Circuit Breaker implementation for external service dependencies
 * (Google OAuth token verification, SMS gateways, Email services).
 *
 * <p>Key Fixes:
 * 1. Independent per-service isolation: Outages in SMS or Google OAuth do not impact each other.
 * 2. 4xx Client error bypass: Client auth failures (UnauthorizedException, BadRequestException)
 *    do not trip circuit breakers or cause Denial of Service for other users.
 */
@Slf4j
@Component
public class CircuitBreaker {

    public enum State {
        CLOSED,     // Normal operations: requests flow through
        OPEN,       // Tripped: fail fast / route to fallback immediately
        HALF_OPEN   // Testing if downstream dependency has recovered
    }

    private static final int FAILURE_THRESHOLD = 5;       // 5 consecutive failures trips breaker
    private static final int HALF_OPEN_SUCCESS_TARGET = 2; // 2 consecutive successes to close breaker
    private static final long RESET_TIMEOUT_MS = 5000L;    // 5s wait time before trying half-open

    public static class ServiceBreaker {
        private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);
        private final AtomicLong lastStateChangeTimestamp = new AtomicLong(System.currentTimeMillis());

        public State getState() {
            return state.get();
        }

        public void reset() {
            state.set(State.CLOSED);
            consecutiveFailures.set(0);
            halfOpenSuccessCount.set(0);
            lastStateChangeTimestamp.set(System.currentTimeMillis());
        }

        public void trip() {
            state.set(State.OPEN);
            lastStateChangeTimestamp.set(System.currentTimeMillis());
        }
    }

    private final ConcurrentMap<String, ServiceBreaker> serviceBreakers = new ConcurrentHashMap<>();
    private volatile String lastActiveServiceName = "default";

    private ServiceBreaker getOrCreateBreaker(String serviceName) {
        String key = (serviceName != null && !serviceName.isBlank()) ? serviceName.trim() : "default";
        lastActiveServiceName = key;
        return serviceBreakers.computeIfAbsent(key, k -> new ServiceBreaker());
    }

    public <T> T execute(String serviceName, Supplier<T> operation) {
        return execute(serviceName, operation, null, this::defaultShouldRecordFailure);
    }

    public <T> T execute(String serviceName, Supplier<T> operation, Supplier<T> fallback) {
        return execute(serviceName, operation, fallback, this::defaultShouldRecordFailure);
    }

    public <T> T execute(String serviceName,
                         Supplier<T> operation,
                         Supplier<T> fallback,
                         Predicate<Throwable> recordFailureOn) {
        ServiceBreaker breaker = getOrCreateBreaker(serviceName);
        checkAndTransitionState(serviceName, breaker);

        if (breaker.state.get() == State.OPEN) {
            log.warn("[CIRCUIT BREAKER: {}] Breaker is OPEN. Executing fallback immediately.", serviceName);
            if (fallback != null) {
                return fallback.get();
            }
            throw new RuntimeException("Circuit breaker is OPEN for service: " + serviceName);
        }

        try {
            T result = operation.get();
            onSuccess(serviceName, breaker);
            return result;
        } catch (Throwable ex) {
            boolean shouldRecord = recordFailureOn != null ? recordFailureOn.test(ex) : defaultShouldRecordFailure(ex);

            if (!shouldRecord) {
                // Client authentication error: propagate without tripping the breaker or invoking downstream fallback
                if (ex instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(ex);
            }

            onFailure(serviceName, breaker, ex);
            if (fallback != null) {
                log.info("[CIRCUIT BREAKER: {}] Downstream failure recorded. Executing fallback.", serviceName);
                return fallback.get();
            }

            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(ex);
        }
    }

    public boolean defaultShouldRecordFailure(Throwable ex) {
        return !(ex instanceof UnauthorizedException
                || ex instanceof BadRequestException
                || ex instanceof ForbiddenException);
    }

    private void onSuccess(String serviceName, ServiceBreaker breaker) {
        breaker.consecutiveFailures.set(0);
        if (breaker.state.get() == State.HALF_OPEN) {
            int successes = breaker.halfOpenSuccessCount.incrementAndGet();
            if (successes >= HALF_OPEN_SUCCESS_TARGET) {
                breaker.state.set(State.CLOSED);
                breaker.lastStateChangeTimestamp.set(System.currentTimeMillis());
                log.info("[CIRCUIT BREAKER: {}] Service recovered! Transitioned from HALF_OPEN -> CLOSED.", serviceName);
            }
        }
    }

    private void onFailure(String serviceName, ServiceBreaker breaker, Throwable ex) {
        log.warn("[CIRCUIT BREAKER: {}] Failure recorded: {}", serviceName, ex.getMessage());
        int failures = breaker.consecutiveFailures.incrementAndGet();

        if (breaker.state.get() == State.HALF_OPEN || failures >= FAILURE_THRESHOLD) {
            breaker.state.set(State.OPEN);
            breaker.lastStateChangeTimestamp.set(System.currentTimeMillis());
            log.error("[CIRCUIT BREAKER: {}] Failure threshold reached ({}/{}). Breaker TRIPPED to OPEN!",
                    serviceName, failures, FAILURE_THRESHOLD);
        }
    }

    private void checkAndTransitionState(String serviceName, ServiceBreaker breaker) {
        if (breaker.state.get() == State.OPEN) {
            long elapsed = System.currentTimeMillis() - breaker.lastStateChangeTimestamp.get();
            if (elapsed >= RESET_TIMEOUT_MS) {
                breaker.state.set(State.HALF_OPEN);
                breaker.halfOpenSuccessCount.set(0);
                breaker.lastStateChangeTimestamp.set(System.currentTimeMillis());
                log.info("[CIRCUIT BREAKER: {}] Reset timeout expired. Transitioned from OPEN -> HALF_OPEN.", serviceName);
            }
        }
    }

    public State getState(String serviceName) {
        return getOrCreateBreaker(serviceName).getState();
    }

    public State getState() {
        return getOrCreateBreaker(lastActiveServiceName).getState();
    }

    public void trip(String serviceName) {
        getOrCreateBreaker(serviceName).trip();
    }

    public void trip() {
        getOrCreateBreaker(lastActiveServiceName).trip();
    }

    public void reset(String serviceName) {
        getOrCreateBreaker(serviceName).reset();
    }

    public void reset() {
        serviceBreakers.values().forEach(ServiceBreaker::reset);
        getOrCreateBreaker("default").reset();
    }
}
