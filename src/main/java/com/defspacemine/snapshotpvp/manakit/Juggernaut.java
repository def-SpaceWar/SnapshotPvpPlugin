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

public class Juggernaut extends ManaKit {
    public Juggernaut() {
        super("juggernaut", "Juggernaut", "[Melee Damage Tank]");
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
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");

        p.sendActionBar(ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/2");

        if (killstreak >= 2) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1));
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        p.sendActionBar(" ");
    }

    @Override
    public void onDeath(Player p, PlayerDeathEvent e) {
        PlayerInventory inv = p.getInventory();
        resetKit(p);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, -1, 1));
        p.setHealth(28);
    }
}
