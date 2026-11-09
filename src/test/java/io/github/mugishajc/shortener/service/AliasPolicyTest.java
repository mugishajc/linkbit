package io.github.mugishajc.shortener.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"docs", "spring-sale", "Q4_report", "abc"})
    void acceptsSimpleAliases(String alias) {
        assertTrue(AliasPolicy.validate(alias).isEmpty(), alias);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "has space", "slash/inside", "dot.ted", "API", "health"})
    void rejectsBadOrReservedAliases(String alias) {
        assertTrue(AliasPolicy.validate(alias).isPresent(), alias);
    }

    @ParameterizedTest
    @ValueSource(ints = {33, 100})
    void rejectsLongAliases(int length) {
        assertTrue(AliasPolicy.validate("a".repeat(length)).isPresent());
    }
}
