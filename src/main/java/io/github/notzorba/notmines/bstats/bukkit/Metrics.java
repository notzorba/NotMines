/*
 * Vendored from bStats Metrics 3.2.1 under the MIT License.
 */
package io.github.notzorba.notmines.bstats.bukkit;

import io.github.notzorba.notmines.bstats.MetricsBase;
import io.github.notzorba.notmines.bstats.charts.CustomChart;
import io.github.notzorba.notmines.bstats.json.JsonObjectBuilder;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class Metrics {
    private final Plugin plugin;
    private final MetricsBase metricsBase;

    public Metrics(final Plugin plugin, final int serviceId) {
        this.plugin = plugin;

        final File bStatsFolder = new File(plugin.getDataFolder().getParentFile(), "bStats");
        final File configFile = new File(bStatsFolder, "config.yml");
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        if (!config.isSet("serverUuid")) {
            config.addDefault("enabled", true);
            config.addDefault("serverUuid", UUID.randomUUID().toString());
            config.addDefault("logFailedRequests", false);
            config.addDefault("logSentData", false);
            config.addDefault("logResponseStatusText", false);
            config.options().header(
                "bStats (https://bStats.org) collects some basic information for plugin authors, like how\n"
                    + "many people use their plugin and their total player count. It's recommended to keep bStats\n"
                    + "enabled, but if you're not comfortable with this, you can turn this setting off. There is no\n"
                    + "performance penalty associated with having metrics enabled, and data sent to bStats is fully\n"
                    + "anonymous.\n"
                    + "Learn more here: https://bstats.org/docs/server-owners"
            ).copyDefaults(true);
            try {
                config.save(configFile);
            } catch (final IOException ignored) {
            }
        }

        final boolean enabled = config.getBoolean("enabled", true);
        final String serverUuid = config.getString("serverUuid");
        final boolean logErrors = config.getBoolean("logFailedRequests", false);
        final boolean logSentData = config.getBoolean("logSentData", false);
        final boolean logResponseStatusText = config.getBoolean("logResponseStatusText", false);

        boolean isFolia = false;
        try {
            isFolia = Class.forName("io.papermc.paper.threadedregions.RegionizedServer") != null;
        } catch (final Exception ignored) {
        }

        this.metricsBase = new MetricsBase(
            "bukkit",
            serverUuid,
            serviceId,
            enabled,
            this::appendPlatformData,
            this::appendServiceData,
            isFolia ? null : submitDataTask -> Bukkit.getScheduler().runTask(plugin, submitDataTask),
            plugin::isEnabled,
            (message, error) -> this.plugin.getLogger().log(Level.WARNING, message, error),
            message -> this.plugin.getLogger().log(Level.INFO, message),
            logErrors,
            logSentData,
            logResponseStatusText,
            false
        );
    }

    public void shutdown() {
        this.metricsBase.shutdown();
    }

    public void addCustomChart(final CustomChart chart) {
        this.metricsBase.addCustomChart(chart);
    }

    private void appendPlatformData(final JsonObjectBuilder builder) {
        builder.appendField("playerAmount", this.getPlayerAmount());
        builder.appendField("onlineMode", Bukkit.getOnlineMode() ? 1 : 0);
        builder.appendField("bukkitVersion", Bukkit.getVersion());
        builder.appendField("bukkitName", Bukkit.getName());
        builder.appendField("javaVersion", System.getProperty("java.version"));
        builder.appendField("osName", System.getProperty("os.name"));
        builder.appendField("osArch", System.getProperty("os.arch"));
        builder.appendField("osVersion", System.getProperty("os.version"));
        builder.appendField("coreCount", Runtime.getRuntime().availableProcessors());
    }

    private void appendServiceData(final JsonObjectBuilder builder) {
        builder.appendField("pluginVersion", this.plugin.getDescription().getVersion());
    }

    private int getPlayerAmount() {
        try {
            final Method onlinePlayersMethod = Class.forName("org.bukkit.Server").getMethod("getOnlinePlayers");
            return onlinePlayersMethod.getReturnType().equals(Collection.class)
                ? ((Collection<?>) onlinePlayersMethod.invoke(Bukkit.getServer())).size()
                : ((Player[]) onlinePlayersMethod.invoke(Bukkit.getServer())).length;
        } catch (final Exception exception) {
            return Bukkit.getOnlinePlayers().size();
        }
    }
}
