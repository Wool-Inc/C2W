package net.klaaswhite.c2w.events;

import net.klaaswhite.c2w.commands.base.CommandInput;

public class InitializeGameEvent implements IC2WEvent {
    private final CommandInput commandInput;

    public InitializeGameEvent(CommandInput commandInput){
        this.commandInput = commandInput;
    }

    public CommandInput getCommandInput(){
        return this.commandInput;
    }
}
