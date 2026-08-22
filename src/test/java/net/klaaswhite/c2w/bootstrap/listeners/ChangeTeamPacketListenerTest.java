package net.klaaswhite.c2w.bootstrap.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.utility.MinecraftReflection;
import net.klaaswhite.c2w.adapter.managers.PlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChangeTeamPacketListener")
class ChangeTeamPacketListenerTest {

    @Mock private Plugin plugin;
    @Mock private PlayerManager playerManager;

    private ChangeTeamPacketListener listener;

    // ProtocolLib's PacketType static initializer calls Bukkit.getVersion(),
    // which requires a non-null Bukkit server. Mock it for the whole class.
    private static MockedStatic<Bukkit> bukkit;
    private static MockedStatic<MinecraftReflection> reflection;

    @BeforeAll
    static void mockBukkit() {
        bukkit = mockStatic(Bukkit.class);
        Server server = mock(Server.class);
        when(Bukkit.getServer()).thenReturn(server);
        when(Bukkit.getVersion()).thenReturn("git-Paper-1.21.1 (MC: 1.21.1)");
        when(server.getVersion()).thenReturn("git-Paper-1.21.1 (MC: 1.21.1)");
        when(server.getBukkitVersion()).thenReturn("1.21.1-R0.1-SNAPSHOT");

        // PacketContainer's static initializer pulls in BukkitConverters which
        // needs CraftBukkit classes. Stub the reflection lookups to return a
        // harmless placeholder class so the container can be constructed.
        reflection = mockStatic(MinecraftReflection.class);
        reflection.when(MinecraftReflection::getNmsWorldClass).thenReturn(DummyWorld.class);
        reflection.when(MinecraftReflection::getWorldServerClass).thenReturn(DummyWorld.class);
        reflection.when(MinecraftReflection::getCraftWorldClass).thenReturn(DummyWorld.class);
        reflection.when(() -> MinecraftReflection.getCraftBukkitClass(anyString())).thenReturn(DummyWorld.class);
        reflection.when(() -> MinecraftReflection.getMinecraftClass(anyString())).thenReturn(DummyWorld.class);
    }

    @AfterAll
    static void unmockBukkit() {
        bukkit.close();
        reflection.close();
    }

    @BeforeEach
    void setUp() {
        listener = new ChangeTeamPacketListener(plugin, playerManager);
    }

    private PacketEvent eventWith(String teamName, Collection<String> players, Integer action) {
        var packetEvent = mock(PacketEvent.class);
        var packet = mock(PacketContainer.class);
        when(packetEvent.getPacket()).thenReturn(packet);
        // getStrings().read(0)
        var strings = mock(com.comphenix.protocol.reflect.StructureModifier.class);
        when(packet.getStrings()).thenReturn(strings);
        when(strings.read(0)).thenReturn(teamName);
        // getSpecificModifier(Collection.class).read(0)
        var specific = mock(com.comphenix.protocol.reflect.StructureModifier.class);
        when(packet.getSpecificModifier(eq(java.util.Collection.class))).thenReturn(specific);
        when(specific.read(0)).thenReturn(players);
        // getIntegers().read(0)
        var integers = mock(com.comphenix.protocol.reflect.StructureModifier.class);
        when(packet.getIntegers()).thenReturn(integers);
        when(integers.read(0)).thenReturn(action);
        return packetEvent;
    }

    @Test
    @DisplayName("ADD ENTITIES action (3) calls addPlayersToTeam")
    void addAction() {
        var players = List.of("Alice", "Bob");
        listener.onPacketSending(eventWith("Red", players, 3));

        verify(playerManager).addPlayersToTeam("Red", players);
        verify(playerManager, never()).removePlayersFromTeam(any(), any());
    }

    @Test
    @DisplayName("REMOVE ENTITIES action (4) calls removePlayersFromTeam")
    void removeAction() {
        var players = List.of("Alice");
        listener.onPacketSending(eventWith("Blue", players, 4));

        verify(playerManager).removePlayersFromTeam("Blue", players);
        verify(playerManager, never()).addPlayersToTeam(any(), any());
    }

    @Test
    @DisplayName("null action does nothing")
    void nullAction() {
        var players = List.of("Alice");
        listener.onPacketSending(eventWith("Red", players, null));

        verify(playerManager, never()).addPlayersToTeam(any(), any());
        verify(playerManager, never()).removePlayersFromTeam(any(), any());
    }

    @Test
    @DisplayName("unknown action does nothing")
    void unknownAction() {
        var players = List.of("Alice");
        listener.onPacketSending(eventWith("Red", players, 99));

        verify(playerManager, never()).addPlayersToTeam(any(), any());
        verify(playerManager, never()).removePlayersFromTeam(any(), any());
    }
}
