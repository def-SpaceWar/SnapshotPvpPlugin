package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class GodOfStories extends ManaKit {

    private static final int HISTORY_TICKS = 80; // 4 seconds of multi-player history
    private static final double TIME_SLIP_RADIUS = 10.0;
    private static final double TIME_LOOP_RADIUS = 8.0;
    private static final double LOOM_RADIUS = 10.0;
    private static final int LOOM_DURATION_TICKS = 100; // 5 seconds

    private static final int TIME_SLIP_COST = 10;
    private static final int TIME_LOOP_COST = 12;
    private static final int GLORIOUS_PURPOSE_COST = 18;

    private final NamespacedKey slipCountKey = ManaKitListener.MANA_KIT_DATA0;
    private final NamespacedKey loopCountKey = ManaKitListener.MANA_KIT_DATA1;
    private final NamespacedKey loomCountKey = ManaKitListener.MANA_KIT_DATA2;
    private final NamespacedKey loomExpiryKey = ManaKitListener.MANA_KIT_DATA3;

    private final NamespacedKey slipItemKey = ManaKitListener.MANA_KIT_DATA4;
    private final NamespacedKey loopItemKey = ManaKitListener.MANA_KIT_DATA5;
    private final NamespacedKey loomItemKey = ManaKitListener.MANA_KIT_DATA6;

    private final DustOptions yggdrasilGreen = new DustOptions(
        Color.fromRGB(20, 215, 95),
        1.3f
    );
    private final DustOptions temporalGold = new DustOptions(
        Color.fromRGB(255, 200, 50),
        1.3f
    );

    // Tracks 4 seconds (80 ticks) of snapshots for all players in combat
    private final Map<UUID, Deque<TimelineSnapshot>> worldHistory = new HashMap<>();
    // Prevents multiple GodOfStories players from recording snapshots twice in the same tick
    private long lastRecordedTick = -1;
    // Tracks players who have already triggered their emergency Timeline Anchor during an active Loom
    private final Map<UUID, Set<UUID>> anchoredPlayersPerCaster = new HashMap<>();

    private record TimelineSnapshot(
        Location location,
        Vector velocity,
        double health,
        long tick
    ) {}

    public GodOfStories() {
        super("god_of_stories", "God of Stories", "[Utility Control]", 4);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -189,
            16,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(slipCountKey, PersistentDataType.INTEGER, 0);
        pdc.set(loopCountKey, PersistentDataType.INTEGER, 0);
        pdc.set(loomCountKey, PersistentDataType.INTEGER, 0);
        pdc.set(loomExpiryKey, PersistentDataType.INTEGER, 0);

        SnapshotPvpPlugin.clearInv(p, Material.CLOCK);
        SnapshotPvpPlugin.clearInv(p, Material.RECOVERY_COMPASS);
        SnapshotPvpPlugin.clearInv(p, Material.NETHER_STAR);

        anchoredPlayersPerCaster.remove(p.getUniqueId());
    }

    @Override
    public void onEnterCombat(Player p) {
        resetKit(p);
    }

    @Override
    public void onLeaveCombat(Player p) {
        p.clearActivePotionEffects();
        resetKit(p);
    }

    @Override
    public void onCombatTick(Player p) {
        World world = p.getWorld();
        int currentTick = (int) world.getGameTime();

        // 1. Record global combatant timelines once per server tick
        recordCombatTimelines(world, currentTick);

        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        int slipCount = pdc.getOrDefault(
            slipCountKey,
            PersistentDataType.INTEGER,
            0
        );
        int loopCount = pdc.getOrDefault(
            loopCountKey,
            PersistentDataType.INTEGER,
            0
        );
        int loomCount = pdc.getOrDefault(
            loomCountKey,
            PersistentDataType.INTEGER,
            0
        );
        int loomExpiry = pdc.getOrDefault(
            loomExpiryKey,
            PersistentDataType.INTEGER,
            0
        );

        // 2. Process active Loom of Yggdrasil (Glorious Purpose)
        boolean isLoomActive = loomExpiry > currentTick;
        if (isLoomActive) {
            tickYggdrasilLoom(p, currentTick, loomExpiry);
        } else {
            anchoredPlayersPerCaster.remove(p.getUniqueId());
        }

        // 3. Action bar display
        String slipStatus = hasAbilityItem(p, slipItemKey)
            ? ChatColor.GREEN + "READY"
            : ChatColor.WHITE + "" + slipCount + "/" + TIME_SLIP_COST;

        String loopStatus = hasAbilityItem(p, loopItemKey)
            ? ChatColor.GREEN + "READY"
            : ChatColor.WHITE + "" + loopCount + "/" + TIME_LOOP_COST;

        String loomStatus = isLoomActive
            ? ChatColor.GOLD + "ACTIVE (" + (loomExpiry - currentTick) + ")"
            : (hasAbilityItem(p, loomItemKey)
                ? ChatColor.GREEN + "READY"
                : ChatColor.WHITE + "" + loomCount + "/" + GLORIOUS_PURPOSE_COST);

        p.sendActionBar(
            ChatColor.DARK_GREEN +
                "Time Slip: " +
                slipStatus +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.AQUA +
                "Loop: " +
                loopStatus +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.GOLD +
                "Loom: " +
                loomStatus +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.RED +
                "Killstreak: " +
                ChatColor.WHITE +
                killstreak +
                "/3"
        );

        // 4. Killstreak & Passive Buffs
        p.addPotionEffect(
            new PotionEffect(PotionEffectType.SATURATION, 100, 0, false, false)
        );
        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 0)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.RESISTANCE, 100, 0)
        );
        if (killstreak >= 3) p.addPotionEffect(
            new PotionEffect(PotionEffectType.STRENGTH, 100, 0)
        );
    }

    private void recordCombatTimelines(World world, long currentTick) {
        if (lastRecordedTick == currentTick) return;
        lastRecordedTick = currentTick;

        Set<UUID> activeCombatants = new HashSet<>();
        for (Player target : world.getPlayers()) {
            if (
                target.getScoreboardTags().contains("combat") &&
                !target.isDead() &&
                target.getGameMode() != GameMode.SPECTATOR
            ) {
                UUID id = target.getUniqueId();
                activeCombatants.add(id);

                Deque<TimelineSnapshot> history = worldHistory.computeIfAbsent(
                    id,
                    k -> new ArrayDeque<>()
                );
                history.addLast(
                    new TimelineSnapshot(
                        target.getLocation().clone(),
                        target.getVelocity().clone(),
                        target.getHealth(),
                        currentTick
                    )
                );
                if (history.size() > HISTORY_TICKS) {
                    history.removeFirst();
                }
            }
        }

        // Clean up players who left combat to prevent stale snapshots
        Iterator<UUID> it = worldHistory.keySet().iterator();
        while (it.hasNext()) {
            if (!activeCombatants.contains(it.next())) {
                it.remove();
            }
        }
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) {
            int killstreak = SnapshotPvpPlugin.getPlayerScore(
                p,
                "dummyKillstreak"
            );
            addTemporalCharges(p, killstreak >= 2 ? 2 : 1);
        }
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player) {
            int killstreak = SnapshotPvpPlugin.getPlayerScore(
                p,
                "dummyKillstreak"
            );
            addTemporalCharges(p, killstreak >= 2 ? 2 : 1);
        }
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        if (!hasAbilityItem(p, slipItemKey)) {
            giveTimeSlipItem(p);
        }
        addTemporalCharges(p, 4);
    }

    private void addTemporalCharges(Player p, int amount) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        // 1. Time Slip (10 charges)
        if (!hasAbilityItem(p, slipItemKey)) {
            int next =
                pdc.getOrDefault(slipCountKey, PersistentDataType.INTEGER, 0) +
                amount;
            if (next >= TIME_SLIP_COST) {
                pdc.set(slipCountKey, PersistentDataType.INTEGER, 0);
                giveTimeSlipItem(p);
            } else {
                pdc.set(slipCountKey, PersistentDataType.INTEGER, next);
            }
        }

        // 2. Time Loop (12 charges)
        if (!hasAbilityItem(p, loopItemKey)) {
            int next =
                pdc.getOrDefault(loopCountKey, PersistentDataType.INTEGER, 0) +
                amount;
            if (next >= TIME_LOOP_COST) {
                pdc.set(loopCountKey, PersistentDataType.INTEGER, 0);
                giveTimeLoopItem(p);
            } else {
                pdc.set(loopCountKey, PersistentDataType.INTEGER, next);
            }
        }

        // 3. Glorious Purpose / Loom (18 charges)
        int currentTick = (int) p.getWorld().getGameTime();
        int loomExpiry = pdc.getOrDefault(
            loomExpiryKey,
            PersistentDataType.INTEGER,
            0
        );
        if (!hasAbilityItem(p, loomItemKey) && loomExpiry <= currentTick) {
            int next =
                pdc.getOrDefault(loomCountKey, PersistentDataType.INTEGER, 0) +
                amount;
            if (next >= GLORIOUS_PURPOSE_COST) {
                pdc.set(loomCountKey, PersistentDataType.INTEGER, 0);
                giveGloriousPurposeItem(p);
            } else {
                pdc.set(loomCountKey, PersistentDataType.INTEGER, next);
            }
        }
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (
            e.getAction() != Action.RIGHT_CLICK_AIR &&
            e.getAction() != Action.RIGHT_CLICK_BLOCK
        ) return;

        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        PersistentDataContainer itemPdc = item
            .getItemMeta()
            .getPersistentDataContainer();

        // 1. Activate Multi-Player Time Slip
        if (itemPdc.has(slipItemKey, PersistentDataType.INTEGER)) {
            e.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
            activateTimeSlip(p);
            return;
        }

        // 2. Activate AoE Time Loop
        if (itemPdc.has(loopItemKey, PersistentDataType.INTEGER)) {
            e.setCancelled(true);
            if (activateTimeLoop(p)) {
                item.setAmount(item.getAmount() - 1);
            }
            return;
        }

        // 3. Activate Glorious Purpose (Yggdrasil Loom)
        if (itemPdc.has(loomItemKey, PersistentDataType.INTEGER)) {
            e.setCancelled(true);
            item.setAmount(item.getAmount() - 1);

            int expiry =
                (int) p.getWorld().getGameTime() + LOOM_DURATION_TICKS;
            p.getPersistentDataContainer().set(
                loomExpiryKey,
                PersistentDataType.INTEGER,
                expiry
            );
            anchoredPlayersPerCaster.put(p.getUniqueId(), new HashSet<>());

            p.getWorld().playSound(
                p.getLocation(),
                Sound.BLOCK_BEACON_ACTIVATE,
                1.2f,
                0.7f
            );
            p.getWorld().playSound(
                p.getLocation(),
                Sound.ITEM_TOTEM_USE,
                0.7f,
                1.3f
            );
        }
    }

    // ------------------------------------------------------------------
    // ABILITY 1: MULTI-PLAYER TIME SLIP
    // ------------------------------------------------------------------
    private void activateTimeSlip(Player caster) {
        Location casterOrigin = caster.getLocation().add(0, 1, 0);
        World world = caster.getWorld();

        for (Player target : world.getNearbyPlayers(
            caster.getLocation(),
            TIME_SLIP_RADIUS
        )) {
            if (
                !target.getScoreboardTags().contains("combat") ||
                target.isDead() ||
                target.getGameMode() == GameMode.SPECTATOR
            ) continue;

            Deque<TimelineSnapshot> history = worldHistory.get(
                target.getUniqueId()
            );
            if (history == null || history.isEmpty()) continue;

            TimelineSnapshot past = history.peekFirst();
            if (past == null) continue;

            Location beforeLoc = target.getLocation().clone();
            drawTimelineThread(casterOrigin, beforeLoc.clone().add(0, 1, 0));

            world.spawnParticle(
                Particle.REVERSE_PORTAL,
                beforeLoc.add(0, 1, 0),
                25,
                0.3,
                0.5,
                0.3,
                0.08
            );

            // Teleport & restore velocity
            target.teleport(past.location());
            target.setVelocity(past.velocity());
            target.setFallDistance(0f);

            double currentHp = target.getHealth();
            double pastHp = past.health();
            double maxHp = target.getMaxHealth();

            if (target.equals(caster)) {
                // Self: Full restore to past HP if higher
                target.setHealth(Math.min(maxHp, Math.max(currentHp, pastHp)));
            } else if (SnapshotPvpPlugin.isSameTeam(caster, target)) {
                // Ally: Restore 50% of health lost over the last 4 seconds
                if (pastHp > currentHp) {
                    double heal = (pastHp - currentHp) * 0.5;
                    target.setHealth(Math.min(maxHp, currentHp + heal));
                }
            } else {
                // Enemy: Revert 50% of any healing gained over the last 4 seconds (floor of 4.0 HP)
                if (currentHp > pastHp) {
                    double revert = (currentHp - pastHp) * 0.5;
                    target.setHealth(Math.max(4.0, currentHp - revert));
                }
            }

            Location afterLoc = past.location().clone().add(0, 1, 0);
            world.spawnParticle(
                Particle.PORTAL,
                afterLoc,
                30,
                0.3,
                0.5,
                0.3,
                0.1
            );
            world.playSound(
                afterLoc,
                Sound.ITEM_CHORUS_FRUIT_TELEPORT,
                1.0f,
                1.3f
            );
        }
    }

    // ------------------------------------------------------------------
    // ABILITY 2: AOE TIME LOOP
    // ------------------------------------------------------------------
    private boolean activateTimeLoop(Player caster) {
        Map<Player, Location> trappedTargets = new HashMap<>();

        for (Player nearby : caster
            .getWorld()
            .getNearbyPlayers(caster.getLocation(), TIME_LOOP_RADIUS)) {
            if (
                nearby.equals(caster) ||
                 nearby.isDead() ||
                !nearby.getScoreboardTags().contains("combat") ||
                nearby.getGameMode() == GameMode.SPECTATOR ||
                SnapshotPvpPlugin.isSameTeam(caster, nearby)
            ) continue;

            trappedTargets.put(nearby, nearby.getLocation().clone());
        }

        if (trappedTargets.isEmpty()) {
            caster.sendMessage(
                ChatColor.RED + "No enemies within 8 blocks to trap in a Time Loop!"
            );
            caster.playSound(
                caster.getLocation(),
                Sound.ENTITY_VILLAGER_NO,
                0.6f,
                1.2f
            );
            return false;
        }

        caster
            .getWorld()
            .playSound(
                caster.getLocation(),
                Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN,
                1.0f,
                1.4f
            );

        for (Map.Entry<Player, Location> entry : trappedTargets.entrySet()) {
            drawTimelineThread(
                caster.getLocation().add(0, 1, 0),
                entry.getValue().clone().add(0, 1, 0)
            );
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (
                    ticks > 60 ||
                    !caster.isOnline() ||
                    !caster.getScoreboardTags().contains("combat")
                ) {
                    this.cancel();
                    return;
                }

                for (Map.Entry<Player, Location> entry : trappedTargets.entrySet()) {
                    Player victim = entry.getKey();
                    Location anchor = entry.getValue();

                    if (
                        !victim.isOnline() ||
                        victim.isDead() ||
                        !victim.getScoreboardTags().contains("combat")
                    ) continue;

                    // Draw persistent temporal tether to their loop anchor
                    if (ticks % 4 == 0) {
                        drawTimelineThread(
                            anchor.clone().add(0, 0.2, 0),
                            victim.getLocation().add(0, 1, 0)
                        );
                    }

                    // Snap back every 20 ticks (1s, 2s, 3s)
                    if (ticks > 0 && ticks % 20 == 0) {
                        Location returnLoc = anchor.clone();
                        returnLoc.setYaw(victim.getLocation().getYaw());
                        returnLoc.setPitch(victim.getLocation().getPitch());

                        victim.teleport(returnLoc);
                        victim.setVelocity(new Vector(0, 0, 0));
                        victim.addPotionEffect(
                            new PotionEffect(PotionEffectType.SLOWNESS, 15, 1)
                        );

                        DamageSource loopDamage = DamageSource.builder(
                            DamageType.INDIRECT_MAGIC
                        )
                            .withCausingEntity(caster)
                            .withDirectEntity(caster)
                            .build();
                        victim.damage(2.0, loopDamage);

                        victim
                            .getWorld()
                            .playSound(
                                returnLoc,
                                Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,
                                0.9f,
                                1.5f
                            );
                        victim
                            .getWorld()
                            .spawnParticle(
                                Particle.REVERSE_PORTAL,
                                returnLoc.add(0, 1, 0),
                                20,
                                0.3,
                                0.5,
                                0.3,
                                0.05
                            );
                    }
                }

                ticks++;
            }
        }.runTaskTimer(SnapshotPvpPlugin.instance, 0L, 1L);

        return true;
    }

    // ------------------------------------------------------------------
    // ABILITY 3: GLORIOUS PURPOSE (LOOM OF YGGDRASIL)
    // ------------------------------------------------------------------
    private void tickYggdrasilLoom(
        Player caster,
        int currentTick,
        int expiryTick
    ) {
        Location center = caster.getLocation();
        World world = center.getWorld();

        // Draw perimeter ring & rising Yggdrasil threads every 2 ticks
        if (currentTick % 2 == 0) {
            int points = 28;
            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI * i) / points;
                double x = center.getX() + LOOM_RADIUS * Math.cos(angle);
                double z = center.getZ() + LOOM_RADIUS * Math.sin(angle);
                Location ringLoc = new Location(
                    world,
                    x,
                    center.getY() + 0.15,
                    z
                );
                world.spawnParticle(
                    Particle.DUST,
                    ringLoc,
                    1,
                    0,
                    0,
                    0,
                    0,
                    i % 2 == 0 ? yggdrasilGreen : temporalGold
                );
            }
        }

        Set<UUID> savedSet = anchoredPlayersPerCaster.computeIfAbsent(
            caster.getUniqueId(),
            k -> new HashSet<>()
        );

        // Affect players inside the Loom
        for (Player target : world.getNearbyPlayers(center, LOOM_RADIUS)) {
            if (
                !target.getScoreboardTags().contains("combat") ||
                target.isDead() ||
                target.getGameMode() == GameMode.SPECTATOR
            ) continue;

            if (
                target.equals(caster) ||
                SnapshotPvpPlugin.isSameTeam(caster, target)
            ) {
                // Allies & Self: Accelerated Time + Emergency Timeline Anchor
                target.addPotionEffect(
                    new PotionEffect(PotionEffectType.SPEED, 20, 1)
                );
                target.addPotionEffect(
                    new PotionEffect(PotionEffectType.HASTE, 20, 1)
                );
                target.addPotionEffect(
                    new PotionEffect(PotionEffectType.RESISTANCE, 20, 0)
                );

                if (
                    target.getHealth() <= 8.0 &&
                    !savedSet.contains(target.getUniqueId())
                ) {
                    savedSet.add(target.getUniqueId());
                    triggerEmergencyTimelineSave(caster, target);
                }
            } else {
                // Enemies: Decelerated Time
                target.addPotionEffect(
                    new PotionEffect(PotionEffectType.SLOWNESS, 20, 1)
                );
                target.addPotionEffect(
                    new PotionEffect(PotionEffectType.MINING_FATIGUE, 20, 0)
                );
            }
        }

        // Decelerate enemy projectiles inside the Loom
        for (Entity entity : world.getNearbyEntities(
            center,
            LOOM_RADIUS,
            LOOM_RADIUS,
            LOOM_RADIUS
        )) {
            if (entity instanceof Projectile proj) {
                if (
                    proj.getShooter() instanceof Player shooter &&
                    !shooter.equals(caster) &&
                    !SnapshotPvpPlugin.isSameTeam(caster, shooter)
                ) {
                    proj.setVelocity(proj.getVelocity().multiply(0.6));
                    world.spawnParticle(
                        Particle.DUST,
                        proj.getLocation(),
                        1,
                        0,
                        0,
                        0,
                        0,
                        yggdrasilGreen
                    );
                }
            }
        }
    }

    private void triggerEmergencyTimelineSave(Player caster, Player ally) {
        Deque<TimelineSnapshot> history = worldHistory.get(ally.getUniqueId());
        TimelineSnapshot past = (history != null && !history.isEmpty())
            ? history.peekFirst()
            : null;

        Location oldLoc = ally.getLocation().clone();
        if (past != null) {
            ally.teleport(past.location());
            ally.setVelocity(past.velocity());
            ally.setHealth(
                Math.min(ally.getMaxHealth(), Math.max(12.0, past.health()))
            );
        } else {
            ally.setHealth(Math.min(ally.getMaxHealth(), 12.0));
        }

        ally.setFallDistance(0f);
        ally.addPotionEffect(
            new PotionEffect(PotionEffectType.ABSORPTION, 60, 1)
        );

        drawTimelineThread(
            caster.getLocation().add(0, 1, 0),
            ally.getLocation().add(0, 1, 0)
        );
        ally.getWorld().spawnParticle(
            Particle.TOTEM_OF_UNDYING,
            oldLoc.add(0, 1, 0),
            25,
            0.3,
            0.5,
            0.3,
            0.15
        );
        ally.getWorld().playSound(
            ally.getLocation(),
            Sound.ITEM_TOTEM_USE,
            0.8f,
            1.4f
        );
    }

    // ------------------------------------------------------------------
    // ITEM BUILDERS & VISUAL HELPERS
    // ------------------------------------------------------------------
    private void drawTimelineThread(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) return;
        double dist = from.distance(to);
        if (dist <= 0.1 || dist > 40.0) return;

        Vector dir = to.toVector().subtract(from.toVector()).normalize();
        for (double d = 0; d < dist; d += 0.5) {
            Location point = from.clone().add(dir.clone().multiply(d));
            from.getWorld().spawnParticle(
                Particle.DUST,
                point,
                1,
                0.02,
                0.02,
                0.02,
                0,
                ((int) (d * 2)) % 2 == 0 ? yggdrasilGreen : temporalGold
            );
        }
    }

    private void giveTimeSlipItem(Player p) {
        ItemStack item = new ItemStack(Material.CLOCK, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(
                ChatColor.GREEN + "" + ChatColor.BOLD + "Time Slip"
            );
            meta.setLore(
                List.of(
                    ChatColor.GRAY + "Right-click to rewind yourself and all",
                    ChatColor.GRAY + "players within 10 blocks back " + ChatColor.GREEN + "4 seconds" + ChatColor.GRAY + "."
                )
            );
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.getPersistentDataContainer().set(
                slipItemKey,
                PersistentDataType.INTEGER,
                1
            );
            item.setItemMeta(meta);
        }
        p.getInventory().addItem(item);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.4f);
    }

    private void giveTimeLoopItem(Player p) {
        ItemStack item = new ItemStack(Material.RECOVERY_COMPASS, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(
                ChatColor.AQUA + "" + ChatColor.BOLD + "Time Twister Loop"
            );
            meta.setLore(
                List.of(
                    ChatColor.GRAY + "Right-click to trap enemies within 8 blocks",
                    ChatColor.GRAY + "in a repeating " + ChatColor.AQUA + "3-second Time Loop" + ChatColor.GRAY + "."
                )
            );
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.getPersistentDataContainer().set(
                loopItemKey,
                PersistentDataType.INTEGER,
                1
            );
            item.setItemMeta(meta);
        }
        p.getInventory().addItem(item);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
    }

    private void giveGloriousPurposeItem(Player p) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(
                ChatColor.GOLD + "" + ChatColor.BOLD + "Glorious Purpose"
            );
            meta.setLore(
                List.of(
                    ChatColor.GRAY + "Right-click to weave the " + ChatColor.GREEN + "Loom of Yggdrasil",
                    ChatColor.GRAY + "for 5s, accelerating allies, slowing",
                    ChatColor.GRAY + "enemies, and auto-rewinding low-HP allies."
                )
            );
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.getPersistentDataContainer().set(
                loomItemKey,
                PersistentDataType.INTEGER,
                1
            );
            item.setItemMeta(meta);
        }
        p.getInventory().addItem(item);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.3f);
    }

    private boolean hasAbilityItem(Player p, NamespacedKey key) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()) {
                if (
                    item
                        .getItemMeta()
                        .getPersistentDataContainer()
                        .has(key, PersistentDataType.INTEGER)
                ) {
                    return true;
                }
            }
        }
        return false;
    }
}
