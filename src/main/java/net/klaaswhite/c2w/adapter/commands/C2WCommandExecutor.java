package net.klaaswhite.c2w.adapter.commands;

import net.klaaswhite.c2w.domain.commands.CommandInput;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

import java.util.function.Function;

public class C2WCommandExecutor implements CommandExecutor {

    Function<CommandInput, Boolean> onCommand;

    public C2WCommandExecutor(Function<CommandInput, Boolean> onCommand){
        this.onCommand = onCommand;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender commandSender, @NonNull Command command, @NonNull String s, String[] strings) {
        var input = new CommandInput();
        input.commandSender = commandSender;
        input.command = command;
        input.s = s;
        input.strings = strings;

        return onCommand.apply(input);
    }
}
