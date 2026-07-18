package net.klaaswhite.c2w.bootstrap.config.impl;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BukkitConfigAccess")
class BukkitConfigAccessTest {

    @Mock private JavaPlugin plugin;
    private YamlConfiguration config;
    private BukkitConfigAccess access;

    @BeforeEach
    void setUp() {
        config = new YamlConfiguration();
        lenient().when(plugin.getConfig()).thenReturn(config);
        access = new BukkitConfigAccess(plugin);
    }

    @Test
    @DisplayName("getDataFolder delegates to plugin")
    void getDataFolder() {
        var folder = new File("/tmp/c2w");
        when(plugin.getDataFolder()).thenReturn(folder);
        assertEquals(folder, access.getDataFolder());
    }

    @Test
    @DisplayName("getString / getInt / getBoolean / getDouble delegate to config")
    void getters() {
        config.set("name", "castle");
        config.set("count", 7);
        config.set("flag", true);
        config.set("ratio", 1.5);

        assertEquals("castle", access.getString("name"));
        assertEquals(7, access.getInt("count"));
        assertTrue(access.getBoolean("flag"));
        assertEquals(1.5, access.getDouble("ratio"));
    }

    @Test
    @DisplayName("get returns the raw object")
    void get() {
        config.set("name", "castle");
        assertEquals("castle", access.get("name"));
        assertNull(access.get("missing"));
    }

    @Test
    @DisplayName("set and contains reflect the underlying config")
    void setAndContains() {
        assertFalse(access.contains("x"));
        access.set("x", 42);
        assertTrue(access.contains("x"));
        assertEquals(42, access.getInt("x"));
    }

    @Test
    @DisplayName("getKeys returns child keys of a section")
    void getKeys() {
        config.set("types.castle", 1);
        config.set("types.tower", 2);
        List<String> keys = access.getKeys("types");
        assertEquals(2, keys.size());
        assertTrue(keys.contains("castle"));
        assertTrue(keys.contains("tower"));
    }

    @Test
    @DisplayName("getKeys returns empty list for missing section")
    void getKeysMissing() {
        assertTrue(access.getKeys("nope").isEmpty());
    }

    @Test
    @DisplayName("save and saveResource delegate to plugin")
    void saveDelegates() {
        access.save();
        verify(plugin).saveConfig();

        access.saveResource("config.yml", false);
        verify(plugin).saveResource("config.yml", false);
    }
}
