package io.github.notzorba.notmines.stats;

public record StatsDelta(
    String lastKnownName,
    long gamesPlayed,
    long wins,
    long losses,
    long totalWageredMinor,
    long totalPaidMinor,
    long tilesCleared,
    long bestCashoutMinor,
    long biggestBetMinor,
    String skinTexture,
    String skinTextureSignature
) {
    public static StatsDelta fromRound(
        final String playerName,
        final long wagerMinor,
        final long payoutMinor,
        final int safeTilesCleared,
        final boolean won
    ) {
        return fromRound(playerName, wagerMinor, payoutMinor, safeTilesCleared, won, null, null);
    }

    public static StatsDelta fromRound(
        final String playerName,
        final long wagerMinor,
        final long payoutMinor,
        final int safeTilesCleared,
        final boolean won,
        final String skinTexture,
        final String skinTextureSignature
    ) {
        return new StatsDelta(
            playerName,
            1L,
            won ? 1L : 0L,
            won ? 0L : 1L,
            wagerMinor,
            payoutMinor,
            safeTilesCleared,
            payoutMinor,
            wagerMinor,
            skinTexture,
            skinTextureSignature
        );
    }

    public StatsDelta merge(final StatsDelta other) {
        if (other == null) {
            return this;
        }

        final String mergedName = other.lastKnownName.isBlank() ? this.lastKnownName : other.lastKnownName;
        final boolean updatedTexturePresent = hasText(other.skinTexture);
        final String mergedSkinTexture = updatedTexturePresent ? other.skinTexture : this.skinTexture;
        final String mergedSkinTextureSignature = updatedTexturePresent
            ? blankToNull(other.skinTextureSignature)
            : this.skinTextureSignature;
        return new StatsDelta(
            mergedName,
            this.gamesPlayed + other.gamesPlayed,
            this.wins + other.wins,
            this.losses + other.losses,
            this.totalWageredMinor + other.totalWageredMinor,
            this.totalPaidMinor + other.totalPaidMinor,
            this.tilesCleared + other.tilesCleared,
            Math.max(this.bestCashoutMinor, other.bestCashoutMinor),
            Math.max(this.biggestBetMinor, other.biggestBetMinor),
            mergedSkinTexture,
            mergedSkinTextureSignature
        );
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(final String value) {
        return hasText(value) ? value : null;
    }
}
