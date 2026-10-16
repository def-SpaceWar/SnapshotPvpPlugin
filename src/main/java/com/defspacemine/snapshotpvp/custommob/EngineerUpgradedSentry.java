package com.defspacemine.snapshotpvp.custommob;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Skeleton;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;
import com.defspacemine.snapshotpvp.customegg.CustomMob;
import com.defspacemine.snapshotpvp.manakit.Engineer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class EngineerUpgradedSentry implements CustomMob {

    private ItemStack bow;

    public EngineerUpgradedSentry() {
        bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        meta.addEnchant(Enchantment.PUNCH, 4, true);
        meta.addEnchant(Enchantment.POWER, 10, true);
        meta.setUnbreakable(true);
        bow.setItemMeta(meta);
    }

    public String getId() {
        return "engineer_upgraded_sentry";
    }

    public LivingEntity spawn(Location loc, PersistentDataContainer pdc) {
        Skeleton sentry = loc.getWorld().spawn(loc, Skeleton.class);
        PersistentDataContainer dPdc = sentry.getPersistentDataContainer();
        String owner = pdc.get(
            CustomEggListener.OWNER,
            PersistentDataType.STRING
        );
        if (owner != null) dPdc.set(
            CustomEggListener.OWNER,
            PersistentDataType.STRING,
            owner
        );

        sentry.customName(
            Component.text("Sentry")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
        );
        sentry.setMaxHealth(Engineer.SENTRY_HEALTH);
        sentry.setHealth(Engineer.SENTRY_HEALTH);
        sentry.setCustomNameVisible(true);

        sentry.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_BLOCK));
        sentry
            .getEquipment()
            .setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        sentry
            .getEquipment()
            .setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        sentry.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
        sentry.getEquipment().setItemInMainHand(bow);

        sentry.addPotionEffect(
            new PotionEffect(PotionEffectType.GLOWING, -1, 0)
        );
        sentry.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
        sentry.setPersistent(true);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (sentry.isDead() || sentry == null) {
                    cancel();
                    return;
                }

                tick++;
                if (tick >= Engineer.SUMMON_TICKS) {
                    sentry.remove();
                    cancel();
                }
            }
        }.runTaskTimer(SnapshotPvpPlugin.instance, 0, 1);

        return sentry;
    }
}
