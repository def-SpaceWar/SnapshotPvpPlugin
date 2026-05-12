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
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public class Frog extends ManaKit {
    final int chargeRestock = 5; // wind charges every 5 attacks
    final NamespacedKey chargeRestockCounter = ManaKitListener.MANA_KIT_DATA0;

    private ItemStack windCharges;

    public Frog() {
        super("frog", "Frog", "[Melee Assassin]", 3);

        windCharges = new ItemStack(Material.WIND_CHARGE, 3);
        windCharges.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(p, "goopshotpeshvp", -184, 7, -185);
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(chargeRestockCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int chargeRestockC = pdc.get(chargeRestockCounter, PersistentDataType.INTEGER);

        p.sendActionBar(ChatColor.BLUE + "Wind Charges: " +
                ChatColor.WHITE + chargeRestockC + "/" + chargeRestock +
                ChatColor.GRAY + "  |  " +
                ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/2");

        if (killstreak >= 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
        if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 100, 0));

        PlayerInventory inv = p.getInventory();
        if (chargeRestockC >= chargeRestock) {
            SnapshotPvpPlugin.clearInv(inv, Material.WIND_CHARGE);
            inv.addItem(windCharges);
            pdc.set(chargeRestockCounter, PersistentDataType.INTEGER, 0);
        }

        ItemStack chestplate = inv.getChestplate();
        ArmorMeta meta = (ArmorMeta) chestplate.getItemMeta();
        meta.setGlider(p.getHealth() > 12);
        chestplate.setItemMeta(meta);
    }

    @Override
    public void onLeaveCombat(Player p) {
        p.clearActivePotionEffects();
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.WIND_CHARGE);
        resetKit(p);

        ItemStack chestplate = p.getInventory().getChestplate();
        ArmorMeta meta = (ArmorMeta) chestplate.getItemMeta();
        meta.setGlider(true);
        chestplate.setItemMeta(meta);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(chargeRestockCounter, PersistentDataType.INTEGER, chargeRestock);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(chargeRestockCounter, PersistentDataType.INTEGER,
                pdc.get(chargeRestockCounter, PersistentDataType.INTEGER) + 1);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(chargeRestockCounter, PersistentDataType.INTEGER, chargeRestock);
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 200, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 0));
    }
}
