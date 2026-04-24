package com.defspacemine.snapshotpvp.manakit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class ManaKitListener implements Listener {
    public static ManaKitListener instance;
    private final JavaPlugin plugin;

    public final Map<String, ManaKit> manakitRegistry = new HashMap<String, ManaKit>();
    public static final NamespacedKey MANA_KIT = new NamespacedKey("defspacemine", "mana_kit");
    public static final NamespacedKey MANA_FOOD = new NamespacedKey("defspacemine", "mana_food");

    public static final NamespacedKey MANA_KIT_DATA0 = new NamespacedKey("defspacemine", "mana_kit_data0");
    public static final NamespacedKey MANA_KIT_DATA1 = new NamespacedKey("defspacemine", "mana_kit_data1");
    public static final NamespacedKey MANA_KIT_DATA2 = new NamespacedKey("defspacemine", "mana_kit_data2");
    public static final NamespacedKey MANA_KIT_DATA3 = new NamespacedKey("defspacemine", "mana_kit_data3");
    public static final NamespacedKey MANA_KIT_DATA4 = new NamespacedKey("defspacemine", "mana_kit_data4");
    public static final NamespacedKey MANA_KIT_DATA5 = new NamespacedKey("defspacemine", "mana_kit_data5");
    public static final NamespacedKey MANA_KIT_DATA6 = new NamespacedKey("defspacemine", "mana_kit_data6");
    public static final NamespacedKey MANA_KIT_DATA7 = new NamespacedKey("defspacemine", "mana_kit_data7");
    public static final NamespacedKey MANA_KIT_DATA8 = new NamespacedKey("defspacemine", "mana_kit_data8");
    public static final NamespacedKey MANA_KIT_DATA9 = new NamespacedKey("defspacemine", "mana_kit_data9");

    public static final NamespacedKey MANA_KIT_DATASTR0 = new NamespacedKey("defspacemine", "mana_kit_datastr0");
    public static final NamespacedKey MANA_KIT_DATASTR1 = new NamespacedKey("defspacemine", "mana_kit_datastr1");
    public static final NamespacedKey MANA_KIT_DATASTR2 = new NamespacedKey("defspacemine", "mana_kit_datastr2");
    public static final NamespacedKey MANA_KIT_DATASTR3 = new NamespacedKey("defspacemine", "mana_kit_datastr3");
    public static final NamespacedKey MANA_KIT_DATASTR4 = new NamespacedKey("defspacemine", "mana_kit_datastr4");
    public static final NamespacedKey MANA_KIT_DATASTR5 = new NamespacedKey("defspacemine", "mana_kit_datastr5");
    public static final NamespacedKey MANA_KIT_DATASTR6 = new NamespacedKey("defspacemine", "mana_kit_datastr6");
    public static final NamespacedKey MANA_KIT_DATASTR7 = new NamespacedKey("defspacemine", "mana_kit_datastr7");
    public static final NamespacedKey MANA_KIT_DATASTR8 = new NamespacedKey("defspacemine", "mana_kit_datastr8");
    public static final NamespacedKey MANA_KIT_DATASTR9 = new NamespacedKey("defspacemine", "mana_kit_datastr9");

    public static final NamespacedKey MANA_KIT_DATABOOL0 = new NamespacedKey("defspacemine", "mana_kit_databool0");
    public static final NamespacedKey MANA_KIT_DATABOOL1 = new NamespacedKey("defspacemine", "mana_kit_databool1");
    public static final NamespacedKey MANA_KIT_DATABOOL2 = new NamespacedKey("defspacemine", "mana_kit_databool2");
    public static final NamespacedKey MANA_KIT_DATABOOL3 = new NamespacedKey("defspacemine", "mana_kit_databool3");
    public static final NamespacedKey MANA_KIT_DATABOOL4 = new NamespacedKey("defspacemine", "mana_kit_databool4");
    public static final NamespacedKey MANA_KIT_DATABOOL5 = new NamespacedKey("defspacemine", "mana_kit_databool5");
    public static final NamespacedKey MANA_KIT_DATABOOL6 = new NamespacedKey("defspacemine", "mana_kit_databool6");
    public static final NamespacedKey MANA_KIT_DATABOOL7 = new NamespacedKey("defspacemine", "mana_kit_databool7");
    public static final NamespacedKey MANA_KIT_DATABOOL8 = new NamespacedKey("defspacemine", "mana_kit_databool8");
    public static final NamespacedKey MANA_KIT_DATABOOL9 = new NamespacedKey("defspacemine", "mana_kit_databool9");

    public ManaKitListener(JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;

        registerKit(new Squire());
        registerKit(new Poacher());
        registerKit(new FireInTheHole());
        registerKit(new Juggernaut());
        registerKit(new Berserk());
        registerKit(new Thief());
        registerKit(new ICBM());
        registerKit(new Tridentite());
        registerKit(new LightPaladin());
        registerKit(new Mercenary());
        registerKit(new Cultist());
        registerKit(new Barbarian());
        registerKit(new Shulkian());
        registerKit(new JapaneseGoblin());
        registerKit(new JadeTrio());
        registerKit(new Titan());
        registerKit(new Frog());
        registerKit(new Breezy());
        registerKit(new Colossus());
        registerKit(new Pharmacist());
        registerKit(new Incendiary());
        registerKit(new Gambler());
        registerKit(new Engineer());
        registerKit(new AtlantianPrince());

        manakitGameLoop();
        startGlobalScoreboardTask();
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

    public static void giveItemsFromShulker(Player p, String worldName, int x, int y, int z) {
        World world = Bukkit.getWorld(worldName);
        if (world == null)
            return;

        Block block = world.getBlockAt(x, y, z);
        if (!(block.getState() instanceof ShulkerBox shulker))
            return;

        PlayerInventory inv = p.getInventory();
        inv.clear();

        Inventory shulkerInv = shulker.getInventory();

        for (int i = 0; i < shulkerInv.getSize(); i++) {
            ItemStack item = shulkerInv.getItem(i);
            if (item == null || item.getType() == Material.AIR)
                continue;

            ItemStack giveItem = item.clone();

            switch (i) {
                case 0 -> inv.setItemInOffHand(giveItem);
                case 1 -> inv.setHelmet(giveItem);
                case 2 -> inv.setChestplate(giveItem);
                case 3 -> inv.setLeggings(giveItem);
                case 4 -> inv.setBoots(giveItem);
                default -> inv.addItem(giveItem);
            }
        }
        p.updateInventory();
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

    @EventHandler(ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter))
            return;

        ManaKit shooterKit = getPlayerKit(shooter);
        if (shooterKit == null)
            return;
        shooterKit.onShootBow(shooter, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = null;
        Entity victim = event.getEntity();

        if (event.getDamageSource().getCausingEntity() instanceof Player p)
            attacker = p;
        else if (event.getDamager() instanceof LightningStrike strike)
            if (strike.getCausingEntity() instanceof Player p)
                attacker = p;

        Team vTeam = SnapshotPvpPlugin.getTeamG(victim);
        if (attacker != null && vTeam != null && vTeam.equals(SnapshotPvpPlugin.getTeam(attacker)))
            return;

        if (victim instanceof Player p) {
            ManaKit victimKit = getPlayerKit(p);
            if (victimKit != null)
                victimKit.onDamageTaken(p, event);
        }

        if (attacker != null) {
            ManaKit attackerKit = getPlayerKit(attacker);
            if (attackerKit != null)
                attackerKit.onDamageDealt(attacker, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        ManaKit victimKit = getPlayerKit(victim);
        if (victimKit != null)
            victimKit.onDeath(victim, event);

        Player attacker = victim.getKiller();

        Team vTeam = SnapshotPvpPlugin.getTeam(victim);
        if (vTeam != null && attacker != null && vTeam.equals(SnapshotPvpPlugin.getTeam(attacker)))
            return;

        if (attacker != null) {
            Set<String> playerTags = attacker.getScoreboardTags();
            if (!playerTags.contains("combat"))
                return;

            int killstreak = SnapshotPvpPlugin.getPlayerScore(attacker, "dummyKillstreak");
            SnapshotPvpPlugin.setPlayerScore(attacker, "dummyKillstreak", killstreak + 1);
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

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        ItemStack consumedItem = e.getItem();

        if (consumedItem.hasItemMeta() && p.getGameMode() != GameMode.CREATIVE) {
            ItemMeta meta = consumedItem.getItemMeta();
            if (meta.getPersistentDataContainer().has(MANA_FOOD, PersistentDataType.BOOLEAN)) {
                ItemStack replacement = consumedItem.clone();
                replacement.add(1);
                boolean isOffHand = consumedItem.equals(p.getInventory().getItemInOffHand());
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (isOffHand)
                            p.getInventory().setItemInOffHand(replacement);
                        else
                            p.getInventory().setItemInMainHand(replacement);
                        p.updateInventory();
                    }
                }.runTask(plugin);
            }
        }

        ManaKit kit = getPlayerKit(p);
        if (kit == null)
            return;
        kit.onConsume(p, e);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p))
            return;
        ManaKit kit = getPlayerKit(p);
        if (kit == null)
            return;
        kit.onProjectileHit(p, e);
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
            String difficulty = ChatColor.MAGIC + "?????";
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
                    difficulty = ChatColor.DARK_GRAY + "★★★★★";
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
                            player.clearActivePotionEffects();
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

    public void startGlobalScoreboardTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    List<Player> playersInWorld = world.getPlayers();

                    for (Player viewer : playersInWorld) {
                        if (viewer == null || !viewer.isOnline())
                            continue;

                        if (viewer.getScoreboardTags().contains("fighting"))
                            updateWorldScoreboard(viewer, playersInWorld);
                        else if (viewer.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard())
                            viewer.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void updateWorldScoreboard(Player viewer, List<Player> trackedPlayers) {
        Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
        Scoreboard board = viewer.getScoreboard();

        if (board == mainBoard) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            viewer.setScoreboard(board);
        }

        for (Team mainTeam : mainBoard.getTeams()) {
            if (mainTeam.getName() == null)
                continue;

            Team localTeam = board.getTeam(mainTeam.getName());
            if (localTeam == null) {
                localTeam = board.registerNewTeam(mainTeam.getName());
            }

            localTeam.setColor(mainTeam.getColor());
            localTeam.setPrefix(mainTeam.getPrefix());
            localTeam.setAllowFriendlyFire(mainTeam.allowFriendlyFire());
            localTeam.setCanSeeFriendlyInvisibles(mainTeam.canSeeFriendlyInvisibles());

            for (String entry : mainTeam.getEntries()) {
                Entity entity = SnapshotPvpPlugin.getEntityFromEntry(entry);
                if (!localTeam.hasEntry(entry) &&
                        entity != null)
                    if (viewer.getWorld().equals(entity.getWorld()))
                        localTeam.addEntry(entry);
                    else
                        localTeam.removeEntry(entry);

            }
        }

        Objective healthObj = board.getObjective("health");
        if (healthObj == null) {
            healthObj = board.registerNewObjective("health", Criteria.DUMMY,
                    Component.text("❤").color(NamedTextColor.RED));
            healthObj.setDisplaySlot(DisplaySlot.PLAYER_LIST);
            healthObj.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }

        for (Player p : trackedPlayers)
            healthObj.getScore(p).setScore((int) (p.getHealth() + p.getAbsorptionAmount()));

        Objective obj = board.getObjective("combat_list");
        if (obj == null) {
            obj = board.registerNewObjective("combat_list", Criteria.DUMMY,
                    Component.text("— FFA STATS —").color(NamedTextColor.GOLD)
                            .decorate(TextDecoration.BOLD));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        for (String entry : board.getEntries())
            obj.getScore(entry).resetScore();

        List<Player> sortedPlayers = new ArrayList<>(trackedPlayers);
        sortedPlayers.removeIf((p) -> {
            return !p.getScoreboardTags().contains("fighting");
        });
        sortedPlayers.sort((p1, p2) -> {
            int ks1 = SnapshotPvpPlugin.getPlayerScore(p1, "dummyKillstreak");
            int ks2 = SnapshotPvpPlugin.getPlayerScore(p2, "dummyKillstreak");
            if (ks1 != ks2)
                return Integer.compare(ks2, ks1);

            double hp1 = p1.getHealth();
            double hp2 = p2.getHealth();
            if (hp1 != hp2)
                return Double.compare(hp2, hp1);

            return p1.getName().compareToIgnoreCase(p2.getName());
        });

        int position = 100;
        setHiddenScore(obj, "", position--);

        for (Player p : sortedPlayers) {
            int ks = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
            int hp = (int) p.getHealth();
            int absorp = (int) p.getAbsorptionAmount();
            Team team = SnapshotPvpPlugin.getTeam(p);
            ChatColor nameColor = (team != null) ? ChatColor.valueOf(team.getColor().name()) : ChatColor.WHITE;

            String prefix = team == null ? "" : team.getPrefix();
            String hpText = "" + ChatColor.GREEN + hp + "❤";
            if (absorp > 0)
                hpText += " " + ChatColor.YELLOW + absorp + "❤";
            String entry = nameColor + prefix + p.getName() + ChatColor.DARK_GRAY + ": " + ChatColor.GOLD + ks + "🔥"
                    + ChatColor.GRAY + " | " + hpText;
            setHiddenScore(obj, entry, position--);
        }

        setHiddenScore(obj, " ", position--);
        setHiddenScore(obj, ChatColor.GRAY + "Players: " + ChatColor.WHITE + trackedPlayers.size(), position--);
    }

    private void setHiddenScore(Objective obj, String text, int value) {
        if (obj == null || text == null)
            return;
        Score score = obj.getScore(text);
        score.setScore(value);
        score.numberFormat(NumberFormat.blank());
    }
}
