/*
package net.klaaswhite.c2w.legacy.species;

import net.klaaswhite.c2w.classes.ManagedPlayer;
import net.klaaswhite.c2w.managers.GameManager;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Hashtable;
import java.util.Objects;

public class SpecieStrongman extends Specie{

    private final Hashtable<Integer, Material> emptyTableForLockedSlots = new Hashtable<>();
    private final Hashtable<Integer, Material> lockedSlotsWhenCarrying = new Hashtable<>();

    private Entity carrying = null;

    @Override
    protected Hashtable<Integer, Material> getLockedInventorySlots() {
        if (carrying == null) return emptyTableForLockedSlots;
        return lockedSlotsWhenCarrying;
    }

    @Override
    public boolean onInteractEntity(PlayerInteractEntityEvent playerInteractEntityEvent) {
        if (super.onInteractEntity(playerInteractEntityEvent)) return true;

        if (carrying != null) return false;
        if (affectedPlayer.getPlayer().getInventory().getItemInOffHand().getType() != Material.AIR
        && affectedPlayer.getPlayer().getInventory().getItemInMainHand().getType() != Material.AIR) return false;

        var entity = playerInteractEntityEvent.getRightClicked();

        if (entity instanceof Player player){
            var playerTeam = manager.getPlayerManager().getPlayer(player).getTeam();
            if (Objects.equals(playerTeam, affectedPlayer.getTeam())){
                addCarrying(player);
            }
        } else if (entity instanceof LivingEntity livingEntity){
            if (!(livingEntity instanceof ArmorStand)){
                addCarrying(livingEntity);
            }
        } else if (entity instanceof TNTPrimed primedTNT){
            addCarrying(primedTNT);
        }

        return true;
    }

    @Override
    public boolean onInteract(PlayerInteractEvent playerInteractEvent) {
        if (super.onInteract(playerInteractEvent)) return true;

        if (carrying == null) return false;

        if (playerInteractEvent.hasBlock()) return false;

        affectedPlayer.getPlayer().removePassenger(carrying);
        var lookingDirection = affectedPlayer.getPlayer().getLocation().getDirection();
        lookingDirection.normalize().multiply(1.5);
        carrying.setVelocity(lookingDirection);
        affectedPlayer.getPlayer().getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        carrying = null;
        return true;
    }

    @Override
    public void onApply(GameManager manager, ManagedPlayer p) {
        super.onApply(manager, p);
        lockedSlotsWhenCarrying.put(45, Material.GRAY_DYE);
    }

    @Override
    public void onRespawn(PlayerRespawnEvent playerRespawnEvent) {
        super.onRespawn(playerRespawnEvent);

        carrying = null;
    }

    @Override
    protected int getBaseHealth() {
        return 22;
    }

    private void addCarrying(Entity entity){
        affectedPlayer.getPlayer().addPassenger(entity);
        affectedPlayer.getPlayer().getInventory().setItemInOffHand(new ItemStack(Material.GRAY_DYE));
        carrying = entity;
    }
}
*/
