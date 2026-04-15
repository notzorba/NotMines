package io.github.notzorba.notmines.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public record GuiSoundConfig(
    boolean enabled,
    List<GuiSoundEffect> boardOpen,
    List<GuiSoundEffect> safePick,
    List<GuiSoundEffect> mineHit,
    List<GuiSoundEffect> cashout,
    List<GuiSoundEffect> boardCleared
) {
    public static GuiSoundConfig load(final YamlConfiguration config) {
        return new GuiSoundConfig(
            config.getBoolean("sounds.enabled", true),
            loadSequence(config, "sounds.board-open", defaultBoardOpen()),
            loadSequence(config, "sounds.safe-pick", defaultSafePick()),
            loadSequence(config, "sounds.mine-hit", defaultMineHit()),
            loadSequence(config, "sounds.cashout", defaultCashout()),
            loadSequence(config, "sounds.board-cleared", defaultBoardCleared())
        );
    }

    private static List<GuiSoundEffect> loadSequence(
        final YamlConfiguration config,
        final String path,
        final List<GuiSoundEffect> fallback
    ) {
        if (!config.contains(path)) {
            return fallback;
        }

        final List<Map<?, ?>> rawEntries = new ArrayList<>(config.getMapList(path));
        if (rawEntries.isEmpty()) {
            final ConfigurationSection section = config.getConfigurationSection(path);
            if (section != null) {
                rawEntries.add(section.getValues(false));
            }
        }

        final List<GuiSoundEffect> parsed = new ArrayList<>(rawEntries.size());
        for (int index = 0; index < rawEntries.size(); index++) {
            parsed.add(GuiSoundEffect.fromMap(rawEntries.get(index), path + "[" + index + "]"));
        }

        return List.copyOf(parsed);
    }

    private static List<GuiSoundEffect> defaultBoardOpen() {
        return List.of(
            new GuiSoundEffect(org.bukkit.Sound.UI_BUTTON_CLICK, 0.65F, 1.10F, 0),
            new GuiSoundEffect(org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.50F, 1.25F, 0)
        );
    }

    private static List<GuiSoundEffect> defaultSafePick() {
        return List.of(
            new GuiSoundEffect(org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.60F, 1.30F, 0),
            new GuiSoundEffect(org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.40F, 1.70F, 1)
        );
    }

    private static List<GuiSoundEffect> defaultMineHit() {
        return List.of(
            new GuiSoundEffect(org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.95F, 0.75F, 0),
            new GuiSoundEffect(org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.60F, 0.60F, 1)
        );
    }

    private static List<GuiSoundEffect> defaultCashout() {
        return List.of(
            new GuiSoundEffect(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.75F, 1.20F, 0),
            new GuiSoundEffect(org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.55F, 1.55F, 1)
        );
    }

    private static List<GuiSoundEffect> defaultBoardCleared() {
        return List.of(
            new GuiSoundEffect(org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.90F, 1.05F, 0),
            new GuiSoundEffect(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.80F, 1.45F, 2)
        );
    }
}
