package com.defspacemine.snapshotpvp.customegg;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.custommob.FITHBomb1;
import com.defspacemine.snapshotpvp.custommob.FITHBomb2;
import com.defspacemine.snapshotpvp.custommob.FITHBomb3;
import com.defspacemine.snapshotpvp.custommob.FITHNuke;
import com.defspacemine.snapshotpvp.custommob.MercAutoBomb;
import com.defspacemine.snapshotpvp.custommob.MercGoodShotBhaiya;
import com.defspacemine.snapshotpvp.custommob.MercLauncher;
import com.defspacemine.snapshotpvp.manakit.ICBM;
import com.defspacemine.snapshotpvp.manakit.LightPaladin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class CustomEggListener implements Listener {
    public final static NamespacedKey CUSTOM_EGG = new NamespacedKey("defspacemine", "custom_egg");
    public final static NamespacedKey OWNER = new NamespacedKey("defspacemine", "owner");

    public final static NamespacedKey CREEPER_CHAIN = new NamespacedKey("defspacemine", "creeper_chain");
    public final static NamespacedKey RED_TERROR = new NamespacedKey("defspacemine", "red_terror");
    public final static NamespacedKey BLINDING_LIGHT = new NamespacedKey("defspacemine", "blinding_light");

    private static final Map<String, CustomMob> registry = new HashMap<>();

    public static void register(CustomMob mob) {
        registry.put(mob.getId(), mob);
    }

    public static CustomMob get(String id) {
        return registry.get(id);
    }

    public static boolean exists(String id) {
        return registry.containsKey(id);
    }

    public static ItemStack injectOwner(ItemStack egg, Player p) {
        ItemStack result = egg.clone();
        ItemMeta meta = egg.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(OWNER, PersistentDataType.STRING, p.getUniqueId().toString());
        result.setItemMeta(meta);
        return result;
    }

    public static void injectOwner(Entity e, Player p) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(OWNER, PersistentDataType.STRING, p.getUniqueId().toString());
    }

    public static Player getOwner(Entity entity) {
        String uuid = entity.getPersistentDataContainer().get(OWNER, PersistentDataType.STRING);

        if (uuid == null)
            return null;
        return Bukkit.getPlayer(UUID.fromString(uuid));
    }

    public static Player getOwner(ItemMeta meta) {
        String uuid = meta.getPersistentDataContainer().get(OWNER, PersistentDataType.STRING);

        if (uuid == null)
            return null;
        return Bukkit.getPlayer(UUID.fromString(uuid));
    }

    private static JavaPlugin plugin;

    public CustomEggListener(JavaPlugin plugin) {
        CustomEggListener.plugin = plugin;

        register(new FITHBomb1());
        register(new FITHBomb2());
        register(new FITHBomb3());
        register(new FITHNuke());
        register(new MercLauncher());
        register(new MercAutoBomb());
        register(new MercGoodShotBhaiya());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null)
            return;

        if (!e.hasItem())
            return;

        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = e.getItem();
        if (item == null || !item.getType().toString().endsWith("_SPAWN_EGG"))
            return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(CustomEggListener.CUSTOM_EGG, PersistentDataType.STRING);
        if (id == null)
            return;

        if (!exists(id))
            return;

        Block clicked = e.getClickedBlock();
        BlockFace face = e.getBlockFace();

        if (clicked == null || face == null)
            return;

        Block targetBlock = clicked.getRelative(face);

        if (!targetBlock.isPassable())
            return;

        e.setCancelled(true);

        Location spawnLoc = targetBlock.getLocation().add(0.5, 0, 0.5);
        CustomMob mob = get(id);
        LivingEntity entity = mob.spawn(spawnLoc.add(0, 0.01, 0), pdc);

        if (e.getPlayer().getGameMode() != GameMode.CREATIVE)
            item.setAmount(item.getAmount() - 1);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Creeper creeper) {
            Player owner = getOwner(creeper);

            if (e.getEntity() instanceof Player victim) {
                if (owner == null)
                    return;
                e.setCancelled(true);
                victim.damage(e.getFinalDamage(), owner);
                applyExplosionKnockback(victim, creeper.getLocation(), creeper.getExplosionRadius(),
                        creeper.isPowered());
            } else if (e.getEntity() instanceof Creeper c) {
                if (getOwner(e.getEntity()) == null)
                    return;
                Boolean chainable = c.getPersistentDataContainer().get(CREEPER_CHAIN,
                        PersistentDataType.BOOLEAN);
                if (chainable == null || !chainable)
                    return;
                e.setCancelled(true);
                if (creeper.isPowered())
                    c.setPowered(true);
                c.explode();
            }
            return;
        }

        Player owner = getOwner(e.getDamager());
        if (owner != null && e.getEntity() instanceof Player victim) {
            e.setCancelled(true);
            victim.damage(e.getFinalDamage(), owner);
        }
    }

    private void applyExplosionKnockback(Entity entity, Location explosion, float radius, boolean isPowered) {
        if (isPowered)
            radius *= 2;
        double exposure = getExplosionExposure(entity, explosion, 2);

        Location el = entity.getLocation();
        double dx = el.getX() - explosion.getX();
        double dy = el.getY() + entity.getHeight() * 0.5 - explosion.getY();
        double dz = el.getZ() - explosion.getZ();

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance == 0)
            return;

        double nx = dx / distance;
        double ny = dy / distance;
        double nz = dz / distance;

        double distanceRatio = distance / radius;
        if (distanceRatio > 1)
            return;

        double impact = (1 - distanceRatio) * exposure;
        Vector knockback = new Vector(nx * impact, ny * impact + impact * 0.5, nz * impact);

        if (entity instanceof LivingEntity le) {
            double expRes = 0.0;
            if (le.getAttribute(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE) != null)
                expRes = le.getAttribute(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE).getValue();

            double genRes = 0.0;
            if (le.getAttribute(Attribute.KNOCKBACK_RESISTANCE) != null)
                genRes = le.getAttribute(Attribute.KNOCKBACK_RESISTANCE).getValue();

            expRes = Math.max(0, Math.min(1, expRes));
            genRes = Math.max(0, Math.min(1, genRes));
            double combinedRes = 1 - ((1 - expRes) * (1 - genRes));
            knockback.multiply(1 - combinedRes);
        }

        entity.setVelocity(entity.getVelocity().add(knockback));
    }

    private double getExplosionExposure(Entity entity, Location explosionLoc, int samplesPerAxis) {
        BoundingBox box = entity.getBoundingBox();

        int unobstructed = 0;
        int total = 0;

        World world = entity.getWorld();

        double dx = box.getMaxX() - box.getMinX();
        double dy = box.getMaxY() - box.getMinY();
        double dz = box.getMaxZ() - box.getMinZ();

        // iterate over a grid inside the bounding box
        for (int i = 0; i <= samplesPerAxis; i++) {
            double x = box.getMinX() + dx * i / samplesPerAxis;
            for (int j = 0; j <= samplesPerAxis; j++) {
                double y = box.getMinY() + dy * j / samplesPerAxis;
                for (int k = 0; k <= samplesPerAxis; k++) {
                    double z = box.getMinZ() + dz * k / samplesPerAxis;
                    Vector dir = new Vector(x - explosionLoc.getX(),
                            y - explosionLoc.getY(),
                            z - explosionLoc.getZ());
                    RayTraceResult result = world.rayTraceBlocks(
                            explosionLoc,
                            dir,
                            dir.length(),
                            FluidCollisionMode.NEVER);
                    if (result == null)
                        unobstructed++;
                    total++;
                }
            }
        }

        if (total == 0)
            return 1.0; // fallback
        return (double) unobstructed / total;
    }

    @EventHandler
    public void onFireworkLaunch(EntitySpawnEvent e) {
        if (!(e.getEntity() instanceof Firework firework))
            return;

        ItemStack item = firework.getItem();
        if (item == null)
            return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        if (meta.getPersistentDataContainer().has(RED_TERROR, PersistentDataType.INTEGER))
            startRedTerrorTask(firework);

        if (meta.getPersistentDataContainer().has(BLINDING_LIGHT, PersistentDataType.INTEGER))
            startBlindingLightTask(firework);
    }

    private void startRedTerrorTask(Firework firework) {
        firework.getPersistentDataContainer().set(
                RED_TERROR,
                PersistentDataType.INTEGER,
                1);
        Vector dir = firework.getVelocity();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (firework.isDead() || !firework.isValid()) {
                    cancel();
                    return;
                }
                firework.setVelocity(dir);

                Location loc = firework.getLocation();
                World world = loc.getWorld();

                Creeper creeper = (Creeper) world.spawnEntity(loc, EntityType.CREEPER);

                if (firework.getShooter() instanceof Player owner)
                    injectOwner(creeper, owner);

                creeper.customName(Component.text("Red Terror")
                        .color(NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD));
                creeper.setFuseTicks(0);
                creeper.setExplosionRadius(ICBM.RED_TERROR_RADIUS);
                creeper.explode();

                firework.setVelocity(dir);
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private void startBlindingLightTask(Firework firework) {
        firework.getPersistentDataContainer().set(
                BLINDING_LIGHT,
                PersistentDataType.INTEGER,
                1);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (firework.isDead() || !firework.isValid()) {
                    cancel();
                    return;
                }

                Location loc = firework.getLocation();
                World world = loc.getWorld();

                Entity e = world.spawnEntity(loc, EntityType.LIGHTNING_BOLT);
                if (firework.getShooter() instanceof Player owner)
                    injectOwner(e, owner);

                e.customName(Component.text("Blinding Light")
                        .color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD));

                if (!(firework.getShooter() instanceof Player p))
                    return;
                Team pTeam = SnapshotPvpPlugin.scoreboard.getEntryTeam(p.getName());

                for (Player target : world.getNearbyPlayers(loc, LightPaladin.BLINDING_LIGHT_RADIUS)) {
                    if (target.equals(p) || target.getGameMode() != GameMode.ADVENTURE)
                        continue;

                    Team targetTeam = SnapshotPvpPlugin.scoreboard.getEntryTeam(target.getName());
                    if (pTeam != null && pTeam.equals(targetTeam))
                        continue;

                    target.addPotionEffect(new PotionEffect(
                            PotionEffectType.GLOWING,
                            100,
                            0,
                            false,
                            true,
                            true));
                    target.addPotionEffect(new PotionEffect(
                            PotionEffectType.BLINDNESS,
                            100,
                            0,
                            false,
                            true,
                            true));
                    target.addPotionEffect(new PotionEffect(
                            PotionEffectType.LEVITATION,
                            20,
                            6,
                            false,
                            true,
                            true));
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private static final Set<Mob> factionMobs = new HashSet<>();

    public static void registerFactionMob(Mob mob) {
        factionMobs.add(mob);
    }

    public void startGlobalAggroTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (factionMobs.isEmpty())
                return;
            Scoreboard scoreboard = SnapshotPvpPlugin.scoreboard;
            Iterator<Mob> iterator = factionMobs.iterator();

            while (iterator.hasNext()) {
                Mob mob = iterator.next();

                if (!mob.isValid() || mob.isDead()) {
                    iterator.remove();
                    continue;
                }

                LivingEntity current = mob.getTarget();
                if (current != null && !current.isDead())
                    continue;

                Team mobTeam = scoreboard.getEntryTeam(mob.getUniqueId().toString());
                if (mobTeam == null)
                    continue;

                double range = 16;
                LivingEntity closestEnemy = null;
                double closestDistance = Double.MAX_VALUE;

                for (Entity nearby : mob.getNearbyEntities(range, range, range)) {
                    if (!(nearby instanceof LivingEntity target))
                        continue;
                    if (target.isDead())
                        continue;
                    if (target == mob)
                        continue;

                    Team targetTeam = scoreboard.getEntryTeam(target.getUniqueId().toString());
                    if (targetTeam != null && !targetTeam.equals(mobTeam)) {
                        double distance = mob.getLocation().distanceSquared(target.getLocation());
                        if (distance >= closestDistance)
                            continue;

                        closestDistance = distance;
                        closestEnemy = target;
                    }
                }

                if (closestEnemy != null)
                    mob.setTarget(closestEnemy);
            }
        }, 0L, 10L);
    }
}
