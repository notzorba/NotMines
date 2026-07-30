package io.github.notzorba.notmines;

import java.util.List;

final class StartupPresentation {
    private static final List<String> BANNER = List.of(
        " _   _ __  __ ___ _   _ _____ ____ ",
        "| \\ | |  \\/  |_ _| \\ | | ____/ ___|",
        "|  \\| | |\\/| || ||  \\| |  _| \\___ \\",
        "| |\\  | |  | || || |\\  | |___ ___) |",
        "|_| \\_|_|  |_|___|_| \\_|_____|____/ "
    );

    private StartupPresentation() {
    }

    static void log(final NotMinesPlugin plugin) {
        final String version = plugin.getDescription().getVersion();
        final String authors = plugin.getDescription().getAuthors().isEmpty()
            ? "Unknown"
            : String.join(", ", plugin.getDescription().getAuthors());
        final String placeholderStatus = plugin.placeholdersRegistered()
            ? "active (%nmines_*%)"
            : "not installed";

        plugin.getLogger().info("");
        BANNER.forEach(line -> plugin.getLogger().info(line));
        plugin.getLogger().info("NotMines v" + version + " by " + authors);
        plugin.getLogger().info(
            "Server: " + plugin.getServer().getName() + " " + plugin.getServer().getMinecraftVersion()
                + " | Compatibility: 1.20.x - 26.x | Java bytecode: 17"
        );
        plugin.getLogger().info(
            "Economy: " + plugin.economyBridge().providerName()
                + " | Storage: SQLite (async) | Config: v" + plugin.getConfig().getInt("config-version")
        );
        plugin.getLogger().info(
            "PlaceholderAPI: " + placeholderStatus
                + " | bStats: " + (plugin.metricsEnabled() ? "enabled" : "disabled")
                + " | Stats flush: " + plugin.settings().saveIntervalSeconds() + "s"
        );
        plugin.getLogger().info("Ready. Use /mines help for commands.");
        plugin.getLogger().info("");
    }
}
