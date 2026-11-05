package com.defspacemine.snapshotpvp;

import com.google.common.collect.Multimap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Drives a Mannequin so it fights the way a player does: it presses
 * forward / strafe / sprint / jump and the entity's own tick does the physics,
 * it hits at the weapon's real full-charge cadence, and sprint hits get the
 * vanilla extra knockback and sprint reset.
 *
 * A Mannequin isn't a player, so it starts with the generic entity attribute
 * defaults (movement speed 0.7, no attack attributes). The constructor sets the
 * base movement speed to the player's 0.1, and the attack numbers fall back to
 * player defaults below.
 */
public class MannequinFighter {

    public final Mannequin mannequin;
    private final Plugin plugin;

    // ---- targeting / combat
    private static final double SEARCH_RADIUS = 16.0;
    private static final int RETARGET_INTERVAL_TICKS = 10;

    private static final double DEFAULT_INTERACTION_RANGE = 3.0;
    private static final double DEFAULT_ATTACK_SPEED = 4.0;
    private static final double DEFAULT_ATTACK_DAMAGE = 1.0;

    // Close the gap until we're this fraction of reach away, then hold position and strafe
    private static final double APPROACH_FACTOR = 0.85;
    // Back off a little if we end up standing inside the target
    private static final double BACK_OFF_FACTOR = 0.4;
    // Sprint when the target is further than reach + this
    private static final double SPRINT_DISTANCE = 1.5;
    // Start strafing once the target is within reach + this
    private static final double STRAFE_RANGE = 2.0;
    // Only swing when we're actually looking at the target (degrees)
    private static final double MAX_AIM_ERROR = 25.0;

    // ---- attack timing / knockback (vanilla)
    private static final int REACTION_JITTER_TICKS = 3; // a human clicks a touch after the charge is full
    private static final double SPRINT_KNOCKBACK = 0.5; // extra knockback strength on a sprint hit
    private static final double SPRINT_HIT_SLOWDOWN = 0.6; // attacker keeps 60% of horizontal speed
    private static final int SPRINT_RESET_TICKS = 3; // the w-tap after a sprint hit

    // ---- aiming (smooth turning instead of snapping)
    private static final float TURN_SMOOTHING = 0.4f;
    private static final float MAX_YAW_STEP = 40f;
    private static final float MAX_PITCH_STEP = 25f;

    // ---- movement (vanilla values)
    private static final double DEFAULT_MOVEMENT_SPEED = 0.1;
    private static final double DEFAULT_JUMP_STRENGTH = 0.42;
    private static final double DEFAULT_GRAVITY = 0.08;
    private static final double DEFAULT_STEP_HEIGHT = 0.6;
    private static final double SPRINT_MULTIPLIER = 1.3;
    private static final double INPUT_SCALE = 0.98;
    private static final double AIR_ACCELERATION = 0.02;
    private static final double SPRINT_AIR_ACCELERATION = 0.026;
    private static final double SPRINT_JUMP_BOOST = 0.2;
    private static final double FLUID_SWIM_UP = 0.04;
    private static final double CLIMB_SPEED = 0.2;
    private static final int JUMP_COOLDOWN_TICKS = 5;

    // ---- parkour
    private static final double SAFE_DROP = 3.0; // vanilla safe fall distance
    private static final double MAX_GAP_SCAN = 5.0;
    private static final double EDGE_PROBE = 0.25;
    private static final double EPS = 1.0E-3;
    private static final double[] LATERAL_SAMPLES = { 0.0, -0.25, 0.25 };

    // ---- unstick
    private static final int STUCK_TICKS = 12;
    private static final int SIDESTEP_TICKS = 25;

    private enum Plan {
        WALK,
        JUMP,
        BRAKE,
    }

    private BukkitTask task;
    private LivingEntity target;
    private int ticksSinceRetarget = RETARGET_INTERVAL_TICKS;
    private int attackCooldownTicks = 0;

