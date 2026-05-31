package net.klaaswhite.c2w.commands.base;

import net.klaaswhite.c2w.commands.CommandPieces.CommandPiece;
import net.klaaswhite.c2w.managers.Managers;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseCommand implements AutoCloseable{

    //region command pieces
    protected CommandPiece initialCommandPiece;

    protected abstract void createCommandChain();
    protected abstract String getCommandName();

    protected Managers managers;

    public BaseCommand(Managers managers){
        this.managers = managers;
        createCommandChain();

        var command = managers.getPlugin().getCommand(getCommandName());
        if (command == null) return;
        command.setExecutor(getExecutor());
        command.setTabCompleter(getTabCompleter());
    }

    private CommandExecutor getExecutor(){
        return new C2WCommandExecutor(this::onCommand);
    }

    private Boolean onCommand(CommandInput input){
        CommandPiece currentCommandPiece = initialCommandPiece;

        for (int i = 0; i < input.strings.length + 1; i++) {

            if (currentCommandPiece == null) break;

            if (i == input.strings.length){
                return currentCommandPiece.execute(input);
            }

            var choice = input.strings[i];
            currentCommandPiece = currentCommandPiece.getNextPiece(choice);
        }

        return false;
    }

    private TabCompleter getTabCompleter(){
        return new C2WTabCompleter(this::onTabComplete);
    }

    private List<String> onTabComplete(CommandInput input) {
        List<String> options = new ArrayList<>();

        CommandPiece currentCommandPiece = initialCommandPiece;

        for (int i = 0; i < input.strings.length; i++) {
            if (currentCommandPiece == null) break;

            if (i != input.strings.length - 1) {
                var choice = input.strings[i];
                currentCommandPiece = currentCommandPiece.getNextPiece(choice);
                continue;
            }

            options = currentCommandPiece.getChoices(input);
        }

        return options;
    }

    @Override
    public void close() throws Exception {
        var command = managers.getPlugin().getCommand(getCommandName());
        if (command == null) return;
        command.setExecutor(null);
        command.setTabCompleter(null);
    }
}
