package com.defspacemine.snapshotpvp.enchantment;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;
import io.papermc.paper.entity.LookAnchor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

public class EnchantmentListener implements Listener {

    public static Enchantment CLEAVING;

    public static Enchantment EXPLOSIVE_HOOK;
    public static Enchantment TRIPLE_JUMP;
    public static Enchantment BREEZY_ASPECT;
    public static Enchantment LIGHTNING_ASPECT;
    public static Enchantment DOUBLE_JUMP;
    public static Enchantment HEAVY_HIT_CURSE;
    public static Enchantment BACKSTAB;
    public static Enchantment UPPERCUT;
    public static Enchantment DASHING;
    public static Enchantment LIFE_FEAST;
    public static Enchantment FREEZING_ASPECT;
    public static Enchantment LIFE_STEAL;
    public static Enchantment NON_FATAL_SMASH_CURSE;
    public static Enchantment TARGET_CHAIN;
    public static Enchantment UNSTABLE;
    public static Enchantment FATIGUE_CURSE;

    private final JavaPlugin plugin;

    private final Map<UUID, Integer> jumpCount = new HashMap<>();
    private final Map<UUID, Boolean> lastJumpInput = new HashMap<>();

    private static final class FatigueState {

        double fatigue = 0;
        int lastTick = Bukkit.getCurrentTick();
        int cooldownTotal = 0;
    }

    private final Map<UUID, FatigueState> bowFatigue = new HashMap<>();
    private final Map<UUID, BossBar> fatigueBars = new HashMap<>();

