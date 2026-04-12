package io.github.notzorba.notmines.game;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MinesInventoryHolder implements InventoryHolder {
    private final UUID ownerId;
    private Inventory inventory;

    public MinesInventoryHolder(final UUID ownerId) {
        this.ownerId = ownerId;
    }

    public UUID ownerId() {
        return this.ownerId;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return Objects.requireNonNull(this.inventory, "Inventory has not been attached yet.");
    }
}
