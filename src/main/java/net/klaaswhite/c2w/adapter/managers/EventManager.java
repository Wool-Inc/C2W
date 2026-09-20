package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.bootstrap.C2W;
import net.klaaswhite.c2w.bootstrap.Managers;
import net.klaaswhite.c2w.bootstrap.listeners.EntityEventListeners;
import net.klaaswhite.c2w.bootstrap.listeners.PlayerEventListeners;
import net.klaaswhite.c2w.domain.events.C2WEvent;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Central event hub. Owns every subscription to Bukkit events and the internal
 * event bus. Other managers and game
 * objects call {@link #registerMinecraftEvent}, {@link #registerInternalEvent}
 * and the corresponding {@code push*} dispatchers instead of touching Bukkit's
 * event bus directly.
 */
public class EventManager implements AutoCloseable {

    private final Managers managers;
    private final C2W plugin;

    /**
    * Production constructor. Registers the Bukkit event listeners.
     */
    public EventManager(C2W plugin, Managers managers) {
        this.plugin = plugin;
        this.managers = managers;
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new EntityEventListeners(this), plugin);
        pluginManager.registerEvents(new PlayerEventListeners(this), plugin);
    }

    /**
    * Headless constructor for tests. Skips Bukkit listener registration so the
    * event hub can be used without a running server. The
     * two-channel dispatch logic is identical to the production constructor.
     */
    public EventManager(Managers managers) {
        this.plugin = null;
        this.managers = managers;
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

    private final Map<Class<? extends C2WEvent>, List<Consumer<? extends C2WEvent>>> internalEventListeners = new HashMap<>();

    public <T extends C2WEvent>
    void registerInternalEvent(Class<T> eventType, Consumer<T> listener) {
        internalEventListeners
                .computeIfAbsent(eventType, k -> new ArrayList<>())
                .add(listener);
    }

    @SuppressWarnings("unchecked")
    public <T extends C2WEvent>
    void unregisterInternalEvent(Class<T> eventType, Consumer<T> listener) {
        var listeners = internalEventListeners.get(eventType);
        if (listeners == null) return;
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            internalEventListeners.remove(eventType);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends C2WEvent>
    void pushInternalEvent(T event) {
        List<Consumer<? extends C2WEvent>> eventListeners =
                internalEventListeners.get(event.getClass());

        if (eventListeners == null) {
            return;
        }

        for (Consumer<? extends C2WEvent> listener : eventListeners) {
            Consumer<T> typedListener = (Consumer<T>) listener;
            typedListener.accept(event);
        }
    }


    @Override
    public void close() {
        minecraftEventListeners.clear();
        internalEventListeners.clear();

        if (this.plugin == null) return;
        HandlerList.unregisterAll(this.plugin);
    }
}
