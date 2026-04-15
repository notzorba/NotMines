package io.github.notzorba.notmines.gui;

import java.util.Locale;
import java.util.Map;
import org.bukkit.Sound;

public record GuiSoundEffect(
    Sound sound,
    float volume,
    float pitch,
    int delayTicks
) {
    public static GuiSoundEffect fromMap(final Map<?, ?> values, final String path) {
        final String soundName = stringValue(values.get("sound"), path + ".sound");
        final Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException(path + ".sound uses an unknown sound '" + soundName + "'.");
        }

        final float volume = clamp(numberValue(values.get("volume"), 0.70F, path + ".volume"), 0.0F, 4.0F);
        final float pitch = clamp(numberValue(values.get("pitch"), 1.0F, path + ".pitch"), 0.5F, 2.0F);
        final int delayTicks = Math.max(0, Math.round(numberValue(values.get("delay-ticks"), 0.0F, path + ".delay-ticks")));
        return new GuiSoundEffect(sound, volume, pitch, delayTicks);
    }

    private static String stringValue(final Object value, final String path) {
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }

        throw new IllegalArgumentException(path + " must be a non-empty sound name.");
    }

    private static float numberValue(final Object value, final float fallback, final String path) {
        if (value == null) {
            return fallback;
        }

        if (value instanceof Number number) {
            return number.floatValue();
        }

        if (value instanceof String text) {
            try {
                return Float.parseFloat(text.trim());
            } catch (final NumberFormatException exception) {
                throw new IllegalArgumentException(path + " must be a number.");
            }
        }

        throw new IllegalArgumentException(path + " must be a number.");
    }

    private static float clamp(final float value, final float min, final float max) {
        return Math.max(min, Math.min(max, value));
    }
}
