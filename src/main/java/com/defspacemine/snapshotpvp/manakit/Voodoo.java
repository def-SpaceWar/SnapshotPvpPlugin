package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.entity.LivingEntity;
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

    private final double DOLL_HP = 30.0;
    private final double DOLL_DAMAGE_FACTOR = .5;
    private final double SELF_DAMAGE_FACTOR = .33;
    private final int HITS_REQUIRED = 3;

    private final NamespacedKey linkedTargetUUID =
        ManaKitListener.MANA_KIT_DATASTR0;
    private final NamespacedKey dollUUIDKey = ManaKitListener.MANA_KIT_DATASTR1;
    private final NamespacedKey potentialTargetUUID =
        ManaKitListener.MANA_KIT_DATASTR2;
    private final NamespacedKey potentialHitCount =
        ManaKitListener.MANA_KIT_DATA0;
    private final NamespacedKey isSelfBoundKey =
        ManaKitListener.MANA_KIT_DATABOOL0;

    private final NamespacedKey dollOwnerKey =
        ManaKitListener.MANA_KIT_DATASTR0;

    public Voodoo() {
        super("voodoo", "Voodoo", "[Melee Damage]", 2);
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

        removeExistingDoll(p);

        pdc.set(linkedTargetUUID, PersistentDataType.STRING, "");
        pdc.set(dollUUIDKey, PersistentDataType.STRING, "");
        pdc.set(potentialTargetUUID, PersistentDataType.STRING, "");
        pdc.set(potentialHitCount, PersistentDataType.INTEGER, 0);
        pdc.set(isSelfBoundKey, PersistentDataType.BOOLEAN, false);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        Player linkedTarget = getPlayerFromPDC(pdc, linkedTargetUUID);
        Player potentialTarget = getPlayerFromPDC(pdc, potentialTargetUUID);
        Mannequin doll = getDoll(p);

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

        TextComponent.Builder actionBar = Component.text();
        boolean hasPreviousSection = false;

        if (linkedTarget != null && linkedTarget.isOnline()) {
            Component targetName = Component.text(linkedTarget.getName()).color(
                SnapshotPvpPlugin.getTeamColor(linkedTarget)
            );

            if (doll != null && doll.isValid()) {
                actionBar
                    .append(
                        Component.text("Linked: ", NamedTextColor.DARK_PURPLE)
                    )
                    .append(targetName)
                    .append(
                        Component.text(" [Doll]", NamedTextColor.LIGHT_PURPLE)
                    );
                hasPreviousSection = true;
            } else if (selfBound) {
                actionBar
                    .append(
                        Component.text("Linked: ", NamedTextColor.DARK_PURPLE)
                    )
                    .append(targetName)
                    .append(
                        Component.text(" [Self]", NamedTextColor.DARK_PURPLE)
                    );
                hasPreviousSection = true;
            }
        }

        if (potentialTarget != null && potentialTarget.isOnline()) {
            if (hasPreviousSection) {
                actionBar.append(Component.text("  |  ", NamedTextColor.GRAY));
            }

            actionBar.append(Component.text("Target: ", NamedTextColor.GOLD));
            if (hits < HITS_REQUIRED) {
                actionBar.append(
                    Component.text(
                        potentialTarget.getName() +
                            " (" +
                            hits +
                            "/" +
                            HITS_REQUIRED +
                            ")",
                        NamedTextColor.YELLOW
                    )
                );
            } else {
                actionBar.append(
                    Component.text(
                        potentialTarget.getName() + " [Ready to Bind]",
                        NamedTextColor.GREEN
                    )
                );
            }
            hasPreviousSection = true;
        }

        if (hasPreviousSection) {
            actionBar.append(Component.text("  |  ", NamedTextColor.GRAY));
        }

        actionBar
            .append(Component.text("Killstreak: ", NamedTextColor.RED))
            .append(Component.text(killstreak + "/2", NamedTextColor.WHITE));

        p.sendActionBar(actionBar.build());

        if (killstreak >= 1) {
            p.addPotionEffect(
                new PotionEffect(PotionEffectType.STRENGTH, 100, 0)
            );
        }
        if (killstreak >= 2) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 0));
        }

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
        Entity victim = e.getEntity();
        PersistentDataContainer victimPDC = victim.getPersistentDataContainer();

        if (victimPDC.has(dollOwnerKey, PersistentDataType.STRING)) {
            String ownerUUIDStr = victimPDC.get(
                dollOwnerKey,
                PersistentDataType.STRING
            );
            if (
                ownerUUIDStr != null &&
                ownerUUIDStr.equals(p.getUniqueId().toString())
            ) {
                Player linkedTarget = getPlayerFromPDC(
                    p.getPersistentDataContainer(),
                    linkedTargetUUID
                );
                if (linkedTarget != null) {
                    double damage = Math.max(e.getDamage(), 1.0);
                    transferDollDamage(p, linkedTarget, victim, damage);
                }
                return;
            }
        }

        if (victim instanceof Player hitPlayer && !hitPlayer.equals(p) && e.getDamageSource().getDamageType() != DamageType.INDIRECT_MAGIC) {
            PersistentDataContainer pdc = p.getPersistentDataContainer();
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
    public void onDamageInWorld(
        Player p,
        Player attacker,
        Entity victim,
        EntityDamageByEntityEvent e
    ) {
        PersistentDataContainer victimPDC = victim.getPersistentDataContainer();

        if (victimPDC.has(dollOwnerKey, PersistentDataType.STRING)) {
            String ownerUUIDStr = victimPDC.get(
                dollOwnerKey,
                PersistentDataType.STRING
            );
            if (
                ownerUUIDStr != null &&
                ownerUUIDStr.equals(p.getUniqueId().toString())
            ) {
                if (
                    attacker != null &&
                    !attacker.equals(p) &&
                    SnapshotPvpPlugin.isSameTeam(p, attacker)
                ) {
                    Player linkedTarget = getPlayerFromPDC(
                        p.getPersistentDataContainer(),
                        linkedTargetUUID
                    );
                    if (linkedTarget != null) {
                        double damage = Math.max(e.getDamage(), 1.0);
                        transferDollDamage(
                            attacker,
                            linkedTarget,
                            victim,
                            damage
                        );
                    }
                }
            }
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

            DamageSource damageSource = DamageSource.builder(
                DamageType.INDIRECT_MAGIC
            )
                .withCausingEntity(p)
                .withDirectEntity(p)
                .build();

            linkedTarget.setNoDamageTicks(0);
            linkedTarget.damage(damage * SELF_DAMAGE_FACTOR, damageSource);

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
                    (int) Math.max(1, damage * 2),
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

    private void transferDollDamage(
        Player attacker,
        Player linkedTarget,
        Entity doll,
        double damage
    ) {
        if (doll instanceof LivingEntity livingDoll) {
            livingDoll.setNoDamageTicks(0);
        }

        if (
            linkedTarget != null &&
            linkedTarget.isOnline() &&
            linkedTarget.getGameMode() != GameMode.CREATIVE &&
            linkedTarget.getGameMode() != GameMode.SPECTATOR
        ) {
            DamageSource damageSource = DamageSource.builder(
                DamageType.INDIRECT_MAGIC
            )
                .withCausingEntity(attacker)
                .withDirectEntity(attacker)
                .build();

            linkedTarget.setNoDamageTicks(0);
            linkedTarget.damage(damage * DOLL_DAMAGE_FACTOR, damageSource);

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
                    (int) Math.max(1, damage * 2),
                    0.2,
                    0.5,
                    0.2,
                    0.1
                );
        }

        if (doll != null) {
            doll.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                doll.getLocation().add(0, 1, 0),
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

        doll.getPersistentDataContainer().set(
            dollOwnerKey,
            PersistentDataType.STRING,
            owner.getUniqueId().toString()
        );

        owner
            .getPersistentDataContainer()
            .set(
                dollUUIDKey,
                PersistentDataType.STRING,
                doll.getUniqueId().toString()
            );

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
        doll.customName(
            Component.text(
                owner.getName(),
                SnapshotPvpPlugin.getTeamColor(owner)
            )
                .append(
                    Component.text("'s Doll of ", NamedTextColor.DARK_PURPLE)
                )
                .append(
                    Component.text(
                        target.getName(),
                        SnapshotPvpPlugin.getTeamColor(target)
                    )
                )
        );
        doll.setCustomNameVisible(true);

        loc.getWorld().playSound(
            loc,
            Sound.ENTITY_ELDER_GUARDIAN_CURSE,
            1.0f,
            0.8f
        );
        loc.getWorld().spawnParticle(
            Particle.WITCH,
            loc.clone().add(0, 1, 0),
            30,
            0.5,
            0.5,
            0.5,
            0.1
        );

        SnapshotPvpPlugin.addToTeam(owner, doll);
    }

    private void removeExistingDoll(Player owner) {
        Mannequin oldDoll = getDoll(owner);
        if (oldDoll != null) {
            oldDoll.remove();
        }
        owner
            .getPersistentDataContainer()
            .set(dollUUIDKey, PersistentDataType.STRING, "");
    }

    private Mannequin getDoll(Player owner) {
        PersistentDataContainer pdc = owner.getPersistentDataContainer();
        if (!pdc.has(dollUUIDKey, PersistentDataType.STRING)) return null;
        String uuidStr = pdc.get(dollUUIDKey, PersistentDataType.STRING);
        if (uuidStr == null || uuidStr.isEmpty()) return null;
        try {
            Entity entity = Bukkit.getEntity(UUID.fromString(uuidStr));
            if (entity instanceof Mannequin mannequin && mannequin.isValid()) {
                return mannequin;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Player getPlayerFromPDC(
        PersistentDataContainer pdc,
        NamespacedKey key
    ) {
        if (!pdc.has(key, PersistentDataType.STRING)) return null;
        String uuidStr = pdc.get(key, PersistentDataType.STRING);
        if (uuidStr == null || uuidStr.isEmpty()) return null;
        try {
            return Bukkit.getPlayer(UUID.fromString(uuidStr));
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
