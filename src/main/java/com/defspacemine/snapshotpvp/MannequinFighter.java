package com.defspacemine.snapshotpvp;

import com.google.common.collect.Multimap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
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
 * sprint hits get the vanilla extra knockback and sprint reset, and it tries
 * to stay out of the void: it won't walk or jump off a ledge it can't survive,
 * brakes when knocked toward one, reverses its strafe at one, and air-strafes
 * back to the nearest ground if it ends up falling. A bow or crossbow in its main
 * hand makes it a pure archer that keeps its distance. A bow or crossbow in its
 * OFFHAND (with something else in the main hand) makes it a hybrid: it shoots the
 * offhand weapon from range and switches to its main-hand weapon up close.
 *
 * Difficulty only adds openings (sloppier spacing, an occasional missed swing,
 * slower aim, less accurate arrows); it never lowers damage, speed or knockback.
 * HARD is the default and is the full-strength behaviour described above.
 *
 * A Mannequin isn't a player, so it starts with the generic entity attribute
 * defaults (movement speed 0.7, no attack attributes). The constructor sets the
 * base movement speed to the player's 0.1, and the attack numbers fall back to
 * player defaults below.
 */
public class MannequinFighter {

    public final Mannequin mannequin;
    private final Plugin plugin;
    private final Difficulty difficulty;
    // How close it closes in before holding position; re-rolled after every swing
    private double approachFactor;

    // ---- targeting / combat
    private static final double SEARCH_RADIUS = 16.0;
    private static final int RETARGET_INTERVAL_TICKS = 10;

    private static final double DEFAULT_INTERACTION_RANGE = 2.5;
    // Held back for this many attack cooldowns: the next hit gets bonus reach
    private static final int CHARGED_REACH_COOLDOWNS = 2;
    private static final double CHARGED_REACH_BONUS = 1.0;
    private static final double DEFAULT_ATTACK_SPEED = 4.0;
    private static final double DEFAULT_ATTACK_DAMAGE = 2.0;

    // Back off a little if we end up standing inside the target
    private static final double BACK_OFF_FACTOR = 0.4;
    // Start strafing once the target is within reach + this
    private static final double STRAFE_RANGE = 2.0;
    // Only swing when we're actually looking at the target (degrees)
    private static final double MAX_AIM_ERROR = 25.0;

    // ---- knockback (vanilla)
    private static final double BASE_KNOCKBACK = 0.4; // applied by damage() itself
    private static final double KNOCKBACK_PER_LEVEL = 0.5; // sprint hit / Knockback enchant, each count as one level
    private static final double SPRINT_HIT_SLOWDOWN = 0.6; // attacker keeps 60% of horizontal speed
    private static final int SPRINT_RESET_TICKS = 3; // the w-tap after a sprint hit

    // ---- criticals
    private static final double CRIT_MULTIPLIER = 1.5;
    // Hop so the swing lands on the way down. Chance per attack, 0 disables hopping.
    private static final double CRIT_HOP_CHANCE = 0.7;
    private static final double CRIT_HOP_RANGE = 2.5; // start hopping this far outside reach
    private static final int CRIT_HOP_LEAD_TICKS = 8; // hop when the swing is this close to ready
    private static final int CRIT_WAIT_MAX_TICKS = 20; // never hold a swing longer than this

    // ---- fluid crit rule
    private static final int CRIT_FLUID_GRACE_TICKS = 20; // no crits this soon after leaving water/lava

    // ---- ranged weapons (bow / crossbow)
    private static final double RANGED_SEARCH_RADIUS = 32.0; // sees targets from further away than melee
    private static final double RANGED_BACK_OFF_DISTANCE = 8.0; // retreat when closer than this
    private static final double RANGED_APPROACH_DISTANCE = 24.0; // close in when further than this (or no line of sight)
    private static final double RANGED_SHOOT_DISTANCE = 30.0;
    // Hybrid (ranged weapon in the offhand): melee below the first distance, shoot beyond the second
    private static final double HYBRID_MELEE_DISTANCE = 5.0;
    private static final double HYBRID_RANGED_DISTANCE = 7.0;
    private static final int RANGED_RECOVERY_TICKS = 4;
    private static final int BOW_DRAW_TICKS = 20;
    private static final int CROSSBOW_CHARGE_TICKS = 25; // minus 5 per Quick Charge level
    private static final double BOW_ARROW_SPEED = 3.0;
    private static final double CROSSBOW_ARROW_SPEED = 3.15;
    private static final double ITEM_USE_SLOWDOWN = 0.2; // vanilla slows you to 20% while drawing
    private static final double ARROW_GRAVITY = 0.05;
    private static final double ARROW_DRAG = 0.99;

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

