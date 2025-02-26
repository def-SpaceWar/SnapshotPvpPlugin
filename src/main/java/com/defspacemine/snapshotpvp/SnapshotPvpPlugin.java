package com.defspacemine.snapshotpvp;

import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;

import io.papermc.lib.PaperLib;

public final class SnapshotPvpPlugin extends JavaPlugin {
    final NamespacedKey UPDATE_LORE = new NamespacedKey("combatium", "update_lore");
    final NamespacedKey ATTACK_DAMAGE = new NamespacedKey("combatium", "attack_damage");
    final NamespacedKey ATTACK_SPEED = new NamespacedKey("combatium", "attack_speed");
    final NamespacedKey ATTACK_REACH = new NamespacedKey("combatium", "reach");
    final NamespacedKey BLOCK_ANGLE = new NamespacedKey("combatium", "block_angle");
    final NamespacedKey BLOCK_AMOUNT = new NamespacedKey("combatium", "block_amount");
    final NamespacedKey BLOCK_KNOCKBACK_REDUCTION = new NamespacedKey("combatium", "knockback_reduction");

    @Override
    public void onEnable() {
        PaperLib.suggestPaper(this);
        saveDefaultConfig();

        new JoinListener(this);

        BukkitScheduler scheduler = getServer().getScheduler();
        scheduler.scheduleSyncRepeatingTask(this, new ManaKitHandler(this), 0L, 1L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /hub
        // /kit
        return false;
    }
}

class JoinListener implements Listener {
    private final SnapshotPvpPlugin plugin;

    public JoinListener(SnapshotPvpPlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        {
                AttributeInstance attr = player.getAttribute(Attribute.ATTACK_SPEED);
                attr.setBaseValue(4.0);
        }
        player.sendMessage("set ur base attributes :D");
    }
}

class ManaKitHandler implements Runnable {
    //  private final SnapshotPvpPlugin plugin;
    private final Server server;

    ManaKitHandler(SnapshotPvpPlugin plugin) {
        //  this.plugin = plugin;
        this.server = plugin.getServer();
    }

    @Override
    public void run() {
        for (Player player : server.getOnlinePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0));
        }
    }
}
