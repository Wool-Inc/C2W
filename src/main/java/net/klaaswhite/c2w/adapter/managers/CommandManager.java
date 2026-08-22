package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.commands.BaseCommand;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class CommandManager implements AutoCloseable {

    private final List<BaseCommand> commands;

    public CommandManager(Plugin plugin, BaseCommand... commands) {
        this.commands = new ArrayList<>(List.of(commands));
    }

    @Override
    public void close() {
        for (var command : commands) {
            try {
                command.close();
            } catch (Exception ignored) {
            }
        }
    }
}
