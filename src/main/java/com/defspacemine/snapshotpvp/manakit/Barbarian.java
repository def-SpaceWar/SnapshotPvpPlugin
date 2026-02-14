package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public class Barbarian extends ManaKit {
    final int bloodlust = 15; // 15 attacks given above 5 hearts gives regeneration
    final NamespacedKey bloodlustCounter = ManaKitListener.MANA_KIT_DATA0;
    final int secondWind = 15; // 15 hits taken below 5 hearts gives absorption
    final NamespacedKey secondWindCounter = ManaKitListener.MANA_KIT_DATA1;

    public Barbarian() {
        super("barbarian", "Barbarian", "[Melee Knockback]", 1);
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
        pdc.set(bloodlustCounter, PersistentDataType.INTEGER, 0);
        pdc.set(secondWindCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int bloodlustC = pdc.get(bloodlustCounter, PersistentDataType.INTEGER);
        int secondWindC = pdc.get(secondWindCounter, PersistentDataType.INTEGER);

        String displayMessage = ChatColor.RED + "Killstreak: " + ChatColor.WHITE + killstreak + "/2";

        if (p.getHealth() > 10) {
            pdc.set(secondWindCounter, PersistentDataType.INTEGER, 0);
            displayMessage = ChatColor.GOLD + "Bloodlust: " + ChatColor.WHITE + bloodlustC + "/"
                    + bloodlust
                    + ChatColor.GRAY + " | " + displayMessage;

            p.addPotionEffect(new PotionEffect(
                    PotionEffectType.STRENGTH,
                    100,
                    (killstreak > 1) ? 1 : 0));

            if (bloodlustC >= bloodlust) {
                pdc.set(bloodlustCounter, PersistentDataType.INTEGER, 0);
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.REGENERATION,
                        200,
                        0));
            }
        } else {
            pdc.set(bloodlustCounter, PersistentDataType.INTEGER, 0);
            displayMessage = ChatColor.AQUA + "Second Wind: " + ChatColor.WHITE + secondWindC + "/"
                    + secondWind
                    + ChatColor.GRAY + " | " + displayMessage;

            p.addPotionEffect(new PotionEffect(
                    PotionEffectType.RESISTANCE,
                    100,
                    (killstreak > 0) ? 1 : 0));

            if (secondWindC >= secondWind) {
                pdc.set(secondWindCounter, PersistentDataType.INTEGER, 0);
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.ABSORPTION,
                        200,
                        0));
            }
        }

        p.sendActionBar(displayMessage);

    }

    @Override
    public void onLeaveCombat(Player p) {
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(bloodlustCounter, PersistentDataType.INTEGER, 0);
        pdc.set(secondWindCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (p.getHealth() <= 10)
            return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(bloodlustCounter, PersistentDataType.INTEGER,
                pdc.get(bloodlustCounter, PersistentDataType.INTEGER) + 1);
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        if (p.getHealth() > 10)
            return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(secondWindCounter, PersistentDataType.INTEGER,
                pdc.get(secondWindCounter, PersistentDataType.INTEGER) + 1);
    }
}
