package io.github.mugishajc.shortener.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/**
 * Checks that a target URL is something we are willing to redirect to.
 */
public class UrlValidator {

    static final int MAX_LENGTH = 2048;

    private final String ownHost;

    /**
     * @param ownHost host of this service; links back to it are refused so a
     *                short link cannot point at another short link
     */
    public UrlValidator(String ownHost) {
        this.ownHost = ownHost == null ? null : ownHost.toLowerCase(Locale.ROOT);
    }

    /** Returns an error message, or empty if the URL is acceptable. */
    public Optional<String> validate(String url) {
        if (url == null || url.isBlank()) {
            return Optional.of("Field 'url' is required");
        }
        if (url.length() > MAX_LENGTH) {
            return Optional.of("URL must be at most " + MAX_LENGTH + " characters");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return Optional.of("URL is not valid");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Optional.of("Only http and https URLs can be shortened");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return Optional.of("URL must include a host");
        }
        if (ownHost != null && uri.getHost().toLowerCase(Locale.ROOT).equals(ownHost)) {
            return Optional.of("URL already points at this shortener");
        }
        return Optional.empty();
    }
}
