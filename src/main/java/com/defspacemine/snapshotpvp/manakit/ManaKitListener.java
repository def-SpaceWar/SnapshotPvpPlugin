package com.defspacemine.snapshotpvp.manakit;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;

import de.tr7zw.nbtapi.NBT;
import io.papermc.lib.PaperLib;
import net.kyori.adventure.text.Component;

public final class ManaKitListener implements Listener {
    public static ManaKitListener instance;

    public static final NamespacedKey MANA_KIT = new NamespacedKey("defspacemine", "mana_kit");
    public static final NamespacedKey MANA_KIT_DATA0 = new NamespacedKey("defspacemine", "mana_kit_data0");
    public static final NamespacedKey MANA_KIT_DATA1 = new NamespacedKey("defspacemine", "mana_kit_data1");
    public static final NamespacedKey MANA_KIT_DATA2 = new NamespacedKey("defspacemine", "mana_kit_data2");
    public static final NamespacedKey MANA_KIT_DATA3 = new NamespacedKey("defspacemine", "mana_kit_data3");
    public static final NamespacedKey MANA_KIT_DATA4 = new NamespacedKey("defspacemine", "mana_kit_data4");

    private final Map<String, ManaKit> manakitRegistry = new HashMap<String, ManaKit>();

    private void registerKit(ManaKit kit) {
        manakitRegistry.put(kit.getId(), kit);
    }

    private final JavaPlugin plugin;

    public ManaKitListener(JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;

        registerKit(new Squire());
        registerKit(new Poacher());
        registerKit(new FireInTheHole());
        registerKit(new Juggernaut());
        registerKit(new Berserk());
        manakitGameLoop();
    }

    public ManaKit getKit(String id) {
        return manakitRegistry.get(id);
    }

    public ManaKit getPlayerKit(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String kitId = pdc.get(MANA_KIT, PersistentDataType.STRING);

        if (kitId == null)
            return null;

        return getKit(kitId);
    }

    public void givePlayerKit(Player player, ManaKit kit) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(MANA_KIT, PersistentDataType.STRING, kit.getId());
        kit.giveKit(player);
    }

    public boolean hasKit(Player player) {
        return player.getPersistentDataContainer().has(MANA_KIT, PersistentDataType.STRING);
    }

    void showKits(Player player) {
        player.sendMessage(ChatColor.GREEN + "===== Available Kits =====");
        for (ManaKit kit : manakitRegistry.values()) {
            player.sendMessage(ChatColor.GOLD + kit.getId()
                    + ChatColor.WHITE + ": " + kit.getDisplayName()
                    + " - " + kit.getDescription());
        }
        player.sendMessage(ChatColor.WHITE + "Use /kit "
                + ChatColor.GOLD + "<id>"
                + ChatColor.WHITE + " to select a kit!");
    }

    public boolean kit(CommandSender sender, String id) {
        if (!(sender instanceof Player))
            return true;

        Player player = (Player) sender;
        // if (player is fighting) return;

        if (id == null) {
            showKits(player);
            return true;
        }

        ManaKit kit = getKit(id);
        if (kit == null) {
            showKits(player);
            player.sendMessage(ChatColor.DARK_RED + "You selected an invalid <id> of " + id + ".");
            return true;
        }

        givePlayerKit(player, kit);
        player.sendMessage(ChatColor.WHITE + "You have selected "
                + ChatColor.GOLD + kit.getDisplayName()
                + ChatColor.WHITE + " successfully!");

        return true;
    }

    private void unsetPlayerKit(Player player) {
        ManaKit kit = getPlayerKit(player);
        if (kit == null)
            return;
        player.sendMessage(ChatColor.GRAY + "You have unequipped "
                + ChatColor.GOLD + kit.getDisplayName()
                + ChatColor.GRAY + ".");
    }

    private void setPlayerKit(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate == null || !chestplate.hasItemMeta()) {
            unsetPlayerKit(player);
            pdc.remove(MANA_KIT);
            return;
        }

        ItemMeta meta = chestplate.getItemMeta();
        if (!meta.getPersistentDataContainer().has(MANA_KIT, PersistentDataType.STRING)) {
            unsetPlayerKit(player);
            pdc.remove(MANA_KIT);
            return;
        }

        String kitId = meta.getPersistentDataContainer().get(MANA_KIT, PersistentDataType.STRING);
        ManaKit kit = getPlayerKit(player);

        if (kit != null && kitId.equals(kit.getId()))
            return;

        kit = getKit(kitId);
        pdc.set(MANA_KIT, PersistentDataType.STRING, kit.getId());
        kit.resetKit(player);
        player.sendMessage(ChatColor.WHITE + "You have selected "
                + ChatColor.GOLD + kit.getDisplayName()
                + ChatColor.WHITE + " successfully!");
    }

    private void manakitGameLoop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : SnapshotPvpPlugin.server.getOnlinePlayers()) {
                    Set<String> playerTags = player.getScoreboardTags();
                    setPlayerKit(player);

                    if (!hasKit(player)) {
                        continue;
                    }

                    ManaKit kit = getPlayerKit(player);
                    if (kit == null)
                        continue;

                    if (playerTags.contains("fighting")) {
                        if (!playerTags.contains("combat")) {
                            player.addScoreboardTag("combat");
                            kit.onEnterCombat(player);
                        }
                        kit.onCombatTick(player);
                    } else {
                        if (playerTags.contains("combat")) {
                            player.removeScoreboardTag("combat");
                            kit.onLeaveCombat(player);
                        }
                        kit.onIdleTick(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamageSource().getCausingEntity() instanceof Player) {
            Player attacker = (Player) event.getDamageSource().getCausingEntity();
            ManaKit attackerKit = getPlayerKit(attacker);

            if (attackerKit != null) {
                attackerKit.onDamageDealt(attacker, event);
            }

        }

        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            ManaKit victimKit = getPlayerKit(victim);

            if (victimKit != null) {
                victimKit.onDamageTaken(victim, event);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        ManaKit victimKit = getPlayerKit(victim);
        if (victimKit != null) {
            victimKit.onDeath(victim, event);
        }

        Entity killer = event.getDamageSource().getCausingEntity();
        if (killer instanceof Player) {
            Player attacker = (Player) killer;

            ManaKit attackerKit = getPlayerKit(attacker);
            if (attackerKit != null) {
                attackerKit.onKill((Player) killer, event);
            }
        }
    }
}
