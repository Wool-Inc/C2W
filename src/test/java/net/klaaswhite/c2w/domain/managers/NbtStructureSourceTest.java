package net.klaaswhite.c2w.domain.managers;

import net.klaaswhite.c2w.adapter.minecraft.BossBars;
import net.klaaswhite.c2w.adapter.minecraft.BossBar;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Players;
import net.klaaswhite.c2w.adapter.minecraft.Server;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager.Structures;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager.Worlds;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.Mirror;
import net.klaaswhite.c2w.domain.model.StructureData;
import net.klaaswhite.c2w.domain.model.StructureRotation;
import net.klaaswhite.c2w.domain.ops.FakeFileSystemOps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("NbtStructureSource")
class NbtStructureSourceTest {

    private MinecraftManager mc;
    private Structures structures;
    private FakeFileSystemOps fs;

    @BeforeEach
    void setUp() {
        mc = mock(MinecraftManager.class);
        structures = mock(Structures.class);
        fs = new FakeFileSystemOps();

        var bossBars = mock(BossBars.class);
        var bossBar = mock(BossBar.class);
        var server = mock(Server.class);
        var players = mock(Players.class);
        var worlds = mock(Worlds.class);

        when(mc.structures()).thenReturn(structures);
        when(mc.bossBars()).thenReturn(bossBars);
        when(bossBars.createBossBar(anyString(), any(), any())).thenReturn(bossBar);
        when(mc.server()).thenReturn(server);
        when(mc.players()).thenReturn(players);
        when(mc.worlds()).thenReturn(worlds);
    }

    private NbtStructureSource createSource() {
        var dataFolder = fs.createDir("/plugins/c2w");
        return new NbtStructureSource(dataFolder, mc, typeName -> null, fs);
    }

