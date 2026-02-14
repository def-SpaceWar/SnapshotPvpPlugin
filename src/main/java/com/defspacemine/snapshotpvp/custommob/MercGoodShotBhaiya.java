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

public class MercGoodShotBhaiya implements CustomMob {
    public String getId() {
        return "merc_goodshotbhaiya";
    }

    public LivingEntity spawn(Location loc, PersistentDataContainer pdc) {
        Creeper creeper = (Creeper) loc.getWorld().spawnEntity(loc, EntityType.CREEPER);

        creeper.setCustomName("good shot bhaiya");
        creeper.setCustomNameVisible(true);
        creeper.setMaxFuseTicks(0);
        creeper.setExplosionRadius(Mercenary.GOOD_SHOT_BHAIYA_RADIUS);
        creeper.explode();

        return creeper;
    }
}
