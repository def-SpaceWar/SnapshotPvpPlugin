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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
import org.bukkit.util.Vector;

public class Hacker extends ManaKit implements Listener {

    private final NamespacedKey attackCountKey = ManaKitListener.MANA_KIT_DATA0;
    private final NamespacedKey flightExpiryKey =
        ManaKitListener.MANA_KIT_DATA1;
    private final NamespacedKey rewindItemKey = ManaKitListener.MANA_KIT_DATA2;
    private final NamespacedKey flightItemKey = ManaKitListener.MANA_KIT_DATA3;
    private final NamespacedKey flightAttackCountKey =
        ManaKitListener.MANA_KIT_DATA4;

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
        boolean hadFlightItem
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
        pdc.set(flightExpiryKey, PersistentDataType.INTEGER, 0);

        // Remove creative flight
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            p.setAllowFlight(false);
            p.setFlying(false);
        }

        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.CLOCK);
        SnapshotPvpPlugin.clearInv(inv, Material.FEATHER);

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
                hasFlightItemInInventory(p)
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
        int remainingFlightSec = Math.max(0, (flightExpiry - currentTick) / 20);

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

        boolean hasRewindItem = hasRewindItemInInventory(p);
        String rewindStatus = hasRewindItem
            ? ChatColor.GREEN + "READY"
            : ChatColor.WHITE + "" + attackCount + "/10";

        p.sendActionBar(
            flightStatus +
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
                    pdc.set(
                        flightAttackCountKey,
                        PersistentDataType.INTEGER,
                        0
                    );
                    giveFlightItem(p);
                } else {
                    pdc.set(
                        flightAttackCountKey,
                        PersistentDataType.INTEGER,
                        currentFlightCount
                    );
                }
            }
        }
    }

    @EventHandler
    public void onKill(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;

        PersistentDataContainer pdc = killer.getPersistentDataContainer();
        String currentKit = pdc.get(
            ManaKitListener.MANA_KIT,
            PersistentDataType.STRING
        );

        if (this.id.equals(currentKit)) {
            if (!hasFlightItemInInventory(killer)) {
                giveFlightItem(killer);
            }
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
            // p.sendMessage(
            //     ChatColor.AQUA +
            //         "" +
            //         ChatColor.BOLD +
            //         ">> FLIGHT ACTIVATED! Flying enabled for 20 seconds."
            // );
            return;
        }

        // 2. Handle Time Rewind Item Interaction
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

            // Restore Flight Timer & Flight Status
            playerPdc.set(
                flightExpiryKey,
                PersistentDataType.INTEGER,
                past.flightExpiry()
            );
            int currentTick = (int) p.getWorld().getGameTime();
            if (past.flightExpiry() > currentTick) {
                p.setAllowFlight(true);
                p.setFlying(past.isFlying());
            } else {
                if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
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

            // p.sendMessage(
            //     ChatColor.LIGHT_PURPLE +
            //         "" +
            //         ChatColor.BOLD +
            //         ">> REWOUND 5 SECONDS BACK IN TIME!"
            // );
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
        // p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
        // p.sendMessage(
        //     ChatColor.AQUA +
        //         "" +
        //         ChatColor.BOLD +
        //         ">> FLIGHT OVERRIDE OBTAINED!"
        // );
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
        // p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.8f);
        // p.sendMessage(
        //     ChatColor.LIGHT_PURPLE +
        //         "" +
        //         ChatColor.BOLD +
        //         ">> TIME REWIND OBTAINED!"
        // );
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

    @Override
    public void onLeaveCombat(Player p) {
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        resetKit(p);
    }
}
