/*
package net.klaaswhite.c2w.legacy.species;

import net.klaaswhite.c2w.classes.ManagedPlayer;
import net.klaaswhite.c2w.managers.GameManager;
import net.klaaswhite.c2w.legacy.timers.entityApplicationTimers.EntityCloseToEntityApplicationTimer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;

public class SpecieLuteboi extends Specie {

    private final PotionEffect regenEffect = new PotionEffect(PotionEffectType.REGENERATION, 2, 1);
    private final PotionEffect saturationEffect = new PotionEffect(PotionEffectType.SATURATION, 2, 1);
    private EntityCloseToEntityApplicationTimer applyRegenTimer;

    @Override
    public void onApply(GameManager manager, ManagedPlayer p) {
        super.onApply(manager, p);

        applyRegenTimer = new EntityCloseToEntityApplicationTimer(Duration.ofSeconds(1), this::applyRegen, p.getPlayer(), 5);
        applyRegenTimer.startTimer();
    }

    @Override
    public void onRemove() {
        super.onRemove();

        applyRegenTimer.end();
    }

    @Override
    public void onKillEntity(EntityDeathEvent entityDeathEvent) {
        super.onKillEntity(entityDeathEvent);
        var nearbyEntities = affectedPlayer.getPlayer().getNearbyEntities(5, 5, 5);
        for (var entity : nearbyEntities) {
            if (!(entity instanceof Player player)) continue;
            player.addPotionEffect(saturationEffect);
        }
    }

    private void applyRegen(Entity target) {
        if (!(target instanceof Player)) return;
        ((Player) target).addPotionEffect(regenEffect);
        affectedPlayer.getPlayer().addPotionEffect(regenEffect);
    }
}
*/
