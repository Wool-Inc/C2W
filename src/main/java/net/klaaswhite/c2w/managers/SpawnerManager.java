package net.klaaswhite.c2w.managers;

import net.klaaswhite.c2w.commands.base.BaseCommand;
import net.klaaswhite.c2w.commands.base.CommandInput;
import net.klaaswhite.c2w.events.StartGameEvent;
import net.klaaswhite.c2w.interfaces.IManager;
import org.bukkit.Bukkit;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class SpawnerManager implements IManager {
    private final Managers managers;

    private final ConcurrentHashMap<CreatureSpawner, List<LivingEntity>> knownSpawners;

    private boolean enabled = false;

    private BukkitTask checkTimer;

    private final AtomicInteger usingQueue = new AtomicInteger(0);
    private final ArrayList<Queue<SpawnerSpawnEvent>> queues;

    private final AtomicInteger distance = new AtomicInteger(30);

    public SpawnerManager(Managers managers) {
        this.managers = managers;
        knownSpawners = new ConcurrentHashMap<>();

        queues = new ArrayList<>();
        queues.add(new ConcurrentLinkedQueue<>());
        queues.add(new ConcurrentLinkedQueue<>());

        this.managers.get(EventManager.class).getValue().registerInternalEvent(StartGameEvent.class, this::startGame);
    }

    public void startGame(StartGameEvent event){
        if (enabled) return;

        this.enabled = true;

        if (checkTimer != null){
            checkTimer.cancel();
            checkTimer = null;
        }

        checkTimer = Bukkit.getScheduler().runTaskTimer(
                this.managers.getPlugin(),
                this::onTimer,
                0,
                20
        );
    }

    private void onTimer(){
        var currentQueue = usingQueue.get();
        usingQueue.set(currentQueue == 0 ? 1 : 0);

        var queue = queues.get(currentQueue);
        for(var event : queue){
            var spawner = event.getSpawner();
            var entity = event.getEntity();

            if (!(entity instanceof LivingEntity livingEntity))
                return;

            if (knownSpawners.containsKey(spawner)){
                knownSpawners.get(spawner).add(livingEntity);
            }
            else {
                var entities = new ArrayList<LivingEntity>();
                entities.add(livingEntity);
                knownSpawners.put(spawner, entities);
            }
        }

        queue.clear();
        var distance = this.distance.get();

        knownSpawners.forEach((spawner, entities) -> {
            var world = spawner.getWorld();
            var location = spawner.getLocation().toVector();

            var players = world.getPlayers();

            var foundPlayerInRange = false;
            for (Player player : players) {
                var playerLocation = player.getLocation().toVector();

                if (location.distance(playerLocation) < distance) {
                    foundPlayerInRange = true;
                    break;
                }
            }

            if (!foundPlayerInRange){
                knownSpawners.remove(spawner);
                for(LivingEntity entity : entities){
                    entity.setHealth(0);
                }
            }
        });
    }

    public void setDistance(CommandInput input, int distance){
        this.distance.set(distance);
        input.commandSender.sendMessage("Set distance to " + distance + " blocks");
    }

    @Override
    public void close() throws Exception {
        checkTimer.cancel();
        checkTimer = null;
        queues.get(0).clear();
        queues.get(1).clear();
        knownSpawners.clear();
    }
}
