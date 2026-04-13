package io.github.notzorba.notmines.stats;

import io.github.notzorba.notmines.config.PluginSettings;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

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
            last_updated_epoch_ms
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            last_updated_epoch_ms = excluded.last_updated_epoch_ms
        """;

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final ScheduledExecutorService executor;
    private final Map<UUID, PlayerStatsSnapshot> cache = new ConcurrentHashMap<>();
    private final Map<UUID, StatsDelta> pendingDeltas = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final Object pendingFileLock = new Object();
    private final File pendingFile;

    private volatile Connection connection;

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
        final StatsDelta delta = StatsDelta.fromRound(playerName, wagerMinor, payoutMinor, safeTilesCleared, won);
        this.pendingDeltas.merge(playerId, delta, StatsDelta::merge);
        this.nameIndex.put(PlayerStatsSnapshot.normalizeName(playerName), playerId);
        this.persistPendingToDisk();
    }

    public CompletableFuture<PlayerStatsSnapshot> getStatsAsync(final UUID playerId, final String fallbackName) {
        final PlayerStatsSnapshot cached = this.cache.get(playerId);
        final StatsDelta pending = this.pendingDeltas.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached.merge(pending));
        }

        return CompletableFuture.supplyAsync(() -> {
            final PlayerStatsSnapshot stored = this.loadByUuid(playerId, fallbackName).orElse(PlayerStatsSnapshot.empty(playerId, fallbackName));
            this.cache.putIfAbsent(playerId, stored);
            this.nameIndex.put(stored.searchName(), playerId);
            return stored.merge(this.pendingDeltas.get(playerId));
        }, this.executor);
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
            });
            return stored.map(snapshot -> snapshot.merge(this.pendingDeltas.get(snapshot.uuid())));
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
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            this.connection.setAutoCommit(false);

            try (Statement statement = this.connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
                statement.execute("PRAGMA temp_store = MEMORY");
                statement.execute("PRAGMA busy_timeout = 5000");
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
                        last_updated_epoch_ms INTEGER NOT NULL DEFAULT 0
                    )
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_player_stats_search_name ON player_stats(search_name)");
            }

            this.connection.commit();
            this.flushPendingSafely();
        } catch (final Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to initialize the NotMines stats database.", exception);
            this.closeConnection();
        }
    }

    private Optional<PlayerStatsSnapshot> loadByUuid(final UUID playerId, final String fallbackName) {
        if (this.connection == null) {
            return Optional.empty();
        }

        try (PreparedStatement statement = this.connection.prepareStatement(
            """
                SELECT uuid, last_name, search_name, games_played, wins, losses, total_wagered_minor,
                       total_paid_minor, tiles_cleared, best_cashout_minor, biggest_bet_minor
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
                       total_paid_minor, tiles_cleared, best_cashout_minor, biggest_bet_minor
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
            resultSet.getLong("biggest_bet_minor")
        );
    }

    private void flushPendingSafely() {
        try {
            this.flushPendingInternal();
        } catch (final Exception exception) {
            this.plugin.getLogger().log(Level.WARNING, "A scheduled NotMines stats flush failed.", exception);
        }
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
                statement.setLong(12, now);
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
                        section.getLong("biggest-bet-minor")
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
}
