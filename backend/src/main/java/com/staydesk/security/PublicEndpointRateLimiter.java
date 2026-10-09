package com.staydesk.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

/**
 * Simple in-memory sliding-window rate limiter for unauthenticated public endpoints (e.g. the
 * online booking form), which have no staff JWT to key off of. In-memory is sufficient given
 * Staydesk's single-instance deployment; a horizontally-scaled deployment would need a shared
 * store (e.g. Redis) instead.
 */
@Component
public class PublicEndpointRateLimiter {
    private static final int MAX_REQUESTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final Map<String, Deque<Instant>> requestsByKey = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key) {
        Instant now = Instant.now();
        Deque<Instant> timestamps = requestsByKey.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (timestamps) {
            Instant cutoff = now.minus(WINDOW);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= MAX_REQUESTS) {
                return false;
            }

            timestamps.addLast(now);
            return true;
        }
    }

    // Keys accumulate forever otherwise (a spoofed X-Forwarded-For lets a caller mint unlimited
    // distinct keys) -- periodically drop any entry that's had no activity in the last window.
    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    public void evictStaleEntries() {
        Instant cutoff = Instant.now().minus(WINDOW);

        requestsByKey.entrySet().removeIf(entry -> {
            Deque<Instant> timestamps = entry.getValue();

            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                    timestamps.pollFirst();
                }

                return timestamps.isEmpty();
            }
        });
    }
}
