package io.github.notzorba.notmines.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PayoutTableTest {
    @Test
    void zeroSafePicksAlwaysReturnBaseMultiplier() {
        final PayoutTable payoutTable = new PayoutTable(0.03D);

        assertEquals(1.0D, payoutTable.multiplier(3, 0), 0.000001D);
        assertEquals(1.0D, payoutTable.multiplier(24, 0), 0.000001D);
    }

    @Test
    void multiplierGrowsAsMoreSafeTilesAreCleared() {
        final PayoutTable payoutTable = new PayoutTable(0.03D);

        assertTrue(payoutTable.multiplier(5, 1) > 1.0D);
        assertTrue(payoutTable.multiplier(5, 2) > payoutTable.multiplier(5, 1));
        assertTrue(payoutTable.multiplier(5, 3) > payoutTable.multiplier(5, 2));
    }

    @Test
    void fullClearPaysMoreThanSingleSafeReveal() {
        final PayoutTable payoutTable = new PayoutTable(0.03D);

        final double firstReveal = payoutTable.multiplier(3, 1);
        final double fullClear = payoutTable.multiplier(3, PayoutTable.TOTAL_TILES - 3);

        assertTrue(fullClear > firstReveal);
    }
}
