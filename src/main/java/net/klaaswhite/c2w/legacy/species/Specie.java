/*
package net.klaaswhite.c2w.legacy.species;

import com.google.common.collect.Lists;
import net.klaaswhite.c2w.classes.ManagedPlayer;
import net.klaaswhite.c2w.interfaces.ISpecie;
import net.klaaswhite.c2w.managers.GameManager;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.*;

public abstract class Specie implements ISpecie {

    //region static
    // 39
    // 38
    // 37
    // 36 40
    // 9 - 17
    // 18 - 26
    // 27 - 35
    // 0 - 8

    protected GameManager manager;
    protected ManagedPlayer affectedPlayer;
    //endregion

    //region fields

    private static int convertPacketSlotToInventorySlot(int packetStyle) {
        if (packetStyle <= 4) return 0;
        if (packetStyle <= 8) return packetStyle + 34;
        if (packetStyle <= 35) return packetStyle;
        if (packetStyle <= 44) return packetStyle - 36;
        if (packetStyle == 45) return 40;

        return 0;
    }

    private static int convertInventorySlotToPacketSlot(int inventorySlot) {
        if (inventorySlot <= 8) return inventorySlot + 36;
        if (inventorySlot <= 35) return inventorySlot;
        if (inventorySlot <= 39) return (39 - inventorySlot) + 5;
        if (inventorySlot == 40) return 45;

        return 0;
    }

    private boolean hasSpecialSlots() {
        return !(getLockedInventorySlots().isEmpty() && getAllowedItemsForInventorySlots().isEmpty() && getDisallowedItemsForInventorySlots().isEmpty());
    }

    protected Hashtable<Integer, Material> getLockedInventorySlots() {
        return new Hashtable<>();
    }

    protected Hashtable<Integer, HashSet<Material>> getAllowedItemsForInventorySlots() {
        return new Hashtable<>();
    }

    protected Hashtable<Integer, HashSet<Material>> getDisallowedItemsForInventorySlots() {
        return new Hashtable<>();
    }

    protected int getBaseHealth() {
        return 20;
    }

    protected float getBaseMovementSpeed() {
        return 0.2f;
    }

    //endregion

    //region Lifecycle Events
    @Override
    public void onApply(GameManager manager, ManagedPlayer p) {
        this.manager = manager;
        affectedPlayer = p;

        for (PotionEffect effect : p.getPlayer().getActivePotionEffects())
            p.getPlayer().removePotionEffect(effect.getType());

        ensureEffects();
        ensureInventory();
    }

    @Override
    public void onRemove() {
        removeLockedInventorySlots(affectedPlayer, getLockedInventorySlots().keySet());
        affectedPlayer.getPlayer().setHealthScale(20);
        affectedPlayer.getPlayer().setWalkSpeed(0.2f);
    }

    //endregion

    //region Spigot Events
    //region Player lifecycle
    @Override
    public void onRespawn(PlayerRespawnEvent playerRespawnEvent) {
        ensureEffects();
    }

    @Override
    public void onDeath() {

    }
    //endregion

    //region Damage
    @Override
    public void onHit() {

    }

    @Override
    public void onDamage(int amount) {

    }

    @Override
    public void onProvideHit(ManagedPlayer target) {

    }

    @Override
    public void onKillEntity(EntityDeathEvent entityDeathEvent) {

    }
    //endregion

    //region Inventory related
    @Override
    public void onDropItem(Item droppedItem) {

    }

    @Override
    public void onSwapHand(PlayerSwapHandItemsEvent swapHandEvent) {
        var player = swapHandEvent.getPlayer();
        var heldSlot = player.getInventory().getHeldItemSlot();
        var heldPacketSlot = heldSlot + 35;
        if (canPlaceItemStackInSlot(swapHandEvent.getMainHandItem(), heldPacketSlot) || canPlaceItemStackInSlot(swapHandEvent.getOffHandItem(), 45))
            swapHandEvent.setCancelled(true);
    }

    @Override
    public void onPickupItem(EntityPickupItemEvent pickupItemEvent) {
        if (!hasSpecialSlots()) return;
        var pickedUp = addItemsToInventory(Collections.singleton(pickupItemEvent.getItem().getItemStack()), false);
        pickupItemEvent.setCancelled(true);
        if (pickedUp)
            pickupItemEvent.getItem().remove();
    }

    @Override
    public void onInventoryClick(InventoryClickEvent inventoryClickEvent) {
        var slot = convertInventorySlotToPacketSlot(inventoryClickEvent.getSlot());
        var key = convertInventorySlotToPacketSlot(inventoryClickEvent.getHotbarButton());
        if (inventoryClickEvent.getClickedInventory() == affectedPlayer.getPlayer().getInventory()) {
            switch (inventoryClickEvent.getAction()) {
                case PICKUP_ONE:
                case PICKUP_SOME:
                case PICKUP_HALF:
                case PICKUP_ALL:
                case DROP_ALL_CURSOR:
                case DROP_ONE_CURSOR:
                case DROP_ONE_SLOT:
                case MOVE_TO_OTHER_INVENTORY:
                case SWAP_WITH_CURSOR:
                case PLACE_ALL:
                case PLACE_SOME:
                case PLACE_ONE:
                    inventoryClickEvent.setCancelled(shouldBlockPlace(inventoryClickEvent.getCursor(), slot));
                    break;
                case HOTBAR_SWAP:
                    switch (inventoryClickEvent.getClick()) {
                        case NUMBER_KEY:
                            inventoryClickEvent.setCancelled(shouldBlockSwap(inventoryClickEvent.getCurrentItem(), slot, inventoryClickEvent.getCursor(), key));
                            break;
                        case SWAP_OFFHAND:
                            inventoryClickEvent.setCancelled(shouldBlockSwap(inventoryClickEvent.getCurrentItem(), slot, inventoryClickEvent.getCursor(), 45));
                            break;
                    }

                default:
                    break;
            }
            return;
        }
        switch (inventoryClickEvent.getAction()) {
            case HOTBAR_SWAP:
            case HOTBAR_MOVE_AND_READD:
                switch (inventoryClickEvent.getClick()) {
                    case NUMBER_KEY:
                        inventoryClickEvent.setCancelled(shouldBlockSwap(inventoryClickEvent.getCurrentItem(), slot, inventoryClickEvent.getCursor(), key));
                        if (inventoryClickEvent.isCancelled())
                            affectedPlayer.getPlayer().getInventory().setItem(convertPacketSlotToInventorySlot(slot), inventoryClickEvent.getCursor());
                        break;
                    case SWAP_OFFHAND:
                        inventoryClickEvent.setCancelled(shouldBlockSwap(inventoryClickEvent.getCurrentItem(), slot, inventoryClickEvent.getCursor(), 45));
                        if (inventoryClickEvent.isCancelled())
                            affectedPlayer.getPlayer().getInventory().setItem(convertPacketSlotToInventorySlot(45), inventoryClickEvent.getCursor());
                        break;
                }
                break;
            default:
                break;
        }
    }
    //endregion

    //region Interaction
    @Override
    public boolean onInteractEntity(PlayerInteractEntityEvent playerInteractEntityEvent) {
        return false;
    }

    @Override
    public boolean onInteract(PlayerInteractEvent playerInteractEvent) {
        return false;
    }
    //endregion

    //region Movement

    @Override
    public void onToggleSneak(PlayerToggleSneakEvent playerToggleSneakEvent) {

    }
    //endregion
    //endregion

    //region Protected
    protected void ensureEffects() {
        affectedPlayer.getPlayer().setHealthScale(getBaseHealth());
        affectedPlayer.getPlayer().setWalkSpeed(getBaseMovementSpeed());
    }
    //endregion

    //region private

    private void ensureInventory() {
        var lockedInventorySlots = getLockedInventorySlots();
        var allowedItemsForInventorySlots = getAllowedItemsForInventorySlots();
        var disallowedItemsForInventorySlots = getDisallowedItemsForInventorySlots();
        var replacedItems = new HashSet<ItemStack>();
        if (!lockedInventorySlots.isEmpty()) {
            lockedInventorySlots.forEach((slot, material) -> {
                replacedItems.add(affectedPlayer.getPlayer().getInventory().getItem(convertPacketSlotToInventorySlot(slot)));
                affectedPlayer.getPlayer().getInventory().setItem(convertPacketSlotToInventorySlot(slot), new ItemStack(material, 64));
            });
        }
        if (!allowedItemsForInventorySlots.isEmpty()) {
            allowedItemsForInventorySlots.forEach((slot, material) -> {
                var currentItem = affectedPlayer.getPlayer().getInventory().getItem(convertPacketSlotToInventorySlot(slot));
                if (!canPlaceItemStackInSlot(currentItem, slot)) {
                    replacedItems.add(currentItem);
                    affectedPlayer.getPlayer().getInventory().setItem(convertPacketSlotToInventorySlot(slot), new ItemStack(Material.AIR, 1));
                }
            });
        }
        if (!disallowedItemsForInventorySlots.isEmpty()) {
            disallowedItemsForInventorySlots.forEach((slot, material) -> {
                var currentItem = affectedPlayer.getPlayer().getInventory().getItem(convertPacketSlotToInventorySlot(slot));
                if (!canPlaceItemStackInSlot(currentItem, slot)) {
                    replacedItems.add(currentItem);
                    affectedPlayer.getPlayer().getInventory().setItem(convertPacketSlotToInventorySlot(slot), new ItemStack(Material.AIR, 1));
                }
            });
        }

        addItemsToInventory(replacedItems, true);
    }

    // Check if item from cursor can be place in slot
    private boolean shouldBlockPlace(ItemStack item, int slot) {
        return shouldBlockPickupItemInItemSlot(slot) || !canPlaceItemStackInSlot(item, slot);
    }

    // Should check if item 1 can be place on slot 2 and vise versa
    private boolean shouldBlockSwap(ItemStack item1, int slot1, ItemStack item2, int slot2) {
        if (shouldBlockPickupItemInItemSlot(slot1) || shouldBlockPickupItemInItemSlot(slot2)) return true;
        return !canPlaceItemStackInSlot(item2, slot1) || !canPlaceItemStackInSlot(item1, slot2);
    }

    private boolean canPlaceItemStackInSlot(ItemStack itemStack, int packetSlotNumber) {
        if (getLockedInventorySlots().containsKey(packetSlotNumber)) return false;
        if (itemStack.getType() == Material.AIR) return true;
        if (getAllowedItemsForInventorySlots().containsKey(packetSlotNumber) && !getAllowedItemsForInventorySlots().get(packetSlotNumber).contains(itemStack.getType()))
            return false;
        return !getDisallowedItemsForInventorySlots().containsKey(packetSlotNumber) || !getDisallowedItemsForInventorySlots().get(packetSlotNumber).contains(itemStack.getType());
    }

    private boolean shouldBlockPickupItemInItemSlot(int packetSlotNumber) {
        return getLockedInventorySlots().containsKey(packetSlotNumber);
    }

    private void removeLockedInventorySlots(ManagedPlayer managedPlayer, Set<Integer> lockedSlots) {
        if (lockedSlots == null) return;
        var inventory = managedPlayer.getPlayer().getInventory();

        try {
            lockedSlots.forEach((slotNumber) -> {
                inventory.setItem(convertPacketSlotToInventorySlot(slotNumber), new ItemStack(Material.AIR));
            });
        } catch (Exception e) {
            System.out.println("Exception at inventory unlocking: " + e);
        }
    }

    private boolean canPickupItem(ItemStack itemStack) {
        var canPickup = false;
        PlayerInventory inventory = affectedPlayer.getPlayer().getInventory();
        for (int i = 0; i < 36; i++) {
            if (inventory.getItem(i) != null) continue;
            if (canPlaceItemStackInSlot(itemStack, convertInventorySlotToPacketSlot(i))) {
                canPickup = true;
                break;
            }
        }
        return canPickup;
    }

    private boolean addItemsToInventory(Iterable<ItemStack> itemStackIterable, boolean shouldThrow) {
        ArrayList<ItemStack> items = Lists.newArrayList(itemStackIterable);
        PlayerInventory inventory = affectedPlayer.getPlayer().getInventory();
        for (int i = 0; i < 36; i++) {
            if (inventory.getItem(i) != null) continue;
            for (int j = 0; j < items.size(); j++) {
                if (canPlaceItemStackInSlot(items.get(j), convertInventorySlotToPacketSlot(i))) {
                    inventory.setItem(i, items.get(j));
                    items.remove(j);
                    break;
                }
            }
            if (items.isEmpty()) break;
        }
        if (!items.isEmpty() && shouldThrow) {
            Player player = affectedPlayer.getPlayer();
            int heldItemSlot = inventory.getHeldItemSlot();
            ItemStack heldItem = inventory.getItem(heldItemSlot);
            items.forEach((item) -> {
                inventory.setItem(heldItemSlot, item);
                player.dropItem(true);
            });
            inventory.setItem(heldItemSlot, heldItem);
        }

        return items.isEmpty();
    }

    //endregion
}
*/