    // ---- void avoidance
    private static final double MOMENTUM_BRAKE_SPEED = 0.1; // blocks/tick of slide worth reacting to
    private static final double RECOVERY_SCAN_RADIUS = 6.0;
    private static final double RECOVERY_RING_STEP = 1.0;
    private static final int RECOVERY_SAMPLES = 12;
    private static final double RECOVERY_FALL_BUDGET = 12.0; // ground within this far down counts as "not the void"
    private static final int RECOVERY_REFRESH_TICKS = 4;
    private static final int JUMP_TRUST_TICKS = 14; // don't second-guess a planned jump mid-air

    // ---- unstick
    private static final int STUCK_TICKS = 12;
    private static final int SIDESTEP_TICKS = 25;

    /**
     * HARD is the full-strength bot. EASY and MEDIUM keep the same damage, speed and
     * knockback but leave it more openings: it closes in further than it needs to,
     * sometimes whiffs a swing because its aim is off, turns more slowly, and its
     * bow / crossbow aim wobbles, is released more sloppily, is spread wider and
     * leads moving targets less.
     */
    public enum Difficulty {
        //   approach range   melee miss   aim turn   arrow    arrow   archer     aim      release
        //   (x reach)        chance       speed      spread   lead    back-off   wobble   tolerance
        //                                                                         (deg)    (deg)
        EASY(0.50, 0.70, 0.25, 0.6, 6.0, 0.4, 0.5, 4.0, 10.0),
        MEDIUM(0.65, 0.80, 0.10, 0.8, 3.0, 0.7, 0.75, 2.0, 8.0),
        HARD(0.85, 0.85, 0.00, 1.0, 1.0, 1.0, 1.0, 0.0, 6.0);

        final double approachMin;
        final double approachMax;
        final double meleeMissChance;
        final double turnScale;
        final double rangedInaccuracy;
        final double leadScale;
        final double backOffScale;
        final double rangedAimWobble;
        final double rangedReleaseTolerance;

        Difficulty(
            double approachMin,
            double approachMax,
            double meleeMissChance,
            double turnScale,
            double rangedInaccuracy,
            double leadScale,
            double backOffScale,
            double rangedAimWobble,
            double rangedReleaseTolerance
        ) {
            this.approachMin = approachMin;
            this.approachMax = approachMax;
            this.meleeMissChance = meleeMissChance;
            this.turnScale = turnScale;
            this.rangedInaccuracy = rangedInaccuracy;
            this.leadScale = leadScale;
            this.backOffScale = backOffScale;
            this.rangedAimWobble = rangedAimWobble;
            this.rangedReleaseTolerance = rangedReleaseTolerance;
        }
    }

    private enum Plan {
        WALK,
        JUMP,
        BRAKE,
    }

    private enum RangedWeapon {
        NONE,
        BOW,
        CROSSBOW,
    }

    private BukkitTask task;
    private LivingEntity target;
    private int ticksSinceRetarget = RETARGET_INTERVAL_TICKS;
    private int attackCooldownTicks = 0;
    private int ticksSinceAttack = 0;

    private int jumpCooldown = 0;
    private int strafeTicksLeft = 0;
    private double strafeDir = 1;
    private boolean wantsToMove = false;
    private boolean sprinting = false;
    private int sprintResetTicks = 0;
    private boolean hopForCrit = false;
    private int critWaitTicks = 0;
    private int ticksSinceFluid = 1000;

    private RangedWeapon rangedMode = RangedWeapon.NONE;
    private boolean targetVisible = false;
    private boolean aimSolved = false;
    private int drawTicks = 0;
    private int shotCooldown = 0;
    private EquipmentSlot rangedHand = EquipmentSlot.HAND;
    private boolean hybrid = false;
    private double wobbleYaw = 0;
    private double wobblePitch = 0;
    private int wobbleTicks = 0;
    private double lastAimPitchError = 0;
    private LivingEntity velTarget;
    private Vector lastTargetPos;
    private Vector targetVel = new Vector();
    private Vector recoveryDir;
    private int recoveryRefresh = 0;
    private int recoverySuppressTicks = 0;
    private int stuckTicks = 0;
    private int sidestepTicks = 0;
    private double sidestepDir = 1;
    private double lastX, lastZ;

    /** Full-strength bot (HARD). */
    public MannequinFighter(Plugin plugin, Mannequin mannequin) {
        this(plugin, mannequin, Difficulty.HARD);
    }