    private int jumpCooldown = 0;
    private int strafeTicksLeft = 0;
    private double strafeDir = 1;
    private boolean wantsToMove = false;
    private boolean sprinting = false;
    private int sprintResetTicks = 0;
    private int stuckTicks = 0;
    private int sidestepTicks = 0;
    private double sidestepDir = 1;
    private double lastX, lastZ;

    public MannequinFighter(Plugin plugin, Mannequin mannequin) {
        this.plugin = plugin;
        this.mannequin = mannequin;
        configureMannequin();
        startCombatLoop();
    }

    private void configureMannequin() {
        mannequin.setImmovable(false);
        mannequin.setMainHand(MainHand.RIGHT);

        // Mannequins start with the generic entity movement speed (0.7), which is
        // 7x a player's 0.1 and was making this run at ~30 blocks/s. Speed and
        // Slowness effects are modifiers on this base, so they still scale it.
        AttributeInstance speed = mannequin.getAttribute(
            Attribute.MOVEMENT_SPEED
        );
        if (speed != null) speed.setBaseValue(DEFAULT_MOVEMENT_SPEED);
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
                if (jumpCooldown > 0) jumpCooldown--;
                if (sprintResetTicks > 0) sprintResetTicks--;

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

        double aimError = lookAt(target.getBoundingBox().getCenter());
        steer(target, reach, distance);

        if (
            distance <= reach &&
            aimError <= MAX_AIM_ERROR &&
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

        boolean sprintHit = sprinting;
        double before = victim.getHealth() + victim.getAbsorptionAmount();
        victim.damage(calculateDamage(), source);
        double after = victim.getHealth() + victim.getAbsorptionAmount();
        if (!sprintHit || after >= before) return;

        // Vanilla sprint hit: extra knockback on the target, and the attacker
        // drops sprint and loses 40% of its horizontal speed (a "w-tap").
        double yaw = Math.toRadians(mannequin.getLocation().getYaw());
        victim.knockback(SPRINT_KNOCKBACK, Math.sin(yaw), -Math.cos(yaw));

        Vector vel = mannequin.getVelocity();
        mannequin.setVelocity(
            new Vector(
                vel.getX() * SPRINT_HIT_SLOWDOWN,
                vel.getY(),
                vel.getZ() * SPRINT_HIT_SLOWDOWN
            )
        );
        sprinting = false;
        sprintResetTicks = SPRINT_RESET_TICKS;
    }

    /** Weapon-adjusted attack damage plus sharpness. */
    private double calculateDamage() {
        double damage = getHeldValue(
            Attribute.ATTACK_DAMAGE,
            DEFAULT_ATTACK_DAMAGE
        );

        EntityEquipment equipment = mannequin.getEquipment();
        if (equipment == null) return damage;

        int sharpness = equipment
            .getItemInMainHand()
            .getEnchantmentLevel(Enchantment.SHARPNESS);
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

    /**
     * An attribute with the held item's modifiers folded in. A mannequin has no
     * attack attributes of its own, so the entity can't apply the weapon for us
     * and it's done by hand here. If the attribute does exist, the entity has
     * already applied the weapon and its value is used as-is.
     */
    private double getHeldValue(Attribute attribute, double base) {
        AttributeInstance instance = mannequin.getAttribute(attribute);
        if (instance != null) return instance.getValue();

        EntityEquipment equipment = mannequin.getEquipment();
        if (equipment == null) return base;

        ItemStack held = equipment.getItemInMainHand();
        if (held.getType().isAir()) return base;

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
        for (AttributeModifier mod : mods.get(attribute)) {
            switch (mod.getOperation()) {
                case ADD_NUMBER -> add += mod.getAmount();
                case ADD_SCALAR -> mulBase += mod.getAmount();
                case MULTIPLY_SCALAR_1 -> mulTotal *= 1 + mod.getAmount();
            }
        }
        return (base + add) * (1 + mulBase) * mulTotal;
    }

    // ------------------------------------------------------------------ aiming

    /**
     * Turns toward the point with exponential smoothing and a max turn rate,
     * so it looks like a mouse movement instead of a snap.
     *
     * @return the remaining yaw error in degrees
     */
    private double lookAt(Vector point) {
        Vector to = point
            .clone()
            .subtract(mannequin.getEyeLocation().toVector());
        if (to.lengthSquared() < 1.0E-4) return 0;

        float targetYaw = (float) Math.toDegrees(
            Math.atan2(-to.getX(), to.getZ())
        );
        float targetPitch = (float) -Math.toDegrees(
            Math.atan2(to.getY(), Math.hypot(to.getX(), to.getZ()))
        );

        Location loc = mannequin.getLocation();
        float yaw = loc.getYaw();
        float pitch = loc.getPitch();

        float dYaw = wrapDegrees(targetYaw - yaw);
        float dPitch = targetPitch - pitch;

        yaw += clamp(dYaw * TURN_SMOOTHING, -MAX_YAW_STEP, MAX_YAW_STEP);
        pitch += clamp(
            dPitch * TURN_SMOOTHING,
            -MAX_PITCH_STEP,
            MAX_PITCH_STEP
        );
        pitch = clamp(pitch, -90f, 90f);

        mannequin.setRotation(yaw, pitch);
        return Math.abs(wrapDegrees(targetYaw - yaw));
    }

    private static float wrapDegrees(float degrees) {
        degrees %= 360f;
        if (degrees >= 180f) degrees -= 360f;
        if (degrees < -180f) degrees += 360f;
        return degrees;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---------------------------------------------------------------- movement

    /**
     * Decides what a player would "press" this tick (forward / strafe / sprint / jump)
     * and feeds it into the entity exactly like vanilla's moveRelative() does.
     * Friction, gravity, collisions, step-up, knockback, potion attribute
     * modifiers and block speed factors are all applied by the entity's own tick.
     */
    private void steer(LivingEntity target, double reach, double distance) {
        updateStuckDetection();

        double forward = 0;
        double strafe = 0;
        boolean sprint = false;

        if (sidestepTicks > 0) {
            sidestepTicks--;
            forward = 0.3;
            strafe = sidestepDir;
        } else {
            if (distance > reach * APPROACH_FACTOR) {
                forward = 1;
                sprint = distance > reach + SPRINT_DISTANCE;
            } else if (distance < reach * BACK_OFF_FACTOR) {
                forward = -0.6;
            }
            if (distance <= reach + STRAFE_RANGE) {
                strafe = nextStrafeDirection() * (forward != 0 ? 0.5 : 1.0);
            }
        }

        wantsToMove = forward != 0 || strafe != 0;

        boolean onGround = mannequin.isOnGround();
        boolean inFluid = mannequin.isInWater() || mannequin.isInLava();
        boolean jump = false;

        if (wantsToMove && onGround && !inFluid) {
            Vector dir = worldDirection(forward, strafe);
            if (dir != null) {
                Plan plan = planParkour(dir, sprint && forward > 0);
                if (plan == Plan.BRAKE) {
                    forward = 0;
                    strafe = 0;
                } else if (plan == Plan.JUMP && jumpCooldown <= 0) {
                    jump = true;
                }
            }
        }
        sprint = sprint && forward > 0 && sprintResetTicks <= 0;
        sprinting = sprint;

        Vector vel = mannequin.getVelocity();

        if (jump) applyJump(vel, sprint);
        addInput(vel, forward, strafe, sprint);

        if (inFluid) {
            vel.setY(vel.getY() + FLUID_SWIM_UP); // like holding the jump key in water
        } else if (
            isClimbing() &&
            target.getLocation().getY() > mannequin.getLocation().getY() + 0.5
        ) {
            vel.setY(Math.max(vel.getY(), CLIMB_SPEED));
        }

        mannequin.setVelocity(vel);
    }

    private double nextStrafeDirection() {
        if (--strafeTicksLeft <= 0) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            strafeTicksLeft = rng.nextInt(12, 35);
            strafeDir = rng.nextBoolean() ? 1 : -1;
        }
        return strafeDir;
    }

    private void updateStuckDetection() {
        Location loc = mannequin.getLocation();
        if (wantsToMove && mannequin.isOnGround() && sidestepTicks <= 0) {
            double moved = Math.hypot(loc.getX() - lastX, loc.getZ() - lastZ);
            stuckTicks = moved < 0.03 ? stuckTicks + 1 : 0;
        } else {
            stuckTicks = 0;
        }
        lastX = loc.getX();
        lastZ = loc.getZ();

        if (stuckTicks >= STUCK_TICKS) {
            stuckTicks = 0;
            sidestepTicks = SIDESTEP_TICKS;
            sidestepDir = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        }
    }

    /** Vanilla LivingEntity#moveRelative: input is scaled by 0.98, normalised, rotated by yaw. */
    private void addInput(
        Vector vel,
        double forward,
        double strafe,
        boolean sprint
    ) {
        double f = forward * INPUT_SCALE;
        double s = strafe * INPUT_SCALE;
        double lenSq = f * f + s * s;
        if (lenSq < 1.0E-7) return;
        if (lenSq > 1.0) {
            double len = Math.sqrt(lenSq);
            f /= len;
            s /= len;
        }

        double accel = getAcceleration(sprint);
        f *= accel;
        s *= accel;

        double yaw = Math.toRadians(mannequin.getLocation().getYaw());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);

        // strafe > 0 is "left", like vanilla
        vel.setX(vel.getX() + s * cos - f * sin);
        vel.setZ(vel.getZ() + f * cos + s * sin);
    }

    /** The world-space direction the given input moves us in, or null if there's none. */
    private Vector worldDirection(double forward, double strafe) {
        double yaw = Math.toRadians(mannequin.getLocation().getYaw());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double x = strafe * cos - forward * sin;
        double z = forward * cos + strafe * sin;
        double len = Math.hypot(x, z);
        if (len < 1.0E-6) return null;
        return new Vector(x / len, 0, z / len);
    }

    /**
     * Per-tick acceleration, same formula as vanilla.
     * Speed / Slowness are attribute modifiers, so getValue() already includes them.
     */
    private double getAcceleration(boolean sprint) {
        double speed =
            getAttributeValue(
                Attribute.MOVEMENT_SPEED,
                DEFAULT_MOVEMENT_SPEED
            ) * (sprint ? SPRINT_MULTIPLIER : 1.0);

        if (mannequin.isInWater()) {
            double efficiency = getAttributeValue(
                Attribute.WATER_MOVEMENT_EFFICIENCY,
                0.0
            );
            return AIR_ACCELERATION + (speed - AIR_ACCELERATION) * efficiency;
        }
        if (mannequin.isInLava()) return AIR_ACCELERATION;

        if (mannequin.isOnGround()) {
            double friction = getBlockFriction();
            return speed * (0.216 / (friction * friction * friction));
        }
        return sprint ? SPRINT_AIR_ACCELERATION : AIR_ACCELERATION;
    }

    private double getBlockFriction() {
        Block below = mannequin
            .getLocation()
            .subtract(0, 0.500001, 0)
            .getBlock();
        return switch (below.getType()) {
            case ICE, PACKED_ICE, FROSTED_ICE -> 0.98;
            case BLUE_ICE -> 0.989;
            case SLIME_BLOCK -> 0.8;
            default -> 0.6;
        };
    }

    private boolean isClimbing() {
        return Tag.CLIMBABLE.isTagged(
            mannequin.getLocation().getBlock().getType()
        );
    }

    // ------------------------------------------------------------------ jumping

    private double getJumpPower() {
        double power = getAttributeValue(
            Attribute.JUMP_STRENGTH,
            DEFAULT_JUMP_STRENGTH
        );
        PotionEffect boost = mannequin.getPotionEffect(
            PotionEffectType.JUMP_BOOST
        );
        if (boost != null) power += 0.1 * (boost.getAmplifier() + 1);
        return power;
    }

    /** Vanilla LivingEntity#jumpFromGround. */
    private void applyJump(Vector vel, boolean sprinting) {
        vel.setY(Math.max(getJumpPower(), vel.getY()));
        if (sprinting) {
            double yaw = Math.toRadians(mannequin.getLocation().getYaw());
            vel.setX(vel.getX() - Math.sin(yaw) * SPRINT_JUMP_BOOST);
            vel.setZ(vel.getZ() + Math.cos(yaw) * SPRINT_JUMP_BOOST);
        }
        jumpCooldown = JUMP_COOLDOWN_TICKS;
    }

    private double getMaxJumpHeight() {
        double gravity = getAttributeValue(Attribute.GRAVITY, DEFAULT_GRAVITY);
        double vy = getJumpPower();
        double y = 0;
        for (int i = 0; i < 100 && vy > 0; i++) {
            y += vy;
            vy = (vy - gravity) * 0.98;
        }
        return y;
    }

    /**
     * Simulates a jump with vanilla physics and returns how far (horizontally, from the
     * take-off point) we've travelled when we come back down to landingDy relative to
     * the take-off height. Returns 0 if that height can't be reached.
     */
    private double jumpReach(
        double landingDy,
        double takeoffSpeed,
        boolean sprinting
    ) {
        double gravity = getAttributeValue(Attribute.GRAVITY, DEFAULT_GRAVITY);
        double airAccel =
            (sprinting ? SPRINT_AIR_ACCELERATION : AIR_ACCELERATION) *
            INPUT_SCALE;

        double vy = getJumpPower();
        // take-off tick still uses ground acceleration + sprint boost
        double h =
            takeoffSpeed +
            (sprinting ? SPRINT_JUMP_BOOST : 0.0) +
            getAcceleration(sprinting) * INPUT_SCALE;
        double friction = 0.6 * 0.91; // ground friction applies after the first move
        double x = 0,
            y = 0,
            peak = 0;

        for (int tick = 0; tick < 100; tick++) {
            x += h;
            y += vy;
            peak = Math.max(peak, y);
            vy = (vy - gravity) * 0.98;
            if (vy < 0 && y <= landingDy) {
                return peak >= landingDy ? x : 0;
            }
            h = h * friction + airAccel;
            friction = 0.91;
        }
        return 0;
    }

    // ------------------------------------------------------------------ parkour

    /**
     * Looks ahead in the direction we're about to move and decides whether to just walk,
     * jump (up a block / across a gap), or stop (wall, or a drop with no safe landing).
     */
    private Plan planParkour(Vector dir, boolean sprinting) {
        double feetY = mannequin.getLocation().getY();
        double height = mannequin.getHeight();
        double step = getAttributeValue(
            Attribute.STEP_HEIGHT,
            DEFAULT_STEP_HEIGHT
        );
        double jumpHeight = getMaxJumpHeight();
        double hSpeed = Math.max(0.0, mannequin.getVelocity().setY(0).dot(dir));
        double ahead = 0.45 + hSpeed;

        // 1. Something solid in front that's taller than we can simply step onto
        if (
            blockedAhead(dir, ahead, feetY + step + EPS, feetY + height - EPS)
        ) {
            double top = floorAhead(
                dir,
                ahead,
                feetY + jumpHeight + EPS,
                feetY + step + EPS
            );
            if (Double.isNaN(top)) return Plan.BRAKE; // wall, fence or low ceiling
            boolean headroom = !blockedAhead(
                dir,
                ahead,
                top + EPS,
                top + height
            );
            return headroom ? Plan.JUMP : Plan.BRAKE;
        }

        // 2. Ground ahead (walking down up to SAFE_DROP is fine)
        double maxStand = feetY + step + EPS;
        double minStand = feetY - SAFE_DROP;
        if (
            !Double.isNaN(floorAhead(dir, ahead, maxStand, minStand))
        ) return Plan.WALK;
        if (
            !Double.isNaN(floorAhead(dir, EDGE_PROBE, maxStand, minStand))
        ) return Plan.WALK;

        // 3. We're at a ledge with no safe drop below: jump the gap or stop
        return planGap(dir, feetY, sprinting, hSpeed);
    }

    private Plan planGap(
        Vector dir,
        double feetY,
        boolean sprinting,
        double hSpeed
    ) {
        if (jumpCooldown > 0) return Plan.BRAKE;

        double height = mannequin.getHeight();
        double jumpHeight = getMaxJumpHeight();

        for (double dist = 0.5; dist <= MAX_GAP_SCAN; dist += 0.25) {
            double floor = floorAhead(
                dir,
                dist,
                feetY + jumpHeight + EPS,
                feetY - SAFE_DROP
            );
            if (Double.isNaN(floor)) continue;

            // nearest landing found: can we actually make it?
            double reach = jumpReach(floor - feetY, hSpeed, sprinting);
            boolean headroom = !blockedAhead(
                dir,
                dist,
                floor + EPS,
                floor + height
            );
            return reach >= dist - 0.1 && headroom ? Plan.JUMP : Plan.BRAKE;
        }
        return Plan.BRAKE;
    }

    /** Highest surface in [minY, maxY] in front of us (best of 3 samples across our width). */
    private double floorAhead(
        Vector dir,
        double dist,
        double maxY,
        double minY
    ) {
        Location loc = mannequin.getLocation();
        double best = Double.NaN;
        for (double lateral : LATERAL_SAMPLES) {
            double x = loc.getX() + dir.getX() * dist - dir.getZ() * lateral;
            double z = loc.getZ() + dir.getZ() * dist + dir.getX() * lateral;
            double y = standY(x, z, maxY, minY);
            if (!Double.isNaN(y) && (Double.isNaN(best) || y > best)) best = y;
        }
        return best;
    }

    private boolean blockedAhead(
        Vector dir,
        double dist,
        double y0,
        double y1
    ) {
        Location loc = mannequin.getLocation();
        for (double lateral : LATERAL_SAMPLES) {
            double x = loc.getX() + dir.getX() * dist - dir.getZ() * lateral;
            double z = loc.getZ() + dir.getZ() * dist + dir.getX() * lateral;
            if (solidAt(x, z, y0, y1)) return true;
        }
        return false;
    }

    /** Highest collision-surface top in this column that lies within [minY, maxY], or NaN. */
    private double standY(double x, double z, double maxY, double minY) {
        World world = mannequin.getWorld();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return Double.NaN;

        double best = Double.NaN;
        // start one block lower than minY because fences/walls collide up to 1.5 tall
        for (
            int by = (int) Math.floor(maxY);
            by >= (int) Math.floor(minY) - 1;
            by--
        ) {
            if (
                by < world.getMinHeight() || by >= world.getMaxHeight()
            ) continue;
            Block block = world.getBlockAt(bx, by, bz);
            for (BoundingBox bb : block
                .getCollisionShape()
                .getBoundingBoxes()) {
                if (!columnContains(bb, x - bx, z - bz)) continue;
                double top = by + bb.getMaxY();
                if (
                    top <= maxY + EPS &&
                    top >= minY - EPS &&
                    (Double.isNaN(best) || top > best)
                ) best = top;
            }
        }
        return best;
    }

    /** Whether any block collision overlaps the vertical span (y0, y1) at this x/z point. */
    private boolean solidAt(double x, double z, double y0, double y1) {
        World world = mannequin.getWorld();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return true; // treat unloaded as a wall

        for (
            int by = (int) Math.floor(y0) - 1;
            by <= (int) Math.floor(y1);
            by++
        ) {
            if (
                by < world.getMinHeight() || by >= world.getMaxHeight()
            ) continue;
            Block block = world.getBlockAt(bx, by, bz);
            for (BoundingBox bb : block
                .getCollisionShape()
                .getBoundingBoxes()) {
                if (!columnContains(bb, x - bx, z - bz)) continue;
                if (
                    by + bb.getMaxY() > y0 && by + bb.getMinY() < y1
                ) return true;
            }
        }
        return false;
    }

    private static boolean columnContains(
        BoundingBox bb,
        double localX,
        double localZ
    ) {
        return (
            localX >= bb.getMinX() &&
            localX <= bb.getMaxX() &&
            localZ >= bb.getMinZ() &&
            localZ <= bb.getMaxZ()
        );
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
