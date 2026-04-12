package io.github.notzorba.notmines.listener;

import io.github.notzorba.notmines.game.GameManager;
import io.github.notzorba.notmines.game.MinesInventoryHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class MinesListener implements Listener {
    private final GameManager gameManager;

    public MinesListener(final GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MinesInventoryHolder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            this.gameManager.handleInventoryClick(player, event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MinesInventoryHolder)) {
            return;
        }

        final int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MinesInventoryHolder)) {
            return;
        }

        if (event.getPlayer() instanceof Player player) {
            this.gameManager.handleInventoryClose(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.gameManager.handleDisconnect(event.getPlayer());
    }
}
