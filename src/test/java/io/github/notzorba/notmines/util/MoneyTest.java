package io.github.notzorba.notmines.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MoneyTest {
    @Test
    void parseMinorSupportsPlainNumbers() {
        assertEquals(12_345L, Money.parseMinor("123.45", 2));
        assertEquals(123_400L, Money.parseMinor("1,234", 2));
    }

    @Test
    void parseMinorSupportsCompactSuffixes() {
        assertEquals(100_000L, Money.parseMinor("1k", 2));
        assertEquals(110_000L, Money.parseMinor("1.1K", 2));
        assertEquals(140_000_000L, Money.parseMinor("1.4m", 2));
        assertEquals(2_000_000_000L, Money.parseMinor("20m", 2));
        assertEquals(100_000_000_000L, Money.parseMinor("1b", 2));
    }

    @Test
    void parseMinorRejectsMissingNumericPortion() {
        assertThrows(IllegalArgumentException.class, () -> Money.parseMinor("k", 2));
        assertThrows(IllegalArgumentException.class, () -> Money.parseMinor("   ", 2));
    }
}
