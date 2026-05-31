/*
package net.klaaswhite.c2w.legacy.species;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Hashtable;

public class SpecieMessenger extends Specie {

    @Override
    protected Hashtable<Integer, Material> getLockedInventorySlots() {
        var lockedInventorySlots = new Hashtable<Integer, Material>();


        lockedInventorySlots.put(17, Material.PAPER);

        return lockedInventorySlots;
    }

    @Override
    protected int getBaseHealth() {
        return 18;
    }

    @Override
    protected float getBaseMovementSpeed() {
        return 0.25f;
    }

    @Override
    protected void ensureEffects() {
        super.ensureEffects();
        affectedPlayer.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 1));
    }
}
*/
