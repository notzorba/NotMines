package io.github.notzorba.notmines.gui;

import io.github.notzorba.notmines.game.PayoutTable;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

public record GuiConfig(
    String title,
    GuiLayout layout,
    GuiSoundConfig sounds,
    GuiItemTemplate filler,
    GuiItemTemplate hiddenTile,
    GuiItemTemplate safeTile,
    GuiItemTemplate unrevealedSafeTile,
    GuiItemTemplate cashedOutTile,
    GuiItemTemplate mineTile,
    GuiItemTemplate cashoutActive,
    GuiItemTemplate cashoutDisabled,
    GuiItemTemplate cashoutCollected,
    GuiItemTemplate boardInfoActive,
    GuiItemTemplate boardInfoLoss,
    GuiItemTemplate boardInfoCashout,
    GuiItemTemplate betInfo,
    GuiItemTemplate oddsInfoActive,
    GuiItemTemplate oddsInfoLoss,
    GuiItemTemplate oddsInfoCashout,
    GuiItemTemplate help,
    GuiItemTemplate statsLoading,
    GuiItemTemplate statsLoaded,
    LeaderboardGuiConfig leaderboard
) {
    public static GuiConfig load(final File file) {
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        final int inventorySize = validateInventorySize(config.getInt("layout.inventory-size", 54), "GUI");
        final int[] boardSlots = loadBoardSlots(config, inventorySize);

        final GuiLayout layout = new GuiLayout(
            inventorySize,
            boardSlots,
            loadSlot(config, "layout.info-slot", inventorySize, 45),
            loadSlot(config, "layout.stats-slot", inventorySize, 46),
            loadSlot(config, "layout.bet-slot", inventorySize, 47),
            loadSlot(config, "layout.cashout-slot", inventorySize, 49),
            loadSlot(config, "layout.odds-slot", inventorySize, 51),
            loadSlot(config, "layout.help-slot", inventorySize, 53)
        );
        validateLayout(layout);

        return new GuiConfig(
            config.getString("title", "<dark_gray>Mines | GUI</dark_gray>"),
            layout,
            GuiSoundConfig.load(config),
            loadItem(config, "items.filler", Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>"),
            loadItem(config, "items.hidden-tile", Material.GRAY_STAINED_GLASS_PANE, "<#8F9FB3>Hidden Tile</#8F9FB3>"),
            loadItem(config, "items.safe-tile", Material.DIAMOND, "<#53B38C>Safe</#53B38C>"),
            loadItem(config, "items.unrevealed-safe-tile", Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<#A5AFBC>Safe Tile</#A5AFBC>"),
            loadItem(config, "items.cashed-out-tile", Material.YELLOW_STAINED_GLASS_PANE, "<#D5B25C>Locked In</#D5B25C>"),
            loadItem(config, "items.mine-tile", Material.TNT, "<#D96B6B>Mine</#D96B6B>"),
            loadItem(config, "items.cashout-active", Material.EMERALD, "<#53B38C>Cash Out</#53B38C>"),
            loadItem(config, "items.cashout-disabled", Material.BARRIER, "<#C97A7A>Round Ended</#C97A7A>"),
            loadItem(config, "items.cashout-collected", Material.EMERALD, "<#53B38C>Collected</#53B38C>"),
            loadItem(config, "items.board-info-active", Material.COMPASS, "<#7C92B8>Board Status</#7C92B8>"),
            loadItem(config, "items.board-info-loss", Material.COMPASS, "<#D96B6B>Mine Hit</#D96B6B>"),
            loadItem(config, "items.board-info-cashout", Material.COMPASS, "<#53B38C>Paid Out</#53B38C>"),
            loadItem(config, "items.bet-info", Material.GOLD_INGOT, "<#D5B25C>Bet Info</#D5B25C>"),
            loadItem(config, "items.odds-info-active", Material.CLOCK, "<#8E88D6>Odds</#8E88D6>"),
            loadItem(config, "items.odds-info-loss", Material.PAPER, "<#A5AFBC>Payout</#A5AFBC>"),
            loadItem(config, "items.odds-info-cashout", Material.PAPER, "<#A5AFBC>House Edge</#A5AFBC>"),
            loadItem(config, "items.help", Material.BOOK, "<#C7A768>Quick Help</#C7A768>"),
            loadItem(config, "items.stats-loading", Material.NETHER_STAR, "<#7588A6>Your Stats</#7588A6>"),
            loadItem(config, "items.stats-loaded", Material.NETHER_STAR, "<#7588A6>Your Stats</#7588A6>"),
            loadLeaderboard(config)
        );
    }

    private static LeaderboardGuiConfig loadLeaderboard(final YamlConfiguration config) {
        final int inventorySize = validateInventorySize(config.getInt("leaderboard.layout.inventory-size", 54), "Leaderboard GUI");
        final int[] entrySlots = loadSlots(
            config,
            "leaderboard.layout.entry-slots",
            inventorySize,
            defaultLeaderboardEntrySlots(),
            "leaderboard entry"
        );

        final LeaderboardLayout leaderboardLayout = new LeaderboardLayout(
            inventorySize,
            entrySlots,
            loadSlot(config, "leaderboard.layout.summary-slot", inventorySize, 4),
            loadSlot(config, "leaderboard.layout.filter-slot", inventorySize, 49),
            loadSlot(config, "leaderboard.layout.previous-page-slot", inventorySize, 47),
            loadSlot(config, "leaderboard.layout.next-page-slot", inventorySize, 51),
            loadSlot(config, "leaderboard.layout.page-info-slot", inventorySize, 53),
            loadSlot(config, "leaderboard.layout.close-slot", inventorySize, 45)
        );
        validateLayout(leaderboardLayout);

        return new LeaderboardGuiConfig(
            config.getString("leaderboard.title", "<dark_gray>Mines | Leaderboard</dark_gray>"),
            leaderboardLayout,
            loadItem(config, "leaderboard.items.filler", Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>"),
            loadItem(config, "leaderboard.items.summary", Material.COMPARATOR, "<#F4C95D>Leaderboard Summary</#F4C95D>"),
            loadItem(config, "leaderboard.items.filter", Material.HOPPER, "<#7BAAF7>Sort Filter</#7BAAF7>"),
            loadItem(config, "leaderboard.items.loading", Material.CLOCK, "<#7BAAF7>Loading Leaderboard</#7BAAF7>"),
            loadItem(config, "leaderboard.items.empty", Material.PAPER, "<#D5B25C>No Ranked Players Yet</#D5B25C>"),
            loadItem(config, "leaderboard.items.entry", Material.PLAYER_HEAD, "<#F5F7FA>#<rank> <player></#F5F7FA>"),
            loadItem(config, "leaderboard.items.focused-entry", Material.PLAYER_HEAD, "<#F5F7FA>#<rank> <player></#F5F7FA>"),
            loadItem(config, "leaderboard.items.previous-page", Material.ARROW, "<#A5AFBC>Previous Page</#A5AFBC>"),
            loadItem(config, "leaderboard.items.next-page", Material.ARROW, "<#A5AFBC>Next Page</#A5AFBC>"),
            loadItem(config, "leaderboard.items.page-info", Material.BOOK, "<#7C92B8>Page <page>/<pages></#7C92B8>"),
            loadItem(config, "leaderboard.items.close", Material.BARRIER, "<#D96B6B>Close</#D96B6B>")
        );
    }

    private static int validateInventorySize(final int inventorySize, final String label) {
        if (inventorySize < 9 || inventorySize > 54 || inventorySize % 9 != 0) {
            throw new IllegalArgumentException(label + " inventory size must be a multiple of 9 between 9 and 54.");
        }
        return inventorySize;
    }

    private static int[] loadBoardSlots(final YamlConfiguration config, final int inventorySize) {
        final List<Integer> values = config.getIntegerList("layout.board-slots");
        if (values.size() != PayoutTable.TOTAL_TILES) {
            throw new IllegalArgumentException("GUI board-slots must contain exactly 25 slots.");
        }

        final int[] slots = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            final int slot = values.get(index);
            if (slot < 0 || slot >= inventorySize) {
                throw new IllegalArgumentException("Each GUI board slot must be between 0 and " + (inventorySize - 1) + ".");
            }
            slots[index] = slot;
        }

        final Set<Integer> uniqueSlots = new HashSet<>();
        for (int slot : slots) {
            if (!uniqueSlots.add(slot)) {
                throw new IllegalArgumentException("GUI board-slots may not contain duplicates.");
            }
        }

        return slots;
    }

    private static int[] loadSlots(
        final YamlConfiguration config,
        final String path,
        final int inventorySize,
        final int[] fallback,
        final String label
    ) {
        final List<Integer> configuredValues = config.getIntegerList(path);
        final List<Integer> values = configuredValues.isEmpty()
            ? java.util.Arrays.stream(fallback).boxed().toList()
            : configuredValues;
        if (values.isEmpty()) {
            throw new IllegalArgumentException(path + " must contain at least 1 " + label + " slot.");
        }

        final int[] slots = new int[values.size()];
        final Set<Integer> uniqueSlots = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            final int slot = values.get(index);
            if (slot < 0 || slot >= inventorySize) {
                throw new IllegalArgumentException(path + " contains a slot outside the inventory bounds.");
            }
            if (!uniqueSlots.add(slot)) {
                throw new IllegalArgumentException(path + " may not contain duplicate slots.");
            }
            slots[index] = slot;
        }

        return slots;
    }

    private static int[] defaultLeaderboardEntrySlots() {
        return new int[] {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };
    }

    private static int loadSlot(
        final YamlConfiguration config,
        final String path,
        final int inventorySize,
        final int fallback
    ) {
        final int slot = config.getInt(path, fallback);
        if (slot < 0 || slot >= inventorySize) {
            throw new IllegalArgumentException(path + " must be between 0 and " + (inventorySize - 1) + ".");
        }
        return slot;
    }

    private static void validateLayout(final GuiLayout layout) {
        final Set<Integer> occupied = new HashSet<>();
        for (int slot : layout.boardSlots()) {
            occupied.add(slot);
        }

        validateUtilitySlot(occupied, layout.infoSlot(), "layout.info-slot");
        validateUtilitySlot(occupied, layout.statsSlot(), "layout.stats-slot");
        validateUtilitySlot(occupied, layout.betSlot(), "layout.bet-slot");
        validateUtilitySlot(occupied, layout.cashoutSlot(), "layout.cashout-slot");
        validateUtilitySlot(occupied, layout.oddsSlot(), "layout.odds-slot");
        validateUtilitySlot(occupied, layout.helpSlot(), "layout.help-slot");
    }

    private static void validateLayout(final LeaderboardLayout layout) {
        final Set<Integer> occupied = new HashSet<>();
        for (int slot : layout.entrySlots()) {
            occupied.add(slot);
        }

        validateUtilitySlot(occupied, layout.summarySlot(), "leaderboard.layout.summary-slot");
        validateUtilitySlot(occupied, layout.filterSlot(), "leaderboard.layout.filter-slot");
        validateUtilitySlot(occupied, layout.previousPageSlot(), "leaderboard.layout.previous-page-slot");
        validateUtilitySlot(occupied, layout.nextPageSlot(), "leaderboard.layout.next-page-slot");
        validateUtilitySlot(occupied, layout.pageInfoSlot(), "leaderboard.layout.page-info-slot");
        validateUtilitySlot(occupied, layout.closeSlot(), "leaderboard.layout.close-slot");
    }

    private static void validateUtilitySlot(final Set<Integer> occupied, final int slot, final String path) {
        if (!occupied.add(slot)) {
            throw new IllegalArgumentException(path + " overlaps another configured GUI slot.");
        }
    }

    private static GuiItemTemplate loadItem(
        final YamlConfiguration config,
        final String path,
        final Material fallbackMaterial,
        final String fallbackTitle
    ) {
        final Material material = resolveMaterial(config.getString(path + ".material"), fallbackMaterial);
        final String title = config.getString(path + ".title", fallbackTitle);
        final List<String> lore = List.copyOf(config.getStringList(path + ".lore"));
        return new GuiItemTemplate(material, title, lore);
    }

    private static Material resolveMaterial(final String rawMaterial, final Material fallbackMaterial) {
        if (rawMaterial == null || rawMaterial.isBlank()) {
            return fallbackMaterial;
        }

        final Material material = Material.matchMaterial(rawMaterial.toUpperCase(Locale.ROOT));
        return material == null ? fallbackMaterial : material;
    }
}
