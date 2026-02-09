package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public class Tridentite extends ManaKit {
    final NamespacedKey hasTridentCounter = ManaKitListener.MANA_KIT_DATA0;
    final int tridentReturn = 600; // 30 seconds before trident auto return
    final NamespacedKey tridentReturnCounter = ManaKitListener.MANA_KIT_DATA1;

    private ItemStack trident;

    public Tridentite() {
        super("tridentite", "Tridentite", "[Melee Movement]", 3);

        trident = new ItemStack(Material.TRIDENT);
        ItemMeta meta = trident.getItemMeta();
        meta.addEnchant(Enchantment.IMPALING, 5, true);
        meta.setUnbreakable(true);
        trident.setItemMeta(meta);
    }

    @Override
    public void giveKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        resetKit(p);

        // give items
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(tridentReturnCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int hasTridentC = pdc.get(hasTridentCounter, PersistentDataType.INTEGER);
        int tridentReturnC = pdc.get(tridentReturnCounter, PersistentDataType.INTEGER);

        String displayMessage = ChatColor.RED + "Killstreak: " + ChatColor.WHITE + killstreak + "/4";

        PlayerInventory inv = p.getInventory();
        if (inv.contains(Material.TRIDENT) ||
                inv.getItemInOffHand().getType() == Material.TRIDENT) {
            displayMessage = ChatColor.AQUA + "Trident!"
                    + ChatColor.GRAY + " | " + displayMessage;
            pdc.set(tridentReturnCounter, PersistentDataType.INTEGER, 0);
            pdc.set(hasTridentCounter, PersistentDataType.INTEGER, 1);

            if (hasTridentC == 0) {
                double health = p.getHealth();
                double maxHealth = p.getMaxHealth();
                p.setHealth(Math.min(maxHealth, health + (maxHealth - health) * .2));
            }
        } else {
            displayMessage = ChatColor.DARK_AQUA + "Trident: " + ChatColor.WHITE + tridentReturnC + "/"
                    + tridentReturn
                    + ChatColor.GRAY + " | " + displayMessage;
            pdc.set(tridentReturnCounter, PersistentDataType.INTEGER, tridentReturnC + 1);
            pdc.set(hasTridentCounter, PersistentDataType.INTEGER, 0);

            p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 80, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2));
        }

        p.sendActionBar(displayMessage);

        p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 100, 0));
        if (killstreak >= 1 && !p.hasPotionEffect(PotionEffectType.HEALTH_BOOST))
            p.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, -1, 2));
        if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0));

        if (tridentReturnC >= tridentReturn) {
            SnapshotPvpPlugin.clearInv(inv, Material.CHORUS_FRUIT);
            retrieveTrident(p);
            pdc.set(tridentReturnCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        if (!inv.contains(Material.TRIDENT) &&
                inv.getItemInOffHand().getType() != Material.TRIDENT)
            retrieveTrident(p);
        p.clearActivePotionEffects();
        resetKit(p);
    }

    private void retrieveTrident(Player p) {
        p.getInventory().addItem(trident);
        for (Entity e : p.getWorld().getEntities())
            if (e instanceof Trident t)
                if (t.getOwnerUniqueId().equals(p.getUniqueId()))
                    t.remove();
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(hasTridentCounter, PersistentDataType.INTEGER, 1);
        pdc.set(tridentReturnCounter, PersistentDataType.INTEGER, 0);
    }
}
