package net.klaaswhite.c2w.adapter.managers;

import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("EntityManager")
class EntityManagerTest {

    private EventManager eventManager;
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        eventManager = mock(EventManager.class);
        entityManager = new EntityManager(eventManager);
    }

    // --- Construction ---

    @Test
    @DisplayName("constructs without exception")
    void constructs() {
        assertNotNull(entityManager);
    }

    private Item createMockItem() {
        var item = mock(Item.class);
        when(item.getUniqueId()).thenReturn(UUID.randomUUID());
        return item;
    }

    // --- addItemPickedUpEventListener + onEntityPickupItemEvent ---

    @Test
    @DisplayName("callback fires when event contains matching item")
    void callbackFiresForMatchingItem() {
        var item = createMockItem();
        var event = mock(EntityPickupItemEvent.class);
        when(event.getItem()).thenReturn(item);

        var called = new AtomicInteger(0);
        entityManager.addItemPickedUpEventListener(item, e -> called.incrementAndGet());

        entityManager.onEntityPickupItemEvent(event);

        assertEquals(1, called.get());
    }

    @Test
    @DisplayName("callback does not fire for different item")
    void callbackNotCalledForDifferentItem() {
        var registeredItem = createMockItem();
        var differentItem = createMockItem();
        var event = mock(EntityPickupItemEvent.class);
        when(event.getItem()).thenReturn(differentItem);

        var called = new AtomicInteger(0);
        entityManager.addItemPickedUpEventListener(registeredItem, e -> called.incrementAndGet());

        entityManager.onEntityPickupItemEvent(event);

        assertEquals(0, called.get());
    }

    @Test
    @DisplayName("callback does not fire for null item")
    void callbackNotCalledForNullReturningEvent() {
        var item = createMockItem();
        var event = mock(EntityPickupItemEvent.class);
        when(event.getItem()).thenReturn(null);

        var called = new AtomicInteger(0);
        entityManager.addItemPickedUpEventListener(item, e -> called.incrementAndGet());

        entityManager.onEntityPickupItemEvent(event);

        assertEquals(0, called.get());
    }

    @Test
    @DisplayName("event with no registered listeners does nothing")
    void eventWithNoListeners() {
        var item = createMockItem();
        var event = mock(EntityPickupItemEvent.class);
        when(event.getItem()).thenReturn(item);

        assertDoesNotThrow(() -> entityManager.onEntityPickupItemEvent(event));
    }

    // --- removeItemPickedUpEventListener ---

    @Test
    @DisplayName("removed callback does not fire")
    void removedCallbackDoesNotFire() {
        var item = createMockItem();
        var event = mock(EntityPickupItemEvent.class);
        when(event.getItem()).thenReturn(item);

        var called = new AtomicInteger(0);
        Consumer<EntityPickupItemEvent> callback = e -> called.incrementAndGet();

        entityManager.addItemPickedUpEventListener(item, callback);
        entityManager.removeItemPickedUpEventListener(item, callback);

        entityManager.onEntityPickupItemEvent(event);

        assertEquals(0, called.get());
    }

    @Test
    @DisplayName("removing non-existent callback does nothing")
    void removingNonExistentCallback() {
        var item = createMockItem();

        assertDoesNotThrow(() -> entityManager.removeItemPickedUpEventListener(item, e -> {}));
    }

    @Test
    @DisplayName("removing one callback does not affect other callbacks for same item")
    void removingOneCallbackLeavesOthers() {
        var item = createMockItem();
        var event = mock(EntityPickupItemEvent.class);
        when(event.getItem()).thenReturn(item);

        var callCount = new AtomicInteger(0);
        Consumer<EntityPickupItemEvent> toRemove = e -> callCount.incrementAndGet();
        Consumer<EntityPickupItemEvent> toKeep = e -> callCount.incrementAndGet();

        entityManager.addItemPickedUpEventListener(item, toRemove);
        entityManager.addItemPickedUpEventListener(item, toKeep);
        entityManager.removeItemPickedUpEventListener(item, toRemove);

        entityManager.onEntityPickupItemEvent(event);

        assertEquals(1, callCount.get());
    }

    // --- close() ---

    @Test
    @DisplayName("close clears all listeners")
    void closeClearsListeners() {
        var item = createMockItem();
        var event = mock(EntityPickupItemEvent.class);
        when(event.getItem()).thenReturn(item);

        var called = new AtomicInteger(0);
        entityManager.addItemPickedUpEventListener(item, e -> called.incrementAndGet());

        entityManager.close();

        entityManager.onEntityPickupItemEvent(event);

        assertEquals(0, called.get());
    }

    @Test
    @DisplayName("close can be called safely multiple times")
    void closeCalledMultipleTimes() {
        assertDoesNotThrow(() -> {
            entityManager.close();
            entityManager.close();
            entityManager.close();
        });
    }

    // --- Multiple callbacks ---

    @Test
    @DisplayName("multiple callbacks for same item all fire")
    void multipleCallbacksForSameItem() {
        var item = createMockItem();
        var event = mock(EntityPickupItemEvent.class);
        when(event.getItem()).thenReturn(item);

        var callCount = new AtomicInteger(0);

        entityManager.addItemPickedUpEventListener(item, e -> callCount.incrementAndGet());
        entityManager.addItemPickedUpEventListener(item, e -> callCount.incrementAndGet());
        entityManager.addItemPickedUpEventListener(item, e -> callCount.incrementAndGet());

        entityManager.onEntityPickupItemEvent(event);

        assertEquals(3, callCount.get());
    }
}