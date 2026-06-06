package net.klaaswhite.c2w.managers;

import net.klaaswhite.c2w.C2W;
import net.klaaswhite.c2w.classes.Lazy;
import net.klaaswhite.c2w.interfaces.IManager;

import java.util.HashMap;
import java.util.Map;

public class Managers implements AutoCloseable {

    private final Map<Class<?>, IManager> managers;
    private final C2W plugin;

    public Managers(C2W plugin){
        this.plugin = plugin;

        this.managers = new HashMap<>();
        createManagers();
    }

    private void createManagers(){
        register(EventManager.class, new EventManager(this));
        register(GameManager.class, new GameManager(this));
        register(EntityManager.class, new EntityManager(this));
        register(MarkerManager.class, new MarkerManager(this));
        register(PlayerManager.class, new PlayerManager(this));
        register(WorldManager.class, new WorldManager(this));
        register(BoundaryManager.class, new BoundaryManager(this));

        register(CommandManager.class, new CommandManager(this));

    }

    private void closeManagers(){
        managers.forEach((_, manager) -> {
            try {
                manager.close();
            } catch (Exception _) {
            }
        });

        managers.clear();
    }

    public void reload(){
        closeManagers();
        createManagers();
    }



    public C2W getPlugin(){
        return plugin;
    }

    public <T extends IManager>
    void register(Class<T> type, T instance) {
        this.managers.put(type, instance);
    }

    private <T extends IManager>
    T getInternal(Class<T> type){
        return type.cast(managers.get(type));
    }

    public <T extends IManager>
    Lazy<T> get(Class<T> type){
        return new Lazy<>(() -> getInternal(type));
    }

    @Override
    public void close() throws Exception {
        closeManagers();
    }
}
