package io.github.mugishajc.shortener.store;

import io.vertx.core.Future;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread safe in-memory store. Links are lost on restart, which is fine for
 * a demo and for tests.
 */
public class InMemoryLinkStore implements LinkStore {

    private final ConcurrentMap<String, ShortLink> links = new ConcurrentHashMap<>();
    private final AtomicLong sequence;

    public InMemoryLinkStore() {
        // start at 62^3 so generated codes are at least four characters
        this(238_328L);
    }

    public InMemoryLinkStore(long firstId) {
        this.sequence = new AtomicLong(firstId);
    }

    @Override
    public Future<Long> nextId() {
        return Future.succeededFuture(sequence.getAndIncrement());
    }

    @Override
    public Future<ShortLink> insert(ShortLink link) {
        ShortLink existing = links.putIfAbsent(link.code(), link);
        if (existing != null) {
            return Future.failedFuture(new CodeTakenException(link.code()));
        }
        return Future.succeededFuture(link);
    }

    @Override
    public Future<Optional<ShortLink>> find(String code) {
        return Future.succeededFuture(Optional.ofNullable(links.get(code)));
    }

    @Override
    public Future<Optional<ShortLink>> recordClick(String code, Instant at) {
        // computeIfPresent is atomic per key, so concurrent clicks are not lost
        return Future.succeededFuture(Optional.ofNullable(links.computeIfPresent(code, (k, link) -> link.withClick(at))));
    }

    @Override
    public Future<Boolean> delete(String code) {
        return Future.succeededFuture(links.remove(code) != null);
    }

    public int size() {
        return links.size();
    }
}
