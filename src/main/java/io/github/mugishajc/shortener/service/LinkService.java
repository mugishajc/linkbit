package io.github.mugishajc.shortener.service;

import io.github.mugishajc.shortener.code.Base62;
import io.github.mugishajc.shortener.store.CodeTakenException;
import io.github.mugishajc.shortener.store.LinkStore;
import io.github.mugishajc.shortener.store.ShortLink;
import io.github.mugishajc.shortener.web.ApiException;
import io.vertx.core.Future;

import java.time.Clock;
import java.util.Optional;

public class LinkService {

    // a generated code can clash with a custom alias created earlier
    private static final int MAX_GENERATE_ATTEMPTS = 5;

    private final LinkStore store;
    private final Clock clock;
    private final UrlValidator urlValidator;

    public LinkService(LinkStore store, Clock clock, UrlValidator urlValidator) {
        this.store = store;
        this.clock = clock;
        this.urlValidator = urlValidator;
    }

    /**
     * Creates a short link. When {@code alias} is null a code is generated,
     * otherwise the alias is used and must be free.
     */
    public Future<ShortLink> shorten(String targetUrl, String alias) {
        Optional<String> urlError = urlValidator.validate(targetUrl);
        if (urlError.isPresent()) {
            return Future.failedFuture(ApiException.badRequest(urlError.get()));
        }
        if (alias != null) {
            Optional<String> aliasError = AliasPolicy.validate(alias);
            if (aliasError.isPresent()) {
                return Future.failedFuture(ApiException.badRequest(aliasError.get()));
            }
            return store.insert(ShortLink.create(alias, targetUrl, clock.instant()))
                    .recover(err -> Future.failedFuture(err instanceof CodeTakenException
                            ? new ApiException(409, err.getMessage())
                            : err));
        }
        return insertGenerated(targetUrl, MAX_GENERATE_ATTEMPTS);
    }

    private Future<ShortLink> insertGenerated(String targetUrl, int attemptsLeft) {
        return store.nextId()
                .map(Base62::encode)
                .compose(code -> store.insert(ShortLink.create(code, targetUrl, clock.instant())))
                .recover(err -> {
                    if (err instanceof CodeTakenException && attemptsLeft > 1) {
                        return insertGenerated(targetUrl, attemptsLeft - 1);
                    }
                    return Future.failedFuture(err);
                });
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
