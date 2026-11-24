package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

public class HellboundRonin extends ManaKit {

    @Override
    public org.bukkit.inventory.ItemStack getIcon() {
        return getIconFromShulker(-187, 10, -185);
    }

    private final int flameCharge = 15;
    private final NamespacedKey flameChargeCounter =
        ManaKitListener.MANA_KIT_DATA0;
    private final NamespacedKey soulEndCounter = ManaKitListener.MANA_KIT_DATA1;

    public HellboundRonin() {
        super("hellbound_ronin", "Hellbound Ronin", "[Melee Movement]", 3);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -187,
            10,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(flameChargeCounter, PersistentDataType.INTEGER, 0);
        pdc.set(soulEndCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        int currentTick = (int) p.getWorld().getGameTime();
        int soulEndTick = pdc.getOrDefault(
            soulEndCounter,
            PersistentDataType.INTEGER,
            0
        );
        boolean isSoulActive = soulEndTick > currentTick;
        int flameChargeC = pdc.getOrDefault(
            flameChargeCounter,
            PersistentDataType.INTEGER,
            0
        );

        String soulStatus;
        if (isSoulActive) {
            soulStatus =
                ChatColor.AQUA +
                "Soul Mode" +
                ChatColor.BOLD +
                ": " +
                ChatColor.WHITE +
                (soulEndTick - currentTick);
        } else {
            soulStatus =
                ChatColor.YELLOW +
                "Flame Charges: " +
                ChatColor.WHITE +
                flameChargeC +
                "/" +
                flameCharge;
        }

        p.sendActionBar(
            soulStatus +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.RED +
                "Killstreak: " +
                ChatColor.WHITE +
                killstreak +
                "/2"
        );

        // if (p.getHealth() > 20.0 && p.getFoodLevel() < 20) {
        //     double excessHp = p.getHealth() - 20.0;
        //     int maxBarsConvertible = (int) (excessHp / 4.0);

        //     if (maxBarsConvertible > 0) {
        //         int foodNeeded = 20 - p.getFoodLevel();
        //         int barsNeeded = (int) Math.ceil(foodNeeded / 2.0);
        //         int barsToConvert = Math.min(maxBarsConvertible, barsNeeded);

        //         p.setHealth(p.getHealth() - barsToConvert * 4.0);
        //         p.setFoodLevel(
        //             Math.min(20, p.getFoodLevel() + barsToConvert * 2)
        //         );
        //     }
        // }

        PotionEffect hunger = p.getPotionEffect(PotionEffectType.HUNGER);
        if (hunger == null) p.addPotionEffect(
            new PotionEffect(PotionEffectType.HUNGER, -1, 0)
        );
        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 0)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.STRENGTH, 100, 0)
        );

        if (p.getTicksLived() % 5 == 0) {
            Location trailLoc = p.getLocation().clone().add(0, 1.0, 0);
            spawnFlameHazard(p, pdc, trailLoc, 4, 10L);
        }
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (
            e.getDamageSource().getDamageType() == DamageType.INDIRECT_MAGIC
        ) return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int currentTick = (int) p.getWorld().getGameTime();
        int soulEndTick = pdc.getOrDefault(
            soulEndCounter,
            PersistentDataType.INTEGER,
            0
        );

        boolean isSoulActive = soulEndTick > currentTick;

        SnapshotPvpPlugin.server.sendMessage(
            Component.text("Cause: " + e.getCause())
        );

        if (
            isSoulActive &&
            e.getEntity() instanceof LivingEntity target &&
            !isTeammate(p, target)
        ) {
            for (Location arcLoc : getSweepArcLocations(p)) {
                spawnFlameHazard(p, pdc, arcLoc, 4, 10L);
            }

            Location loc = target.getLocation().add(0, 1.0, 0);
            loc.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                loc,
                8,
                0.2,
                0.2,
                0.2,
                0.05
            );
            loc.getWorld().spawnParticle(
                Particle.SOUL,
                loc,
                4,
                0.2,
                0.2,
                0.2,
                0.02
            );
            loc.getWorld().playSound(
                loc,
                Sound.PARTICLE_SOUL_ESCAPE,
                0.8f,
                1.2f
            );

            DamageSource soulDamageSource = DamageSource.builder(
                DamageType.INDIRECT_MAGIC
            )
                .withCausingEntity(p)
                .withDirectEntity(p)
                .build();

            target.damage(2.0, soulDamageSource);
        }
    }

    private List<Location> getSweepArcLocations(Player p) {
        List<Location> points = new ArrayList<>();
        Location eyeOrBody = p.getLocation().clone().add(0, 1.0, 0);
        double baseYawRad = Math.toRadians(p.getLocation().getYaw());

        // Forward direction in Minecraft yaw: (-sin(yaw), 0, cos(yaw))
        double radius = 2.2;
        double halfArcRad = Math.toRadians(65.0);
        int samples = 5;

        for (int i = 0; i < samples; i++) {
            double t = samples == 1 ? 0.0 : (double) i / (samples - 1);
            double angleOffset = -halfArcRad + t * (2.0 * halfArcRad);
            double yaw = baseYawRad + angleOffset;

            Vector offset = new Vector(
                -Math.sin(yaw) * radius,
                0.0,
                Math.cos(yaw) * radius
            );
            points.add(eyeOrBody.clone().add(offset));
        }
        return points;
    }

    private void spawnFlameHazard(
        Player p,
        PersistentDataContainer pdc,
        Location hazardLoc,
        int totalRuns,
        long periodTicks
    ) {
        if (hazardLoc.getWorld() == null) return;

        new BukkitRunnable() {
            int runsRemaining = totalRuns;

            @Override
            public void run() {
                if (isInShadowWorld(p)) {
                    this.cancel();
                    return;
                }
                if (runsRemaining <= 0) {
                    this.cancel();
                    return;
                }
                runsRemaining--;

                int now = (int) p.getWorld().getGameTime();
                int soulEnd = pdc.getOrDefault(
                    soulEndCounter,
                    PersistentDataType.INTEGER,
                    0
                );
                boolean activeSoulMode = soulEnd > now;

                if (activeSoulMode) {
                    hazardLoc
                        .getWorld()
                        .spawnParticle(
                            Particle.SOUL_FIRE_FLAME,
                            hazardLoc,
                            4,
                            0.1,
                            0.1,
                            0.1,
                            0.01
                        );
                    hazardLoc
                        .getWorld()
                        .spawnParticle(
                            Particle.SOUL,
                            hazardLoc,
                            2,
                            0.05,
                            0.05,
                            0.05,
                            0.01
                        );
                } else {
                    hazardLoc
                        .getWorld()
                        .spawnParticle(
                            Particle.FLAME,
                            hazardLoc,
                            4,
                            0.1,
                            0.1,
                            0.1,
                            0.01
                        );
                    hazardLoc
                        .getWorld()
                        .spawnParticle(
                            Particle.SMOKE,
                            hazardLoc,
                            2,
                            0.05,
                            0.05,
                            0.05,
                            0.01
                        );
                }

                double trailDamage = activeSoulMode ? 2.0 : 1.0;

                for (Entity entity : hazardLoc
                    .getWorld()
                    .getNearbyEntities(hazardLoc, 0.8, 1.0, 0.8)) {
                    if (
                        entity instanceof LivingEntity target &&
                        !entity.equals(p) &&
                        !isTeammate(p, target)
                    ) {
                        target.setFireTicks(30);

                        DamageSource damageSource = DamageSource.builder(
                            DamageType.INDIRECT_MAGIC
                        )
                            .withCausingEntity(p)
                            .withDirectEntity(p)
                            .build();

                        target.damage(trailDamage, damageSource);

                        if (!activeSoulMode && target instanceof Player) {
                            int currentCharge =
                                pdc.getOrDefault(
                                    flameChargeCounter,
                                    PersistentDataType.INTEGER,
                                    0
                                ) + 1;

                            if (currentCharge >= flameCharge) {
                                pdc.set(
                                    flameChargeCounter,
                                    PersistentDataType.INTEGER,
                                    0
                                );
                                pdc.set(
                                    soulEndCounter,
                                    PersistentDataType.INTEGER,
                                    (int) p.getWorld().getGameTime() + 300
                                );

                                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 300, 1));

                                p.getWorld().playSound(
                                    p.getLocation(),
                                    Sound.ENTITY_WITHER_SPAWN,
                                    0.6f,
                                    1.4f
                                );
                            } else {
                                pdc.set(
                                    flameChargeCounter,
                                    PersistentDataType.INTEGER,
                                    currentCharge
                                );
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(
            SnapshotPvpPlugin.getPlugin(SnapshotPvpPlugin.class),
            0L,
            periodTicks
        );
    }

    private boolean isTeammate(Player p1, Entity entity) {
        if (!(entity instanceof Player p2)) return false;
        if (p1.equals(p2)) return false;
        Team t1 = SnapshotPvpPlugin.getTeam(p1);
        Team t2 = SnapshotPvpPlugin.getTeam(p2);
        return t1 != null && t1.equals(t2);
    }

    @Override
    public void onLeaveCombat(Player p) {
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        resetKit(p);
    }
}
