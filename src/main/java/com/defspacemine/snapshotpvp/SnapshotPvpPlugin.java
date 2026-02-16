package com.defspacemine.snapshotpvp;

import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import com.defspacemine.snapshotpvp.customegg.CustomEggListener;
import com.defspacemine.snapshotpvp.enchantment.EnchantmentListener;
import com.defspacemine.snapshotpvp.manakit.ManaKitListener;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import de.tr7zw.nbtapi.NBT;
import io.papermc.lib.PaperLib;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;

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

    public static void clearInv(PlayerInventory inv, Material type) {
        inv.remove(type);
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand.getType() == type)
            inv.setItemInOffHand(null);
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

        server.getPluginManager().registerEvents(new ManaKitListener(this), this);
        server.getPluginManager().registerEvents(new CustomEggListener(this), this);
        server.getPluginManager().registerEvents(new EnchantmentListener(this), this);

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
}
