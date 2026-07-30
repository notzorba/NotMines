package io.github.notzorba.notmines.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.notzorba.notmines.gui.GuiConfig;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigurationResourcesTest {
    @Test
    void allBundledYamlFilesParse() {
        for (String resource : new String[] {"config.yml", "messages.yml", "gui.yml", "plugin.yml"}) {
            assertDoesNotThrow(() -> load(resource), resource + " should contain valid YAML");
        }
    }

    @Test
    void managedResourcesExposeCurrentConfigVersion() throws Exception {
        for (String resource : new String[] {"config.yml", "messages.yml", "gui.yml"}) {
            assertEquals(1, load(resource).getInt("config-version"), resource);
        }
    }

    @Test
    void oldestSupportedApiCanLoadEveryConfiguredGuiMaterialAndSound() throws URISyntaxException {
        final var resource = Objects.requireNonNull(
            ConfigurationResourcesTest.class.getClassLoader().getResource("gui.yml"),
            "gui.yml"
        );
        assertDoesNotThrow(() -> GuiConfig.load(Path.of(resource.toURI()).toFile()));
    }

    @Test
    void newGameplayAndAnnouncementDefaultsLoad() throws Exception {
        final PluginSettings settings = PluginSettings.load(load("config.yml"), 2);
        assertTrue(settings.metricsEnabled());
        assertTrue(settings.safePickMessages());
        assertTrue(settings.boardCloseMessages());
        assertTrue(settings.announcementConsoleEnabled());
        assertEquals(0L, settings.announcementMinPayoutMinor());
    }

    @Test
    void pluginMetadataUsesShortPublicIdentifiersAndOldestApiBaseline() throws Exception {
        final YamlConfiguration plugin = load("plugin.yml");
        assertEquals("1.20", plugin.getString("api-version"));
        assertEquals("nmines.use", plugin.getString("commands.mines.permission"));
        assertTrue(plugin.contains("permissions.nmines.admin"));
    }

    private static YamlConfiguration load(final String resource) throws Exception {
        try (InputStream input = Objects.requireNonNull(
            ConfigurationResourcesTest.class.getClassLoader().getResourceAsStream(resource),
            resource
        )) {
            final YamlConfiguration configuration = new YamlConfiguration();
            configuration.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            return configuration;
        }
    }
}
