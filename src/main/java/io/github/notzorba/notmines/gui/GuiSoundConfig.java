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
    List<GuiSoundEffect> boardCleared,
    List<GuiSoundEffect> leaderboardOpen,
    List<GuiSoundEffect> leaderboardPage,
    List<GuiSoundEffect> leaderboardFilter,
    List<GuiSoundEffect> menuClose
) {
    public static GuiSoundConfig load(final YamlConfiguration config) {
        return new GuiSoundConfig(
            config.getBoolean("sounds.enabled", true),
            loadSequence(config, "sounds.board-open", defaultBoardOpen()),
            loadSequence(config, "sounds.safe-pick", defaultSafePick()),
            loadSequence(config, "sounds.mine-hit", defaultMineHit()),
            loadSequence(config, "sounds.cashout", defaultCashout()),
            loadSequence(config, "sounds.board-cleared", defaultBoardCleared()),
            loadSequence(config, "sounds.leaderboard-open", defaultLeaderboardOpen()),
            loadSequence(config, "sounds.leaderboard-page", defaultLeaderboardPage()),
            loadSequence(config, "sounds.leaderboard-filter", defaultLeaderboardFilter()),
            loadSequence(config, "sounds.menu-close", defaultMenuClose())
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
            new GuiSoundEffect("minecraft:ui.button.click", 0.65F, 1.10F, 0),
            new GuiSoundEffect("minecraft:item.book.page_turn", 0.50F, 1.25F, 0)
        );
    }

    private static List<GuiSoundEffect> defaultSafePick() {
        return List.of(
            new GuiSoundEffect("minecraft:block.note_block.chime", 0.60F, 1.30F, 0),
            new GuiSoundEffect("minecraft:entity.experience_orb.pickup", 0.40F, 1.70F, 1)
        );
    }

    private static List<GuiSoundEffect> defaultMineHit() {
        return List.of(
            new GuiSoundEffect("minecraft:entity.generic.explode", 0.95F, 0.75F, 0),
            new GuiSoundEffect("minecraft:block.glass.break", 0.60F, 0.60F, 1)
        );
    }

    private static List<GuiSoundEffect> defaultCashout() {
        return List.of(
            new GuiSoundEffect("minecraft:entity.player.levelup", 0.75F, 1.20F, 0),
            new GuiSoundEffect("minecraft:block.note_block.pling", 0.55F, 1.55F, 1)
        );
    }

    private static List<GuiSoundEffect> defaultBoardCleared() {
        return List.of(
            new GuiSoundEffect("minecraft:ui.toast.challenge_complete", 0.90F, 1.05F, 0),
            new GuiSoundEffect("minecraft:entity.player.levelup", 0.80F, 1.45F, 2)
        );
    }

    private static List<GuiSoundEffect> defaultLeaderboardOpen() {
        return List.of(
            new GuiSoundEffect("minecraft:item.book.page_turn", 0.55F, 1.15F, 0),
            new GuiSoundEffect("minecraft:block.note_block.chime", 0.35F, 1.45F, 1)
        );
    }

    private static List<GuiSoundEffect> defaultLeaderboardPage() {
        return List.of(new GuiSoundEffect("minecraft:item.book.page_turn", 0.60F, 1.20F, 0));
    }

    private static List<GuiSoundEffect> defaultLeaderboardFilter() {
        return List.of(
            new GuiSoundEffect("minecraft:ui.button.click", 0.55F, 1.20F, 0),
            new GuiSoundEffect("minecraft:block.note_block.hat", 0.35F, 1.65F, 1)
        );
    }

    private static List<GuiSoundEffect> defaultMenuClose() {
        return List.of(new GuiSoundEffect("minecraft:ui.button.click", 0.45F, 0.90F, 0));
    }
}
