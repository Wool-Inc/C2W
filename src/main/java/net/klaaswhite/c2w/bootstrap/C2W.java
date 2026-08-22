package net.klaaswhite.c2w.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

public final class C2W extends JavaPlugin {

    public App app;

    @Override
    public void onDisable() {
        try {
            this.app.close();
        }
        catch (Exception ignored) {
        }
    }

    @Override
    public void onEnable() {
        this.app = new App(this);
    }
}
