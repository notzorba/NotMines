package io.github.notzorba.notmines.stats;

import io.github.notzorba.notmines.config.PluginSettings;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.sqlite.SQLiteConfig;

public final class StatsService {
    private static final String UPSERT_SQL = """
        INSERT INTO player_stats (
            uuid,
            last_name,
            search_name,
            games_played,
            wins,
            losses,
            total_wagered_minor,
            total_paid_minor,
            tiles_cleared,
            best_cashout_minor,
            biggest_bet_minor,
            skin_texture,
            skin_texture_signature,
            last_updated_epoch_ms
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(uuid) DO UPDATE SET
            last_name = excluded.last_name,
            search_name = excluded.search_name,
            games_played = player_stats.games_played + excluded.games_played,
            wins = player_stats.wins + excluded.wins,
            losses = player_stats.losses + excluded.losses,
            total_wagered_minor = player_stats.total_wagered_minor + excluded.total_wagered_minor,
            total_paid_minor = player_stats.total_paid_minor + excluded.total_paid_minor,
            tiles_cleared = player_stats.tiles_cleared + excluded.tiles_cleared,
            best_cashout_minor = max(player_stats.best_cashout_minor, excluded.best_cashout_minor),
            biggest_bet_minor = max(player_stats.biggest_bet_minor, excluded.biggest_bet_minor),
            skin_texture = coalesce(excluded.skin_texture, player_stats.skin_texture),
            skin_texture_signature = coalesce(excluded.skin_texture_signature, player_stats.skin_texture_signature),
            last_updated_epoch_ms = excluded.last_updated_epoch_ms
        """;

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final ScheduledExecutorService executor;
    private final Map<UUID, PlayerStatsSnapshot> cache = new ConcurrentHashMap<>();
    private final Map<UUID, StatsDelta> pendingDeltas = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final Set<UUID> cacheLoadsInFlight = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean pendingDiskPersistScheduled = new AtomicBoolean(false);
    private final AtomicBoolean pendingDiskPersistDirty = new AtomicBoolean(false);
    private final AtomicLong leaderboardCacheRevision = new AtomicLong(0L);
    private final Object pendingFileLock = new Object();
    private final File pendingFile;

    private volatile Connection connection;
    private volatile LeaderboardSnapshot leaderboardSnapshot = new LeaderboardSnapshot(-1L, Map.of(), new EnumMap<>(LeaderboardStat.class));

    public StatsService(final JavaPlugin plugin, final PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.executor = Executors.newSingleThreadScheduledExecutor(new StatsThreadFactory());
        this.pendingFile = new File(new File(this.plugin.getDataFolder(), "data"), "pending-stats.yml");
    }

    public void initialize() {
        if (!this.initialized.compareAndSet(false, true)) {
            return;
        }

        this.executor.execute(this::initializeDatabase);
        this.executor.scheduleWithFixedDelay(
            this::flushPendingSafely,
            this.settings.saveIntervalSeconds(),
            this.settings.saveIntervalSeconds(),
            TimeUnit.SECONDS
        );
    }

    public void recordRound(
        final UUID playerId,
        final String playerName,
        final long wagerMinor,
        final long payoutMinor,
        final int safeTilesCleared,
        final boolean won
    ) {
        this.recordRound(playerId, playerName, wagerMinor, payoutMinor, safeTilesCleared, won, null, null);
    }

    public void recordRound(
        final UUID playerId,
        final String playerName,
        final long wagerMinor,
        final long payoutMinor,
        final int safeTilesCleared,
        final boolean won,
        final String skinTexture,
        final String skinTextureSignature
    ) {
        final StatsDelta delta = StatsDelta.fromRound(
            playerName,
            wagerMinor,
            payoutMinor,
            safeTilesCleared,
            won,
            skinTexture,
            skinTextureSignature
        );
        this.pendingDeltas.merge(playerId, delta, StatsDelta::merge);
        this.nameIndex.put(PlayerStatsSnapshot.normalizeName(playerName), playerId);
        this.invalidateLeaderboardCache();
        this.schedulePendingPersist();
    }

