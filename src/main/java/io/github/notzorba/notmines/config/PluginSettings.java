package io.github.notzorba.notmines.config;

import io.github.notzorba.notmines.util.Money;
import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(
    boolean metricsEnabled,
    long minBetMinor,
    long maxBetMinor,
    int minMines,
    int maxMines,
    double houseEdge,
    boolean safePickMessages,
    boolean boardCloseMessages,
    boolean announcementEnabled,
    double announcementMinMultiplier,
    long announcementMinPayoutMinor,
    boolean announcementConsoleEnabled,
    int saveIntervalSeconds,
    long shutdownWaitMillis,
    int endScreenCloseDelayTicks
) {
    public static PluginSettings load(final FileConfiguration config, final int currencyScale) {
        final boolean metricsEnabled = config.getBoolean("metrics.enabled", true);
        final long minBetMinor = Money.parseMinor(config.getString("limits.min-bet", "100"), currencyScale);
        final long maxBetMinor = Money.parseMinor(config.getString("limits.max-bet", "1000000"), currencyScale);
        final int minMines = Math.max(1, config.getInt("limits.min-mines", 1));
        final int maxMines = Math.min(24, Math.max(minMines, config.getInt("limits.max-mines", 24)));
        final double houseEdge = clamp(config.getDouble("gameplay.house-edge", 0.03D), 0.0D, 0.20D);
        final boolean safePickMessages = config.getBoolean("gameplay.safe-pick-messages", true);
        final boolean boardCloseMessages = config.getBoolean("gameplay.board-close-messages", true);
        final boolean announcementEnabled = config.getBoolean("announcements.enabled", true);
        final double announcementMinMultiplier = Math.max(1.01D, config.getDouble("announcements.min-multiplier", 5.0D));
        final long announcementMinPayoutMinor = Math.max(
            0L,
            Money.parseMinor(config.getString("announcements.min-payout", "0"), currencyScale)
        );
        final boolean announcementConsoleEnabled = config.getBoolean("announcements.broadcast-to-console", true);
        final int saveIntervalSeconds = Math.max(5, config.getInt("stats.save-interval-seconds", 15));
        final long shutdownWaitMillis = Math.max(250L, config.getLong("stats.shutdown-wait-millis", 1500L));
        final int endScreenCloseDelayTicks = Math.max(20, config.getInt("gameplay.end-screen-close-delay-ticks", 50));

        return new PluginSettings(
            metricsEnabled,
            Math.min(minBetMinor, maxBetMinor),
            Math.max(minBetMinor, maxBetMinor),
            minMines,
            maxMines,
            houseEdge,
            safePickMessages,
            boardCloseMessages,
            announcementEnabled,
            announcementMinMultiplier,
            announcementMinPayoutMinor,
            announcementConsoleEnabled,
            saveIntervalSeconds,
            shutdownWaitMillis,
            endScreenCloseDelayTicks
        );
    }

    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }
}
