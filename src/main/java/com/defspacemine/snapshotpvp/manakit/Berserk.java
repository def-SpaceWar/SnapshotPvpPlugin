package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public class Berserk extends ManaKit {
    final int enrage = 2000; // 26.67 attacks or getting hit 80 times!
    final NamespacedKey enrageCounter = ManaKitListener.MANA_KIT_DATA0;

    private ItemStack ragePotion;

    public Berserk() {
        super("berserk", "Berserk", "[Melee Damage]");

        ragePotion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) ragePotion.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "Potion of Rage");
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        meta.setColor(Color.fromRGB(16711680));
        meta.addCustomEffect(new PotionEffect(
                PotionEffectType.ABSORPTION,
                500,
                1,
                false,
                true,
                true), true);
        meta.addCustomEffect(new PotionEffect(
                PotionEffectType.RESISTANCE,
                500,
                0,
                false,
                true,
                true), true);
        meta.addCustomEffect(new PotionEffect(
                PotionEffectType.STRENGTH,
                500,
                0,
                false,
                true,
                true), true);
        ragePotion.setItemMeta(meta);
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
        pdc.set(enrageCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int enrageC = pdc.get(enrageCounter, PersistentDataType.INTEGER);

        p.sendActionBar(ChatColor.GOLD + "Enrage: " +
                ChatColor.WHITE + enrageC + "/" + enrage +
                ChatColor.GRAY + "  |  " +
                ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/4");

        if (killstreak == 1) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
        }

        if (killstreak >= 2) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
        }

        if (killstreak >= 3) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 1));
        }

        if (killstreak >= 4) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0));
        }

        PlayerInventory inv = p.getInventory();
        if (enrageC >= enrage) {
            p.getInventory().addItem(ragePotion);
            pdc.set(enrageCounter, PersistentDataType.INTEGER, enrageC - enrage);
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        p.sendActionBar(" ");
    }

    @Override
    public void onDeath(Player p, PlayerDeathEvent e) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.POTION);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(enrageCounter, PersistentDataType.INTEGER,
                pdc.get(enrageCounter, PersistentDataType.INTEGER) + 75);
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(enrageCounter, PersistentDataType.INTEGER,
                pdc.get(enrageCounter, PersistentDataType.INTEGER) + 25);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(enrageCounter, PersistentDataType.INTEGER,
                pdc.get(enrageCounter, PersistentDataType.INTEGER) + 500);
        p.setHealth(Math.max(p.getHealth() + 12, p.getMaxHealth()));
    }
}
