package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

public class Jockey extends ManaKit implements Listener {

    public static final NamespacedKey ENEMY_TARGET_KEY = new NamespacedKey(
        "snapshotpvp",
        "jockey_enemy_target"
    );
    public static final NamespacedKey ARMOR_STAND_KEY = new NamespacedKey(
        "snapshotpvp",
        "jockey_armor_stand"
    );
    public static final NamespacedKey STAND_OWNER_KEY = new NamespacedKey(
        "snapshotpvp",
        "jockey_stand_owner"
    );

    final int arrowRestock = 160; // 4 arrows every 8 seconds, they auto reset
    final NamespacedKey arrowRestockCounter = ManaKitListener.MANA_KIT_DATA0;

    private final ItemStack arrows;

    public Jockey() {
        super("jockey", "Jockey", "[Utility Support]", 0);

        arrows = new ItemStack(Material.ARROW, 4);
        arrows.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -185,
            10,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);

        cleanupEnemyMount(p);

        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
    }

    public static void cleanupEnemyMount(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        String standUuidStr = pdc.get(
            ARMOR_STAND_KEY,
            PersistentDataType.STRING
        );

        pdc.remove(ENEMY_TARGET_KEY);
        pdc.remove(ARMOR_STAND_KEY);

        if (standUuidStr != null) {
            try {
                UUID standUuid = UUID.fromString(standUuidStr);
                Entity stand = Bukkit.getEntity(standUuid);
                if (stand != null && stand.isValid()) {
                    stand.remove();
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        Entity vehicle = p.getVehicle();

        Component mountStatus = Component.text(
            "Not Mounted",
            NamedTextColor.GRAY
        );

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        String targetUuidStr = pdc.get(
            ENEMY_TARGET_KEY,
            PersistentDataType.STRING
        );
        String standUuidStr = pdc.get(
            ARMOR_STAND_KEY,
            PersistentDataType.STRING
        );

        if (targetUuidStr != null && standUuidStr != null) {
            UUID standUuid = null;
            UUID targetUuid = null;
            try {
                standUuid = UUID.fromString(standUuidStr);
                targetUuid = UUID.fromString(targetUuidStr);
            } catch (IllegalArgumentException e) {
                cleanupEnemyMount(p);
            }

            if (standUuid != null && targetUuid != null) {
                Entity standEntity = Bukkit.getEntity(standUuid);

                if (
                    vehicle == null ||
                    standEntity == null ||
                    !vehicle.equals(standEntity) ||
                    !standEntity.isValid() ||
                    standEntity.getPassengers().isEmpty()
                ) {
                    cleanupEnemyMount(p);
                } else {
                    Player target = Bukkit.getPlayer(targetUuid);
                    if (
                        target != null &&
                        target.isOnline() &&
                        !target.isDead() &&
                        target.getWorld().equals(p.getWorld())
                    ) {
                        standEntity.teleport(target.getLocation());
                        mountStatus = Component.text(
                            "Riding Enemy " + target.getName(),
                            NamedTextColor.RED
                        );
                        p.addPotionEffect(
                            new PotionEffect(PotionEffectType.STRENGTH, 2, 1)
                        );
                    } else {
                        p.leaveVehicle();
                        cleanupEnemyMount(p);
                    }
                }
            }
        } else if (vehicle instanceof Player mount && isTeammate(p, mount)) {
            mountStatus = Component.text(
                "Riding ",
                NamedTextColor.GREEN
            ).append(
                Component.text(
                    mount.getName(),
                    SnapshotPvpPlugin.getTeamColor(mount)
                )
            );
        }

        int arrowRestockC = pdc.getOrDefault(
            arrowRestockCounter,
            PersistentDataType.INTEGER,
            0
        );
        pdc.set(
            arrowRestockCounter,
            PersistentDataType.INTEGER,
            arrowRestockC + 1
        );

        Component actionBar = mountStatus
            .append(Component.text("  |  ", NamedTextColor.GRAY))
            .append(Component.text("Arrow Restock: ", NamedTextColor.GREEN))
            .append(
                Component.text(
                    arrowRestockC + "/" + arrowRestock,
                    NamedTextColor.WHITE
                )
            )
            .append(Component.text("  |  ", NamedTextColor.GRAY))
            .append(Component.text("Killstreak: ", NamedTextColor.RED))
            .append(Component.text(killstreak + "/3", NamedTextColor.WHITE));

        p.sendActionBar(actionBar);

        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 1)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 0)
        );
        if (killstreak >= 3) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 2)
        );

        if (arrowRestockC >= arrowRestock) {
            PlayerInventory inv = p.getInventory();
            SnapshotPvpPlugin.clearInv(inv, Material.ARROW);
            inv.addItem(arrows.clone());
            pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onInteractEntity(Player p, PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Player target)) return;

        if (isTeammate(p, target)) {
            cleanupEnemyMount(p);
            target.addPassenger(p);

            p.getWorld().playSound(
                p.getLocation(),
                Sound.ENTITY_HORSE_ARMOR,
                1.0f,
                1.2f
            );
            p.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                target.getLocation().add(0, 1, 0),
                10,
                0.3,
                0.5,
                0.3,
                0.1
            );
            return;
        }

        double maxHealth =
            target.getAttribute(Attribute.MAX_HEALTH) != null
                ? target.getAttribute(Attribute.MAX_HEALTH).getValue()
                : 20.0;

        if (target.getHealth() < maxHealth / 2.0) {
            cleanupEnemyMount(p);

            ArmorStand stand = target
                .getWorld()
                .spawn(target.getLocation(), ArmorStand.class, armorStand -> {
                    armorStand.setInvisible(true);
                    armorStand.setGravity(false);
                    armorStand.setInvulnerable(true);
                    armorStand.setPersistent(true);
                    armorStand.setSmall(true);

                    armorStand
                        .getPersistentDataContainer()
                        .set(
                            STAND_OWNER_KEY,
                            PersistentDataType.STRING,
                            p.getUniqueId().toString()
                        );
                });

            stand.addPassenger(p);

            PersistentDataContainer pdc = p.getPersistentDataContainer();
            pdc.set(
                ENEMY_TARGET_KEY,
                PersistentDataType.STRING,
                target.getUniqueId().toString()
            );
            pdc.set(
                ARMOR_STAND_KEY,
                PersistentDataType.STRING,
                stand.getUniqueId().toString()
            );

            p.getWorld().playSound(
                p.getLocation(),
                Sound.ENTITY_HORSE_ARMOR,
                1.0f,
                0.7f
            );
            p.getWorld().spawnParticle(
                Particle.ANGRY_VILLAGER,
                target.getLocation().add(0, 1, 0),
                10,
                0.3,
                0.5,
                0.3,
                0.1
            );
        } else {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            p.sendMessage(
                Component.text(
                    "Enemies must be below half health to mount!",
                    NamedTextColor.RED
                )
            );
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (
            e.getEntity() instanceof Player p &&
            e.getDismounted() instanceof ArmorStand stand
        ) {
            PersistentDataContainer standPdc =
                stand.getPersistentDataContainer();
            if (standPdc.has(STAND_OWNER_KEY, PersistentDataType.STRING)) {
                stand.remove();
                cleanupEnemyMount(p);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cleanupEnemyMount(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        cleanupEnemyMount(e.getEntity());
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        Entity vehicle = p.getVehicle();

        if (vehicle instanceof Player mount && isTeammate(p, mount)) {
            EntityDamageEvent.DamageCause cause = e.getCause();

            if (
                cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK ||
                cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
            ) {
                if (e.getEntity().equals(mount)) {
                    e.setCancelled(true);
                    p.playSound(
                        p.getLocation(),
                        Sound.ENTITY_VILLAGER_NO,
                        1.0f,
                        1.0f
                    );
                }
            } else if (cause == EntityDamageEvent.DamageCause.PROJECTILE) {
                mount.addPotionEffect(
                    new PotionEffect(
                        PotionEffectType.REGENERATION,
                        100,
                        1,
                        false,
                        true,
                        true
                    )
                );
                mount
                    .getWorld()
                    .playSound(
                        mount.getLocation(),
                        Sound.ENTITY_PLAYER_LEVELUP,
                        0.5f,
                        1.8f
                    );
                mount
                    .getWorld()
                    .spawnParticle(
                        Particle.HEART,
                        mount.getLocation().add(0, 2, 0),
                        5,
                        0.3,
                        0.3,
                        0.3,
                        0.1
                    );
            }
        }
    }

    private boolean isTeammate(Player p1, Player p2) {
        if (p1.equals(p2)) return false;
        Team t1 = SnapshotPvpPlugin.getTeam(p1);
        Team t2 = SnapshotPvpPlugin.getTeam(p2);
        return t1 != null && t1.equals(t2);
    }

    @Override
    public void onLeaveCombat(Player p) {
        SnapshotPvpPlugin.clearInv(p.getInventory(), Material.ARROW);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        resetKit(p);
        p.getPersistentDataContainer().set(
            arrowRestockCounter,
            PersistentDataType.INTEGER,
            arrowRestock
        );
    }
}
