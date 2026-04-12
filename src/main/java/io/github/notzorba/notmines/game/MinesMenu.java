package io.github.notzorba.notmines.game;

import io.github.notzorba.notmines.economy.EconomyBridge;
import io.github.notzorba.notmines.gui.GuiConfig;
import io.github.notzorba.notmines.gui.GuiItemTemplate;
import io.github.notzorba.notmines.stats.PlayerStatsSnapshot;
import io.github.notzorba.notmines.util.ItemFactory;
import io.github.notzorba.notmines.util.MessageService;
import io.github.notzorba.notmines.util.Money;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class MinesMenu {
    private MinesMenu() {
    }

    public static Inventory createInventory(
        final MinesSession session,
        final MessageService messages,
        final GuiConfig guiConfig,
        final EconomyBridge economy,
        final PayoutTable payouts
    ) {
        final MinesInventoryHolder holder = new MinesInventoryHolder(session.playerId());
        final Inventory inventory = Bukkit.createInventory(
            holder,
            guiConfig.layout().inventorySize(),
            messages.renderRaw(guiConfig.title())
        );
        holder.setInventory(inventory);
        session.setInventory(inventory);
        renderActive(session, messages, guiConfig, economy, payouts);
        return inventory;
    }

    public static void renderActive(
        final MinesSession session,
        final MessageService messages,
        final GuiConfig guiConfig,
        final EconomyBridge economy,
        final PayoutTable payouts
    ) {
        final Inventory inventory = session.inventory();
        fillBackground(inventory, messages, guiConfig);

        for (int tileIndex = 0; tileIndex < guiConfig.layout().boardSlots().length; tileIndex++) {
            final int slot = guiConfig.layout().boardSlots()[tileIndex];
            if (session.isSafeRevealed(tileIndex)) {
                inventory.setItem(slot, safeTile(messages, guiConfig, economy, session, payouts));
            } else {
                inventory.setItem(slot, hiddenTile(messages, guiConfig, session));
            }
        }

        inventory.setItem(guiConfig.layout().infoSlot(), boardInfo(messages, guiConfig, session));
        updateStatsButton(session, messages, guiConfig, economy);
        inventory.setItem(guiConfig.layout().betSlot(), betInfo(messages, guiConfig, economy, session));
        inventory.setItem(guiConfig.layout().cashoutSlot(), cashoutButton(messages, guiConfig, economy, session, payouts));
        inventory.setItem(guiConfig.layout().oddsSlot(), oddsInfo(messages, guiConfig, session, payouts));
        inventory.setItem(guiConfig.layout().helpSlot(), helpInfo(messages, guiConfig));
    }

    public static void renderLoss(
        final MinesSession session,
        final MessageService messages,
        final GuiConfig guiConfig,
        final EconomyBridge economy
    ) {
        final Inventory inventory = session.inventory();
        fillBackground(inventory, messages, guiConfig);

        for (int tileIndex = 0; tileIndex < guiConfig.layout().boardSlots().length; tileIndex++) {
            final int slot = guiConfig.layout().boardSlots()[tileIndex];
            if (session.isMine(tileIndex)) {
                inventory.setItem(slot, mineTile(messages, guiConfig));
            } else if (session.isSafeRevealed(tileIndex)) {
                inventory.setItem(slot, safeTile(messages, guiConfig, economy, session, null));
            } else {
                inventory.setItem(slot, lostSafeTile(messages, guiConfig));
            }
        }

        inventory.setItem(guiConfig.layout().infoSlot(), boardLossInfo(messages, guiConfig, session));
        updateStatsButton(session, messages, guiConfig, economy);
        inventory.setItem(guiConfig.layout().betSlot(), betInfo(messages, guiConfig, economy, session));
        inventory.setItem(guiConfig.layout().cashoutSlot(), disabledButton(messages, guiConfig));
        inventory.setItem(guiConfig.layout().oddsSlot(), oddsLossInfo(messages, guiConfig));
        inventory.setItem(guiConfig.layout().helpSlot(), helpInfo(messages, guiConfig));
    }

    public static void renderCashedOut(
        final MinesSession session,
        final MessageService messages,
        final GuiConfig guiConfig,
        final EconomyBridge economy,
        final PayoutTable payouts,
        final long payoutMinor
    ) {
        final Inventory inventory = session.inventory();
        fillBackground(inventory, messages, guiConfig);

        for (int tileIndex = 0; tileIndex < guiConfig.layout().boardSlots().length; tileIndex++) {
            final int slot = guiConfig.layout().boardSlots()[tileIndex];
            if (session.isSafeRevealed(tileIndex)) {
                inventory.setItem(slot, safeTile(messages, guiConfig, economy, session, payouts));
            } else {
                inventory.setItem(slot, cashedOutTile(messages, guiConfig));
            }
        }

        inventory.setItem(guiConfig.layout().infoSlot(), boardCashoutInfo(messages, guiConfig, session, payouts));
        updateStatsButton(session, messages, guiConfig, economy);
        inventory.setItem(guiConfig.layout().betSlot(), betInfo(messages, guiConfig, economy, session));
        inventory.setItem(guiConfig.layout().cashoutSlot(), cashoutCollectedInfo(messages, guiConfig, payoutMinor, economy));
        inventory.setItem(guiConfig.layout().oddsSlot(), oddsCashoutInfo(messages, guiConfig));
        inventory.setItem(guiConfig.layout().helpSlot(), helpInfo(messages, guiConfig));
    }

    public static int tileFromSlot(final GuiConfig guiConfig, final int rawSlot) {
        for (int tileIndex = 0; tileIndex < guiConfig.layout().boardSlots().length; tileIndex++) {
            if (guiConfig.layout().boardSlots()[tileIndex] == rawSlot) {
                return tileIndex;
            }
        }

        return -1;
    }

    private static void fillBackground(final Inventory inventory, final MessageService messages, final GuiConfig guiConfig) {
        final ItemStack filler = item(messages, guiConfig.filler());
        for (int slot = 0; slot < guiConfig.layout().inventorySize(); slot++) {
            inventory.setItem(slot, filler.clone());
        }
    }

    private static org.bukkit.inventory.ItemStack hiddenTile(
        final MessageService messages,
        final GuiConfig guiConfig,
        final MinesSession session
    ) {
        return item(
            messages,
            guiConfig.hiddenTile(),
            placeholder("mines", Integer.toString(session.mineCount())),
            placeholder("chance", Money.formatPercent(session.nextSafeChancePercent()))
        );
    }

    private static org.bukkit.inventory.ItemStack safeTile(
        final MessageService messages,
        final GuiConfig guiConfig,
        final EconomyBridge economy,
        final MinesSession session,
        final PayoutTable payouts
    ) {
        return item(
            messages,
            guiConfig.safeTile(),
            placeholder("picks", Integer.toString(session.safeReveals())),
            placeholder("payout", payouts == null ? economy.format(0L) : economy.format(session.currentPayoutMinor(payouts)))
        );
    }

    private static org.bukkit.inventory.ItemStack lostSafeTile(final MessageService messages, final GuiConfig guiConfig) {
        return item(messages, guiConfig.unrevealedSafeTile());
    }

    private static org.bukkit.inventory.ItemStack cashedOutTile(final MessageService messages, final GuiConfig guiConfig) {
        return item(messages, guiConfig.cashedOutTile());
    }

    private static org.bukkit.inventory.ItemStack mineTile(final MessageService messages, final GuiConfig guiConfig) {
        return item(messages, guiConfig.mineTile());
    }

    private static org.bukkit.inventory.ItemStack cashoutButton(
        final MessageService messages,
        final GuiConfig guiConfig,
        final EconomyBridge economy,
        final MinesSession session,
        final PayoutTable payouts
    ) {
        return item(
            messages,
            guiConfig.cashoutActive(),
            placeholder("multiplier", Money.formatMultiplier(session.currentMultiplier(payouts))),
            placeholder("payout", economy.format(session.currentPayoutMinor(payouts)))
        );
    }

    private static org.bukkit.inventory.ItemStack disabledButton(final MessageService messages, final GuiConfig guiConfig) {
        return item(messages, guiConfig.cashoutDisabled());
    }

    private static org.bukkit.inventory.ItemStack boardInfo(
        final MessageService messages,
        final GuiConfig guiConfig,
        final MinesSession session
    ) {
        return item(
            messages,
            guiConfig.boardInfoActive(),
            placeholder("safe_picks", Integer.toString(session.safeReveals())),
            placeholder("remaining_safe", Integer.toString(session.remainingSafeTiles())),
            placeholder("mines", Integer.toString(session.mineCount()))
        );
    }

    private static org.bukkit.inventory.ItemStack boardLossInfo(
        final MessageService messages,
        final GuiConfig guiConfig,
        final MinesSession session
    ) {
        return item(
            messages,
            guiConfig.boardInfoLoss(),
            placeholder("safe_picks", Integer.toString(session.safeReveals()))
        );
    }

    private static org.bukkit.inventory.ItemStack boardCashoutInfo(
        final MessageService messages,
        final GuiConfig guiConfig,
        final MinesSession session,
        final PayoutTable payouts
    ) {
        return item(
            messages,
            guiConfig.boardInfoCashout(),
            placeholder("safe_picks", Integer.toString(session.safeReveals())),
            placeholder("multiplier", Money.formatMultiplier(session.currentMultiplier(payouts)))
        );
    }

    private static org.bukkit.inventory.ItemStack betInfo(
        final MessageService messages,
        final GuiConfig guiConfig,
        final EconomyBridge economy,
        final MinesSession session
    ) {
        return item(
            messages,
            guiConfig.betInfo(),
            placeholder("wager", economy.format(session.betMinor())),
            placeholder("board_size", Integer.toString(PayoutTable.TOTAL_TILES)),
            placeholder("player", session.playerName())
        );
    }

    private static org.bukkit.inventory.ItemStack oddsInfo(
        final MessageService messages,
        final GuiConfig guiConfig,
        final MinesSession session,
        final PayoutTable payouts
    ) {
        return item(
            messages,
            guiConfig.oddsInfoActive(),
            placeholder("multiplier", Money.formatMultiplier(session.currentMultiplier(payouts))),
            placeholder("chance", Money.formatPercent(session.nextSafeChancePercent()))
        );
    }

    private static org.bukkit.inventory.ItemStack oddsLossInfo(final MessageService messages, final GuiConfig guiConfig) {
        return item(messages, guiConfig.oddsInfoLoss());
    }

    private static org.bukkit.inventory.ItemStack oddsCashoutInfo(final MessageService messages, final GuiConfig guiConfig) {
        return item(messages, guiConfig.oddsInfoCashout());
    }

    private static org.bukkit.inventory.ItemStack cashoutCollectedInfo(
        final MessageService messages,
        final GuiConfig guiConfig,
        final long payoutMinor,
        final EconomyBridge economy
    ) {
        return item(messages, guiConfig.cashoutDisabled(), placeholder("payout", economy.format(payoutMinor)));
    }

    private static org.bukkit.inventory.ItemStack helpInfo(final MessageService messages, final GuiConfig guiConfig) {
        return item(messages, guiConfig.help());
    }

    public static void updateStatsButton(
        final MinesSession session,
        final MessageService messages,
        final GuiConfig guiConfig,
        final EconomyBridge economy
    ) {
        session.inventory().setItem(guiConfig.layout().statsSlot(), session.statsSnapshot() == null
            ? loadingStatsButton(messages, guiConfig)
            : statsButton(messages, guiConfig, economy, session.statsSnapshot()));
    }

    private static org.bukkit.inventory.ItemStack loadingStatsButton(final MessageService messages, final GuiConfig guiConfig) {
        return item(messages, guiConfig.statsLoading());
    }

    private static org.bukkit.inventory.ItemStack statsButton(
        final MessageService messages,
        final GuiConfig guiConfig,
        final EconomyBridge economy,
        final PlayerStatsSnapshot stats
    ) {
        return item(
            messages,
            guiConfig.statsLoaded(),
            placeholder("games", Long.toString(stats.gamesPlayed())),
            placeholder("wins", Long.toString(stats.wins())),
            placeholder("losses", Long.toString(stats.losses())),
            placeholder("win_rate", Money.formatPercent(stats.winRate())),
            placeholder("wagered", economy.format(stats.totalWageredMinor())),
            placeholder("paid", economy.format(stats.totalPaidMinor())),
            placeholder("profit", economy.format(stats.netProfitMinor())),
            placeholder("best_cashout", economy.format(stats.bestCashoutMinor())),
            placeholder("biggest_bet", economy.format(stats.biggestBetMinor())),
            placeholder("tiles", Long.toString(stats.tilesCleared()))
        );
    }

    private static ItemStack item(
        final MessageService messages,
        final GuiItemTemplate template,
        final TagResolver... resolvers
    ) {
        final Component title = messages.renderRaw(template.title(), resolvers);
        final List<Component> lore = template.lore().stream()
            .map(line -> messages.renderRaw(line, resolvers))
            .toList();
        return ItemFactory.create(template.material(), title, lore);
    }

    private static TagResolver placeholder(final String name, final String value) {
        return Placeholder.unparsed(name, value);
    }
}
