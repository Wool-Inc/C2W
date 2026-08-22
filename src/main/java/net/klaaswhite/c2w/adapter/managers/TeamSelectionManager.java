package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Lets players in the draft world pick their team by walking onto one of the
 * team selection platforms built by {@link WorldManager#createTeamSelectionAreas}
 * (RED_WOOL = Red, BLUE_WOOL = Blue, GLASS = Spectator). Stepping on a platform
 * sets the player's team and syncs it to the Bukkit scoreboard.
 */
public class TeamSelectionManager implements AutoCloseable {

    private final EventManager eventManager;
    private final WorldManager worldManager;
    private final PlayerManager playerManager;
    private final MinecraftManager mc;

    public TeamSelectionManager(
            EventManager eventManager,
            WorldManager worldManager,
            PlayerManager playerManager,
            MinecraftManager mc
    ) {
        this.eventManager = eventManager;
        this.worldManager = worldManager;
        this.playerManager = playerManager;
        this.mc = mc;

        this.eventManager.registerMinecraftEvent(PlayerMoveEvent.class, this::onPlayerMove);
    }

    /** Bukkit move handler — applies a team when a player steps on a selection platform. */
    public void onPlayerMove(PlayerMoveEvent event) {
        var to = event.getTo();
        if (to == null) return;

        // The selection platforms only exist in the draft world.
        var draft = worldManager.getDraftWorld();
        if (draft == null || draft.getWorld() == null) return;
        if (!to.getWorld().getName().equals(draft.getName())) return;

        // Movement-transient: only fire when actually standing on a platform.
        String teamName = WorldManager.resolveTeamSelectionAt(
                to.getBlockX(), to.getBlockY(), to.getBlockZ());
        if (teamName == null) return;

        var player = event.getPlayer();
        var managedPlayer = playerManager.getPlayer(player);
        if (managedPlayer == null) return;

        var team = ManagedTeam.teams.get(teamName);
        if (team == null) return;
        if (team.equals(managedPlayer.getTeam())) return;

        var previous = managedPlayer.getTeam();
        if (previous != null) {
            mc.scoreboards().removePlayerFromTeam(player.getName(), previous.teamName);
        }
        managedPlayer.setTeam(team);
        mc.scoreboards().addPlayerToTeam(player.getName(), teamName);

        player.sendMessage("You joined the " + teamName + " team.");
    }

    @Override
    public void close() {
        // No resources owned — the event subscription lives in EventManager.
    }
}