package io.github.notzorba.notmines;

import io.github.notzorba.notmines.command.MinesCommand;
import io.github.notzorba.notmines.config.PluginSettings;
import io.github.notzorba.notmines.economy.EconomyBridge;
import io.github.notzorba.notmines.game.GameManager;
import io.github.notzorba.notmines.gui.GuiConfig;
import io.github.notzorba.notmines.listener.MinesListener;
import io.github.notzorba.notmines.stats.StatsService;
import io.github.notzorba.notmines.util.MessageService;
import java.io.File;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class NotMinesPlugin extends JavaPlugin {
    private PluginSettings settings;
    private MessageService messages;
    private EconomyBridge economyBridge;
    private StatsService statsService;
    private GameManager gameManager;
    private GuiConfig guiConfig;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.ensureResource("messages.yml");
        this.ensureResource("gui.yml");

        this.messages = MessageService.create(this);
        try {
            this.guiConfig = GuiConfig.load(new File(this.getDataFolder(), "gui.yml"));
        } catch (final IllegalArgumentException exception) {
            this.getLogger().severe("Failed to load gui.yml: " + exception.getMessage());
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.economyBridge = new EconomyBridge(this);
        if (!this.economyBridge.refresh()) {
            this.getLogger().severe("Vault or a compatible economy provider was not found.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.settings = PluginSettings.load(this.getConfig(), this.economyBridge.currencyScale());
        this.statsService = new StatsService(this, this.settings);
        this.gameManager = new GameManager(this, this.settings, this.messages, this.guiConfig, this.economyBridge, this.statsService);

        this.registerCommand();
        this.getServer().getPluginManager().registerEvents(new MinesListener(this.gameManager), this);
        this.statsService.initialize();

        this.getLogger().info("NotMines enabled with async stats persistence and Vault economy support.");
    }

    @Override
    public void onDisable() {
        if (this.gameManager != null) {
            this.gameManager.shutdown();
        }

        if (this.statsService != null) {
            this.statsService.shutdown();
        }
    }

    public PluginSettings settings() {
        return this.settings;
    }

    public MessageService messages() {
        return this.messages;
    }

    public EconomyBridge economyBridge() {
        return this.economyBridge;
    }

    public GuiConfig guiConfig() {
        return this.guiConfig;
    }

    public StatsService statsService() {
        return this.statsService;
    }

    public GameManager gameManager() {
        return this.gameManager;
    }

    private void registerCommand() {
        final PluginCommand minesCommand = Objects.requireNonNull(
            this.getCommand("mines"),
            "The /mines command is missing from plugin.yml."
        );

        final MinesCommand executor = new MinesCommand(this, this.gameManager, this.statsService, this.messages);
        minesCommand.setExecutor(executor);
        minesCommand.setTabCompleter(executor);
    }

    private void ensureResource(final String resourcePath) {
        final File file = new File(this.getDataFolder(), resourcePath);
        if (!file.exists()) {
            this.saveResource(resourcePath, false);
        }
    }
}
