package net.klaaswhite.c2w.legacy;/*
package me.klaaswhite.ctwreimagined.nmsMethods;

import me.klaaswhite.ctwreimagined.classes.CtwPlayer;
import net.minecraft.world.entity.player.En;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.inventory.ContainerPlayer;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_20_R4.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R4.inventory.CraftItemStack;

import java.util.*;

// 39
// 38
// 37
// 36 40
// 9 - 17
// 18 - 26
// 27 - 35
// 0 - 8


public abstract class InventoryHelper {

    private static int convertPacketSlotToInventorySlot(int packetStyle) {
        if (packetStyle <= 4) return 0;
        if (packetStyle <= 8) return packetStyle + 34;
        if (packetStyle <= 35) return packetStyle;
        if (packetStyle <= 44) return packetStyle - 36;
        if (packetStyle == 45) return 40;

        return 0;
    }

    public static void lockInventorySlots(CtwPlayer ctwPlayer, Hashtable<Integer, org.bukkit.inventory.ItemStack> lockedSlots) {
        try {
            var player = ctwPlayer.getPlayer();
            EntityHuman entityHuman = ((CraftPlayer) player).getHandle();
            Container container = entityHuman.cb;
            PlayerInventory inventory = entityHuman.gc();
            HashSet<ItemStack> replacedItems = new HashSet<>();
            List<ItemStack> lockedItemStacks = new ArrayList<>();

            var slots = container.i;

            for (int i = 1; i < slots.size(); i++) {
                if (lockedSlots.containsKey(i)) {
                    replacedItems.add(inventory.a(i));

                    var oldSlot = slots.get(i);
                    var newSlot = new CtwSlot(oldSlot) {
                        @Override
                        public boolean a(ItemStack itemstack) {
                            return false;
                        }

                        @Override
                        public boolean a(EntityHuman player) {
                            return false;
                        }
                    };
                    slots.set(i, newSlot);

                    var nmsItemStack = CraftItemStack.asNMSCopy(lockedSlots.get(i));
                    //nmsItemStack.setCount(nmsItemStack.getMaxItemStack());
                    nmsItemStack.e(nmsItemStack.j());
                    lockedItemStacks.add(nmsItemStack);
                    inventory.a(convertPacketSlotToInventorySlot(i), nmsItemStack);
                } else {
                    var oldSlot = slots.get(i);

                    var newSlot = new CtwSlot(oldSlot) {
                        @Override
                        public boolean a(ItemStack itemstack) {
                            if (!oldSlot.a(itemstack)) return false;

                            boolean result = true;
                            try {
                                for (var lockedItemStack : lockedItemStacks) {
                                    if (Objects.equals(lockedItemStack, itemstack)) {
                                        result = false;
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                System.out.println("Exception at mayPlace other slots: " + e);
                            }

                            return result;
                        }
                    };

                    slots.set(i, newSlot);
                }
            }

            for (var item : replacedItems) {
                if (item == null) continue;
                inventory.f(item);
            }
        } catch (Exception e) {
            System.out.println("Exception at inventory locking: " + e);
        }
    }

    private static Hashtable<Integer, ItemStack> getItemStacksInLimitedInventorySlots(EntityHuman player, Set<Integer> inventorySlots) {
        PlayerInfo inventory = player.gc();
        var ret = new Hashtable<Integer, ItemStack>();

        inventorySlots.forEach((index) -> {
            ret.put(index, inventory.a(convertInventoryToInventory(index)));
        });

        return ret;
    }

    private static Integer tryGetLimitedInventorySlotOfItem(ItemStack item, EntityHuman player, Set<Integer> inventorySlots) {
        var itemStacksInLimitedSlots = getItemStacksInLimitedInventorySlots(player, inventorySlots);

        AtomicInteger foundIndex = new AtomicInteger(-1);

        itemStacksInLimitedSlots.forEach((index, itemStack) -> {
            if (Objects.equals(itemStack, item)) {
                foundIndex.set(index);
            }
        });

        return foundIndex.get();
    }

    private static boolean canItemBePlacedHere(HashSet<org.bukkit.inventory.ItemStack> allowedItem, ItemStack attemptedItem) {
        var allowed = false;

        try {
            for (var item : allowedItem) {
                var testItemStack = CraftItemStack.asNMSCopy(item);
                allowed = allowed || attemptedItem.a(testItemStack.g());
            }
        } catch (Exception e) {
            allowed = true;
            System.out.println("Exception at setting checking specific slot: " + e);
        }

        return allowed;
    }

    public static void setAllowedItemsInSlots(CtwPlayer ctwPlayer, Hashtable<Integer, HashSet<org.bukkit.inventory.ItemStack>> allowedItemsInSlots) {
        try {
            var player = ctwPlayer.getPlayer();
            var craftInventory = player.getInventory();
            EntityHuman entityHuman = ((CraftPlayer) player).getHandle();
            Container container = entityHuman.cb;
            PlayerInventory inventory = entityHuman.gc();

            var slots = container.i;
            HashSet<ItemStack> replacedItems = new HashSet<>();

            for (int i = 1; i < slots.size(); i++) {
                var localIndex = i;
                var oldSlot = slots.get(i);
                var newSlot = new CtwSlot(oldSlot) {
                    @Override
                    public boolean a(ItemStack itemstack) {
                        if (!oldSlot.a(itemstack)) return false;
                        if (allowedItemsInSlots.containsKey(localIndex) && !canItemBePlacedHere(allowedItemsInSlots.get(localIndex), itemstack))
                            return false;

                        var itemStacksInLimitedSlots = tryGetLimitedInventorySlotOfItem(itemstack, entityHuman, allowedItemsInSlots.keySet());
                        if (itemStacksInLimitedSlots == -1) return true;

                        var currentItemInSlot = inventory.a(convertInventoryToInventory(this.d));
                        return !allowedItemsInSlots.containsKey(itemStacksInLimitedSlots) || canItemBePlacedHere(allowedItemsInSlots.get(itemStacksInLimitedSlots), currentItemInSlot);
                    }

                    @Override
                    public void a(EntityHuman var0, ItemStack var1) {
                        if (CraftItemStack.asBukkitCopy(var1).getType() == Material.GRAY_DYE) {
                            craftInventory.remove(Material.GRAY_DYE);
                        }
                    }

                };

                slots.set(i, newSlot);


                if (allowedItemsInSlots.containsKey(i)) {
                    var foundItem = inventory.a(convertInventoryToInventory(i));
                    if (foundItem == null) continue;

                    if (CraftItemStack.asBukkitCopy(foundItem).getType() == Material.AIR) {
                        craftInventory.setItem(convertInventoryToInventory(i), new org.bukkit.inventory.ItemStack(Material.GRAY_DYE));
                        continue;
                    }

                    var shouldReplace = false;

                    var allowedItemsInSlot = allowedItemsInSlots.get(i);

                    for (var item : allowedItemsInSlot) {
                        var testItemStack = CraftItemStack.asNMSCopy(item);
                        shouldReplace = !foundItem.a(testItemStack.g());
                    }

                    replacedItems.add(foundItem);
                    if (shouldReplace)
                        craftInventory.setItem(convertInventoryToInventory(i), new org.bukkit.inventory.ItemStack(Material.GRAY_DYE));
                }
            }

            for (var item : replacedItems) {
                if (item == null) continue;
                inventory.f(item);
            }
        } catch (Exception e) {
            System.out.println("Exception at setting specific slot: " + e);
        }
    }


    public static void removeLockedInventorySlots(CtwPlayer ctwPlayer, Set<Integer> lockedSlots) {
        if (lockedSlots == null) return;

        var player = ctwPlayer.getPlayer();
        var inventory = player.getInventory();
        EntityHuman entityHuman = ((CraftPlayer) player).getHandle();

        ContainerPlayer container = (ContainerPlayer) entityHuman.cb;

        try {
            var slots = container.i;

            for (int i = 1; i < slots.size(); i++) {
                var slot = slots.get(i);
                if (!slot.getClass().isInstance(CtwSlot.class)) continue;

                var ctwSlot = (CtwSlot) slot;
                slots.set(i, ctwSlot.getOldSlot());
            }

            lockedSlots.forEach((slotNumber) -> {
                inventory.setItem(convertPacketSlotToInventorySlot(slotNumber), new org.bukkit.inventory.ItemStack(Material.AIR));
            });
        } catch (Exception e) {
            System.out.println("Exception at inventory unlocking: " + e);
        }
    }

}
*/
