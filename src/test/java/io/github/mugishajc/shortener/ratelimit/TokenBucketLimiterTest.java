package io.github.mugishajc.shortener.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketLimiterTest {

    private static final long SECOND = 1_000_000_000L;

    private final AtomicLong now = new AtomicLong(0);
    // 3 requests burst, one new token every 2 seconds
    private final TokenBucketLimiter limiter = new TokenBucketLimiter(3, 0.5, now::get);

    @Test
    void allowsBurstUpToCapacity() {
        assertEquals(2, limiter.tryAcquire("1.1.1.1").remaining());
        assertEquals(1, limiter.tryAcquire("1.1.1.1").remaining());
        assertEquals(0, limiter.tryAcquire("1.1.1.1").remaining());

        TokenBucketLimiter.Decision blocked = limiter.tryAcquire("1.1.1.1");
        assertFalse(blocked.allowed());
        assertEquals(2, blocked.retryAfterSeconds());
    }

    @Test
    void refillsOverTime() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("ip");
        }
        assertFalse(limiter.tryAcquire("ip").allowed());

        now.addAndGet(SECOND);
        TokenBucketLimiter.Decision halfway = limiter.tryAcquire("ip");
        assertFalse(halfway.allowed());
        assertEquals(1, halfway.retryAfterSeconds());

        now.addAndGet(SECOND);
        assertTrue(limiter.tryAcquire("ip").allowed());
    }

    @Test
    void neverRefillsAboveCapacity() {
        now.addAndGet(3600 * SECOND);
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("ip").allowed());
        }
        assertFalse(limiter.tryAcquire("ip").allowed());
    }

    @Test
    void keepsSeparateBucketsPerKey() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }
        assertFalse(limiter.tryAcquire("a").allowed());
        assertTrue(limiter.tryAcquire("b").allowed());
    }

    @Test
    void evictsOnlyBucketsThatAreFullAgain() {
        limiter.tryAcquire("old");
        now.addAndGet(5 * SECOND);
        limiter.tryAcquire("recent");
        now.addAndGet(SECOND);

        assertEquals(1, limiter.evictIdle());
        assertEquals(1, limiter.trackedKeys());
    }

    @Test
    void rejectsBadSettings() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketLimiter(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketLimiter(5, 0));
    }
}