    @Test
    @DisplayName("discover returns empty when structures directory does not exist")
    void discoverEmptyWhenDirMissing() {
        var dataFolder = fs.createDir("/plugins/c2w");
        // Don't create structures subdir
        var source = new NbtStructureSource(dataFolder, mc, typeName -> null, fs);
        var result = source.discover();
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("discover returns empty when structures directory is empty")
    void discoverEmptyWhenDirEmpty() {
        fs.createDir("/plugins/c2w/structures");
        var source = createSource();
        var result = source.discover();
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("discover returns StructureData for each .nbt file found")
    void discoverWithNbtFiles() throws IOException {
        fs.createDir("/plugins/c2w/structures/dungeon/instances");
        fs.createFile("/plugins/c2w/structures/dungeon/instances/test_1.nbt", new byte[0]);
        fs.createFile("/plugins/c2w/structures/dungeon/instances/test_2.nbt", new byte[0]);

        when(structures.loadStructure(any(File.class))).thenReturn("struct-id");

        var source = createSource();
        var result = source.discover();

        assertEquals(2, result.size());
        result.sort(java.util.Comparator.comparing(StructureData::getId));
        assertEquals("dungeon", result.get(0).getTypeName());
        assertEquals("test_1", result.get(0).getId());
        assertEquals("dungeon", result.get(1).getTypeName());
        assertEquals("test_2", result.get(1).getId());
    }

    @Test
    @DisplayName("discover groups structures by type subdirectory")
    void discoverMultipleTypes() throws IOException {
        fs.createDir("/plugins/c2w/structures/dungeon/instances");
        fs.createFile("/plugins/c2w/structures/dungeon/instances/d1.nbt", new byte[0]);
        fs.createDir("/plugins/c2w/structures/spawn/instances");
        fs.createFile("/plugins/c2w/structures/spawn/instances/s1.nbt", new byte[0]);

        when(structures.loadStructure(any(File.class))).thenReturn("struct-id");

        var source = createSource();
        var result = source.discover();

        assertEquals(2, result.size());
        var typeNames = result.stream().map(StructureData::getTypeName).toList();
        assertTrue(typeNames.contains("dungeon"));
        assertTrue(typeNames.contains("spawn"));
    }

    @Test
    @DisplayName("discover skips non-.nbt files")
    void discoverSkipsNonNbtFiles() throws IOException {
        fs.createDir("/plugins/c2w/structures/dungeon/instances");
        fs.createFile("/plugins/c2w/structures/dungeon/instances/notes.txt", "text");
        fs.createFile("/plugins/c2w/structures/dungeon/instances/structure.nbt", new byte[0]);

        when(structures.loadStructure(any(File.class))).thenReturn("struct-id");

        var source = createSource();
        var result = source.discover();

        assertEquals(1, result.size());
        assertEquals("structure", result.get(0).getId());
    }

    @Test
    @DisplayName("discover skips files in structures dir (not subdirs)")
    void discoverSkipsFilesInStructuresDir() throws IOException {
        fs.createDir("/plugins/c2w/structures");
        fs.createFile("/plugins/c2w/structures/not_a_dir.nbt", new byte[0]);

        var source = createSource();
        var result = source.discover();

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("transport places structure after discover")
    void transportAfterDiscover() throws IOException {
        fs.createDir("/plugins/c2w/structures/dungeon/instances");
        fs.createFile("/plugins/c2w/structures/dungeon/instances/test_1.nbt", new byte[0]);

        when(structures.loadStructure(any(File.class))).thenReturn("struct-id");

        var source = createSource();
        var discovered = source.discover();
        assertEquals(1, discovered.size());

        var template = discovered.getFirst();
        var pos = new BlockPos(10, 20, 30);
        source.transport(template, "game_world", pos);

        verify(structures).place(eq("struct-id"), eq("game_world"), eq(pos), eq(true),
                eq(StructureRotation.NONE), eq(Mirror.NONE), eq(-1), eq(1.0f), any(Random.class));
    }

    @Test
    @DisplayName("transport lazy-loads structure when not previously discovered")
    void transportLazyLoads() throws IOException {
        fs.createDir("/plugins/c2w/structures/dungeon/instances");
        var nbtFile = fs.createFile("/plugins/c2w/structures/dungeon/instances/lazy_load.nbt", new byte[0]);

        when(structures.loadStructure(any(File.class))).thenReturn("lazy-struct-id");

        var source = createSource();
        var template = new StructureData("dungeon", "lazy_load",
                new BlockPos(0, 0, 0), new BlockPos(0, 0, 0));
        var pos = new BlockPos(5, 10, 15);
        source.transport(template, "game_world", pos);

        verify(structures).loadStructure(nbtFile);
        verify(structures).place(eq("lazy-struct-id"), eq("game_world"), eq(pos), eq(true),
                eq(StructureRotation.NONE), eq(Mirror.NONE), eq(-1), eq(1.0f), any(Random.class));
    }

    @Test
    @DisplayName("transport does nothing when nbt file does not exist")
    void transportNoFileDoesNothing() throws IOException {
        var source = createSource();
        var template = new StructureData("dungeon", "nonexistent",
                new BlockPos(0, 0, 0), new BlockPos(0, 0, 0));
        source.transport(template, "game_world", new BlockPos(0, 0, 0));

        verify(structures, never()).loadStructure(any(File.class));
        verify(structures, never()).place(anyString(), anyString(), any(BlockPos.class),
                anyBoolean(), any(), any(), anyInt(), anyFloat(), any(Random.class));
    }

    @Test
    @DisplayName("discover continues after IOException on one file")
    void discoverContinuesAfterIoException() throws IOException {
        fs.createDir("/plugins/c2w/structures/dungeon/instances");
        fs.createFile("/plugins/c2w/structures/dungeon/instances/bad.nbt", new byte[0]);
        fs.createFile("/plugins/c2w/structures/dungeon/instances/good.nbt", new byte[0]);

        when(structures.loadStructure(any(File.class))).thenAnswer(invocation -> {
            File f = invocation.getArgument(0);
            if (f.getName().equals("bad.nbt")) {
                throw new IOException("Corrupt file");
            }
            return "good-id";
        });

        var source = createSource();
        var result = source.discover();

        assertEquals(1, result.size());
        assertEquals("good", result.get(0).getId());
    }

    @Test
    @DisplayName("getNbtDimensions returns actual NBT structure size")
    void getNbtDimensionsReturnsSize() throws IOException {
        fs.createDir("/plugins/c2w/structures/dungeon/instances");
        var nbtFile = fs.createFile("/plugins/c2w/structures/dungeon/instances/test_1.nbt", new byte[0]);

        when(structures.loadStructure(nbtFile)).thenReturn("struct-id");
        when(structures.getSize("struct-id")).thenReturn(new int[]{16, 10, 16});

        var source = createSource();
        var result = source.getNbtDimensions("dungeon", "test_1");

        assertArrayEquals(new int[]{16, 10, 16}, result);
    }

    @Test
    @DisplayName("getNbtDimensions returns null for missing file")
    void getNbtDimensionsNullForMissing() {
        var source = createSource();
        var result = source.getNbtDimensions("nonexistent", "nope");
        assertNull(result);
    }

    @Test
    @DisplayName("getTypeDimensions delegates to TypeDimensionSource")
    void getTypeDimensionsDelegates() {
        var source = createSource();
        // Need to create with a non-null returning source
        var dataFolder = fs.createDir("/plugins/c2w2");
        var src = new NbtStructureSource(dataFolder, mc, typeName -> new int[]{32, 20, 32}, fs);
        var result = src.getTypeDimensions("any");
        assertArrayEquals(new int[]{32, 20, 32}, result);
    }

    @Test
    @DisplayName("getTypeDimensions returns null when source returns null")
    void getTypeDimensionsNull() {
        var source = createSource();
        var result = source.getTypeDimensions("any");
        assertNull(result);
    }

    @Test
    @DisplayName("discoverTypeNames returns type directories that contain structure.yml")
    void discoverTypeNamesReturnsValidTypes() {
        fs.createDir("/plugins/c2w/structures/dungeon");
        fs.createFile("/plugins/c2w/structures/dungeon/structure.yml", new byte[0]);
        fs.createDir("/plugins/c2w/structures/spawn");
        fs.createFile("/plugins/c2w/structures/spawn/structure.yml", new byte[0]);
        fs.createDir("/plugins/c2w/structures/empty_type");

        var source = createSource();
        var result = source.discoverTypeNames();

        assertEquals(2, result.size());
        assertTrue(result.contains("dungeon"));
        assertTrue(result.contains("spawn"));
        assertFalse(result.contains("empty_type"));
    }

    @Test
    @DisplayName("discoverTypeNames returns empty when structures dir missing")
    void discoverTypeNamesEmptyWhenDirMissing() {
        var dataFolder = fs.createDir("/plugins/c2w");
        var source = new NbtStructureSource(dataFolder, mc, typeName -> null, fs);
        var result = source.discoverTypeNames();
        assertTrue(result.isEmpty());
    }
}