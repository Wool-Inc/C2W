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

        for (var listener : listeners)
            listener.accept(event);
    }

    public void addItemPickedUpEventListener(Item item, Consumer<EntityPickupItemEvent> callBack) {
        var id = item.getUniqueId();
        itemPickedUpEventListeners.computeIfAbsent(id, k -> new ArrayList<>()).add(callBack);
    }

    public void removeItemPickedUpEventListener(Item item, Consumer<EntityPickupItemEvent> callBack) {
        var id = item.getUniqueId();
        var listeners = itemPickedUpEventListeners.get(id);
        if (listeners == null) return;

        listeners.remove(callBack);
        if (listeners.isEmpty())
            itemPickedUpEventListeners.remove(id);
    }

    @Override
    public void close() {
        itemPickedUpEventListeners.clear();
    }
}
