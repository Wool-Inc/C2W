package net.klaaswhite.c2w.adapter.minecraft;

import net.klaaswhite.c2w.domain.events.WoolCapturedEvent;
import net.klaaswhite.c2w.domain.events.WoolDroppedEvent;
import net.klaaswhite.c2w.domain.model.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-wool state machine: pickup, carry, capture, drop-on-death.
 * <p>
 * <b>Adapter layer.</b> All Minecraft interactions go through {@link MinecraftManager}.
 * Implements the domain {@link net.klaaswhite.c2w.domain.model.Wool} interface so
 * domain code can depend on the abstraction without importing this class.
 *
 * <h2>State transitions</h2>
 * <pre>
 * WAITING --pickup()--&gt; CARRIED --capture()--&gt; CAPPED
 *    ^                    |
 *    +---dropOnDeath()----+
 * </pre>
 */
public class Wool implements net.klaaswhite.c2w.domain.model.Wool {

    /** Maximum capture amount (ticks at 20/sec = 60 seconds with modifier 20). */
    public static final int CAP_AMOUNT = 20 * 60;

    // --- Fields ---

    private final MinecraftManager mc;
    private final WoolColor color;
    private final BlockPos spawnPos;
    private final String worldName;
    private final String capMarkerName;
    private final BossBar bossBar;
    private final net.klaaswhite.c2w.domain.game.WoolTimer woolTimer;

    private final AtomicBoolean capping = new AtomicBoolean(false);
    private final AtomicInteger cappingModifier = new AtomicInteger(0);
    private final AtomicInteger cappedAmount = new AtomicInteger(0);
    private final AtomicReference<ManagedPlayer> carrier = new AtomicReference<>(null);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private boolean entityPlaced = false;
    private boolean blockPlaced = false; // ponytail: volatile-free, accessed only on Bukkit main thread
    private java.util.UUID droppedItemId = null; // tracked so we can remove the dropped Item on pickup/close

    public Wool(MinecraftManager mc, net.klaaswhite.c2w.domain.game.WoolTimer woolTimer, WoolColor color, BlockPos spawnPos, String worldName, String capMarkerName) {
        this.mc = mc;
        this.woolTimer = woolTimer;
        this.color = color;
        this.spawnPos = spawnPos;
        this.worldName = worldName;
        this.capMarkerName = capMarkerName;
        this.bossBar = mc.bossBars().createBossBar(color.name() + " capture", color, BossBarStyle.SOLID);
        this.bossBar.setVisible(false);
    }

    // --- State queries ---

    public WoolColor getColor() { return color; }
    public BlockPos getSpawnPos() { return spawnPos; }
    public String getWorldName() { return worldName; }
    public String getCapMarkerName() { return capMarkerName; }
    public ManagedPlayer getCarrier() { return carrier.get(); }
    public boolean isCarried() { return carrier.get() != null; }
    public boolean isCapped() { return blockPlaced; }
    public boolean isCapping() { return capping.get(); }
    public int getCappedAmount() { return cappedAmount.get(); }
    public int getCappingModifier() { return cappingModifier.get(); }

    // --- State transitions ---

    /**
     * A player picks up this wool. Returns true if successful.
     * Fails if the wool is already carried or the player is already carrying.
     */
    public boolean pickup(ManagedPlayer player) {
        if (isCapped()) return false;
        if (!carrier.compareAndSet(null, player)) return false;
        if (!player.tryAddCarriable(this)) {
            carrier.set(null);
            return false;
        }

        removeEntityFromWorld();
        cappingModifier.set(woolTimer.getBaseCapture());
        bossBar.addPlayer(player.getPlayer());

        String playerName = player.getPlayer().getName();
        mc.server().broadcastMessage("Wool '" + color.name() + "' picked up by '" + player.getPlayer().getDisplayName() + "'!");
        mc.players().setHelmet(playerName, ItemStackRef.wool(color));
        mc.players().sendActionBar(playerName, "§aYou picked up §e" + color.name() + "§a wool!");
        mc.players().playSound(playerName, "ENTITY_ITEM_PICKUP", 1.0f, 1.0f);
        mc.players().spawnParticles(playerName, worldName, spawnPos.x(), spawnPos.y(), spawnPos.z(), "REDSTONE", 20, 255, 255, 255);
        return true;
    }

