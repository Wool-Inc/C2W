package net.klaaswhite.c2w.eventlisteners;

import net.klaaswhite.c2w.managers.EventManager;
import net.klaaswhite.c2w.managers.Managers;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BlockEventListeners implements Listener {
    Managers managers;
    EventManager eventManager;


    public BlockEventListeners(Managers managers, EventManager eventManager){
        this.managers = managers;
        this.eventManager = eventManager;
    }

    @EventHandler
    public void onVaultDispenseLoot(BlockDispenseLootEvent event) {
        /*if (event.getBlock().getType() == Material.VAULT) {
            var itemStack = new ItemStack(Material.GREEN_WOOL, 64);
            List<ItemStack> items = new ArrayList<>();
            items.add(itemStack);

            event.setDispensedLoot(items);
        }*/
    }
}
