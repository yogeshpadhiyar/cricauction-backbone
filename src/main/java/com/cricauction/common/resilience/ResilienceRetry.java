package com.cricauction.common.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Resilient Retry mechanism with exponential backoff and randomized jitter
 * for external network calls and transient database failures.
 */
@Slf4j
@Component
public class ResilienceRetry {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long DEFAULT_INITIAL_BACKOFF_MS = 50L;
    private static final double DEFAULT_MULTIPLIER = 2.0;

    public <T> T executeWithRetry(String operationName, Supplier<T> operation) {
        return executeWithRetry(operationName, operation, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_BACKOFF_MS,
                DEFAULT_MULTIPLIER, ex -> true);
    }

    public <T> T executeWithRetry(String operationName,
                                  Supplier<T> operation,
                                  int maxAttempts,
                                  long initialBackoffMs,
                                  double multiplier,
                                  Predicate<Throwable> retryOn) {
        int attempt = 0;
        long currentBackoff = initialBackoffMs;

        while (true) {
            attempt++;
            try {
                return operation.get();
            } catch (Throwable ex) {
                if (attempt >= maxAttempts || (retryOn != null && !retryOn.test(ex))) {
                    log.error("[RETRY EXHAUSTED: {}] Failed after {} attempts: {}", operationName, attempt, ex.getMessage());
                    throw ex;
                }

                // Calculate exponential backoff with jitter
                long jitter = ThreadLocalRandom.current().nextLong(10, 50);
                long sleepTime = currentBackoff + jitter;

                log.warn("[RETRY: {}] Attempt {} failed ({}). Retrying in {} ms...",
                        operationName, attempt, ex.getMessage(), sleepTime);

                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted for operation: " + operationName, ie);
                }

                currentBackoff = (long) (currentBackoff * multiplier);
            }
        }
    }
}
