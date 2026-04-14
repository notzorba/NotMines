package io.github.notzorba.notmines.listener;

import io.github.notzorba.notmines.game.GameManager;
import io.github.notzorba.notmines.game.MinesInventoryHolder;
import io.github.notzorba.notmines.leaderboard.LeaderboardInventoryHolder;
import io.github.notzorba.notmines.leaderboard.LeaderboardManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class MinesListener implements Listener {
    private final GameManager gameManager;
    private final LeaderboardManager leaderboardManager;

    public MinesListener(final GameManager gameManager, final LeaderboardManager leaderboardManager) {
        this.gameManager = gameManager;
        this.leaderboardManager = leaderboardManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(final InventoryClickEvent event) {
        final Object holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof MinesInventoryHolder) && !(holder instanceof LeaderboardInventoryHolder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            if (holder instanceof MinesInventoryHolder) {
                this.gameManager.handleInventoryClick(player, event.getRawSlot());
                return;
            }

            this.leaderboardManager.handleInventoryClick(player, (LeaderboardInventoryHolder) holder, event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(final InventoryDragEvent event) {
        final Object holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof MinesInventoryHolder) && !(holder instanceof LeaderboardInventoryHolder)) {
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        this.gameManager.handleJoin(event.getPlayer());
    }
}
