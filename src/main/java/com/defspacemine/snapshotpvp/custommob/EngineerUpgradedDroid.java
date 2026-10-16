package com.defspacemine.snapshotpvp.custommob;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.ItemStack;
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

public class EngineerUpgradedDroid implements CustomMob {
    public String getId() {
        return "engineer_upgraded_droid";
    }

    public LivingEntity spawn(Location loc, PersistentDataContainer pdc) {
        WitherSkeleton droid = loc.getWorld().spawn(loc, WitherSkeleton.class);
        PersistentDataContainer dPdc = droid.getPersistentDataContainer();
        String owner = pdc.get(CustomEggListener.OWNER, PersistentDataType.STRING);
        if (owner != null)
            dPdc.set(CustomEggListener.OWNER, PersistentDataType.STRING, owner);

        droid.customName(Component.text("Upgraded Droid")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        droid.setMaxHealth(Engineer.DROID_HEALTH);
        droid.setHealth(Engineer.DROID_HEALTH);
        droid.setCustomNameVisible(true);

        droid.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_BLOCK));
        droid.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        droid.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        droid.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        droid.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));

        droid.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, -1, 4));
        droid.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, -1, 4));
        droid.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, -1, 0));
        droid.setPersistent(true);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (droid.isDead() || droid == null) {
                    cancel();
                    return;
                }

                tick++;
                if (tick >= Engineer.SUMMON_TICKS) {
                    droid.remove();
                    cancel();
                }
            }
        }.runTaskTimer(SnapshotPvpPlugin.instance, 0, 1);

        return droid;
    }
}
