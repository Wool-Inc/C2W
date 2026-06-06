package net.klaaswhite.c2w.classes;

import net.klaaswhite.c2w.events.WoolCapturedEvent;
import net.klaaswhite.c2w.interfaces.ICarriable;
import net.klaaswhite.c2w.managers.*;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Wool implements ICarriable, AutoCloseable {

    @Nullable
    public static Wool Create(Managers managers, World world, Location location, KnownMarkers marker){
        Material material;
        BarColor barColor;
        KnownMarkers capMarker;

        switch(marker){
            case WOOL_RED -> {
                material = Material.RED_WOOL;
                barColor = BarColor.RED;
                capMarker = KnownMarkers.CAP_RED;
            }
            case WOOL_GREEN -> {
                material = Material.GREEN_WOOL;
                barColor = BarColor.GREEN;
                capMarker = KnownMarkers.CAP_GREEN;
            }
            case WOOL_BLUE -> {
                material = Material.BLUE_WOOL;
                barColor = BarColor.BLUE;
                capMarker = KnownMarkers.CAP_BLUE;
            }
            case WOOL_YELLOW -> {
                material = Material.YELLOW_WOOL;
                barColor = BarColor.YELLOW;
                capMarker = KnownMarkers.CAP_YELLOW;
            }
            default -> {
                return null;
            }
        }

        return new Wool(managers, world, marker.getName().split("-")[1], location, material, barColor, capMarker);
    }

    private final int capAmount = 20 * 60;

    private final Managers managers;
    private final MarkerManager markerManager;

    private final World world;
    private final String name;
    private final Location location;
    private final Material material;
    private final BossBar bossBar;
    private final KnownMarkers capMarker;

    private final AtomicBoolean capping;
    private final AtomicBoolean barVisible;
    private final AtomicInteger cappingModifier;
    private final AtomicInteger cappedAmount;

    private ManagedPlayer carrier;
    @Nullable private Item item;
    private BlockData block;

    public Wool(Managers managers, World world, String name, Location location, Material material, BarColor barColor, KnownMarkers capMarker){
        this.managers = managers;
        this.markerManager = this.managers.get(MarkerManager.class).getValue();
        this.world = world;
        this.name = name;
        this.location = location;
        this.material = material;
        this.capMarker = capMarker;

        this.bossBar = Bukkit.createBossBar(
                "",
                barColor,
                BarStyle.SOLID
        );
        this.barVisible = new AtomicBoolean(false);
        this.bossBar.setVisible(false);
        this.managers.get(PlayerManager.class).getValue().registerBossBar(this.bossBar);

        this.capping = new AtomicBoolean(false);
        this.cappingModifier = new AtomicInteger(0);
        this.cappedAmount = new AtomicInteger(0);
    }

    public void placeEntityInWorld(){
        item = this.world.dropItem(this.location, new ItemStack(this.material), (item) -> {
            item.setVelocity(new Vector(0, 0, 0));
            item.setGravity(false);
        });
        item.setPersistent(true);
        this.managers.get(EntityManager.class).getValue().addItemPickedUpEventListener(item, this::pickup);
    }

    public void removeEntityFromWorld(){
        if (this.item == null)
            return;

        this.item.remove();
        this.managers.get(EntityManager.class).getValue().removeItemPickedUpEventListener(item, this::pickup);
        this.item = null;
    }

    public void pickup(EntityPickupItemEvent event){
        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player player)){
            return;
        }

        var managedPlayer = this.managers.get(PlayerManager.class).getValue().getPlayer(player);
        if (managedPlayer == null)
            return;

        if (!managedPlayer.tryAddCarriable(this))
            return;

        removeEntityFromWorld();
        carrier = managedPlayer;
        this.managers.getPlugin().getServer().broadcastMessage("Wool '" + name + "' has been picked up by '" + carrier.getPlayer().getDisplayName() + "'!");
        this.managers.get(PlayerManager.class).getValue().addPlayerDeathListener(carrier, this::playerDeath);
        player.getInventory().setHelmet(new ItemStack(this.material));
        this.cappingModifier.set(20);
    }

    public void playerDeath(ManagedPlayer player){
        if (player != carrier || carrier == null) return;

        removeFromCarrier();

        this.managers.getPlugin().getServer().broadcastMessage("Wool '" + name + "' has been dropped!");

        this.capping.set(false);
        placeEntityInWorld();
        this.managers.get(BoundaryManager.class).getValue().removeWoolFromCapping(this);
        this.cappedAmount.set(0);
        this.setBossBar();
        WoolTimer.unregisterWoolForTiming(this);
    }

    private void removeFromCarrier(){
        if (carrier == null) return;

        carrier.removeCarry();
        carrier.getPlayer().getInventory().setHelmet(null);
        carrier = null;
    }

    public void capture(){
        capWool();
    }

    public void setCapping(boolean capping){
        var current = this.capping.get();

        if (current == capping)
            return;

        if (capping){
            WoolTimer.registerWoolForTiming(this.managers, this);
        }
        this.capping.set(capping);
    }

    public ManagedPlayer getCarrier(){
        return this.carrier;
    }

    public void setCappingModifier(int cappingMod){
        this.cappingModifier.set(cappingMod);
    }

    private void setBossBar(){
        var current = this.cappedAmount.get();

        if (current <= 0){
            this.bossBar.setProgress(0);
            this.bossBar.setVisible(false);
            this.barVisible.set(false);
            return;
        }

        if (!this.barVisible.get()){
            this.bossBar.setVisible(true);
            this.barVisible.set(true);
        }

        double progress = (double)current / (double)capAmount;
        var progressBar = Math.min(progress, 100);
        this.bossBar.setProgress(progressBar);
    }

    public void ensureEntity(){
        if (item == null) return;

        removeEntityFromWorld();
        placeEntityInWorld();
    }

    private void capWool(){
        if (this.carrier == null)
            return;

        WoolTimer.unregisterWoolForTiming(this);

        this.bossBar.setVisible(false);
        this.barVisible.set(false);
        this.carrier.removeCarry();
        carrier.getPlayer().getInventory().setHelmet(null);

        managers.getPlugin().getServer().broadcastMessage("Wool '" + name + "' has been captured!");

        var capPointMarker = this.markerManager.getMarker(capMarker);
        if (capPointMarker == null)
            return;

        var blockLocation = capPointMarker.getLocation();
        this.block = Bukkit.createBlockData(this.material);
        capPointMarker.getWorld().setBlockData(blockLocation, this.block);


        this.managers.get(EventManager.class).getValue().pushInternalEvent(new WoolCapturedEvent(this.carrier, this));
    }

    public void tick(){
        if (this.block != null)
            return;

        var capping = this.capping.get();
        var modifier = this.cappingModifier.get();
        var current = this.cappedAmount.get();

        if (!capping){
            int nonCappingDecrease = 10;
            current -= nonCappingDecrease;
            if (current <= 0){
                this.cappedAmount.set(0);
                WoolTimer.unregisterWoolForTiming(this);
            }
            else
                this.cappedAmount.set(current);

            setBossBar();
            return;
        }

        current += modifier;

        if (current > capAmount){
            capWool();
            return;
        }

        this.cappedAmount.set(current);
        setBossBar();
    }

    @Override
    public void close() throws Exception {
        removeEntityFromWorld();
        removeFromCarrier();
        if (this.block != null){
            var capPointMarker = this.markerManager.getMarker(capMarker);
            if (capPointMarker != null){
                var blockLocation = capPointMarker.getLocation();
                var airBlock = Bukkit.createBlockData(Material.AIR);
                capPointMarker.getWorld().setBlockData(blockLocation, airBlock);
            }
            this.block = null;
        }
    }
}
