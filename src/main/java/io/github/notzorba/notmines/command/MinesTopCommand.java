package io.github.notzorba.notmines.command;

import io.github.notzorba.notmines.NotMinesPlugin;
import io.github.notzorba.notmines.leaderboard.LeaderboardManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MinesTopCommand implements CommandExecutor {
    private final NotMinesPlugin plugin;
    private final LeaderboardManager leaderboardManager;

    public MinesTopCommand(final NotMinesPlugin plugin, final LeaderboardManager leaderboardManager) {
        this.plugin = plugin;
        this.leaderboardManager = leaderboardManager;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!sender.hasPermission("notmines.use")) {
            this.plugin.messages().send(sender, "general.no-permission");
            return true;
        }

        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.players-only");
            return true;
        }

        this.leaderboardManager.openLeaderboard(player);
        return true;
    }
}
