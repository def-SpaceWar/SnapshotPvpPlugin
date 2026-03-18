package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.WeatherManager;

public class AtlantianPrince extends ManaKit {
    public AtlantianPrince() {
        super("atlantian_prince", "Atlantian Prince", "[Melee Assassin]", 1);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");

        if (killstreak >= 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
        if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0));

        p.sendActionBar(ChatColor.RED + "Killstreak: " + ChatColor.WHITE + killstreak + "/2");
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        p.setHealth(p.getHealth() + (p.getMaxHealth() - p.getHealth()) / 4);
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 2));
        WeatherManager.instance.queueWeather(
                p.getWorld(),
                WeatherManager.WeatherType.RAIN,
                400L);
    }

    @Override
    public void onLeaveCombat(Player p) {
        resetKit(p);
    }
}
