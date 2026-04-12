package io.github.notzorba.notmines.command;

import io.github.notzorba.notmines.NotMinesPlugin;
import io.github.notzorba.notmines.game.GameManager;
import io.github.notzorba.notmines.stats.PlayerStatsSnapshot;
import io.github.notzorba.notmines.stats.StatsService;
import io.github.notzorba.notmines.util.MessageService;
import io.github.notzorba.notmines.util.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class MinesCommand implements CommandExecutor, TabCompleter {
    private final NotMinesPlugin plugin;
    private final GameManager gameManager;
    private final StatsService statsService;
    private final MessageService messages;

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
            this.messages.renderList("command.help").forEach(component -> sender.sendMessage(component));
            return true;
        }

        final String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "cashout" -> {
                final Player player = requirePlayer(sender);
                if (player != null) {
                    this.gameManager.cashOut(player);
                }
                return true;
            }
            case "reopen" -> {
                final Player player = requirePlayer(sender);
                if (player != null) {
                    this.gameManager.reopen(player);
                }
                return true;
            }
            case "stats" -> {
                this.handleStats(sender, args);
                return true;
            }
            case "help" -> {
                this.messages.renderList("command.help").forEach(component -> sender.sendMessage(component));
                return true;
            }
            default -> {
                if (args.length != 2) {
                    this.messages.renderList("command.help").forEach(component -> sender.sendMessage(component));
                    return true;
                }

                final Player player = requirePlayer(sender);
                if (player != null) {
                    this.gameManager.startGame(player, args[0], args[1]);
                }
                return true;
            }
        }
    }

    private void handleStats(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            final Player player = requirePlayer(sender);
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
        ).forEach(component -> sender.sendMessage(component));
    }

    private Player requirePlayer(final CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }

        this.messages.send(sender, "general.players-only");
        return null;
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
            if (args[0].isBlank()) {
                suggestions.add("100");
            }
            return suggestions.stream().filter(value -> value.startsWith(args[0].toLowerCase())).toList();
        }

        if (args.length == 2 && "stats".equalsIgnoreCase(args[0])) {
            return this.plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                .toList();
        }

        if (args.length == 2 && !List.of("cashout", "reopen", "stats", "help").contains(args[0].toLowerCase())) {
            return List.of("3", "5", "8", "12");
        }

        return List.of();
    }
}
