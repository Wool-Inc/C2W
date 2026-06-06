package net.klaaswhite.c2w.worlds;

import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;

public class ManagedWorld {

    private final String name;
    private final World world;

    public ManagedWorld(String name){
        this.name = name;
        this.world = ensureWorld();

    }

    public World getWorld(){
        return this.world;
    }

    private World ensureWorld(){

        var world = Bukkit.getWorld(this.name);

        if (world == null){
            WorldCreator creator = new WorldCreator(this.name);
            creator.type(WorldType.FLAT);
            creator.generator(new VoidChunkGenerator());

            world = Bukkit.createWorld(creator);

            if (world == null)
                return null;

            Location spawn = getWorldSpawn();

            createInitialWorld(world);

            world.setSpawnLocation(spawn);
        }

        return world;
    }


    protected void createInitialWorld(World world){
        world.getBlockAt(0, 64, 0).setType(Material.BEDROCK);
        world.getBlockAt(1, 64, 0).setType(Material.BEDROCK);
        world.getBlockAt(-1, 64, 0).setType(Material.BEDROCK);
        world.getBlockAt(0, 64, 1).setType(Material.BEDROCK);
        world.getBlockAt(0, 64, -1).setType(Material.BEDROCK);
    }

    protected Location getWorldSpawn(){
        return new Location(world, 0, 65, 0);
    }

    private static class VoidChunkGenerator extends ChunkGenerator {

    }
}
