package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

public class Shulkian extends ManaKit {
    final int arrowRestock = 100; // 4 arrows every 5 seconds, they auto reset
    final NamespacedKey arrowRestockCounter = ManaKitListener.MANA_KIT_DATA0;

    private ItemStack arrows;

    public Shulkian() {
        super("shulkian", "Shulkian", "[Ranged Knockback Tank]", 2);

        {
            arrows = new ItemStack(Material.TIPPED_ARROW, 4);
            PotionMeta meta = (PotionMeta) arrows.getItemMeta();
            meta.displayName(Component.text("Arrow of Levitation")
                    .decoration(TextDecoration.ITALIC, false));
            meta.addCustomEffect(
                    new PotionEffect(
                            PotionEffectType.LEVITATION,
                            480,
                            1),
                    true);
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            arrows.setItemMeta(meta);
        }
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
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, arrowRestock);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int arrowRestockC = pdc.get(arrowRestockCounter, PersistentDataType.INTEGER);

        p.sendActionBar(ChatColor.LIGHT_PURPLE + "Reload: " +
                ChatColor.WHITE + arrowRestockC + "/" + arrowRestock +
                ChatColor.GRAY + "  |  " +
                ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/3");

        if (killstreak >= 1 && !p.hasPotionEffect(PotionEffectType.HEALTH_BOOST))
            p.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, -1, 1));
        switch (killstreak) {
            case 0, 1:
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 3));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 2));
                break;
            case 2:
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 2));
                break;
            default:
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 3));
                break;
        }

        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, arrowRestockC + 1);

        PlayerInventory inv = p.getInventory();
        if (arrowRestockC >= arrowRestock) {
            SnapshotPvpPlugin.clearInv(inv, Material.TIPPED_ARROW);
            inv.addItem(arrows);
            pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.TIPPED_ARROW);
        p.clearActivePotionEffects();
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, arrowRestock);
    }
}
