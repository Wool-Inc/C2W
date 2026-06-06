package net.klaaswhite.c2w.managers;

import net.klaaswhite.c2w.worlds.ManagedWorld;
import net.klaaswhite.c2w.interfaces.IManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class WorldManager implements IManager {

    private final ManagedWorld lobbyWorld;
    private final ManagedWorld templateWorld;
    private final ManagedWorld referenceWorld;
    private ManagedWorld draftWorld;
    private ManagedWorld gameWorld;

    public WorldManager(Managers managers){
        this.lobbyWorld = new ManagedWorld("c2w_lobby");
        this.templateWorld = new ManagedWorld("c2w_templates");
        this.referenceWorld = new ManagedWorld("c2w_reference");
    }

    public ManagedWorld getTemplateWorld() {
        return templateWorld;
    }

    @Override
    public void close() throws Exception {

    }
}
