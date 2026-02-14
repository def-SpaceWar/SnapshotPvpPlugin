package com.defspacemine.snapshotpvp.manakit;

import java.util.ArrayList;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SpawnEggMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class Mercenary extends ManaKit {
    public static final int LAUNCHER_TICKS = 5;
    public static final int LAUNCHER_RADIUS = -2;
    public static final int AUTO_BOMB_TICKS = 15;
    public static final int AUTO_BOMB_RADIUS = 3;
    public static final int GOOD_SHOT_BHAIYA_RADIUS = -64;

    final int mercRestock = 300; // bomb restock every 15 seconds, they auto reset
    final NamespacedKey mercRestockCounter = ManaKitListener.MANA_KIT_DATA0;

    private ItemStack launchers;
    private ItemStack autoBombs;
    private ItemStack enderpearl;
    private ItemStack goodShotBhaiyas;

    public Mercenary() {
        super("mercenary", "Mercenary", "[Melee Assassin]", 4);

        {
            launchers = new ItemStack(Material.CREEPER_SPAWN_EGG, 4);
            SpawnEggMeta meta = (SpawnEggMeta) launchers.getItemMeta();
            meta.displayName(Component.text("Launcher")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(CustomEggListener.CUSTOM_EGG, PersistentDataType.STRING, "merc_launcher");
            launchers.setItemMeta(meta);
        }

        {
            autoBombs = new ItemStack(Material.CREEPER_SPAWN_EGG, 4);
            SpawnEggMeta meta = (SpawnEggMeta) autoBombs.getItemMeta();
            meta.displayName(Component.text("Auto Bomb")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(CustomEggListener.CUSTOM_EGG, PersistentDataType.STRING, "merc_autobomb");
            autoBombs.setItemMeta(meta);
        }

        enderpearl = new ItemStack(Material.ENDER_PEARL, 1);
        enderpearl.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);

        {
            goodShotBhaiyas = new ItemStack(Material.OCELOT_SPAWN_EGG, 4);
            SpawnEggMeta meta = (SpawnEggMeta) goodShotBhaiyas.getItemMeta();
            meta.displayName(Component.text("good shot bhaiya")
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.BOLD, false)
                    .decoration(TextDecoration.ITALIC, false));
            ArrayList<Component> lore = new ArrayList();
            lore.add(Component.text("bum chidi chidi bum bum")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(CustomEggListener.CUSTOM_EGG, PersistentDataType.STRING, "merc_goodshotbhaiya");
            goodShotBhaiyas.setItemMeta(meta);
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
        pdc.set(mercRestockCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int mercRestockC = pdc.get(mercRestockCounter, PersistentDataType.INTEGER);

        p.sendActionBar(ChatColor.DARK_RED + "Explosive Tactics: " +
                ChatColor.WHITE + mercRestockC + "/" + mercRestock +
                ChatColor.GRAY + "  |  " +
                ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/3");

        if (killstreak >= 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0));
        if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 0));
        if (killstreak >= 3)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));

        pdc.set(mercRestockCounter, PersistentDataType.INTEGER, mercRestockC + 1);

        PlayerInventory inv = p.getInventory();
        if (mercRestockC >= mercRestock) {
            SnapshotPvpPlugin.clearInv(inv, Material.CREEPER_SPAWN_EGG);
            SnapshotPvpPlugin.clearInv(inv, Material.ENDER_PEARL);
            inv.addItem(launchers);
            inv.addItem(CustomEggListener.injectOwner(autoBombs, p));
            inv.addItem(enderpearl);
            pdc.set(mercRestockCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.CREEPER_SPAWN_EGG);
        SnapshotPvpPlugin.clearInv(inv, Material.ENDER_PEARL);
        SnapshotPvpPlugin.clearInv(inv, Material.OCELOT_SPAWN_EGG);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(mercRestockCounter, PersistentDataType.INTEGER, mercRestock);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.OCELOT_SPAWN_EGG);
        inv.addItem(goodShotBhaiyas);
    }
}
