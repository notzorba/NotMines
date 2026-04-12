package io.github.notzorba.notmines.game;

public final class PayoutTable {
    public static final int TOTAL_TILES = 25;

    private final double[][] multipliers = new double[TOTAL_TILES][TOTAL_TILES];

    public PayoutTable(final double houseEdge) {
        final double edgeMultiplier = 1.0D - houseEdge;
        for (int mines = 1; mines < TOTAL_TILES; mines++) {
            this.multipliers[mines][0] = 1.0D;

            final int maxSafePicks = TOTAL_TILES - mines;
            for (int safePicks = 1; safePicks <= maxSafePicks; safePicks++) {
                double survivalChance = 1.0D;
                for (int pick = 0; pick < safePicks; pick++) {
                    survivalChance *= (double) (TOTAL_TILES - mines - pick) / (double) (TOTAL_TILES - pick);
                }

                this.multipliers[mines][safePicks] = (1.0D / survivalChance) * edgeMultiplier;
            }
        }
    }

    public double multiplier(final int mineCount, final int safePicks) {
        if (mineCount <= 0 || mineCount >= TOTAL_TILES) {
            throw new IllegalArgumentException("Mine count must be between 1 and 24.");
        }

        final int maxSafePicks = TOTAL_TILES - mineCount;
        if (safePicks < 0 || safePicks > maxSafePicks) {
            throw new IllegalArgumentException("Safe picks must be between 0 and " + maxSafePicks + ".");
        }

        return this.multipliers[mineCount][safePicks];
    }
}
