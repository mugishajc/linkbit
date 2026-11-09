package io.github.mugishajc.shortener.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator("sho.rt");

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/path?q=1#top",
            "HTTPS://Example.com:8443/a/b",
            "https://xn--bcher-kva.example/books"
    })
    void acceptsWebUrls(String url) {
        assertTrue(validator.validate(url).isEmpty(), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/file",
            "javascript:alert(1)",
            "mailto:someone@example.com",
            "example.com/no-scheme",
            "https://",
            "https://exa mple.com"
    })
    void rejectsOtherUrls(String url) {
        assertTrue(validator.validate(url).isPresent(), url);
    }

    @Test
    void rejectsBlankAndOverlongUrls() {
        assertEquals("Field 'url' is required", validator.validate("  ").orElseThrow());
        String longUrl = "https://example.com/" + "a".repeat(UrlValidator.MAX_LENGTH);
        assertTrue(validator.validate(longUrl).orElseThrow().contains("at most"));
    }

    @Test
    void rejectsLinksBackToTheShortener() {
        assertEquals("URL already points at this shortener",
                validator.validate("https://SHO.RT/abcd").orElseThrow());
    }
}
