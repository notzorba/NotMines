package io.github.notzorba.notmines.command;

import io.github.notzorba.notmines.NotMinesPlugin;
import io.github.notzorba.notmines.config.PluginSettings;
import io.github.notzorba.notmines.game.GameManager;
import io.github.notzorba.notmines.stats.PlayerStatsSnapshot;
import io.github.notzorba.notmines.stats.StatsService;
import io.github.notzorba.notmines.util.MessageService;
import io.github.notzorba.notmines.util.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class MinesCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "notmines.admin";

    private final NotMinesPlugin plugin;
    private final GameManager gameManager;
    private final StatsService statsService;
    private MessageService messages;

    public MinesCommand(
        final NotMinesPlugin plugin,
        final GameManager gameManager,
        final StatsService statsService,
        final MessageService messages
    ) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.statsService = statsService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!sender.hasPermission("notmines.use")) {
            this.messages.send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            this.sendHelp(sender);
            return true;
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "cashout" -> {
                final Player player = this.requirePlayer(sender);
                if (player != null) {
                    this.gameManager.cashOut(player);
                }
                return true;
            }
            case "reopen" -> {
                final Player player = this.requirePlayer(sender);
                if (player != null) {
                    this.gameManager.reopen(player);
                }
                return true;
            }
            case "stats" -> {
                this.handleStats(sender, args);
                return true;
            }
            case "limits" -> {
                this.handleLimits(sender, args);
                return true;
            }
            case "reload" -> {
                this.handleReload(sender);
                return true;
            }
            case "help" -> {
                this.sendHelp(sender);
                return true;
            }
            default -> {
                if (args.length != 2) {
                    this.sendHelp(sender);
                    return true;
                }

                final Player player = this.requirePlayer(sender);
                if (player != null) {
                    this.gameManager.startGame(player, args[0], args[1]);
                }
                return true;
            }
        }
    }

    private void handleStats(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            final Player player = this.requirePlayer(sender);
            if (player == null) {
                return;
            }

            this.messages.send(sender, "general.stats-loading");
            this.sendStats(sender, player.getName(), this.statsService.getStatsAsync(player.getUniqueId(), player.getName()));
            return;
        }

        if (!sender.hasPermission("notmines.stats.others")) {
            this.messages.send(sender, "general.no-permission");
            return;
        }

        final String targetName = args[1];
        this.messages.send(sender, "general.stats-loading");
        this.statsService.getStatsByNameAsync(targetName).whenComplete((snapshotOptional, throwable) -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("Failed to load NotMines stats for " + targetName + ".");
                return;
            }

            if (!this.plugin.isEnabled()) {
                return;
            }

            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                if (snapshotOptional.isEmpty()) {
                    this.messages.send(sender, "stats.missing", Placeholder.unparsed("player", targetName));
                    return;
                }

                this.showStats(sender, snapshotOptional.get());
            });
        });
    }

    private void handleLimits(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            this.messages.send(sender, "general.no-permission");
            return;
        }

        if (args.length == 1) {
            this.showLimits(sender);
            return;
        }

        if (args.length != 3) {
            this.messages.renderList("command.limits-usage").forEach(sender::sendMessage);
            return;
        }

        final LimitKey key = LimitKey.fromInput(args[1]);
        if (key == null) {
            this.messages.send(sender, "command.limit-unknown", Placeholder.unparsed("limit", args[1]));
            return;
        }

        final PluginSettings settings = this.plugin.settings();
        if (key.betLimit()) {
            this.updateBetLimit(sender, key, args[2], settings);
            return;
        }

        this.updateMineLimit(sender, key, args[2], settings);
    }

    private void updateBetLimit(
        final CommandSender sender,
        final LimitKey key,
        final String rawValue,
        final PluginSettings settings
    ) {
        final long parsedMinor;
        try {
            parsedMinor = Money.parseMinor(rawValue, this.plugin.economyBridge().currencyScale());
        } catch (final IllegalArgumentException exception) {
            this.messages.send(sender, "command.invalid-bet-format", Placeholder.unparsed("value", rawValue));
            return;
        }

        if (parsedMinor < 0L) {
            this.messages.send(
                sender,
                "command.limit-too-low",
                Placeholder.unparsed("limit", key.id()),
                Placeholder.unparsed("other", "0"),
                Placeholder.unparsed("value", "0")
            );
            return;
        }

        if (key == LimitKey.MIN_BET && parsedMinor > settings.maxBetMinor()) {
            this.messages.send(
                sender,
                "command.limit-too-high",
                Placeholder.unparsed("limit", key.id()),
                Placeholder.unparsed("other", "max-bet"),
                Placeholder.unparsed("value", this.plugin.economyBridge().format(settings.maxBetMinor()))
            );
            return;
        }

        if (key == LimitKey.MAX_BET && parsedMinor < settings.minBetMinor()) {
            this.messages.send(
                sender,
                "command.limit-too-low",
                Placeholder.unparsed("limit", key.id()),
                Placeholder.unparsed("other", "min-bet"),
                Placeholder.unparsed("value", this.plugin.economyBridge().format(settings.minBetMinor()))
            );
            return;
        }

        this.plugin.updateLimit(key.path(), normalizeMoneyValue(rawValue));
        this.messages.send(
            sender,
            "command.limit-updated",
            Placeholder.unparsed("limit", key.id()),
            Placeholder.unparsed("value", this.plugin.economyBridge().format(parsedMinor))
        );
        this.showLimits(sender);
    }

    private void handleReload(final CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            this.messages.send(sender, "general.no-permission");
            return;
        }

        try {
            this.plugin.reloadRuntimeResources();
            this.messages.send(sender, "command.reload-success");
        } catch (final IllegalArgumentException exception) {
            this.messages.send(sender, "command.reload-failed", Placeholder.unparsed("reason", exception.getMessage()));
        }
    }

    private void updateMineLimit(
        final CommandSender sender,
        final LimitKey key,
        final String rawValue,
        final PluginSettings settings
    ) {
        final int parsedValue;
        try {
            parsedValue = Integer.parseInt(rawValue.trim());
        } catch (final NumberFormatException exception) {
            this.messages.send(sender, "command.invalid-mines-format", Placeholder.unparsed("value", rawValue));
            return;
        }

        if (parsedValue < 1 || parsedValue > 24) {
            this.messages.send(
                sender,
                "command.invalid-mines",
                Placeholder.unparsed("min", "1"),
                Placeholder.unparsed("max", "24")
            );
            return;
        }

        if (key == LimitKey.MIN_MINES && parsedValue > settings.maxMines()) {
            this.messages.send(
                sender,
                "command.limit-too-high",
                Placeholder.unparsed("limit", key.id()),
                Placeholder.unparsed("other", "max-mines"),
                Placeholder.unparsed("value", Integer.toString(settings.maxMines()))
            );
            return;
        }

        if (key == LimitKey.MAX_MINES && parsedValue < settings.minMines()) {
            this.messages.send(
                sender,
                "command.limit-too-low",
                Placeholder.unparsed("limit", key.id()),
                Placeholder.unparsed("other", "min-mines"),
                Placeholder.unparsed("value", Integer.toString(settings.minMines()))
            );
            return;
        }

        this.plugin.updateLimit(key.path(), parsedValue);
        this.messages.send(
            sender,
            "command.limit-updated",
            Placeholder.unparsed("limit", key.id()),
            Placeholder.unparsed("value", Integer.toString(parsedValue))
        );
        this.showLimits(sender);
    }

    private void showLimits(final CommandSender sender) {
        final PluginSettings settings = this.plugin.settings();
        this.messages.renderList(
            "command.limits-current",
            Placeholder.unparsed("min_bet", this.plugin.economyBridge().format(settings.minBetMinor())),
            Placeholder.unparsed("max_bet", this.plugin.economyBridge().format(settings.maxBetMinor())),
            Placeholder.unparsed("min_mines", Integer.toString(settings.minMines())),
            Placeholder.unparsed("max_mines", Integer.toString(settings.maxMines()))
        ).forEach(sender::sendMessage);
    }

    private void sendHelp(final CommandSender sender) {
        this.messages.renderList("command.help").forEach(sender::sendMessage);
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            this.messages.renderList("command.help-admin").forEach(sender::sendMessage);
        }
    }

    public void reloadRuntimeResources(final MessageService messages) {
        this.messages = messages;
    }

    private void sendStats(
        final CommandSender sender,
        final String playerName,
        final CompletableFuture<PlayerStatsSnapshot> future
    ) {
        future.whenComplete((snapshot, throwable) -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("Failed to load NotMines stats for " + playerName + ".");
                return;
            }

            if (!this.plugin.isEnabled()) {
                return;
            }

            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.showStats(sender, snapshot));
        });
    }

    private void showStats(final CommandSender sender, final PlayerStatsSnapshot stats) {
        this.messages.send(sender, "stats.header", Placeholder.unparsed("player", stats.lastKnownName()));
        this.messages.renderList(
            "stats.body",
            Placeholder.unparsed("games", Long.toString(stats.gamesPlayed())),
            Placeholder.unparsed("wins", Long.toString(stats.wins())),
            Placeholder.unparsed("losses", Long.toString(stats.losses())),
            Placeholder.unparsed("win_rate", Money.formatPercent(stats.winRate())),
            Placeholder.unparsed("tiles", Long.toString(stats.tilesCleared())),
            Placeholder.unparsed("wagered", this.plugin.economyBridge().format(stats.totalWageredMinor())),
            Placeholder.unparsed("paid_out", this.plugin.economyBridge().format(stats.totalPaidMinor())),
            Placeholder.unparsed("profit", this.plugin.economyBridge().format(stats.netProfitMinor())),
            Placeholder.unparsed("best_cashout", this.plugin.economyBridge().format(stats.bestCashoutMinor()))
        ).forEach(sender::sendMessage);
    }

    private Player requirePlayer(final CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }

        this.messages.send(sender, "general.players-only");
        return null;
    }

    private static String normalizeMoneyValue(final String rawValue) {
        return rawValue.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public List<String> onTabComplete(
        final CommandSender sender,
        final Command command,
        final String alias,
        final String[] args
    ) {
        if (args.length == 1) {
            final List<String> suggestions = new ArrayList<>(List.of("cashout", "reopen", "stats", "help"));
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                suggestions.add("limits");
                suggestions.add("reload");
            }
            if (args[0].isBlank()) {
                suggestions.add("100");
            }
            return filterSuggestions(suggestions, args[0]);
        }

        if (args.length == 2 && "stats".equalsIgnoreCase(args[0])) {
            return this.plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                .toList();
        }

        if (args.length == 2 && "limits".equalsIgnoreCase(args[0]) && sender.hasPermission(ADMIN_PERMISSION)) {
            return filterSuggestions(LimitKey.ids(), args[1]);
        }

        if (args.length == 3 && "limits".equalsIgnoreCase(args[0]) && sender.hasPermission(ADMIN_PERMISSION)) {
            final LimitKey key = LimitKey.fromInput(args[1]);
            if (key == null) {
                return List.of();
            }

            final PluginSettings settings = this.plugin.settings();
            final List<String> suggestions = switch (key) {
                case MIN_BET -> List.of(this.plugin.getConfig().getString(key.path(), "100"), "1k", "10k");
                case MAX_BET -> List.of(this.plugin.getConfig().getString(key.path(), "1m"), "1m", "5m");
                case MIN_MINES -> List.of(Integer.toString(settings.minMines()), "1", "3");
                case MAX_MINES -> List.of(Integer.toString(settings.maxMines()), "12", "24");
            };
            return filterSuggestions(suggestions, args[2]);
        }

        if (args.length == 2 && !isSubcommand(args[0], sender)) {
            return filterSuggestions(List.of("3", "5", "8", "12"), args[1]);
        }

        return List.of();
    }

    private static List<String> filterSuggestions(final List<String> suggestions, final String input) {
        final String normalizedInput = input.toLowerCase(Locale.ROOT);
        return suggestions.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalizedInput))
            .distinct()
            .toList();
    }

    private static boolean isSubcommand(final String value, final CommandSender sender) {
        final String normalized = value.toLowerCase(Locale.ROOT);
        if (List.of("cashout", "reopen", "stats", "help").contains(normalized)) {
            return true;
        }
        return List.of("limits", "reload").contains(normalized) && sender.hasPermission(ADMIN_PERMISSION);
    }

    private enum LimitKey {
        MIN_BET("min-bet", "limits.min-bet", true),
        MAX_BET("max-bet", "limits.max-bet", true),
        MIN_MINES("min-mines", "limits.min-mines", false),
        MAX_MINES("max-mines", "limits.max-mines", false);

        private final String id;
        private final String path;
        private final boolean betLimit;

        LimitKey(final String id, final String path, final boolean betLimit) {
            this.id = id;
            this.path = path;
            this.betLimit = betLimit;
        }

        public String id() {
            return this.id;
        }

        public String path() {
            return this.path;
        }

        public boolean betLimit() {
            return this.betLimit;
        }

        public static LimitKey fromInput(final String input) {
            final String normalized = input.toLowerCase(Locale.ROOT).replace("_", "-");
            final String compact = normalized.replace("-", "");
            for (LimitKey value : values()) {
                if (value.id.equals(normalized) || value.id.replace("-", "").equals(compact)) {
                    return value;
                }
            }
            return null;
        }

        public static List<String> ids() {
            return List.of(MIN_BET.id, MAX_BET.id, MIN_MINES.id, MAX_MINES.id);
        }
    }
}
