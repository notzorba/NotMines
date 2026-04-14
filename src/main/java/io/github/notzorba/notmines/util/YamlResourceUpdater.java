package io.github.notzorba.notmines.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlResourceUpdater {
    private YamlResourceUpdater() {
    }

    public static boolean sync(final JavaPlugin plugin, final String resourcePath) {
        final File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
            return true;
        }

        final YamlConfiguration liveConfig = loadFile(file);
        final YamlConfiguration bundledConfig = loadBundled(plugin, resourcePath);

        boolean changed = mergeRootComments(liveConfig, bundledConfig);
        changed |= mergeSection(liveConfig, bundledConfig, bundledConfig, "");
        if (!changed) {
            return false;
        }

        try {
            liveConfig.save(file);
            return true;
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to save merged YAML resource " + resourcePath + ".", exception);
        }
    }

    private static YamlConfiguration loadFile(final File file) {
        try {
            final YamlConfiguration configuration = new YamlConfiguration();
            configuration.options().parseComments(true);
            if (file.exists()) {
                configuration.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            }
            return configuration;
        } catch (final IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Failed to load YAML file " + file.getAbsolutePath() + ".", exception);
        }
    }

    private static YamlConfiguration loadBundled(final JavaPlugin plugin, final String resourcePath) {
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Bundled resource " + resourcePath + " is missing.");
            }

            final YamlConfiguration configuration = new YamlConfiguration();
            configuration.options().parseComments(true);
            configuration.loadFromString(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            return configuration;
        } catch (final IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Failed to load bundled YAML resource " + resourcePath + ".", exception);
        }
    }

    private static boolean mergeRootComments(final YamlConfiguration liveConfig, final YamlConfiguration bundledConfig) {
        boolean changed = false;
        if (liveConfig.options().getHeader().isEmpty() && !bundledConfig.options().getHeader().isEmpty()) {
            liveConfig.options().setHeader(bundledConfig.options().getHeader());
            changed = true;
        }
        if (liveConfig.options().getFooter().isEmpty() && !bundledConfig.options().getFooter().isEmpty()) {
            liveConfig.options().setFooter(bundledConfig.options().getFooter());
            changed = true;
        }
        return changed;
    }

    private static boolean mergeSection(
        final YamlConfiguration liveConfig,
        final YamlConfiguration bundledConfig,
        final ConfigurationSection bundledSection,
        final String parentPath
    ) {
        boolean changed = false;
        for (String key : bundledSection.getKeys(false)) {
            final String path = parentPath.isEmpty() ? key : parentPath + "." + key;
            if (bundledSection.isConfigurationSection(key)) {
                if (!liveConfig.contains(path)) {
                    liveConfig.createSection(path);
                    changed = true;
                } else if (!liveConfig.isConfigurationSection(path)) {
                    continue;
                }

                changed |= mergePathComments(liveConfig, bundledConfig, path);
                final ConfigurationSection childSection = bundledSection.getConfigurationSection(key);
                if (childSection != null) {
                    changed |= mergeSection(liveConfig, bundledConfig, childSection, path);
                }
                continue;
            }

            if (!liveConfig.contains(path)) {
                liveConfig.set(path, bundledSection.get(key));
                changed = true;
            }

            changed |= mergePathComments(liveConfig, bundledConfig, path);
        }

        return changed;
    }

    private static boolean mergePathComments(
        final YamlConfiguration liveConfig,
        final YamlConfiguration bundledConfig,
        final String path
    ) {
        boolean changed = false;
        if (liveConfig.getComments(path).isEmpty() && !bundledConfig.getComments(path).isEmpty()) {
            liveConfig.setComments(path, bundledConfig.getComments(path));
            changed = true;
        }
        if (liveConfig.getInlineComments(path).isEmpty() && !bundledConfig.getInlineComments(path).isEmpty()) {
            liveConfig.setInlineComments(path, bundledConfig.getInlineComments(path));
            changed = true;
        }
        return changed;
    }
}
