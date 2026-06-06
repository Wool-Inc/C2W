package net.klaaswhite.c2w.managers;

import net.klaaswhite.c2w.classes.Wool;
import net.klaaswhite.c2w.commands.base.CommandInput;
import net.klaaswhite.c2w.events.InitializeGameEvent;
import net.klaaswhite.c2w.interfaces.IManager;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.function.Consumer;

public class EntityManager implements IManager {

    private final Hashtable<Item, ArrayList<Consumer<EntityPickupItemEvent>>> itemPickedUpEventListeners;

    public EntityManager(Managers managers){
        var eventManager = managers.get(EventManager.class);
        eventManager.getValue().registerMinecraftEvent(EntityPickupItemEvent.class, this::onEntityPickupItemEvent);

        itemPickedUpEventListeners = new Hashtable<>();
    }

    public void onEntityPickupItemEvent(EntityPickupItemEvent event){
        var item = event.getItem();
        if (!itemPickedUpEventListeners.containsKey(item)) return;

        var listeners = itemPickedUpEventListeners.get(item);
        for (var listener : listeners)
            listener.accept(event);
    }

    public void addItemPickedUpEventListener(Item item, Consumer<EntityPickupItemEvent> callBack){
        if (itemPickedUpEventListeners.containsKey(item))
            itemPickedUpEventListeners.get(item).add(callBack);
        else {
            var consumers = new ArrayList<Consumer<EntityPickupItemEvent>>();
            consumers.add(callBack);
            itemPickedUpEventListeners.put(item, consumers);
        }
    }

    public void removeItemPickedUpEventListener(Item item, Consumer<EntityPickupItemEvent> callBack){
        if (!itemPickedUpEventListeners.containsKey(item))
            return;

        var listeners = itemPickedUpEventListeners.get(item);
        listeners.remove(callBack);
        if (listeners.isEmpty())
            itemPickedUpEventListeners.remove(item);
    }

    @Override
    public void close() throws Exception {

    }
}
