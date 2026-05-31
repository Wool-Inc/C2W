package net.klaaswhite.c2w.managers;

import net.klaaswhite.c2w.classes.ManagedTeam;
import net.klaaswhite.c2w.classes.Wool;
import net.klaaswhite.c2w.commands.base.BaseCommand;
import net.klaaswhite.c2w.commands.base.CommandInput;
import net.klaaswhite.c2w.events.*;
import net.klaaswhite.c2w.interfaces.IManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Hashtable;

public class GameManager implements IManager {

    private final Managers managers;
    private final EventManager eventManager;

    private GameState gameState;

    private Hashtable<ManagedTeam, ArrayList<Wool>> cappedWools;

    public GameManager(Managers managers) {
        this.managers = managers;
        this.eventManager = this.managers.get(EventManager.class);

        gameState = GameState.NOT_STARTED;

        cappedWools = new Hashtable<>();
    }

    public void init(CommandInput input) {
        if (!(input.commandSender instanceof Player player))
            return;

        if (gameState != GameState.NOT_STARTED){
            player.sendMessage("Game can only be initialized when in not started state (/c2w reload)");
            return;
        }

        eventManager.pushInternalEvent(new InitializeGameEvent(input));
        gameState = GameState.INITIALIZED;
    }

    public void preview(CommandInput input){
        eventManager.pushInternalEvent(new PreviewRequestEvent(input));
    }

    public void start(CommandInput input){
        if (!(input.commandSender instanceof Player player))
            return;

        if (gameState != GameState.INITIALIZED){
            player.sendMessage("Game can only be started after it was initialized (/c2w init)");
            return;
        }


        this.managers.getPlugin().getServer().broadcastMessage("Starting game!");

        this.eventManager.registerInternalEvent(WoolCapturedEvent.class, this::woolCapped);
        gameState = GameState.STARTED;
        this.eventManager.pushInternalEvent(new StartGameEvent());
    }

    public void end(CommandInput input){
        if (!(input.commandSender instanceof Player player))
            return;

        if (gameState != GameState.STARTED){
            player.sendMessage("Game can only be ended after it was started (/c2w start)");
            return;
        }

        end();
    }

    public void end(){
        gameState = GameState.ENDED;

        this.managers.getPlugin().getServer().broadcastMessage("Game has ended!");

        this.eventManager.pushInternalEvent(new EndGameEvent());
    }

    public void woolCapped(WoolCapturedEvent event){
        var player = event.getPlayer();
        var team = player.getTeam();
        var capturedWools = this.cappedWools.get(team);
        if (capturedWools == null){
            var capped = new ArrayList<Wool>();
            capped.add(event.getWool());
            this.cappedWools.put(team, capped);
            return;
        }

        this.managers.getPlugin().getServer().broadcastMessage(team.teamName + " has captured 2 wools and wins the game!");

        end();
    }

    @Override
    public void close() throws Exception {

    }

    private enum GameState {
        NOT_STARTED,
        INITIALIZED,
        STARTED,
        ENDED
    }
}
