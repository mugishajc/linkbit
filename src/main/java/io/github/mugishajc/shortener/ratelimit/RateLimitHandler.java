package io.github.mugishajc.shortener.ratelimit;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Rejects requests with 429 once a client IP runs out of tokens.
 */
public class RateLimitHandler implements Handler<RoutingContext> {

    private final TokenBucketLimiter limiter;
    private final boolean trustForwardedFor;

    /**
     * @param trustForwardedFor read the client IP from X-Forwarded-For. Only
     *                          enable this behind a proxy that sets the header,
     *                          otherwise clients can pick their own key.
     */
    public RateLimitHandler(TokenBucketLimiter limiter, boolean trustForwardedFor) {
        this.limiter = limiter;
        this.trustForwardedFor = trustForwardedFor;
    }

    @Override
    public void handle(RoutingContext ctx) {
        TokenBucketLimiter.Decision decision = limiter.tryAcquire(clientIp(ctx));
        ctx.response().putHeader("X-RateLimit-Limit", String.valueOf(limiter.capacity()));
        ctx.response().putHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (decision.allowed()) {
            ctx.next();
            return;
        }
        ctx.response()
                .setStatusCode(429)
                .putHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()))
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                        .put("status", 429)
                        .put("error", "Too many requests, retry in " + decision.retryAfterSeconds() + "s")
                        .encode());
    }

    String clientIp(RoutingContext ctx) {
        if (trustForwardedFor) {
            String forwarded = ctx.request().getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // the left-most entry is the original client
                return forwarded.split(",")[0].trim();
            }
        }
        return ctx.request().remoteAddress() == null ? "unknown" : ctx.request().remoteAddress().host();
    }
}
