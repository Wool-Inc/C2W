package net.klaaswhite.c2w.managers;

import net.klaaswhite.c2w.classes.ManagedPlayer;
import net.klaaswhite.c2w.classes.ManagedTeam;
import net.klaaswhite.c2w.commands.base.CommandInput;
import net.klaaswhite.c2w.events.InitializeGameEvent;
import net.klaaswhite.c2w.events.PreviewRequestEvent;
import net.klaaswhite.c2w.interfaces.IManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Team;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;


public class PlayerManager implements IManager {

    private final Managers managers;
    private final EventManager eventManager;

    private final Hashtable<UUID, ManagedPlayer> playersByUUID;
    private final Hashtable<String, ManagedPlayer> playersByName;
    private final Hashtable<Player, ManagedPlayer> playersByBukkitPlayer;
    private final ArrayList<BossBar> bossBars;

    private final Hashtable<ManagedPlayer, ArrayList<Consumer<ManagedPlayer>>> playerDeathListeners;

    public PlayerManager(Managers managers){
        this.managers = managers;
        this.eventManager = this.managers.get(EventManager.class);

        this.eventManager.registerInternalEvent(InitializeGameEvent.class, this::init);

        playersByUUID = new Hashtable<>();
        playersByName = new Hashtable<>();
        playersByBukkitPlayer = new Hashtable<>();
        playerDeathListeners = new Hashtable<>();
        bossBars = new ArrayList<>();

    }

    public ManagedPlayer getPlayer(UUID playerUUID) {
        return playersByUUID.get(playerUUID);
    }

    public ManagedPlayer getPlayer(String playerName) {
        return playersByName.get(playerName);
    }

    public @Nullable ManagedPlayer getPlayer(Player player) {
        return playersByBukkitPlayer.get(player);
    }

    public void init(InitializeGameEvent event){
        if (!(event.getCommandInput().commandSender instanceof Player player))
            return;

        ensureTeams();
        ensurePlayers();

        eventManager.registerMinecraftEvent(PlayerJoinEvent.class, this::onNewPlayer);
        eventManager.registerMinecraftEvent(PlayerDeathEvent.class, this::onPlayerDeath);
        eventManager.registerInternalEvent(PreviewRequestEvent.class, this::preview);

        player.sendMessage("PlayerManager state has been reset, add players to the correct teams, call /c2w preview to view current teams.");
    }

    public void preview(PreviewRequestEvent event){
        if (!(event.getCommandInput().commandSender instanceof Player player))
            return;

        player.sendMessage("Following players are currently known: ");
        ManagedTeam.teams.forEach((name, team) -> {
            team.players.forEach(teamPlayer -> {
                player.sendMessage(team.colour + teamPlayer.getPlayer().getDisplayName());
            });
        });
    }

    private void ensureTeams(){
        var scoreboard = Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard();
        var teams = scoreboard.getTeams();
        teams.forEach(Team::unregister);

        ManagedTeam.teams.put("Red", new ManagedTeam("Red", ChatColor.RED, scoreboard));
        ManagedTeam.teams.put("Blue", new ManagedTeam("Blue", ChatColor.BLUE, scoreboard));
    }

    public void ensurePlayers() {
        Player[] localPlayers = new Player[Bukkit.getServer().getOnlinePlayers().size()];
        Bukkit.getServer().getOnlinePlayers().toArray(localPlayers);

        for(var localPlayer:localPlayers){
            ensurePlayer(localPlayer);
        }
    }

    private void ensurePlayer(Player player){
        var foundPlayer = playersByUUID.get(player.getUniqueId());
        if (foundPlayer != null) {
            playersByBukkitPlayer.put(player, foundPlayer);
            foundPlayer.ensureInMcTeam();
            return;
        }

        var newC2WPlayer = new ManagedPlayer(player);
        playersByUUID.put(player.getUniqueId(), newC2WPlayer);
        playersByName.put(player.getName(), newC2WPlayer);
        playersByBukkitPlayer.put(player, newC2WPlayer);
    }

    public void onNewPlayer(PlayerJoinEvent event) {
        var player = event.getPlayer();

        for(var bossBar : this.bossBars){
            bossBar.addPlayer(player);
        }
    }

    public void registerBossBar(BossBar bossBar) {
        bossBars.add(bossBar);
        for(Player player : Bukkit.getOnlinePlayers()){
            bossBar.addPlayer(player);
        }
    }

    public void addPlayersToTeam(String teamName, Iterable<String> players){
        var team = ManagedTeam.teams.get(teamName);
        if (team == null) return;

        players.forEach(player -> {
            var ctwPlayer = playersByName.get(player);
            if (ctwPlayer != null) {
                ctwPlayer.setTeam(team);
            };
        });
    }

    public void removePlayersFromTeam(String teamName, Iterable<String> players){
        var team = ManagedTeam.teams.get(teamName);
        if (team == null) return;

        players.forEach(player -> {
            var ctwPlayer = playersByName.get(player);
            if (ctwPlayer != null) {
                ctwPlayer.removeTeam(team);
            };
        });
    }

    public void onPlayerDeath(PlayerDeathEvent event) {
        var player = event.getEntity();
        var managedPlayer = playersByBukkitPlayer.get(player);
        if (managedPlayer == null || !playerDeathListeners.containsKey(managedPlayer)) return;

        var listeners = playerDeathListeners.get(managedPlayer);
        for (var listener : listeners)
            listener.accept(managedPlayer);
    }

    public void addPlayerDeathListener(ManagedPlayer player, Consumer<ManagedPlayer> callBack){
        if (playerDeathListeners.containsKey(player))
            playerDeathListeners.get(player).add(callBack);
        else {
            var consumers = new ArrayList<Consumer<ManagedPlayer>>();
            consumers.add(callBack);
            playerDeathListeners.put(player, consumers);
        }
    }

    public void removePlayerDeathListener(ManagedPlayer player, Consumer<ManagedPlayer> callBack){
        if (!playerDeathListeners.containsKey(player))
            return;

        var listeners = playerDeathListeners.get(player);
        listeners.remove(callBack);
        if (listeners.isEmpty())
            playerDeathListeners.remove(player);
    }

    @Override
    public void close() throws Exception {
        ManagedTeam.teams.clear();
    }
}
