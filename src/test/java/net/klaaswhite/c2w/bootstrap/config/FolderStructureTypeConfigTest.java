package net.klaaswhite.c2w.bootstrap.config;

import net.klaaswhite.c2w.integration.TempDirConfigAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FolderStructureTypeConfigTest {

    @TempDir
    Path dataFolder;

    @Test
    void discoversGeneralFromLegacySpecialWorldFiles() throws Exception {
        Path structures = Files.createDirectories(dataFolder.resolve("structures"));
        Files.writeString(structures.resolve("lobby.nbt"), "legacy lobby");
        Files.writeString(structures.resolve("draft.nbt"), "legacy draft");

        var config = new FolderStructureTypeConfig(new TempDirConfigAccess(dataFolder.toFile()));

        assertTrue(config.getTypeNames().contains("general"));
        assertTrue(config.getInstanceIds("general").contains("lobby"));
        assertTrue(config.getInstanceIds("general").contains("draft"));
    }

    @Test
    void discoversGeneralFromCanonicalInstances() throws Exception {
        Path instances = Files.createDirectories(dataFolder.resolve("structures/general/instances"));
        Files.writeString(instances.resolve("lobby.nbt"), "canonical lobby");

        var config = new FolderStructureTypeConfig(new TempDirConfigAccess(dataFolder.toFile()));

        assertTrue(config.getTypeNames().contains("general"));
        assertTrue(config.getInstanceIds("general").contains("lobby"));
    }
}