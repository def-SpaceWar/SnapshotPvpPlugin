package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.WeatherManager;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class AtlantianPrince extends ManaKit implements Listener {

    private final NamespacedKey floodChargeKey = ManaKitListener.MANA_KIT_DATA0;
    private final NamespacedKey bucketTimerKey = ManaKitListener.MANA_KIT_DATA1;

    public AtlantianPrince() {
        super("atlantian_prince", "Atlantian Prince", "[Melee Damage]", 1);
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
            -189,
            7,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(floodChargeKey, PersistentDataType.INTEGER, 0);
        pdc.set(bucketTimerKey, PersistentDataType.INTEGER, 0);

        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(p, Material.GOLDEN_HELMET); // Clear old flood item if present
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        int floodCharge = pdc.getOrDefault(
            floodChargeKey,
            PersistentDataType.INTEGER,
            0
        );
        int bucketTimer = pdc.getOrDefault(
            bucketTimerKey,
            PersistentDataType.INTEGER,
            0
        );

        // 1. Refill any empty buckets / lava buckets into water buckets every 5 seconds (100 ticks)
        bucketTimer++;
        if (bucketTimer >= 100) {
            bucketTimer = 0;
            refillBuckets(p);
        }
        pdc.set(bucketTimerKey, PersistentDataType.INTEGER, bucketTimer);

        // 2. Passives & Action bar status (including bucket refill countdown)
        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 1)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.STRENGTH, 100, 0)
        );

        boolean hasFloodItem = hasRoyalFloodItem(p);
        String floodStatus = hasFloodItem
            ? ChatColor.GREEN + "READY"
            : ChatColor.WHITE + "" + floodCharge + "/10";

        p.sendActionBar(
            ChatColor.BLUE +
                "Flood: " +
                floodStatus +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.DARK_AQUA +
                "Refill: " +
                ChatColor.WHITE +
                "" +
                bucketTimer +
                "/100" +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.RED +
                "Killstreak: " +
                ChatColor.WHITE +
                killstreak +
                "/2"
        );
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (hasRoyalFloodItem(p)) return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int charge =
            pdc.getOrDefault(floodChargeKey, PersistentDataType.INTEGER, 0) + 1;
        if (charge >= 10) {
            pdc.set(floodChargeKey, PersistentDataType.INTEGER, 0);
            giveRoyalFloodItem(p);
        } else {
            pdc.set(floodChargeKey, PersistentDataType.INTEGER, charge);
        }
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (
            e.getAction() != Action.RIGHT_CLICK_AIR &&
            e.getAction() != Action.RIGHT_CLICK_BLOCK
        ) return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.GOLDEN_HELMET) return;

        e.setCancelled(true);
        item.setAmount(item.getAmount() - 1);

        triggerRoyalFlood(p);
    }

    private void triggerRoyalFlood(Player p) {
        Location center = p.getLocation();
        int radius = 6; // Radius of the temporary flood area

        p.getWorld().playSound(
            center,
            Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED,
            1.2f,
            0.8f
        );
        p.sendMessage(
            ChatColor.AQUA + "" + ChatColor.BOLD + ">> ROYAL FLOOD ACTIVATED!"
        );

        List<Block> floodedBlocks = new ArrayList<>();

        // Flood the exact Y level temporarily in a 13x13 square area
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Block b = center.clone().add(x, 0, z).getBlock();
                if (b.getType() != Material.WATER && b.getType() == Material.AIR) {
                    b.setType(Material.WATER);
                    floodedBlocks.add(b);
                }
            }
        }

        // Automatically get rid of the water after 8 seconds (160 ticks)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Block b : floodedBlocks) {
                    if (b.getType() == Material.WATER) {
                        b.setType(Material.AIR);
                    }
                }
            }
        }.runTaskLater(SnapshotPvpPlugin.instance, 160L);
    }

    private void giveRoyalFloodItem(Player p) {
        ItemStack item = new ItemStack(Material.GOLDEN_HELMET, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(
                ChatColor.AQUA + "" + ChatColor.BOLD + "Royal Flood"
            );
            meta.setLore(
                List.of(
                    ChatColor.GRAY + "Right-click to temporarily flood",
                    ChatColor.AQUA + "your current Y-level for 8 seconds."
                )
            );
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            item.setItemMeta(meta);
        }
        p.getInventory().addItem(item);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.4f);
    }

    private boolean hasRoyalFloodItem(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == Material.GOLDEN_HELMET) {
                return true;
            }
        }
        return false;
    }

    private void refillBuckets(Player p) {
        PlayerInventory inventory = p.getInventory();
        boolean updated = false;

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (
                item != null &&
                (item.getType() == Material.BUCKET ||
                    item.getType() == Material.LAVA_BUCKET)
            ) {
                inventory.setItem(
                    i,
                    new ItemStack(Material.WATER_BUCKET, item.getAmount())
                );
                updated = true;
            }
        }

        ItemStack offHandItem = inventory.getItemInOffHand();
        if (
            offHandItem != null &&
            (offHandItem.getType() == Material.BUCKET ||
                offHandItem.getType() == Material.LAVA_BUCKET)
        ) {
            inventory.setItemInOffHand(
                new ItemStack(Material.WATER_BUCKET, offHandItem.getAmount())
            );
            updated = true;
        }

        if (updated) {
            p.updateInventory();
        }
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        playAbilityFeedback(p);
        p.setHealth(p.getHealth() + (p.getMaxHealth() - p.getHealth()) * .4);
        p.addPotionEffect(
            new PotionEffect(PotionEffectType.RESISTANCE, 100, 2)
        );
        WeatherManager.instance.queueWeather(
            p.getWorld(),
            WeatherManager.WeatherType.RAIN,
            400L
        );
    }

    @Override
    public void onLeaveCombat(Player p) {
        refillBuckets(p);
        resetKit(p);
    }
}
