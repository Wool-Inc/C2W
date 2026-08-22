package net.klaaswhite.c2w.bootstrap.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import net.klaaswhite.c2w.adapter.managers.PlayerManager;
import org.bukkit.plugin.Plugin;
import java.util.logging.Logger;

public class ChangeTeamPacketListener extends PacketAdapter {

    private final PlayerManager playerManager;
    private static final Logger log = Logger.getLogger("C2W");

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
            log.info("[ChangeTeamPacketListener] mode=" + playerActionOpt
                    + " team=" + teamName + " players=" + playerNames);
            if (playerActionOpt != null) {
                // In Minecraft 1.21.2 (api-version 26.1), SCOREBOARD_TEAM mode values:
                //   0 = CREATE TEAM     1 = REMOVE TEAM     2 = UPDATE TEAM INFO
                //   3 = ADD ENTITIES    4 = REMOVE ENTITIES
                // Vanilla `/team join` sends mode 3; `/team leave` sends mode 4.
                switch (playerActionOpt) {
                    case 3: // ADD ENTITIES
                        log.info("[ChangeTeamPacketListener] -> calling addPlayersToTeam(" + teamName + ", " + playerNames + ")");
                        playerManager.addPlayersToTeam(teamName, playerNames);
                        break;
                    case 4: // REMOVE ENTITIES
                        log.info("[ChangeTeamPacketListener] -> calling removePlayersFromTeam(" + teamName + ", " + playerNames + ")");
                        playerManager.removePlayersFromTeam(teamName, playerNames);
                        break;
                    default:
                        log.info("[ChangeTeamPacketListener] -> ignoring mode " + playerActionOpt);
                }
            }
        }
    }
}
