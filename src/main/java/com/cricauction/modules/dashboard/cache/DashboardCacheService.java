package com.cricauction.modules.dashboard.cache;

import com.cricauction.common.cache.BoundedLruCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Thread-safe, bounded in-memory cache with non-blocking Single-Flight Request Coalescing.
 * Shields the database from thundering herd and cache stampedes under flash traffic
 * without holding map bucket locks during database execution or leaking heap memory.
 */
@Slf4j
@Component
public class DashboardCacheService {

    public static final int MAX_CACHE_ENTRIES = 5000;
    public static final long DEFAULT_TTL_SECONDS = 3;

    private final BoundedLruCache<String, Object> boundedCache;
    private final ConcurrentHashMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

    public DashboardCacheService() {
        this.boundedCache = new BoundedLruCache<>(MAX_CACHE_ENTRIES, DEFAULT_TTL_SECONDS);
    }

    public DashboardCacheService(int maxCapacity, long ttlSeconds) {
        this.boundedCache = new BoundedLruCache<>(maxCapacity, ttlSeconds);
    }

    public <T> T getOrCompute(String key, Supplier<T> supplier) {
        if (key == null) {
            return supplier.get();
        }

        // 1. Fast path: check bounded LRU cache (hit returns immediately without coalescing)
        Object cached = boundedCache.get(key);
        if (cached != null) {
            @SuppressWarnings("unchecked")
            T val = (T) cached;
            return val;
        }

        // 2. Single-flight request coalescing without holding hash bucket locks during I/O
        CompletableFuture<Object> future = new CompletableFuture<>();
        CompletableFuture<Object> existingInFlight = inFlight.putIfAbsent(key, future);

        if (existingInFlight != null) {
            // Another thread is already computing this key; await its result
            try {
                @SuppressWarnings("unchecked")
                T result = (T) existingInFlight.join();
                return result;
            } catch (Exception ex) {
                log.warn("In-flight computation failed for key [{}], falling back to direct supplier: {}", key, ex.getMessage());
            }
        }

        // 3. This thread won the race to execute the supplier on the current thread
        try {
            T computed = supplier.get();
            if (computed != null) {
                boundedCache.put(key, computed);
            }
            future.complete(computed);
            return computed;
        } catch (Throwable t) {
            future.completeExceptionally(t);
            throw t;
        } finally {
            inFlight.remove(key, future);
        }
    }

    public void evict(String key) {
        if (key != null) {
            boundedCache.remove(key);
            inFlight.remove(key);
        }
    }

    public void clear() {
        boundedCache.clear();
        inFlight.clear();
    }

    public int size() {
        return boundedCache.size();
    }
}
