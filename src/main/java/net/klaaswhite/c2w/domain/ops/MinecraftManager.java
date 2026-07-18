package net.klaaswhite.c2w.domain.ops;

import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.Mirror;
import net.klaaswhite.c2w.domain.model.StructureRotation;

import org.bukkit.World;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Domain port for the Minecraft access layer.
 * <p>
 * This is the domain-facing subset of the adapter {@code MinecraftManager}
 * that domain code (e.g. {@code NbtStructureSource}) is allowed to depend on.
 * It deliberately contains only the operations the domain needs, so the
 * {@code domain/} layer never imports from {@code adapter/} or {@code bootstrap/}.
 * The adapter provides the concrete implementation.
 */
public interface MinecraftManager {

    /** Structure load/save/place operations needed by the domain. */
    Structures structures();

    /** World operations needed by the domain. */
    Worlds worlds();

    /** Domain sub-port for structure operations. */
    interface Structures {
        String loadStructure(File file) throws IOException;

        void saveStructure(File file, String structureId) throws IOException;

        void place(String structureId, String worldName, BlockPos pos, boolean includeEntities,
                   StructureRotation rotation, Mirror mirror, int palette, float integrity, Random random);

        @Nullable int[] getSize(String structureId);
    }

    /** Domain sub-port for world operations. */
    interface Worlds {
        @Nullable World getWorld(String name);
    }
}
