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

public class Squire extends ManaKit {
    final int arrowRestock = 400; // 3 arrows every 20 seconds, they auto reset
    final NamespacedKey arrowRestockCounter = ManaKitListener.MANA_KIT_DATA0;
    final int honorableRestock = 12; // 12 attacks, arrow shots count too!
    final NamespacedKey honorableRestockCounter = ManaKitListener.MANA_KIT_DATA1;

    private ItemStack arrows;
    private ItemStack honorPotion;

    public Squire() {
        super("squire", "Squire", "[Melee Damage]", 1);

        arrows = new ItemStack(Material.ARROW, 3);
        arrows.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);

        honorPotion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) honorPotion.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "Potion of Honor");
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        meta.setColor(Color.fromRGB(3847130));
        meta.addCustomEffect(new PotionEffect(
                PotionEffectType.ABSORPTION,
                250,
                1,
                false,
                true,
                true), true);
        meta.addCustomEffect(new PotionEffect(
                PotionEffectType.REGENERATION,
                250,
                0,
                false,
                true,
                true), true);
        honorPotion.setItemMeta(meta);
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
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
        pdc.set(honorableRestockCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int arrowRestockC = pdc.get(arrowRestockCounter, PersistentDataType.INTEGER);
        int honorableRestockC = pdc.get(honorableRestockCounter, PersistentDataType.INTEGER);

        p.sendActionBar(ChatColor.GREEN + "Arrow Restock: " +
                ChatColor.WHITE + arrowRestockC + "/" + arrowRestock +
                ChatColor.GRAY + "  |  " +
                ChatColor.AQUA + "Honorable Restock: " +
                ChatColor.WHITE + honorableRestockC + "/" + honorableRestock +
                ChatColor.GRAY + "  |  " +
                ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/2");

        if (killstreak >= 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0));
        if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0));

        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, arrowRestockC + 1);

        PlayerInventory inv = p.getInventory();
        if (arrowRestockC >= arrowRestock) {
            SnapshotPvpPlugin.clearInv(inv, Material.ARROW);
            inv.addItem(arrows);
            pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
        }

        if (honorableRestockC >= honorableRestock) {
            inv.addItem(honorPotion);
            pdc.set(honorableRestockCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.ARROW);
        SnapshotPvpPlugin.clearInv(inv, Material.POTION);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, arrowRestock);
        pdc.set(honorableRestockCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) {
            PersistentDataContainer pdc = p.getPersistentDataContainer();
            pdc.set(honorableRestockCounter, PersistentDataType.INTEGER,
                    pdc.get(honorableRestockCounter, PersistentDataType.INTEGER) + 1);
        }
    }
}
