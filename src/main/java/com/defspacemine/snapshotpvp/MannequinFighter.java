package com.defspacemine.snapshotpvp;

import com.google.common.collect.Multimap;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public class MannequinFighter {

    public final Mannequin mannequin;
    private final Plugin plugin;

    private static final double SEARCH_RADIUS = 16.0;
    private static final int RETARGET_INTERVAL_TICKS = 10;

    private static final double DEFAULT_INTERACTION_RANGE = 3.0;
    private static final double DEFAULT_MOVEMENT_SPEED = 0.1;
    private static final double DEFAULT_ATTACK_SPEED = 4.0;
    private static final double DEFAULT_ATTACK_DAMAGE = 1.0;

    // Vanilla input acceleration: ground = movement speed attribute, air = 0.02
    private static final double AIR_ACCELERATION = 0.02;
    // Stop closing the gap a bit inside reach so it doesn't jitter on the boundary
    private static final double APPROACH_FACTOR = 0.85;

    private BukkitTask task;
    private LivingEntity target;
    private int ticksSinceRetarget = RETARGET_INTERVAL_TICKS;
    private int attackCooldownTicks = 0;

    public MannequinFighter(Plugin plugin, Mannequin mannequin) {
        this.plugin = plugin;
        this.mannequin = mannequin;
        configureMannequin();
        startCombatLoop();
    }

    private void configureMannequin() {
        mannequin.setImmovable(false);
        mannequin.setMainHand(MainHand.RIGHT);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void startCombatLoop() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!mannequin.isValid() || mannequin.isDead()) {
                    cancel();
                    return;
                }

                if (attackCooldownTicks > 0) attackCooldownTicks--;

                if (
                    ++ticksSinceRetarget >= RETARGET_INTERVAL_TICKS ||
                    !isValidTarget(target)
                ) {
                    ticksSinceRetarget = 0;
                    target = findTarget();
                }
                if (target == null) return;

                tickCombat(target);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void tickCombat(LivingEntity target) {
        double reach = getAttributeValue(
            Attribute.ENTITY_INTERACTION_RANGE,
            DEFAULT_INTERACTION_RANGE
        );
        double distance = distanceToBoundingBox(target);

        faceTarget(target);

        if (distance > reach * APPROACH_FACTOR) {
            accelerateTowards(target);
        }

        if (
            distance <= reach &&
            attackCooldownTicks <= 0 &&
            mannequin.hasLineOfSight(target)
        ) {
            performAttack(target);
            attackCooldownTicks = getAttackCooldownTicks();
        }
    }

    // ------------------------------------------------------------------ attack

    private void performAttack(LivingEntity victim) {
        mannequin.swingMainHand();

        DamageSource source = DamageSource.builder(DamageType.MOB_ATTACK)
            .withCausingEntity(mannequin)
            .withDirectEntity(mannequin)
            .build();

        victim.damage(calculateDamage(), source);
    }

    /** Base attribute + held item's attack damage modifiers + sharpness. */
    private double calculateDamage() {
        double damage = getAttributeValue(
            Attribute.ATTACK_DAMAGE,
            DEFAULT_ATTACK_DAMAGE
        );

        EntityEquipment equipment = mannequin.getEquipment();
        if (equipment == null) return damage;

        ItemStack held = equipment.getItemInMainHand();
        if (held.getType().isAir()) return damage;

        Multimap<Attribute, AttributeModifier> mods = null;
        ItemMeta meta = held.getItemMeta();
        if (meta != null && meta.hasAttributeModifiers()) {
            mods = meta.getAttributeModifiers(EquipmentSlot.HAND);
        }
        if (mods == null || mods.isEmpty()) {
            mods = held
                .getType()
                .getDefaultAttributeModifiers(EquipmentSlot.HAND);
        }

        double add = 0,
            mulBase = 0,
            mulTotal = 1;
        for (AttributeModifier mod : mods.get(Attribute.ATTACK_DAMAGE)) {
            switch (mod.getOperation()) {
                case ADD_NUMBER -> add += mod.getAmount();
                case ADD_SCALAR -> mulBase += mod.getAmount();
                case MULTIPLY_SCALAR_1 -> mulTotal *= 1 + mod.getAmount();
            }
        }
        damage = (damage + add) * (1 + mulBase) * mulTotal;

        int sharpness = held.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharpness > 0) damage += 0.5 * sharpness + 0.5;

        return damage;
    }

    private int getAttackCooldownTicks() {
        double attackSpeed = getAttributeValue(
            Attribute.ATTACK_SPEED,
            DEFAULT_ATTACK_SPEED
        );
        if (attackSpeed <= 0) return 20;
        return Math.max(1, (int) Math.ceil(20.0 / attackSpeed)) * 2;
    }

    // ---------------------------------------------------------------- movement

    /**
     * Accelerates toward the target while preserving external knockback momentum
     * by letting high speeds decay naturally through friction rather than hard-clamping.
     */
    private void accelerateTowards(LivingEntity target) {
        Location from = mannequin.getLocation();
        Location to = target.getLocation();

        Vector dir = new Vector(
            to.getX() - from.getX(),
            0,
            to.getZ() - from.getZ()
        );
        if (dir.lengthSquared() < 1.0E-4) return;
        dir.normalize();

        boolean onGround = mannequin.isOnGround();
        double speed = getAttributeValue(
            Attribute.MOVEMENT_SPEED,
            DEFAULT_MOVEMENT_SPEED
        );
        double accel = onGround ? speed : AIR_ACCELERATION;
        accel *= 0.98; // vanilla scales input by 0.98

        Vector vel = mannequin.getVelocity();

        // Apply vanilla ground friction so it doesn't slide unnaturally
        if (onGround) {
            vel.multiply(new Vector(0.6, 1.0, 0.6));
        }

        double y = vel.getY();
        if (onGround && isBlockedAhead(dir)) {
            y = 0.42; // vanilla jump
        }

        Vector hVel = new Vector(vel.getX(), 0, vel.getZ());
        double maxSpeed = speed * 2.1585;

        // Only add movement acceleration if we aren't currently flying from knockback.
        // If speed > maxSpeed (due to knockback), we let friction naturally slow it down.
        if (hVel.length() < maxSpeed) {
            vel.setX(vel.getX() + dir.getX() * accel);
            vel.setZ(vel.getZ() + dir.getZ() * accel);
        }

        vel.setY(y);
        mannequin.setVelocity(vel);
    }

    private boolean isBlockedAhead(Vector dir) {
        Location ahead = mannequin.getLocation().add(dir.clone().multiply(0.7));

        Block foot = ahead.getBlock();
        Block head = ahead.clone().add(0, 1, 0).getBlock();
        Block above = ahead.clone().add(0, 2, 0).getBlock();

        return !foot.isPassable() && head.isPassable() && above.isPassable();
    }

    private void faceTarget(Entity target) {
        Location eye = mannequin.getEyeLocation();
        Vector to = target
            .getBoundingBox()
            .getCenter()
            .subtract(eye.toVector());

        double yaw = Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
        double pitch = -Math.toDegrees(
            Math.atan2(to.getY(), Math.hypot(to.getX(), to.getZ()))
        );
        mannequin.setRotation((float) yaw, (float) pitch);
    }

    // --------------------------------------------------------------- targeting

    private LivingEntity findTarget() {
        LivingEntity closest = null;
        double minDistSq = Double.MAX_VALUE;

        for (Entity entity : mannequin.getNearbyEntities(
            SEARCH_RADIUS,
            SEARCH_RADIUS / 2,
            SEARCH_RADIUS
        )) {
            if (
                !(entity instanceof LivingEntity living) ||
                !isValidTarget(living)
            ) continue;

            double distSq = mannequin
                .getLocation()
                .distanceSquared(living.getLocation());
            if (distSq < minDistSq) {
                minDistSq = distSq;
                closest = living;
            }
        }
        return closest;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (
            entity == null || !entity.isValid() || entity.isDead()
        ) return false;
        if (entity.equals(mannequin)) return false;
        if (entity.isInvulnerable()) return false;
        if (
            entity instanceof Player p &&
            (p.getGameMode() == GameMode.CREATIVE ||
                p.getGameMode() == GameMode.SPECTATOR)
        ) {
            return false;
        }
        if (!entity.getWorld().equals(mannequin.getWorld())) return false;
        return !SnapshotPvpPlugin.isSameTeam(mannequin, entity);
    }

    // ----------------------------------------------------------------- helpers

    private double distanceToBoundingBox(Entity target) {
        BoundingBox bb = target.getBoundingBox();
        Vector eye = mannequin.getEyeLocation().toVector();

        double cx = Math.max(bb.getMinX(), Math.min(eye.getX(), bb.getMaxX()));
        double cy = Math.max(bb.getMinY(), Math.min(eye.getY(), bb.getMaxY()));
        double cz = Math.max(bb.getMinZ(), Math.min(eye.getZ(), bb.getMaxZ()));

        return eye.distance(new Vector(cx, cy, cz));
    }

    private double getAttributeValue(Attribute attribute, double fallback) {
        AttributeInstance instance = mannequin.getAttribute(attribute);
        return instance != null ? instance.getValue() : fallback;
    }
}
