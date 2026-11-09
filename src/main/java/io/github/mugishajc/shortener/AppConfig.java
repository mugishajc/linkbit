package io.github.mugishajc.shortener;

import io.vertx.core.json.JsonObject;

import java.util.Map;

/**
 * Builds the verticle config from environment variables, falling back to
 * defaults that work for local use.
 */
final class AppConfig {

    private AppConfig() {
    }

    static JsonObject fromEnv(Map<String, String> env) {
        int port = intValue(env, "PORT", 8080);
        return new JsonObject()
                .put("port", port)
                .put("baseUrl", stripTrailingSlash(env.getOrDefault("BASE_URL", "http://localhost:" + port)))
                .put("rateLimitCapacity", intValue(env, "RATE_LIMIT_CAPACITY", 20))
                .put("rateLimitPerMinute", intValue(env, "RATE_LIMIT_PER_MINUTE", 20))
                .put("trustForwardedFor", Boolean.parseBoolean(env.getOrDefault("TRUST_FORWARDED_FOR", "false")));
    }

    private static int intValue(Map<String, String> env, String name, int fallback) {
        String raw = env.get(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number, got '" + raw + "'");
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
