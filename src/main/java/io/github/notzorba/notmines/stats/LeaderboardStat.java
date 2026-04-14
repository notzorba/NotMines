package io.github.notzorba.notmines.stats;

import java.util.Locale;

public enum LeaderboardStat {
    NET_PROFIT("profit", "Net Profit"),
    TOTAL_WAGERED("wagered", "Total Wagered"),
    TOTAL_PAID("paid", "Total Paid"),
    BEST_CASHOUT("best-cashout", "Best Cashout"),
    BIGGEST_BET("biggest-bet", "Biggest Bet"),
    GAMES_PLAYED("games", "Games Played"),
    WINS("wins", "Wins"),
    WIN_RATE("win-rate", "Win Rate"),
    TILES_CLEARED("tiles", "Tiles Cleared");

    private final String id;
    private final String displayName;

    LeaderboardStat(final String id, final String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return this.id;
    }

    public String displayName() {
        return this.displayName;
    }

    public double sortValue(final PlayerStatsSnapshot snapshot) {
        return switch (this) {
            case NET_PROFIT -> snapshot.netProfitMinor();
            case TOTAL_WAGERED -> snapshot.totalWageredMinor();
            case TOTAL_PAID -> snapshot.totalPaidMinor();
            case BEST_CASHOUT -> snapshot.bestCashoutMinor();
            case BIGGEST_BET -> snapshot.biggestBetMinor();
            case GAMES_PLAYED -> snapshot.gamesPlayed();
            case WINS -> snapshot.wins();
            case WIN_RATE -> snapshot.winRate();
            case TILES_CLEARED -> snapshot.tilesCleared();
        };
    }

    public LeaderboardStat next() {
        final LeaderboardStat[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static LeaderboardStat[] orderedValues() {
        return values();
    }

    public static LeaderboardStat fromId(final String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        final String normalized = rawValue.toLowerCase(Locale.ROOT).replace('_', '-');
        final String compact = normalized.replace("-", "");
        for (LeaderboardStat value : values()) {
            if (value.id.equals(normalized) || value.id.replace("-", "").equals(compact)) {
                return value;
            }
        }
        return null;
    }
}
