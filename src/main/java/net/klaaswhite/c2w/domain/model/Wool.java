package net.klaaswhite.c2w.domain.model;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Domain abstraction for a single wool that can be picked up, carried, and
 * captured. Pure domain — no Bukkit imports.
 * <p>
 * The adapter layer provides the concrete implementation
 * ({@code net.klaaswhite.c2w.adapter.minecraft.Wool}) which performs the
 * Minecraft side-effects. Domain code (state machine, boundary engine, timer)
 * depends only on this interface, keeping the layering rule
 * {@code domain/} never imports from {@code adapter/} intact.
 */
public interface Wool extends AutoCloseable {

    /** Maximum capture amount (ticks at 20/sec = 60 seconds with modifier 20). */
    int CAP_AMOUNT = 20 * 60;

    WoolColor getColor();

    BlockPos getSpawnPos();

    String getWorldName();

    String getCapMarkerName();

    ManagedPlayer getCarrier();

    boolean isCarried();

    boolean isCapped();

    boolean isCapping();

    int getCappedAmount();

    int getCappingModifier();

    /** A player picks up this wool. Returns true if successful. */
    boolean pickup(ManagedPlayer player);

    /** The carrier dies - drop the wool back into the world. */
    void dropOnDeath(ManagedPlayer player);

    void setCapping(boolean active);

    void setCappingModifier(int mod);

    /** Tick the capture progress. Called every game tick for active wools. */
    void tick();

    /** Capture this wool (called when cap progress reaches 100%). */
    void capture();

    void placeEntityInWorld();

    void removeEntityFromWorld();

    void ensureEntity();
}
