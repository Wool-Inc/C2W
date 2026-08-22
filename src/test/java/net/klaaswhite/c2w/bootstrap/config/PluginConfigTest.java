package net.klaaswhite.c2w.bootstrap.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PluginConfig")
class PluginConfigTest {

    private YamlConfiguration yamlConfig;

    @BeforeEach
    void setUp() {
        yamlConfig = new YamlConfiguration();
    }

    private PluginConfig config() {
        return new PluginConfig(yamlConfig);
    }

    private void set(String path, Object value) {
        yamlConfig.set(path, value);
    }

    // --- getGameOverTarget ---

    @Test
    @DisplayName("getGameOverTarget defaults to 'draft'")
    void gameOverTargetDefault() {
        assertEquals("draft", config().getGameOverTarget());
    }

    @Test
    @DisplayName("getGameOverTarget returns the configured value")
    void gameOverTargetCustom() {
        set("gameOverTarget", "lobby");
        assertEquals("lobby", config().getGameOverTarget());
    }

    // --- isLobbyAutoJoin ---

    @Test
    @DisplayName("isLobbyAutoJoin defaults to true")
    void lobbyAutoJoinDefault() {
        assertTrue(config().isLobbyAutoJoin());
    }

    @Test
    @DisplayName("isLobbyAutoJoin returns the configured value")
    void lobbyAutoJoinCustom() {
        set("lobbyAutoJoin", false);
        assertFalse(config().isLobbyAutoJoin());
    }

    // --- isOverrideOnStartup ---

    @Test
    @DisplayName("isOverrideOnStartup defaults to false")
    void overrideOnStartupDefault() {
        assertFalse(config().isOverrideOnStartup());
    }

    @Test
    @DisplayName("isOverrideOnStartup returns the configured value")
    void overrideOnStartupCustom() {
        set("overrideOnStartup", true);
        assertTrue(config().isOverrideOnStartup());
    }
}