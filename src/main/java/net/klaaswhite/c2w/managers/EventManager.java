package net.klaaswhite.c2w.managers;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import net.klaaswhite.c2w.C2W;
import net.klaaswhite.c2w.eventlisteners.BlockEventListeners;
import net.klaaswhite.c2w.eventlisteners.EntityEventListeners;
import net.klaaswhite.c2w.eventlisteners.PlayerEventListeners;
import net.klaaswhite.c2w.events.IC2WEvent;
import net.klaaswhite.c2w.interfaces.IManager;
import net.klaaswhite.c2w.packetlisteners.ChangeTeamPacketListener;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EventManager implements IManager {

    private final C2W plugin;
    private final ProtocolManager protocolManager;

    public EventManager(Managers managers){
        plugin = managers.getPlugin();

        var pluginManager = plugin.getServer().getPluginManager();
        protocolManager = ProtocolLibrary.getProtocolManager();

        pluginManager.registerEvents(new BlockEventListeners(managers, this), plugin);
        pluginManager.registerEvents(new EntityEventListeners(managers, this), plugin);
        pluginManager.registerEvents(new PlayerEventListeners(managers, this), plugin);

        protocolManager.addPacketListener(new ChangeTeamPacketListener(managers));
    }


    private final Map<Class<? extends Event>, List<Consumer<? extends Event>>> minecraftEventListeners = new HashMap<>();

    public <T extends Event>
    void registerMinecraftEvent(Class<T> eventType, Consumer<T> listener) {
        minecraftEventListeners
                .computeIfAbsent(eventType, k -> new ArrayList<>())
                .add(listener);
    }

    @SuppressWarnings("unchecked")
    public <T extends Event>
    void pushMinecraftEvent(T event) {
        List<Consumer<? extends Event>> eventListeners =
                minecraftEventListeners.get(event.getClass());

        if (eventListeners == null) {
            return;
        }

        for (Consumer<? extends Event> listener : eventListeners) {
            Consumer<T> typedListener = (Consumer<T>) listener;
            typedListener.accept(event);
        }
    }

    private final Map<Class<? extends IC2WEvent>, List<Consumer<? extends IC2WEvent>>> internalEventListeners = new HashMap<>();

    public <T extends IC2WEvent>
    void registerInternalEvent(Class<T> eventType, Consumer<T> listener) {
        internalEventListeners
                .computeIfAbsent(eventType, k -> new ArrayList<>())
                .add(listener);
    }

    @SuppressWarnings("unchecked")
    public <T extends IC2WEvent>
    void pushInternalEvent(T event) {
        List<Consumer<? extends IC2WEvent>> eventListeners =
                internalEventListeners.get(event.getClass());

        if (eventListeners == null) {
            return;
        }

        for (Consumer<? extends IC2WEvent> listener : eventListeners) {
            Consumer<T> typedListener = (Consumer<T>) listener;
            typedListener.accept(event);
        }
    }

    @Override
    public void close() throws Exception {
        minecraftEventListeners.clear();
        internalEventListeners.clear();

        HandlerList.unregisterAll(this.plugin);
        protocolManager.removePacketListeners(this.plugin);
    }
}
