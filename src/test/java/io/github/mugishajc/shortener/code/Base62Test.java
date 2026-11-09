package io.github.mugishajc.shortener.code;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Base62Test {

    @Test
    void encodesKnownValues() {
        assertEquals("0", Base62.encode(0));
        assertEquals("a", Base62.encode(10));
        assertEquals("Z", Base62.encode(61));
        assertEquals("10", Base62.encode(62));
        assertEquals("1000", Base62.encode(62L * 62 * 62));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 61, 62, 3843, 238_328, 56_800_235_583L, Long.MAX_VALUE})
    void decodeReversesEncode(long value) {
        assertEquals(value, Base62.decode(Base62.encode(value)));
    }

    @Test
    void rejectsNegativeNumbers() {
        assertThrows(IllegalArgumentException.class, () -> Base62.encode(-1));
    }

    @Test
    void rejectsCharactersOutsideTheAlphabet() {
        assertThrows(IllegalArgumentException.class, () -> Base62.decode("ab-c"));
        assertThrows(IllegalArgumentException.class, () -> Base62.decode(""));
        assertFalse(Base62.isValid("abc!"));
        assertFalse(Base62.isValid(""));
        assertTrue(Base62.isValid("aZ09"));
    }

    @Test
    void detectsOverflow() {
        assertThrows(ArithmeticException.class, () -> Base62.decode("zzzzzzzzzzzzzzzz"));
    }
}
