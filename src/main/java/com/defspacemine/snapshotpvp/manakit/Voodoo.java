package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class Voodoo extends ManaKit {

    private final double DOLL_HP = 40.0;
    private final int HITS_REQUIRED = 3; // Hits needed before the soul can be bound

    private final NamespacedKey linkedTargetUUID =
        ManaKitListener.MANA_KIT_DATASTR0;
    private final NamespacedKey dollEntityUUID =
        ManaKitListener.MANA_KIT_DATASTR1;
    private final NamespacedKey potentialTargetUUID =
        ManaKitListener.MANA_KIT_DATASTR2;

    private final NamespacedKey potentialHitCount =
        ManaKitListener.MANA_KIT_DATA0;
    private final NamespacedKey isSelfBoundKey =
        ManaKitListener.MANA_KIT_DATABOOL0;

    public Voodoo() {
        super("voodoo", "Voodoo", "[Melee Damage]", 0);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -183,
            10,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);

        pdc.set(linkedTargetUUID, PersistentDataType.STRING, "");
        pdc.set(dollEntityUUID, PersistentDataType.STRING, "");
        pdc.set(potentialTargetUUID, PersistentDataType.STRING, "");

        pdc.set(potentialHitCount, PersistentDataType.INTEGER, 0);
        pdc.set(isSelfBoundKey, PersistentDataType.BOOLEAN, false);

        removeExistingDoll(p);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        Player linkedTarget = getPlayerFromPDC(pdc, linkedTargetUUID);
        Player potentialTarget = getPlayerFromPDC(pdc, potentialTargetUUID);

        Entity doll = getEntityFromPDC(pdc, dollEntityUUID);
        boolean selfBound = pdc.getOrDefault(
            isSelfBoundKey,
            PersistentDataType.BOOLEAN,
            false
        );
        int hits = pdc.getOrDefault(
            potentialHitCount,
            PersistentDataType.INTEGER,
            0
        );

        StringBuilder actionBar = new StringBuilder();

        if (linkedTarget != null && linkedTarget.isOnline()) {
            if (doll != null && doll.isValid()) {
                actionBar
                    .append(ChatColor.DARK_PURPLE)
                    .append("Linked: ")
                    .append(SnapshotPvpPlugin.getTeamColor(linkedTarget))
                    .append(linkedTarget.getName())
                    .append(ChatColor.LIGHT_PURPLE)
                    .append(" [Doll]");
            } else if (selfBound) {
                actionBar
                    .append(ChatColor.DARK_PURPLE)
                    .append("Linked: ")
                    .append(SnapshotPvpPlugin.getTeamColor(linkedTarget))
                    .append(linkedTarget.getName())
                    .append(ChatColor.DARK_PURPLE)
                    .append(" [Self]");
            }
        }

        if (potentialTarget != null && potentialTarget.isOnline()) {
            if (actionBar.length() > 0) {
                actionBar.append(ChatColor.GRAY).append("  |  ");
            }
            if (hits < HITS_REQUIRED) {
                actionBar
                    .append(ChatColor.GOLD)
                    .append("Target: ")
                    .append(ChatColor.YELLOW)
                    .append(potentialTarget.getName())
                    .append(" (")
                    .append(hits)
                    .append("/")
                    .append(HITS_REQUIRED)
                    .append(")");
            } else {
                actionBar
                    .append(ChatColor.GOLD)
                    .append("Target: ")
                    .append(ChatColor.GREEN)
                    .append(potentialTarget.getName())
                    .append(" [Ready to Bind]");
            }
        }

        // 3. Killstreak Status
        if (actionBar.length() > 0) {
            actionBar.append(ChatColor.GRAY).append("  |  ");
        }
        actionBar
            .append(ChatColor.RED)
            .append("Killstreak: ")
            .append(ChatColor.WHITE)
            .append(killstreak)
            .append("/2");

        p.sendActionBar(actionBar.toString());

        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.STRENGTH, 100, 0)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.HASTE, 100, 0)
        );

        if (
            doll != null &&
            doll.isValid() &&
            linkedTarget != null &&
            linkedTarget.isOnline()
        ) {
            spawnParticleTether(
                doll.getLocation().add(0, 1, 0),
                linkedTarget.getLocation().add(0, 1, 0)
            );
        } else if (
            selfBound && linkedTarget != null && linkedTarget.isOnline()
        ) {
            spawnParticleTether(
                p.getLocation().add(0, 1, 0),
                linkedTarget.getLocation().add(0, 1, 0)
            );
        }
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        Entity targetEntity = e.getEntity();
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Entity doll = getEntityFromPDC(pdc, dollEntityUUID);

        if (doll != null && doll.equals(targetEntity)) {
            Player linkedTarget = getPlayerFromPDC(pdc, linkedTargetUUID);

            if (
                linkedTarget != null &&
                linkedTarget.isOnline() &&
                linkedTarget.getGameMode() != GameMode.CREATIVE &&
                linkedTarget.getGameMode() != GameMode.SPECTATOR
            ) {
                double damage = e.getDamage();

                DamageSource damageSource = DamageSource.builder(
                    DamageType.INDIRECT_MAGIC
                )
                    .withCausingEntity(p)
                    .withDirectEntity(p)
                    .build();

                linkedTarget.damage(damage, damageSource);

                linkedTarget
                    .getWorld()
                    .playSound(
                        linkedTarget.getLocation(),
                        Sound.ENTITY_STRAY_HURT,
                        1.0f,
                        0.5f
                    );
                linkedTarget
                    .getWorld()
                    .spawnParticle(
                        Particle.DAMAGE_INDICATOR,
                        linkedTarget.getLocation().add(0, 1, 0),
                        (int) (damage * 2),
                        0.2,
                        0.5,
                        0.2,
                        0.1
                    );

                if (Math.random() < 0.15) {
                    linkedTarget.addPotionEffect(
                        new PotionEffect(PotionEffectType.SLOWNESS, 40, 1)
                    );
                }
            }

            doll.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                doll.getLocation().add(0, 1, 0),
                10,
                0.2,
                0.4,
                0.2,
                0.05
            );
        } else if (
            targetEntity instanceof Player hitPlayer && !hitPlayer.equals(p)
        ) {
            String currentPotentialUUID = pdc.getOrDefault(
                potentialTargetUUID,
                PersistentDataType.STRING,
                ""
            );
            String newTargetUUID = hitPlayer.getUniqueId().toString();

            int hits = currentPotentialUUID.equals(newTargetUUID)
                ? pdc.getOrDefault(
                      potentialHitCount,
                      PersistentDataType.INTEGER,
                      0
                  )
                : 0;

            hits++;

            pdc.set(
                potentialTargetUUID,
                PersistentDataType.STRING,
                newTargetUUID
            );
            pdc.set(potentialHitCount, PersistentDataType.INTEGER, hits);
        }
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        boolean selfBound = pdc.getOrDefault(
            isSelfBoundKey,
            PersistentDataType.BOOLEAN,
            false
        );
        if (!selfBound) return;

        Player linkedTarget = getPlayerFromPDC(pdc, linkedTargetUUID);
        if (
            linkedTarget != null &&
            linkedTarget.isOnline() &&
            linkedTarget.getGameMode() != GameMode.CREATIVE &&
            linkedTarget.getGameMode() != GameMode.SPECTATOR
        ) {
            double damage = e.getDamage();
            if (damage <= 0) return;

            e.setCancelled(true);

            DamageSource damageSource = DamageSource.builder(
                DamageType.INDIRECT_MAGIC
            )
                .withCausingEntity(p)
                .withDirectEntity(p)
                .build();

            linkedTarget.damage(damage, damageSource);

            linkedTarget
                .getWorld()
                .playSound(
                    linkedTarget.getLocation(),
                    Sound.ENTITY_STRAY_HURT,
                    1.0f,
                    0.5f
                );
            linkedTarget
                .getWorld()
                .spawnParticle(
                    Particle.DAMAGE_INDICATOR,
                    linkedTarget.getLocation().add(0, 1, 0),
                    (int) (damage * 2),
                    0.2,
                    0.5,
                    0.2,
                    0.1
                );

            p.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                p.getLocation().add(0, 1, 0),
                10,
                0.2,
                0.4,
                0.2,
                0.05
            );
        }
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (
            e.getAction() != Action.RIGHT_CLICK_BLOCK &&
            e.getAction() != Action.RIGHT_CLICK_AIR
        ) return;
        ItemStack item = e.getItem();
        if (item == null) return;

        boolean isPoppy = item.getType() == Material.POPPY;
        boolean isWitherRose = item.getType() == Material.WITHER_ROSE;

        if (!isPoppy && !isWitherRose) return;

        e.setCancelled(true);

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Player potentialTarget = getPlayerFromPDC(pdc, potentialTargetUUID);
        int hits = pdc.getOrDefault(
            potentialHitCount,
            PersistentDataType.INTEGER,
            0
        );

        if (potentialTarget == null || hits < HITS_REQUIRED) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        pdc.set(
            linkedTargetUUID,
            PersistentDataType.STRING,
            potentialTarget.getUniqueId().toString()
        );

        pdc.set(potentialTargetUUID, PersistentDataType.STRING, "");
        pdc.set(potentialHitCount, PersistentDataType.INTEGER, 0);

        if (isPoppy) {
            pdc.set(isSelfBoundKey, PersistentDataType.BOOLEAN, false);

            Location spawnLoc =
                e.getClickedBlock() != null
                    ? e
                          .getClickedBlock()
                          .getRelative(e.getBlockFace())
                          .getLocation()
                          .add(0.5, 0, 0.5)
                    : p.getLocation();

            spawnVoodooDoll(p, potentialTarget, spawnLoc);
        } else if (isWitherRose) {
            removeExistingDoll(p);
            pdc.set(isSelfBoundKey, PersistentDataType.BOOLEAN, true);

            p.getWorld().playSound(
                p.getLocation(),
                Sound.ENTITY_ELDER_GUARDIAN_CURSE,
                1.0f,
                0.8f
            );
            p.getWorld().spawnParticle(
                Particle.WITCH,
                p.getLocation().add(0, 1, 0),
                30,
                0.5,
                0.5,
                0.5,
                0.1
            );
        }
    }

    private void spawnVoodooDoll(Player owner, Player target, Location loc) {
        removeExistingDoll(owner);

        Mannequin doll = (Mannequin) loc
            .getWorld()
            .spawnEntity(loc, EntityType.MANNEQUIN);
        doll.setAI(false);
        doll.setGravity(true);
        doll.setCanPickupItems(false);

        doll.setProfile(
            ResolvableProfile.resolvableProfile().name(target.getName()).build()
        );
        if (doll.getEquipment() != null) {
            doll.getEquipment().setChestplate(
                new ItemStack(Material.GOLDEN_CHESTPLATE)
            );
        }

        if (doll.getAttribute(Attribute.MAX_HEALTH) != null) {
            doll.getAttribute(Attribute.MAX_HEALTH).setBaseValue(DOLL_HP);
        }
        doll.setHealth(DOLL_HP);
        doll.setCustomName(
            SnapshotPvpPlugin.getTeamColor(owner) +
                owner.getName() +
                "'s" +
                ChatColor.DARK_PURPLE +
                " Doll of " +
                SnapshotPvpPlugin.getTeamColor(target) +
                target.getName()
        );
        doll.setCustomNameVisible(true);

        owner
            .getPersistentDataContainer()
            .set(
                dollEntityUUID,
                PersistentDataType.STRING,
                doll.getUniqueId().toString()
            );

        loc.getWorld().playSound(
            loc,
            Sound.ENTITY_ELDER_GUARDIAN_CURSE,
            1.0f,
            0.8f
        );
        loc.getWorld().spawnParticle(
            Particle.WITCH,
            loc.add(0, 1, 0),
            30,
            0.5,
            0.5,
            0.5,
            0.1
        );

        SnapshotPvpPlugin.addToTeam(owner, doll);
    }

    private void removeExistingDoll(Player owner) {
        PersistentDataContainer pdc = owner.getPersistentDataContainer();
        Entity oldDoll = getEntityFromPDC(pdc, dollEntityUUID);
        if (oldDoll != null) {
            oldDoll.remove();
        }
        pdc.set(dollEntityUUID, PersistentDataType.STRING, "");
    }

    private Player getPlayerFromPDC(
        PersistentDataContainer pdc,
        NamespacedKey key
    ) {
        if (!pdc.has(key, PersistentDataType.STRING)) return null;
        String uuidStr = pdc.get(key, PersistentDataType.STRING);
        if (uuidStr == null || uuidStr.isEmpty()) return null;
        try {
            UUID id = UUID.fromString(uuidStr);
            return Bukkit.getPlayer(id);
        } catch (Exception ex) {
            return null;
        }
    }

    private Entity getEntityFromPDC(
        PersistentDataContainer pdc,
        NamespacedKey key
    ) {
        if (!pdc.has(key, PersistentDataType.STRING)) return null;
        String uuidStr = pdc.get(key, PersistentDataType.STRING);
        if (uuidStr == null || uuidStr.isEmpty()) return null;
        try {
            UUID id = UUID.fromString(uuidStr);
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid()) return null;
            return e;
        } catch (Exception ex) {
            return null;
        }
    }

    private void spawnParticleTether(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) return;

        double distance = from.distance(to);
        if (distance > 160.0) return;

        Vector direction = to.toVector().subtract(from.toVector()).normalize();
        double stepSize = 0.5;

        for (double d = 0; d < distance; d += stepSize) {
            Location point = from.clone().add(direction.clone().multiply(d));
            from.getWorld().spawnParticle(Particle.SOUL, point, 1, 0, 0, 0, 0);
        }
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
