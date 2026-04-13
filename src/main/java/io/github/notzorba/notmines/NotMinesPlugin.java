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
    private MinesCommand minesCommand;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.ensureResource("messages.yml");
        this.ensureResource("gui.yml");

        try {
            this.messages = this.loadMessages();
            this.guiConfig = this.loadGuiConfig();
        } catch (final IllegalArgumentException exception) {
            this.getLogger().severe("Failed to load NotMines resources: " + exception.getMessage());
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.economyBridge = new EconomyBridge(this);
        if (!this.economyBridge.refresh()) {
            this.getLogger().severe("Vault or a compatible economy provider was not found.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.reloadSettings();
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

    public void reloadSettings() {
        this.settings = this.loadSettings();
    }

    public void updateLimit(final String path, final Object value) {
        this.getConfig().set(path, value);
        this.saveConfig();
        this.reloadSettings();
    }

    private void registerCommand() {
        final PluginCommand minesCommand = Objects.requireNonNull(
            this.getCommand("mines"),
            "The /mines command is missing from plugin.yml."
        );

        this.minesCommand = new MinesCommand(this, this.gameManager, this.statsService, this.messages);
        minesCommand.setExecutor(this.minesCommand);
        minesCommand.setTabCompleter(this.minesCommand);
    }

    private void ensureResource(final String resourcePath) {
        final File file = new File(this.getDataFolder(), resourcePath);
        if (!file.exists()) {
            this.saveResource(resourcePath, false);
        }
    }

    public void reloadRuntimeResources() {
        this.reloadConfig();

        final MessageService reloadedMessages = this.loadMessages();
        final GuiConfig reloadedGuiConfig = this.loadGuiConfig();
        final PluginSettings reloadedSettings = this.loadSettings();

        this.messages = reloadedMessages;
        this.guiConfig = reloadedGuiConfig;
        this.settings = reloadedSettings;

        if (this.gameManager != null) {
            this.gameManager.reloadRuntimeResources(this.messages, this.guiConfig, this.settings);
        }

        if (this.minesCommand != null) {
            this.minesCommand.reloadRuntimeResources(this.messages);
        }
    }

    private MessageService loadMessages() {
        return MessageService.create(this);
    }

    private GuiConfig loadGuiConfig() {
        return GuiConfig.load(new File(this.getDataFolder(), "gui.yml"));
    }

    private PluginSettings loadSettings() {
        return PluginSettings.load(this.getConfig(), this.economyBridge.currencyScale());
    }
}
