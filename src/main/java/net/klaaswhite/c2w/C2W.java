package net.klaaswhite.c2w;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import net.klaaswhite.c2w.eventlisteners.EntityEventListeners;
import net.klaaswhite.c2w.eventlisteners.PlayerEventListeners;
import net.klaaswhite.c2w.eventlisteners.BlockEventListeners;
import net.klaaswhite.c2w.managers.Managers;
import net.klaaswhite.c2w.packetlisteners.ChangeTeamPacketListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class C2W extends JavaPlugin {

    public Managers managers;

    @Override
    public void onDisable() {
        try {
            this.managers.close();
        }
        catch (Exception _){}

    }

    @Override
    public void onEnable() {
        this.managers = new Managers(this);
    }
}
