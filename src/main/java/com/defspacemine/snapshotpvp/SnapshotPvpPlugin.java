package com.defspacemine.snapshotpvp;

import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.defspacemine.snapshotpvp.customegg.CustomEggListener;
import com.defspacemine.snapshotpvp.enchantment.EnchantmentListener;
import com.defspacemine.snapshotpvp.manakit.ManaKitListener;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import de.tr7zw.nbtapi.NBT;
import io.papermc.lib.PaperLib;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public final class SnapshotPvpPlugin extends JavaPlugin implements Listener {
    public static SnapshotPvpPlugin instance;

    public static final NamespacedKey BLOCK_KNOCKBACK_REDUCTION = new NamespacedKey("combatium", "knockback_reduction");

    public static Scoreboard scoreboard;
    public static Server server;
    public static Logger logger;

    public static int getPlayerScore(Player p, String objective) {
        Objective o = scoreboard.getObjective(objective);
        if (o == null)
            return 0;
        return o.getScore(p).getScore();
    }

    public static boolean setPlayerScore(Player p, String objective, int value) {
        Objective o = scoreboard.getObjective(objective);
        if (o == null)
            return false;
        o.getScore(p).setScore(value);
        return true;
    }

    public static boolean addPlayerScore(Player p, String objective, int value) {
        Objective o = scoreboard.getObjective(objective);
        if (o == null)
            return false;
        Score score = o.getScore(p);
        int prev = 0;
        try {
            prev = score.getScore();
        } catch (Error e) {
        }
        score.setScore(prev + value);
        return true;
    }

    public static Team getTeam(Player p) {
        return scoreboard.getEntryTeam(p.getName());
    }

    public static Team getTeamE(Entity e) {
        return scoreboard.getEntryTeam(e.getUniqueId().toString());
    }

    public static Team getTeamG(Entity e) {
        return e instanceof Player p ? getTeam(p) : getTeamE(e);
    }

    public static void addToTeam(Player p, Entity e) {
        Team team = getTeam(p);
        if (team == null)
            return;
        team.addEntity(e);
    }

    public static TextColor getTeamColor(Entity e) {
        Team team = scoreboard.getEntryTeam(e.getName());
        return (team != null) ? team.color() : NamedTextColor.WHITE;
    }

    public static void clearInv(PlayerInventory inv, Material type) {
        inv.remove(type);
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand.getType() == type)
            inv.setItemInOffHand(null);
    }

    public static void clearInv(PlayerInventory inv, ItemStack type) {
        ItemStack stack = type.clone();
        stack.setAmount(type.getAmount() * 99);
        inv.removeItemAnySlot(stack);
    }

    @Override
    public void onEnable() {
        instance = this;
        PaperLib.suggestPaper(this);
        saveDefaultConfig();
        server = getServer();
        server.getPluginManager().registerEvents(this, this);
        logger = getLogger();
        SnapshotPvpPlugin.scoreboard = server.getScoreboardManager().getMainScoreboard();

        if (!NBT.preloadApi()) {
            logger.warning("NBT-API wasn't initialized properly, disabling the plugin");
            server.getPluginManager().disablePlugin(this);
            return;
        }

        new WeatherManager(this);
        server.getPluginManager().registerEvents(new AntiSwapExploit(this), this);
        server.getPluginManager().registerEvents(new CustomEggListener(this), this);
        server.getPluginManager().registerEvents(new EnchantmentListener(this), this);
        server.getPluginManager().registerEvents(new ManaKitListener(this), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar()
                    .register(Commands.literal("hub")
                            .executes((CommandContext<CommandSourceStack> ctx) -> {
                                hub(ctx.getSource().getSender());
                                return Command.SINGLE_SUCCESS;
                            })
                            .build());

            commands.registrar().register(
                    Commands.literal("kit")
                            .executes((CommandContext<CommandSourceStack> ctx) -> {
                                ManaKitListener.instance.kit(ctx.getSource().getSender(), null);
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("id", StringArgumentType.string())
                                    .executes((CommandContext<CommandSourceStack> ctx) -> {
                                        String id = StringArgumentType.getString(ctx, "id");
                                        ManaKitListener.instance.kit(ctx.getSource().getSender(), id);
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .build());

            commands.registrar().register(
                    Commands.literal("weatherqueue")
                            .requires(source -> source.getSender().hasPermission("admin.weather"))
                            .then(Commands.literal("clear")
                                    .executes(ctx -> {
                                        Player p = (Player) ctx.getSource().getSender();
                                        WeatherManager.instance.clearWeather(p.getWorld());
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .then(Commands.literal("rain")
                                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                            .executes(ctx -> {
                                                Player p = (Player) ctx.getSource().getSender();
                                                int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                                WeatherManager.instance.queueWeather(p.getWorld(),
                                                        WeatherManager.WeatherType.RAIN, seconds * 20L);
                                                return Command.SINGLE_SUCCESS;
                                            })))
                            .then(Commands.literal("storm")
                                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                            .executes(ctx -> {
                                                Player p = (Player) ctx.getSource().getSender();
                                                int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                                WeatherManager.instance.queueWeather(p.getWorld(),
                                                        WeatherManager.WeatherType.STORM, seconds * 20L);
                                                return Command.SINGLE_SUCCESS;
                                            })))
                            .build());
        });
    }

    public void hub(CommandSender sender) {
        if (!(sender instanceof Player))
            return;

        Player player = (Player) sender;
        if (player.getScoreboardTags().contains("fighting")) {
            player.sendMessage(ChatColor.DARK_RED + "You are fighting!");
            return;
        }

        player.clearActivePotionEffects();

        player.teleport(new Location(
                Bukkit.getWorld("goopshotpeshvp"),
                -299.5, 2, -292.5,
                180, 0));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        server.sendMessage(Component.text(player.getName() + " joined. :D"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        server.sendMessage(Component.text(player.getName() + " left. ;["));
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent e) {
        if (e.getEntity() instanceof TNTPrimed tnt)
            e.blockList().clear();
    }
}
