package com.defspacemine.snapshotpvp.manakit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.defspacemine.snapshotpvp.lobby.ManaKitLobbyType;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
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
import org.bukkit.scoreboard.Team;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public final class ManaKitListener implements Listener {

    public static ManaKitListener instance;
    private final JavaPlugin plugin;

    public final Map<String, ManaKit> manaKitRegistry = new HashMap<
        String,
        ManaKit
    >();
    public static final NamespacedKey MANA_KIT = new NamespacedKey(
        "defspacemine",
        "mana_kit"
    );
    public static final NamespacedKey MANA_FOOD = new NamespacedKey(
        "defspacemine",
        "mana_food"
    );

    public static final NamespacedKey MANA_KIT_DATA0 = new NamespacedKey(
        "defspacemine",
        "mana_kit_data0"
    );
    public static final NamespacedKey MANA_KIT_DATA1 = new NamespacedKey(
        "defspacemine",
        "mana_kit_data1"
    );
    public static final NamespacedKey MANA_KIT_DATA2 = new NamespacedKey(
        "defspacemine",
        "mana_kit_data2"
    );
    public static final NamespacedKey MANA_KIT_DATA3 = new NamespacedKey(
        "defspacemine",
        "mana_kit_data3"
    );
    public static final NamespacedKey MANA_KIT_DATA4 = new NamespacedKey(
        "defspacemine",
        "mana_kit_data4"
    );
    public static final NamespacedKey MANA_KIT_DATA5 = new NamespacedKey(
        "defspacemine",
        "mana_kit_data5"
    );
    public static final NamespacedKey MANA_KIT_DATA6 = new NamespacedKey(
        "defspacemine",
        "mana_kit_data6"
    );
    public static final NamespacedKey MANA_KIT_DATA7 = new NamespacedKey(
        "defspacemine",
        "mana_kit_data7"
    );
    public static final NamespacedKey MANA_KIT_DATA8 = new NamespacedKey(
        "defspacemine",
        "mana_kit_data8"
    );
    public static final NamespacedKey MANA_KIT_DATA9 = new NamespacedKey(
        "defspacemine",
        "mana_kit_data9"
    );

    public static final NamespacedKey MANA_KIT_DATASTR0 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datastr0"
    );
    public static final NamespacedKey MANA_KIT_DATASTR1 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datastr1"
    );
    public static final NamespacedKey MANA_KIT_DATASTR2 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datastr2"
    );
    public static final NamespacedKey MANA_KIT_DATASTR3 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datastr3"
    );
    public static final NamespacedKey MANA_KIT_DATASTR4 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datastr4"
    );
    public static final NamespacedKey MANA_KIT_DATASTR5 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datastr5"
    );
    public static final NamespacedKey MANA_KIT_DATASTR6 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datastr6"
    );
    public static final NamespacedKey MANA_KIT_DATASTR7 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datastr7"
    );
    public static final NamespacedKey MANA_KIT_DATASTR8 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datastr8"
    );
    public static final NamespacedKey MANA_KIT_DATASTR9 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datastr9"
    );

    public static final NamespacedKey MANA_KIT_DATABOOL0 = new NamespacedKey(
        "defspacemine",
        "mana_kit_databool0"
    );
    public static final NamespacedKey MANA_KIT_DATABOOL1 = new NamespacedKey(
        "defspacemine",
        "mana_kit_databool1"
    );
    public static final NamespacedKey MANA_KIT_DATABOOL2 = new NamespacedKey(
        "defspacemine",
        "mana_kit_databool2"
    );
    public static final NamespacedKey MANA_KIT_DATABOOL3 = new NamespacedKey(
        "defspacemine",
        "mana_kit_databool3"
    );
    public static final NamespacedKey MANA_KIT_DATABOOL4 = new NamespacedKey(
        "defspacemine",
        "mana_kit_databool4"
    );
    public static final NamespacedKey MANA_KIT_DATABOOL5 = new NamespacedKey(
        "defspacemine",
        "mana_kit_databool5"
    );
    public static final NamespacedKey MANA_KIT_DATABOOL6 = new NamespacedKey(
        "defspacemine",
        "mana_kit_databool6"
    );
    public static final NamespacedKey MANA_KIT_DATABOOL7 = new NamespacedKey(
        "defspacemine",
        "mana_kit_databool7"
    );
    public static final NamespacedKey MANA_KIT_DATABOOL8 = new NamespacedKey(
        "defspacemine",
        "mana_kit_databool8"
    );
    public static final NamespacedKey MANA_KIT_DATABOOL9 = new NamespacedKey(
        "defspacemine",
        "mana_kit_databool9"
    );

    public static final NamespacedKey MANA_KIT_DATADBL0 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datadbl0"
    );
    public static final NamespacedKey MANA_KIT_DATADBL1 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datadbl1"
    );
    public static final NamespacedKey MANA_KIT_DATADBL2 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datadbl2"
    );
    public static final NamespacedKey MANA_KIT_DATADBL3 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datadbl3"
    );
    public static final NamespacedKey MANA_KIT_DATADBL4 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datadbl4"
    );
    public static final NamespacedKey MANA_KIT_DATADBL5 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datadbl5"
    );
    public static final NamespacedKey MANA_KIT_DATADBL6 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datadbl6"
    );
    public static final NamespacedKey MANA_KIT_DATADBL7 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datadbl7"
    );
    public static final NamespacedKey MANA_KIT_DATADBL8 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datadbl8"
    );
    public static final NamespacedKey MANA_KIT_DATADBL9 = new NamespacedKey(
        "defspacemine",
        "mana_kit_datadbl9"
    );

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
        registerKit(new CactusChampion());
        registerKit(new Voodoo());
        registerKit(new Ranger());
        registerKit(new Jockey());
        registerKit(new SculkWarrior());
        registerKit(new HellboundRonin());
        registerKit(new Deadeye());
        registerKit(new BuckshotViking());
        registerKit(new HeadlessHorseman());
        registerKit(new SlimeKing());
        registerKit(new Hacker());
        registerKit(new Violence());
        registerKit(new Monk());
        registerKit(new TwoTimer());

        manakitGameLoop();
    }

    private void registerKit(ManaKit kit) {
        manaKitRegistry.put(kit.getId(), kit);
    }

    public ManaKit getKit(String id) {
        return manaKitRegistry.get(id);
    }

    public ManaKit getPlayerKit(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String kitId = pdc.get(MANA_KIT, PersistentDataType.STRING);

        if (kitId == null) return null;

        return getKit(kitId);
    }

    public void givePlayerKit(Player player, ManaKit kit) {
        if (ManaKit.isInShadowWorld(player)) return;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(MANA_KIT, PersistentDataType.STRING, kit.getId());
        kit.giveKit(player);
        kit.playItemGainFeedback(player);
    }

    public void enterCombat(Player player) {
        if (
            ManaKit.isInShadowWorld(player) ||
            !player.getScoreboardTags().contains("fighting")
        ) return;
        ManaKit kit = getPlayerKit(player);
        if (kit == null || !player.addScoreboardTag("combat")) return;
        player.clearActivePotionEffects();
        givePlayerKit(player, kit);
        kit.onEnterCombat(player);
    }

    public static void clearManaKitData(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        for (
            NamespacedKey key : List.of(
                ManaKit.SHADOW_WORLD_TIMER,
                MANA_KIT,
                MANA_FOOD,
                MANA_KIT_DATA0,
                MANA_KIT_DATA1,
                MANA_KIT_DATA2,
                MANA_KIT_DATA3,
                MANA_KIT_DATA4,
                MANA_KIT_DATA5,
                MANA_KIT_DATA6,
                MANA_KIT_DATA7,
                MANA_KIT_DATA8,
                MANA_KIT_DATA9,
                MANA_KIT_DATASTR0,
                MANA_KIT_DATASTR1,
                MANA_KIT_DATASTR2,
                MANA_KIT_DATASTR3,
                MANA_KIT_DATASTR4,
                MANA_KIT_DATASTR5,
                MANA_KIT_DATASTR6,
                MANA_KIT_DATASTR7,
                MANA_KIT_DATASTR8,
                MANA_KIT_DATASTR9,
                MANA_KIT_DATABOOL0,
                MANA_KIT_DATABOOL1,
                MANA_KIT_DATABOOL2,
                MANA_KIT_DATABOOL3,
                MANA_KIT_DATABOOL4,
                MANA_KIT_DATABOOL5,
                MANA_KIT_DATABOOL6,
                MANA_KIT_DATABOOL7,
                MANA_KIT_DATABOOL8,
                MANA_KIT_DATABOOL9,
                MANA_KIT_DATADBL0,
                MANA_KIT_DATADBL1,
                MANA_KIT_DATADBL2,
                MANA_KIT_DATADBL3,
                MANA_KIT_DATADBL4,
                MANA_KIT_DATADBL5,
                MANA_KIT_DATADBL6,
                MANA_KIT_DATADBL7,
                MANA_KIT_DATADBL8,
                MANA_KIT_DATADBL9
            )
        ) {
            pdc.remove(key);
        }
    }

    public static void giveItemsFromShulker(
        Player p,
        String worldName,
        int x,
        int y,
        int z
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Block block = world.getBlockAt(x, y, z);
        if (!(block.getState() instanceof ShulkerBox shulker)) return;

        PlayerInventory inv = p.getInventory();
        inv.clear();

        Inventory shulkerInv = shulker.getInventory();

        for (int i = 0; i < shulkerInv.getSize(); i++) {
            ItemStack item = shulkerInv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

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

    public static ItemStack getItemFromShulker(
        int idx,
        String worldName,
        int x,
        int y,
        int z
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        Block block = world.getBlockAt(x, y, z);
        if (!(block.getState() instanceof ShulkerBox shulker)) return null;

        return shulker.getInventory().getItem(idx);
    }

    public boolean hasKit(Player player) {
        return player
            .getPersistentDataContainer()
            .has(MANA_KIT, PersistentDataType.STRING);
    }

    public boolean kit(CommandSender sender, String id) {
        if (!(sender instanceof Player player)) return true;

        ManaKitLobbyType.ManaKitLobbyInstance lobby =
            ManaKitLobbyType.getLobbyForPlayer(player);
        if (lobby == null || !lobby.canSelectKit(player)) {
            player.sendMessage(
                ChatColor.DARK_RED +
                    "Kit selection is not available during this match."
            );
            return true;
        }

        if (id == null) {
            if (!lobby.tryOpenKitGui(player)) {
                player.sendMessage(
                    ChatColor.DARK_RED +
                        "Your kit selection GUI has already been used this match."
                );
                return true;
            }
            showKits(player);
            return true;
        }

        ManaKit kit = getKit(id);
        if (kit == null) {
            player.sendMessage(
                ChatColor.DARK_RED +
                    "You selected an invalid <id> of " +
                    id +
                    "."
            );
            if (lobby.tryOpenKitGui(player)) showKits(player);
            return true;
        }

        selectKit(player, kit, getPlayerKit(player));
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) return;
        if (!shooter.getScoreboardTags().contains("combat")) return;

        ManaKit shooterKit = getPlayerKit(shooter);
        if (shooterKit == null) return;
        if (ManaKit.isInShadowWorld(shooter)) shooterKit.onShootBowShadow(
            shooter,
            event
        );
        else shooterKit.onShootBow(shooter, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.getScoreboardTags().contains("combat")) return;

        ManaKit kit = getPlayerKit(player);
        if (kit == null) return;
        if (ManaKit.isInShadowWorld(player)) kit.onToggleGlideShadow(
            player,
            event
        );
        else kit.onToggleGlide(player, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = null;
        Entity victim = event.getEntity();

        if (
            event.getDamageSource().getCausingEntity() instanceof Player p
        ) attacker = p;
        else if (event.getDamager() instanceof LightningStrike strike) if (
            strike.getCausingEntity() instanceof Player p
        ) attacker = p;

        Team vTeam = SnapshotPvpPlugin.getTeamG(victim);
        if (
            attacker != null &&
            vTeam != null &&
            vTeam.equals(SnapshotPvpPlugin.getTeam(attacker))
        ) return;

        for (Player player : victim.getWorld().getPlayers()) {
            if (
                player.equals(victim) ||
                player.equals(attacker) ||
                !player.getScoreboardTags().contains("combat")
            ) continue;
            ManaKit kit = getPlayerKit(player);
            if (kit == null) continue;
            if (ManaKit.isInShadowWorld(player)) kit.onDamageInWorldShadow(
                player,
                attacker,
                victim,
                event
            );
            else kit.onDamageInWorld(player, attacker, victim, event);
        }

        if (
            victim instanceof Player p &&
            p.getScoreboardTags().contains("combat")
        ) {
            ManaKit victimKit = getPlayerKit(p);
            if (victimKit != null) {
                if (ManaKit.isInShadowWorld(p)) victimKit.onDamageTakenShadow(
                    p,
                    event
                );
                else victimKit.onDamageTaken(p, event);
            }
        }

        if (
            attacker != null &&
            attacker.getScoreboardTags().contains("combat")
        ) {
            ManaKit attackerKit = getPlayerKit(attacker);
            if (attackerKit != null) {
                if (
                    ManaKit.isInShadowWorld(attacker)
                ) attackerKit.onDamageDealtShadow(attacker, event);
                else attackerKit.onDamageDealt(attacker, event);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        ManaKit victimKit = getPlayerKit(victim);
        if (victimKit != null) {
            if (ManaKit.isInShadowWorld(victim)) victimKit.onDeathShadow(
                victim,
                event
            );
            else victimKit.onDeath(victim, event);
        }

        Player attacker = victim.getKiller();

        Team vTeam = SnapshotPvpPlugin.getTeam(victim);
        if (
            attacker != null &&
            vTeam != null &&
            vTeam.equals(SnapshotPvpPlugin.getTeam(attacker))
        ) return;

        if (attacker != null) {
            Set<String> playerTags = attacker.getScoreboardTags();
            if (!playerTags.contains("combat")) return;

            int killstreak = SnapshotPvpPlugin.getPlayerScore(
                attacker,
                "dummyKillstreak"
            );
            SnapshotPvpPlugin.setPlayerScore(
                attacker,
                "dummyKillstreak",
                killstreak + 1
            );
            ManaKit attackerKit = getPlayerKit(attacker);
            if (attackerKit != null) {
                if (ManaKit.isInShadowWorld(attacker)) attackerKit.onKillShadow(
                    attacker,
                    event
                );
                else attackerKit.onKill(attacker, event);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!p.getScoreboardTags().contains("combat")) return;
        ManaKit kit = getPlayerKit(p);
        if (kit == null) return;
        if (ManaKit.isInShadowWorld(p)) kit.onInteractShadow(p, e);
        else kit.onInteract(p, e);
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (!p.getScoreboardTags().contains("combat")) return;
        ManaKit kit = getPlayerKit(p);
        if (kit == null) return;
        if (ManaKit.isInShadowWorld(p)) kit.onInteractEntityShadow(p, e);
        else kit.onInteractEntity(p, e);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        ItemStack consumedItem = e.getItem();

        if (
            consumedItem.hasItemMeta() && p.getGameMode() != GameMode.CREATIVE
        ) {
            ItemMeta meta = consumedItem.getItemMeta();
            if (
                meta
                    .getPersistentDataContainer()
                    .has(MANA_FOOD, PersistentDataType.BOOLEAN)
            ) {
                ItemStack replacement = consumedItem.clone();
                replacement.add(1);
                boolean isOffHand = consumedItem.equals(
                    p.getInventory().getItemInOffHand()
                );
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (isOffHand) p.getInventory().setItemInOffHand(
                            replacement
                        );
                        else p.getInventory().setItemInMainHand(replacement);
                        p.updateInventory();
                    }
                }.runTask(plugin);
            }
        }

        if (!p.getScoreboardTags().contains("combat")) return;
        ManaKit kit = getPlayerKit(p);
        if (kit == null) return;
        if (ManaKit.isInShadowWorld(p)) kit.onConsumeShadow(p, e);
        else kit.onConsume(p, e);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p)) return;
        if (!p.getScoreboardTags().contains("combat")) return;
        ManaKit kit = getPlayerKit(p);
        if (kit == null) return;
        if (ManaKit.isInShadowWorld(p)) kit.onProjectileHitShadow(p, e);
        else kit.onProjectileHit(p, e);
    }

    public void showKits(Player player) {
        List<ManaKit> kits = new ArrayList<>(manaKitRegistry.values());
        kits.sort(
            Comparator.comparingInt((ManaKit kit) -> kit.stars).thenComparing(
                ManaKit::getDisplayName
            )
        );

        Gui gui = Gui.gui()
            .title(Component.text("Available Kits"))
            .rows(6)
            .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));

        ManaKit selectedKit = getPlayerKit(player);
        for (ManaKit kit : kits) {
            ManaKitLobbyType.ManaKitLobbyInstance lobby =
                ManaKitLobbyType.getLobbyForPlayer(player);
            if (lobby != null && !lobby.isKitAvailable(player, kit.getId())) {
                continue;
            }
            ItemStack icon = kit.getIcon();
            ItemMeta meta = icon.getItemMeta();
            boolean selected = selectedKit != null &&
                kit.getId().equals(selectedKit.getId());
            meta.displayName(
                Component.text(
                    kit.getDisplayName() + (selected ? " [Selected]" : ""),
                    selected ? NamedTextColor.GREEN : NamedTextColor.GOLD
                )
            );
            meta.setEnchantmentGlintOverride(selected);
            meta.lore(
                List.of(
                    Component.text(
                        kit.getDescription(),
                        NamedTextColor.GRAY
                    ),
                    Component.text(
                        "Difficulty: ",
                        NamedTextColor.AQUA
                    ).append(kit.getStarRatingComponent()),
                    Component.text(
                        selected ? "Already selected" : "Click to select",
                        selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW
                    )
                )
            );
            icon.setItemMeta(meta);
            gui.addItem(
                new GuiItem(icon, event -> {
                    event.setCancelled(true);
                    if (lobby == null || !lobby.canSelectKit(player)) {
                        player.sendMessage(
                            ChatColor.DARK_RED +
                                "Kit selection is no longer available."
                        );
                        return;
                    }

                    gui.close(player);
                    selectKit(player, kit, getPlayerKit(player));
                })
            );
        }
        ItemStack randomKit = new ItemStack(Material.NETHER_STAR);
        ItemMeta randomMeta = randomKit.getItemMeta();
        randomMeta.setDisplayName(ChatColor.GREEN + "Random kit");
        randomKit.setItemMeta(randomMeta);
        gui.setItem(
            53,
            new GuiItem(randomKit, event -> {
                event.setCancelled(true);
                ManaKitLobbyType.ManaKitLobbyInstance lobby =
                    ManaKitLobbyType.getLobbyForPlayer(player);
                if (lobby == null || !lobby.canSelectKit(player)) {
                    player.sendMessage(
                        ChatColor.DARK_RED +
                            "Kit selection is no longer available."
                    );
                    return;
                }

                gui.close(player);
                selectRandomKit(player);
            })
        );
        gui.open(player);
    }

    public ManaKit selectRandomKit(Player player) {
        List<ManaKit> kits = new ArrayList<>(manaKitRegistry.values());
        ManaKitLobbyType.ManaKitLobbyInstance lobby =
            ManaKitLobbyType.getLobbyForPlayer(player);
        if (lobby != null) {
            kits.removeIf(kit -> !lobby.isKitAvailable(player, kit.getId()));
        }
        if (kits.isEmpty()) return null;
        ManaKit kit = kits.get(
            ThreadLocalRandom.current().nextInt(kits.size())
        );
        selectKit(player, kit, getPlayerKit(player));
        return kit;
    }

    private void selectKit(Player player, ManaKit kit, ManaKit currentKit) {
        ManaKitLobbyType.ManaKitLobbyInstance lobby =
            ManaKitLobbyType.getLobbyForPlayer(player);
        if (lobby != null && !lobby.isKitAvailable(player, kit.getId())) {
            player.sendMessage(
                ChatColor.RED + "You have already used that kit this match."
            );
            return;
        }
        if (currentKit != null && !currentKit.equals(kit)) {
            unsetPlayerKit(player);
        }
        player
            .getPersistentDataContainer()
            .set(MANA_KIT, PersistentDataType.STRING, kit.getId());
        player.sendMessage(
            ChatColor.WHITE +
                "You have selected " +
                ChatColor.GOLD +
                kit.getDisplayName() +
                ChatColor.WHITE +
                ". Your kit will be given when combat begins."
        );
        if (player.getScoreboardTags().contains("combat")) {
            givePlayerKit(player, kit);
        }
    }

    private void unsetPlayerKit(Player player) {
        ManaKit kit = getPlayerKit(player);
        if (kit == null) return;
        player.sendMessage(
            ChatColor.GRAY +
                "You have unequipped " +
                ChatColor.GOLD +
                kit.getDisplayName() +
                ChatColor.GRAY +
                "."
        );
    }

    private void setPlayerKit(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate == null || !chestplate.hasItemMeta()) return;

        ItemMeta meta = chestplate.getItemMeta();
        if (
            !meta
                .getPersistentDataContainer()
                .has(MANA_KIT, PersistentDataType.STRING)
        ) return;

        String kitId = meta
            .getPersistentDataContainer()
            .get(MANA_KIT, PersistentDataType.STRING);
        if (kitId == null) return;
        ManaKitLobbyType.ManaKitLobbyInstance lobby =
            ManaKitLobbyType.getLobbyForPlayer(player);
        if (lobby != null && !lobby.isKitAvailable(player, kitId)) return;
        ManaKit kit = getPlayerKit(player);

        if (kit != null) return;

        kit = getKit(kitId);
        if (kit == null) return;
        pdc.set(MANA_KIT, PersistentDataType.STRING, kit.getId());
        kit.resetKit(player);
        player.sendMessage(
            ChatColor.WHITE +
                "You have selected " +
                ChatColor.GOLD +
                kit.getDisplayName() +
                ChatColor.WHITE +
                " successfully!"
        );
    }

    private void manakitGameLoop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : SnapshotPvpPlugin.server.getOnlinePlayers()) {
                    if (ManaKit.isInShadowWorld(player)) {
                        int ticksRemaining = ManaKit.getShadowWorldTimer(player);
                        if (ticksRemaining <= 1) {
                            if (!ManaKit.returnFromShadowWorld(player)) {
                                ManaKit kit = getPlayerKit(player);
                                if (kit != null) kit.onShadowTick(player);
                            }
                            continue;
                        }
                        ManaKit.setShadowWorldTimer(
                            player,
                            ticksRemaining - 1
                        );
                        ManaKit kit = getPlayerKit(player);
                        if (kit != null) kit.onShadowTick(player);
                        continue;
                    }
                    Set<String> playerTags = player.getScoreboardTags();
                    setPlayerKit(player);

                    if (!hasKit(player)) {
                        continue;
                    }

                    ManaKit kit = getPlayerKit(player);
                    if (kit == null) continue;

                    if (playerTags.contains("fighting")) {
                        if (!playerTags.contains("combat")) {
                            enterCombat(player);
                        }
                        kit.onCombatTick(player);
                    } else {
                        if (playerTags.contains("combat")) {
                            SnapshotPvpPlugin.setPlayerScore(
                                player,
                                "dummyKillstreak",
                                0
                            );
                            player.removeScoreboardTag("combat");
                            kit.onLeaveCombat(player);
                        }
                        kit.onIdleTick(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (
                meta
                    .getPersistentDataContainer()
                    .has(MANA_FOOD, PersistentDataType.BOOLEAN)
            ) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        ManaKit kit = getPlayerKit(p);
        if (kit == null) return;

        if (!kit.canDropItem(p, e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        if (!(e.getView().getPlayer() instanceof Player p)) return;
        ManaKit kit = getPlayerKit(p);
        if (kit == null) return;

        ItemStack result = e.getInventory().getResult();
        if (result == null) return;

        if (!kit.canCraft(p, result)) {
            e.getInventory().setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ManaKit kit = getPlayerKit(p);
        if (kit == null) return;

        if (!kit.canCraft(p, e.getRecipe().getResult())) {
            e.setCancelled(true);
        }
    }
}
