package io.github.notzorba.notmines.leaderboard;

import io.github.notzorba.notmines.stats.LeaderboardStat;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class LeaderboardInventoryHolder implements InventoryHolder {
    private final UUID viewerId;
    private final String viewerName;
    private final String viewerSkinTexture;
    private final String viewerSkinTextureSignature;
    private Inventory inventory;
    private LeaderboardStat stat;
    private int page;
    private int requestVersion;

    public LeaderboardInventoryHolder(
        final UUID viewerId,
        final String viewerName,
        final String viewerSkinTexture,
        final String viewerSkinTextureSignature,
        final LeaderboardStat stat
    ) {
        this.viewerId = viewerId;
        this.viewerName = viewerName;
        this.viewerSkinTexture = viewerSkinTexture;
        this.viewerSkinTextureSignature = viewerSkinTextureSignature;
        this.stat = stat;
    }

    public UUID viewerId() {
        return this.viewerId;
    }

    public String viewerName() {
        return this.viewerName;
    }

    public String viewerSkinTexture() {
        return this.viewerSkinTexture;
    }

    public String viewerSkinTextureSignature() {
        return this.viewerSkinTextureSignature;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    public LeaderboardStat stat() {
        return this.stat;
    }

    public void setStat(final LeaderboardStat stat) {
        this.stat = stat;
    }

    public int page() {
        return this.page;
    }

    public void setPage(final int page) {
        this.page = Math.max(0, page);
    }

    public int nextRequestVersion() {
        this.requestVersion++;
        return this.requestVersion;
    }

    public boolean isRequestCurrent(final int requestVersion) {
        return this.requestVersion == requestVersion;
    }

    @Override
    public Inventory getInventory() {
        return Objects.requireNonNull(this.inventory, "Inventory has not been attached yet.");
    }
}
