package com.defspacemine.snapshotpvp.customegg;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import com.defspacemine.snapshotpvp.custommob.FITHBomb1;
import com.defspacemine.snapshotpvp.custommob.FITHBomb2;
import com.defspacemine.snapshotpvp.custommob.FITHBomb3;
import com.defspacemine.snapshotpvp.custommob.FITHNuke;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class CustomEggListener implements Listener {
    public final static NamespacedKey CUSTOM_EGG = new NamespacedKey("defspacemine", "custom_egg");
    public final static NamespacedKey OWNER = new NamespacedKey("defspacemine", "owner");
    public final static NamespacedKey CREEPER_CHAIN = new NamespacedKey("defspacemine", "creeper_chain");
    public final static NamespacedKey RED_TERROR = new NamespacedKey("defspacemine", "red_terror");

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

    private final JavaPlugin plugin;

    public CustomEggListener(JavaPlugin plugin) {
        this.plugin = plugin;

        register(new FITHBomb1());
        register(new FITHBomb2());
        register(new FITHBomb3());
        register(new FITHNuke());
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

        e.setCancelled(true);

        CustomMob mob = get(id);
        LivingEntity entity = mob.spawn(e.getClickedBlock().getLocation().add(0, 1, 0), pdc);

        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    @EventHandler
    public void onExplosionDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Creeper creeper))
            return;
        Player owner = getOwner(creeper);

        if (e.getEntity() instanceof Player victim) {
            if (owner == null)
                return;
            e.setCancelled(true);
            victim.damage(e.getFinalDamage(), owner);
            applyExplosionKnockback(victim, creeper.getLocation(), creeper.getExplosionRadius(), creeper.isPowered());
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
            startRedTerrorTask(firework, meta);
    }

    private void startRedTerrorTask(Firework firework, ItemMeta meta) {
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
                creeper.explode();

                firework.setVelocity(dir);
            }
        }.runTaskTimer(plugin, 0L, 1L); // every tick
    }
}
