package net.klaaswhite.c2w.domain.config;

import net.klaaswhite.c2w.domain.model.MapLayout;
import net.klaaswhite.c2w.domain.ops.FakeFileSystemOps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("YamlLayoutFileLoader")
class YamlLayoutFileLoaderTest {

    private FakeFileSystemOps fs;
    private YamlLayoutFileLoader loader;

    @BeforeEach
    void setUp() {
        fs = new FakeFileSystemOps();
        loader = new YamlLayoutFileLoader(fs);
    }

    @Test
    @DisplayName("loadLayout returns null for non-existent file")
    void nonExistentFile() {
        var file = new File("/nonexistent.yml");
        var result = loader.loadLayout(file, "test");
        assertNull(result);
    }

    @Test
    @DisplayName("loadLayout parses valid layout YAML file")
    void validYaml() {
        var file = fs.createFile("/layouts/valid_layout.yml", """
                tileWidth: 16
                tileHeight: 10
                tileDepth: 16
                origin:
                  x: 0
                  y: 0
                  z: 0
                grid: |
                  AA
                  AA
                charMap:
                  A: DUNGEON
                cellOverrides: []
                """);

        var result = loader.loadLayout(file, "test-layout");

        assertNotNull(result);
        assertEquals("test-layout", result.getName());
        assertEquals(16, result.getTileWidth());
        assertEquals(10, result.getTileHeight());
        assertEquals(16, result.getTileDepth());

        var origin = result.getOrigin();
        assertEquals(0, origin.x());
        assertEquals(0, origin.y());
        assertEquals(0, origin.z());

        assertEquals(4, result.getCells().size());
        assertEquals(2, result.getRows());
        assertEquals(2, result.getCols());
        result.getCells().forEach(cell -> assertEquals("DUNGEON", cell.typeName()));
    }

    @Test
    @DisplayName("loadLayout parses layout with spawn cells assigned to teams")
    void spawnCells() {
        var file = fs.createFile("/layouts/spawn_layout.yml", """
                tileWidth: 16
                tileHeight: 10
                tileDepth: 16
                origin:
                  x: 0
                  y: 64
                  z: 0
                grid: |
                  S.
                  .S
                charMap:
                  S: SPAWN
                  .: VOID
                cellOverrides: []
                """);

        var result = loader.loadLayout(file, "spawn-layout");

        assertNotNull(result);
        assertEquals(2, result.getCells().size());

        assertEquals("Red", result.getCells().get(0).team());
        assertEquals("Blue", result.getCells().get(1).team());
    }

    @Test
    @DisplayName("loadLayout uses defaults when optional fields are missing")
    void defaultsForMissingFields() {
        var file = fs.createFile("/layouts/minimal_layout.yml", """
                grid: |
                  A
                charMap:
                  A: DUNGEON
                """);

        var result = loader.loadLayout(file, "minimal");

        assertNotNull(result);
        assertEquals(32, result.getTileWidth());
        assertEquals(32, result.getTileHeight());
        assertEquals(32, result.getTileDepth());
        assertEquals(0, result.getOrigin().x());
        assertEquals(64, result.getOrigin().y());
        assertEquals(0, result.getOrigin().z());
        assertEquals(1, result.getCells().size());
    }

    @Test
    @DisplayName("loadLayout returns null when grid is missing")
    void missingGrid() {
        var file = fs.createFile("/layouts/no_grid.yml", """
                tileWidth: 16
                tileHeight: 16
                tileDepth: 16
                charMap:
                  A: DUNGEON
                """);

        assertNull(loader.loadLayout(file, "no-grid"));
    }

    @Test
    @DisplayName("loadLayout returns null when grid is blank")
    void blankGrid() {
        var file = fs.createFile("/layouts/blank_grid.yml", """
                tileWidth: 16
                tileHeight: 16
                tileDepth: 16
                grid: ""
                charMap:
                  A: DUNGEON
                """);

        assertNull(loader.loadLayout(file, "blank-grid"));
    }

    @Test
    @DisplayName("loadLayout skips VOID characters in grid")
    void skipsVoidCells() {
        var file = fs.createFile("/layouts/void_layout.yml", """
                tileWidth: 16
                tileHeight: 10
                tileDepth: 16
                origin:
                  x: 0
                  y: 0
                  z: 0
                grid: |
                  A.V
                  V.V
                charMap:
                  A: DUNGEON
                  .: VOID
                cellOverrides: []
                """);

        var result = loader.loadLayout(file, "void-layout");

        assertNotNull(result);
        assertEquals(1, result.getCells().size());
        assertEquals("DUNGEON", result.getCells().get(0).typeName());
    }

    @Test
    @DisplayName("loadLayout returns null for malformed YAML")
    void malformedYaml() {
        var file = fs.createFile("/layouts/malformed.yml", """
                tileWidth: 16
                tileHeight 10
                tileDepth: 16
                """);

        assertNull(loader.loadLayout(file, "malformed"));
    }

    @Test
    @DisplayName("loadLayout returns null for non-map root YAML")
    void nonMapRoot() {
        var file = fs.createFile("/layouts/string_root.yml", "just a string");

        assertNull(loader.loadLayout(file, "string-root"));
    }
}