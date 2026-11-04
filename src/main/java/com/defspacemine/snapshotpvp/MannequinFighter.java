package com.defspacemine.snapshotpvp;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.MainHand;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class MannequinFighter {

    public final Mannequin mannequin;
    private final Plugin plugin;

    public MannequinFighter(Plugin plugin, Mannequin mannequin) {
        this.plugin = plugin;
        this.mannequin = mannequin;

        // Configure using vanilla Mannequin and LivingEntity methods
        configureMannequin();
        startCombatLoop();
    }

    private void configureMannequin() {
        // Vanilla Mannequin properties
        mannequin.setImmovable(false); // Allow movement if navigating
        mannequin.setMainHand(MainHand.RIGHT);

        // Optional description or skin configuration
        // mannequin.setDescription(Component.text("Combat Bot"));

        // Equipment setup via standard LivingEntity/EntityEquipment methods
        EntityEquipment equipment = mannequin.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(null); // or custom armor
            // equipment.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        }
    }

    private void startCombatLoop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!mannequin.isValid() || mannequin.isDead()) {
                    cancel();
                    return;
                }

                Entity target = findTarget();
                if (target == null) return;

                // Team check using SnapshotPvpPlugin
                if (SnapshotPvpPlugin.isSameTeam(mannequin, target)) {
                    return;
                }

                // Check melee attack range (e.g., within 3-4 blocks squared)
                if (mannequin.getLocation().distanceSquared(target.getLocation()) <= 16.0) {
                    // Use the vanilla attack method directly!
                    mannequin.attack(target);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // Check every 0.5 seconds (10 ticks)
    }

    private Entity findTarget() {
        Entity closest = null;
        double minDistanceSq = Double.MAX_VALUE;

        for (Entity entity : mannequin.getNearbyEntities(12, 6, 12)) {
            if (entity instanceof LivingEntity && !(entity instanceof Mannequin)) {
                // Exclude teammates immediately during search
                if (SnapshotPvpPlugin.isSameTeam(mannequin, entity)) {
                    continue;
                }

                double distSq = mannequin.getLocation().distanceSquared(entity.getLocation());
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq;
                    closest = entity;
                }
            }
        }
        return closest;
    }
}
