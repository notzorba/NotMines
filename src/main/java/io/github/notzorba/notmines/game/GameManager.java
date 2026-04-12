package io.github.notzorba.notmines.game;

import io.github.notzorba.notmines.NotMinesPlugin;
import io.github.notzorba.notmines.config.PluginSettings;
import io.github.notzorba.notmines.economy.EconomyBridge;
import io.github.notzorba.notmines.gui.GuiConfig;
import io.github.notzorba.notmines.stats.StatsService;
import io.github.notzorba.notmines.util.MessageService;
import io.github.notzorba.notmines.util.Money;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class GameManager {
    private final NotMinesPlugin plugin;
    private final PluginSettings settings;
    private final MessageService messages;
    private final GuiConfig guiConfig;
    private final EconomyBridge economy;
    private final StatsService statsService;
    private final PayoutTable payoutTable;
    private final Map<UUID, MinesSession> activeSessions = new ConcurrentHashMap<>();

    public GameManager(
        final NotMinesPlugin plugin,
        final PluginSettings settings,
        final MessageService messages,
        final GuiConfig guiConfig,
        final EconomyBridge economy,
        final StatsService statsService
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.guiConfig = guiConfig;
        this.economy = economy;
        this.statsService = statsService;
        this.payoutTable = new PayoutTable(settings.houseEdge());
    }

    public void startGame(final Player player, final String betInput, final String minesInput) {
        if (this.activeSessions.containsKey(player.getUniqueId())) {
            this.messages.send(player, "command.already-started");
            return;
        }

        final long betMinor;
        try {
            betMinor = Money.parseMinor(betInput, this.economy.currencyScale());
        } catch (final IllegalArgumentException exception) {
            this.messages.send(player, "command.invalid-number", Placeholder.unparsed("value", betInput));
            return;
        }

        final int mineCount;
        try {
            mineCount = Integer.parseInt(minesInput);
        } catch (final NumberFormatException exception) {
            this.messages.send(player, "command.invalid-number", Placeholder.unparsed("value", minesInput));
            return;
        }

        if (betMinor < this.settings.minBetMinor() || betMinor > this.settings.maxBetMinor()) {
            this.messages.send(
                player,
                "command.invalid-bet",
                Placeholder.unparsed("min", this.economy.format(this.settings.minBetMinor())),
                Placeholder.unparsed("max", this.economy.format(this.settings.maxBetMinor()))
            );
            return;
        }

        if (mineCount < this.settings.minMines() || mineCount > this.settings.maxMines()) {
            this.messages.send(
                player,
                "command.invalid-mines",
                Placeholder.unparsed("min", Integer.toString(this.settings.minMines())),
                Placeholder.unparsed("max", Integer.toString(this.settings.maxMines()))
            );
            return;
        }

        if (!this.economy.has(player, betMinor)) {
            this.messages.send(player, "economy.insufficient-funds", Placeholder.unparsed("required", this.economy.format(betMinor)));
            return;
        }

        final EconomyBridge.EconomyResult withdrawal = this.economy.withdraw(player, betMinor);
        if (!withdrawal.success()) {
            this.messages.send(player, "economy.withdraw-failed");
            return;
        }

        final MinesSession session = new MinesSession(player.getUniqueId(), player.getName(), betMinor, mineCount);
        this.activeSessions.put(player.getUniqueId(), session);
        player.openInventory(MinesMenu.createInventory(session, this.messages, this.guiConfig, this.economy, this.payoutTable));
        this.refreshStatsButton(session, false);

        this.messages.send(
            player,
            "general.game-opening",
            Placeholder.unparsed("bet", this.economy.format(betMinor)),
            Placeholder.unparsed("mines", Integer.toString(mineCount))
        );
    }

    public void reopen(final Player player) {
        final MinesSession session = this.activeSessions.get(player.getUniqueId());
        if (session == null) {
            this.messages.send(player, "command.reopen-empty");
            return;
        }

        session.setSuppressCloseMessage(true);
        player.openInventory(session.inventory());
        MinesMenu.renderActive(session, this.messages, this.guiConfig, this.economy, this.payoutTable);
        this.refreshStatsButton(session, false);
    }

    public void cashOut(final Player player) {
        final MinesSession session = this.activeSessions.get(player.getUniqueId());
        if (session == null) {
            this.messages.send(player, "command.cashout-empty");
            return;
        }

        this.trySettleCashout(player, session, false, true);
    }

    public void handleInventoryClick(final Player player, final int rawSlot) {
        final MinesSession session = this.activeSessions.get(player.getUniqueId());
        if (session == null || session.isSettled()) {
            return;
        }

        if (rawSlot == this.guiConfig.layout().cashoutSlot()) {
            this.trySettleCashout(player, session, false, true);
            return;
        }

        final int tileIndex = MinesMenu.tileFromSlot(this.guiConfig, rawSlot);
        if (tileIndex < 0 || session.isSafeRevealed(tileIndex)) {
            return;
        }

        if (session.isMine(tileIndex)) {
            this.handleMineHit(player, session);
            return;
        }

        if (!session.revealSafe(tileIndex)) {
            return;
        }

        MinesMenu.renderActive(session, this.messages, this.guiConfig, this.economy, this.payoutTable);
        this.messages.send(
            player,
            "game.hit-safe",
            Placeholder.unparsed("multiplier", Money.formatMultiplier(session.currentMultiplier(this.payoutTable))),
            Placeholder.unparsed("payout", this.economy.format(session.currentPayoutMinor(this.payoutTable)))
        );

        if (session.isBoardCleared()) {
            this.trySettleCashout(player, session, false, true);
        }
    }

    public void handleInventoryClose(final Player player) {
        final MinesSession session = this.activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (session.suppressCloseMessage()) {
            session.setSuppressCloseMessage(false);
            return;
        }

        if (!session.isSettled()) {
            this.messages.send(player, "general.board-closed");
        }
    }

    public void handleDisconnect(final Player player) {
        final MinesSession session = this.activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        this.trySettleCashout(player, null, session, true, false);
    }

    public void shutdown() {
        final var sessions = new ArrayList<>(this.activeSessions.values());
        for (MinesSession session : sessions) {
            final Player onlinePlayer = this.plugin.getServer().getPlayer(session.playerId());
            final OfflinePlayer offlinePlayer = onlinePlayer != null ? onlinePlayer : this.plugin.getServer().getOfflinePlayer(session.playerId());
            this.trySettleCashout(offlinePlayer, onlinePlayer, session, true, false);
            if (onlinePlayer != null) {
                this.closeInventoryImmediately(onlinePlayer, session);
            }
        }
        this.activeSessions.clear();
    }

    private void handleMineHit(final Player player, final MinesSession session) {
        if (!session.markSettled()) {
            return;
        }

        this.activeSessions.remove(session.playerId());
        MinesMenu.renderLoss(session, this.messages, this.guiConfig, this.economy);
        this.statsService.recordRound(session.playerId(), session.playerName(), session.betMinor(), 0L, session.safeReveals(), false);
        this.refreshStatsButton(session, true);
        this.messages.send(player, "game.hit-mine", Placeholder.unparsed("bet", this.economy.format(session.betMinor())));
        this.scheduleInventoryClose(player, session);
    }

    private void trySettleCashout(
        final Player player,
        final MinesSession session,
        final boolean forced,
        final boolean closeInventory
    ) {
        this.trySettleCashout(player, player, session, forced, closeInventory);
    }

    private void trySettleCashout(
        final OfflinePlayer payoutTarget,
        final Player viewer,
        final MinesSession session,
        final boolean forced,
        final boolean closeInventory
    ) {
        if (session.isSettled()) {
            return;
        }

        final long payoutMinor = session.currentPayoutMinor(this.payoutTable);
        final EconomyBridge.EconomyResult deposit = this.economy.deposit(payoutTarget, payoutMinor);
        if (!deposit.success()) {
            if (viewer != null && viewer.isOnline()) {
                this.messages.send(viewer, "economy.deposit-failed");
            }
            this.plugin.getLogger().log(
                Level.SEVERE,
                "Failed to pay out NotMines board for " + payoutTarget.getName() + ": " + deposit.errorMessage()
            );
            return;
        }

        if (!session.markSettled()) {
            return;
        }

        this.activeSessions.remove(session.playerId());
        this.statsService.recordRound(session.playerId(), session.playerName(), session.betMinor(), payoutMinor, session.safeReveals(), true);

        if (viewer != null && viewer.isOnline()) {
            MinesMenu.renderCashedOut(session, this.messages, this.guiConfig, this.economy, this.payoutTable, payoutMinor);
            this.refreshStatsButton(session, true);

            if (forced) {
                this.messages.send(viewer, "game.forced-cashout", Placeholder.unparsed("payout", this.economy.format(payoutMinor)));
            } else if (session.isBoardCleared()) {
                this.messages.send(
                    viewer,
                    "game.board-cleared",
                    Placeholder.unparsed("payout", this.economy.format(payoutMinor)),
                    Placeholder.unparsed("multiplier", Money.formatMultiplier(session.currentMultiplier(this.payoutTable)))
                );
            } else {
                this.messages.send(
                    viewer,
                    "game.cashed-out",
                    Placeholder.unparsed("payout", this.economy.format(payoutMinor)),
                    Placeholder.unparsed("multiplier", Money.formatMultiplier(session.currentMultiplier(this.payoutTable)))
                );
            }

            if (closeInventory) {
                this.scheduleInventoryClose(viewer, session);
            }
        }
    }

    private void scheduleInventoryClose(final Player player, final MinesSession session) {
        session.setSuppressCloseMessage(true);
        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == session.inventory()) {
                player.closeInventory();
            }
        }, this.settings.endScreenCloseDelayTicks());
    }

    private void closeInventoryImmediately(final Player player, final MinesSession session) {
        session.setSuppressCloseMessage(true);
        if (player.getOpenInventory().getTopInventory() == session.inventory()) {
            player.closeInventory();
        }
    }

    private void refreshStatsButton(final MinesSession session, final boolean refreshFromService) {
        if (refreshFromService) {
            session.setStatsSnapshot(null);
        }
        MinesMenu.updateStatsButton(session, this.messages, this.guiConfig, this.economy);
        if (!refreshFromService && session.statsSnapshot() != null) {
            return;
        }

        this.statsService.getStatsAsync(session.playerId(), session.playerName()).whenComplete((snapshot, throwable) -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("Failed to refresh GUI stats for " + session.playerName() + ".");
                return;
            }

            session.setStatsSnapshot(snapshot);
            if (!this.plugin.isEnabled()) {
                return;
            }

            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                if (session.inventory() != null) {
                    MinesMenu.updateStatsButton(session, this.messages, this.guiConfig, this.economy);
                }
            });
        });
    }
}
