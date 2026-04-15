package io.github.notzorba.notmines.gui;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class GuiSoundPlayer {
    private GuiSoundPlayer() {
    }

    public static void play(final Plugin plugin, final Player player, final List<GuiSoundEffect> effects) {
        play(plugin, player, effects, 0.0F);
    }

    public static void play(
        final Plugin plugin,
        final Player player,
        final List<GuiSoundEffect> effects,
        final float pitchBoost
    ) {
        for (GuiSoundEffect effect : effects) {
            final Runnable playback = () -> {
                if (!player.isOnline()) {
                    return;
                }

                final float adjustedPitch = clamp(effect.pitch() + pitchBoost, 0.5F, 2.0F);
                player.playSound(player.getLocation(), effect.sound(), effect.volume(), adjustedPitch);
            };

            if (effect.delayTicks() <= 0) {
                playback.run();
                continue;
            }

            plugin.getServer().getScheduler().runTaskLater(plugin, playback, effect.delayTicks());
        }
    }

    private static float clamp(final float value, final float min, final float max) {
        return Math.max(min, Math.min(max, value));
    }
}