    public MannequinFighter(
        Plugin plugin,
        Mannequin mannequin,
        Difficulty difficulty
    ) {
        this.plugin = plugin;
        this.mannequin = mannequin;
        this.difficulty = difficulty == null ? Difficulty.HARD : difficulty;
        this.approachFactor = rollApproachFactor();
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

        // Base reach is 2.5; modifiers on the attribute still apply on top
        AttributeInstance reach = mannequin.getAttribute(
            Attribute.ENTITY_INTERACTION_RANGE
        );
        if (reach != null) reach.setBaseValue(DEFAULT_INTERACTION_RANGE);
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
                ticksSinceAttack = Math.min(ticksSinceAttack + 1, 100_000);
                if (jumpCooldown > 0) jumpCooldown--;
                if (sprintResetTicks > 0) sprintResetTicks--;
                if (mannequin.isInWater() || mannequin.isInLava()) {
                    ticksSinceFluid = 0;
                } else {
                    ticksSinceFluid = Math.min(ticksSinceFluid + 1, 1000);
                }
                if (recoverySuppressTicks > 0) recoverySuppressTicks--;

                if (
                    ++ticksSinceRetarget >= RETARGET_INTERVAL_TICKS ||
                    !isValidTarget(target)
                ) {
                    ticksSinceRetarget = 0;
                    target = findTarget();
                }
                if (target == null) {
                    velTarget = null;
                    drawTicks = 0;
                    return;
                }

                tickCombat(target);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void tickCombat(LivingEntity target) {
        double reach = getAttributeValue(
            Attribute.ENTITY_INTERACTION_RANGE,
            DEFAULT_INTERACTION_RANGE
        );
        // Positioning (approach / strafe distances) uses the normal reach; only the
        // swing itself gets the bonus, so a held-back mannequin lunges in from further out
        double attackReach =
            reach + (hasChargedReach() ? CHARGED_REACH_BONUS : 0.0);
        double distance = distanceToBoundingBox(target);

        updateCombatMode(distance);
        trackTargetVelocity(target);

        Vector aimPoint = target.getBoundingBox().getCenter();
        if (rangedMode != RangedWeapon.NONE) {
            targetVisible = mannequin.hasLineOfSight(target);
            Vector solved = rangedAimPoint(target);
            aimSolved = solved != null;
            if (solved != null) aimPoint = solved;
        } else {
            drawTicks = 0;
            shotCooldown = 0;
        }

        double aimError = lookAt(aimPoint);
        steer(target, reach, distance);

        // Bow / crossbow: shoot instead of meleeing
        if (rangedMode != RangedWeapon.NONE) {
            tickRanged(distance, aimError);
            return;
        }

        boolean ready =
            distance <= attackReach &&
            aimError <= MAX_AIM_ERROR &&
            attackCooldownTicks <= 0 &&
            !hasDamageImmunity(target) &&
            mannequin.hasLineOfSight(target);

        // Hopping for a crit: hold the swing until we're actually falling
        if (
            ready &&
            hopForCrit &&
            !isCriticalHit() &&
            ticksSinceFluid >= CRIT_FLUID_GRACE_TICKS &&
            critWaitTicks < CRIT_WAIT_MAX_TICKS
        ) {
            critWaitTicks++;
            ready = false;
        }

        if (ready) {
            if (rollsMiss(aimError)) {
                // Whiffed: the swing animation plays and the cooldown is spent,
                // but no damage or knockback is dealt
                mannequin.swingMainHand();
            } else {
                performAttack(target);
            }
            attackCooldownTicks = getAttackCooldownTicks();
            critWaitTicks = 0;
            ticksSinceAttack = 0;
            hopForCrit =
                ThreadLocalRandom.current().nextDouble() < CRIT_HOP_CHANCE;
            approachFactor = rollApproachFactor();
        }
    }

    /** Picks how close it will close in this time. HARD is always the same tight spacing. */
    private double rollApproachFactor() {
        double min = difficulty.approachMin;
        double max = difficulty.approachMax;
        return max <= min
            ? min
            : ThreadLocalRandom.current().nextDouble(min, max);
    }

    /** Lower difficulties occasionally whiff; being further off-target at the swing makes it likelier. */
    private boolean rollsMiss(double aimError) {
        double base = difficulty.meleeMissChance;
        if (base <= 0) return false;
        double chance = Math.min(0.9, base * (0.5 + aimError / MAX_AIM_ERROR));
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    // ------------------------------------------------------------------ attack

    private void performAttack(LivingEntity victim) {
        mannequin.swingMainHand();

        boolean sprintHit = sprinting;
        // Custom rules: unlike vanilla, sprinting does NOT cancel a critical hit
        boolean crit = isCriticalHit();

        DamageSource source = DamageSource.builder(DamageType.MOB_ATTACK)
            .withCausingEntity(mannequin)
            .withDirectEntity(mannequin)
            .build();

        Vector velBefore = victim.getVelocity();
        double before = victim.getHealth() + victim.getAbsorptionAmount();
        victim.damage(calculateDamage(crit), source);
        double after = victim.getHealth() + victim.getAbsorptionAmount();

        // if (after >= before) return; // No damage dealt

        World world = victim.getWorld();
        Location hitPoint = victim
            .getLocation()
            .add(0, victim.getHeight() * 0.5, 0);

        if (crit) {
            world.playSound(hitPoint, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
            world.spawnParticle(Particle.CRIT, hitPoint, 15, 0.3, 0.3, 0.3, 0.2);
        }

        // Vanilla shows the enchanted-hit sparkles whenever an enchantment added damage
        if (getSharpnessLevel() > 0) {
            world.spawnParticle(
                Particle.ENCHANTED_HIT,
                hitPoint,
                15,
                0.3,
                0.3,
                0.3,
                0.2
            );
        }

        // NOTE: LivingEntity#knockback takes the direction of the knockback SOURCE
        // (victim -> attacker); the victim is pushed the opposite way. The old code
        // passed attacker -> victim, which pulled the victim toward the mannequin.

        // 1. Base 0.4 knockback. damage() normally applies it itself, but if the
        // server swallowed it (victim velocity untouched) apply it here.
        Location from = mannequin.getLocation();
        Location to = victim.getLocation();
        if (victim.getVelocity().distanceSquared(velBefore) < 1.0E-6) {
            victim.knockback(
                BASE_KNOCKBACK,
                from.getX() - to.getX(),
                from.getZ() - to.getZ()
            );
        }

        // 2. Extra knockback like Player#attack: (sprint + Knockback enchant + attribute) * 0.5,
        // pushed along the direction the attacker is facing
        double level =
            getAttributeValue(Attribute.ATTACK_KNOCKBACK, 0.0) +
            getKnockbackEnchantLevel() +
            (sprintHit ? 1 : 0);
        if (level <= 0) return;

        if (sprintHit) {
            world.playSound(
                from,
                Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK,
                1f,
                1f
            );
        }

        double yaw = Math.toRadians(from.getYaw());
        victim.knockback(
            level * KNOCKBACK_PER_LEVEL,
            Math.sin(yaw),
            -Math.cos(yaw)
        );

        // Vanilla: any extra-knockback hit costs the attacker 40% of its horizontal
        // speed and drops its sprint (the w-tap)
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

    /**
     * Vanilla critical conditions (falling, off the ground, not climbing / swimming /
     * blind / riding) minus the "not sprinting" rule, so crit + sprint hits stack.
     * Also no crits in water/lava or for CRIT_FLUID_GRACE_TICKS after leaving it, so
     * swimming up out of water can't be turned into a crit.
     */
    private boolean isCriticalHit() {
        return (
            mannequin.getFallDistance() > 0.0f &&
            !mannequin.isOnGround() &&
            !isClimbing() &&
            !mannequin.isInWater() &&
            !mannequin.isInLava() &&
            ticksSinceFluid >= CRIT_FLUID_GRACE_TICKS &&
            !mannequin.isInsideVehicle() &&
            !mannequin.hasPotionEffect(PotionEffectType.BLINDNESS)
        );
    }

    /**
     * True while the victim is still inside its post-hit invulnerability window
     * (noDamageTicks above half of maximumNoDamageTicks, the same threshold vanilla
     * uses to ignore incoming damage). We skip the swing entirely then, so there's
     * no wasted attack and no knockback. The swing isn't put on cooldown either,
     * so it fires on the first tick the window ends.
     */
    private boolean hasDamageImmunity(LivingEntity victim) {
        return (
            victim.getNoDamageTicks() > victim.getMaximumNoDamageTicks() / 2.0
        );
    }

    /**
     * True once it has gone CHARGED_REACH_COOLDOWNS full attack cooldowns without
     * swinging. The next hit then gets CHARGED_REACH_BONUS extra reach, and the
     * timer resets as soon as it attacks.
     */
    private boolean hasChargedReach() {
        return (
            ticksSinceAttack >=
            CHARGED_REACH_COOLDOWNS * getAttackCooldownTicks()
        );
    }

    /** Whether to start a hop now so the next swing lands on the way down. */
    private boolean shouldCritHop(double distance, double reach) {
        return (
            rangedMode == RangedWeapon.NONE &&
            hopForCrit &&
            attackCooldownTicks <= CRIT_HOP_LEAD_TICKS &&
            distance <= reach + CRIT_HOP_RANGE
        );
    }

    private int getKnockbackEnchantLevel() {
        EntityEquipment equipment = mannequin.getEquipment();
        if (equipment == null) return 0;
        return equipment
            .getItemInMainHand()
            .getEnchantmentLevel(Enchantment.KNOCKBACK);
    }

    /**
     * Weapon-adjusted attack damage plus sharpness. Like vanilla, a crit multiplies
     * the base damage only; the sharpness bonus is added afterwards.
     */
    private double calculateDamage(boolean crit) {
        double damage = getHeldValue(
            Attribute.ATTACK_DAMAGE,
            DEFAULT_ATTACK_DAMAGE
        );
        if (crit) damage *= CRIT_MULTIPLIER;

        int sharpness = getSharpnessLevel();
        if (sharpness > 0) damage += 0.5 * sharpness + 0.5;

        return damage;
    }

    private int getSharpnessLevel() {
        EntityEquipment equipment = mannequin.getEquipment();
        if (equipment == null) return 0;
        return equipment
            .getItemInMainHand()
            .getEnchantmentLevel(Enchantment.SHARPNESS);
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

    // ------------------------------------------------------------------ ranged

    /** The bow / crossbow (if any) held in the given hand. */
    private RangedWeapon weaponIn(EquipmentSlot slot) {
        EntityEquipment equipment = mannequin.getEquipment();
        if (equipment == null) return RangedWeapon.NONE;
        return switch (equipment.getItem(slot).getType()) {
            case BOW -> RangedWeapon.BOW;
            case CROSSBOW -> RangedWeapon.CROSSBOW;
            default -> RangedWeapon.NONE;
        };
    }

    /** A bow / crossbow in either hand (used for how far it can see targets). */
    private RangedWeapon getRangedWeapon() {
        RangedWeapon off = weaponIn(EquipmentSlot.OFF_HAND);
        return off != RangedWeapon.NONE ? off : weaponIn(EquipmentSlot.HAND);
    }

    /** The item in the hand that holds the bow / crossbow being used. */
    private ItemStack getRangedItem() {
        EntityEquipment equipment = mannequin.getEquipment();
        if (equipment == null) return new ItemStack(Material.AIR);
        return rangedHand == EquipmentSlot.OFF_HAND
            ? equipment.getItemInOffHand()
            : equipment.getItemInMainHand();
    }

    /**
     * Picks shooting or melee for this tick.
     *  - ranged weapon in the main hand: pure archer, always shoots
     *  - ranged weapon in the offhand + anything else in the main hand: hybrid,
     *    shoots from range and switches to the main-hand weapon up close
     *    (with a gap between the two distances so it doesn't flicker)
     *  - otherwise: melee
     */
    private void updateCombatMode(double distance) {
        RangedWeapon main = weaponIn(EquipmentSlot.HAND);
        RangedWeapon off = weaponIn(EquipmentSlot.OFF_HAND);

        if (main == RangedWeapon.NONE && off != RangedWeapon.NONE) {
            hybrid = true;
            rangedHand = EquipmentSlot.OFF_HAND;
            boolean shooting = rangedMode != RangedWeapon.NONE
                ? distance > HYBRID_MELEE_DISTANCE
                : distance > HYBRID_RANGED_DISTANCE;
            rangedMode = shooting ? off : RangedWeapon.NONE;
        } else if (main != RangedWeapon.NONE) {
            hybrid = false;
            rangedHand = EquipmentSlot.HAND;
            rangedMode = main;
        } else {
            hybrid = false;
            rangedMode = RangedWeapon.NONE;
        }
    }

    private double getArrowSpeed(RangedWeapon weapon) {
        return weapon == RangedWeapon.CROSSBOW
            ? CROSSBOW_ARROW_SPEED
            : BOW_ARROW_SPEED;
    }

    /** Ticks to fully draw a bow / charge a crossbow (Quick Charge speeds up the crossbow). */
    private int getDrawTicks(RangedWeapon weapon) {
        if (weapon == RangedWeapon.BOW) return BOW_DRAW_TICKS;
        int quickCharge = getRangedItem().getEnchantmentLevel(
            Enchantment.QUICK_CHARGE
        );
        return Math.max(5, CROSSBOW_CHARGE_TICKS - 5 * quickCharge);
    }

    /** Smoothed per-tick velocity of the target, measured by hand (player velocity isn't reliable). */
    private void trackTargetVelocity(LivingEntity target) {
        Vector pos = target.getLocation().toVector();
        if (target != velTarget || lastTargetPos == null) {
            velTarget = target;
            targetVel = new Vector();
        } else {
            Vector instant = pos.clone().subtract(lastTargetPos);
            targetVel = targetVel.multiply(0.5).add(instant.multiply(0.5));
        }
        lastTargetPos = pos;
    }

    private void tickRanged(double distance, double aimError) {
        if (shotCooldown > 0) {
            shotCooldown--;
            drawTicks = 0;
            return;
        }

        boolean canShoot =
            targetVisible && aimSolved && distance <= RANGED_SHOOT_DISTANCE;
        if (!canShoot) {
            drawTicks = 0; // lower the bow
            return;
        }

        int required = getDrawTicks(rangedMode);
        if (drawTicks < required) drawTicks++;

        // Only release once fully drawn AND actually looking where the arrow should go
        if (
            drawTicks >= required &&
            aimError <= difficulty.rangedReleaseTolerance &&
            lastAimPitchError <= difficulty.rangedReleaseTolerance
        ) {
            fireProjectiles(rangedMode);
            drawTicks = 0;
            shotCooldown = RANGED_RECOVERY_TICKS;
        }
    }

    private void fireProjectiles(RangedWeapon weapon) {
        ItemStack held = getRangedItem();

        double speed = getArrowSpeed(weapon);
        Vector look = applyInaccuracy(mannequin.getEyeLocation().getDirection());
        boolean multishot =
            weapon == RangedWeapon.CROSSBOW &&
            held.getEnchantmentLevel(Enchantment.MULTISHOT) > 0;

        int arrows = multishot ? 3 : 1;
        for (int i = 0; i < arrows; i++) {
            Vector dir = look.clone();
            if (multishot) dir.rotateAroundY(Math.toRadians((i - 1) * 10.0));
            launchArrow(weapon, held, dir.multiply(speed));
        }

        if (rangedHand == EquipmentSlot.OFF_HAND) mannequin.swingOffHand();
        else mannequin.swingMainHand();
        mannequin
            .getWorld()
            .playSound(
                mannequin.getEyeLocation(),
                weapon == RangedWeapon.BOW
                    ? Sound.ENTITY_ARROW_SHOOT
                    : Sound.ITEM_CROSSBOW_SHOOT,
                1f,
                1f
            );
    }

    private void launchArrow(RangedWeapon weapon, ItemStack held, Vector velocity) {
        Arrow arrow = mannequin.launchProjectile(Arrow.class, velocity);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        // We only release at full draw, and crossbow arrows are always critical
        arrow.setCritical(true);

        if (weapon == RangedWeapon.BOW) {
            int power = held.getEnchantmentLevel(Enchantment.POWER);
            if (power > 0) {
                arrow.setDamage(arrow.getDamage() + 0.5 * power + 0.5);
            }
            int punch = held.getEnchantmentLevel(Enchantment.PUNCH);
            if (punch > 0) arrow.setKnockbackStrength(punch);
            if (held.getEnchantmentLevel(Enchantment.FLAME) > 0) {
                arrow.setFireTicks(100);
            }
        } else {
            int piercing = held.getEnchantmentLevel(Enchantment.PIERCING);
            if (piercing > 0) arrow.setPierceLevel(piercing);
        }
    }

    /** Vanilla's projectile inaccuracy: a little gaussian noise on each axis. */
    private Vector applyInaccuracy(Vector dir) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double spread = 0.0075 * difficulty.rangedInaccuracy;
        return dir
            .clone()
            .add(
                new Vector(
                    rng.nextGaussian() * spread,
                    rng.nextGaussian() * spread,
                    rng.nextGaussian() * spread
                )
            )
            .normalize();
    }

    /**
     * Where to look so an arrow lands on the (moving) target: leads the target
     * horizontally and compensates for arrow drop. Null if it's out of range.
     */
    private Vector rangedAimPoint(LivingEntity target) {
        double speed = getArrowSpeed(rangedMode);
        Vector eye = mannequin.getEyeLocation().toVector();
        Vector center = target.getBoundingBox().getCenter();

        // lower difficulties under-lead moving targets
        double leadTicks =
            Math.min(center.distance(eye) / speed, 40.0) * difficulty.leadScale;
        Vector predicted = center
            .clone()
            .add(
                new Vector(
                    targetVel.getX() * leadTicks,
                    0,
                    targetVel.getZ() * leadTicks
                )
            );

        Vector dir = solveLaunchDirection(eye, predicted, speed);
        if (dir == null) return null;
        Vector point = eye
            .clone()
            .add(dir.multiply(Math.max(predicted.distance(eye), 1.0)));
        return applyAimWobble(eye, point);
    }

    /**
     * Lower difficulties track the target imperfectly: the aim point drifts by a
     * random yaw / pitch offset that is re-rolled every half second.
     */
    private Vector applyAimWobble(Vector eye, Vector point) {
        double wobble = difficulty.rangedAimWobble;
        if (wobble <= 0) return point;

        if (--wobbleTicks <= 0) {
            wobbleTicks = 10;
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            wobbleYaw = rng.nextGaussian() * wobble;
            wobblePitch = rng.nextGaussian() * wobble;
        }

        Vector to = point.clone().subtract(eye);
        double len = to.length();
        if (len < 1.0E-6) return point;

        double yaw = Math.atan2(-to.getX(), to.getZ()) + Math.toRadians(wobbleYaw);
        double pitch =
            Math.asin(Math.max(-1.0, Math.min(1.0, to.getY() / len))) +
            Math.toRadians(wobblePitch);

        Vector dir = new Vector(
            -Math.sin(yaw) * Math.cos(pitch),
            Math.sin(pitch),
            Math.cos(yaw) * Math.cos(pitch)
        );
        return eye.clone().add(dir.multiply(len));
    }

    /** Bisects the launch pitch so the arrow passes through the point, simulating vanilla arrow physics. */
    private Vector solveLaunchDirection(Vector from, Vector to, double speed) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 1.0E-3) {
            return new Vector(0, dy >= 0 ? 1 : -1, 0);
        }

        double lo = -0.6;
        double hi = 1.2; // radians
        if (arrowHeightAt(horizontal, hi, speed) < dy) return null; // out of range

        for (int i = 0; i < 14; i++) {
            double mid = (lo + hi) / 2;
            if (arrowHeightAt(horizontal, mid, speed) < dy) lo = mid;
            else hi = mid;
        }

        double pitch = (lo + hi) / 2;
        double cos = Math.cos(pitch);
        return new Vector(
            (dx / horizontal) * cos,
            Math.sin(pitch),
            (dz / horizontal) * cos
        );
    }

    /** Height of an arrow launched at this pitch when it has travelled `horizontal` blocks. */
    private double arrowHeightAt(double horizontal, double pitch, double speed) {
        double vx = speed * Math.cos(pitch);
        double vy = speed * Math.sin(pitch);
        double x = 0;
        double y = 0;
        for (int tick = 0; tick < 120; tick++) {
            double nx = x + vx;
            double ny = y + vy;
            if (nx >= horizontal) {
                double t = (horizontal - x) / (nx - x);
                return y + (ny - y) * t;
            }
            x = nx;
            y = ny;
            vx *= ARROW_DRAG;
            vy = vy * ARROW_DRAG - ARROW_GRAVITY;
        }
        return Double.POSITIVE_INFINITY; // never gets there (too steep)
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

        float turn = (float) difficulty.turnScale; // lower difficulties turn more slowly
        yaw += clamp(
            dYaw * TURN_SMOOTHING * turn,
            -MAX_YAW_STEP * turn,
            MAX_YAW_STEP * turn
        );
        pitch += clamp(
            dPitch * TURN_SMOOTHING * turn,
            -MAX_PITCH_STEP * turn,
            MAX_PITCH_STEP * turn
        );
        pitch = clamp(pitch, -90f, 90f);

        mannequin.setRotation(yaw, pitch);
        lastAimPitchError = Math.abs(targetPitch - pitch);
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
        } else if (rangedMode != RangedWeapon.NONE) {
            // Archer: hold a distance, strafe while shooting, close in when it
            // can't see the target or the target is too far to shoot
            if (!targetVisible || distance > RANGED_APPROACH_DISTANCE) {
                forward = 1;
                sprint = true;
            } else if (
                !hybrid &&
                distance < RANGED_BACK_OFF_DISTANCE * difficulty.backOffScale
            ) {
                forward = -1;
            }
            strafe = nextStrafeDirection() * (forward != 0 ? 0.5 : 1.0);
        } else {
            if (distance > reach * approachFactor) {
                forward = 1;
                // Hold sprint while closing in. It used to turn off beyond
                // reach, so by the time a swing landed it was never sprinting.
                sprint = true;
            } else if (distance < reach * BACK_OFF_FACTOR) {
                forward = -0.6;
            }
            if (distance <= reach + STRAFE_RANGE) {
                strafe = nextStrafeDirection() * (forward != 0 ? 0.5 : 1.0);
            }
        }

        // Drawing a bow / charging a crossbow slows you to 20% and stops sprinting
        if (drawTicks > 0) {
            forward *= ITEM_USE_SLOWDOWN;
            strafe *= ITEM_USE_SLOWDOWN;
            sprint = false;
        }

        // Knocked off an edge or fell short of a jump: air-strafe back toward ground
        Vector recovery = airRecoveryDirection();
        if (recovery != null) {
            double[] input = inputToward(recovery);
            forward = input[0];
            strafe = input[1];
            sprint = true;
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
                    if (forward == 0) reverseStrafe(); // walk the other way along the ledge
                    forward = 0;
                    strafe = 0;
                } else if (plan == Plan.JUMP && jumpCooldown <= 0) {
                    jump = true;
                } else if (
                    plan == Plan.WALK &&
                    jumpCooldown <= 0 &&
                    shouldCritHop(distance, reach)
                ) {
                    jump = true; // hop so the swing lands as a crit
                }
            }
        }

