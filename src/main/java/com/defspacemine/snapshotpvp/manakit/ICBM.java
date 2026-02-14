package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class ICBM extends ManaKit {
    public static final int RED_TERROR_RADIUS = 3;

    final int ammoRestock = 400; // 20 firework rockets every 20 seconds
    final NamespacedKey ammoRestockCounter = ManaKitListener.MANA_KIT_DATA0;
    final int terrorism = 2; // 2 kills for 5 red terrors
    final NamespacedKey terrorismCounter = ManaKitListener.MANA_KIT_DATA1;

    private ItemStack avgMissiles;
    private ItemStack antimatterMissiles;
    private ItemStack redTerrors;

    public ICBM() {
        super("icbm", "ICBM", "[Ranged Control]", 1);

        {
            avgMissiles = new ItemStack(Material.FIREWORK_ROCKET, 4);
            FireworkMeta meta = (FireworkMeta) avgMissiles.getItemMeta();
            meta.displayName(Component.text("Average Missiles")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));

            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            FireworkEffect effect = FireworkEffect.builder()
                    .with(FireworkEffect.Type.BURST)
                    .withColor(Color.LIME)
                    .withFade(Color.YELLOW)
                    .trail(true)
                    .flicker(true)
                    .build();
           for (int i =0; i < 8; i++) meta.addEffect(effect);
            meta.setPower(7);
            avgMissiles.setItemMeta(meta);
        }

        {
            antimatterMissiles = new ItemStack(Material.FIREWORK_ROCKET, 2);
            FireworkMeta meta = (FireworkMeta) antimatterMissiles.getItemMeta();
            meta.displayName(Component.text("Antimatter Missiles")
                    .color(NamedTextColor.BLACK)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            FireworkEffect effect = FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL)
                    .build();
            for (int i =0; i < 12; i++) meta.addEffect(effect);
            meta.setPower(0);
            antimatterMissiles.setItemMeta(meta);
        }

        {
            redTerrors = new ItemStack(Material.FIREWORK_ROCKET, 5);
            ItemMeta meta = redTerrors.getItemMeta();
            meta.displayName(Component.text("Red Terrors")
                    .color(NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD));
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, false);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(CustomEggListener.RED_TERROR, PersistentDataType.INTEGER, 1);
            redTerrors.setItemMeta(meta);
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
        pdc.set(ammoRestockCounter, PersistentDataType.INTEGER, 0);
        pdc.set(terrorismCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int ammoRestockC = pdc.get(ammoRestockCounter, PersistentDataType.INTEGER);
        int terrorismC = pdc.get(terrorismCounter, PersistentDataType.INTEGER);

        p.sendActionBar(ChatColor.GREEN + "Ammo Restock: " +
                ChatColor.WHITE + ammoRestockC + "/" + ammoRestock +
                ChatColor.GRAY + "  |  " +
                ChatColor.YELLOW + "Terrorism : " +
                ChatColor.WHITE + terrorismC + "/" + terrorism +
                ChatColor.GRAY + "  |  " +
                ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/3");

        if (killstreak >= 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0));
        if (killstreak >= 2 && !p.hasPotionEffect(PotionEffectType.REGENERATION))
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, -1, 0));
        if (killstreak >= 3)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));

        pdc.set(ammoRestockCounter, PersistentDataType.INTEGER, ammoRestockC + 1);

        PlayerInventory inv = p.getInventory();
        if (ammoRestockC >= ammoRestock) {
            p.getInventory().addItem(avgMissiles);
            p.getInventory().addItem(antimatterMissiles);
            pdc.set(ammoRestockCounter, PersistentDataType.INTEGER, 0);
        }

        if (terrorismC >= terrorism) {
            p.getInventory().addItem(redTerrors);
            pdc.set(terrorismCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int terrorismC = pdc.get(terrorismCounter, PersistentDataType.INTEGER);
        pdc.set(terrorismCounter, PersistentDataType.INTEGER, terrorismC + 1);
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.FIREWORK_ROCKET);
        p.clearActivePotionEffects();
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ammoRestockCounter, PersistentDataType.INTEGER, ammoRestock);
        pdc.set(terrorismCounter, PersistentDataType.INTEGER, 0);
    }
}
