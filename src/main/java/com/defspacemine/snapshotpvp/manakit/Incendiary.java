package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public class Incendiary extends ManaKit {
    final double SIPHON_RADIUS = 24;
    private static final int HEAT_PER_TICK = 15;
    private static final int COOL_RATE = 2;
    private static final int MAX_RANGE = 8;

    final int flamethrower = 2000; // max ammo
    final NamespacedKey flamethrowerCounter = ManaKitListener.MANA_KIT_DATA0;
    private static final int maxHeat = 1800;
    final NamespacedKey heatKey = ManaKitListener.MANA_KIT_DATA1;
    private static final int maxRamp = 60;
    final NamespacedKey rampKey = ManaKitListener.MANA_KIT_DATA2;
    final NamespacedKey overheatKey = ManaKitListener.MANA_KIT_DATA3;

    public Incendiary() {
        super("incendiary", "Incendiary", "[Utility Attrition]", 3);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(p, "goopshotpeshvp", -186, 4, -185);
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(flamethrowerCounter, PersistentDataType.INTEGER, 0);
        pdc.set(heatKey, PersistentDataType.INTEGER, 0);
        pdc.set(rampKey, PersistentDataType.INTEGER, 0);
        pdc.set(overheatKey, PersistentDataType.BOOLEAN, false);
    }

    @Override
    public void onCombatTick(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!isFlamethrower(item) || !p.isHandRaised()) {
            pdc.set(rampKey, PersistentDataType.INTEGER, Math.max(0,
                    pdc.getOrDefault(rampKey, PersistentDataType.INTEGER, 0) - 1));
            cool(p);
        } else
            fireTick(p);

        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        int flamethrowerC = pdc.get(flamethrowerCounter, PersistentDataType.INTEGER);
        int heatC = pdc.get(heatKey, PersistentDataType.INTEGER);
        int rampC = pdc.get(rampKey, PersistentDataType.INTEGER);
        boolean overheatC = pdc.get(overheatKey, PersistentDataType.BOOLEAN);

        p.sendActionBar(ChatColor.GOLD + "Flamethrower: " +
                ChatColor.WHITE + flamethrowerC + "/" + flamethrower +
                ChatColor.GRAY + "  |  " +
                ChatColor.YELLOW + "Heat: " +
                ChatColor.WHITE + heatC + "/" + maxHeat +
                ChatColor.GRAY + "  |  " +
                (overheatC ? ChatColor.RED + "" + ChatColor.BOLD + "OVERHEATED!" + ChatColor.RESET
                        : ChatColor.LIGHT_PURPLE + "Damage Ramp: " + ChatColor.WHITE + rampC + "/" + maxRamp)
                +
                ChatColor.GRAY + "  |  " +
                ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/2");

        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0));
        if (killstreak >= 1 && !p.hasPotionEffect(PotionEffectType.REGENERATION))
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, -1, 1));
        if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));

        PlayerInventory inv = p.getInventory();
        if (flamethrowerC >= flamethrower) {
            pdc.set(flamethrowerCounter, PersistentDataType.INTEGER, flamethrower);
            return;
        }

        if (p.getHealth() <= 0)
            return;

        int newFlamethrowerC = flamethrowerC;
        Team pTeam = SnapshotPvpPlugin.scoreboard.getEntryTeam(p.getName());
        for (Player target : p.getLocation().getNearbyPlayers(SIPHON_RADIUS)) {
            if (target.getFireTicks() <= 0)
                continue;

            Team targetTeam = SnapshotPvpPlugin.scoreboard.getEntryTeam(target.getName());
            if (pTeam != null && pTeam.equals(targetTeam))
                continue;

            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
            target.setExhaustion(target.getExhaustion() + .1f);
            p.setSaturation(Math.min(20, p.getSaturation() + 1));
            p.setFoodLevel(Math.min(20, p.getFoodLevel() + 1));
            p.setHealth(Math.min(20, p.getHealth() + .05));
            newFlamethrowerC += 2;
        }
        if (p.getFireTicks() > 0) {
            p.setSaturation(Math.min(20, p.getSaturation() + 1));
            p.setFoodLevel(Math.min(20, p.getFoodLevel() + 1));
            p.setHealth(Math.min(20, p.getHealth() + .05));

            p.setHealth(p.getHealth() * (.98));
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1));
        }
        pdc.set(flamethrowerCounter, PersistentDataType.INTEGER, newFlamethrowerC);
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(flamethrowerCounter, PersistentDataType.INTEGER, 0);
        pdc.set(heatKey, PersistentDataType.INTEGER, 0);
        pdc.set(rampKey, PersistentDataType.INTEGER, 0);
        pdc.set(overheatKey, PersistentDataType.BOOLEAN, false);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(heatKey, PersistentDataType.INTEGER, 0);
        pdc.set(overheatKey, PersistentDataType.BOOLEAN, false);
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (!e.getAction().isRightClick())
            return;

        ItemStack item = p.getInventory().getItemInMainHand();

        if (!isFlamethrower(item))
            return;

        p.getPersistentDataContainer()
                .set(overheatKey, PersistentDataType.BOOLEAN,
                        p.getPersistentDataContainer()
                                .getOrDefault(overheatKey, PersistentDataType.BOOLEAN, false));
    }

    private void fireTick(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int flamethrowerC = pdc.get(flamethrowerCounter, PersistentDataType.INTEGER);
        if (flamethrowerC <= 0)
            return;

        int heat = pdc.getOrDefault(heatKey, PersistentDataType.INTEGER, 0);
        int ramp = pdc.getOrDefault(rampKey, PersistentDataType.INTEGER, 0);
        boolean overheated = pdc.getOrDefault(overheatKey, PersistentDataType.BOOLEAN, false);

        if (overheated) {
            cool(player);
            return;
        }

        heat += HEAT_PER_TICK;
        pdc.set(heatKey, PersistentDataType.INTEGER, heat);

        if (ramp < maxRamp) {
            ramp++;
            pdc.set(rampKey, PersistentDataType.INTEGER, ramp);
        }

        if (heat >= maxHeat) {
            triggerOverheat(player);
            return;
        }

        double damage = 0.05 + (ramp * 0.005);

        World world = player.getWorld();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location start = player.getEyeLocation();
        Team pTeam = SnapshotPvpPlugin.scoreboard.getEntryTeam(player.getName());

        main: for (double i = 0; i < (MAX_RANGE * ramp / maxRamp); i += 0.5) {
            if (flamethrowerC <= 0) {
                ramp -= 2;
                pdc.set(rampKey, PersistentDataType.INTEGER, Math.max(0, ramp));
                break main;
            }

            double r = i / 2 + 1;
            Location point = start.clone().add(direction.clone().multiply(i));
            world.spawnParticle(Particle.FLAME, point, (int) r, 0.2 * r, 0.2 * r, 0.2 * r, 0.02);
            flamethrowerC -= 1;

            for (Entity entity : world.getNearbyEntities(point, r, r, r)) {
                if (flamethrowerC <= 0) {
                    ramp -= 2;
                    pdc.set(rampKey, PersistentDataType.INTEGER, Math.max(0, ramp));
                    break main;
                }
                if (entity.equals(player))
                    continue;

                Team entityTeam = SnapshotPvpPlugin.scoreboard.getEntryTeam(entity.getName());
                if (pTeam != null && pTeam.equals(entityTeam))
                    continue;

                entity.setFireTicks(40 + ramp);
                if (entity instanceof LivingEntity living)
                    living.damage(damage, DamageSource.builder(DamageType.IN_FIRE)
                            .withDirectEntity(player)
                            .withCausingEntity(player).build());
            }
        }

        pdc.set(flamethrowerCounter, PersistentDataType.INTEGER, Math.max(0, flamethrowerC));
    }

    private void triggerOverheat(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(overheatKey, PersistentDataType.BOOLEAN, true);
        pdc.set(rampKey, PersistentDataType.INTEGER, 0);

        // player.sendMessage("§c§lFLAMETHROWER OVERHEATED!");
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.5f);
        player.setFireTicks(60);
        player.damage(15.0);
    }

    private void cool(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        int heat = pdc.getOrDefault(heatKey, PersistentDataType.INTEGER, 0);
        boolean overheated = pdc.getOrDefault(overheatKey, PersistentDataType.BOOLEAN, false);

        if (heat <= 0) {
            pdc.set(heatKey, PersistentDataType.INTEGER, 0);
            pdc.set(overheatKey, PersistentDataType.BOOLEAN, false);
        } else {
            heat -= COOL_RATE;
            pdc.set(heatKey, PersistentDataType.INTEGER, heat);
        }
    }

    private boolean isFlamethrower(ItemStack item) {
        return item != null && item.getType() == Material.FLINT_AND_STEEL;
    }
}
