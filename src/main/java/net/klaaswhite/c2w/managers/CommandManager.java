package net.klaaswhite.c2w.managers;

import net.klaaswhite.c2w.commands.C2WCommand;
import net.klaaswhite.c2w.commands.MarkerCommand;
import net.klaaswhite.c2w.commands.SpawnerCommand;
import net.klaaswhite.c2w.commands.WorldCommand;
import net.klaaswhite.c2w.commands.base.BaseCommand;
import net.klaaswhite.c2w.interfaces.IManager;

import java.util.ArrayList;

public class CommandManager implements IManager {

    private final ArrayList<BaseCommand> commands;

    public CommandManager(Managers managers){
        commands = new ArrayList<>();

        commands.add(new SpawnerCommand(managers));
        commands.add(new MarkerCommand(managers));
        commands.add(new C2WCommand(managers));
        commands.add(new WorldCommand(managers));
    }

    @Override
    public void close() throws Exception {
        commands.forEach(command -> {
            try {
                command.close();
            }
            catch (Exception _){}
        });
    }
}
