package com.defspacemine.snapshotpvp.custommob;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;
import com.defspacemine.snapshotpvp.customegg.CustomMob;

import net.kyori.adventure.text.Component;

public class FITHBomb1 implements CustomMob {
    public String getId() {
        return "fith_bomb1";
    }

    public LivingEntity spawn(Location loc, PersistentDataContainer pdc) {
        Creeper creeper = (Creeper) loc.getWorld().spawnEntity(loc, EntityType.CREEPER);
        PersistentDataContainer cPdc = creeper.getPersistentDataContainer();
        cPdc.set(CustomEggListener.CREEPER_CHAIN, PersistentDataType.BOOLEAN, true);

        String owner = pdc.get(CustomEggListener.OWNER, PersistentDataType.STRING);
        if (owner != null) {
            cPdc.set(CustomEggListener.OWNER, PersistentDataType.STRING, owner);
        }

        creeper.setCustomName("Small Bomb");
        creeper.setCustomNameVisible(true);
        creeper.getAttribute(Attribute.MAX_HEALTH).setBaseValue(16);
        creeper.setHealth(10);
        creeper.getAttribute(Attribute.SCALE).setBaseValue(.75);
        creeper.setMaxFuseTicks(30);
        creeper.setExplosionRadius(4);

        loc.getWorld().playSound(loc, Sound.ENTITY_CREEPER_PRIMED, 1, 0.7f);

        return creeper;
    }
}