        // Sliding or knocked toward an edge: hold the opposite key
        if (onGround && !inFluid && !jump) {
            Vector momentum = mannequin.getVelocity().setY(0);
            if (momentum.length() > MOMENTUM_BRAKE_SPEED) {
                Vector heading = momentum.normalize();
                if (planParkour(heading, false) == Plan.BRAKE) {
                    double[] counter = inputToward(heading.multiply(-1));
                    forward = counter[0];
                    strafe = counter[1];
                    sprint = false;
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

    private void reverseStrafe() {
        strafeDir = -strafeDir;
        sidestepDir = -sidestepDir;
        strafeTicksLeft = ThreadLocalRandom.current().nextInt(12, 35);
    }

    /** The (forward, strafe) key presses that move us along a world direction at our current yaw. */
    private double[] inputToward(Vector dir) {
        double yaw = Math.toRadians(mannequin.getLocation().getYaw());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        return new double[] {
            -dir.getX() * sin + dir.getZ() * cos,
            dir.getX() * cos + dir.getZ() * sin,
        };
    }

    /**
     * Airborne with nothing solid within RECOVERY_FALL_BUDGET below (knocked off an
     * edge, a jump that fell short): the direction to air-strafe toward the nearest
     * ground, or null when there's nothing to fix. Refreshed every few ticks.
     */
    private Vector airRecoveryDirection() {
        if (
            mannequin.isOnGround() ||
            mannequin.isInWater() ||
            mannequin.isInLava() ||
            isClimbing() ||
            recoverySuppressTicks > 0
        ) {
            recoveryDir = null;
            recoveryRefresh = 0;
            return null;
        }

        if (--recoveryRefresh <= 0) {
            recoveryRefresh = RECOVERY_REFRESH_TICKS;
            recoveryDir = findLandingDirection();
        }
        return recoveryDir;
    }

    private Vector findLandingDirection() {
        Location loc = mannequin.getLocation();
        double y = loc.getY();

        // ground right below us (even a long fall): not the void, nothing to fix
        double below = floorAhead(
            new Vector(1, 0, 0),
            0.0,
            y + EPS,
            y - RECOVERY_FALL_BUDGET
        );
        if (!Double.isNaN(below)) return null;

        // otherwise the nearest ring around us that has ground, preferring the highest
        for (
            double r = RECOVERY_RING_STEP;
            r <= RECOVERY_SCAN_RADIUS;
            r += RECOVERY_RING_STEP
        ) {
            Vector best = null;
            double bestTop = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < RECOVERY_SAMPLES; i++) {
                double angle = (2 * Math.PI * i) / RECOVERY_SAMPLES;
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                double top = standY(
                    loc.getX() + dx * r,
                    loc.getZ() + dz * r,
                    y + EPS,
                    y - RECOVERY_FALL_BUDGET
                );
                if (!Double.isNaN(top) && top > bestTop) {
                    bestTop = top;
                    best = new Vector(dx, 0, dz);
                }
            }
            if (best != null) return best;
        }
        return null; // open void, nothing in reach
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
        recoverySuppressTicks = JUMP_TRUST_TICKS;
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

        // A bow / crossbow can hit from much further, so it notices targets from further
        double radius =
            getRangedWeapon() != RangedWeapon.NONE
                ? RANGED_SEARCH_RADIUS
                : SEARCH_RADIUS;

        for (Entity entity : mannequin.getNearbyEntities(
            radius,
            radius / 2,
            radius
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
