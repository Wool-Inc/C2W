package net.klaaswhite.c2w.domain.managers;

import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.Mirror;
import net.klaaswhite.c2w.domain.model.StructureData;
import net.klaaswhite.c2w.domain.model.StructureRotation;
import net.klaaswhite.c2w.domain.ops.FileSystemOps;
import net.klaaswhite.c2w.domain.ops.MinecraftManager;
import net.klaaswhite.c2w.domain.ops.TypeDimensionSource;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Discovers structure instances from vanilla NBT files on disk and transports
 * them by pasting into the target world.
 *
 * <p>File layout: {@code plugins/c2w/structures/<typeName>/instances/<id>.nbt}</p>
 */
public class NbtStructureSource {

    private static final Logger log = Logger.getLogger(NbtStructureSource.class.getName());

    private final File structuresDir;
    private final MinecraftManager mc;
    private final Map<String, String> loadedStructureIds = new HashMap<>();
    private final TypeDimensionSource typeDimensionSource;
    private final FileSystemOps fs;

    public NbtStructureSource(File dataFolder, MinecraftManager mc, TypeDimensionSource typeDimensionSource, FileSystemOps fs) {
        this.structuresDir = new File(dataFolder, "structures");
        this.mc = mc;
        this.typeDimensionSource = typeDimensionSource;
        this.fs = fs;
    }

    public List<StructureData> discover() {
        var results = new ArrayList<StructureData>();
        if (!fs.isDirectory(structuresDir)) return results;

        for (var typeDir : fs.listFiles(structuresDir)) {
            if (!fs.isDirectory(typeDir)) continue;
            String typeName = typeDir.getName();

            var instancesDir = new File(typeDir, "instances");
            if (!fs.isDirectory(instancesDir)) continue;

            for (var nbtFile : fs.listFiles(instancesDir, ".nbt")) {
                String id = nbtFile.getName().replace(".nbt", "");
                String key = typeName + "/" + id;

                try {
                    var structureId = mc.structures().loadStructure(nbtFile);
                    var c1 = new BlockPos(0, 0, 0);
                    var c2 = c1;
                    var size = mc.structures().getSize(structureId);
                    if (size != null) {
                        c2 = new BlockPos(size[0] - 1, size[1] - 1, size[2] - 1);
                    }

                    var template = new StructureData(typeName, id, c1, c2);
                    results.add(template);
                    loadedStructureIds.put(key, structureId);
                } catch (IOException e) {
                    log.warning("Failed to load structure NBT: " + nbtFile.getPath());
                }
            }
        }
        return results;
    }

    /** Return all type names (directory names under structures/ that contain a structure.yml). */
    public List<String> discoverTypeNames() {
        var results = new ArrayList<String>();
        if (!fs.isDirectory(structuresDir)) return results;

        for (var typeDir : fs.listFiles(structuresDir)) {
            if (!fs.isDirectory(typeDir)) continue;
            var structureYml = new File(typeDir, "structure.yml");
            if (fs.isFile(structureYml)) {
                results.add(typeDir.getName());
            }
        }
        return results;
    }

    public void transport(StructureData template, String worldName, BlockPos pos) {
        transport(template, worldName, pos, StructureRotation.NONE);
    }

    public void transport(StructureData template, String worldName, BlockPos pos, StructureRotation rotation) {
        String key = template.getTypeName() + "/" + template.getId();
        var structureId = loadedStructureIds.get(key);

        if (structureId == null) {
            var nbtFile = new File(structuresDir, template.getTypeName() + "/instances/" + template.getId() + ".nbt");
            if (!fs.isFile(nbtFile)) return;
            try {
                structureId = mc.structures().loadStructure(nbtFile);
                loadedStructureIds.put(key, structureId);
            } catch (IOException e) {
                log.warning("Failed to load structure NBT: " + nbtFile.getPath());
                return;
            }
        }

        mc.structures().place(structureId, worldName, pos, true,
                rotation, Mirror.NONE, -1, 1.0f, new Random());
    }

    /** Get the actual dimensions of a structure NBT file. */
    public @Nullable int[] getNbtDimensions(String typeName, String id) {
        var nbtFile = new File(structuresDir, typeName + "/instances/" + id + ".nbt");
        if (!fs.isFile(nbtFile)) return null;
        try {
            String structureId = mc.structures().loadStructure(nbtFile);
            return mc.structures().getSize(structureId);
        } catch (IOException e) {
            return null;
        }
    }

    /** Get the configured dimensions for a type from the dimension source. */
    public @Nullable int[] getTypeDimensions(String typeName) {
        return typeDimensionSource.getDimensions(typeName);
    }
}
