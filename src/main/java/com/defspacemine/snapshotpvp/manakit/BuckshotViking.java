package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class BuckshotViking extends ManaKit {

    final int ammoRestock = 400; // 3 ammo every 20 seconds, they auto reset
    final NamespacedKey ammoRestockCounter = ManaKitListener.MANA_KIT_DATA0;
    final int gritRestock = 20; // 20 attacks, ammo shots count too!
    final NamespacedKey gritRestockCounter = ManaKitListener.MANA_KIT_DATA1;

    private ItemStack ammo;
    private ItemStack gritPotion;

    public BuckshotViking() {
        super("buckshot_viking", "Buckshot Viking", "[Melee Damage]", 0);

        {
            ammo = new ItemStack(Material.TIPPED_ARROW, 3);
            ammo.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
            PotionMeta meta = (PotionMeta) ammo.getItemMeta();

            meta.setDisplayName(ChatColor.DARK_GRAY + "Ammo");
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.setColor(Color.fromRGB(0x555588));
            meta.addCustomEffect(
                new PotionEffect(
                    PotionEffectType.SLOWNESS,
                    1600,
                    1,
                    false,
                    true,
                    true
                ),
                true
            );
            ammo.setItemMeta(meta);
        }

        gritPotion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) gritPotion.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Potion of Grit");
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        meta.setColor(Color.fromRGB(0xAA0000));
        meta.addCustomEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 4, false, true, true),
            true
        );
        meta.addCustomEffect(
            new PotionEffect(
                PotionEffectType.HASTE,
                100,
                4,
                false,
                true,
                true
            ),
            true
        );
        meta.addCustomEffect(
            new PotionEffect(
                PotionEffectType.INSTANT_HEALTH,
                1,
                1,
                false,
                true,
                true
            ),
            true
        );
        gritPotion.setItemMeta(meta);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -189,
            10,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(ammoRestockCounter, PersistentDataType.INTEGER, 0);
        pdc.set(gritRestockCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int ammoRestockC = pdc.get(
            ammoRestockCounter,
            PersistentDataType.INTEGER
        );
        int gritRestockC = pdc.get(
            gritRestockCounter,
            PersistentDataType.INTEGER
        );

        p.sendActionBar(
            ChatColor.DARK_GRAY +
                "Ammo Restock: " +
                ChatColor.WHITE +
                ammoRestockC +
                "/" +
                ammoRestock +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.DARK_RED +
                "Grit Restock: " +
                ChatColor.WHITE +
                gritRestockC +
                "/" +
                gritRestock +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.RED +
                "Killstreak: " +
                ChatColor.WHITE +
                killstreak +
                "/3"
        );

        if (killstreak == 1 || killstreak == 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 0)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.STRENGTH, 100, 1)
        );
        if (killstreak >= 3) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 1)
        );

        pdc.set(
            ammoRestockCounter,
            PersistentDataType.INTEGER,
            ammoRestockC + 1
        );

        PlayerInventory inv = p.getInventory();
        if (ammoRestockC >= ammoRestock) {
            SnapshotPvpPlugin.clearInv(inv, Material.TIPPED_ARROW);
            inv.addItem(ammo);
            pdc.set(ammoRestockCounter, PersistentDataType.INTEGER, 0);
        }

        if (gritRestockC >= gritRestock) {
            inv.addItem(gritPotion);
            pdc.set(gritRestockCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.TIPPED_ARROW);
        SnapshotPvpPlugin.clearInv(inv, Material.POTION);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ammoRestockCounter, PersistentDataType.INTEGER, ammoRestock);
        pdc.set(gritRestockCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) {
            PersistentDataContainer pdc = p.getPersistentDataContainer();
            pdc.set(
                gritRestockCounter,
                PersistentDataType.INTEGER,
                pdc.get(gritRestockCounter, PersistentDataType.INTEGER) + 1
            );
        }
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ammoRestockCounter, PersistentDataType.INTEGER, ammoRestock);
    }
}
