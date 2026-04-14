package io.github.notzorba.notmines.stats;

import java.util.Locale;
import java.util.UUID;

public record PlayerStatsSnapshot(
    UUID uuid,
    String lastKnownName,
    String searchName,
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
    public static PlayerStatsSnapshot empty(final UUID uuid, final String playerName) {
        return new PlayerStatsSnapshot(
            uuid,
            playerName,
            normalizeName(playerName),
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            null,
            null
        );
    }

    public PlayerStatsSnapshot merge(final StatsDelta delta) {
        if (delta == null) {
            return this;
        }

        final String mergedName = delta.lastKnownName().isBlank() ? this.lastKnownName : delta.lastKnownName();
        final boolean updatedTexturePresent = hasText(delta.skinTexture());
        final String mergedSkinTexture = updatedTexturePresent ? delta.skinTexture() : this.skinTexture;
        final String mergedSkinTextureSignature = updatedTexturePresent
            ? blankToNull(delta.skinTextureSignature())
            : this.skinTextureSignature;
        return new PlayerStatsSnapshot(
            this.uuid,
            mergedName,
            normalizeName(mergedName),
            this.gamesPlayed + delta.gamesPlayed(),
            this.wins + delta.wins(),
            this.losses + delta.losses(),
            this.totalWageredMinor + delta.totalWageredMinor(),
            this.totalPaidMinor + delta.totalPaidMinor(),
            this.tilesCleared + delta.tilesCleared(),
            Math.max(this.bestCashoutMinor, delta.bestCashoutMinor()),
            Math.max(this.biggestBetMinor, delta.biggestBetMinor()),
            mergedSkinTexture,
            mergedSkinTextureSignature
        );
    }

    public boolean hasActivity() {
        return this.gamesPlayed > 0L;
    }

    public boolean hasSkinTexture() {
        return this.skinTexture != null && !this.skinTexture.isBlank();
    }

    public long netProfitMinor() {
        return this.totalPaidMinor - this.totalWageredMinor;
    }

    public double winRate() {
        return this.gamesPlayed == 0L ? 0.0D : ((double) this.wins / (double) this.gamesPlayed) * 100.0D;
    }

    public static String normalizeName(final String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(final String value) {
        return hasText(value) ? value : null;
    }
}
