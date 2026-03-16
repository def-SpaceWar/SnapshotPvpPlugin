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

import com.defspacemine.snapshotpvp.customegg.CustomEggListener;
import com.defspacemine.snapshotpvp.customegg.CustomMob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class EngineerSentry implements CustomMob {
    private ItemStack bow;

    public EngineerSentry() {
        bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        meta.addEnchant(Enchantment.PUNCH, 2, true);
        meta.addEnchant(Enchantment.POWER, 5, true);
        meta.setUnbreakable(true);
        bow.setItemMeta(meta);
    }

    public String getId() {
        return "engineer_sentry";
    }

    public LivingEntity spawn(Location loc, PersistentDataContainer pdc) {
        Skeleton sentry = loc.getWorld().spawn(loc, Skeleton.class);
        PersistentDataContainer dPdc = sentry.getPersistentDataContainer();
        String owner = pdc.get(CustomEggListener.OWNER, PersistentDataType.STRING);
        if (owner != null)
            dPdc.set(CustomEggListener.OWNER, PersistentDataType.STRING, owner);

        sentry.customName(Component.text("Sentry")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        sentry.setMaxHealth(200);
        sentry.setHealth(200);
        sentry.setCustomNameVisible(true);

        sentry.getEquipment().setHelmet(new ItemStack(Material.IRON_BLOCK));
        sentry.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        sentry.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        sentry.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));
        sentry.getEquipment().setItemInMainHand(bow);

        sentry.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, -1, 255));
        sentry.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, -1, 0));
        sentry.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
        sentry.setPersistent(true);

        return sentry;
    }
}
