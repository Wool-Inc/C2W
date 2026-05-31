package net.klaaswhite.c2w.events;

import net.klaaswhite.c2w.commands.base.CommandInput;

public class PreviewRequestEvent implements IC2WEvent {
    private final CommandInput commandInput;

    public PreviewRequestEvent(CommandInput commandInput){
        this.commandInput = commandInput;
    }

    public CommandInput getCommandInput(){
        return this.commandInput;
    }
}
