package net.klaaswhite.c2w.domain.events;

import net.klaaswhite.c2w.adapter.minecraft.BossBar;
import net.klaaswhite.c2w.adapter.minecraft.BossBars;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Wool;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.PlayerHandle;
import net.klaaswhite.c2w.domain.model.WoolColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Domain Events")
class EventsTest {

    private MinecraftManager createMockMc() {
        var mc = mock(MinecraftManager.class);
        var bossBars = mock(BossBars.class);
        var bossBar = mock(BossBar.class);
        when(mc.bossBars()).thenReturn(bossBars);
        when(bossBars.createBossBar(anyString(), any(), any())).thenReturn(bossBar);
        return mc;
    }

    private Wool createWool(WoolColor color) {
        var mc = createMockMc();
        var woolTimer = new net.klaaswhite.c2w.domain.game.WoolTimer(new net.klaaswhite.c2w.domain.game.WoolTimer.Scheduler() {
            public Object scheduleRepeating(Runnable task, long delay, long interval) { return null; }
            public void cancel(Object taskId) {}
        });
        return new Wool(mc, woolTimer, color, new net.klaaswhite.c2w.domain.model.BlockPos(0, 64, 0),
                "game", "cap-" + color.name().toLowerCase());
    }

    private ManagedPlayer createPlayer(String name) {
        var handle = mock(PlayerHandle.class);
        var uuid = UUID.randomUUID();
        when(handle.getName()).thenReturn(name);
        when(handle.getDisplayName()).thenReturn(name);
        when(handle.getUniqueId()).thenReturn(uuid);
        return new ManagedPlayer(handle);
    }

    // --- C2WEvent interface compliance ---

    @Test
    @DisplayName("WoolCapturedEvent implements C2WEvent")
    void woolCapturedEventImplementsC2WEvent() {
        var player = createPlayer("Alice");
        var wool = createWool(WoolColor.RED);
        var event = new WoolCapturedEvent(player, wool);
        assertInstanceOf(C2WEvent.class, event);
    }

    @Test
    @DisplayName("WoolDroppedEvent implements C2WEvent")
    void woolDroppedEventImplementsC2WEvent() {
        var wool = createWool(WoolColor.BLUE);
        var event = new WoolDroppedEvent(wool);
        assertInstanceOf(C2WEvent.class, event);
    }

    @Test
    @DisplayName("StartGameEvent implements C2WEvent")
    void startGameEventImplementsC2WEvent() {
        var event = new StartGameEvent("c2w_game");
        assertInstanceOf(C2WEvent.class, event);
    }

    @Test
    @DisplayName("EndGameEvent implements C2WEvent")
    void endGameEventImplementsC2WEvent() {
        var event = new EndGameEvent();
        assertInstanceOf(C2WEvent.class, event);
    }

    @Test
    @DisplayName("GameWorldCreatedEvent implements C2WEvent")
    void gameWorldCreatedEventImplementsC2WEvent() {
        var event = new GameWorldCreatedEvent("c2w_game");
        assertInstanceOf(C2WEvent.class, event);
    }

    @Test
    @DisplayName("DraftCreatedEvent implements C2WEvent")
    void draftCreatedEventImplementsC2WEvent() {
        var event = new DraftCreatedEvent("c2w_draft");
        assertInstanceOf(C2WEvent.class, event);
    }

    @Test
    @DisplayName("PreviewRequestEvent implements C2WEvent")
    void previewRequestEventImplementsC2WEvent() {
        var input = new CommandInput();
        var event = new PreviewRequestEvent(input);
        assertInstanceOf(C2WEvent.class, event);
    }

    // --- WoolCapturedEvent ---

    @Test
    @DisplayName("WoolCapturedEvent carries correct player")
    void woolCapturedEventCarriesPlayer() {
        var player = createPlayer("Alice");
        var wool = createWool(WoolColor.RED);
        var event = new WoolCapturedEvent(player, wool);

        assertSame(player, event.getPlayer());
    }

    @Test
    @DisplayName("WoolCapturedEvent carries correct wool")
    void woolCapturedEventCarriesWool() {
        var player = createPlayer("Alice");
        var wool = createWool(WoolColor.GREEN);
        var event = new WoolCapturedEvent(player, wool);

        assertSame(wool, event.getWool());
        assertEquals(WoolColor.GREEN, event.getWool().getColor());
    }

    @Test
    @DisplayName("WoolCapturedEvent carries correct color info")
    void woolCapturedEventCarriesColor() {
        var player = createPlayer("Bob");
        var wool = createWool(WoolColor.BLUE);
        var event = new WoolCapturedEvent(player, wool);

        assertEquals(WoolColor.BLUE, event.getWool().getColor());
    }

    // --- WoolDroppedEvent ---

    @Test
    @DisplayName("WoolDroppedEvent carries correct wool")
    void woolDroppedEventCarriesWool() {
        var wool = createWool(WoolColor.YELLOW);
        var event = new WoolDroppedEvent(wool);

        assertSame(wool, event.getWool());
        assertEquals(WoolColor.YELLOW, event.getWool().getColor());
    }

    // --- StartGameEvent ---

    @Test
    @DisplayName("StartGameEvent carries game world name")
    void startGameEventCarriesWorldName() {
        var event = new StartGameEvent("c2w_game_123");
        assertEquals("c2w_game_123", event.getGameWorldName());
    }

    // --- GameWorldCreatedEvent ---

    @Test
    @DisplayName("GameWorldCreatedEvent carries game world name")
    void gameWorldCreatedEventCarriesWorldName() {
        var event = new GameWorldCreatedEvent("c2w_game_456");
        assertEquals("c2w_game_456", event.getGameWorldName());
    }

    // --- DraftCreatedEvent ---

    @Test
    @DisplayName("DraftCreatedEvent carries draft world name")
    void draftCreatedEventCarriesWorldName() {
        var event = new DraftCreatedEvent("c2w_draft");
        assertEquals("c2w_draft", event.getDraftWorldName());
    }

    // --- PreviewRequestEvent ---

    @Test
    @DisplayName("PreviewRequestEvent carries CommandInput")
    void previewRequestEventCarriesCommandInput() {
        var input = new CommandInput();
        input.strings = new String[]{"preview", "arena"};
        var event = new PreviewRequestEvent(input);

        assertSame(input, event.getCommandInput());
        assertArrayEquals(new String[]{"preview", "arena"}, event.getCommandInput().strings);
    }

    // --- toString / output ---

    @Test
    @DisplayName("WoolCapturedEvent has non-empty toString")
    void woolCapturedEventToString() {
        var player = createPlayer("Alice");
        var wool = createWool(WoolColor.RED);
        var event = new WoolCapturedEvent(player, wool);
        var str = event.toString();

        assertNotNull(str);
        assertFalse(str.isEmpty());
    }

    @Test
    @DisplayName("WoolDroppedEvent has non-empty toString")
    void woolDroppedEventToString() {
        var wool = createWool(WoolColor.BLUE);
        var event = new WoolDroppedEvent(wool);
        var str = event.toString();

        assertNotNull(str);
        assertFalse(str.isEmpty());
    }

    @Test
    @DisplayName("EndGameEvent has non-empty toString")
    void endGameEventToString() {
        var event = new EndGameEvent();
        var str = event.toString();

        assertNotNull(str);
        assertFalse(str.isEmpty());
    }
}
