package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class Monk extends ManaKit {

    final int maxMoves = 3; // 3 moves held at a time
    final int flowRestock = 200; // flow needed for the next random move
    final int flowPerTick = 1; // baseline trickle, 10s per move standing still
    final double flowStep = 1.5; // blocks of travel that count as one step
    final double flowStepSq = flowStep * flowStep;
    final int flowPerStep = 8; // each step moved, running nonstop is ~3.5s per move
    final int flowPerHit = 30; // each hit landed (fists or moves), 15% of a move
    final NamespacedKey flowCounter = ManaKitListener.MANA_KIT_DATA0;
    final NamespacedKey lastLocXKey = ManaKitListener.MANA_KIT_DATADBL0;
    final NamespacedKey lastLocYKey = ManaKitListener.MANA_KIT_DATADBL1;
    final NamespacedKey lastLocZKey = ManaKitListener.MANA_KIT_DATADBL2;

    private final double SHADOW_STEP_RANGE = 7;
    private final PotionEffectType[] CLEANSE = {
        PotionEffectType.SLOWNESS,
        PotionEffectType.WEAKNESS,
        PotionEffectType.MINING_FATIGUE,
        PotionEffectType.POISON,
        PotionEffectType.WITHER,
        PotionEffectType.BLINDNESS,
        PotionEffectType.NAUSEA,
        PotionEffectType.DARKNESS,
        PotionEffectType.JUMP_BOOST,
    };

    private enum Move {
        // movement
        FLYING_KICK(
            Material.FEATHER,
            "Flying Kick",
            NamedTextColor.AQUA,
            "[Movement] Dash forward, kicking every enemy in your path.",
            "None"
        ),
        SHADOW_STEP(
            Material.PHANTOM_MEMBRANE,
            "Shadow Step",
            NamedTextColor.DARK_PURPLE,
            "[Movement] Blink forward up to 7 blocks and brace for a moment.",
            "At least 2 blocks of open space in front of you"
        ),
        CRANE_LEAP(
            Material.RABBIT_FOOT,
            "Crane Leap",
            NamedTextColor.WHITE,
            "[Movement] Leap high in your facing direction and glide down, kicking every enemy you pass through.",
            "None"
        ),
        // offense
        RISING_DRAGON(
            Material.BLAZE_POWDER,
            "Rising Dragon",
            NamedTextColor.GOLD,
            "[Offense] Uppercut every nearby enemy into the air.",
            "An enemy within 3.5 blocks"
        ),
        WHIRLWIND_KICK(
            Material.PRISMARINE_SHARD,
            "Whirlwind Kick",
            NamedTextColor.DARK_AQUA,
            "[Offense] Spin and sweep every nearby enemy away.",
            "An enemy within 3.5 blocks"
        ),
        PALM_STRIKE(
            Material.AMETHYST_SHARD,
            "Palm Strike",
            NamedTextColor.LIGHT_PURPLE,
            "[Offense] A heavy palm that launches the enemy you're facing.",
            "An enemy you're facing within 5 blocks"
        ),
        IRON_FIST(
            Material.IRON_NUGGET,
            "Iron Fist",
            NamedTextColor.RED,
            "[Offense] Your fists hit far harder for a few seconds.",
            "None"
        ),
        // control
        PRESSURE_POINT(
            Material.FLINT,
            "Pressure Point",
            NamedTextColor.YELLOW,
            "[Control] Strike a nerve: weakens and slows the enemy you're facing.",
            "An enemy you're facing within 4.5 blocks"
        ),
        // defense
        STONE_GUARD(
            Material.NAUTILUS_SHELL,
            "Stone Guard",
            NamedTextColor.GRAY,
            "[Defense] Harden your body, taking far less damage.",
            "None"
        ),
        INNER_PEACE(
            Material.GHAST_TEAR,
            "Inner Peace",
            NamedTextColor.GREEN,
            "[Defense] Cleanse debuffs and regenerate.",
            "None"
        );

        final Material material;
        final String displayName;
        final NamedTextColor color;
        final String description;
        final String requirement;

        Move(
            Material material,
            String displayName,
            NamedTextColor color,
            String description,
            String requirement
        ) {
            this.material = material;
            this.displayName = displayName;
            this.color = color;
            this.description = description;
            this.requirement = requirement;
        }

        static Move fromMaterial(Material material) {
            for (Move move : values())
                if (move.material == material) return move;
            return null;
        }
    }

    private final EnumMap<Move, ItemStack> moveItems = new EnumMap<>(
        Move.class
    );

    public Monk() {
        super("monk", "Monk", "[Melee Movement]", 0);

        for (Move move : Move.values()) {
            ItemStack item = new ItemStack(move.material);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(
                Component.text(move.displayName)
                    .color(move.color)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false)
            );
            meta.lore(
                List.of(
                    Component.text(move.description)
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                    Component.text("Requires: ")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(
                            Component.text(
                                move.requirement,
                                NamedTextColor.WHITE
                            )
                        )
                )
            );
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            item.setItemMeta(meta);
            moveItems.put(move, item);
        }
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -185,
            13,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        resetFlow(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        resetFlow(p);

        clearMoves(p);
        for (int i = 0; i < maxMoves; i++) giveRandomMove(p, heldMoves(p));
    }

    @Override
    public void onLeaveCombat(Player p) {
        clearMoves(p);
        p.clearActivePotionEffects();
        resetKit(p);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int flow = pdc.get(flowCounter, PersistentDataType.INTEGER);
        Set<Move> held = heldMoves(p);

        p.addPotionEffect(
            new PotionEffect(PotionEffectType.SATURATION, 100, 0, false, false, true)
        );
        // passive mobility, no weapons so you live off your moves
        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 0, false, false, true)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(
                PotionEffectType.JUMP_BOOST,
                100,
                0,
                false,
                false,
                true
            )
        );
        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.STRENGTH, 100, 0)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.RESISTANCE, 100, 0)
        );

        // movement, same idea as the slime king meter: every time you get
        // flowStep blocks away from the last counted spot, that's a step
        Location currentLoc = p.getLocation();
        double lastX = pdc.getOrDefault(
            lastLocXKey,
            PersistentDataType.DOUBLE,
            currentLoc.getX()
        );
        double lastY = pdc.getOrDefault(
            lastLocYKey,
            PersistentDataType.DOUBLE,
            currentLoc.getY()
        );
        double lastZ = pdc.getOrDefault(
            lastLocZKey,
            PersistentDataType.DOUBLE,
            currentLoc.getZ()
        );
        double distSq =
            Math.pow(currentLoc.getX() - lastX, 2) +
            Math.pow(currentLoc.getY() - lastY, 2) +
            Math.pow(currentLoc.getZ() - lastZ, 2);
        boolean stepped = distSq >= flowStepSq;
        if (stepped) {
            pdc.set(lastLocXKey, PersistentDataType.DOUBLE, currentLoc.getX());
            pdc.set(lastLocYKey, PersistentDataType.DOUBLE, currentLoc.getY());
            pdc.set(lastLocZKey, PersistentDataType.DOUBLE, currentLoc.getZ());
        }

        int moves = held.size();
        if (moves < maxMoves) {
            flow += flowPerTick;
            if (stepped) flow += flowPerStep;
            if (flow >= flowRestock) {
                giveRandomMove(p, held);
                moves++;
                flow -= flowRestock; // overflow carries into the next move
            }
        } else {
            flow = 0;
        }
        pdc.set(flowCounter, PersistentDataType.INTEGER, flow);

        p.sendActionBar(
            Component.text()
                .append(Component.text("Moves: ", NamedTextColor.GOLD))
                .append(
                    Component.text(moves + "/" + maxMoves, NamedTextColor.WHITE)
                )
                .append(Component.text("  |  ", NamedTextColor.GRAY))
                .append(Component.text("Flow: ", NamedTextColor.AQUA))
                .append(
                    Component.text(
                        moves >= maxMoves
                            ? "Full"
                            : (flow * 100) / flowRestock + "%",
                        NamedTextColor.WHITE
                    )
                )
                .append(Component.text("  |  ", NamedTextColor.GRAY))
                .append(Component.text("Killstreak: ", NamedTextColor.RED))
                .append(Component.text(killstreak + "/2", NamedTextColor.WHITE))
                .build()
        );
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (heldMoves(p).size() >= maxMoves) return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(
            flowCounter,
            PersistentDataType.INTEGER,
            pdc.get(flowCounter, PersistentDataType.INTEGER) + flowPerHit
        );
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        // a kill puts you in a flow state, next move arrives immediately
        p.getPersistentDataContainer().set(
            flowCounter,
            PersistentDataType.INTEGER,
            flowRestock
        );
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!e.getAction().isRightClick()) return;
        if (e.getItem() == null) return;

        Move move = Move.fromMaterial(e.getItem().getType());
        if (move == null) return;

        boolean used = switch (move) {
            case FLYING_KICK -> flyingKick(p);
            case SHADOW_STEP -> shadowStep(p);
            case CRANE_LEAP -> craneLeap(p);
            case RISING_DRAGON -> risingDragon(p);
            case WHIRLWIND_KICK -> whirlwindKick(p);
            case PALM_STRIKE -> palmStrike(p);
            case IRON_FIST -> ironFist(p);
            case PRESSURE_POINT -> pressurePoint(p);
            case STONE_GUARD -> stoneGuard(p);
            case INNER_PEACE -> innerPeace(p);
        };

        if (used) SnapshotPvpPlugin.clearInv(p.getInventory(), move.material);
    }

    // ---------------------------------------------------------------- moves

    private boolean flyingKick(Player p) {
        Vector forward = flatDirection(p);
        p.setVelocity(forward.clone().multiply(1.1).setY(0.35));
        p.getWorld().playSound(
            p.getLocation(),
            Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK,
            1.0f,
            1.2f
        );

        hitWhileTravelling(
            p,
            12,
            false,
            1.5,
            forward.clone().multiply(1.0).setY(0.4),
            6.0,
            0.75
        );
        return true;
    }

    private boolean shadowStep(Player p) {
        Vector dir = flatDirection(p);
        Location start = p.getLocation();
        Location best = null;

        if (dir.lengthSquared() > 0) {
            for (double d = 0.5; d <= SHADOW_STEP_RANGE; d += 0.5) {
                Location check = start.clone().add(dir.clone().multiply(d));
                if (
                    !check.getBlock().isPassable() ||
                    !check.clone().add(0, 1, 0).getBlock().isPassable()
                ) break;
                best = check;
            }
        }

        if (best == null || best.distanceSquared(start) < 4) {
            p.sendMessage(
                Component.text(
                    "There's no room to step forward.",
                    NamedTextColor.RED
                )
            );
            return false;
        }

        p.getWorld().spawnParticle(
            Particle.PORTAL,
            start.clone().add(0, 1, 0),
            30,
            0.3,
            0.6,
            0.3,
            0.1
        );
        p.teleport(best);
        p.getWorld().spawnParticle(
            Particle.PORTAL,
            best.clone().add(0, 1, 0),
            30,
            0.3,
            0.6,
            0.3,
            0.1
        );
        p.getWorld().playSound(
            best,
            Sound.ENTITY_ENDERMAN_TELEPORT,
            0.8f,
            1.4f
        );
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 1));
        return true;
    }

    private boolean craneLeap(Player p) {
        Vector direction = flatDirection(p);
        Vector forward = direction.clone().multiply(0.7);
        p.setVelocity(new Vector(forward.getX(), 0.8, forward.getZ()));
        p.addPotionEffect(
            new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0)
        );
        p.getWorld().spawnParticle(
            Particle.CLOUD,
            p.getLocation(),
            15,
            0.3,
            0.1,
            0.3,
            0.05
        );
        p.getWorld().playSound(
            p.getLocation(),
            Sound.ENTITY_RABBIT_JUMP,
            1.0f,
            0.8f
        );

        hitWhileTravelling(
            p,
            60,
            true,
            2.0,
            direction.clone().multiply(0.9).setY(0.4),
            3.0,
            0.7
        );
        return true;
    }

    private boolean risingDragon(Player p) {
        List<LivingEntity> targets = enemiesNear(p, 3.5, 2.5, 3.5);
        if (targets.isEmpty()) return noTarget(p);

        double damage = moveDamage(p, 4.0, 0.7);
        for (LivingEntity target : targets) {
            hit(p, target, damage, new Vector(0, 0.95, 0));
            impact(target);
        }

        p.setVelocity(p.getVelocity().setY(0.6));
        for (double i = 0; i < 360; i += 20) {
            double angle = Math.toRadians(i);
            p.getWorld().spawnParticle(
                Particle.FLAME,
                p
                    .getLocation()
                    .add(2 * Math.cos(angle), 0.2, 2 * Math.sin(angle)),
                2,
                0,
                0.3,
                0,
                0.02
            );
        }
        p.getWorld().playSound(
            p.getLocation(),
            Sound.ENTITY_BLAZE_SHOOT,
            1.0f,
            1.2f
        );
        return true;
    }

    private boolean whirlwindKick(Player p) {
        List<LivingEntity> targets = enemiesNear(p, 3.5, 2, 3.5);
        if (targets.isEmpty()) return noTarget(p);

        double damage = moveDamage(p, 4.0, 0.7);
        Vector fallback = flatDirection(p);
        for (LivingEntity target : targets) {
            Vector away = target
                .getLocation()
                .toVector()
                .subtract(p.getLocation().toVector());
            away.setY(0);
            away =
                away.lengthSquared() < 1.0E-4
                    ? fallback.clone()
                    : away.normalize();
            hit(p, target, damage, away.multiply(1.1).setY(0.35));
            impact(target);
        }

        for (double i = 0; i < 360; i += 20) {
            double angle = Math.toRadians(i);
            p.getWorld().spawnParticle(
                Particle.SWEEP_ATTACK,
                p
                    .getLocation()
                    .add(2.5 * Math.cos(angle), 1, 2.5 * Math.sin(angle)),
                1,
                0,
                0,
                0,
                0
            );
        }
        p.getWorld().playSound(
            p.getLocation(),
            Sound.ENTITY_PLAYER_ATTACK_SWEEP,
            1.0f,
            0.8f
        );
        return true;
    }

    private boolean palmStrike(Player p) {
        LivingEntity target = targetInSight(p, 5.0);
        if (target == null) return noTarget(p);

        hit(
            p,
            target,
            moveDamage(p, 5.0, 1.0),
            flatDirection(p).multiply(1.7).setY(0.35)
        );
        impact(target);
        p.getWorld().playSound(
            target.getLocation(),
            Sound.ENTITY_PLAYER_ATTACK_STRONG,
            1.0f,
            0.7f
        );
        return true;
    }

    private boolean ironFist(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1));
        p.getWorld().spawnParticle(
            Particle.CRIT,
            p.getLocation().add(0, 1, 0),
            25,
            0.4,
            0.5,
            0.4,
            0.2
        );
        p.getWorld().playSound(
            p.getLocation(),
            Sound.ENTITY_PLAYER_ATTACK_CRIT,
            1.0f,
            0.7f
        );
        return true;
    }

    private boolean pressurePoint(Player p) {
        LivingEntity target = targetInSight(p, 4.5);
        if (target == null) return noTarget(p);

        target.damage(moveDamage(p, 3.0, 0.75), p);
        target.addPotionEffect(
            new PotionEffect(PotionEffectType.WEAKNESS, 100, 1)
        );
        target.addPotionEffect(
            new PotionEffect(PotionEffectType.SLOWNESS, 40, 1)
        );
        impact(target);
        p.getWorld().playSound(
            target.getLocation(),
            Sound.ENTITY_ARROW_HIT_PLAYER,
            1.0f,
            0.6f
        );
        return true;
    }

    private boolean stoneGuard(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0));
        p.getWorld().spawnParticle(
            Particle.CRIT,
            p.getLocation().add(0, 1, 0),
            20,
            0.4,
            0.6,
            0.4,
            0.05
        );
        p.getWorld().playSound(
            p.getLocation(),
            Sound.ITEM_SHIELD_BLOCK,
            1.0f,
            0.6f
        );
        return true;
    }

    private boolean innerPeace(Player p) {
        for (PotionEffectType type : CLEANSE) p.removePotionEffect(type);
        p.addPotionEffect(
            new PotionEffect(PotionEffectType.REGENERATION, 80, 2)
        );
        p.getWorld().spawnParticle(
            Particle.HAPPY_VILLAGER,
            p.getLocation().add(0, 1, 0),
            20,
            0.5,
            0.7,
            0.5,
            0
        );
        p.getWorld().playSound(
            p.getLocation(),
            Sound.BLOCK_AMETHYST_BLOCK_CHIME,
            1.0f,
            1.0f
        );
        return true;
    }

    // -------------------------------------------------------------- helpers

    private void resetFlow(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(flowCounter, PersistentDataType.INTEGER, 0);

        Location loc = p.getLocation();
        pdc.set(lastLocXKey, PersistentDataType.DOUBLE, loc.getX());
        pdc.set(lastLocYKey, PersistentDataType.DOUBLE, loc.getY());
        pdc.set(lastLocZKey, PersistentDataType.DOUBLE, loc.getZ());
    }

    private Set<Move> heldMoves(Player p) {
        Set<Move> held = EnumSet.noneOf(Move.class);
        PlayerInventory inv = p.getInventory();
        for (Move move : Move.values())
            if (inv.contains(move.material)) held.add(move);
        return held;
    }

    private void clearMoves(Player p) {
        PlayerInventory inv = p.getInventory();
        for (Move move : Move.values())
            SnapshotPvpPlugin.clearInv(inv, move.material);
    }

    private void giveRandomMove(Player p, Set<Move> held) {
        List<Move> pool = new ArrayList<>();
        for (Move move : Move.values())
            if (!held.contains(move)) pool.add(move);
        if (pool.isEmpty()) return;

        Move next = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        p.getInventory().addItem(moveItems.get(next).clone());
        p.playSound(
            p.getLocation(),
            Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
            0.6f,
            1.6f
        );
    }

    private boolean isEnemy(Player p, Entity entity) {
        return (
            entity instanceof LivingEntity l &&
            !(entity instanceof ArmorStand) &&
            !l.isDead() &&
            !l.equals(p) &&
            !SnapshotPvpPlugin.isSameTeam(p, l)
        );
    }

    private List<LivingEntity> enemiesNear(
        Player p,
        double rx,
        double ry,
        double rz
    ) {
        List<LivingEntity> enemies = new ArrayList<>();
        for (Entity entity : p.getNearbyEntities(rx, ry, rz)) {
            if (isEnemy(p, entity)) enemies.add((LivingEntity) entity);
        }
        return enemies;
    }

    private LivingEntity targetInSight(Player p, double range) {
        Location eye = p.getEyeLocation();
        RayTraceResult result = p
            .getWorld()
            .rayTraceEntities(eye, eye.getDirection(), range, 0.8, entity ->
                isEnemy(p, entity)
            );
        if (
            result == null ||
            !(result.getHitEntity() instanceof LivingEntity target)
        ) return null;
        return target;
    }

    private Vector flatDirection(Player p) {
        Vector dir = p.getLocation().getDirection();
        dir.setY(0);
        if (dir.lengthSquared() < 1.0E-4) return new Vector(0, 0, 0);
        return dir.normalize();
    }

    /**
     * Hits every enemy the monk passes through while a move is carrying them,
     * once per enemy. Runs for up to maxTicks, or until the monk lands if
     * stopOnLanding is set. reachY is how far above/below you can hit.
     */
    private void hitWhileTravelling(
        Player p,
        int maxTicks,
        boolean stopOnLanding,
        double reachY,
        Vector knockback,
        double base,
        double multiplier
    ) {
        Set<UUID> hitEntities = new HashSet<>(); // once per enemy per move

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (
                    tick++ > maxTicks ||
                    !p.isOnline() ||
                    p.isDead() ||
                    (stopOnLanding && tick > 5 && p.isOnGround())
                ) {
                    this.cancel();
                    return;
                }

                p.setFallDistance(0);
                p.getWorld().spawnParticle(
                    Particle.CLOUD,
                    p.getLocation(),
                    3,
                    0.2,
                    0.2,
                    0.2,
                    0.01
                );

                for (Entity entity : p.getNearbyEntities(1.8, reachY, 1.8)) {
                    if (!isEnemy(p, entity)) continue;
                    if (!hitEntities.add(entity.getUniqueId())) continue;
                    LivingEntity target = (LivingEntity) entity;
                    hit(
                        p,
                        target,
                        moveDamage(p, base, multiplier),
                        knockback.clone()
                    );
                    impact(target);
                    p.getWorld().playSound(
                        target.getLocation(),
                        Sound.ENTITY_PLAYER_ATTACK_STRONG,
                        1.0f,
                        1.1f
                    );
                }
            }
        }.runTaskTimer(SnapshotPvpPlugin.instance, 0L, 1L);
    }

    /**
     * Damage for a move: the move's base plus the monk's attack damage times
     * the move's multiplier. The monk's attack damage is 7, and Strength adds
     * 20% per level on top (I = 8.4, II = 9.8, III = 11.2), so the bonus
     * ranges from ~5 (weakest move, no Strength) to ~10 (Palm Strike, Strength II).
     */
    private double moveDamage(Player p, double base, double multiplier) {
        AttributeInstance attribute = p.getAttribute(Attribute.ATTACK_DAMAGE);
        double attack = attribute == null ? 7.0 : attribute.getValue();
        return base + attack * multiplier;
    }

    private void hit(
        Player p,
        LivingEntity target,
        double damage,
        Vector knockback
    ) {
        target.damage(damage, p);
        target.setVelocity(knockback);
    }

    private void impact(LivingEntity target) {
        target
            .getWorld()
            .spawnParticle(
                Particle.CRIT,
                target.getLocation().add(0, 1, 0),
                15,
                0.3,
                0.5,
                0.3,
                0.2
            );
    }

    private boolean noTarget(Player p) {
        p.sendMessage(
            Component.text(
                "You need an enemy in range to use that move.",
                NamedTextColor.RED
            )
        );
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.2f);
        return false;
    }
}