    public EnchantmentListener(JavaPlugin plugin) {
        this.plugin = plugin;

        CLEAVING = Registry.ENCHANTMENT.get(
            new NamespacedKey("minecraft", "cleaving")
        );

        EXPLOSIVE_HOOK = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "explosive_hook")
        );
        TRIPLE_JUMP = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "triple_jump")
        );
        BREEZY_ASPECT = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "breezy_aspect")
        );
        LIGHTNING_ASPECT = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "lightning_aspect")
        );
        DOUBLE_JUMP = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "double_jump")
        );
        HEAVY_HIT_CURSE = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "heavy_hit_curse")
        );
        BACKSTAB = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "backstab")
        );
        UPPERCUT = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "uppercut")
        );
        DASHING = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "dashing")
        );
        LIFE_FEAST = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "life_feast")
        );
        FREEZING_ASPECT = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "freezing_aspect")
        );
        LIFE_STEAL = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "life_steal")
        );
        NON_FATAL_SMASH_CURSE = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "non_fatal_smash_curse")
        );
        TARGET_CHAIN = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "target_chain")
        );
        UNSTABLE = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "unstable")
        );
        FATIGUE_CURSE = Registry.ENCHANTMENT.get(
            new NamespacedKey("defspacemine", "fatigue_curse")
        );

        Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::updateFatigueBars,
            0L,
            2L
        );
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p.isOnGround()) {
            jumpCount.put(p.getUniqueId(), 0);
        }
    }

    @EventHandler
    public void onPlayerInput(PlayerInputEvent e) {
        Player p = e.getPlayer();

        if (
            p.getGameMode() == GameMode.CREATIVE ||
            p.getGameMode() == GameMode.SPECTATOR
        ) return;

        boolean hasTriple = hasTripleJump(p);
        boolean hasDouble = hasDoubleJump(p);
        if (!hasTriple && !hasDouble) return;

        Input input = e.getInput();
        boolean isJumpingNow = input.isJump();
        boolean wasJumpingLast = lastJumpInput.getOrDefault(
            p.getUniqueId(),
            false
        );

        lastJumpInput.put(p.getUniqueId(), isJumpingNow);

        if (isJumpingNow && !wasJumpingLast) {
            if (!p.isOnGround() && !p.isSwimming() && !p.isClimbing()) {
                int maxJumps = hasTriple ? 2 : 1;
                int currentJumps = jumpCount.getOrDefault(p.getUniqueId(), 0);

                if (currentJumps < maxJumps) {
                    if (p.isGliding()) p.setGliding(false);

                    jumpCount.put(p.getUniqueId(), currentJumps + 1);

                    if (hasTriple) performTripleJump(p);
                    else performDoubleJump(p);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        jumpCount.remove(uuid);
        lastJumpInput.remove(uuid);
        bowFatigue.remove(uuid);
        BossBar bar = fatigueBars.remove(uuid);
        if (bar != null) e.getPlayer().hideBossBar(bar);
    }

    private boolean hasTripleJump(Player p) {
        ItemStack boots = p.getInventory().getBoots();
        return boots != null && boots.containsEnchantment(TRIPLE_JUMP);
    }

    private boolean hasDoubleJump(Player p) {
        ItemStack boots = p.getInventory().getBoots();
        return boots != null && boots.containsEnchantment(DOUBLE_JUMP);
    }

    private void performTripleJump(Player p) {
        Vector direction = p.getLocation().getDirection().normalize();
        Vector boost = direction.multiply(0.6);
        boost.setY(0.9);

        p.setVelocity(p.getVelocity().add(boost));

        p.getWorld().spawnParticle(
            Particle.GUST,
            p.getLocation(),
            10,
            0.2,
            0.2,
            0.2,
            0
        );

        p.getWorld().playSound(
            p.getLocation(),
            Sound.ENTITY_WIND_CHARGE_THROW,
            1f,
            1f
        );

        WindCharge charge = (WindCharge) p
            .getWorld()
            .spawnEntity(
                p.getLocation().subtract(0, 1, 0),
                EntityType.WIND_CHARGE
            );

        charge.setShooter(p);
        charge.setVelocity(new Vector(0, -1, 0));
    }

    private void performDoubleJump(Player p) {
        Location loc = p.getLocation();
        Creeper creeper = (Creeper) loc
            .getWorld()
            .spawnEntity(loc, EntityType.CREEPER);

        creeper.customName(
            Component.text(
                "Double Jump",
                NamedTextColor.AQUA,
                TextDecoration.BOLD
            ).append(Component.text(" ...wait what?", NamedTextColor.WHITE))
        );
        creeper.setCustomNameVisible(true);
        creeper.setMaxFuseTicks(0);
        creeper.setExplosionRadius(-1);
        creeper.setIgnited(true);
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        Player player = e.getPlayer();
        ItemStack rod = player.getInventory().getItem(e.getHand());

        if (rod == null || rod.getType() != Material.FISHING_ROD) return;

        if (!rod.containsEnchantment(EXPLOSIVE_HOOK)) return;

        int level = rod.getEnchantmentLevel(EXPLOSIVE_HOOK);
        FishHook hook = e.getHook();

        new BukkitRunnable() {
            boolean enabled = false;

            @Override
            public void run() {
                if (hook == null || hook.isDead() || !hook.isValid()) {
                    cancel();
                    return;
                }

                if (enabled) explosiveHook(player, hook.getLocation(), level);

                if (hook.getVelocity().lengthSquared() < 0.001) {
                    enabled = true;
                    if (hook.getHookedEntity() != null) {
                        explosiveHook(player, hook.getLocation(), level);
                        cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void explosiveHook(Player p, Location loc, int level) {
        Creeper creeper = (Creeper) loc
            .getWorld()
            .spawnEntity(loc, EntityType.CREEPER);
        CustomEggListener.injectOwner(creeper, p);
        PersistentDataContainer pdc = creeper.getPersistentDataContainer();
        pdc.set(CustomEggListener.CUSTOM_DAMAGE, PersistentDataType.FLOAT, 1f);
        creeper.customName(
            Component.text("Explosive Hook")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
        );
        creeper.setExplosionRadius(level);
        creeper.explode();
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (
            e.getDamageSource().getDamageType() != DamageType.PLAYER_ATTACK
        ) return;

        if (!(e.getDamager() instanceof Player player)) return;

        if (!(e.getEntity() instanceof LivingEntity target)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (weapon == null) return;

        if (weapon.containsEnchantment(BREEZY_ASPECT)) {
            int level = weapon.getEnchantmentLevel(BREEZY_ASPECT);
            spawnBreezeBurst(player, level);
        }

        if (weapon.containsEnchantment(LIGHTNING_ASPECT)) {
            int level = weapon.getEnchantmentLevel(LIGHTNING_ASPECT);
            spawnLightningAttack(player, target, level);
        }

        if (weapon.containsEnchantment(FREEZING_ASPECT)) {
            int level = weapon.getEnchantmentLevel(FREEZING_ASPECT);
            spawnFreezingAttack(player, target, level);
        }

        if (weapon.containsEnchantment(LIFE_FEAST)) {
            int level = weapon.getEnchantmentLevel(LIFE_FEAST);
            player.addPotionEffect(
                new PotionEffect(PotionEffectType.SATURATION, level * 20, 1)
            );
            player.addPotionEffect(
                new PotionEffect(
                    PotionEffectType.REGENERATION,
                    20 * (int) Math.pow(2, level - 1),
                    3
                )
            );
        }

        if (weapon.containsEnchantment(LIFE_STEAL) && !player.isDead()) {
            int level = weapon.getEnchantmentLevel(LIFE_STEAL);
            player.heal(e.getFinalDamage() * level);
        }

        if (weapon.containsEnchantment(HEAVY_HIT_CURSE)) {
            int level = weapon.getEnchantmentLevel(HEAVY_HIT_CURSE);
            player.addPotionEffect(
                new PotionEffect(
                    PotionEffectType.SLOWNESS,
                    (int) e.getFinalDamage() * 4 * level,
                    2
                )
            );
            player.addPotionEffect(
                new PotionEffect(
                    PotionEffectType.WEAKNESS,
                    (int) e.getFinalDamage() * 2 * level,
                    1
                )
            );
            player.addPotionEffect(
                new PotionEffect(
                    PotionEffectType.JUMP_BOOST,
                    (int) e.getFinalDamage() * 2 * (level + 1),
                    255
                )
            );
        }

        if (weapon.containsEnchantment(BACKSTAB)) {
            int level = weapon.getEnchantmentLevel(BACKSTAB);
            performBackstab(player, target, level);
        }

        if (weapon.containsEnchantment(UPPERCUT)) {
            int level = weapon.getEnchantmentLevel(UPPERCUT);
            performUppercut(player, target, level);
        }

        if (weapon.containsEnchantment(UNSTABLE)) {
            int level = weapon.getEnchantmentLevel(UNSTABLE);
            performUnstable(player, target, level);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmash(EntityDamageByEntityEvent e) {
        if (
            e.getDamageSource().getDamageType() != DamageType.MACE_SMASH
        ) return;

        if (!(e.getDamager() instanceof Player player)) return;

        if (!(e.getEntity() instanceof LivingEntity target)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null) return;

        if (weapon.containsEnchantment(NON_FATAL_SMASH_CURSE)) {
            double finalDamage = e.getFinalDamage();
            double currentHealth = target.getHealth();

            if (finalDamage >= currentHealth) {
                double targetRemainingHealth = 0.5;
                double maxAllowedFinalDamage = Math.max(
                    0.1,
                    currentHealth - targetRemainingHealth
                );
                e.setDamage(
                    (e.getDamage() * maxAllowedFinalDamage) / finalDamage
                );
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();

        if (
            e.getAction() != Action.RIGHT_CLICK_AIR &&
            e.getAction() != Action.RIGHT_CLICK_BLOCK
        ) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (weapon.containsEnchantment(DASHING)) {
            if (player.hasCooldown(weapon)) return;

            Location playerLoc = player.getLocation();
            Location idealLoc = playerLoc
                .clone()
                .add(playerLoc.getDirection().normalize().clone().multiply(3));
            Location teleportLoc = raycastToWall(playerLoc, idealLoc);

            player.addPotionEffect(
                new PotionEffect(
                    PotionEffectType.SPEED,
                    20,
                    2,
                    false,
                    true,
                    true
                )
            );

            player
                .getWorld()
                .spawnParticle(
                    Particle.CLOUD,
                    playerLoc,
                    20,
                    0.3,
                    0.5,
                    0.3,
                    0.1
                );

            player.setCooldown(weapon.getType(), 35);
            player.teleport(teleportLoc);
        }
    }

    @EventHandler
    public void onTargetChain(ProjectileHitEvent e) {
        if (e.getHitBlock() != null) return;
        if (!(e.getHitEntity() instanceof LivingEntity victim)) return;

        Projectile projectile = e.getEntity();
        ItemStack weapon;
        if (projectile instanceof Arrow arr) {
            weapon = arr.getWeapon();
        } else if (projectile instanceof SpectralArrow spec) {
            weapon = spec.getWeapon();
        } else return;
        if (weapon == null || !weapon.containsEnchantment(TARGET_CHAIN)) return;

        ProjectileSource source = projectile.getShooter();
        if (!(source instanceof LivingEntity shooter)) return;
        Team team = SnapshotPvpPlugin.getTeamG(shooter);

        for (Entity entity : projectile.getNearbyEntities(20, 20, 20)) {
            if (!projectile.canHitEntity(entity)) continue;
            if (victim.equals(entity) || shooter.equals(entity)) continue;
            if (SnapshotPvpPlugin.isSameTeam(shooter, entity)) continue;
            projectile.setVelocity(
                entity
                    .getBoundingBox()
                    .getCenter()
                    .subtract(projectile.getLocation().toVector())
                    .normalize()
                    .multiply(3)
            );
            return;
        }
    }

    private void spawnBreezeBurst(Player player, int level) {
        Location spawnLoc = player.getEyeLocation();
        Vector baseDir = spawnLoc.getDirection().normalize();

        int count = 2 + level;

        for (int i = 0; i < count; i++) {
            WindCharge charge = (WindCharge) player
                .getWorld()
                .spawnEntity(spawnLoc, EntityType.WIND_CHARGE);

            charge.setShooter(player);

            Vector spread = baseDir
                .clone()
                .add(
                    new Vector(
                        (Math.random() - 0.5) * 0.3,
                        (Math.random() - 0.5) * 0.2,
                        (Math.random() - 0.5) * 0.3
                    )
                )
                .normalize();

            charge.setVelocity(spread.multiply(1.6));
        }
    }

    private void spawnLightningAttack(
        Player p,
        LivingEntity target,
        int level
    ) {
        World world = p.getWorld();
        Location spawnLoc = target.getLocation();
        double radius = 4 + 2 * level;
        double damage = 1 + level;

        Team pTeam = SnapshotPvpPlugin.getTeam(p);

        for (Entity t : world.getNearbyEntities(
            target.getLocation(),
            radius,
            radius,
            radius
        )) {
            if (t instanceof Player player) if (
                player.equals(p) ||
                (player.getGameMode() != GameMode.SURVIVAL &&
                    player.getGameMode() != GameMode.ADVENTURE)
            ) continue;

            Team tTeam = SnapshotPvpPlugin.getTeamG(t);
            if (pTeam != null && pTeam.equals(tTeam)) continue;

            SnapshotPvpPlugin.strikeLightning(t, p, damage);
        }
    }

    private void spawnFreezingAttack(Player p, LivingEntity target, int level) {
        int ticks = 140 + level * 160;
        target.setFreezeTicks(ticks);
        target.damage(
            level,
            DamageSource.builder(DamageType.FREEZE)
                .withDirectEntity(p)
                .withCausingEntity(p)
                .build()
        );
    }

    private void performBackstab(
        Player player,
        LivingEntity target,
        int level
    ) {
        double maxDistance = 2.0 + level * 1;

        Location targetLoc = target.getLocation();
        Location idealLoc = targetLoc
            .clone()
            .add(
                targetLoc
                    .getDirection()
                    .normalize()
                    .clone()
                    .multiply(-maxDistance)
            );
        Location teleportLoc = raycastToWall(target.getLocation(), idealLoc);
        if (teleportLoc == null) return;

        player.teleport(
            teleportLoc.setDirection(player.getLocation().getDirection())
        );
    }

    private void performUppercut(
        Player player,
        LivingEntity target,
        int level
    ) {
        double maxDistance = 3.0 + level * 1;

        Location targetLoc = target.getLocation();
        Vector direction = targetLoc.getDirection().normalize();

        Vector rightVec = direction
            .clone()
            .crossProduct(new Vector(0, 1, 0))
            .normalize();
        Vector upVec = rightVec.clone().crossProduct(direction).normalize();
        Location idealLoc = targetLoc
            .clone()
            .add(upVec.clone().multiply(maxDistance));

        Location teleportLoc = raycastToWall(target.getLocation(), idealLoc);
        if (teleportLoc == null) return;

        player.teleport(
            teleportLoc.setDirection(player.getLocation().getDirection())
        );
    }

    private Location raycastToWall(Location from, Location to) {
        World world = from.getWorld();
        Vector direction = to.toVector().subtract(from.toVector());
        double maxDistance = direction.length();
        direction.normalize();

        Location lastValidLoc = from.clone();

        double step = .1;
        for (double d = step; d <= maxDistance; d += step) {
            Location checkPoint = from
                .clone()
                .add(direction.clone().multiply(d));
            Material type = checkPoint.getBlock().getType();

            if (
                type.isSolid() && type.isOccluding() && !type.isInteractable()
            ) return lastValidLoc;
            lastValidLoc = checkPoint.clone();
        }

        return to.clone();
    }

    private void performUnstable(
        Player player,
        LivingEntity target,
        int level
    ) {
        if (Math.random() >= 0.20) return;

        double distance = 2.0 + Math.random() * level * 1.5;

        double randomAngle = Math.random() * 2 * Math.PI;

        Location targetLoc = target.getLocation();

        double offsetX = Math.cos(randomAngle) * distance;
        double offsetZ = Math.sin(randomAngle) * distance;

        Location idealLoc = targetLoc.clone().add(offsetX, 0, offsetZ);

        Location teleportLoc = raycastToWall(target.getLocation(), idealLoc);
        if (teleportLoc == null) return;

        player.teleport(
            teleportLoc.setDirection(player.getLocation().getDirection())
        );

        player
            .getWorld()
            .spawnParticle(
                Particle.PORTAL,
                player.getLocation().add(0, 1, 0),
                25,
                0.3,
                0.5,
                0.3,
                0.1
            );
        player
            .getWorld()
            .playSound(
                player.getLocation(),
                Sound.ENTITY_ENDERMAN_TELEPORT,
                0.8f,
                1.2f
            );
    }

    @EventHandler(ignoreCancelled = true)
    public void onFatigueShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;

        ItemStack bow = e.getBow();
        if (bow == null || bow.getType() != Material.BOW) return;
        if (
            FATIGUE_CURSE == null || !bow.containsEnchantment(FATIGUE_CURSE)
        ) return;

        int level = bow.getEnchantmentLevel(FATIGUE_CURSE);
        int threshold = fatigueThreshold(level);
        int decayTicksPerShot = fatigueDecayTicks(level);
        int cooldownTicks = fatigueCooldownTicks(level);

        FatigueState state = bowFatigue.computeIfAbsent(p.getUniqueId(), k ->
            new FatigueState()
        );

        int now = Bukkit.getCurrentTick();
        state.fatigue = Math.max(
            0,
            state.fatigue - (now - state.lastTick) / (double) decayTicksPerShot
        );
        state.lastTick = now;
        state.fatigue += 1;

        if (state.fatigue >= threshold) {
            state.fatigue = 0;
            state.cooldownTotal = cooldownTicks;
            p.setCooldown(Material.BOW, cooldownTicks);

            p.getWorld().playSound(
                p.getLocation(),
                Sound.ENTITY_PLAYER_BREATH,
                1.0f,
                0.6f
            );
        }
    }

    private int fatigueThreshold(int level) {
        return Math.max(2, 6 - level);
    }

    private int fatigueDecayTicks(int level) {
        return Math.max(20, 50 + level * 10);
    }

    private int fatigueCooldownTicks(int level) {
        return 60 + level * 40;
    }

    /** Returns the fatigue-cursed bow in either hand, or null. */
    private ItemStack findFatigueBow(Player p) {
        if (FATIGUE_CURSE == null) return null;
        ItemStack main = p.getInventory().getItemInMainHand();
        if (
            main.getType() == Material.BOW &&
            main.containsEnchantment(FATIGUE_CURSE)
        ) return main;
        ItemStack off = p.getInventory().getItemInOffHand();
        if (
            off.getType() == Material.BOW &&
            off.containsEnchantment(FATIGUE_CURSE)
        ) return off;
        return null;
    }

    private void hideFatigueBar(Player p) {
        BossBar bar = fatigueBars.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
    }

    private void updateFatigueBars() {
        int now = Bukkit.getCurrentTick();

        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack bow = findFatigueBow(p);
            if (bow == null) {
                hideFatigueBar(p);
                continue;
            }

            int level = bow.getEnchantmentLevel(FATIGUE_CURSE);
            int threshold = fatigueThreshold(level);
            FatigueState state = bowFatigue.get(p.getUniqueId());

            BossBar bar = fatigueBars.get(p.getUniqueId());
            if (bar == null) {
                bar = BossBar.bossBar(
                    Component.empty(),
                    0f,
                    BossBar.Color.GREEN,
                    BossBar.Overlay.PROGRESS
                );
                fatigueBars.put(p.getUniqueId(), bar);
                p.showBossBar(bar);
            }

            int cooldownLeft = p.getCooldown(Material.BOW);
            if (cooldownLeft > 0) {
                int total =
                    state != null && state.cooldownTotal > 0
                        ? state.cooldownTotal
                        : cooldownLeft;
                bar.progress(
                    Math.max(0f, Math.min(1f, cooldownLeft / (float) total))
                );
                bar.color(BossBar.Color.RED);
                bar.name(
                    Component.text(
                        "Exhausted ",
                        NamedTextColor.RED,
                        TextDecoration.BOLD
                    ).append(
                        Component.text(
                            String.format("(%.1fs)", cooldownLeft / 20.0),
                            NamedTextColor.GRAY
                        )
                    )
                );
                continue;
            }

            double fatigue = 0;
            if (state != null) {
                fatigue = Math.max(
                    0,
                    state.fatigue -
                        (now - state.lastTick) /
                            (double) fatigueDecayTicks(level)
                );
            }

            float progress = (float) Math.max(
                0,
                Math.min(1, fatigue / threshold)
            );
            bar.progress(progress);
            bar.color(
                progress < 0.5f
                    ? BossBar.Color.GREEN
                    : progress < 0.8f
                      ? BossBar.Color.YELLOW
                      : BossBar.Color.RED
            );
            bar.name(
                Component.text("Bow Fatigue ", NamedTextColor.GOLD).append(
                    Component.text(
                        (int) Math.ceil(fatigue) + "/" + threshold,
                        NamedTextColor.WHITE
                    )
                )
            );
        }
    }
}