    public CompletableFuture<PlayerStatsSnapshot> getStatsAsync(final UUID playerId, final String fallbackName) {
        final PlayerStatsSnapshot cached = this.cache.get(playerId);
        final StatsDelta pending = this.pendingDeltas.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached.merge(pending));
        }

        return CompletableFuture.supplyAsync(() -> {
            final boolean databaseReady = this.connection != null;
            final Optional<PlayerStatsSnapshot> loaded = this.loadByUuid(playerId, fallbackName);
            loaded.ifPresent(snapshot -> {
                this.cache.putIfAbsent(playerId, snapshot);
                this.nameIndex.put(snapshot.searchName(), playerId);
                this.invalidateLeaderboardCache();
            });
            if (loaded.isEmpty() && databaseReady) {
                this.cache.putIfAbsent(playerId, PlayerStatsSnapshot.empty(playerId, fallbackName));
                this.invalidateLeaderboardCache();
            }

            return loaded.orElse(PlayerStatsSnapshot.empty(playerId, fallbackName)).merge(this.pendingDeltas.get(playerId));
        }, this.executor);
    }

    public PlayerStatsSnapshot getCachedStats(final UUID playerId, final String fallbackName) {
        final PlayerStatsSnapshot cached = this.cache.get(playerId);
        final StatsDelta pending = this.pendingDeltas.get(playerId);
        if (cached != null) {
            return cached.merge(pending);
        }

        return PlayerStatsSnapshot.empty(playerId, fallbackName).merge(pending);
    }

    public void preloadStats(final UUID playerId, final String fallbackName) {
        if (this.cache.containsKey(playerId) || !this.cacheLoadsInFlight.add(playerId)) {
            return;
        }

        this.executor.execute(() -> {
            try {
                final boolean databaseReady = this.connection != null;
                final Optional<PlayerStatsSnapshot> loaded = this.loadByUuid(playerId, fallbackName);
                if (loaded.isPresent()) {
                    final PlayerStatsSnapshot stored = loaded.get();
                    this.cache.put(playerId, stored);
                    this.nameIndex.put(stored.searchName(), playerId);
                    this.invalidateLeaderboardCache();
                } else if (databaseReady) {
                    this.cache.put(playerId, PlayerStatsSnapshot.empty(playerId, fallbackName));
                    this.invalidateLeaderboardCache();
                }
            } finally {
                this.cacheLoadsInFlight.remove(playerId);
            }
        });
    }

    public CompletableFuture<Optional<PlayerStatsSnapshot>> getStatsByNameAsync(final String playerName) {
        final String normalized = PlayerStatsSnapshot.normalizeName(playerName);
        final UUID indexedUuid = this.nameIndex.get(normalized);
        if (indexedUuid != null) {
            return this.getStatsAsync(indexedUuid, playerName).thenApply(Optional::of);
        }

        return CompletableFuture.supplyAsync(() -> {
            final Optional<PlayerStatsSnapshot> stored = this.loadByName(normalized);
            stored.ifPresent(snapshot -> {
                this.cache.putIfAbsent(snapshot.uuid(), snapshot);
                this.nameIndex.put(snapshot.searchName(), snapshot.uuid());
                this.invalidateLeaderboardCache();
            });
            return stored.map(snapshot -> snapshot.merge(this.pendingDeltas.get(snapshot.uuid())));
        }, this.executor);
    }

    public CompletableFuture<LeaderboardPage> getLeaderboardAsync(
        final LeaderboardStat stat,
        final int page,
        final int pageSize,
        final UUID viewerId
    ) {
        final int normalizedPageSize = Math.max(1, pageSize);
        return CompletableFuture.supplyAsync(() -> {
            final LeaderboardSnapshot snapshot = this.getOrBuildLeaderboardSnapshot();
            final Map<UUID, PlayerStatsSnapshot> mergedSnapshots = snapshot.mergedSnapshots();
            final List<PlayerStatsSnapshot> sorted = this.getOrBuildSortedLeaderboard(snapshot, stat);
            final int totalEntries = sorted.size();
            final int totalPages = Math.max(1, (int) Math.ceil((double) totalEntries / (double) normalizedPageSize));
            final int normalizedPage = Math.max(0, Math.min(page, totalPages - 1));
            final int startIndex = Math.min(normalizedPage * normalizedPageSize, totalEntries);
            final int endIndex = Math.min(startIndex + normalizedPageSize, totalEntries);
            final PlayerStatsSnapshot viewerSnapshot = mergedSnapshots.get(viewerId);
            final int viewerRank = viewerSnapshot != null && viewerSnapshot.hasActivity()
                ? findRank(sorted, viewerId)
                : 0;

            return new LeaderboardPage(
                stat,
                normalizedPage,
                totalPages,
                totalEntries,
                viewerRank,
                viewerSnapshot,
                sorted.subList(startIndex, endIndex)
            );
        }, this.executor);
    }

    public void shutdown() {
        if (!this.shuttingDown.compareAndSet(false, true)) {
            return;
        }

        try {
            final Future<?> flushFuture = this.executor.submit(() -> {
                this.flushPendingInternal();
                this.closeConnection();
            });
            flushFuture.get(this.settings.shutdownWaitMillis(), TimeUnit.MILLISECONDS);
        } catch (final Exception exception) {
            this.plugin.getLogger().log(Level.WARNING, "NotMines stats shutdown timed out or failed.", exception);
        } finally {
            this.executor.shutdown();
            try {
                if (!this.executor.awaitTermination(1L, TimeUnit.SECONDS)) {
                    this.executor.shutdownNow();
                }
            } catch (final InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                this.executor.shutdownNow();
            }
        }
    }

    private void initializeDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");

            final File dataDirectory = this.ensureDataDirectory();
            if (dataDirectory == null) {
                throw new IOException("Failed to create the NotMines data directory.");
            }

            this.loadPendingFromDisk();

            final File databaseFile = new File(dataDirectory, "stats.db");
            this.connection = this.openConnection(databaseFile);

            this.connection.setAutoCommit(false);

            try (Statement statement = this.connection.createStatement()) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        uuid TEXT PRIMARY KEY,
                        last_name TEXT NOT NULL,
                        search_name TEXT NOT NULL,
                        games_played INTEGER NOT NULL DEFAULT 0,
                        wins INTEGER NOT NULL DEFAULT 0,
                        losses INTEGER NOT NULL DEFAULT 0,
                        total_wagered_minor INTEGER NOT NULL DEFAULT 0,
                        total_paid_minor INTEGER NOT NULL DEFAULT 0,
                        tiles_cleared INTEGER NOT NULL DEFAULT 0,
                        best_cashout_minor INTEGER NOT NULL DEFAULT 0,
                        biggest_bet_minor INTEGER NOT NULL DEFAULT 0,
                        skin_texture TEXT,
                        skin_texture_signature TEXT,
                        last_updated_epoch_ms INTEGER NOT NULL DEFAULT 0
                    )
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_player_stats_search_name ON player_stats(search_name)");
            }

            this.ensureColumnExists("player_stats", "skin_texture", "TEXT");
            this.ensureColumnExists("player_stats", "skin_texture_signature", "TEXT");

            this.connection.commit();
            this.loadCacheFromDatabase();
            this.flushPendingSafely();
        } catch (final Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to initialize the NotMines stats database.", exception);
            this.closeConnection();
        }
    }

    private Connection openConnection(final File databaseFile) throws SQLException {
        try {
            return this.createConnection(databaseFile, true);
        } catch (final SQLException exception) {
            if (!this.isWalModeFailure(exception)) {
                throw exception;
            }

            this.plugin.getLogger().warning(
                "SQLite WAL mode could not be enabled for NotMines stats; falling back to the default journal mode."
            );
            return this.createConnection(databaseFile, false);
        }
    }

    private Connection createConnection(final File databaseFile, final boolean preferWal) throws SQLException {
        final SQLiteConfig sqliteConfig = new SQLiteConfig();
        if (preferWal) {
            sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);
            sqliteConfig.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        }
        sqliteConfig.setTempStore(SQLiteConfig.TempStore.MEMORY);
        sqliteConfig.setBusyTimeout(5000);
        return sqliteConfig.createConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    }

    private void ensureColumnExists(final String tableName, final String columnName, final String definition) throws SQLException {
        if (this.connection == null || this.columnExists(tableName, columnName)) {
            return;
        }

        try (Statement statement = this.connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private boolean columnExists(final String tableName, final String columnName) throws SQLException {
        if (this.connection == null) {
            return false;
        }

        try (Statement statement = this.connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isWalModeFailure(final SQLException exception) {
        final String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("cannot change into wal mode");
    }

    private Optional<PlayerStatsSnapshot> loadByUuid(final UUID playerId, final String fallbackName) {
        if (this.connection == null) {
            return Optional.empty();
        }

        try (PreparedStatement statement = this.connection.prepareStatement(
            """
                SELECT uuid, last_name, search_name, games_played, wins, losses, total_wagered_minor,
                       total_paid_minor, tiles_cleared, best_cashout_minor, biggest_bet_minor,
                       skin_texture, skin_texture_signature
                FROM player_stats
                WHERE uuid = ?
                """
        )) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(this.mapSnapshot(resultSet));
            }
        } catch (final SQLException exception) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to load NotMines stats for " + fallbackName + ".", exception);
            return Optional.empty();
        }
    }

    private Optional<PlayerStatsSnapshot> loadByName(final String normalizedName) {
        if (this.connection == null) {
            return Optional.empty();
        }

        try (PreparedStatement statement = this.connection.prepareStatement(
            """
                SELECT uuid, last_name, search_name, games_played, wins, losses, total_wagered_minor,
                       total_paid_minor, tiles_cleared, best_cashout_minor, biggest_bet_minor,
                       skin_texture, skin_texture_signature
                FROM player_stats
                WHERE search_name = ?
                LIMIT 1
                """
        )) {
            statement.setString(1, normalizedName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(this.mapSnapshot(resultSet));
            }
        } catch (final SQLException exception) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to load NotMines stats for " + normalizedName + ".", exception);
            return Optional.empty();
        }
    }

    private PlayerStatsSnapshot mapSnapshot(final ResultSet resultSet) throws SQLException {
        return new PlayerStatsSnapshot(
            UUID.fromString(resultSet.getString("uuid")),
            resultSet.getString("last_name"),
            resultSet.getString("search_name"),
            resultSet.getLong("games_played"),
            resultSet.getLong("wins"),
            resultSet.getLong("losses"),
            resultSet.getLong("total_wagered_minor"),
            resultSet.getLong("total_paid_minor"),
            resultSet.getLong("tiles_cleared"),
            resultSet.getLong("best_cashout_minor"),
            resultSet.getLong("biggest_bet_minor"),
            resultSet.getString("skin_texture"),
            resultSet.getString("skin_texture_signature")
        );
    }

    private void loadCacheFromDatabase() {
        if (this.connection == null) {
            return;
        }

        final List<PlayerStatsSnapshot> snapshots = new ArrayList<>();
        try (Statement statement = this.connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                 """
                     SELECT uuid, last_name, search_name, games_played, wins, losses, total_wagered_minor,
                            total_paid_minor, tiles_cleared, best_cashout_minor, biggest_bet_minor,
                            skin_texture, skin_texture_signature
                     FROM player_stats
                     """
             )) {
            while (resultSet.next()) {
                snapshots.add(this.mapSnapshot(resultSet));
            }
        } catch (final SQLException exception) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to warm the NotMines stats cache from the database.", exception);
            return;
        }

        for (PlayerStatsSnapshot snapshot : snapshots) {
            this.cache.put(snapshot.uuid(), snapshot);
            this.nameIndex.put(snapshot.searchName(), snapshot.uuid());
        }
        this.invalidateLeaderboardCache();
    }

    private void flushPendingSafely() {
        try {
            this.flushPendingInternal();
        } catch (final Exception exception) {
            this.plugin.getLogger().log(Level.WARNING, "A scheduled NotMines stats flush failed.", exception);
        }
    }

    private void schedulePendingPersist() {
        this.pendingDiskPersistDirty.set(true);
        if (!this.pendingDiskPersistScheduled.compareAndSet(false, true)) {
            return;
        }

        this.executor.execute(() -> {
            try {
                do {
                    this.pendingDiskPersistDirty.set(false);
                    this.persistPendingToDisk();
                } while (this.pendingDiskPersistDirty.get());
            } finally {
                this.pendingDiskPersistScheduled.set(false);
                if (this.pendingDiskPersistDirty.get()) {
                    this.schedulePendingPersist();
                }
            }
        });
    }

    private void flushPendingInternal() {
        if (this.connection == null || this.pendingDeltas.isEmpty()) {
            return;
        }

        final Map<UUID, StatsDelta> snapshot = new ConcurrentHashMap<>(this.pendingDeltas);
        if (snapshot.isEmpty()) {
            return;
        }

        try (PreparedStatement statement = this.connection.prepareStatement(UPSERT_SQL)) {
            final long now = System.currentTimeMillis();
            for (Map.Entry<UUID, StatsDelta> entry : snapshot.entrySet()) {
                final UUID playerId = entry.getKey();
                final StatsDelta delta = entry.getValue();

                statement.setString(1, playerId.toString());
                statement.setString(2, delta.lastKnownName());
                statement.setString(3, PlayerStatsSnapshot.normalizeName(delta.lastKnownName()));
                statement.setLong(4, delta.gamesPlayed());
                statement.setLong(5, delta.wins());
                statement.setLong(6, delta.losses());
                statement.setLong(7, delta.totalWageredMinor());
                statement.setLong(8, delta.totalPaidMinor());
                statement.setLong(9, delta.tilesCleared());
                statement.setLong(10, delta.bestCashoutMinor());
                statement.setLong(11, delta.biggestBetMinor());
                statement.setString(12, delta.skinTexture());
                statement.setString(13, delta.skinTextureSignature());
                statement.setLong(14, now);
                statement.addBatch();
            }

            statement.executeBatch();
            this.connection.commit();

            for (Map.Entry<UUID, StatsDelta> entry : snapshot.entrySet()) {
                final UUID playerId = entry.getKey();
                final StatsDelta delta = entry.getValue();
                this.pendingDeltas.remove(playerId, delta);
                this.cache.compute(playerId, (ignored, existing) -> {
                    final PlayerStatsSnapshot base = existing == null
                        ? PlayerStatsSnapshot.empty(playerId, delta.lastKnownName())
                        : existing;
                    return base.merge(delta);
                });
                this.nameIndex.put(PlayerStatsSnapshot.normalizeName(delta.lastKnownName()), playerId);
            }
            this.invalidateLeaderboardCache();
            this.persistPendingToDisk();
        } catch (final SQLException exception) {
            try {
                this.connection.rollback();
            } catch (final SQLException rollbackException) {
                this.plugin.getLogger().log(Level.WARNING, "A NotMines stats rollback also failed.", rollbackException);
            }
            throw new IllegalStateException("Unable to flush NotMines stats.", exception);
        }
    }

    private Map<UUID, PlayerStatsSnapshot> snapshotMergedStats() {
        final Map<UUID, PlayerStatsSnapshot> merged = new HashMap<>(this.cache);
        for (Map.Entry<UUID, StatsDelta> entry : this.pendingDeltas.entrySet()) {
            merged.compute(entry.getKey(), (ignored, existing) -> {
                final PlayerStatsSnapshot base = existing == null
                    ? PlayerStatsSnapshot.empty(entry.getKey(), entry.getValue().lastKnownName())
                    : existing;
                return base.merge(entry.getValue());
            });
        }
        return merged;
    }

    private LeaderboardSnapshot getOrBuildLeaderboardSnapshot() {
        final long revision = this.leaderboardCacheRevision.get();
        final LeaderboardSnapshot current = this.leaderboardSnapshot;
        if (current.revision() == revision) {
            return current;
        }

        final Map<UUID, PlayerStatsSnapshot> mergedSnapshots = this.snapshotMergedStats();
        if (this.leaderboardCacheRevision.get() != revision) {
            return this.getOrBuildLeaderboardSnapshot();
        }

        final LeaderboardSnapshot rebuilt = new LeaderboardSnapshot(revision, mergedSnapshots, new EnumMap<>(LeaderboardStat.class));
        this.leaderboardSnapshot = rebuilt;
        return rebuilt;
    }

    private List<PlayerStatsSnapshot> getOrBuildSortedLeaderboard(
        final LeaderboardSnapshot snapshot,
        final LeaderboardStat stat
    ) {
        final List<PlayerStatsSnapshot> cachedList = snapshot.sortedByStat().get(stat);
        if (cachedList != null) {
            return cachedList;
        }

        final List<PlayerStatsSnapshot> sorted = this.sortLeaderboard(stat, snapshot.mergedSnapshots().values());
        if (this.leaderboardCacheRevision.get() != snapshot.revision()) {
            return this.getOrBuildSortedLeaderboard(this.getOrBuildLeaderboardSnapshot(), stat);
        }

        snapshot.sortedByStat().put(stat, sorted);
        return sorted;
    }

    private List<PlayerStatsSnapshot> sortLeaderboard(
        final LeaderboardStat stat,
        final Iterable<PlayerStatsSnapshot> snapshots
    ) {
        final Comparator<PlayerStatsSnapshot> comparator = switch (stat) {
            case WIN_RATE -> Comparator
                .comparingDouble(PlayerStatsSnapshot::winRate)
                .thenComparingLong(PlayerStatsSnapshot::wins)
                .thenComparingLong(PlayerStatsSnapshot::gamesPlayed);
            case GAMES_PLAYED -> Comparator.comparingLong(PlayerStatsSnapshot::gamesPlayed);
            case WINS -> Comparator.comparingLong(PlayerStatsSnapshot::wins);
            case TILES_CLEARED -> Comparator.comparingLong(PlayerStatsSnapshot::tilesCleared);
            case TOTAL_WAGERED -> Comparator.comparingLong(PlayerStatsSnapshot::totalWageredMinor);
            case TOTAL_PAID -> Comparator.comparingLong(PlayerStatsSnapshot::totalPaidMinor);
            case BEST_CASHOUT -> Comparator.comparingLong(PlayerStatsSnapshot::bestCashoutMinor);
            case BIGGEST_BET -> Comparator.comparingLong(PlayerStatsSnapshot::biggestBetMinor);
            case NET_PROFIT -> Comparator.comparingLong(PlayerStatsSnapshot::netProfitMinor);
        };

        final List<PlayerStatsSnapshot> orderedSnapshots = new ArrayList<>();
        for (PlayerStatsSnapshot snapshot : snapshots) {
            orderedSnapshots.add(snapshot);
        }

        return orderedSnapshots.stream()
            .filter(PlayerStatsSnapshot::hasActivity)
            .sorted(
                comparator.reversed()
                    .thenComparing(PlayerStatsSnapshot::lastKnownName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(snapshot -> snapshot.uuid().toString())
            )
            .toList();
    }

    private static int findRank(final List<PlayerStatsSnapshot> sorted, final UUID viewerId) {
        for (int index = 0; index < sorted.size(); index++) {
            if (sorted.get(index).uuid().equals(viewerId)) {
                return index + 1;
            }
        }
        return 0;
    }

    private void invalidateLeaderboardCache() {
        this.leaderboardCacheRevision.incrementAndGet();
    }

    private void closeConnection() {
        if (this.connection == null) {
            return;
        }

        try {
            this.connection.close();
        } catch (final SQLException exception) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to close the NotMines stats database cleanly.", exception);
        } finally {
            this.connection = null;
        }
    }

    private void loadPendingFromDisk() {
        synchronized (this.pendingFileLock) {
            if (!this.pendingFile.exists()) {
                return;
            }

            final YamlConfiguration config = YamlConfiguration.loadConfiguration(this.pendingFile);
            final ConfigurationSection players = config.getConfigurationSection("players");
            if (players == null) {
                return;
            }

            for (String key : players.getKeys(false)) {
                final ConfigurationSection section = players.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }

                try {
                    final UUID playerId = UUID.fromString(key);
                    final StatsDelta delta = new StatsDelta(
                        section.getString("last-known-name", ""),
                        section.getLong("games-played"),
                        section.getLong("wins"),
                        section.getLong("losses"),
                        section.getLong("total-wagered-minor"),
                        section.getLong("total-paid-minor"),
                        section.getLong("tiles-cleared"),
                        section.getLong("best-cashout-minor"),
                        section.getLong("biggest-bet-minor"),
                        section.getString("skin-texture"),
                        section.getString("skin-texture-signature")
                    );
                    this.pendingDeltas.merge(playerId, delta, StatsDelta::merge);
                    this.nameIndex.put(PlayerStatsSnapshot.normalizeName(delta.lastKnownName()), playerId);
                } catch (final IllegalArgumentException exception) {
                    this.plugin.getLogger().log(Level.WARNING, "Skipping malformed pending stats entry for key " + key + ".", exception);
                }
            }
        }
    }

    private void persistPendingToDisk() {
        synchronized (this.pendingFileLock) {
            final Map<UUID, StatsDelta> snapshot = new ConcurrentHashMap<>(this.pendingDeltas);
            if (snapshot.isEmpty()) {
                if (this.pendingFile.exists() && !this.pendingFile.delete()) {
                    this.plugin.getLogger().warning("Failed to delete the pending NotMines stats journal.");
                }
                return;
            }

            final File dataDirectory = this.ensureDataDirectory();
            if (dataDirectory == null) {
                this.plugin.getLogger().warning("Failed to persist pending NotMines stats because the data directory is unavailable.");
                return;
            }

            final YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<UUID, StatsDelta> entry : snapshot.entrySet()) {
                final String root = "players." + entry.getKey();
                final StatsDelta delta = entry.getValue();
                config.set(root + ".last-known-name", delta.lastKnownName());
                config.set(root + ".games-played", delta.gamesPlayed());
                config.set(root + ".wins", delta.wins());
                config.set(root + ".losses", delta.losses());
                config.set(root + ".total-wagered-minor", delta.totalWageredMinor());
                config.set(root + ".total-paid-minor", delta.totalPaidMinor());
                config.set(root + ".tiles-cleared", delta.tilesCleared());
                config.set(root + ".best-cashout-minor", delta.bestCashoutMinor());
                config.set(root + ".biggest-bet-minor", delta.biggestBetMinor());
                config.set(root + ".skin-texture", delta.skinTexture());
                config.set(root + ".skin-texture-signature", delta.skinTextureSignature());
            }

            try {
                config.save(this.pendingFile);
            } catch (final IOException exception) {
                this.plugin.getLogger().log(Level.WARNING, "Failed to persist pending NotMines stats to disk.", exception);
            }
        }
    }

    private File ensureDataDirectory() {
        final File dataDirectory = new File(this.plugin.getDataFolder(), "data");
        if (!dataDirectory.exists() && !dataDirectory.mkdirs()) {
            this.plugin.getLogger().warning("Failed to create the NotMines data directory.");
            return null;
        }
        return dataDirectory;
    }

    private static final class StatsThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(final Runnable runnable) {
            final Thread thread = new Thread(runnable, "NotMines-Stats");
            thread.setDaemon(true);
            return thread;
        }
    }

    private record LeaderboardSnapshot(
        long revision,
        Map<UUID, PlayerStatsSnapshot> mergedSnapshots,
        EnumMap<LeaderboardStat, List<PlayerStatsSnapshot>> sortedByStat
    ) {
    }
}
