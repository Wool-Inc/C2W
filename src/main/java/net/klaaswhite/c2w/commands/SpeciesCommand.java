/*
package net.klaaswhite.c2w.commands;

import net.klaaswhite.c2w.managers.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class SpeciesCommand extends BaseCommand {

    public SpeciesCommand(GameManager gameManager) {
        super(gameManager);

    }

    @Override
    protected void createCommandChain() {
        OptionsCommandPiece specieCommand = new OptionsCommandPiece();
        initialCommandPiece = specieCommand;

        ActionCommandPiece getPlayersOptions = new ActionCommandPiece();
        ActionCommandPiece getEnabledSpeciesForSetOptions = new ActionCommandPiece();
        ActionCommandPiece getEnabledSpeciesOptions = new ActionCommandPiece();
        ActionCommandPiece getDisabledSpeciesOptions = new ActionCommandPiece();

        CommandPiece setSpecieCommand = new CommandPiece();
        CommandPiece enableSpecieCommand = new CommandPiece();
        CommandPiece disableSpecieCommand = new CommandPiece();

        getPlayersOptions.tabCompleteAction = this::getAllOnlinePlayers;
        getEnabledSpeciesForSetOptions.tabCompleteAction = this::getAllEnabledSpecieNames;
        getEnabledSpeciesOptions.tabCompleteAction = this::getAllEnabledSpecieNames;
        getDisabledSpeciesOptions.tabCompleteAction = this::getAllDisabledSpecieNames;

        setSpecieCommand.actionToExecute = this::setPlayerSpecie;
        enableSpecieCommand.actionToExecute = this::enableSpecie;
        disableSpecieCommand.actionToExecute = this::disableSpecie;

        getEnabledSpeciesForSetOptions.nextTabCompleteCommand = setSpecieCommand;
        getEnabledSpeciesOptions.nextTabCompleteCommand = enableSpecieCommand;
        getDisabledSpeciesOptions.nextTabCompleteCommand = disableSpecieCommand;

        getPlayersOptions.nextTabCompleteCommand = getEnabledSpeciesForSetOptions;

        specieCommand.nextCommands.put("set", getPlayersOptions);
        specieCommand.nextCommands.put("enable", getEnabledSpeciesOptions);
        specieCommand.nextCommands.put("disable", getDisabledSpeciesOptions);
    }

    @Override
    protected String getCommandName() {
        return "specie";
    }

    private List<String> getAllEnabledSpecieNames(CommandInput commandInput) {
        return Arrays.stream(this.gameManager.getSpecieManager().getEnabledSpecieList()).toList();
    }

    private List<String> getAllDisabledSpecieNames(CommandInput commandInput) {
        return Arrays.stream(this.gameManager.getSpecieManager().getDisabledSpecieList()).toList();
    }

    private List<String> getAllOnlinePlayers(CommandInput commandInput) {
        Player[] players = new Player[Bukkit.getServer().getOnlinePlayers().size()];
        Bukkit.getServer().getOnlinePlayers().toArray(players);

        return Arrays.stream(players).map(Player::getName).toList();
    }

    private void setPlayerSpecie(CommandInput input) {
        if (input.strings.length < 3 || !Objects.equals(input.strings[0], "set")) return;

        Player invoker = input.commandSender.getServer().getPlayer(input.commandSender.getName());
        gameManager.getSpecieManager().setPlayerSpecie(invoker, input.strings[1], input.strings[2]);
    }

    private void enableSpecie(CommandInput input) {
        if (input.strings.length < 2 || !Objects.equals(input.strings[0], "enable")) return;

        Player invoker = input.commandSender.getServer().getPlayer(input.commandSender.getName());
        gameManager.getSpecieManager().enableSpecie(invoker, input.strings[1]);
    }

    private void disableSpecie(CommandInput input) {
        if (input.strings.length < 2 || !Objects.equals(input.strings[0], "disable")) return;

        Player invoker = input.commandSender.getServer().getPlayer(input.commandSender.getName());
        gameManager.getSpecieManager().disableSpecie(invoker, input.strings[1]);
    }
}
*/
