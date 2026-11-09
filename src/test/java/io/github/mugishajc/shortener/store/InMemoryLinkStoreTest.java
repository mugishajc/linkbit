package io.github.mugishajc.shortener.store;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryLinkStoreTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private final InMemoryLinkStore store = new InMemoryLinkStore(100);

    @Test
    void handsOutIncreasingIds() {
        assertEquals(100L, store.nextId().result());
        assertEquals(101L, store.nextId().result());
    }

    @Test
    void storesAndFindsLinks() {
        store.insert(ShortLink.create("abcd", "https://example.com", NOW));

        Optional<ShortLink> found = store.find("abcd").result();
        assertTrue(found.isPresent());
        assertEquals("https://example.com", found.get().targetUrl());
        assertTrue(store.find("nope").result().isEmpty());
    }

    @Test
    void refusesDuplicateCodes() {
        store.insert(ShortLink.create("docs", "https://example.com/a", NOW));

        var second = store.insert(ShortLink.create("docs", "https://example.com/b", NOW));

        assertTrue(second.failed());
        assertInstanceOf(CodeTakenException.class, second.cause());
        assertEquals("https://example.com/a", store.find("docs").result().orElseThrow().targetUrl());
    }

    @Test
    void recordsClicks() {
        store.insert(ShortLink.create("abcd", "https://example.com", NOW));
        Instant later = NOW.plusSeconds(60);

        store.recordClick("abcd", NOW);
        ShortLink updated = store.recordClick("abcd", later).result().orElseThrow();

        assertEquals(2, updated.clicks());
        assertEquals(later, updated.lastClickedAt());
        assertTrue(store.recordClick("missing", NOW).result().isEmpty());
    }

    @Test
    void doesNotLoseConcurrentClicks() throws InterruptedException {
        store.insert(ShortLink.create("busy", "https://example.com", NOW));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 1000; i++) {
            pool.execute(() -> store.recordClick("busy", NOW));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1000, store.find("busy").result().orElseThrow().clicks());
    }

    @Test
    void deletesLinks() {
        store.insert(ShortLink.create("gone", "https://example.com", NOW));

        assertTrue(store.delete("gone").result());
        assertFalse(store.delete("gone").result());
        assertEquals(0, store.size());
    }
}