    /**
     * The carrier dies - drop the wool back into the world.
     */
    public void dropOnDeath(ManagedPlayer player) {
        if (!isCarried()) return;
        if (carrier.get() != player) return;

        String playerName = player.getPlayer().getName();
        removeFromCarrier();
        capping.set(false);
        cappedAmount.set(0);
        updateBossBar();
        placeEntityInWorld();
        mc.players().sendActionBar(playerName, "§cYou dropped §e" + color.name() + "§c wool!");
        mc.server().broadcastMessage("§e" + player.getPlayer().getDisplayName() + " §cdropped §e" + color.name() + "§c wool!");
        var deadPlayer = player.getPlayer();
        BlockPos dropPos = deadPlayer.getPosition();
        mc.players().spawnParticles(playerName, worldName,
                dropPos.x(), dropPos.y(), dropPos.z(),
                "REDSTONE", 15, 255, 0, 0);
        mc.pushEvent(new WoolDroppedEvent(this));
    }

    public void setCapping(boolean active) {
        capping.set(active);
    }

    public void setCappingModifier(int mod) {
        cappingModifier.set(mod);
    }

    /**
     * Tick the capture progress. Called every game tick for active wools.
     */
    public void tick() {
        if (blockPlaced) return;

        boolean active = capping.get();
        int modifier = cappingModifier.get();
        int current = cappedAmount.get();

        if (!active) {
            current -= woolTimer.getDecreaseOutsideArea();
            if (current <= 0) {
                cappedAmount.set(0);
            } else {
                cappedAmount.set(current);
            }
            updateBossBar();
            return;
        }

        current += modifier;
        if (current >= CAP_AMOUNT) {
            capture();
            return;
        }
        cappedAmount.set(current);
        updateBossBar();
        ManagedPlayer currentCarrier = carrier.get();
        if (currentCarrier != null) {
            String playerName = currentCarrier.getPlayer().getName();
            mc.players().sendActionBar(playerName, "§eCapturing: " + getProgressString(current, CAP_AMOUNT));
        }
    }

    /**
     * Capture this wool (called when cap progress reaches 100%).
     */
    public void capture() {
        if (isCapped()) return;
        ManagedPlayer capper = carrier.get();
        if (capper == null) return;

        String playerName = capper.getPlayer().getName();
        removeFromCarrier();
        blockPlaced = true;
        bossBar.setVisible(false);
        mc.server().broadcastMessage("Wool '" + color.name() + "' has been captured!");
        mc.players().sendTitle(playerName, "§6Wool Captured!", "§e" + color.name() + " wool secured!", 10, 40, 20);
        mc.players().playSound(playerName, "ENTITY_PLAYER_LEVELUP", 1.0f, 1.0f);
        var capperPlayer = capper.getPlayer();
        BlockPos capPos = capperPlayer.getPosition();
        mc.players().spawnParticles(capperPlayer.getName(), worldName,
                capPos.x(), capPos.y(), capPos.z(),
                "REDSTONE", 50, 255, 215, 0);
        mc.pushEvent(new WoolCapturedEvent(capper, this));
    }

    public void placeEntityInWorld() {
        if (blockPlaced) return;
        entityPlaced = true;
        droppedItemId = mc.worlds().dropItem(worldName, spawnPos, color.name() + "_WOOL", 1);
    }

    public void removeEntityFromWorld() {
        entityPlaced = false;
        removeDroppedItem();
    }

    /** Remove the dropped Item entity from the world if it still exists. */
    private void removeDroppedItem() {
        if (droppedItemId == null) return;
        var item = org.bukkit.Bukkit.getEntity(droppedItemId);
        if (item instanceof org.bukkit.entity.Item i) {
            i.remove();
        }
        droppedItemId = null;
    }

    public void ensureEntity() {
        if (!entityPlaced && !blockPlaced && !isCarried()) {
            placeEntityInWorld();
        }
    }

    // --- Private helpers ---

    private void removeFromCarrier() {
        ManagedPlayer prev = carrier.getAndSet(null);
        if (prev != null) {
            prev.removeCarry();
            mc.players().setHelmet(prev.getPlayer().getName(), ItemStackRef.empty());
        }
    }

    private String getProgressString(int current, int max) {
        int filled = (int) ((double) current / max * 10);
        return "§a" + "█".repeat(filled) + "§7" + "░".repeat(10 - filled) + " §f" + (int)((double)current/max*100) + "%";
    }

    private void updateBossBar() {
        int current = cappedAmount.get();
        if (current <= 0) {
            bossBar.setProgress(0);
            bossBar.setVisible(false);
            return;
        }
        bossBar.setVisible(true);
        double progress = Math.min((double) current / CAP_AMOUNT, 1.0);
        bossBar.setProgress(progress);
    }

    @Override
    public void close() {
        if (closed.getAndSet(true)) return;
        removeEntityFromWorld();
        removeFromCarrier();
        bossBar.setVisible(false);
    }
}