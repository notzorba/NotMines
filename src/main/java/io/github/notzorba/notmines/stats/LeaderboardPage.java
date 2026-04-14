package io.github.notzorba.notmines.stats;

import java.util.List;

public record LeaderboardPage(
    LeaderboardStat stat,
    int page,
    int totalPages,
    int totalEntries,
    int viewerRank,
    PlayerStatsSnapshot viewerSnapshot,
    List<PlayerStatsSnapshot> entries
) {
}
