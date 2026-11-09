package io.github.mugishajc.shortener.service;

import io.github.mugishajc.shortener.code.Base62;
import io.github.mugishajc.shortener.store.LinkStore;
import io.github.mugishajc.shortener.store.ShortLink;
import io.vertx.core.Future;

import java.time.Clock;
import java.util.Optional;

public class LinkService {

    private final LinkStore store;
    private final Clock clock;

    public LinkService(LinkStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public Future<ShortLink> shorten(String targetUrl) {
        return store.nextId()
                .map(Base62::encode)
                .compose(code -> store.insert(ShortLink.create(code, targetUrl, clock.instant())));
    }

    public Future<Optional<ShortLink>> find(String code) {
        return store.find(code);
    }

    /** Looks up the link for a redirect and counts the click. */
    public Future<Optional<ShortLink>> resolve(String code) {
        return store.recordClick(code, clock.instant());
    }

    public Future<Boolean> delete(String code) {
        return store.delete(code);
    }
}
