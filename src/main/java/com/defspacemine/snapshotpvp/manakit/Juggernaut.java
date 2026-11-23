package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class Juggernaut extends ManaKit {

    @Override
    public org.bukkit.inventory.ItemStack getIcon() {
        return getIconFromShulker(-185, 1, -185);
    }

    private final int SMASH_COOLDOWN = 400;
    private final int SMASH_IMMUNITY_DURATION = 100;
    private final double SMASH_RADIUS = 4;
    private final double SMASH_RADIUS_SQUARED = SMASH_RADIUS * SMASH_RADIUS;
    final NamespacedKey smashCooldown = ManaKitListener.MANA_KIT_DATA2;
    final NamespacedKey unstoppableMeter = ManaKitListener.MANA_KIT_DATA3;

    public Juggernaut() {
        super("juggernaut", "Juggernaut", "[Melee Damage Tank]", 2);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -185,
            1,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(smashCooldown, PersistentDataType.INTEGER, 0);
        pdc.set(unstoppableMeter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int cooldown = pdc.getOrDefault(
            smashCooldown,
            PersistentDataType.INTEGER,
            0
        );
        if (cooldown > 0) {
            pdc.set(smashCooldown, PersistentDataType.INTEGER, cooldown - 1);
        }

        int meter = pdc.getOrDefault(
            unstoppableMeter,
            PersistentDataType.INTEGER,
            0
        );
        boolean nearbyPlayer = p
            .getWorld()
            .getPlayers()
            .stream()
            .anyMatch(
                other ->
                    !other.equals(p) &&
                    !other.isDead() &&
                    other.getGameMode() != org.bukkit.GameMode.SPECTATOR &&
                    other.getLocation().distanceSquared(p.getLocation()) <= 25
            );
        meter = nearbyPlayer
            ? Math.min(400, meter + 1)
            : Math.max(0, meter - 1);
        pdc.set(unstoppableMeter, PersistentDataType.INTEGER, meter);

        Color dustColor =
            meter >= 400
                ? Color.PURPLE
                : meter > 100
                  ? Color.RED
                  : meter > 0
                    ? Color.YELLOW
                    : Color.GRAY;
        Particle.DustOptions dust = new Particle.DustOptions(dustColor, 1.1f);
        p.getWorld().spawnParticle(
            Particle.DUST,
            p.getLocation().add(0, 1.5, 0),
            Math.max(1, meter / 100),
            0.25,
            0.35,
            0.25,
            0,
            dust
        );

        p.sendActionBar(
            (cooldown > 0
                ? ChatColor.LIGHT_PURPLE +
                  "Counter Smash: " +
                  ChatColor.WHITE +
                  cooldown
                : ChatColor.LIGHT_PURPLE + "Counter Smash!") +
                ChatColor.GRAY +
                " | " +
                ChatColor.DARK_PURPLE +
                "Unstoppable: " +
                (meter >= 400
                    ? ChatColor.LIGHT_PURPLE
                    : meter > 100
                      ? ChatColor.RED
                      : meter > 0
                        ? ChatColor.YELLOW
                        : ChatColor.GRAY) +
                meter +
                ChatColor.GRAY +
                " | " +
                ChatColor.RED +
                "Killstreak: " +
                ChatColor.WHITE +
                killstreak +
                "/2"
        );

        if (
            killstreak >= 1 && !p.hasPotionEffect(PotionEffectType.HEALTH_BOOST)
        ) p.addPotionEffect(
            new PotionEffect(PotionEffectType.HEALTH_BOOST, -1, 1)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.RESISTANCE, 100, 0)
        );
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        p.clearActivePotionEffects();
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(smashCooldown, PersistentDataType.INTEGER, 0);
        pdc.set(unstoppableMeter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        if (p.isDead()) return;
        p.addPotionEffect(
            new PotionEffect(PotionEffectType.HEALTH_BOOST, -1, 1)
        );
        p.setHealth(28);
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (!e.getAction().isRightClick()) return;
        if (
            e.getItem() == null || e.getItem().getType() != Material.MACE
        ) return;
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int cooldown = pdc.getOrDefault(
            smashCooldown,
            PersistentDataType.INTEGER,
            0
        );
        if (cooldown > 0 || !p.isOnGround()) return;
        pdc.set(smashCooldown, PersistentDataType.INTEGER, SMASH_COOLDOWN);
        World world = p.getWorld();

        PotionEffect jumpBoost = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
        PotionEffect slowness = p.getPotionEffect(PotionEffectType.SLOWNESS);
        PotionEffect weakness = p.getPotionEffect(PotionEffectType.WEAKNESS);

        Location pos = p.getLocation();
        world.playSound(
            pos,
            org.bukkit.Sound.ITEM_MACE_SMASH_GROUND_HEAVY,
            1.0f,
            0.5f
        );

        double posX = pos.x();
        double posY = pos.y();
        double posZ = pos.z();
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof LivingEntity l)) continue;
            if (SnapshotPvpPlugin.isSameTeam(p, l)) continue;
            Location lPos = l.getLocation();
            if (
                Math.abs(lPos.y() - posY) > 1 ||
                square(lPos.x() - posX) + square(lPos.z() - posZ) >
                    SMASH_RADIUS_SQUARED
            ) continue;
            if (
                jumpBoost != null && jumpBoost.getAmplifier() == 255
            ) l.addPotionEffect(jumpBoost);
            if (slowness != null) l.addPotionEffect(slowness);
            if (weakness != null) l.addPotionEffect(weakness);
            world.playSound(
                lPos,
                org.bukkit.Sound.BLOCK_ANVIL_LAND,
                0.5f,
                0.8f
            );
        }

        java.util.Random random =
            java.util.concurrent.ThreadLocalRandom.current();

        int blockCount = (int) (SMASH_RADIUS * 18);
        org.bukkit.block.data.BlockData netheriteData =
            Material.NETHERITE_BLOCK.createBlockData();
        org.bukkit.block.data.BlockData amethystData =
            Material.AMETHYST_BLOCK.createBlockData();

        for (int i = 0; i < blockCount; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;
            double r = Math.sqrt(random.nextDouble()) * SMASH_RADIUS;

            double pX = posX + r * Math.cos(theta);
            double pZ = posZ + r * Math.sin(theta);
            Location spawnLoc = new Location(world, pX, posY + 0.2, pZ);

            org.bukkit.block.Block groundBlock = world.getBlockAt(
                (int) Math.floor(pX),
                (int) Math.floor(posY - 0.5),
                (int) Math.floor(pZ)
            );

            org.bukkit.block.data.BlockData data;
            if (groundBlock.getType().isAir()) {
                data = random.nextBoolean() ? netheriteData : amethystData;
            } else {
                data = groundBlock.getBlockData();
            }

            FallingBlock block = world.spawnFallingBlock(spawnLoc, data);
            block.setDropItem(false);
            block.setHurtEntities(false);
            block.setCancelDrop(true);

            double outwardSpeed = 0.25 + random.nextDouble() * 0.25;
            double vx = Math.cos(theta) * outwardSpeed;
            double vz = Math.sin(theta) * outwardSpeed;
            double vy = 0.35 + random.nextDouble() * 0.3;

            block.setVelocity(new org.bukkit.util.Vector(vx, vy, vz));

            Bukkit.getScheduler().runTaskLater(
                SnapshotPvpPlugin.instance,
                block::remove,
                20L
            );
        }

        p.addPotionEffect(
            new PotionEffect(
                PotionEffectType.ABSORPTION,
                SMASH_IMMUNITY_DURATION,
                2
            )
        );
        new BukkitRunnable() {
            int currentTick = 0;

            @Override
            public void run() {
                if (
                    currentTick > SMASH_IMMUNITY_DURATION ||
                    !p.isOnline() ||
                    p.isDead()
                ) {
                    this.cancel();
                    return;
                }

                PotionEffect jumpBoost = p.getPotionEffect(
                    PotionEffectType.JUMP_BOOST
                );
                if (
                    jumpBoost != null && jumpBoost.getAmplifier() == 255
                ) p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.removePotionEffect(PotionEffectType.WEAKNESS);

                currentTick++;
            }
        }.runTaskTimer(SnapshotPvpPlugin.instance, 0L, 1L);
    }

    double square(double x) {
        return x * x;
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity)) return;
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int meter = pdc.getOrDefault(
            unstoppableMeter,
            PersistentDataType.INTEGER,
            0
        );
        pdc.set(
            unstoppableMeter,
            PersistentDataType.INTEGER,
            Math.max(0, meter - 50)
        );

        double multiplier = meter >= 400 ? 2.0 : meter > 100 ? 1.0 : 0.5;
        e.setDamage(e.getDamage() * multiplier);
    }
}
