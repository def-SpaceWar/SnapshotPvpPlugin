package com.defspacemine.snapshotpvp.custommob;

import org.bukkit.Location;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.defspacemine.snapshotpvp.customegg.CustomEggListener;
import com.defspacemine.snapshotpvp.customegg.CustomMob;
import com.defspacemine.snapshotpvp.manakit.Mercenary;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class MercAutoBomb implements CustomMob {
    public String getId() {
        return "merc_autobomb";
    }

    public LivingEntity spawn(Location loc, PersistentDataContainer pdc) {
        Creeper creeper = (Creeper) loc.getWorld().spawnEntity(loc, EntityType.CREEPER);
        PersistentDataContainer cPdc = creeper.getPersistentDataContainer();
        String owner = pdc.get(CustomEggListener.OWNER, PersistentDataType.STRING);
        if (owner != null) {
            cPdc.set(CustomEggListener.OWNER, PersistentDataType.STRING, owner);
        }

        creeper.customName(Component.text("Auto Bomb")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        creeper.setMaxHealth(1000);
        creeper.setHealth(1000);
        creeper.setCustomNameVisible(true);
        creeper.setMaxFuseTicks(Mercenary.AUTO_BOMB_TICKS);
        creeper.setExplosionRadius(Mercenary.AUTO_BOMB_RADIUS);
        creeper.ignite();

        return creeper;
    }
}
