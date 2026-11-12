package com.defspacemine.snapshotpvp.custommob;

import com.defspacemine.snapshotpvp.MannequinFighter;
import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;
import com.defspacemine.snapshotpvp.customegg.CustomMob;
import com.defspacemine.snapshotpvp.manakit.Engineer;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class FighterDummyEasy implements CustomMob {

    public String getId() {
        return "fighter_dummy_easy";
    }

    public LivingEntity spawn(Location loc, PersistentDataContainer pdc) {
        Mannequin mannequin = loc.getWorld().spawn(loc, Mannequin.class);
        new MannequinFighter(SnapshotPvpPlugin.instance, mannequin, MannequinFighter.Difficulty.EASY);

        mannequin.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
        mannequin
            .getEquipment()
            .setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        mannequin
            .getEquipment()
            .setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        mannequin.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));
        mannequin
            .getEquipment()
            .setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        mannequin.getEquipment().setItemInOffHand(new ItemStack(Material.CROSSBOW));
        mannequin.setProfile(
            ResolvableProfile.resolvableProfile().name("defSpaceMine").build()
        );
        mannequin.customName(
            Component.text("Fighter Dummy: ")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
                .append(
                    Component.text("Easy")
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false)
                )
        );
        mannequin.setCustomNameVisible(true);
        mannequin.setPersistent(true);

        return mannequin;
    }
}
