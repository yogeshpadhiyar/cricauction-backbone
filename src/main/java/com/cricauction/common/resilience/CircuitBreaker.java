package com.cricauction.common.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Thread-safe Circuit Breaker pattern implementation for external service dependencies
 * (Google OAuth token verification, SMS gateways, Email services).
 */
@Slf4j
@Component
public class CircuitBreaker {

    public enum State {
        CLOSED,     // Normal operations: requests flow through
        OPEN,       // Tripped: fail fast / route to fallback immediately
        HALF_OPEN   // Testing if downstream dependency has recovered
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);
    private final AtomicLong lastStateChangeTimestamp = new AtomicLong(System.currentTimeMillis());

    private static final int FAILURE_THRESHOLD = 5;       // 5 consecutive failures trips breaker
    private static final int HALF_OPEN_SUCCESS_TARGET = 2; // 2 consecutive successes to close breaker
    private static final long RESET_TIMEOUT_MS = 5000L;    // 5s wait time before trying half-open

    public <T> T execute(String serviceName, Supplier<T> operation, Supplier<T> fallback) {
        checkAndTransitionState(serviceName);

        if (state.get() == State.OPEN) {
            log.warn("[CIRCUIT BREAKER: {}] Breaker is OPEN. Executing fallback immediately.", serviceName);
            if (fallback != null) {
                return fallback.get();
            }
            throw new RuntimeException("Circuit breaker is OPEN for service: " + serviceName);
        }

        try {
            T result = operation.get();
            onSuccess(serviceName);
            return result;
        } catch (Exception ex) {
            onFailure(serviceName, ex);
            if (fallback != null) {
                log.info("[CIRCUIT BREAKER: {}] Primary operation failed. Executing fallback.", serviceName);
                return fallback.get();
            }
            throw ex;
        }
    }

    private void onSuccess(String serviceName) {
        consecutiveFailures.set(0);
        if (state.get() == State.HALF_OPEN) {
            int successes = halfOpenSuccessCount.incrementAndGet();
            if (successes >= HALF_OPEN_SUCCESS_TARGET) {
                state.set(State.CLOSED);
                lastStateChangeTimestamp.set(System.currentTimeMillis());
                log.info("[CIRCUIT BREAKER: {}] Service recovered! Transitioned from HALF_OPEN -> CLOSED.", serviceName);
            }
        }
    }

    private void onFailure(String serviceName, Throwable ex) {
        log.warn("[CIRCUIT BREAKER: {}] Failure recorded: {}", serviceName, ex.getMessage());
        int failures = consecutiveFailures.incrementAndGet();

        if (state.get() == State.HALF_OPEN || failures >= FAILURE_THRESHOLD) {
            state.set(State.OPEN);
            lastStateChangeTimestamp.set(System.currentTimeMillis());
            log.error("[CIRCUIT BREAKER: {}] Failure threshold reached ({}/{}). Breaker TRIPPED to OPEN!",
                    serviceName, failures, FAILURE_THRESHOLD);
        }
    }

    private void checkAndTransitionState(String serviceName) {
        if (state.get() == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastStateChangeTimestamp.get();
            if (elapsed >= RESET_TIMEOUT_MS) {
                state.set(State.HALF_OPEN);
                halfOpenSuccessCount.set(0);
                lastStateChangeTimestamp.set(System.currentTimeMillis());
                log.info("[CIRCUIT BREAKER: {}] Reset timeout expired. Transitioned from OPEN -> HALF_OPEN.", serviceName);
            }
        }
    }

    public State getState() {
        return state.get();
    }

    public void trip() {
        state.set(State.OPEN);
        lastStateChangeTimestamp.set(System.currentTimeMillis());
    }

    public void reset() {
        state.set(State.CLOSED);
        consecutiveFailures.set(0);
        halfOpenSuccessCount.set(0);
        lastStateChangeTimestamp.set(System.currentTimeMillis());
    }
}
