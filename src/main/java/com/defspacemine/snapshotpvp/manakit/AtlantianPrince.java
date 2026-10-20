package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.WeatherManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class AtlantianPrince extends ManaKit {

    public AtlantianPrince() {
        super("atlantian_prince", "Atlantian Prince", "[Melee Assassin]", 1);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -189,
            7,
            -185
        );
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");

        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 1)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.STRENGTH, 100, 0)
        );

        p.sendActionBar(
            ChatColor.RED + "Killstreak: " + ChatColor.WHITE + killstreak + "/2"
        );
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        p.setHealth(p.getHealth() + (p.getMaxHealth() - p.getHealth()) * .4);
        p.addPotionEffect(
            new PotionEffect(PotionEffectType.RESISTANCE, 100, 2)
        );
        WeatherManager.instance.queueWeather(
            p.getWorld(),
            WeatherManager.WeatherType.RAIN,
            400L
        );
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inventory = p.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && (item.getType() == Material.BUCKET || item.getType() == Material.LAVA_BUCKET)) {
                inventory.setItem(
                    i,
                    new ItemStack(Material.WATER_BUCKET, item.getAmount())
                );
            }
        }

        ItemStack offHandItem = inventory.getItemInOffHand();
        if (offHandItem != null && (offHandItem.getType() == Material.BUCKET || offHandItem.getType() == Material.LAVA_BUCKET)) {
            inventory.setItemInOffHand(
                new ItemStack(Material.WATER_BUCKET, offHandItem.getAmount())
            );
        }

        p.updateInventory();

        resetKit(p);
    }
}
