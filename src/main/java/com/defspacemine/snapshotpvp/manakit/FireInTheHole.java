package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SpawnEggMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;

public class FireInTheHole extends ManaKit {
    public static final double SMALL_BOMB_HEALTH = 10;
    public static final double SMALL_BOMB_SCALE = .75;
    public static final int SMALL_BOMB_TICKS = 30;
    public static final int SMALL_BOMB_RADIUS = 3;

    public static final double MEDIUM_BOMB_HEALTH = 15;
    public static final double MEDIUM_BOMB_SCALE = 1;
    public static final int MEDIUM_BOMB_TICKS = 45;
    public static final int MEDIUM_BOMB_RADIUS = 5;

    public static final double LARGE_BOMB_HEALTH = 20;
    public static final double LARGE_BOMB_SCALE = 1.25;
    public static final int LARGE_BOMB_TICKS = 60;
    public static final int LARGE_BOMB_RADIUS = 7;

    public static final double NUKE_HEALTH = 40;
    public static final double NUKE_SCALE = 1.5;
    public static final int NUKE_TICKS = 80;
    public static final int NUKE_RADIUS = 100;

    final int arrowRestock = 300; // 3 arrows every 15 seconds, they auto reset
    final NamespacedKey arrowRestockCounter = ManaKitListener.MANA_KIT_DATA0;
    final int bombersRestock = 6; // 6 melee/ranged/bomb attacks = bombs!
    final NamespacedKey bombersRestockCounter = ManaKitListener.MANA_KIT_DATA1;
    final int nukeRestock = 3; // 3 kills = nuke!
    final NamespacedKey nukeRestockCounter = ManaKitListener.MANA_KIT_DATA2;

    private ItemStack arrows;
    private ItemStack bomb1;
    private ItemStack bomb2;
    private ItemStack bomb3;
    private ItemStack nuke;

    public FireInTheHole() {
        super("fire_in_the_hole", "Fire In The Hole", "[Utility Bomber]", 2);

        {
            arrows = new ItemStack(Material.SPECTRAL_ARROW, 3);
            ItemMeta meta = arrows.getItemMeta();
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, false);
            arrows.setItemMeta(meta);
        }

        {
            bomb1 = new ItemStack(Material.CREEPER_SPAWN_EGG, 4);
            SpawnEggMeta meta = (SpawnEggMeta) bomb1.getItemMeta();
            meta.setItemName("Small Bomb");
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, false);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(CustomEggListener.CUSTOM_EGG, PersistentDataType.STRING, "fith_bomb1");
            bomb1.setItemMeta(meta);
        }

        {
            bomb2 = new ItemStack(Material.CREEPER_SPAWN_EGG, 2);
            SpawnEggMeta meta = (SpawnEggMeta) bomb2.getItemMeta();
            meta.setItemName("Medium Bomb");
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, false);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(CustomEggListener.CUSTOM_EGG, PersistentDataType.STRING, "fith_bomb2");
            bomb2.setItemMeta(meta);
        }

        {
            bomb3 = new ItemStack(Material.CREEPER_SPAWN_EGG, 1);
            SpawnEggMeta meta = (SpawnEggMeta) bomb3.getItemMeta();
            meta.setItemName("Large Bomb");
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, false);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(CustomEggListener.CUSTOM_EGG, PersistentDataType.STRING, "fith_bomb3");
            bomb3.setItemMeta(meta);
        }

        {
            nuke = new ItemStack(Material.CREEPER_SPAWN_EGG, 1);
            SpawnEggMeta meta = (SpawnEggMeta) nuke.getItemMeta();
            meta.setItemName("Nuke");
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, false);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(CustomEggListener.CUSTOM_EGG, PersistentDataType.STRING, "fith_nuke");
            nuke.setItemMeta(meta);
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
        pdc.set(bombersRestockCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int arrowRestockC = pdc.get(arrowRestockCounter, PersistentDataType.INTEGER);
        int bombersRestockC = pdc.get(bombersRestockCounter, PersistentDataType.INTEGER);
        int nukeRestockC = pdc.get(nukeRestockCounter, PersistentDataType.INTEGER);

        p.sendActionBar(ChatColor.YELLOW + "Arrow Restock: " +
                ChatColor.WHITE + arrowRestockC + "/" + arrowRestock +
                ChatColor.GRAY + "  |  " +
                ChatColor.GREEN + "Bomber's Restock: " +
                ChatColor.WHITE + bombersRestockC + "/" + bombersRestock +
                ChatColor.GRAY + "  |  " +
                ChatColor.BLUE + "Nuke: " +
                ChatColor.WHITE + nukeRestockC + "/" + nukeRestock +
                ChatColor.GRAY + "  |  " +
                ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/2");

        if (killstreak >= 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
        if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 1));

        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, arrowRestockC + 1);

        PlayerInventory inv = p.getInventory();
        if (arrowRestockC >= arrowRestock) {
            SnapshotPvpPlugin.clearInv(inv, Material.SPECTRAL_ARROW);
            inv.addItem(arrows);
            pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
        }

        if (bombersRestockC >= bombersRestock) {
            inv.addItem(CustomEggListener.injectOwner(bomb1, p));
            inv.addItem(CustomEggListener.injectOwner(bomb2, p));
            inv.addItem(CustomEggListener.injectOwner(bomb3, p));

            pdc.set(bombersRestockCounter, PersistentDataType.INTEGER, 0);
        }

        if (nukeRestockC >= nukeRestock) {
            p.getInventory().addItem(CustomEggListener.injectOwner(nuke, p));
            pdc.set(nukeRestockCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int nukeRestockC = pdc.get(nukeRestockCounter, PersistentDataType.INTEGER);
        pdc.set(nukeRestockCounter, PersistentDataType.INTEGER, nukeRestockC + 1);
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.SPECTRAL_ARROW);
        SnapshotPvpPlugin.clearInv(inv, Material.CREEPER_SPAWN_EGG);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, arrowRestock);
        pdc.set(bombersRestockCounter, PersistentDataType.INTEGER, bombersRestock);
        pdc.set(nukeRestockCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) {
            PersistentDataContainer pdc = p.getPersistentDataContainer();
            pdc.set(bombersRestockCounter, PersistentDataType.INTEGER,
                    pdc.get(bombersRestockCounter, PersistentDataType.INTEGER) + 1);
        }
    }
}
