package io.github.mugishajc.shortener.store;

import io.vertx.core.json.JsonObject;

import java.time.Instant;

/**
 * Immutable snapshot of a short link and its click statistics.
 */
public record ShortLink(
        String code,
        String targetUrl,
        Instant createdAt,
        long clicks,
        Instant lastClickedAt) {

    public static ShortLink create(String code, String targetUrl, Instant now) {
        return new ShortLink(code, targetUrl, now, 0, null);
    }

    public ShortLink withClick(Instant at) {
        return new ShortLink(code, targetUrl, createdAt, clicks + 1, at);
    }

    public JsonObject toJson(String baseUrl) {
        return new JsonObject()
                .put("code", code)
                .put("shortUrl", baseUrl + "/" + code)
                .put("targetUrl", targetUrl)
                .put("createdAt", createdAt.toString())
                .put("clicks", clicks)
                .put("lastClickedAt", lastClickedAt == null ? null : lastClickedAt.toString());
    }
}
