package io.github.mugishajc.shortener;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @Test
    void usesDefaultsWhenNothingIsSet() {
        JsonObject config = AppConfig.fromEnv(Map.of());

        assertEquals(8080, config.getInteger("port"));
        assertEquals("http://localhost:8080", config.getString("baseUrl"));
        assertEquals(20, config.getInteger("rateLimitCapacity"));
        assertFalse(config.getBoolean("trustForwardedFor"));
    }

    @Test
    void readsEnvironment() {
        JsonObject config = AppConfig.fromEnv(Map.of(
                "PORT", "9000",
                "BASE_URL", "https://go.example.org/",
                "RATE_LIMIT_CAPACITY", "5",
                "RATE_LIMIT_PER_MINUTE", "10",
                "TRUST_FORWARDED_FOR", "true"));

        assertEquals(9000, config.getInteger("port"));
        assertEquals("https://go.example.org", config.getString("baseUrl"));
        assertEquals(5, config.getInteger("rateLimitCapacity"));
        assertEquals(10, config.getInteger("rateLimitPerMinute"));
        assertTrue(config.getBoolean("trustForwardedFor"));
    }

    @Test
    void failsFastOnBadNumbers() {
        assertThrows(IllegalArgumentException.class, () -> AppConfig.fromEnv(Map.of("PORT", "eighty")));
    }
}
