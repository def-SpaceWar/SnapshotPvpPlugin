package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Warden;
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
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class SculkWarrior extends ManaKit {

    private static final double SONIC_BOOM_RANGE = 15.0;
    private static final double SONIC_BOOM_DAMAGE = 10.0;
    private static final int SONIC_BOOM_CHARGE_TICKS = 30;
    private static final int MAX_ECHO_SHARDS = 5;

    private static final int sonicBoomShard = 5; // 5 attacks to get a sonic boom
    final NamespacedKey sonicBoomShardCounter = ManaKitListener.MANA_KIT_DATA0;
    final NamespacedKey isChargingKey = ManaKitListener.MANA_KIT_DATABOOL0;
    final NamespacedKey sculkWardenUUID = ManaKitListener.MANA_KIT_DATASTR0;
    final int shriekerRestock = 3; // 3 kills = warden
    final NamespacedKey shriekerRestockCounter = ManaKitListener.MANA_KIT_DATA1;

    public SculkWarrior() {
        super("sculk_warrior", "Sculk Warrior", "[Melee Assassin]", 0);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -186,
            10,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(sonicBoomShardCounter, PersistentDataType.INTEGER, 0);
        pdc.set(isChargingKey, PersistentDataType.BOOLEAN, false);
        pdc.set(sculkWardenUUID, PersistentDataType.STRING, "");
        pdc.set(shriekerRestockCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(sonicBoomShardCounter, PersistentDataType.INTEGER, 0);
        pdc.set(isChargingKey, PersistentDataType.BOOLEAN, false);
        pdc.set(sculkWardenUUID, PersistentDataType.STRING, "");
        pdc.set(shriekerRestockCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onDeath(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(isChargingKey, PersistentDataType.BOOLEAN, false);

        Entity warden = getEntityFromPDC(pdc, sculkWardenUUID);
        if (warden != null) warden.remove();
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.ECHO_SHARD);
        SnapshotPvpPlugin.clearInv(inv, Material.SCULK_SHRIEKER);

        p.clearActivePotionEffects();
        resetKit(p);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int sonicBoomShardC = pdc.getOrDefault(
            sonicBoomShardCounter,
            PersistentDataType.INTEGER,
            0
        );
        int shriekerRestockC = pdc.getOrDefault(
            shriekerRestockCounter,
            PersistentDataType.INTEGER,
            0
        );

        p.sendActionBar(
            ChatColor.DARK_AQUA +
                "Sonic Charge: " +
                ChatColor.WHITE +
                Math.min(sonicBoomShardC, sonicBoomShard) +
                "/" +
                sonicBoomShard +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.YELLOW +
                "Shrieker: " +
                ChatColor.WHITE +
                shriekerRestockC +
                "/" +
                shriekerRestock +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.RED +
                "Killstreak: " +
                ChatColor.WHITE +
                killstreak +
                "/3"
        );

        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.RESISTANCE, 40, 0)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.HASTE, 40, 1)
        );
        if (killstreak >= 3) p.addPotionEffect(
            new PotionEffect(PotionEffectType.STRENGTH, 40, 1)
        );

        PlayerInventory inv = p.getInventory();
        if (sonicBoomShardC >= sonicBoomShard) {
            pdc.set(
                sonicBoomShardCounter,
                PersistentDataType.INTEGER,
                sonicBoomShardC - sonicBoomShard
            );
            if (countEchoShards(p) < MAX_ECHO_SHARDS) inv.addItem(
                getEchoShard()
            );
        }

        if (shriekerRestockC >= shriekerRestock) {
            pdc.set(
                shriekerRestockCounter,
                PersistentDataType.INTEGER,
                shriekerRestockC - shriekerRestock
            );
            SnapshotPvpPlugin.clearInv(inv, Material.SCULK_SHRIEKER);
            inv.addItem(getSculkShrieker());
        }
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        if (
            pdc.getOrDefault(isChargingKey, PersistentDataType.BOOLEAN, false)
        ) {
            e.setCancelled(true);
            return;
        }

        if (
            e.getAction() != Action.RIGHT_CLICK_BLOCK &&
            e.getAction() != Action.RIGHT_CLICK_AIR
        ) return;

        ItemStack item = e.getItem();
        Material material = e.getMaterial();
        if (item == null || material == null) return;

        if (material.equals(Material.NETHERITE_SWORD)) {
            e.setCancelled(true);

            if (p.hasCooldown(Material.NETHERITE_SWORD)) return;
            p.setCooldown(Material.NETHERITE_SWORD, 30 * 20);

            p.addPotionEffect(
                new PotionEffect(
                    PotionEffectType.BLINDNESS,
                    10 * 20,
                    0,
                    false,
                    true,
                    true
                )
            );
            p.playSound(
                p.getLocation(),
                Sound.ENTITY_WARDEN_HEARTBEAT,
                1.0f,
                0.8f
            );

            Team userTeam = SnapshotPvpPlugin.getTeam(p);

            for (Player target : p.getWorld().getPlayers()) {
                if (
                    target.equals(p) ||
                    target.getGameMode() == GameMode.SPECTATOR
                ) continue;

                Team targetTeam = SnapshotPvpPlugin.getTeam(target);
                if (userTeam == null || !userTeam.equals(targetTeam)) {
                    target.addPotionEffect(
                        new PotionEffect(
                            PotionEffectType.GLOWING,
                            10 * 20,
                            0,
                            false,
                            true,
                            true
                        )
                    );
                    target.addPotionEffect(
                        new PotionEffect(
                            PotionEffectType.BLINDNESS,
                            10 * 20,
                            0,
                            false,
                            true,
                            true
                        )
                    );
                }
            }
        } else if (material.equals(Material.ECHO_SHARD)) {
            e.setCancelled(true);

            if (p.getGameMode() != GameMode.CREATIVE) {
                item.setAmount(item.getAmount() - 1);
            }

            performSonicBoom(p);
        } else if (material.equals(Material.SCULK_SHRIEKER)) {
            e.setCancelled(true);

            Location spawnLoc =
                e.getClickedBlock() != null && e.getBlockFace() != null
                    ? e
                          .getClickedBlock()
                          .getRelative(e.getBlockFace())
                          .getLocation()
                          .add(0.5, 0, 0.5)
                    : p.getLocation();

            spawnWarden(p, spawnLoc);

            if (p.getGameMode() != GameMode.CREATIVE) {
                item.setAmount(item.getAmount() - 1);
            }
        }
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2));
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        if (
            pdc.getOrDefault(isChargingKey, PersistentDataType.BOOLEAN, false)
        ) {
            e.setCancelled(true);
            return;
        }

        if (e.getEntity() instanceof Player) {
            pdc.set(
                sonicBoomShardCounter,
                PersistentDataType.INTEGER,
                pdc.getOrDefault(
                    sonicBoomShardCounter,
                    PersistentDataType.INTEGER,
                    0
                ) + 1
            );
        }
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int shriekerRestockC = pdc.get(
            shriekerRestockCounter,
            PersistentDataType.INTEGER
        );
        pdc.set(
            shriekerRestockCounter,
            PersistentDataType.INTEGER,
            shriekerRestockC + 1
        );
    }

    private void spawnWarden(Player owner, Location loc) {
        PersistentDataContainer pdc = owner.getPersistentDataContainer();
        Entity oldWarden = getEntityFromPDC(pdc, sculkWardenUUID);
        if (oldWarden != null) {
            oldWarden.remove();
        }

        Warden warden = (Warden) loc
            .getWorld()
            .spawnEntity(loc, EntityType.WARDEN);
        warden.setPose(Pose.EMERGING);

        CustomEggListener.injectOwner(warden, owner);
        SnapshotPvpPlugin.addToTeam(owner, warden);
        warden.addPotionEffect(
            new PotionEffect(PotionEffectType.GLOWING, -1, 0)
        );

        pdc.set(
            sculkWardenUUID,
            PersistentDataType.STRING,
            warden.getUniqueId().toString()
        );
        loc.getWorld().playSound(
            loc,
            Sound.BLOCK_SCULK_SHRIEKER_SHRIEK,
            1.0f,
            0.7f
        );
    }

    private void performSonicBoom(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(isChargingKey, PersistentDataType.BOOLEAN, true);

        final float lockedYaw = player.getLocation().getYaw();
        final float lockedPitch = player.getLocation().getPitch();

        //  player.addPotionEffect(
        //      new PotionEffect(
        //          PotionEffectType.SLOWNESS,
        //          SONIC_BOOM_CHARGE_TICKS + 5,
        //          255,
        //          false,
        //          false,
        //          false
        //      )
        //  );
        //  player.addPotionEffect(
        //      new PotionEffect(
        //          PotionEffectType.JUMP_BOOST,
        //          SONIC_BOOM_CHARGE_TICKS + 5,
        //          255,
        //          false,
        //          false,
        //          false
        //      )
        //  );
        player
            .getWorld()
            .playSound(
                player.getLocation(),
                Sound.ENTITY_WARDEN_SONIC_CHARGE,
                1.2f,
                1.0f
            );

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    pdc.set(isChargingKey, PersistentDataType.BOOLEAN, false);
                    cancel();
                    return;
                }

                Location currentLoc = player.getLocation();
                if (
                    currentLoc.getYaw() != lockedYaw ||
                    currentLoc.getPitch() != lockedPitch
                ) {
                    currentLoc.setYaw(lockedYaw);
                    currentLoc.setPitch(lockedPitch);
                    player.teleport(currentLoc);
                }

                Location center = player.getLocation().add(0, 1.0, 0);

                player
                    .getWorld()
                    .spawnParticle(
                        Particle.SCULK_SOUL,
                        center,
                        4,
                        0.3,
                        0.4,
                        0.3,
                        0.02
                    );
                player
                    .getWorld()
                    .spawnParticle(
                        Particle.SCULK_CHARGE_POP,
                        center,
                        2,
                        0.2,
                        0.3,
                        0.2,
                        0.01
                    );

                if (ticks == 15) {
                    player
                        .getWorld()
                        .playSound(
                            player.getLocation(),
                            Sound.ENTITY_WARDEN_HEARTBEAT,
                            1.1f,
                            0.7f
                        );
                }

                ticks++;

                if (ticks >= SONIC_BOOM_CHARGE_TICKS) {
                    pdc.set(isChargingKey, PersistentDataType.BOOLEAN, false);
                    player.removePotionEffect(PotionEffectType.SLOWNESS);
                    player.removePotionEffect(PotionEffectType.JUMP_BOOST);
                    player.removePotionEffect(PotionEffectType.MINING_FATIGUE);

                    fireSonicBoomBeam(player, lockedYaw, lockedPitch);
                    cancel();
                }
            }
        }.runTaskTimer(
            SnapshotPvpPlugin.getPlugin(SnapshotPvpPlugin.class),
            0L,
            1L
        );
    }

    private void fireSonicBoomBeam(Player player, float yaw, float pitch) {
        Location eyeLoc = player.getEyeLocation();
        eyeLoc.setYaw(yaw);
        eyeLoc.setPitch(pitch);
        Vector direction = eyeLoc.getDirection().normalize();

        player
            .getWorld()
            .playSound(eyeLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.0f);

        for (double d = 0; d < SONIC_BOOM_RANGE; d += 0.5) {
            Location point = eyeLoc.clone().add(direction.clone().multiply(d));
            player
                .getWorld()
                .spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
        }

        Location midPoint = eyeLoc
            .clone()
            .add(direction.clone().multiply(SONIC_BOOM_RANGE / 2.0));
        double searchRadius = SONIC_BOOM_RANGE / 2.0 + 2.0;
        Team userTeam = SnapshotPvpPlugin.getTeam(player);
        Vector startVec = eyeLoc.toVector();

        for (Entity entity : player
            .getWorld()
            .getNearbyEntities(
                midPoint,
                searchRadius,
                searchRadius,
                searchRadius
            )) {
            if (
                !(entity instanceof LivingEntity target) ||
                entity.equals(player)
            ) {
                continue;
            }

            if (
                target
                    .getBoundingBox()
                    .expand(0.5)
                    .rayTrace(startVec, direction, SONIC_BOOM_RANGE) != null
            ) {
                Team targetTeam = SnapshotPvpPlugin.getTeamE(target);

                if (userTeam == null || !userTeam.equals(targetTeam)) {
                    target.damage(
                        SONIC_BOOM_DAMAGE,
                        DamageSource.builder(DamageType.SONIC_BOOM)
                            .withCausingEntity(player)
                            .withDirectEntity(player)
                            .build()
                    );

                    Vector knockback = direction
                        .clone()
                        .multiply(1.25)
                        .setY(0.4);
                    target.setVelocity(target.getVelocity().add(knockback));
                }
            }
        }
    }

    private int countEchoShards(Player p) {
        int count = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == Material.ECHO_SHARD) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private Entity getEntityFromPDC(
        PersistentDataContainer pdc,
        NamespacedKey key
    ) {
        if (!pdc.has(key, PersistentDataType.STRING)) return null;
        try {
            UUID id = UUID.fromString(pdc.get(key, PersistentDataType.STRING));
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid()) return null;
            return e;
        } catch (Exception ex) {
            return null;
        }
    }

    public static ItemStack getEchoShard() {
        ItemStack shard = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = shard.getItemMeta();
        meta.displayName(
            Component.text(
                "Sonic Boom",
                NamedTextColor.AQUA,
                TextDecoration.BOLD
            )
        );
        meta.lore(
            List.of(
                Component.text(
                    "Right-click to unleash a Warden Sonic Boom.",
                    NamedTextColor.GRAY
                ),
                Component.text(
                    "Deals 10 piercing damage up to 15 blocks.",
                    NamedTextColor.DARK_AQUA
                )
            )
        );
        meta.addEnchant(Enchantment.VANISHING_CURSE, 0, false);
        shard.setItemMeta(meta);
        return shard;
    }

    public static ItemStack getSculkShrieker() {
        ItemStack shrieker = new ItemStack(Material.SCULK_SHRIEKER);
        ItemMeta meta = shrieker.getItemMeta();
        meta.displayName(
            Component.text(
                "Summoning Shrieker",
                NamedTextColor.DARK_GREEN,
                TextDecoration.BOLD
            )
        );
        meta.lore(
            List.of(
                Component.text(
                    "Right-click to summon an old friend.",
                    NamedTextColor.GRAY
                )
            )
        );
        meta.addEnchant(Enchantment.VANISHING_CURSE, 0, false);
        shrieker.setItemMeta(meta);
        return shrieker;
    }
}
