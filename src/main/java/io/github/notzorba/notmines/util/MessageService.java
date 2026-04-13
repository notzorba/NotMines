package io.github.notzorba.notmines.util;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageService {
    private final MiniMessage miniMessage;
    private final YamlConfiguration messagesConfig;
    private final Component prefix;

    private MessageService(final YamlConfiguration messagesConfig) {
        this.miniMessage = MiniMessage.miniMessage();
        this.messagesConfig = messagesConfig;
        this.prefix = this.miniMessage.deserialize(
            messagesConfig.getString("prefix", "<#F97352><bold>Mines</bold></#F97352> <dark_gray>▶</dark_gray>")
        );
    }

    public static MessageService create(final JavaPlugin plugin) {
        final File file = new File(plugin.getDataFolder(), "messages.yml");
        return new MessageService(YamlConfiguration.loadConfiguration(file));
    }

    public void send(final CommandSender sender, final String path, final TagResolver... resolvers) {
        sender.sendMessage(this.render(path, resolvers));
    }

    public Component render(final String path, final TagResolver... resolvers) {
        final String message = this.messagesConfig.getString(path, "<red>Missing message: " + path + "</red>");
        return this.renderRaw(message, resolvers);
    }

    public List<Component> renderList(final String path, final TagResolver... resolvers) {
        final List<String> lines = this.messagesConfig.getStringList(path);
        return lines.stream().map(line -> this.renderRaw(line, resolvers)).toList();
    }

    public Component renderRaw(final String rawMessage, final TagResolver... resolvers) {
        return this.miniMessage.deserialize(rawMessage, this.withPrefix(resolvers));
    }

    private TagResolver[] withPrefix(final TagResolver... resolvers) {
        final TagResolver[] allResolvers = Arrays.copyOf(resolvers, resolvers.length + 1);
        allResolvers[resolvers.length] = Placeholder.component("prefix", this.prefix);
        return allResolvers;
    }
}
