package net.klaaswhite.c2w.adapter.minecraft;

import net.klaaswhite.c2w.domain.model.BlockPos;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Bukkit operations needed by the trial-spawner runtime. */
public interface TrialSpawners {

    boolean isTrialSpawner(String worldName, BlockPos pos);

    boolean isVault(String worldName, BlockPos pos);

    /** Configure a placed trial spawner from the configured spawn-egg source. */
    void configureSpawner(String worldName, BlockPos pos, List<ItemStack> spawnEggs);

    /** Cancel the native spawn and start a deterministic shuffled egg queue. */
    int startExactTrial(String worldName, BlockPos pos, String trialId);

    /** Configure a paired vault with the items available from its loot source. */
    void configureVault(String worldName, BlockPos pos, List<ItemStack> loot);

    /** Consume one trial key and give one configured vault reward. */
    boolean claimVault(Player player, String worldName, BlockPos pos);

    /** Remove cached trial-vault reward tables during a game reset. */
    void clearConfiguredTrialData();

    void tagEntity(Entity entity, String trialId);

    @Nullable String getEntityTag(Entity entity);

    /** Give exactly one trial key if the player's inventory can accept it. */
    boolean giveTrialKey(Player player);
}