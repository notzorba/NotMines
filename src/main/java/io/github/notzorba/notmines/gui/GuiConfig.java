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
    GuiItemTemplate statsLoaded
) {
    public static GuiConfig load(final File file) {
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        final int inventorySize = validateInventorySize(config.getInt("layout.inventory-size", 54));
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
            loadItem(config, "items.stats-loaded", Material.NETHER_STAR, "<#7588A6>Your Stats</#7588A6>")
        );
    }

    private static int validateInventorySize(final int inventorySize) {
        if (inventorySize < 9 || inventorySize > 54 || inventorySize % 9 != 0) {
            throw new IllegalArgumentException("GUI inventory size must be a multiple of 9 between 9 and 54.");
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
