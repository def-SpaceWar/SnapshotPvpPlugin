package com.defspacemine.snapshotpvp.custommob;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.defspacemine.snapshotpvp.customegg.CustomEggListener;
import com.defspacemine.snapshotpvp.customegg.CustomMob;

public class FITHNuke implements CustomMob {
    public String getId() {
        return "fith_nuke";
    }

    public LivingEntity spawn(Location loc, PersistentDataContainer pdc) {
        Creeper creeper = (Creeper) loc.getWorld().spawnEntity(loc, EntityType.CREEPER);

        String owner = pdc.get(CustomEggListener.OWNER, PersistentDataType.STRING);
        if (owner != null) {
            PersistentDataContainer cPdc = creeper.getPersistentDataContainer();
            cPdc.set(CustomEggListener.OWNER, PersistentDataType.STRING, owner);
        }

        creeper.setCustomName("Nuke");
        creeper.setCustomNameVisible(true);
        creeper.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40);
        creeper.setHealth(40);
        creeper.getAttribute(Attribute.SCALE).setBaseValue(1.5);
        creeper.setMaxFuseTicks(80);
        creeper.setExplosionRadius(20);
        creeper.setPowered(true);

        loc.getWorld().playSound(loc, Sound.ENTITY_CREEPER_PRIMED, 1, 0.7f);

        return creeper;
    }
}
