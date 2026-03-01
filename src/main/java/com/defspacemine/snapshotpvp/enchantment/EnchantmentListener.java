package com.defspacemine.snapshotpvp.enchantment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class EnchantmentListener implements Listener {
    public static Enchantment EXPLOSIVE_HOOK;
    public static Enchantment TRIPLE_JUMP;
    public static Enchantment BREEZY_ASPECT;
    public static Enchantment LIGHTNING_ASPECT;
    public static Enchantment DOUBLE_JUMP;
    public static Enchantment CURSE_HEAVY_HIT;

    private final JavaPlugin plugin;

    private final Map<UUID, Integer> jumpCount = new HashMap<>();
    private final Map<UUID, BukkitTask> groundCheck = new HashMap<>();

    public EnchantmentListener(JavaPlugin plugin) {
        this.plugin = plugin;

        EXPLOSIVE_HOOK = Registry.ENCHANTMENT.get(new NamespacedKey("defspacemine", "explosive_hook"));
        TRIPLE_JUMP = Registry.ENCHANTMENT.get(new NamespacedKey("defspacemine", "triple_jump"));
        BREEZY_ASPECT = Registry.ENCHANTMENT.get(new NamespacedKey("defspacemine", "breezy_aspect"));
        LIGHTNING_ASPECT = Registry.ENCHANTMENT.get(new NamespacedKey("defspacemine", "lightning_aspect"));
        DOUBLE_JUMP = Registry.ENCHANTMENT.get(new NamespacedKey("defspacemine", "double_jump"));
        CURSE_HEAVY_HIT = Registry.ENCHANTMENT.get(new NamespacedKey("defspacemine", "curse_heavy_hit"));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();

        if (hasTripleJump(p) || hasDoubleJump(p)) {
            if (p.isOnGround()) {
                jumpCount.put(p.getUniqueId(), 0);
                p.setAllowFlight(true);
            }
        } else if (p.getGameMode() == GameMode.ADVENTURE || p.getGameMode() == GameMode.SURVIVAL)
            p.setAllowFlight(false);
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        Player player = e.getPlayer();
        ItemStack rod = player.getInventory().getItem(e.getHand());

        if (rod == null || rod.getType() != Material.FISHING_ROD)
            return;

        if (!rod.containsEnchantment(EXPLOSIVE_HOOK))
            return;

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

                if (enabled)
                    explosiveHook(player, hook.getLocation(), level);

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
        Creeper creeper = (Creeper) loc.getWorld().spawnEntity(loc, EntityType.CREEPER);
        CustomEggListener.injectOwner(creeper, p);
        creeper.customName(Component.text("Explosive Hook")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        creeper.setExplosionRadius(level);
        creeper.explode();
    }

    @EventHandler
    public void onFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();

        if (hasTripleJump(p))
            tripleJump(p, e);

        if (hasDoubleJump(p))
            doubleJump(p, e);
    }

    private boolean hasTripleJump(Player p) {
        ItemStack boots = p.getInventory().getBoots();
        return boots != null && boots.containsEnchantment(TRIPLE_JUMP);
    }

    private boolean hasDoubleJump(Player p) {
        ItemStack boots = p.getInventory().getBoots();
        return boots != null && boots.containsEnchantment(DOUBLE_JUMP);
    }

    private void tripleJump(Player p, PlayerToggleFlightEvent e) {
        if (p.getGameMode() == GameMode.CREATIVE)
            return;

        e.setCancelled(true);
        p.setFlying(false);

        int jumps = jumpCount.getOrDefault(p.getUniqueId(), 0);

        if (jumps >= 2) {
            p.setAllowFlight(false);
            return;
        }

        jumpCount.put(p.getUniqueId(), jumps + 1);

        Vector direction = p.getLocation().getDirection().normalize();
        Vector boost = direction.multiply(0.6);
        boost.setY(0.9);

        p.setVelocity(p.getVelocity().add(boost));

        p.getWorld().spawnParticle(
                Particle.GUST,
                p.getLocation(),
                10,
                0.2, 0.2, 0.2,
                0);

        p.getWorld().playSound(
                p.getLocation(),
                Sound.ENTITY_WIND_CHARGE_THROW,
                1f,
                1f);

        WindCharge charge = (WindCharge) p.getWorld().spawnEntity(
                p.getLocation().subtract(0, 1, 0),
                EntityType.WIND_CHARGE);

        charge.setShooter(p);
        charge.setVelocity(new Vector(0, -1, 0));
    }

    private void doubleJump(Player p, PlayerToggleFlightEvent e) {
        if (p.getGameMode() == GameMode.CREATIVE)
            return;

        e.setCancelled(true);
        p.setFlying(false);

        int jumps = jumpCount.getOrDefault(p.getUniqueId(), 0);

        if (jumps >= 1) {
            p.setAllowFlight(false);
            return;
        }

        jumpCount.put(p.getUniqueId(), jumps + 1);

        Location loc = p.getLocation();
        Creeper creeper = (Creeper) loc.getWorld().spawnEntity(loc, EntityType.CREEPER);
        // CustomEggListener.injectOwner(creeper, p);

        creeper.customName(
                Component.text("Double Jump", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .append(Component.text(" ...wait what?", NamedTextColor.WHITE)));
        creeper.setCustomNameVisible(true);
        creeper.setMaxFuseTicks(0);
        creeper.setExplosionRadius(-1);
        creeper.setIgnited(true);
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamageSource().getDamageType() != DamageType.PLAYER_ATTACK)
            return;

        if (!(e.getDamager() instanceof Player player))
            return;

        if (!(e.getEntity() instanceof LivingEntity target))
            return;

        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (weapon == null)
            return;

        if (weapon.containsEnchantment(BREEZY_ASPECT)) {
            int level = weapon.getEnchantmentLevel(BREEZY_ASPECT);
            spawnBreezeBurst(player, target, level);
        }

        if (weapon.containsEnchantment(LIGHTNING_ASPECT)) {
            int level = weapon.getEnchantmentLevel(LIGHTNING_ASPECT);
            spawnLightningAttack(player, target, level);
        }

        if (weapon.containsEnchantment(CURSE_HEAVY_HIT)) {
            int level = weapon.getEnchantmentLevel(CURSE_HEAVY_HIT);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)e.getFinalDamage() * 4 * level, 4));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, (int)e.getFinalDamage() * 2 * level, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, (int)e.getFinalDamage() * 2 * (level + 1), 255));
        }
    }

    private void spawnBreezeBurst(Player player, LivingEntity target, int level) {
        Location spawnLoc = player.getEyeLocation();
        Vector baseDir = spawnLoc.getDirection().normalize();

        int count = 2 + level;

        for (int i = 0; i < count; i++) {
            WindCharge charge = (WindCharge) player.getWorld().spawnEntity(
                    spawnLoc,
                    EntityType.WIND_CHARGE);

            charge.setShooter(player);

            Vector spread = baseDir.clone().add(new Vector(
                    (Math.random() - 0.5) * 0.3,
                    (Math.random() - 0.5) * 0.2,
                    (Math.random() - 0.5) * 0.3)).normalize();

            charge.setVelocity(spread.multiply(1.6));
        }
    }

    private void spawnLightningAttack(Player p, LivingEntity target, int level) {
        World world = p.getWorld();
        Location spawnLoc = target.getLocation();
        double radius = 4 + 2 * level;

        Team pTeam = SnapshotPvpPlugin.scoreboard.getEntryTeam(p.getName());
        for (Entity t : world.getNearbyEntities(target.getLocation(), radius, radius, radius)) {
            if (target instanceof Player player)
                if (player.equals(p) || player.getGameMode() != GameMode.ADVENTURE)
                    continue;

            Team tTeam = SnapshotPvpPlugin.scoreboard.getEntryTeam(t.getName());
            if (pTeam != null && pTeam.equals(tTeam))
                continue;

            LightningStrike strike = world.strikeLightning(t.getLocation());
            strike.setCausingPlayer(p);

            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.GLOWING,
                    600 * (level - 1),
                    0,
                    false,
                    true,
                    true));
            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.WEAKNESS,
                    100 * (level - 1),
                    0,
                    false,
                    true,
                    true));
        }
    }
}
