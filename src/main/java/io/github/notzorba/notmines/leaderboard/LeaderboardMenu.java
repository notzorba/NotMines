package io.github.notzorba.notmines.leaderboard;

import io.github.notzorba.notmines.economy.EconomyBridge;
import io.github.notzorba.notmines.gui.GuiItemTemplate;
import io.github.notzorba.notmines.gui.LeaderboardGuiConfig;
import io.github.notzorba.notmines.stats.LeaderboardPage;
import io.github.notzorba.notmines.stats.LeaderboardStat;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class LeaderboardMenu {
    private LeaderboardMenu() {
    }

    public static Inventory createInventory(
        final LeaderboardInventoryHolder holder,
        final MessageService messages,
        final LeaderboardGuiConfig guiConfig
    ) {
        final Inventory inventory = Bukkit.createInventory(holder, guiConfig.layout().inventorySize(), messages.renderRaw(guiConfig.title()));
        holder.setInventory(inventory);
        renderLoading(holder, messages, guiConfig);
        return inventory;
    }

    public static void renderLoading(
        final LeaderboardInventoryHolder holder,
        final MessageService messages,
        final LeaderboardGuiConfig guiConfig
    ) {
        final Inventory inventory = holder.getInventory();
        fillBackground(inventory, messages, guiConfig);

        final int[] entrySlots = guiConfig.layout().entrySlots();
        if (entrySlots.length > 0) {
            inventory.setItem(entrySlots[entrySlots.length / 2], item(messages, guiConfig.loading(), placeholder("stat", holder.stat().displayName())));
        }

        inventory.setItem(guiConfig.layout().summarySlot(), loadingSummaryItem(messages, holder));
        inventory.setItem(guiConfig.layout().filterSlot(), filterItem(messages, guiConfig, holder.stat()));
        inventory.setItem(guiConfig.layout().previousPageSlot(), navItem(messages, guiConfig.previousPage(), 1, 1));
        inventory.setItem(guiConfig.layout().nextPageSlot(), navItem(messages, guiConfig.nextPage(), 1, 1));
        inventory.setItem(guiConfig.layout().pageInfoSlot(), pageInfo(messages, guiConfig, 1, 1, 0));
        inventory.setItem(guiConfig.layout().closeSlot(), item(messages, guiConfig.close()));
    }

    public static void renderPage(
        final LeaderboardInventoryHolder holder,
        final LeaderboardPage page,
        final MessageService messages,
        final LeaderboardGuiConfig guiConfig,
        final EconomyBridge economy
    ) {
        final Inventory inventory = holder.getInventory();
        fillBackground(inventory, messages, guiConfig);

        final int fromRank = page.totalEntries() == 0 ? 0 : (page.page() * guiConfig.layout().entrySlots().length) + 1;
        final int toRank = page.totalEntries() == 0 ? 0 : fromRank + page.entries().size() - 1;

        inventory.setItem(guiConfig.layout().summarySlot(), summaryItem(messages, holder, page, economy));
        inventory.setItem(guiConfig.layout().filterSlot(), filterItem(messages, guiConfig, page.stat()));
        inventory.setItem(guiConfig.layout().previousPageSlot(), navItem(messages, guiConfig.previousPage(), page.page() + 1, page.totalPages()));
        inventory.setItem(guiConfig.layout().nextPageSlot(), navItem(messages, guiConfig.nextPage(), page.page() + 1, page.totalPages()));
        inventory.setItem(guiConfig.layout().pageInfoSlot(), pageInfo(messages, guiConfig, page.page() + 1, page.totalPages(), page.totalEntries()));
        inventory.setItem(guiConfig.layout().closeSlot(), item(messages, guiConfig.close()));

        if (page.entries().isEmpty()) {
            final int[] entrySlots = guiConfig.layout().entrySlots();
            inventory.setItem(entrySlots[entrySlots.length / 2], item(messages, guiConfig.empty(), placeholder("stat", page.stat().displayName())));
            return;
        }

        final int[] entrySlots = guiConfig.layout().entrySlots();
        for (int index = 0; index < page.entries().size() && index < entrySlots.length; index++) {
            final PlayerStatsSnapshot entry = page.entries().get(index);
            final int rank = (page.page() * entrySlots.length) + index + 1;
            inventory.setItem(entrySlots[index], entryItem(messages, guiConfig, economy, page.stat(), entry, rank));
        }
    }

    private static void fillBackground(
        final Inventory inventory,
        final MessageService messages,
        final LeaderboardGuiConfig guiConfig
    ) {
        final ItemStack filler = item(messages, guiConfig.filler());
        for (int slot = 0; slot < guiConfig.layout().inventorySize(); slot++) {
            inventory.setItem(slot, filler.clone());
        }
    }

    private static ItemStack loadingSummaryItem(
        final MessageService messages,
        final LeaderboardInventoryHolder holder
    ) {
        final Component title = messages.renderRaw("<#F4C95D><bold><player></bold></#F4C95D>", placeholder("player", holder.viewerName()));
        final List<Component> lore = List.of(
            messages.renderRaw("<gray>Loading your placement...</gray>"),
            messages.renderRaw("<dark_gray>The selected filter will update your rank and value.</dark_gray>")
        );
        return ItemFactory.createPlayerHead(
            title,
            lore,
            holder.viewerId(),
            holder.viewerName(),
            holder.viewerSkinTexture(),
            holder.viewerSkinTextureSignature()
        );
    }

    private static ItemStack summaryItem(
        final MessageService messages,
        final LeaderboardInventoryHolder holder,
        final LeaderboardPage page,
        final EconomyBridge economy
    ) {
        final String titlePattern = "<#F4C95D><bold><player></bold></#F4C95D>";
        final Component title = messages.renderRaw(titlePattern, placeholder("player", holder.viewerName()));

        final PlayerStatsSnapshot viewerSnapshot = page.viewerSnapshot();
        final boolean ranked = viewerSnapshot != null && viewerSnapshot.hasActivity() && page.viewerRank() > 0;
        final List<Component> lore = ranked
            ? List.of(
                messages.renderRaw("<gray>Current filter:</gray> <white><stat></white>", placeholder("stat", page.stat().displayName())),
                messages.renderRaw(
                    "<gray>Your rank:</gray> <white>#<rank></white> <dark_gray>/</dark_gray> <white><players></white>",
                    placeholder("rank", Integer.toString(page.viewerRank())),
                    placeholder("players", Integer.toString(page.totalEntries()))
                ),
                messages.renderRaw(
                    "<gray><stat>:</gray> <white><value></white>",
                    placeholder("stat", page.stat().displayName()),
                    placeholder("value", formatValue(page.stat(), viewerSnapshot, economy))
                ),
                messages.renderRaw("<dark_gray>This updates with the active hopper filter.</dark_gray>")
            )
            : List.of(
                messages.renderRaw("<gray>Current filter:</gray> <white><stat></white>", placeholder("stat", page.stat().displayName())),
                messages.renderRaw("<gray>Your rank:</gray> <white>Unranked</white>"),
                messages.renderRaw("<gray><stat>:</gray> <white>No data yet</white>", placeholder("stat", page.stat().displayName())),
                messages.renderRaw("<dark_gray>Play a few rounds to appear on the board.</dark_gray>")
            );

        return ItemFactory.createPlayerHead(
            title,
            lore,
            holder.viewerId(),
            holder.viewerName(),
            holder.viewerSkinTexture(),
            holder.viewerSkinTextureSignature()
        );
    }

    private static ItemStack filterItem(
        final MessageService messages,
        final LeaderboardGuiConfig guiConfig,
        final LeaderboardStat stat
    ) {
        final Component title = messages.renderRaw(
            guiConfig.filter().title(),
            placeholder("stat", stat.displayName()),
            placeholder("next_stat", stat.next().displayName())
        );
        final List<Component> lore = new ArrayList<>();
        for (String line : guiConfig.filter().lore()) {
            lore.add(messages.renderRaw(
                line,
                placeholder("stat", stat.displayName()),
                placeholder("next_stat", stat.next().displayName())
            ));
        }

        lore.add(Component.empty());
        for (LeaderboardStat option : LeaderboardStat.orderedValues()) {
            lore.add(messages.renderRaw(filterListLine(option, stat), placeholder("filter", option.displayName())));
        }

        return ItemFactory.create(guiConfig.filter().material(), title, lore);
    }

    private static ItemStack navItem(
        final MessageService messages,
        final GuiItemTemplate template,
        final int page,
        final int totalPages
    ) {
        return item(messages, template, placeholder("page", Integer.toString(page)), placeholder("pages", Integer.toString(totalPages)));
    }

    private static ItemStack pageInfo(
        final MessageService messages,
        final LeaderboardGuiConfig guiConfig,
        final int page,
        final int totalPages,
        final int entries
    ) {
        return item(
            messages,
            guiConfig.pageInfo(),
            placeholder("page", Integer.toString(page)),
            placeholder("pages", Integer.toString(totalPages)),
            placeholder("entries", Integer.toString(entries))
        );
    }

    private static ItemStack entryItem(
        final MessageService messages,
        final LeaderboardGuiConfig guiConfig,
        final EconomyBridge economy,
        final LeaderboardStat stat,
        final PlayerStatsSnapshot entry,
        final int rank
    ) {
        final GuiItemTemplate template = guiConfig.focusedEntry();
        final Component title = messages.renderRaw(
            template.title(),
            placeholder("rank", Integer.toString(rank)),
            placeholder("player", entry.lastKnownName())
        );
        final List<Component> lore = template.lore().stream()
            .map(line -> messages.renderRaw(
                line,
                placeholder("rank", Integer.toString(rank)),
                placeholder("player", entry.lastKnownName()),
                placeholder("stat", stat.displayName()),
                placeholder("value", formatValue(stat, entry, economy)),
                placeholder("games", Long.toString(entry.gamesPlayed())),
                placeholder("wins", Long.toString(entry.wins())),
                placeholder("win_rate", Money.formatPercent(entry.winRate())),
                placeholder("wagered", economy.format(entry.totalWageredMinor())),
                placeholder("paid", economy.format(entry.totalPaidMinor())),
                placeholder("profit", economy.format(entry.netProfitMinor())),
                placeholder("best_cashout", economy.format(entry.bestCashoutMinor())),
                placeholder("biggest_bet", economy.format(entry.biggestBetMinor())),
                placeholder("tiles", Long.toString(entry.tilesCleared()))
            ))
            .toList();
        return ItemFactory.createPlayerHead(
            title,
            lore,
            entry.uuid(),
            entry.lastKnownName(),
            entry.skinTexture(),
            entry.skinTextureSignature()
        );
    }

    private static String formatValue(
        final LeaderboardStat stat,
        final PlayerStatsSnapshot entry,
        final EconomyBridge economy
    ) {
        return switch (stat) {
            case NET_PROFIT -> economy.format(entry.netProfitMinor());
            case TOTAL_WAGERED -> economy.format(entry.totalWageredMinor());
            case TOTAL_PAID -> economy.format(entry.totalPaidMinor());
            case BEST_CASHOUT -> economy.format(entry.bestCashoutMinor());
            case BIGGEST_BET -> economy.format(entry.biggestBetMinor());
            case GAMES_PLAYED -> Long.toString(entry.gamesPlayed());
            case WINS -> Long.toString(entry.wins());
            case WIN_RATE -> Money.formatPercent(entry.winRate()) + "%";
            case TILES_CLEARED -> Long.toString(entry.tilesCleared());
        };
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

    private static String filterListLine(final LeaderboardStat option, final LeaderboardStat selected) {
        if (option == selected) {
            return "<#7BAAF7>▶ <filter></#7BAAF7>";
        }
        return "<gray>• <filter></gray>";
    }
}
