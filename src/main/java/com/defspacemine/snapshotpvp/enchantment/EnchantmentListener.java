package com.defspacemine.snapshotpvp.enchantment;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.defspacemine.snapshotpvp.customegg.CustomEggListener;
import com.defspacemine.snapshotpvp.manakit.FireInTheHole;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent.Payload.Custom;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class EnchantmentListener implements Listener {
    public static Enchantment EXPLOSIVE_HOOK;

    private final JavaPlugin plugin;

    public EnchantmentListener(JavaPlugin plugin) {
        this.plugin = plugin;

        EXPLOSIVE_HOOK = Registry.ENCHANTMENT.get(new NamespacedKey("defspacemine", "explosive_hook"));
    }

    public Enchantment getExplosiveHook() {
        return EXPLOSIVE_HOOK;
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        Player player = e.getPlayer();
        ItemStack rod = player.getInventory().getItem(e.getHand());

        if (rod == null || rod.getType() != Material.FISHING_ROD)
            return;

        if (!rod.containsEnchantment(EXPLOSIVE_HOOK))
            return;

        int level = rod.getEnchantmentLevel(EXPLOSIVE_HOOK);
        FishHook hook = e.getHook();

        new BukkitRunnable() {
            boolean enabled = false;

            @Override
            public void run() {
                if (hook == null || hook.isDead() || !hook.isValid()) {
                    cancel();
                    return;
                }

                if (enabled)
                    explosiveHook(player, hook.getLocation(), level);

                if (hook.getVelocity().lengthSquared() < 0.001) {
                    enabled = true;
                    if (hook.getHookedEntity() != null) {
                        explosiveHook(player, hook.getLocation(), level);
                        cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void explosiveHook(Player p, Location loc, int level) {
        Creeper creeper = (Creeper) loc.getWorld().spawnEntity(loc, EntityType.CREEPER);
        CustomEggListener.injectOwner(creeper, p);
        creeper.customName(Component.text("Explosive Hook")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        creeper.setExplosionRadius(level);
        creeper.explode();
    }
}
