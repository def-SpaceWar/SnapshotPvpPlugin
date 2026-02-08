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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public class Poacher extends ManaKit {
    final int killShot = 15; // 15 attacks = 1 power toxin!
    final NamespacedKey killShotCounter = ManaKitListener.MANA_KIT_DATA0;

    private ItemStack powerfulToxin;

    public Poacher() {
        super("poacher", "Poacher", "[Ranged Sniper]", 1);

        powerfulToxin = new ItemStack(Material.TIPPED_ARROW, 1);
        powerfulToxin.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
        PotionMeta meta = (PotionMeta) powerfulToxin.getItemMeta();

        meta.setDisplayName(ChatColor.WHITE + "Powerful Toxin");
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        meta.setColor(Color.fromRGB(4485121));
        meta.addCustomEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                1600,
                5,
                false,
                true,
                true), true);
        meta.addCustomEffect(new PotionEffect(
                PotionEffectType.MINING_FATIGUE,
                2400,
                1,
                false,
                true,
                true), true);
        meta.addCustomEffect(new PotionEffect(
                PotionEffectType.WEAKNESS,
                3200,
                1,
                false,
                true,
                true), true);
        meta.addCustomEffect(new PotionEffect(
                PotionEffectType.POISON,
                2400,
                4,
                false,
                true,
                true), true);
        meta.addCustomEffect(new PotionEffect(
                PotionEffectType.GLOWING,
                4000,
                0,
                false,
                true,
                true), true);
        powerfulToxin.setItemMeta(meta);
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
        pdc.set(killShotCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int killShotC = pdc.get(killShotCounter, PersistentDataType.INTEGER);

        p.sendActionBar(ChatColor.DARK_GREEN + "Kill Shot: " +
                ChatColor.WHITE + killShotC + "/" + killShot +
                ChatColor.GRAY + "  |  " +
                ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/2");

        if (killstreak == 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
        else if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));

        PlayerInventory inv = p.getInventory();
        if (killShotC >= killShot) {
            p.getInventory().addItem(powerfulToxin);
            pdc.set(killShotCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.TIPPED_ARROW);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(killShotCounter, PersistentDataType.INTEGER, killShot);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) {
            PersistentDataContainer pdc = p.getPersistentDataContainer();
            pdc.set(killShotCounter, PersistentDataType.INTEGER,
                    pdc.get(killShotCounter, PersistentDataType.INTEGER) + 1);
        }
    }
}
