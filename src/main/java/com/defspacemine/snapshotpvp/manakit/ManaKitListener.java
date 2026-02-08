package com.defspacemine.snapshotpvp.manakit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public final class ManaKitListener implements Listener {
    public static ManaKitListener instance;
    private final JavaPlugin plugin;

    private final Map<String, ManaKit> manakitRegistry = new HashMap<String, ManaKit>();
    public static final NamespacedKey MANA_KIT = new NamespacedKey("defspacemine", "mana_kit");
    public static final NamespacedKey MANA_KIT_DATA0 = new NamespacedKey("defspacemine", "mana_kit_data0");
    public static final NamespacedKey MANA_KIT_DATA1 = new NamespacedKey("defspacemine", "mana_kit_data1");
    public static final NamespacedKey MANA_KIT_DATA2 = new NamespacedKey("defspacemine", "mana_kit_data2");
    public static final NamespacedKey MANA_KIT_DATA3 = new NamespacedKey("defspacemine", "mana_kit_data3");
    public static final NamespacedKey MANA_KIT_DATA4 = new NamespacedKey("defspacemine", "mana_kit_data4");

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

    private void registerKit(ManaKit kit) {
        manakitRegistry.put(kit.getId(), kit);
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

    public boolean kit(CommandSender sender, String id) {
        if (!(sender instanceof Player player))
            return true;

        if (player.getScoreboardTags().contains("fighting")) {
            player.sendMessage(ChatColor.DARK_RED + "You are fighting!");
            return true;
        }

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

        ManaKit currentKit = getPlayerKit(player);
        if (currentKit != null)
            unsetPlayerKit(player);

        givePlayerKit(player, kit);
        player.sendMessage(ChatColor.WHITE + "You have selected "
                + ChatColor.GOLD + kit.getDisplayName()
                + ChatColor.WHITE + " successfully!");

        return true;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamageSource().getCausingEntity() instanceof Player attacker) {
            ManaKit attackerKit = getPlayerKit(attacker);
            if (attackerKit != null)
                attackerKit.onDamageDealt(attacker, event);
        }

        if (event.getEntity() instanceof Player victim) {
            ManaKit victimKit = getPlayerKit(victim);
            if (victimKit != null)
                victimKit.onDamageTaken(victim, event);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        ManaKit victimKit = getPlayerKit(victim);
        if (victimKit != null)
            victimKit.onDeath(victim, event);

        if (event.getDamageSource().getCausingEntity() instanceof Player attacker) {
            ManaKit attackerKit = getPlayerKit(attacker);
            if (attackerKit != null)
                attackerKit.onKill(attacker, event);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ManaKit kit = getPlayerKit(p);
        if (kit == null)
            return;
        kit.onInteract(p, e);
    }

    void showKits(Player player) {
        player.sendMessage(ChatColor.GREEN + "===== Available Kits =====");
        ArrayList<ManaKit> kits = new ArrayList();
        for (ManaKit kit : manakitRegistry.values())
            kits.add(kit);
        kits.sort((a, b) -> {
            if (a.stars != b.stars)
                return a.stars - b.stars;
            return a.getDisplayName().charAt(0) - b.getDisplayName().charAt(0);
        });
        for (ManaKit kit : kits) {
            String difficulty = "?";
            switch (kit.stars) {
                case 1:
                    difficulty = ChatColor.AQUA + "★";
                    break;
                case 2:
                    difficulty = ChatColor.GREEN + "★★";
                    break;
                case 3:
                    difficulty = ChatColor.DARK_PURPLE + "★★★";
                    break;
                case 4:
                    difficulty = ChatColor.RED + "★★★★";
                    break;
                case 5:
                    difficulty = ChatColor.DARK_RED + "★★★★★";
                    break;
            }
            player.sendMessage(ChatColor.GOLD + kit.getId()
                    + ChatColor.GRAY + " [" + difficulty + ChatColor.GRAY + "] "
                    + ChatColor.YELLOW + kit.getDisplayName()
                    + " " + kit.getDescription());
        }
        player.sendMessage(ChatColor.WHITE + "Use /kit "
                + ChatColor.GOLD + "<id>"
                + ChatColor.WHITE + " to select a kit!");
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

        ManaKit currentKit = getPlayerKit(player);
        if (currentKit != null)
            unsetPlayerKit(player);

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
                            SnapshotPvpPlugin.setPlayerScore(player, "dummyKillstreak", 0);
                            player.removeScoreboardTag("combat");
                            kit.onLeaveCombat(player);
                        }
                        kit.onIdleTick(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
