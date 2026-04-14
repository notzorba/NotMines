package io.github.notzorba.notmines.placeholder;

import io.github.notzorba.notmines.NotMinesPlugin;
import io.github.notzorba.notmines.stats.PlayerStatsSnapshot;
import io.github.notzorba.notmines.util.Money;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public final class NotMinesPlaceholderExpansion extends PlaceholderExpansion {
    private final NotMinesPlugin plugin;

    public NotMinesPlaceholderExpansion(final NotMinesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "notmines";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", this.plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(final OfflinePlayer player, final String params) {
        if (player == null || params == null || params.isBlank()) {
            return "";
        }

        final StatPlaceholder placeholder = StatPlaceholder.fromParams(params);
        if (placeholder == null) {
            return "";
        }

        final String fallbackName = player.getName() == null ? "" : player.getName();
        this.plugin.statsService().preloadStats(player.getUniqueId(), fallbackName);

        final PlayerStatsSnapshot snapshot = this.plugin.statsService().getCachedStats(player.getUniqueId(), fallbackName);
        return placeholder.resolve(snapshot, this.plugin);
    }

    private enum StatPlaceholder {
        GAMES((snapshot, plugin) -> Long.toString(snapshot.gamesPlayed()), "games", "games_played", "games_raw", "games_played_raw"),
        WINS((snapshot, plugin) -> Long.toString(snapshot.wins()), "wins", "wins_raw"),
        LOSSES((snapshot, plugin) -> Long.toString(snapshot.losses()), "losses", "losses_raw"),
        TILES_CLEARED((snapshot, plugin) -> Long.toString(snapshot.tilesCleared()), "tiles", "tiles_cleared", "tiles_raw", "tiles_cleared_raw"),
        WIN_RATE((snapshot, plugin) -> Money.formatPercent(snapshot.winRate()), "win_rate", "winrate", "win_rate_raw", "winrate_raw"),
        TOTAL_WAGERED(
            (snapshot, plugin) -> plugin.economyBridge().format(snapshot.totalWageredMinor()),
            "wagered",
            "total_wagered"
        ),
        TOTAL_WAGERED_RAW(
            (snapshot, plugin) -> Money.formatPlainMinor(snapshot.totalWageredMinor(), plugin.economyBridge().currencyScale()),
            "wagered_raw",
            "total_wagered_raw"
        ),
        TOTAL_WAGERED_MINOR((snapshot, plugin) -> Long.toString(snapshot.totalWageredMinor()), "wagered_minor", "total_wagered_minor"),
        TOTAL_PAID(
            (snapshot, plugin) -> plugin.economyBridge().format(snapshot.totalPaidMinor()),
            "paid",
            "paid_out",
            "total_paid",
            "total_paid_out"
        ),
        TOTAL_PAID_RAW(
            (snapshot, plugin) -> Money.formatPlainMinor(snapshot.totalPaidMinor(), plugin.economyBridge().currencyScale()),
            "paid_raw",
            "paid_out_raw",
            "total_paid_raw",
            "total_paid_out_raw"
        ),
        TOTAL_PAID_MINOR(
            (snapshot, plugin) -> Long.toString(snapshot.totalPaidMinor()),
            "paid_minor",
            "paid_out_minor",
            "total_paid_minor",
            "total_paid_out_minor"
        ),
        PROFIT((snapshot, plugin) -> plugin.economyBridge().format(snapshot.netProfitMinor()), "profit", "net_profit"),
        PROFIT_RAW(
            (snapshot, plugin) -> Money.formatPlainMinor(snapshot.netProfitMinor(), plugin.economyBridge().currencyScale()),
            "profit_raw",
            "net_profit_raw"
        ),
        PROFIT_MINOR((snapshot, plugin) -> Long.toString(snapshot.netProfitMinor()), "profit_minor", "net_profit_minor"),
        BEST_CASHOUT((snapshot, plugin) -> plugin.economyBridge().format(snapshot.bestCashoutMinor()), "best_cashout"),
        BEST_CASHOUT_RAW(
            (snapshot, plugin) -> Money.formatPlainMinor(snapshot.bestCashoutMinor(), plugin.economyBridge().currencyScale()),
            "best_cashout_raw"
        ),
        BEST_CASHOUT_MINOR((snapshot, plugin) -> Long.toString(snapshot.bestCashoutMinor()), "best_cashout_minor"),
        BIGGEST_BET((snapshot, plugin) -> plugin.economyBridge().format(snapshot.biggestBetMinor()), "biggest_bet"),
        BIGGEST_BET_RAW(
            (snapshot, plugin) -> Money.formatPlainMinor(snapshot.biggestBetMinor(), plugin.economyBridge().currencyScale()),
            "biggest_bet_raw"
        ),
        BIGGEST_BET_MINOR((snapshot, plugin) -> Long.toString(snapshot.biggestBetMinor()), "biggest_bet_minor");

        private final PlaceholderResolver resolver;
        private final String[] aliases;

        StatPlaceholder(final PlaceholderResolver resolver, final String... aliases) {
            this.resolver = resolver;
            this.aliases = aliases;
        }

        public String resolve(final PlayerStatsSnapshot snapshot, final NotMinesPlugin plugin) {
            return this.resolver.resolve(snapshot, plugin);
        }

        public static StatPlaceholder fromParams(final String params) {
            final String normalized = params.toLowerCase(Locale.ROOT);
            for (StatPlaceholder placeholder : values()) {
                for (String alias : placeholder.aliases) {
                    if (alias.equals(normalized)) {
                        return placeholder;
                    }
                }
            }

            return null;
        }
    }

    @FunctionalInterface
    private interface PlaceholderResolver {
        String resolve(PlayerStatsSnapshot snapshot, NotMinesPlugin plugin);
    }
}
