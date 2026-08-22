package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.domain.commands.CommandPiece;
import net.klaaswhite.c2w.adapter.commands.C2WCommandExecutor;
import net.klaaswhite.c2w.adapter.commands.C2WTabCompleter;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseCommand implements AutoCloseable {

    protected CommandPiece initialCommandPiece;

    protected abstract void createCommandChain();
    protected abstract String getCommandName();

    protected final JavaPlugin plugin;

    public BaseCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    protected void register() {
        var command = plugin.getCommand(getCommandName());
        if (command == null) return;
        command.setExecutor(getExecutor());
        command.setTabCompleter(getTabCompleter());
    }

    private CommandExecutor getExecutor() {
        return new C2WCommandExecutor(this::onCommand);
    }

    private Boolean onCommand(CommandInput input) {
        CommandPiece currentCommandPiece = initialCommandPiece;

        for (int i = 0; i < input.strings.length + 1; i++) {

            if (currentCommandPiece == null) break;

            if (i == input.strings.length) {
                if (currentCommandPiece.isTerminal() == false) {
                    // Reached a non-terminal node at the end of input: this is an
                    // incomplete command, not an error. Report usage instead of failing silently.
                    if (input.commandSender instanceof org.bukkit.command.CommandSender sender) {
                        sender.sendMessage("§cIncomplete command. Use Tab to see available options.");
                    }
                    return false;
                }
                return currentCommandPiece.execute(input);
            }

            var choice = input.strings[i];
            currentCommandPiece = currentCommandPiece.getNextPiece(choice);
        }

        return false;
    }

    private TabCompleter getTabCompleter() {
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
    public void close() {
        var command = plugin.getCommand(getCommandName());
        if (command == null) return;
        command.setExecutor(null);
        command.setTabCompleter(null);
    }
}
