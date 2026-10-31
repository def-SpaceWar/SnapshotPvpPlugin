package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ZombieHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class Hacker extends ManaKit implements Listener {

    private static final double TROJAN_SEARCH_RADIUS = 32;

    private final NamespacedKey attackCountKey = ManaKitListener.MANA_KIT_DATA0;
    private final NamespacedKey flightExpiryKey =
        ManaKitListener.MANA_KIT_DATA1;
    private final NamespacedKey rewindItemKey = ManaKitListener.MANA_KIT_DATA2;
    private final NamespacedKey flightItemKey = ManaKitListener.MANA_KIT_DATA3;
    private final NamespacedKey flightAttackCountKey =
        ManaKitListener.MANA_KIT_DATA4;
    private final NamespacedKey trojanAttackCountKey =
        ManaKitListener.MANA_KIT_DATA5;
    private final NamespacedKey trojanItemKey = ManaKitListener.MANA_KIT_DATA6;

    // Stores 5 seconds (100 ticks) of historical states per player
    private final Map<UUID, Deque<PlayerSnapshot>> historyMap = new HashMap<>();

    private record PlayerSnapshot(
        Location location,
        Vector velocity,
        double health,
        boolean isFlying,
        int flightExpiry,
        int attackCount,
        int flightAttackCount,
        int trojanAttackCount,
        boolean hadFlightItem,
        boolean hadTrojanItem
    ) {}

    public Hacker() {
        super("hacker", "Hacker", "[Melee Assassin]", 0);
        SnapshotPvpPlugin plugin = SnapshotPvpPlugin.getPlugin(
            SnapshotPvpPlugin.class
        );
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -184,
            13,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(attackCountKey, PersistentDataType.INTEGER, 0);
        pdc.set(flightAttackCountKey, PersistentDataType.INTEGER, 0);
        pdc.set(trojanAttackCountKey, PersistentDataType.INTEGER, 0);
        pdc.set(flightExpiryKey, PersistentDataType.INTEGER, 0);

        // Remove creative flight and reset fall distance so it doesn't bleed into other kits
        if (p.getGameMode() != GameMode.CREATIVE) {
            p.setAllowFlight(false);
            p.setFlying(false);
        }
        p.setFallDistance(0.0f);

        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.CLOCK);
        SnapshotPvpPlugin.clearInv(inv, Material.FEATHER);
        SnapshotPvpPlugin.clearInv(inv, Material.ZOMBIE_HORSE_SPAWN_EGG);

        historyMap.remove(p.getUniqueId());
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        int currentTick = (int) p.getWorld().getGameTime();
        int attackCount = pdc.getOrDefault(
            attackCountKey,
            PersistentDataType.INTEGER,
            0
        );
        int flightAttackCount = pdc.getOrDefault(
            flightAttackCountKey,
            PersistentDataType.INTEGER,
            0
        );
        int trojanAttackCount = pdc.getOrDefault(
            trojanAttackCountKey,
            PersistentDataType.INTEGER,
            0
        );
        int flightExpiry = pdc.getOrDefault(
            flightExpiryKey,
            PersistentDataType.INTEGER,
            0
        );

        // 1. Record snapshot for 5-second rewind history (100 ticks max)
        Deque<PlayerSnapshot> history = historyMap.computeIfAbsent(
            p.getUniqueId(),
            k -> new ArrayDeque<>()
        );
        history.addLast(
            new PlayerSnapshot(
                p.getLocation().clone(),
                p.getVelocity().clone(),
                p.getHealth(),
                p.isFlying(),
                flightExpiry,
                attackCount,
                flightAttackCount,
                trojanAttackCount,
                hasFlightItemInInventory(p),
                hasTrojanItemInInventory(p)
            )
        );
        if (history.size() > 100) {
            history.removeFirst();
        }

        // 2. Manage Creative Flight Expiry
        boolean isFlyingActive = flightExpiry > currentTick;
        if (isFlyingActive) {
            if (!p.getAllowFlight()) {
                p.setAllowFlight(true);
            }

            p.getWorld().spawnParticle(
                Particle.END_ROD,
                p.getLocation().add(0, 0.1, 0),
                2,
                0.2,
                0.1,
                0.2,
                0.01
            );
        } else {
            if (
                p.getAllowFlight() &&
                p.getGameMode() != org.bukkit.GameMode.CREATIVE
            ) {
                p.setAllowFlight(false);
                p.setFlying(false);
            }
        }

        // 3. Action bar display
        boolean hasFlightItem = hasFlightItemInInventory(p);
        boolean hasTrojanItem = hasTrojanItemInInventory(p);

        String flightStatus =
            (isFlyingActive
                ? ChatColor.AQUA +
                  "Flight: " +
                  ChatColor.WHITE +
                  (flightExpiry - currentTick) +
                  ChatColor.GRAY +
                  "  |  "
                : "") +
            (hasFlightItem
                ? ChatColor.GRAY + "Fly: " + ChatColor.GREEN + "READY"
                : ChatColor.GRAY +
                  "Fly: " +
                  ChatColor.WHITE +
                  flightAttackCount +
                  "/15");

        String trojanStatus = hasTrojanItem
            ? ChatColor.GREEN + "READY"
            : ChatColor.WHITE + "" + trojanAttackCount + "/7";

        boolean hasRewindItem = hasRewindItemInInventory(p);
        String rewindStatus = hasRewindItem
            ? ChatColor.GREEN + "READY"
            : ChatColor.WHITE + "" + attackCount + "/10";

        p.sendActionBar(
            flightStatus +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.DARK_RED +
                "Trojan: " +
                trojanStatus +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.GOLD +
                "Rewind: " +
                rewindStatus +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.RED +
                "Killstreak: " +
                ChatColor.WHITE +
                killstreak +
                "/2"
        );

        p.addPotionEffect(
            new PotionEffect(PotionEffectType.SATURATION, 100, 0)
        );
        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.HASTE, 100, 0)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0)
        );
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) {
            handleAttackCharges(p);
        }
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player) {
            handleAttackCharges(p);
        }
    }

    private void handleAttackCharges(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        // 1. Time Rewind Charge (10 hits)
        if (!hasRewindItemInInventory(p)) {
            int currentCount =
                pdc.getOrDefault(
                    attackCountKey,
                    PersistentDataType.INTEGER,
                    0
                ) + 1;
            if (currentCount >= 10) {
                pdc.set(attackCountKey, PersistentDataType.INTEGER, 0);
                giveRewindItem(p);
            } else {
                pdc.set(
                    attackCountKey,
                    PersistentDataType.INTEGER,
                    currentCount
                );
            }
        }

        // 2. Flight Override Charge (15 hits)
        if (!hasFlightItemInInventory(p)) {
            int currentFlightCount =
                pdc.getOrDefault(
                    flightAttackCountKey,
                    PersistentDataType.INTEGER,
                    0
                ) + 1;
            if (currentFlightCount >= 15) {
                pdc.set(flightAttackCountKey, PersistentDataType.INTEGER, 0);
                giveFlightItem(p);
            } else {
                pdc.set(
                    flightAttackCountKey,
                    PersistentDataType.INTEGER,
                    currentFlightCount
                );
            }
        }

        // 3. Trojan Horse Charge (7 hits)
        if (!hasTrojanItemInInventory(p)) {
            int currentTrojanCount =
                pdc.getOrDefault(
                    trojanAttackCountKey,
                    PersistentDataType.INTEGER,
                    0
                ) + 1;
            if (currentTrojanCount >= 7) {
                pdc.set(trojanAttackCountKey, PersistentDataType.INTEGER, 0);
                giveTrojanItem(p);
            } else {
                pdc.set(
                    trojanAttackCountKey,
                    PersistentDataType.INTEGER,
                    currentTrojanCount
                );
            }
        }
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        if (!hasFlightItemInInventory(p)) {
            giveFlightItem(p);
        }
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (
            e.getAction() != Action.RIGHT_CLICK_AIR &&
            e.getAction() != Action.RIGHT_CLICK_BLOCK
        ) return;

        ItemStack item = e.getItem();
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // 1. Handle Flight Item Interaction
        if (pdc.has(flightItemKey, PersistentDataType.INTEGER)) {
            e.setCancelled(true);
            item.setAmount(item.getAmount() - 1);

            PersistentDataContainer playerPdc = p.getPersistentDataContainer();
            int expiryTick = (int) p.getWorld().getGameTime() + 300; // 15 seconds
            playerPdc.set(
                flightExpiryKey,
                PersistentDataType.INTEGER,
                expiryTick
            );

            p.setAllowFlight(true);
            p.setFlying(true);
            p.playSound(
                p.getLocation(),
                Sound.ENTITY_FIREWORK_ROCKET_LAUNCH,
                1.0f,
                1.2f
            );
            return;
        }

        // 2. Handle Trojan Horse Interaction
        if (pdc.has(trojanItemKey, PersistentDataType.INTEGER)) {
            e.setCancelled(true);
            item.setAmount(item.getAmount() - 1);

            Location spawnLoc =
                e.getClickedBlock() != null
                    ? e
                          .getClickedBlock()
                          .getRelative(e.getBlockFace())
                          .getLocation()
                          .add(0.5, 0, 0.5)
                    : p.getLocation();

            ZombieHorse horse = (ZombieHorse) spawnLoc
                .getWorld()
                .spawnEntity(spawnLoc, EntityType.ZOMBIE_HORSE);
            SnapshotPvpPlugin.addToTeam(p, horse);
            horse.addPotionEffect(
                new PotionEffect(PotionEffectType.GLOWING, -1, 0, false, false)
            );
            horse.setAdult();
            horse.setTamed(true);
            horse.setCustomNameVisible(true);

            // horse armor so it doesn't burn in daylight, never drops
            EntityEquipment equipment = horse.getEquipment();
            equipment.setItem(
                EquipmentSlot.BODY,
                new ItemStack(Material.IRON_HORSE_ARMOR)
            );
            equipment.setDropChance(EquipmentSlot.BODY, 0f);

            // it's a missile now: max horse speed, and no wandering/panicking
            if (horse.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                horse
                    .getAttribute(Attribute.MOVEMENT_SPEED)
                    .setBaseValue(0.3375);
            }
            Bukkit.getMobGoals().removeAllGoals(horse);

            p.playSound(
                spawnLoc,
                Sound.ENTITY_ZOMBIE_HORSE_AMBIENT,
                1.0f,
                0.5f
            );

            // 4-second explosion countdown timer with visible text above horse
            new BukkitRunnable() {
                double timeLeft = 80; // 4 seconds = 80 ticks

                @Override
                public void run() {
                    if (!horse.isValid() || horse.isDead()) {
                        this.cancel();
                        return;
                    }

                    // re-pick the nearest enemy and run at it every 5 ticks
                    if (timeLeft % 5 == 0) {
                        LivingEntity target = findNearestTarget(p, horse);
                        if (target != null) {
                            horse
                                .getPathfinder()
                                .moveTo(target.getLocation(), 1.3);
                        }
                    }

                    horse.setCustomName(
                        ChatColor.RED +
                            "" +
                            ChatColor.BOLD +
                            String.format("%.1fs", timeLeft / 20.0)
                    );

                    if (timeLeft <= 0) {
                        Location loc = horse.getLocation();
                        loc.getWorld().createExplosion(
                            loc,
                            4.0f,
                            false,
                            true,
                            p
                        );
                        horse.remove();
                        this.cancel();
                        return;
                    }

                    timeLeft--;
                }
            }.runTaskTimer(SnapshotPvpPlugin.instance, 0L, 1L);

            return;
        }

        // 3. Handle Time Rewind Item Interaction
        if (pdc.has(rewindItemKey, PersistentDataType.INTEGER)) {
            e.setCancelled(true);

            Deque<PlayerSnapshot> history = historyMap.get(p.getUniqueId());
            if (history == null || history.isEmpty()) return;

            // Retrieve snapshot from 5 seconds ago (or oldest available snapshot)
            PlayerSnapshot past = history.peekFirst();
            if (past == null) return;

            // Consume rewind item
            item.setAmount(item.getAmount() - 1);

            // Visual effects before teleporting
            Location oldLoc = p.getLocation();
            oldLoc
                .getWorld()
                .spawnParticle(
                    Particle.REVERSE_PORTAL,
                    oldLoc.add(0, 1, 0),
                    30,
                    0.3,
                    0.5,
                    0.3,
                    0.1
                );

            // Restore state (Location, Velocity, Health)
            p.teleport(past.location());
            p.setVelocity(past.velocity());
            p.setHealth(Math.min(p.getMaxHealth(), past.health()));

            PersistentDataContainer playerPdc = p.getPersistentDataContainer();
            int currentTick = (int) p.getWorld().getGameTime();

            // Calculate how many ticks of flight time were remaining 5 seconds ago
            int oldExpiry = past.flightExpiry();
            if (oldExpiry > currentTick) {
                int remainingTicks = oldExpiry - (currentTick - 100);
                if (remainingTicks > 0) {
                    int restoredExpiry = currentTick + remainingTicks;
                    playerPdc.set(
                        flightExpiryKey,
                        PersistentDataType.INTEGER,
                        restoredExpiry
                    );
                    p.setAllowFlight(true);
                    p.setFlying(past.isFlying());
                } else {
                    playerPdc.set(
                        flightExpiryKey,
                        PersistentDataType.INTEGER,
                        0
                    );
                    if (p.getGameMode() != GameMode.CREATIVE) {
                        p.setAllowFlight(false);
                        p.setFlying(false);
                    }
                }
            } else {
                playerPdc.set(flightExpiryKey, PersistentDataType.INTEGER, 0);
                if (p.getGameMode() != GameMode.CREATIVE) {
                    p.setAllowFlight(false);
                    p.setFlying(false);
                }
            }

            // Restore Flight Ability Attack Progress & Item State
            playerPdc.set(
                flightAttackCountKey,
                PersistentDataType.INTEGER,
                past.flightAttackCount()
            );
            if (past.hadFlightItem() && !hasFlightItemInInventory(p)) {
                giveFlightItem(p);
            }

            // Restore Trojan Horse Attack Progress & Item State
            playerPdc.set(
                trojanAttackCountKey,
                PersistentDataType.INTEGER,
                past.trojanAttackCount()
            );
            if (past.hadTrojanItem() && !hasTrojanItemInInventory(p)) {
                giveTrojanItem(p);
            }

            // Restore Time Rewind Attack Progress (Only if no clock is held, to prevent rewind self-charging)
            if (!hasRewindItemInInventory(p)) {
                playerPdc.set(
                    attackCountKey,
                    PersistentDataType.INTEGER,
                    past.attackCount()
                );
            }

            // Visual effects after teleporting
            Location newLoc = past.location();
            newLoc
                .getWorld()
                .spawnParticle(
                    Particle.PORTAL,
                    newLoc.add(0, 1, 0),
                    40,
                    0.4,
                    0.6,
                    0.4,
                    0.1
                );
            newLoc
                .getWorld()
                .playSound(
                    newLoc,
                    Sound.ITEM_CHORUS_FRUIT_TELEPORT,
                    1.0f,
                    1.4f
                );
        }
    }

    private void giveFlightItem(Player p) {
        ItemStack flightItem = new ItemStack(Material.FEATHER, 1);
        ItemMeta meta = flightItem.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(
                ChatColor.AQUA + "" + ChatColor.BOLD + "Flight Override"
            );
            meta.setLore(
                List.of(
                    ChatColor.GRAY + "Right-click to grant yourself",
                    ChatColor.AQUA +
                        "Creative Flight " +
                        ChatColor.GRAY +
                        "for 20 seconds."
                )
            );
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.getPersistentDataContainer().set(
                flightItemKey,
                PersistentDataType.INTEGER,
                1
            );
            flightItem.setItemMeta(meta);
        }

        p.getInventory().addItem(flightItem);
    }

    /** Nearest living thing to the horse that isn't on the hacker's team. */
    private LivingEntity findNearestTarget(Player owner, ZombieHorse horse) {
        LivingEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        Location horseLoc = horse.getLocation();

        for (Entity entity : horse.getNearbyEntities(
            TROJAN_SEARCH_RADIUS,
            TROJAN_SEARCH_RADIUS,
            TROJAN_SEARCH_RADIUS
        )) {
            if (!(entity instanceof LivingEntity target)) continue;
            if (entity instanceof ArmorStand || target.isDead()) continue;
            if (target.equals(owner) || target.equals(horse)) continue;
            if (
                target instanceof Player other &&
                other.getGameMode() == GameMode.SPECTATOR
            ) continue;
            if (SnapshotPvpPlugin.isSameTeam(owner, target)) continue;

            double distSq = horseLoc.distanceSquared(target.getLocation());
            if (distSq < nearestDistSq) {
                nearest = target;
                nearestDistSq = distSq;
            }
        }
        return nearest;
    }

    private void giveTrojanItem(Player p) {
        ItemStack trojanItem = new ItemStack(
            Material.ZOMBIE_HORSE_SPAWN_EGG,
            1
        );
        ItemMeta meta = trojanItem.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(
                ChatColor.DARK_RED + "" + ChatColor.BOLD + "Trojan Horse"
            );
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.getPersistentDataContainer().set(
                trojanItemKey,
                PersistentDataType.INTEGER,
                1
            );
            trojanItem.setItemMeta(meta);
        }

        p.getInventory().addItem(trojanItem);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
    }

    private void giveRewindItem(Player p) {
        ItemStack rewindItem = new ItemStack(Material.CLOCK, 1);
        ItemMeta meta = rewindItem.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Time Rewind"
            );
            meta.setLore(
                List.of(
                    ChatColor.GRAY + "Right-click to teleport back",
                    ChatColor.GRAY + "to your position, HP, and flight state",
                    ChatColor.AQUA + "from 5 seconds ago."
                )
            );
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.getPersistentDataContainer().set(
                rewindItemKey,
                PersistentDataType.INTEGER,
                1
            );
            rewindItem.setItemMeta(meta);
        }

        p.getInventory().addItem(rewindItem);
    }

    private boolean hasRewindItemInInventory(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()) {
                if (
                    item
                        .getItemMeta()
                        .getPersistentDataContainer()
                        .has(rewindItemKey, PersistentDataType.INTEGER)
                ) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasFlightItemInInventory(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()) {
                if (
                    item
                        .getItemMeta()
                        .getPersistentDataContainer()
                        .has(flightItemKey, PersistentDataType.INTEGER)
                ) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasTrojanItemInInventory(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()) {
                if (
                    item
                        .getItemMeta()
                        .getPersistentDataContainer()
                        .has(trojanItemKey, PersistentDataType.INTEGER)
                ) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onLeaveCombat(Player p) {
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        resetKit(p);
    }
}
