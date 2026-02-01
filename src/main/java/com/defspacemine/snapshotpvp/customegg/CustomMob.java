package com.defspacemine.snapshotpvp.customegg;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;

public interface CustomMob {
    String getId();
    LivingEntity spawn(Location location, PersistentDataContainer pdc);
}
