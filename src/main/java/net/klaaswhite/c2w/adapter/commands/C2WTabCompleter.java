package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.domain.commands.CommandInput;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.Function;

public class C2WTabCompleter implements TabCompleter {

    Function<CommandInput, List<String>> onTabComplete;

    public C2WTabCompleter(Function<CommandInput, List<String>> onCommand){
        this.onTabComplete = onCommand;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender commandSender, @NonNull Command command, @NonNull String s, String[] strings) {
        var input = new CommandInput();
        input.commandSender = commandSender;
        input.command = command;
        input.s = s;
        input.strings = strings;

        return onTabComplete.apply(input);
    }
}
