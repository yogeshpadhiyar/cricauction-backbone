package com.cricauction.common.cache;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe, bounded in-memory LRU cache with time-to-live (TTL) expiration.
 * Prevents JVM memory leaks under high concurrent traffic and crawler activity.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class BoundedLruCache<K, V> {

    private record CacheEntry<V>(V value, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final int maxCapacity;
    private final long ttlSeconds;
    private final Map<K, CacheEntry<V>> map;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public BoundedLruCache(int maxCapacity, long ttlSeconds) {
        this.maxCapacity = Math.max(1, maxCapacity);
        this.ttlSeconds = Math.max(1, ttlSeconds);
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                return size() > BoundedLruCache.this.maxCapacity;
            }
        };
    }

    public V get(K key) {
        if (key == null) return null;
        lock.readLock().lock();
        try {
            CacheEntry<V> entry = map.get(key);
            if (entry == null) {
                return null;
            }
            if (entry.isExpired()) {
                // Entry expired: evict under write lock
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    CacheEntry<V> current = map.get(key);
                    if (current != null && current.isExpired()) {
                        map.remove(key);
                    }
                    return null;
                } finally {
                    lock.readLock().lock(); // Downgrade back to read lock
                    lock.writeLock().unlock();
                }
            }
            return entry.value();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(K key, V value) {
        if (key == null || value == null) return;
        lock.writeLock().lock();
        try {
            map.put(key, new CacheEntry<>(value, Instant.now().plusSeconds(ttlSeconds)));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void remove(K key) {
        if (key == null) return;
        lock.writeLock().lock();
        try {
            map.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            map.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return map.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}

