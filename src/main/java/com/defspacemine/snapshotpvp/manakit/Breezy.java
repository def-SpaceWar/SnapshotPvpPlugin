package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public class Breezy extends ManaKit {
    public Breezy() {
        super("breezy", "Breezy", "[Melee Movement]", 2);
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

        if (killstreak >= 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
        if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 0));
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        p.clearActivePotionEffects();
        resetKit(p);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, -1, 1));
    }
}
