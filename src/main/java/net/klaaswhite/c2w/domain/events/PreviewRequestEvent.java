package net.klaaswhite.c2w.domain.events;

import net.klaaswhite.c2w.domain.commands.CommandInput;

public class PreviewRequestEvent implements C2WEvent {
    private final CommandInput commandInput;

    public PreviewRequestEvent(CommandInput commandInput) {
        this.commandInput = commandInput;
    }

    public CommandInput getCommandInput() {
        return this.commandInput;
    }
}
