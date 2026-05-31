/*
package net.klaaswhite.c2w.legacy.species;


import net.klaaswhite.c2w.classes.ManagedPlayer;
import net.klaaswhite.c2w.managers.GameManager;
import net.klaaswhite.c2w.legacy.timers.entityApplicationTimers.EntityCloseToEntityApplicationTimer;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.HashSet;
import java.util.Hashtable;

public class SpecieBulwark extends Specie {

    private final Hashtable<Integer, HashSet<Material>> allowedItemsForInventorySlots = new Hashtable<>();
    private final PotionEffect hungerEffect = new PotionEffect(PotionEffectType.HUNGER, PotionEffect.INFINITE_DURATION, 1);
    private final PotionEffect slownessEffect = new PotionEffect(PotionEffectType.SLOWNESS, 1, 1);
    private boolean isSneaking;
    private EntityCloseToEntityApplicationTimer applySlownessTimer;

    @Override
    protected Hashtable<Integer, HashSet<Material>> getDisallowedItemsForInventorySlots() {
        return allowedItemsForInventorySlots;
    }

    @Override
    protected float getBaseMovementSpeed() {
        return 0.14f;
    }

    @Override
    public void onApply(GameManager manager, ManagedPlayer p) {
        var allowedItemsInOffhand = new HashSet<Material>();
        allowedItemsInOffhand.add(Material.PAPER);
        allowedItemsForInventorySlots.put(36, allowedItemsInOffhand);

        applySlownessTimer = new EntityCloseToEntityApplicationTimer(Duration.ofSeconds(1), this::applySlowness, p.getPlayer(), 5);

        isSneaking = p.getPlayer().isSneaking();
        onSneakChange();

        super.onApply(manager, p);
    }

    @Override
    public void onRespawn(PlayerRespawnEvent playerRespawnEvent) {
        super.onRespawn(playerRespawnEvent);

        isSneaking = playerRespawnEvent.getPlayer().isSneaking();
        onSneakChange();
    }

    @Override
    public void onToggleSneak(PlayerToggleSneakEvent playerToggleSneakEvent) {
        super.onToggleSneak(playerToggleSneakEvent);

        var newIsSneaking = playerToggleSneakEvent.isSneaking();
        if (newIsSneaking == isSneaking) return;
        isSneaking = newIsSneaking;
        onSneakChange();
    }

    private void onSneakChange() {
        if (isSneaking)
            affectedPlayer.getPlayer().addPotionEffect(hungerEffect);
        else
            affectedPlayer.getPlayer().removePotionEffect(PotionEffectType.HUNGER);


    }

    private void applySlowness(Entity target) {
        if (!(target instanceof LivingEntity))
            return;

        ((LivingEntity) target).addPotionEffect(slownessEffect);
    }
}
*/
