package net.klaaswhite.c2w.bootstrap;

import net.klaaswhite.c2w.adapter.managers.EntityManager;
import net.klaaswhite.c2w.adapter.managers.EventManager;
import net.klaaswhite.c2w.adapter.managers.PlayerManager;
import net.klaaswhite.c2w.adapter.managers.WorldManager;
import net.klaaswhite.c2w.bootstrap.config.PluginConfig;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.adapter.managers.BoundaryManager;
import net.klaaswhite.c2w.adapter.managers.GameManager;
import net.klaaswhite.c2w.adapter.managers.MarkerManager;
import net.klaaswhite.c2w.adapter.managers.ResourceManager;
import net.klaaswhite.c2w.adapter.managers.StructureCreationManager;
import net.klaaswhite.c2w.domain.managers.StructureManager;
import net.klaaswhite.c2w.adapter.managers.LayoutEditorManager;
import net.klaaswhite.c2w.domain.managers.LayoutManager;
import net.klaaswhite.c2w.domain.game.WoolTimer;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Central container for every long-lived service in the plugin. Constructed
 * once in {@link App}; passed to deep classes (such as {@code Wool}) that
 * would otherwise need a long parameter list. Top-level managers still take
 * their direct dependencies in their constructors so the wiring at the top of
 * the graph is explicit.
 */
public class Managers {

    public JavaPlugin plugin;
    public PluginConfig pluginConfig;
    public EventManager eventManager;
    public WorldManager worldManager;
    public EntityManager entityManager;
    public PlayerManager playerManager;
    public MarkerManager markerManager;
    public BoundaryManager boundaryManager;
    public StructureManager structureManager;
    public StructureCreationManager structureCreationManager;
    public ResourceManager resourceManager;
    public MinecraftManager mc;
    public FolderStructureTypeConfig structureTypeConfig;
    public GameManager gameManager;
    public WoolTimer woolTimer;
    public LayoutEditorManager layoutEditorManager;
    public LayoutManager layoutManager;

    public Managers(JavaPlugin plugin) {
        this.plugin = plugin;
    }
}
