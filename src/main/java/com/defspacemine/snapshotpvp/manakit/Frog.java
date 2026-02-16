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

public class Frog extends ManaKit {
    public Frog() {
        super("frog", "Frog", "[Melee Movement]", 3);
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
        p.removePotionEffect(PotionEffectType.BLINDNESS);

        if (killstreak >= 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
        if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 100, 0));
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        p.clearActivePotionEffects();
        resetKit(p);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 200, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 0));
    }
}
