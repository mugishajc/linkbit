package io.github.mugishajc.shortener.service;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rules for custom aliases chosen by the caller instead of a generated code.
 */
public final class AliasPolicy {

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_-]{3,32}");
    private static final Set<String> RESERVED = Set.of("api", "health", "admin", "static");

    private AliasPolicy() {
    }

    public static Optional<String> validate(String alias) {
        if (!ALLOWED.matcher(alias).matches()) {
            return Optional.of("Alias must be 3 to 32 characters: letters, digits, '-' or '_'");
        }
        if (RESERVED.contains(alias.toLowerCase())) {
            return Optional.of("Alias '" + alias + "' is reserved");
        }
        return Optional.empty();
    }
}
