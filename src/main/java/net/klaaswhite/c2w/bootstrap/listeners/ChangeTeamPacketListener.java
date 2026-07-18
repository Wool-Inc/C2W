package net.klaaswhite.c2w.bootstrap.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import net.klaaswhite.c2w.adapter.managers.PlayerManager;
import org.bukkit.plugin.Plugin;

public class ChangeTeamPacketListener extends PacketAdapter {

    private final PlayerManager playerManager;

    public ChangeTeamPacketListener(Plugin plugin, PlayerManager playerManager) {
        super(plugin, PacketType.Play.Server.SCOREBOARD_TEAM);
        this.playerManager = playerManager;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        var packet = event.getPacket();
        var teamName = packet.getStrings().read(0);
        var players = packet.getSpecificModifier(java.util.Collection.class).read(0);
        if (players instanceof java.util.Collection<?> playerCollection) {
            @SuppressWarnings("unchecked")
            var playerNames = (java.util.Collection<String>) playerCollection;
            var playerActionOpt = packet.getIntegers().read(0);
            if (playerActionOpt != null) {
                switch (playerActionOpt) {
                    case 0: // ADD
                        playerManager.addPlayersToTeam(teamName, playerNames);
                        break;
                    case 1: // REMOVE
                        playerManager.removePlayersFromTeam(teamName, playerNames);
                        break;
                }
            }
        }
    }
}
