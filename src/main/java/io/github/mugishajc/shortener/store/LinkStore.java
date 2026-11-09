package io.github.mugishajc.shortener.store;

import io.vertx.core.Future;

import java.time.Instant;
import java.util.Optional;

/**
 * Storage for short links. Methods return futures so a database-backed
 * implementation can be dropped in without blocking the event loop.
 */
public interface LinkStore {

    /** Returns the next unique sequence number used to generate codes. */
    Future<Long> nextId();

    /**
     * Saves a new link. Fails with {@link CodeTakenException} if the code is
     * already in use.
     */
    Future<ShortLink> insert(ShortLink link);

    Future<Optional<ShortLink>> find(String code);

    /** Counts a click and returns the updated link, or empty if unknown. */
    Future<Optional<ShortLink>> recordClick(String code, Instant at);

    Future<Boolean> delete(String code);
}
