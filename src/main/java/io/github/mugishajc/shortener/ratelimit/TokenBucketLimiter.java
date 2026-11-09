package io.github.mugishajc.shortener.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * Token bucket per key (the client IP). Each bucket holds up to
 * {@code capacity} tokens and refills continuously at a fixed rate, so short
 * bursts are allowed while the long-run rate stays capped.
 */
public class TokenBucketLimiter {

    public record Decision(boolean allowed, int remaining, long retryAfterSeconds) {
    }

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefillNanos = now;
        }
    }

    private final int capacity;
    private final double tokensPerNano;
    private final LongSupplier nanoClock;
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketLimiter(int capacity, double refillPerSecond) {
        this(capacity, refillPerSecond, System::nanoTime);
    }

    public TokenBucketLimiter(int capacity, double refillPerSecond, LongSupplier nanoClock) {
        if (capacity < 1 || refillPerSecond <= 0) {
            throw new IllegalArgumentException("capacity must be >= 1 and refill rate > 0");
        }
        this.capacity = capacity;
        this.tokensPerNano = refillPerSecond / 1_000_000_000d;
        this.nanoClock = nanoClock;
    }

    public Decision tryAcquire(String key) {
        long now = nanoClock.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));
        synchronized (bucket) {
            refill(bucket, now);
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                return new Decision(true, (int) bucket.tokens, 0);
            }
            double missing = 1 - bucket.tokens;
            long waitSeconds = (long) Math.ceil(missing / tokensPerNano / 1_000_000_000d);
            return new Decision(false, 0, Math.max(1, waitSeconds));
        }
    }

    /**
     * Drops buckets that have been idle long enough to be full again. They
     * would behave the same as a fresh bucket, so nothing is lost.
     */
    public int evictIdle() {
        long now = nanoClock.getAsLong();
        long fullAfterNanos = (long) (capacity / tokensPerNano);
        int before = buckets.size();
        buckets.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return now - e.getValue().lastRefillNanos >= fullAfterNanos;
            }
        });
        return before - buckets.size();
    }

    public int capacity() {
        return capacity;
    }

    int trackedKeys() {
        return buckets.size();
    }

    private void refill(Bucket bucket, long now) {
        long elapsed = now - bucket.lastRefillNanos;
        if (elapsed > 0) {
            bucket.tokens = Math.min(capacity, bucket.tokens + elapsed * tokensPerNano);
            bucket.lastRefillNanos = now;
        }
    }
}
