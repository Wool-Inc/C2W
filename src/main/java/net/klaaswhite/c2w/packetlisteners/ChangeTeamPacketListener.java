package net.klaaswhite.c2w.packetlisteners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import net.klaaswhite.c2w.managers.GameManager;
import net.klaaswhite.c2w.managers.Managers;
import net.klaaswhite.c2w.managers.PlayerManager;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import org.bukkit.plugin.Plugin;

public class ChangeTeamPacketListener extends PacketAdapter {

    private final Managers managers;

    public ChangeTeamPacketListener(Managers managers) {
        super(managers.getPlugin(), PacketType.Play.Server.SCOREBOARD_TEAM);
        this.managers = managers;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        var packet = event.getPacket();
        if (packet.getBytes().getTarget() instanceof ClientboundSetPlayerTeamPacket packetPlayOutScoreboardTeam) {
            var teamName = packetPlayOutScoreboardTeam.getName();
            var players = packetPlayOutScoreboardTeam.getPlayers();
            var playerAction = packetPlayOutScoreboardTeam.getPlayerAction();
            var teamAction = packetPlayOutScoreboardTeam.getTeamAction();
            if (teamAction == null && playerAction != null) {
                switch (playerAction.name()) {
                    case "ADD":
                        managers.get(PlayerManager.class).addPlayersToTeam(teamName, players);
                        break;
                    case "REMOVE":
                        managers.get(PlayerManager.class).removePlayersFromTeam(teamName, players);
                        break;
                }
            }
        }
    }
}
