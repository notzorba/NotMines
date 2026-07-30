package io.github.notzorba.notmines.gui;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public record GuiSoundEffect(
    String sound,
    float volume,
    float pitch,
    int delayTicks
) {
    private static final Pattern SOUND_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public static GuiSoundEffect fromMap(final Map<?, ?> values, final String path) {
        final String soundName = stringValue(values.get("sound"), path + ".sound");
        final String sound = normalizeSoundKey(soundName, path + ".sound");

        final float volume = clamp(numberValue(values.get("volume"), 0.70F, path + ".volume"), 0.0F, 4.0F);
        final float pitch = clamp(numberValue(values.get("pitch"), 1.0F, path + ".pitch"), 0.5F, 2.0F);
        final int delayTicks = Math.max(0, Math.round(numberValue(values.get("delay-ticks"), 0.0F, path + ".delay-ticks")));
        return new GuiSoundEffect(sound, volume, pitch, delayTicks);
    }

    private static String normalizeSoundKey(final String soundName, final String path) {
        final String normalized = soundName.contains(":")
            ? soundName.toLowerCase(Locale.ROOT)
            : "minecraft:" + soundName.toLowerCase(Locale.ROOT).replace('_', '.');
        if (!SOUND_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException(path + " uses an invalid sound name '" + soundName + "'.");
        }
        return normalized;
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
