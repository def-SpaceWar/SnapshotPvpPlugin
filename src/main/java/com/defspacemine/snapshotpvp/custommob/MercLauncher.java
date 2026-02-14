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

public class MercLauncher implements CustomMob {
    public String getId() {
        return "merc_launcher";
    }

    public LivingEntity spawn(Location loc, PersistentDataContainer pdc) {
        Creeper creeper = (Creeper) loc.getWorld().spawnEntity(loc, EntityType.CREEPER);

        creeper.customName(Component.text("Launcher")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        creeper.setCustomNameVisible(true);
        creeper.setMaxFuseTicks(Mercenary.LAUNCHER_TICKS);
        creeper.setExplosionRadius(Mercenary.LAUNCHER_RADIUS);
        creeper.ignite();

        return creeper;
    }
}
