package io.github.notzorba.notmines.game;

import io.github.notzorba.notmines.stats.PlayerStatsSnapshot;
import io.github.notzorba.notmines.util.Money;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.inventory.Inventory;

public final class MinesSession {
    private final UUID playerId;
    private final String playerName;
    private final long betMinor;
    private final int mineCount;
    private final boolean[] mineTiles;
    private final boolean[] revealedSafeTiles;

    private Inventory inventory;
    private boolean settled;
    private boolean suppressCloseMessage;
    private int safeReveals;
    private volatile PlayerStatsSnapshot statsSnapshot;

    public MinesSession(final UUID playerId, final String playerName, final long betMinor, final int mineCount) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.betMinor = betMinor;
        this.mineCount = mineCount;
        this.mineTiles = new boolean[PayoutTable.TOTAL_TILES];
        this.revealedSafeTiles = new boolean[PayoutTable.TOTAL_TILES];
        this.generateMineLayout();
    }

    public UUID playerId() {
        return this.playerId;
    }

    public String playerName() {
        return this.playerName;
    }

    public long betMinor() {
        return this.betMinor;
    }

    public int mineCount() {
        return this.mineCount;
    }

    public Inventory inventory() {
        return this.inventory;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    public boolean isMine(final int tileIndex) {
        return this.mineTiles[tileIndex];
    }

    public boolean isSafeRevealed(final int tileIndex) {
        return this.revealedSafeTiles[tileIndex];
    }

    public boolean revealSafe(final int tileIndex) {
        if (this.mineTiles[tileIndex] || this.revealedSafeTiles[tileIndex]) {
            return false;
        }

        this.revealedSafeTiles[tileIndex] = true;
        this.safeReveals++;
        return true;
    }

    public int safeReveals() {
        return this.safeReveals;
    }

    public int totalSafeTiles() {
        return PayoutTable.TOTAL_TILES - this.mineCount;
    }

    public boolean isBoardCleared() {
        return this.safeReveals >= this.totalSafeTiles();
    }

    public int remainingTiles() {
        return PayoutTable.TOTAL_TILES - this.safeReveals;
    }

    public int remainingSafeTiles() {
        return this.totalSafeTiles() - this.safeReveals;
    }

    public double currentMultiplier(final PayoutTable payoutTable) {
        return payoutTable.multiplier(this.mineCount, this.safeReveals);
    }

    public long currentPayoutMinor(final PayoutTable payoutTable) {
        return Money.applyMultiplier(this.betMinor, this.currentMultiplier(payoutTable));
    }

    public double nextSafeChancePercent() {
        if (this.settled || this.remainingTiles() <= 0) {
            return 0.0D;
        }

        return ((double) this.remainingSafeTiles() / (double) this.remainingTiles()) * 100.0D;
    }

    public boolean isSettled() {
        return this.settled;
    }

    public boolean markSettled() {
        if (this.settled) {
            return false;
        }

        this.settled = true;
        return true;
    }

    public boolean suppressCloseMessage() {
        return this.suppressCloseMessage;
    }

    public void setSuppressCloseMessage(final boolean suppressCloseMessage) {
        this.suppressCloseMessage = suppressCloseMessage;
    }

    public PlayerStatsSnapshot statsSnapshot() {
        return this.statsSnapshot;
    }

    public void setStatsSnapshot(final PlayerStatsSnapshot statsSnapshot) {
        this.statsSnapshot = statsSnapshot;
    }

    private void generateMineLayout() {
        int placed = 0;
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        while (placed < this.mineCount) {
            final int slot = random.nextInt(PayoutTable.TOTAL_TILES);
            if (!this.mineTiles[slot]) {
                this.mineTiles[slot] = true;
                placed++;
            }
        }
    }
}
