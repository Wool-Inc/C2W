package net.klaaswhite.c2w.adapter.managers;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import net.klaaswhite.c2w.bootstrap.C2W;
import net.klaaswhite.c2w.bootstrap.Managers;
import net.klaaswhite.c2w.bootstrap.listeners.EntityEventListeners;
import net.klaaswhite.c2w.bootstrap.listeners.PlayerEventListeners;
import net.klaaswhite.c2w.domain.events.C2WEvent;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("EventManager")
class EventManagerTest {

    private C2W plugin;
    private Managers managers;
    private PluginManager pluginManager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        plugin = mock(C2W.class);
        managers = mock(Managers.class);
        var server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(plugin.getServer()).thenReturn(server);
    }

    /** Creates an EventManager with all Bukkit/ProtocolLib dependencies mocked. */
    private EventManager createEventManager() {
        var protoManager = mock(ProtocolManager.class);
        try (var protoLib = Mockito.mockStatic(ProtocolLibrary.class);
             var entityCons = Mockito.mockConstruction(EntityEventListeners.class);
             var playerCons = Mockito.mockConstruction(PlayerEventListeners.class)) {
            protoLib.when(ProtocolLibrary::getProtocolManager).thenReturn(protoManager);
            return new EventManager(plugin, managers);
        }
    }

    // --- Test event types -------------------------------------------------------

    static class TestInternalEvent implements C2WEvent {
    }

    static class OtherInternalEvent implements C2WEvent {
    }

    static class TestMinecraftEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        @SuppressWarnings("unused")
        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    // --- Internal event tests ---------------------------------------------------

    @Test
    @DisplayName("registerInternalEvent + pushInternalEvent invokes handler")
    void internalEventDispatchedToHandler() {
        var em = createEventManager();
        var captured = new AtomicReference<TestInternalEvent>();

        em.registerInternalEvent(TestInternalEvent.class, captured::set);
        var event = new TestInternalEvent();
        em.pushInternalEvent(event);

        assertSame(event, captured.get());
    }

    @Test
    @DisplayName("multiple handlers for same internal event type all fire")
    void multipleHandlersForSameInternalEvent() {
        var em = createEventManager();
        var count = new AtomicInteger();

        em.registerInternalEvent(TestInternalEvent.class, e -> count.incrementAndGet());
        em.registerInternalEvent(TestInternalEvent.class, e -> count.incrementAndGet());
        em.registerInternalEvent(TestInternalEvent.class, e -> count.incrementAndGet());
        em.pushInternalEvent(new TestInternalEvent());

        assertEquals(3, count.get());
    }

    @Test
    @DisplayName("handlers are not invoked for unrelated internal event types")
    void handlerNotCalledForOtherEventType() {
        var em = createEventManager();
        var called = new AtomicInteger();

        em.registerInternalEvent(TestInternalEvent.class, e -> called.incrementAndGet());
        em.pushInternalEvent(new OtherInternalEvent());

        assertEquals(0, called.get());
    }

    @Test
    @DisplayName("pushing internal event with no registered handlers does nothing")
    void pushInternalEventWithNoHandlers() {
        var em = createEventManager();
        assertDoesNotThrow(() -> em.pushInternalEvent(new TestInternalEvent()));
    }

    // --- Minecraft event tests --------------------------------------------------

    @Test
    @DisplayName("registerMinecraftEvent + pushMinecraftEvent invokes handler")
    void minecraftEventDispatchedToHandler() {
        var em = createEventManager();
        var captured = new AtomicReference<TestMinecraftEvent>();

        em.registerMinecraftEvent(TestMinecraftEvent.class, captured::set);
        var event = new TestMinecraftEvent();
        em.pushMinecraftEvent(event);

        assertSame(event, captured.get());
    }

    @Test
    @DisplayName("multiple handlers for same Minecraft event type all fire")
    void multipleHandlersForSameMinecraftEvent() {
        var em = createEventManager();
        var count = new AtomicInteger();

        em.registerMinecraftEvent(TestMinecraftEvent.class, e -> count.incrementAndGet());
        em.registerMinecraftEvent(TestMinecraftEvent.class, e -> count.incrementAndGet());
        em.pushMinecraftEvent(new TestMinecraftEvent());

        assertEquals(2, count.get());
    }

    @Test
    @DisplayName("handlers are not invoked for unrelated Minecraft event types")
    void minecraftHandlerNotCalledForOtherEventType() {
        var em = createEventManager();
        var called = new AtomicInteger();

        em.registerMinecraftEvent(TestMinecraftEvent.class, e -> called.incrementAndGet());
        em.pushMinecraftEvent(mock(org.bukkit.event.player.PlayerJoinEvent.class));

        assertEquals(0, called.get());
    }

    // --- close() ----------------------------------------------------------------

    @Test
    @DisplayName("close clears internal event handlers")
    void closeClearsInternalEventHandlers() {
        var em = createEventManager();
        var called = new AtomicInteger();

        em.registerInternalEvent(TestInternalEvent.class, e -> called.incrementAndGet());
        em.close();

        em.pushInternalEvent(new TestInternalEvent());
        assertEquals(0, called.get());
    }

    @Test
    @DisplayName("close clears Minecraft event handlers")
    void closeClearsMinecraftEventHandlers() {
        var em = createEventManager();
        var called = new AtomicInteger();

        em.registerMinecraftEvent(TestMinecraftEvent.class, e -> called.incrementAndGet());
        em.close();

        em.pushMinecraftEvent(new TestMinecraftEvent());
        assertEquals(0, called.get());
    }

    @Test
    @DisplayName("close can be called safely multiple times")
    void closeCalledMultipleTimes() {
        var em = createEventManager();
        assertDoesNotThrow(() -> {
            em.close();
            em.close();
            em.close();
        });
    }
}