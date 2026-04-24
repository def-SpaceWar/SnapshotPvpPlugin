package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public class Titan extends ManaKit {
    private static final NamespacedKey FURY_SPEED = new NamespacedKey("defspacemine", "titan_speed");
    private static final NamespacedKey FURY_ATTACK_SPEED = new NamespacedKey("defspacemine", "titan_attack_speed");
    final NamespacedKey furyCounter = ManaKitListener.MANA_KIT_DATA0;

    private final double SMASH_RANGE = 12.0;
    private final double SMASH_ANGLE = 90.0;
    private final double SMASH_DAMAGE = 8.0;
    private final int SMASH_TICKS = 40;
    private final int SMASH_COOLDOWN = 160;

    private int getStage(int fury) {
        if (fury < 200)
            return 0;
        if (fury < 600)
            return 1;
        if (fury < 1200)
            return 2;
        if (fury < 2000)
            return 3;
        if (fury < 3000)
            return 4;
        return 5;
    }

    private void setFuryAttributes(Player p, int stage, int furyC) {
        AttributeInstance speedAttr = p.getAttribute(Attribute.MOVEMENT_SPEED);
        AttributeInstance attackSpeedAttr = p.getAttribute(Attribute.ATTACK_SPEED);
        AttributeInstance attackDamageAttr = p.getAttribute(Attribute.ATTACK_DAMAGE);

        if (speedAttr == null || attackSpeedAttr == null || attackDamageAttr == null)
            return;

        speedAttr.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(FURY_SPEED))
                .forEach(speedAttr::removeModifier);

        attackSpeedAttr.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(FURY_ATTACK_SPEED))
                .forEach(attackSpeedAttr::removeModifier);

        if (stage <= 0)
            return;

        double speedBonus = 0.02 * stage;
        double attackSpeedBonus = 0.5 * stage;

        AttributeModifier speedMod = new AttributeModifier(
                FURY_SPEED,
                speedBonus,
                AttributeModifier.Operation.ADD_NUMBER);

        AttributeModifier attackSpeedMod = new AttributeModifier(
                FURY_ATTACK_SPEED,
                attackSpeedBonus,
                AttributeModifier.Operation.ADD_NUMBER);

        speedAttr.addModifier(speedMod);
        attackSpeedAttr.addModifier(attackSpeedMod);
    }

    public Titan() {
        super("titan", "Titan", "[Melee Damage Tank]", 2);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(p, "goopshotpeshvp", -183, 7, -185);
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(furyCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int furyC = pdc.get(furyCounter, PersistentDataType.INTEGER);
        int stage = getStage(furyC);
        setFuryAttributes(p, stage, furyC);

        String displayMessage = ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/2";

        String color = ChatColor.WHITE.toString();
        Location loc = p.getLocation().add(0, 1, 0);
        World world = p.getWorld();
        Color dustColor = Color.WHITE;

        switch (stage) {
            case 0:
                color = ChatColor.GRAY.toString();
                dustColor = Color.BLACK;
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
                break;
            case 1:
                color = ChatColor.GREEN.toString();
                dustColor = Color.GREEN;
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 0));
                break;
            case 2:
                color = ChatColor.YELLOW.toString();
                dustColor = Color.YELLOW;
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 1));
                break;
            case 3:
                color = ChatColor.GOLD.toString();
                dustColor = Color.ORANGE;
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 2));
                break;
            case 4:
                color = ChatColor.RED.toString();
                dustColor = Color.RED;
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 3));
                break;
            case 5:
                color = ChatColor.WHITE.toString() + ChatColor.BOLD.toString();
                dustColor = Color.AQUA;
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 4));
                break;
        }

        DustOptions dust = new DustOptions(dustColor, 1.5f);
        world.spawnParticle(Particle.DUST, loc, stage + 1, .2, .2, .2, stage + 1, dust);

        displayMessage = ChatColor.DARK_RED + "Fury: " +
                color + furyC + ChatColor.RESET +
                ChatColor.GRAY + "  |  " + displayMessage;
        p.sendActionBar(displayMessage);

        pdc.set(furyCounter, PersistentDataType.INTEGER, Math.max(furyC - stage - 1, 0));

        if (killstreak >= 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
    }

    @Override
    public void onLeaveCombat(Player p) {
        setFuryAttributes(p, 0, 0);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(furyCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (p.getInventory().getItem(EquipmentSlot.HAND).getType() != Material.STONE_AXE)
            return;
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        int furyC = pdc.get(furyCounter, PersistentDataType.INTEGER);
        int stage = getStage(furyC);
        pdc.set(furyCounter, PersistentDataType.INTEGER, furyC +
                (int) ((p.getFireTicks() > 0 ? 1.5 : 1) * (killstreak > 1 ? 160 : 80)));
        if (stage < 5)
            return;
        e.setDamage(e.getDamage() + (furyC / 500) - 5);
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        pdc.set(furyCounter, PersistentDataType.INTEGER,
                (int) ((p.getFireTicks() > 0 ? 1.5 : 1) * (pdc.get(furyCounter, PersistentDataType.INTEGER)
                        + (int) (e.getDamage() * 4 + 40) * (killstreak > 1 ? 2 : 1))));
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int furyC = pdc.get(furyCounter, PersistentDataType.INTEGER);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, -1, getStage(furyC)));
        pdc.set(furyCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (!e.getAction().isRightClick())
            return;
        if (e.getItem() == null || e.getItem().getType() != Material.STONE_AXE)
            return;
        if (p.hasCooldown(Material.STONE_AXE))
            return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Integer currentFury = pdc.get(furyCounter, PersistentDataType.INTEGER);
        if (currentFury == null)
            currentFury = 0;
        int currentStage = getStage(currentFury);
        double multiplier = (1 + currentStage / 2);

        DustOptions dust = new DustOptions(switch (currentStage) {
            case 0 -> Color.BLACK;
            case 1 -> Color.GREEN;
            case 2 -> Color.YELLOW;
            case 3 -> Color.ORANGE;
            case 4 -> Color.RED;
            case 5 -> Color.AQUA;
            default -> Color.WHITE;
        }, 1.5f);

        int newFury = currentFury / 2;
        pdc.set(furyCounter, PersistentDataType.INTEGER, newFury);
        p.setCooldown(Material.STONE_AXE, (int) (SMASH_COOLDOWN / multiplier));

        final double speed = SMASH_RANGE / SMASH_TICKS * multiplier;
        final double ticks = SMASH_TICKS / multiplier;
        final double angle = SMASH_ANGLE / multiplier;
        final double damage = SMASH_DAMAGE * Math.sqrt(multiplier);

        final Location startLoc = p.getLocation();
        final org.bukkit.util.Vector direction = startLoc.getDirection().setY(0).normalize();
        final World world = p.getWorld();
        final java.util.Set<java.util.UUID> hitEntities = new java.util.HashSet<>();
        world.playSound(startLoc, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f);

        Team pTeam = SnapshotPvpPlugin.getTeam(p);

        new BukkitRunnable() {
            int currentTick = 0;

            @Override
            public void run() {
                if (currentTick > ticks) {
                    this.cancel();
                    return;
                }

                double currentDist = currentTick * speed;

                if (currentDist > 0.5) {
                    for (double angleOffset = -angle / 2; angleOffset <= angle / 2; angleOffset += 5) {
                        Vector particleDir = direction.clone()
                                .rotateAroundY(Math.toRadians(angleOffset));
                        Location particleLoc = startLoc.clone().add(particleDir.multiply(currentDist)).add(0, .1, 0);

                        Block blockUnder = particleLoc.clone().subtract(0, 0.5, 0).getBlock();
                        Material blockMat = blockUnder.getType();

                        if (blockMat.isAir() || !blockMat.isSolid())
                            blockMat = Material.DIRT;
                        BlockData blockData = blockMat.createBlockData();

                        world.spawnParticle(Particle.BLOCK, particleLoc, 2, 0.1, 0.1, 0.1, 0.05, blockData);

                        for (int i = 0; i < 2; i++) {
                            double ranX = (Math.random() - 0.5) * 0.2;
                            double ranZ = (Math.random() - 0.5) * 0.2;
                            double upwardForce = 4 + (Math.random() * 5);

                            world.spawnParticle(
                                    Particle.BLOCK,
                                    particleLoc.clone().add(0, 0.2, 0),
                                    0,
                                    ranX,
                                    upwardForce,
                                    ranZ,
                                    1,
                                    blockData);
                        }

                        world.spawnParticle(Particle.DUST, particleLoc, currentStage + 1, .2, .2, .2, currentStage + 1,
                                dust);
                    }
                }

                for (Entity entity : world.getNearbyEntities(startLoc, SMASH_RANGE, SMASH_RANGE,
                        SMASH_RANGE)) {
                    if (!(entity instanceof LivingEntity target))
                        continue;
                    if (target.equals(p) || pTeam.equals(SnapshotPvpPlugin.getTeamG(target)))
                        continue;
                    if (hitEntities.contains(target.getUniqueId()))
                        continue;

                    Location targetLoc = target.getLocation();

                    double yDiff = targetLoc.getY() - startLoc.getY();
                    if (yDiff > 2.0 || yDiff < -2.0)
                        continue;

                    double distToTarget = startLoc.distance(targetLoc);
                    if (distToTarget <= currentDist) {
                        Vector targetDir = targetLoc.toVector().subtract(startLoc.toVector()).setY(0)
                                .normalize();
                        double ang = direction.angle(targetDir);

                        if (ang <= Math.toRadians(angle / 2)) {
                            hitEntities.add(target.getUniqueId());

                            target.addPotionEffect(
                                    new PotionEffect(PotionEffectType.JUMP_BOOST, 80 + (currentStage * 20), 255));
                            target.addPotionEffect(
                                    new PotionEffect(PotionEffectType.SLOWNESS, 80 + (currentStage * 20), 5));

                            target.damage(damage, p);

                            world.playSound(targetLoc, org.bukkit.Sound.BLOCK_ANVIL_PLACE, 0.5f, 0.8f);
                        }
                    }
                }

                currentTick++;
            }
        }.runTaskTimer(SnapshotPvpPlugin.instance, 0L, 1L);
    }
}
