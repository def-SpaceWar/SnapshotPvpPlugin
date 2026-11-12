package com.defspacemine.snapshotpvp;

import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
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
 * When a straight line to the target is blocked it runs an A* search over the
 * terrain and walks the detour instead of pushing into walls.
 *
 * Difficulty only adds openings (sloppier spacing, an occasional missed swing,
 * slower aim, less accurate arrows); it never lowers damage, speed or knockback.
 * MASTER is the default and is the full-strength behaviour described above.
 * Difficulties, easiest to hardest: EASY, MEDIUM, HARD, EXPERT, MASTER.
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
    // Reach shifts with our own movement toward the target, like a real player's does
    // once latency and interpolation are involved: effective reach = reach + speed
    // toward the target (blocks/tick) * this many ticks (~200ms). Deliberately NOT
    // capped, so a sprinting chaser can keep a combo going and a knocked-back
    // fighter really can't swing back until the knockback wears off.
    private static final double VELOCITY_REACH_TICKS = 4.0;
    // A missed swing costs this many EXTRA ticks on top of the normal attack cooldown
    private static final int ATTACK_MISS_PENALTY_TICKS = 4;
    // The AI swings once per this many "full charge" times. 2 = the 200% charged time,
    // which is what the original `ceil(20 / attackSpeed) * 2` worked out to.
    private static final int ATTACK_CADENCE_MULTIPLIER = 2;
    // Combat Test knockback: the vertical part is capped at this, and applies in the air too
    private static final double CT_KNOCKBACK_VERTICAL_CAP = 0.4;

    // ---- combos
    private static final int COMBO_WINDOW_EXTRA_TICKS = 20; // keeps chasing this long past one swing cooldown
    private static final double COMBO_LEAD_TICKS = 2.0; // aim this far ahead of a launched target
    private static final double COMBO_JUMP_HEIGHT = 0.9; // jump after a target that's this far above us
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
    // Hop so the swing lands on the way down (the chance per attack comes from the difficulty)
    private static final double CRIT_HOP_RANGE = 2.5; // start hopping this far outside reach
    private static final int CRIT_HOP_LEAD_TICKS = 8; // hop when the swing is this close to ready
    private static final int CRIT_WAIT_MAX_TICKS = 20; // hard cap on holding a swing for a crit
    // Crit hops only happen when they actually raise damage output (see critHopWorthIt)
    private static final double CRIT_HOP_CYCLE_TICKS = 12.5; // one full hop: jump, fall, land
    private static final double CRIT_WINDOW_TICKS = 6.0; // the falling part of a hop, when crits are possible
    private static final double CRIT_HOP_MARGIN = 1.1; // hop only if it beats plain attacking by 10%

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

    // ---- pathfinding (A* over the terrain, used when the straight line is blocked)
    private static final int PATH_CHECK_TICKS = 10; // how often to test "is the straight line walkable?"
    private static final int PATH_REPLAN_TICKS = 60; // re-search a detour after this long
    private static final double PATH_GOAL_DRIFT = 4.0; // ...or when the target moved this far
    private static final int PATH_MAX_EXPANSIONS = 700; // search budget (scaled by difficulty)
    private static final int PATH_EXPANSIONS_PER_TICK = 30; // spread the search over several ticks
    private static final double PATH_MAX_RADIUS = 30.0; // never search further than this from the start
    private static final double PATH_HEURISTIC_WEIGHT = 1.25; // >1 trades optimality for speed
    private static final double PATH_WAYPOINT_REACH = 0.6;
    private static final int PATH_FAIL_COOLDOWN_TICKS = 40;
    private static final int PATH_SMOOTH_LOOKAHEAD = 6;
    private static final double PATH_GOAL_HORIZONTAL = 1.2;
    private static final double PATH_GOAL_VERTICAL = 1.6;
    private static final double ROUTE_SAMPLE_STEP = 0.5;
    private static final int ROUTE_MAX_SAMPLES = 48;
    private static final double[] ROUTE_WIDTH_SAMPLES = { -0.28, 0.0, 0.28 };
    private static final int[][] PATH_DIRS = {
        { 1, 0 },
        { -1, 0 },
        { 0, 1 },
        { 0, -1 },
        { 1, 1 },
        { 1, -1 },
        { -1, 1 },
        { -1, -1 },
    };

    /**
     * From easiest to hardest: EASY, MEDIUM, HARD, EXPERT, MASTER. MASTER is the
     * full-strength bot and the default; EXPERT is a notch below it. None of them lower
     * damage, speed or knockback. The lower the difficulty, the more openings it leaves:
     * it doesn't use its full reach (and gets less, or none, of the momentum reach
     * bonus; the charged-swing bonus is the same everywhere), reacts late before swinging, closes in further than it
     * needs to, whiffs swings, turns slowly, and loses more reach when it is knocked
     * back, so it is easier to combo and its own combos are rarer. Its bow / crossbow aim
     * wobbles, is released sloppily, is spread wider and leads moving targets less.
     * Movement is less smart too: it re-decides what to press less often, attempts
     * shorter gap jumps, hops for crits less, takes longer to get unstuck and searches
     * shorter detours around obstacles.
     */
    public enum Difficulty {
        EASY(
            new Tuning()
                .reachUsage(0.70)
                .momentum(0.0)
                .knockbackPenalty(1.5)
                .swingDelay(10)
                .approach(0.45, 0.65)
                .missChance(0.45)
                .turn(0.35)
                .arrowSpread(10.0)
                .lead(0.10)
                .backOff(0.30)
                .aimWobble(7.0)
                .release(16.0)
                .moveDelay(8)
                .gaps(0.30)
                .critHops(0.10)
                .unstick(3.0)
                .pathSearch(0.15)
                .combos(0.05)
        ),
        MEDIUM(
            new Tuning()
                .reachUsage(0.78)
                .momentum(0.3)
                .knockbackPenalty(1.25)
                .swingDelay(7)
                .approach(0.50, 0.70)
                .missChance(0.30)
                .turn(0.50)
                .arrowSpread(7.0)
                .lead(0.25)
                .backOff(0.40)
                .aimWobble(5.5)
                .release(13.0)
                .moveDelay(6)
                .gaps(0.40)
                .critHops(0.20)
                .unstick(2.5)
                .pathSearch(0.25)
                .combos(0.15)
        ),
        HARD(
            new Tuning()
                .reachUsage(0.86)
                .momentum(0.6)
                .knockbackPenalty(1.1)
                .swingDelay(4)
                .approach(0.55, 0.75)
                .missChance(0.18)
                .turn(0.70)
                .arrowSpread(4.5)
                .lead(0.50)
                .backOff(0.60)
                .aimWobble(4.0)
                .release(10.0)
                .moveDelay(4)
                .gaps(0.60)
                .critHops(0.35)
                .unstick(1.8)
                .pathSearch(0.45)
                .combos(0.40)
        ),
        // The old MEDIUM
        EXPERT(
            new Tuning()
                .approach(0.65, 0.80)
                .missChance(0.10)
                .turn(0.80)
                .arrowSpread(3.0)
                .lead(0.70)
                .backOff(0.75)
                .aimWobble(2.0)
                .release(8.0)
                .moveDelay(2)
                .gaps(0.75)
                .critHops(0.50)
                .unstick(1.4)
                .pathSearch(0.60)
                .combos(0.65)
        ),
        // The old HARD: every Tuning default
        MASTER(new Tuning());

        final double approachMin;
        final double approachMax;
        final double meleeMissChance;
        final double turnScale;
        final double rangedInaccuracy;
        final double leadScale;
        final double backOffScale;
        final double rangedAimWobble;
        final double rangedReleaseTolerance;
        final int movementDelayTicks;
        final double gapScale;
        final double critHopChance;
        final double stuckScale;
        final double pathScale;
        final double comboFollowUp;
        final double reachUsage;
        final double momentumBonusScale;
        final double knockbackPenaltyScale;
        final int swingDelayTicks;

        Difficulty(Tuning t) {
            this.approachMin = t.approachMin;
            this.approachMax = t.approachMax;
            this.meleeMissChance = t.meleeMissChance;
            this.turnScale = t.turnScale;
            this.rangedInaccuracy = t.rangedInaccuracy;
            this.leadScale = t.leadScale;
            this.backOffScale = t.backOffScale;
            this.rangedAimWobble = t.rangedAimWobble;
            this.rangedReleaseTolerance = t.rangedReleaseTolerance;
            this.movementDelayTicks = t.movementDelayTicks;
            this.gapScale = t.gapScale;
            this.critHopChance = t.critHopChance;
            this.stuckScale = t.stuckScale;
            this.pathScale = t.pathScale;
            this.comboFollowUp = t.comboFollowUp;
            this.reachUsage = t.reachUsage;
            this.momentumBonusScale = t.momentumBonusScale;
            this.knockbackPenaltyScale = t.knockbackPenaltyScale;
            this.swingDelayTicks = t.swingDelayTicks;
        }
    }

    /**
     * Settings for a Difficulty. Every default is the MASTER value, so each difficulty
     * only has to list what it changes.
     */
    private static final class Tuning {

        // how close it closes in before holding position (x reach)
        double approachMin = 0.85;
        double approachMax = 0.85;
        // chance a swing whiffs because its aim is off
        double meleeMissChance = 0.0;
        // how fast it turns toward what it's aiming at
        double turnScale = 1.0;
        // bow / crossbow: arrow spread, target leading, retreat distance, aim drift, release tolerance
        double rangedInaccuracy = 1.0;
        double leadScale = 1.0;
        double backOffScale = 1.0;
        double rangedAimWobble = 0.0;
        double rangedReleaseTolerance = 6.0;
        // movement smarts
        int movementDelayTicks = 1;
        double gapScale = 1.0;
        double critHopChance = 0.70;
        double stuckScale = 1.0;
        double pathScale = 1.0;
        double comboFollowUp = 1.0;
        // reach: fraction of its reach it will actually swing from, how much of the
        // momentum bonus it gets, and how hard knockback cuts it
        double reachUsage = 1.0;
        double momentumBonusScale = 1.0;
        double knockbackPenaltyScale = 1.0;
        // ticks the target must already be in reach before it swings (reaction time)
        int swingDelayTicks = 0;

        Tuning approach(double min, double max) {
            this.approachMin = min;
            this.approachMax = max;
            return this;
        }

        Tuning missChance(double v) {
            this.meleeMissChance = v;
            return this;
        }

        Tuning turn(double v) {
            this.turnScale = v;
            return this;
        }

        Tuning arrowSpread(double v) {
            this.rangedInaccuracy = v;
            return this;
        }

        Tuning lead(double v) {
            this.leadScale = v;
            return this;
        }

        Tuning backOff(double v) {
            this.backOffScale = v;
            return this;
        }

        Tuning aimWobble(double v) {
            this.rangedAimWobble = v;
            return this;
        }

        Tuning release(double v) {
            this.rangedReleaseTolerance = v;
            return this;
        }

        Tuning moveDelay(int v) {
            this.movementDelayTicks = v;
            return this;
        }

        Tuning gaps(double v) {
            this.gapScale = v;
            return this;
        }

        Tuning critHops(double v) {
            this.critHopChance = v;
            return this;
        }

        Tuning unstick(double v) {
            this.stuckScale = v;
            return this;
        }

        Tuning pathSearch(double v) {
            this.pathScale = v;
            return this;
        }

        Tuning combos(double v) {
            this.comboFollowUp = v;
            return this;
        }

        Tuning reachUsage(double v) {
            this.reachUsage = v;
            return this;
        }

        Tuning momentum(double v) {
            this.momentumBonusScale = v;
            return this;
        }

        Tuning knockbackPenalty(double v) {
            this.knockbackPenaltyScale = v;
            return this;
        }

        Tuning swingDelay(int v) {
            this.swingDelayTicks = v;
            return this;
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

    // combo state + actual per-tick movement (used for the velocity-dependent reach)
    private int comboTicks = 0;
    private int inReachTicks = 0;
    private int comboHits = 0;
    private boolean hasPrevPos = false;
    private double prevX, prevZ;
    private double dispX, dispZ;
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

    // movement decisions are held for a few ticks on lower difficulties
    private int decisionCooldown = 0;
    private double heldForward = 0;
    private double heldStrafe = 0;
    private boolean heldSprint = false;

    // pathfinding
    private List<Vector> path;
    private int pathIndex = 0;
    private int pathAge = 0;
    private Vector pathGoal;
    private boolean pathSearching = false;
    private int routeCheckCooldown = 0;
    private int pathFailCooldown = 0;
    private final PriorityQueue<PathNode> pathOpen = new PriorityQueue<>(
        Comparator.comparingDouble((PathNode n) -> n.f)
    );
    private final Map<Long, PathNode> pathBest = new HashMap<>();
    private final Set<Long> pathClosed = new HashSet<>();
    private PathNode pathStart;
    private PathNode pathClosest;
    private double pathClosestDistance;
    private int pathExpansions;
    private Vector pathSearchGoal;
    private double pathStartX, pathStartZ;
    // block collision shapes are cached only while a search is running
    private final Map<Long, Collection<BoundingBox>> shapeCache =
        new HashMap<>();
    private boolean cacheShapes = false;

    /** Full-strength bot (MASTER). */
    public MannequinFighter(Plugin plugin, Mannequin mannequin) {
        this(plugin, mannequin, Difficulty.MASTER);
    }

    public MannequinFighter(
        Plugin plugin,
        Mannequin mannequin,
        Difficulty difficulty
    ) {
        this.plugin = plugin;
        this.mannequin = mannequin;
        this.difficulty = difficulty == null ? Difficulty.MASTER : difficulty;
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
                if (comboTicks > 0 && --comboTicks == 0) comboHits = 0;
                updateMotionTracking();
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
                    path = null;
                    pathSearching = false;
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
        // Reach also follows our own velocity: running into someone extends it, and
        // being knocked back shrinks it, so combos are hard to swing out of
        // Lower difficulties don't use their full reach, get less (or none) of the
        // momentum bonus, and lose more reach when knocked back. The charged-swing
        // bonus is the same CHARGED_REACH_BONUS on every difficulty.
        double attackReach = Math.max(
            0.0,
            reach * difficulty.reachUsage +
                (hasChargedReach() ? CHARGED_REACH_BONUS : 0.0) +
                velocityReachBonus(target)
        );
        double distance = distanceToBoundingBox(target);

        targetVisible = mannequin.hasLineOfSight(target);
        updateCombatMode(distance);
        trackTargetVelocity(target);

        Vector aimPoint = target.getBoundingBox().getCenter();
        // Mid-combo the target is flying away: aim where it's going, not where it was
        if (comboTicks > 0 && rangedMode == RangedWeapon.NONE) {
            aimPoint = aimPoint
                .clone()
                .add(targetVel.clone().multiply(COMBO_LEAD_TICKS));
        }
        if (rangedMode != RangedWeapon.NONE) {
            Vector solved = rangedAimPoint(target);
            aimSolved = solved != null;
            if (solved != null) aimPoint = solved;
        } else {
            drawTicks = 0;
            shotCooldown = 0;
        }

        // Walking a detour around an obstacle: look where we're going so we can sprint it
        if (!targetVisible) {
            Vector look = pathLookPoint();
            if (look != null) aimPoint = look;
        }

        double aimError = lookAt(aimPoint);
        steer(target, reach, distance);

        // Bow / crossbow: shoot instead of meleeing
        if (rangedMode != RangedWeapon.NONE) {
            tickRanged(distance, aimError);
            return;
        }

        // Reaction time: lower difficulties don't swing the instant a target is in reach
        if (distance <= attackReach) inReachTicks++;
        else inReachTicks = 0;

        boolean ready =
            distance <= attackReach &&
            (comboTicks > 0 || inReachTicks > difficulty.swingDelayTicks) &&
            aimError <= MAX_AIM_ERROR &&
            attackCooldownTicks <= 0 &&
            !hasDamageImmunity(target) &&
            targetVisible;

        // Hopping for a crit: hold the swing until we're actually falling
        if (
            ready &&
            comboTicks <= 0 &&
            hopForCrit &&
            !isCriticalHit() &&
            ticksSinceFluid >= CRIT_FLUID_GRACE_TICKS &&
            critWaitTicks < critWaitCap()
        ) {
            critWaitTicks++;
            ready = false;
        }

        if (ready) {
            boolean missed = rollsMiss(aimError);
            if (missed) {
                // Whiffed: the swing animation plays, but no damage or knockback is dealt
                mannequin.swingMainHand();
            } else {
                performAttack(target);
                startCombo();
            }
            // A miss costs 4 extra ticks on top of the normal attack cooldown
            attackCooldownTicks =
                getAttackCooldownTicks() +
                (missed ? ATTACK_MISS_PENALTY_TICKS : 0);
            critWaitTicks = 0;
            ticksSinceAttack = 0;
            hopForCrit =
                critHopWorthIt() &&
                ThreadLocalRandom.current().nextDouble() <
                    difficulty.critHopChance;
            approachFactor = rollApproachFactor();
        }
    }

    /** Picks how close it will close in this time. MASTER is always the same tight spacing. */
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

        // Like crits: no sprint knockback in water/lava or right after leaving it
        boolean sprintHit = sprinting && !isInOrJustOutOfFluid();
        // Custom rules: unlike vanilla, sprinting does NOT cancel a critical hit
        boolean crit = isCriticalHit();

        DamageSource source = DamageSource.builder(DamageType.MOB_ATTACK)
            .withCausingEntity(mannequin)
            .withDirectEntity(mannequin)
            .build();

        Vector velBefore = victim.getVelocity();
        boolean victimAirborne = !victim.isOnGround();
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
            world.spawnParticle(
                Particle.CRIT,
                hitPoint,
                15,
                0.3,
                0.3,
                0.3,
                0.2
            );
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
        if (level <= 0) {
            applyMidairLift(victim, victimAirborne, velBefore.getY(), 0.0);
            return;
        }

        if (sprintHit) {
            world.playSound(from, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f);
        }

        double yaw = Math.toRadians(from.getYaw());
        victim.knockback(
            level * KNOCKBACK_PER_LEVEL,
            Math.sin(yaw),
            -Math.cos(yaw)
        );
        applyMidairLift(
            victim,
            victimAirborne,
            velBefore.getY(),
            level * KNOCKBACK_PER_LEVEL
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
            !isInOrJustOutOfFluid() &&
            !mannequin.isInsideVehicle() &&
            !mannequin.hasPotionEffect(PotionEffectType.BLINDNESS)
        );
    }

    /** In water/lava, or within CRIT_FLUID_GRACE_TICKS of leaving it. Blocks crits and sprint knockback. */
    private boolean isInOrJustOutOfFluid() {
        return (
            mannequin.isInWater() ||
            mannequin.isInLava() ||
            ticksSinceFluid < CRIT_FLUID_GRACE_TICKS
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

    /** Tracks how far we actually moved last tick (teleports are ignored). */
    private void updateMotionTracking() {
        Location loc = mannequin.getLocation();
        if (hasPrevPos) {
            double dx = loc.getX() - prevX;
            double dz = loc.getZ() - prevZ;
            if (Math.hypot(dx, dz) < 3.0) {
                dispX = dx;
                dispZ = dz;
            } else {
                dispX = 0;
                dispZ = 0;
            }
        }
        prevX = loc.getX();
        prevZ = loc.getZ();
        hasPrevPos = true;
    }

    /**
     * Reach follows our own movement along the line to the target, the way a real
     * player's effectively does: effective reach = reach + closing speed * ~4 ticks of
     * latency. Not capped:
     *  - sprinting at someone extends it (rewarding running in to start a combo, and
     *    keeping a chase going once the target is launched),
     *  - being knocked back shrinks it by the same formula, so while the knockback is
     *    carrying us away we can't swing back (which is what makes us combo-able).
     * Actual movement is used because the entity's velocity is measured after friction
     * and understates a sprint by ~45%; velocity is only used to see knockback early.
     */
    private double velocityReachBonus(LivingEntity target) {
        Location from = mannequin.getLocation();
        Location to = target.getLocation();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double len = Math.hypot(dx, dz);
        if (len < 1.0E-4) return 0;
        double ux = dx / len;
        double uz = dz / len;

        double alongMoved = dispX * ux + dispZ * uz; // blocks actually moved toward the target last tick
        Vector vel = mannequin.getVelocity();
        double alongVel = vel.getX() * ux + vel.getZ() * uz; // about to move; knockback shows up here first
        double along =
            alongVel < 0 ? Math.min(alongMoved, alongVel) : alongMoved;

        // Lower difficulties get less (or none) of the momentum bonus, and lose more
        // reach when they're the one being knocked back
        double scale =
            along >= 0
                ? difficulty.momentumBonusScale
                : difficulty.knockbackPenaltyScale;
        return along * VELOCITY_REACH_TICKS * scale;
    }

    /** A landed hit starts (or extends) a combo window, if the difficulty "sees" the follow-up. */
    private void startCombo() {
        if (comboTicks > 0) comboHits++;
        else comboHits = 1;

        comboTicks =
            ThreadLocalRandom.current().nextDouble() < difficulty.comboFollowUp
                ? getAttackCooldownTicks() + COMBO_WINDOW_EXTRA_TICKS
                : 0;
    }

    /** Mid-combo the victim is launched upward: jump after it so the follow-up still connects. */
    private boolean shouldComboJump(
        LivingEntity target,
        double distance,
        double reach
    ) {
        return (
            comboTicks > 0 &&
            rangedMode == RangedWeapon.NONE &&
            target.getLocation().getY() >
                mannequin.getLocation().getY() + COMBO_JUMP_HEIGHT &&
            distance <= reach + 2.0
        );
    }

    /**
     * Combat Test knockback: unlike vanilla, a victim that's in the air is still pushed
     * UP. Vanilla only adds the vertical part when the victim is on the ground
     * (y = min(0.4, y / 2 + strength)); this applies that same formula to airborne
     * victims too, once for the base hit and once more for any extra knockback.
     */
    private void applyMidairLift(
        LivingEntity victim,
        boolean wasAirborne,
        double velYBefore,
        double extraStrength
    ) {
        if (!wasAirborne) return;

        AttributeInstance resistAttr = victim.getAttribute(
            Attribute.KNOCKBACK_RESISTANCE
        );
        double resist =
            1.0 - (resistAttr == null ? 0.0 : resistAttr.getValue());
        resist = Math.max(0.0, Math.min(1.0, resist));
        if (resist <= 0) return;

        double y = Math.min(
            CT_KNOCKBACK_VERTICAL_CAP,
            velYBefore / 2.0 + BASE_KNOCKBACK * resist
        );
        if (extraStrength > 0) {
            y = Math.min(
                CT_KNOCKBACK_VERTICAL_CAP,
                y / 2.0 + extraStrength * resist
            );
        }

        Vector v = victim.getVelocity();
        v.setY(y);
        victim.setVelocity(v);
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

    /**
     * Whether hopping for crits actually raises damage output with the current weapon
     * and attack speed. A hop takes about CRIT_HOP_CYCLE_TICKS and offers a few crit
     * swings during its falling window; plain attacking lands one swing per cooldown.
     * When attacks are fast (cooldown shorter than the hop) plain attacking wins, so it
     * simply swings whenever the cooldown is ready and the target is in reach instead
     * of waiting to fall for a crit.
     */
    private boolean critHopWorthIt() {
        double cooldown = Math.max(1, getAttackCooldownTicks());

        double plainDps = calculateDamage(false) / cooldown;

        double swingsPerHop =
            cooldown >= CRIT_HOP_CYCLE_TICKS
                ? 1
                : 1 + Math.floor(CRIT_WINDOW_TICKS / cooldown);
        double hopDps =
            (swingsPerHop * calculateDamage(true)) /
            Math.max(cooldown, CRIT_HOP_CYCLE_TICKS);

        return hopDps > plainDps * CRIT_HOP_MARGIN;
    }

    /** Never hold a swing for a crit longer than half a cooldown (and never past the hard cap). */
    private int critWaitCap() {
        return Math.max(
            2,
            Math.min(CRIT_WAIT_MAX_TICKS, getAttackCooldownTicks() / 2)
        );
    }

    /** Whether to start a hop now so the next swing lands on the way down. */
    private boolean shouldCritHop(double distance, double reach) {
        return (
            rangedMode == RangedWeapon.NONE &&
            comboTicks <= 0 &&
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

    /**
     * Ticks until a fully charged swing is available, using vanilla / Combat Test timing:
     * the charge takes T = 20 / attackSpeed ticks and a swing is allowed once
     * (ticks + 0.5) / T >= 1, i.e. ceil(T - 0.5) ticks after the last one.
     */
    private int getFullChargeTicks() {
        double attackSpeed = getAttributeValue(
            Attribute.ATTACK_SPEED,
            DEFAULT_ATTACK_SPEED
        );
        if (attackSpeed <= 0) return 10;
        return Math.max(1, (int) Math.ceil(20.0 / attackSpeed - 0.5));
    }

    /**
     * How often the AI swings: the 200% "charged" time (ATTACK_CADENCE_MULTIPLIER full
     * charges). That's what the old `ceil(20 / attackSpeed) * 2` effectively was, and
     * this lands within a tick or two of it.
     */
    private int getAttackCooldownTicks() {
        return getFullChargeTicks() * ATTACK_CADENCE_MULTIPLIER;
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
            boolean shooting =
                rangedMode != RangedWeapon.NONE
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
        Vector look = applyInaccuracy(
            mannequin.getEyeLocation().getDirection()
        );
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

    private void launchArrow(
        RangedWeapon weapon,
        ItemStack held,
        Vector velocity
    ) {
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

        double yaw =
            Math.atan2(-to.getX(), to.getZ()) + Math.toRadians(wobbleYaw);
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
    private double arrowHeightAt(
        double horizontal,
        double pitch,
        double speed
    ) {
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

        // Mid-combo: stay on the target, no spacing games
        boolean combo = comboTicks > 0 && rangedMode == RangedWeapon.NONE;

        boolean approachWanted =
            combo ||
            (rangedMode != RangedWeapon.NONE
                ? !targetVisible || distance > RANGED_APPROACH_DISTANCE
                : distance > reach * approachFactor);
        updateNavigation(target, approachWanted);

        double forward = 0;
        double strafe = 0;
        boolean sprint = false;

        // Lower difficulties only re-decide what to press every few ticks
        boolean redecide = combo || decisionCooldown <= 0;
        if (redecide) decisionCooldown = difficulty.movementDelayTicks;
        decisionCooldown--;

        boolean sidestepping = sidestepTicks > 0;
        if (sidestepping) {
            sidestepTicks--;
            forward = 0.3;
            strafe = sidestepDir;
            decisionCooldown = 0;
        } else if (!redecide) {
            forward = heldForward;
            strafe = heldStrafe;
            sprint = heldSprint;
        } else if (combo) {
            // Chase the launched target flat out, sprinting (it re-sprints after each hit's w-tap)
            forward = distance < reach * BACK_OFF_FACTOR ? 0 : 1;
            sprint = forward > 0;
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

        if (redecide && !sidestepping) {
            heldForward = forward;
            heldStrafe = strafe;
            heldSprint = sprint;
        }

        // Closing in but the straight line is blocked: follow the A* detour instead
        if (!sidestepping && forward > 0) {
            double[] along = pathInput();
            if (along != null) {
                forward = along[0];
                strafe = along[1];
                sprint = along[0] > 0.5;
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
                    (shouldCritHop(distance, reach) ||
                        shouldComboJump(target, distance, reach))
                ) {
                    jump = true; // hop for a crit, or to follow a launched target
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

        if (stuckTicks >= STUCK_TICKS * difficulty.stuckScale) {
            stuckTicks = 0;
            // Look for a real route around whatever we're pushing into, and wiggle
            // sideways for a moment to come off the corner while it's computed
            requestReplan();
            sidestepTicks = SIDESTEP_TICKS / 3;
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

        for (
            double dist = 0.5;
            dist <= MAX_GAP_SCAN * difficulty.gapScale;
            dist += 0.25
        ) {
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
            for (BoundingBox bb : collisionBoxes(world, bx, by, bz)) {
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
            for (BoundingBox bb : collisionBoxes(world, bx, by, bz)) {
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

    // ------------------------------------------------------------------ pathfinding

    private static final class PathNode {

        final int x;
        final int y;
        final int z;
        final double feetY;
        double g;
        double f;
        PathNode parent;

        PathNode(int x, int y, int z, double feetY) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.feetY = feetY;
        }
    }

    private static long packKey(int x, int y, int z) {
        return (
            ((long) (x & 0x3FFFFFF) << 38) |
            ((long) (z & 0x3FFFFFF) << 12) |
            (long) (y & 0xFFF)
        );
    }

    private Collection<BoundingBox> collisionBoxes(
        World world,
        int bx,
        int by,
        int bz
    ) {
        if (!cacheShapes) {
            return world
                .getBlockAt(bx, by, bz)
                .getCollisionShape()
                .getBoundingBoxes();
        }
        return shapeCache.computeIfAbsent(packKey(bx, by, bz), k ->
            world.getBlockAt(bx, by, bz).getCollisionShape().getBoundingBoxes()
        );
    }

    /**
     * Keeps a detour available whenever we want to close in but can't just walk at
     * the target. Every few ticks it tests the straight line; if that's blocked it
     * starts an A* search (spread over several ticks) and the result is followed by
     * pathInput(). If the line is clear again the detour is dropped.
     */
    private void updateNavigation(LivingEntity target, boolean approachWanted) {
        pathAge++;
        if (routeCheckCooldown > 0) routeCheckCooldown--;
        if (pathFailCooldown > 0) pathFailCooldown--;

        if (pathSearching) stepPathSearch();

        if (!approachWanted) {
            path = null;
            pathSearching = false;
            return;
        }

        if (routeCheckCooldown > 0) return;
        routeCheckCooldown = PATH_CHECK_TICKS;

        Vector me = mannequin.getLocation().toVector();
        Vector goal = target.getLocation().toVector();

        if (isRouteWalkable(me, goal)) {
            path = null; // the straight line works
            pathSearching = false;
            return;
        }

        boolean stale =
            path == null ||
            pathAge > PATH_REPLAN_TICKS ||
            (pathGoal != null &&
                pathGoal.distanceSquared(goal) >
                    PATH_GOAL_DRIFT * PATH_GOAL_DRIFT);
        if (stale && !pathSearching && pathFailCooldown <= 0) {
            startPathSearch(goal);
        }
    }

    /** Throws away the current detour so a fresh one is searched at the next check. */
    private void requestReplan() {
        path = null;
        pathSearching = false;
        routeCheckCooldown = 0;
        pathFailCooldown = 0;
    }

    /** The (forward, strafe) keys toward the next waypoint, or null when not on a detour. */
    private double[] pathInput() {
        if (path == null) return null;
        Location loc = mannequin.getLocation();

        while (pathIndex < path.size()) {
            Vector wp = path.get(pathIndex);
            double dx = wp.getX() - loc.getX();
            double dz = wp.getZ() - loc.getZ();
            boolean closeEnough =
                Math.hypot(dx, dz) < PATH_WAYPOINT_REACH &&
                Math.abs(wp.getY() - loc.getY()) < 1.3;
            if (!closeEnough) break;
            pathIndex++;
        }
        if (pathIndex >= path.size()) {
            path = null;
            return null;
        }

        Vector wp = path.get(pathIndex);
        Vector dir = new Vector(
            wp.getX() - loc.getX(),
            0,
            wp.getZ() - loc.getZ()
        );
        if (dir.lengthSquared() < 1.0E-6) return null;
        return inputToward(dir.normalize());
    }

    /** Where to look while following a detour (the next waypoint at eye height), or null. */
    private Vector pathLookPoint() {
        if (path == null || pathIndex >= path.size()) return null;
        Vector wp = path.get(pathIndex);
        return new Vector(
            wp.getX(),
            mannequin.getEyeLocation().getY(),
            wp.getZ()
        );
    }

    private void startPathSearch(Vector goal) {
        Location loc = mannequin.getLocation();

        pathOpen.clear();
        pathBest.clear();
        pathClosed.clear();
        shapeCache.clear();

        pathSearchGoal = goal.clone();
        pathExpansions = 0;
        pathStartX = loc.getX();
        pathStartZ = loc.getZ();

        int sx = (int) Math.floor(loc.getX());
        int sz = (int) Math.floor(loc.getZ());
        int sy = (int) Math.floor(loc.getY() + EPS);
        pathStart = new PathNode(sx, sy, sz, loc.getY());
        pathStart.g = 0;
        pathStart.f = PATH_HEURISTIC_WEIGHT * goalDistance(pathStart);

        pathOpen.add(pathStart);
        pathBest.put(packKey(sx, sy, sz), pathStart);
        pathClosest = pathStart;
        pathClosestDistance = goalDistance(pathStart);
        pathSearching = true;
    }

    private void stepPathSearch() {
        cacheShapes = true;
        try {
            int maxExpansions = (int) (PATH_MAX_EXPANSIONS *
                difficulty.pathScale);
            for (int budget = PATH_EXPANSIONS_PER_TICK; budget > 0; budget--) {
                PathNode node = pathOpen.poll();
                if (node == null || pathExpansions >= maxExpansions) {
                    finishPathSearch(pathClosest);
                    return;
                }
                if (!pathClosed.add(packKey(node.x, node.y, node.z))) continue;
                pathExpansions++;

                double toGoal = goalDistance(node);
                if (toGoal < pathClosestDistance) {
                    pathClosestDistance = toGoal;
                    pathClosest = node;
                }
                if (isGoalNode(node)) {
                    finishPathSearch(node);
                    return;
                }
                expandPathNode(node);
            }
        } finally {
            cacheShapes = false;
        }
    }

    private void finishPathSearch(PathNode end) {
        pathSearching = false;

        List<Vector> points = new ArrayList<>();
        for (PathNode n = end; n != null; n = n.parent) {
            points.add(new Vector(n.x + 0.5, n.feetY, n.z + 0.5));
        }
        Collections.reverse(points);

        // The first point is where we already are. A search that never got anywhere
        // means no usable detour: fall back to the old behaviour for a moment.
        if (end == pathStart || points.size() <= 1) {
            path = null;
            pathFailCooldown = PATH_FAIL_COOLDOWN_TICKS;
            return;
        }
        points.remove(0);

        // Smarter difficulties cut corners along the path instead of tracing every cell
        if (difficulty.pathScale >= 0.6) points = smoothPath(points);

        path = points;
        pathIndex = 0;
        pathAge = 0;
        pathGoal = pathSearchGoal;
    }

    /** Greedy string-pulling: skip ahead to the furthest waypoint we can walk to in a straight line. */
    private List<Vector> smoothPath(List<Vector> points) {
        List<Vector> out = new ArrayList<>();
        Vector current = mannequin.getLocation().toVector();
        int i = 0;
        while (i < points.size()) {
            int farthest = i;
            int limit = Math.min(points.size() - 1, i + PATH_SMOOTH_LOOKAHEAD);
            for (int j = limit; j > i; j--) {
                if (isRouteWalkable(current, points.get(j))) {
                    farthest = j;
                    break;
                }
            }
            out.add(points.get(farthest));
            current = points.get(farthest);
            i = farthest + 1;
        }
        return out;
    }

    private double goalDistance(PathNode n) {
        double dx = Math.abs(pathSearchGoal.getX() - (n.x + 0.5));
        double dz = Math.abs(pathSearchGoal.getZ() - (n.z + 0.5));
        double diagonal = Math.min(dx, dz);
        double straight = Math.max(dx, dz) - diagonal;
        return (
            straight +
            1.414 * diagonal +
            Math.abs(pathSearchGoal.getY() - n.feetY) * 0.5
        );
    }

    private boolean isGoalNode(PathNode n) {
        return (
            Math.hypot(
                n.x + 0.5 - pathSearchGoal.getX(),
                n.z + 0.5 - pathSearchGoal.getZ()
            ) <= PATH_GOAL_HORIZONTAL &&
            Math.abs(n.feetY - pathSearchGoal.getY()) <= PATH_GOAL_VERTICAL
        );
    }

    private void expandPathNode(PathNode n) {
        double height = mannequin.getHeight();
        double step = getAttributeValue(
            Attribute.STEP_HEIGHT,
            DEFAULT_STEP_HEIGHT
        );
        double jumpHeight = getMaxJumpHeight();

        for (int[] d : PATH_DIRS) {
            int nx = n.x + d[0];
            int nz = n.z + d[1];
            boolean diagonal = d[0] != 0 && d[1] != 0;

            if (
                Math.hypot(nx + 0.5 - pathStartX, nz + 0.5 - pathStartZ) >
                PATH_MAX_RADIUS
            ) continue;

            // no cutting corners: both orthogonal neighbours must be free of anything
            // taller than a step
            if (diagonal) {
                if (
                    solidAt(
                        n.x + d[0] + 0.5,
                        n.z + 0.5,
                        n.feetY + step + EPS,
                        n.feetY + height - EPS
                    )
                ) continue;
                if (
                    solidAt(
                        n.x + 0.5,
                        n.z + d[1] + 0.5,
                        n.feetY + step + EPS,
                        n.feetY + height - EPS
                    )
                ) continue;
            }

            double floor = columnFloor(
                nx,
                nz,
                n.feetY,
                jumpHeight,
                SAFE_DROP,
                height
            );
            if (Double.isNaN(floor)) continue;
            double rise = floor - n.feetY;

            double cost = diagonal ? 1.414 : 1.0;
            if (rise > step + EPS) {
                // a jump: not diagonally, and we need headroom to rise
                if (diagonal) continue;
                if (
                    solidAt(
                        n.x + 0.5,
                        n.z + 0.5,
                        n.feetY + height,
                        n.feetY + height + rise + EPS
                    )
                ) continue;
                cost += 1.0;
            } else {
                // walking or dropping: nothing tall at our current level in the next cell
                if (
                    solidAt(
                        nx + 0.5,
                        nz + 0.5,
                        n.feetY + step + EPS,
                        n.feetY + height - EPS
                    )
                ) continue;
                if (rise < -1.0 - EPS) cost += -rise * 0.5; // prefer not to drop
            }

            double g = n.g + cost;
            int ny = (int) Math.floor(floor + EPS);
            long key = packKey(nx, ny, nz);
            if (pathClosed.contains(key)) continue;
            PathNode existing = pathBest.get(key);
            if (existing != null && existing.g <= g) continue;

            PathNode next = new PathNode(nx, ny, nz, floor);
            next.g = g;
            next.f = g + PATH_HEURISTIC_WEIGHT * goalDistance(next);
            next.parent = n;
            pathBest.put(key, next);
            pathOpen.add(next);
        }
    }

    /**
     * The height we'd stand at in block column (bx, bz) when arriving from fromY, or NaN
     * if it can't be stood in (no floor within reach, no room for our body, or hazardous).
     */
    private double columnFloor(
        int bx,
        int bz,
        double fromY,
        double up,
        double down,
        double height
    ) {
        double cx = bx + 0.5;
        double cz = bz + 0.5;
        double maxY = fromY + up + EPS;
        for (int attempt = 0; attempt < 3; attempt++) {
            double floor = standY(cx, cz, maxY, fromY - down);
            if (Double.isNaN(floor)) return Double.NaN;
            if (
                !solidAt(cx, cz, floor + EPS, floor + height - EPS) &&
                !isHazard(bx, bz, floor)
            ) return floor;
            maxY = floor - 0.05; // blocked here (low ceiling?), try the next surface down
        }
        return Double.NaN;
    }

    private boolean isHazard(int bx, int bz, double floor) {
        World world = mannequin.getWorld();
        Material feet = world
            .getBlockAt(bx, (int) Math.floor(floor + EPS), bz)
            .getType();
        Material support = world
            .getBlockAt(bx, (int) Math.floor(floor - EPS), bz)
            .getType();

        boolean badFeet = switch (feet) {
            case
                LAVA,
                WATER,
                FIRE,
                SOUL_FIRE,
                COBWEB,
                SWEET_BERRY_BUSH,
                POWDER_SNOW,
                CAMPFIRE,
                SOUL_CAMPFIRE,
                WITHER_ROSE -> true;
            default -> false;
        };
        if (badFeet) return true;

        return (
            support == Material.MAGMA_BLOCK ||
            support == Material.CACTUS ||
            support == Material.CAMPFIRE ||
            support == Material.SOUL_CAMPFIRE
        );
    }

    /**
     * Whether we can walk (or step / jump / drop along) the straight line between two
     * points: there's always a floor within reach and our whole width fits.
     */
    private boolean isRouteWalkable(Vector from, Vector to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist < 0.5) return true;
        double ux = dx / dist;
        double uz = dz / dist;

        double height = mannequin.getHeight();
        double jumpHeight = getMaxJumpHeight();
        double feetY = from.getY();
        int steps = (int) Math.min(
            ROUTE_MAX_SAMPLES,
            Math.ceil(dist / ROUTE_SAMPLE_STEP)
        );

        for (int i = 1; i <= steps; i++) {
            double d = Math.min(i * ROUTE_SAMPLE_STEP, dist);
            double x = from.getX() + ux * d;
            double z = from.getZ() + uz * d;

            double floor = standY(
                x,
                z,
                feetY + jumpHeight + EPS,
                feetY - SAFE_DROP
            );
            if (Double.isNaN(floor)) return false;

            for (double lateral : ROUTE_WIDTH_SAMPLES) {
                double px = x - uz * lateral;
                double pz = z + ux * lateral;
                if (solidAt(px, pz, floor + EPS, floor + height - EPS)) {
                    return false;
                }
            }
            feetY = floor;
        }
        return true;
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
