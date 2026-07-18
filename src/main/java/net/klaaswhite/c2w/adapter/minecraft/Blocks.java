package net.klaaswhite.c2w.adapter.minecraft;

import net.klaaswhite.c2w.domain.model.BlockPos;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/**
 * Block-level world operations.
 * <p>
 * Abstracts getting and setting block types and block data in a specific world.
 */
public interface Blocks {

    /** Get the material type of a block at the given position in a world. */
    Material getBlockType(String worldName, BlockPos pos);

    /** Set the block at the given position in a world to the specified material. */
    void setBlock(String worldName, BlockPos pos, Material material);

    /** Get the block data at the given position in a world. */
    BlockData getBlockData(String worldName, BlockPos pos);
}