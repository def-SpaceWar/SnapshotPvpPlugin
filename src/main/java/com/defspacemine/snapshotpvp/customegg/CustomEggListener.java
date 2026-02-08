package com.defspacemine.snapshotpvp.customegg;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.custommob.*;

import net.kyori.adventure.text.Component;

public class CustomEggListener implements Listener {
    public final static NamespacedKey CUSTOM_EGG = new NamespacedKey("defspacemine", "custom_egg");
    public final static NamespacedKey OWNER = new NamespacedKey("defspacemine", "owner");
    public final static NamespacedKey CREEPER_CHAIN = new NamespacedKey("defspacemine", "creeper_chain");

    private static final Map<String, CustomMob> registry = new HashMap<>();

    public static void register(CustomMob mob) {
        registry.put(mob.getId(), mob);
    }

    public static CustomMob get(String id) {
        return registry.get(id);
    }

    public static boolean exists(String id) {
        return registry.containsKey(id);
    }

    public static ItemStack injectOwner(ItemStack egg, Player p) {
        ItemStack result = egg.clone();
        ItemMeta meta = egg.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(OWNER, PersistentDataType.STRING, p.getUniqueId().toString());
        result.setItemMeta(meta);
        return result;
    }

    public static Player getOwner(Entity entity) {
        String uuid = entity.getPersistentDataContainer().get(OWNER, PersistentDataType.STRING);

        if (uuid == null)
            return null;
        return Bukkit.getPlayer(UUID.fromString(uuid));
    }

    private final JavaPlugin plugin;

    public CustomEggListener(JavaPlugin plugin) {
        this.plugin = plugin;

        register(new FITHBomb1());
        register(new FITHBomb2());
        register(new FITHBomb3());
        register(new FITHNuke());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null)
            return;

        if (!e.hasItem())
            return;

        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = e.getItem();
        if (item == null || !item.getType().toString().endsWith("_SPAWN_EGG"))
            return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(CustomEggListener.CUSTOM_EGG, PersistentDataType.STRING);
        if (id == null)
            return;

        if (!exists(id))
            return;

        e.setCancelled(true);

        CustomMob mob = get(id);
        LivingEntity entity = mob.spawn(e.getClickedBlock().getLocation().add(0, 1, 0), pdc);

        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    @EventHandler
    public void onExplosionDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Creeper creeper))
            return;
        Player owner = getOwner(creeper);

        if (e.getEntity() instanceof Player victim) {
            if (owner == null)
                return;
            e.setCancelled(true);
            victim.damage(e.getFinalDamage(), owner);
        } else if (e.getEntity() instanceof Creeper c) {
            if (getOwner(e.getEntity()) == null)
                return;
            Boolean chainable = c.getPersistentDataContainer().get(CREEPER_CHAIN,
                    PersistentDataType.BOOLEAN);
            if (chainable == null || !chainable)
                return;
            e.setCancelled(true);
            if (creeper.isPowered())
                c.setPowered(true);
            c.explode();
        }
    }
}
