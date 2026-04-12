package io.github.notzorba.notmines.config;

import io.github.notzorba.notmines.util.Money;
import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(
    long minBetMinor,
    long maxBetMinor,
    int minMines,
    int maxMines,
    double houseEdge,
    int saveIntervalSeconds,
    long shutdownWaitMillis,
    int endScreenCloseDelayTicks
) {
    public static PluginSettings load(final FileConfiguration config, final int currencyScale) {
        final long minBetMinor = Money.parseMinor(config.getString("limits.min-bet", "100"), currencyScale);
        final long maxBetMinor = Money.parseMinor(config.getString("limits.max-bet", "1000000"), currencyScale);
        final int minMines = Math.max(1, config.getInt("limits.min-mines", 1));
        final int maxMines = Math.min(24, Math.max(minMines, config.getInt("limits.max-mines", 24)));
        final double houseEdge = clamp(config.getDouble("gameplay.house-edge", 0.03D), 0.0D, 0.20D);
        final int saveIntervalSeconds = Math.max(5, config.getInt("stats.save-interval-seconds", 15));
        final long shutdownWaitMillis = Math.max(250L, config.getLong("stats.shutdown-wait-millis", 1500L));
        final int endScreenCloseDelayTicks = Math.max(20, config.getInt("gameplay.end-screen-close-delay-ticks", 50));

        return new PluginSettings(
            Math.min(minBetMinor, maxBetMinor),
            Math.max(minBetMinor, maxBetMinor),
            minMines,
            maxMines,
            houseEdge,
            saveIntervalSeconds,
            shutdownWaitMillis,
            endScreenCloseDelayTicks
        );
    }

    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }
}
