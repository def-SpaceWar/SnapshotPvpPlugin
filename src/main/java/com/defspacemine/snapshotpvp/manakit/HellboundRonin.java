package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;

public class HellboundRonin extends ManaKit {

    private final int flameCharge = 15;
    private final NamespacedKey flameChargeCounter = ManaKitListener.MANA_KIT_DATA0;
    private final NamespacedKey soulEndCounter = ManaKitListener.MANA_KIT_DATA1;

    public HellboundRonin() {
        super("hellbound_ronin", "Hellbound Ronin", "[Melee Movement]", 0);
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
            int remainingSec = (soulEndTick - currentTick) / 20;
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
                "/" + flameCharge;
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

        if (p.getHealth() > 20.0 && p.getFoodLevel() < 20) {
            double excessHp = p.getHealth() - 20.0;
            int maxBarsConvertible = (int) (excessHp / 4.0);

            if (maxBarsConvertible > 0) {
                int foodNeeded = 20 - p.getFoodLevel();
                int barsNeeded = (int) Math.ceil(foodNeeded / 2.0);
                int barsToConvert = Math.min(maxBarsConvertible, barsNeeded);

                p.setHealth(p.getHealth() - barsToConvert * 4.0);
                p.setFoodLevel(
                    Math.min(20, p.getFoodLevel() + barsToConvert * 2)
                );
            }
        }

        p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 1));
        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 0)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.STRENGTH, 100, 0)
        );

        if (p.isSprinting() && p.getTicksLived() % 5 == 0) {
            Location trailLoc = p.getLocation().clone().add(0, 1.0, 0);

            new BukkitRunnable() {
                int runsRemaining = 4;

                @Override
                public void run() {
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
                        trailLoc
                            .getWorld()
                            .spawnParticle(
                                Particle.SOUL_FIRE_FLAME,
                                trailLoc,
                                4,
                                0.1,
                                0.1,
                                0.1,
                                0.01
                            );
                        trailLoc
                            .getWorld()
                            .spawnParticle(
                                Particle.SOUL,
                                trailLoc,
                                2,
                                0.05,
                                0.05,
                                0.05,
                                0.01
                            );
                    } else {
                        trailLoc
                            .getWorld()
                            .spawnParticle(
                                Particle.FLAME,
                                trailLoc,
                                4,
                                0.1,
                                0.1,
                                0.1,
                                0.01
                            );
                        trailLoc
                            .getWorld()
                            .spawnParticle(
                                Particle.SMOKE,
                                trailLoc,
                                2,
                                0.05,
                                0.05,
                                0.05,
                                0.01
                            );
                    }

                    double trailDamage = activeSoulMode ? 4.0 : 2.0;

                    for (Entity entity : trailLoc
                        .getWorld()
                        .getNearbyEntities(trailLoc, 0.8, 1.0, 0.8)) {
                        if (
                            entity instanceof LivingEntity target &&
                            !entity.equals(p) &&
                            !isTeammate(p, target)
                        ) {
                            target.setFireTicks(60);

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
                10L
            );
        }
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (e.getDamageSource().getDamageType() == DamageType.INDIRECT_MAGIC) return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int currentTick = (int) p.getWorld().getGameTime();
        int soulEndTick = pdc.getOrDefault(
            soulEndCounter,
            PersistentDataType.INTEGER,
            0
        );

        boolean isSoulActive = soulEndTick > currentTick;

        if (
            isSoulActive &&
            e.getEntity() instanceof LivingEntity target &&
            !isTeammate(p, target)
        ) {
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

            target.damage(4.0, soulDamageSource);
        }
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
