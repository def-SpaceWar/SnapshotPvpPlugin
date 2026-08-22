package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class Juggernaut extends ManaKit {

    private final int SMASH_COOLDOWN = 400;
    private final int SMASH_IMMUNITY_DURATION = 100;
    private final double SMASH_RADIUS = 4;
    private final double SMASH_RADIUS_SQUARED = SMASH_RADIUS * SMASH_RADIUS;

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
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");

        p.sendActionBar(
            ChatColor.RED + "Killstreak: " + ChatColor.WHITE + killstreak + "/2"
        );

        if (
            killstreak >= 1 && !p.hasPotionEffect(PotionEffectType.HEALTH_BOOST)
        ) p.addPotionEffect(
            new PotionEffect(PotionEffectType.HEALTH_BOOST, -1, 1)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.RESISTANCE, 100, 1)
        );
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        p.clearActivePotionEffects();
        resetKit(p);
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
        if (p.hasCooldown(Material.MACE) || !p.isOnGround()) return;
        p.setCooldown(Material.MACE, SMASH_COOLDOWN);
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

        java.util.Random random = java.util.concurrent.ThreadLocalRandom.current();

        int blockCount = (int) (SMASH_RADIUS * 18);
        org.bukkit.block.data.BlockData netheriteData = Material.NETHERITE_BLOCK.createBlockData();
        org.bukkit.block.data.BlockData amethystData = Material.AMETHYST_BLOCK.createBlockData();

        for (int i = 0; i < blockCount; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;
            double r = Math.sqrt(random.nextDouble()) * SMASH_RADIUS;

            double pX = posX + r * Math.cos(theta);
            double pZ = posZ + r * Math.sin(theta);
            Location spawnLoc = new Location(world, pX, posY + 0.2, pZ);

            org.bukkit.block.Block groundBlock = world.getBlockAt((int) Math.floor(pX), (int) Math.floor(posY - 0.5), (int) Math.floor(pZ));

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

        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, SMASH_IMMUNITY_DURATION, 2));
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
}
