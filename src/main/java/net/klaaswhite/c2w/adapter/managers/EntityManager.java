package net.klaaswhite.c2w.adapter.managers;

import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityPickupItemEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;

public class EntityManager implements AutoCloseable {

    // ponytail: UUID keys avoid stale Item entity references after pickup/despawn
    private final HashMap<UUID, ArrayList<Consumer<EntityPickupItemEvent>>> itemPickedUpEventListeners;

    public EntityManager(EventManager eventManager) {
        eventManager.registerMinecraftEvent(EntityPickupItemEvent.class, this::onEntityPickupItemEvent);

        itemPickedUpEventListeners = new HashMap<>();
    }

    public void onEntityPickupItemEvent(EntityPickupItemEvent event) {
        var item = event.getItem();
        if (item == null) return;

        var listeners = itemPickedUpEventListeners.get(item.getUniqueId());
        if (listeners == null) return;

        for (var listener : new ArrayList<>(listeners))
            listener.accept(event);
    }

    public void addItemPickedUpEventListener(Item item, Consumer<EntityPickupItemEvent> callBack) {
        var id = item.getUniqueId();
        itemPickedUpEventListeners.computeIfAbsent(id, k -> new ArrayList<>()).add(callBack);
    }

    /**
     * Register a listener for pickup events on an item entity identified by UUID.
     * Use this when you know the UUID but cannot obtain a live {@link Item} reference
     * (e.g. the entity's chunk may not be loaded).
     */
    public void addItemPickedUpEventListener(UUID itemUuid, Consumer<EntityPickupItemEvent> callBack) {
        itemPickedUpEventListeners.computeIfAbsent(itemUuid, k -> new ArrayList<>()).add(callBack);
    }

    public void removeItemPickedUpEventListener(Item item, Consumer<EntityPickupItemEvent> callBack) {
        var id = item.getUniqueId();
        removeItemPickedUpEventListener(id, callBack);
    }

    public void removeItemPickedUpEventListener(UUID itemUuid, Consumer<EntityPickupItemEvent> callBack) {
        var listeners = itemPickedUpEventListeners.get(itemUuid);
        if (listeners == null) return;

        listeners.remove(callBack);
        if (listeners.isEmpty())
            itemPickedUpEventListeners.remove(itemUuid);
    }

    @Override
    public void close() {
        itemPickedUpEventListeners.clear();
    }
}
