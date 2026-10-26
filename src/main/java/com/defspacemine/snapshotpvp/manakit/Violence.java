package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Violence extends ManaKit implements Listener {

    private final NamespacedKey progressKey = ManaKitListener.MANA_KIT_DATA0;
    private final NamespacedKey violenceDamageModKey = new NamespacedKey(
        "defspacemine",
        "violence_damage"
    );
    private final NamespacedKey violenceSpeedModKey = new NamespacedKey(
        "defspacemine",
        "violence_speed"
    );

    private final ItemStack apathyPotion;
    private ItemStack trident;

    public Violence() {
        super("violence", "Violence", "[Melee Damage]", 0);
        SnapshotPvpPlugin plugin = SnapshotPvpPlugin.getPlugin(
            SnapshotPvpPlugin.class
        );
        Bukkit.getPluginManager().registerEvents(this, plugin);

        {
            // Build Potion of Apathy
            apathyPotion = new ItemStack(Material.POTION);
            PotionMeta meta = (PotionMeta) apathyPotion.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.DARK_PURPLE + "Potion of Hatred");
                meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
                meta.setColor(Color.fromRGB(75, 0, 130)); // Dark purple look

                meta.addCustomEffect(
                    new PotionEffect(
                        PotionEffectType.WITHER,
                        200,
                        4,
                        false,
                        true,
                        true
                    ),
                    true
                );
                meta.addCustomEffect(
                    new PotionEffect(
                        PotionEffectType.REGENERATION,
                        400,
                        0,
                        false,
                        true,
                        true
                    ),
                    true
                );
                meta.addCustomEffect(
                    new PotionEffect(
                        PotionEffectType.SATURATION,
                        400,
                        0,
                        false,
                        true,
                        true
                    ),
                    true
                );
                meta.addCustomEffect(
                    new PotionEffect(
                        PotionEffectType.RESISTANCE,
                        400,
                        0,
                        false,
                        true,
                        true
                    ),
                    true
                );
                meta.addCustomEffect(
                    new PotionEffect(
                        PotionEffectType.STRENGTH,
                        400,
                        0,
                        false,
                        true,
                        true
                    ),
                    true
                );

                apathyPotion.setItemMeta(meta);
            }
        }

        {
            trident = new ItemStack(Material.TRIDENT);
            ItemMeta meta = trident.getItemMeta();
            meta.addEnchant(Enchantment.LOYALTY, 1, true);
            meta.setUnbreakable(true);
            trident.setItemMeta(meta);
        }
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -182,
            16,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(progressKey, PersistentDataType.INTEGER, 0);

        removeAttributes(p);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int progress = pdc.getOrDefault(
            progressKey,
            PersistentDataType.INTEGER,
            0
        );

        // 1. Dynamic Health-Based Attribute Scaling
        double healthRatio = p.getHealth() / p.getMaxHealth(); // 1.0 (full) down to 0.0 (dead)
        double damageMultiplier, speedMultiplier;
        damageMultiplier = speedMultiplier = Math.min(
            0.5,
            (1 - healthRatio) / 1.5
        );

        applyAttributes(p, damageMultiplier, speedMultiplier);

        // 2. Titan-like Red and Purple Particles when buff is above 40% (i.e., damageMultiplier > 0.40)
        if (damageMultiplier > 0.3) {
            p.getWorld().spawnParticle(
                Particle.DUST,
                p.getLocation().add(0, 1, 0),
                4,
                0.3,
                0.5,
                0.3,
                0.01,
                new DustOptions(Color.fromRGB(255, 0, 0), 1.2f) // Red dust
            );
            p.getWorld().spawnParticle(
                Particle.DUST,
                p.getLocation().add(0, 1, 0),
                4,
                0.3,
                0.5,
                0.3,
                0.01,
                new DustOptions(Color.fromRGB(128, 0, 128), 1.2f) // Purple dust
            );
        }

        // 3. Action bar display
        boolean hasApathy = hasApathyPotion(p);
        String apathyStatus = hasApathy
            ? ChatColor.GREEN + "READY"
            : ChatColor.WHITE + "" + progress + "/25";

        ChatColor buffColor;
        if (damageMultiplier > 0.3) {
            // Flicker between RED and LIGHT_PURPLE every 10 ticks
            long gameTime = p.getWorld().getGameTime();
            buffColor =
                gameTime % 10 < 5 ? ChatColor.RED : ChatColor.LIGHT_PURPLE;
        } else {
            buffColor = ChatColor.GRAY;
        }

        p.sendActionBar(
            buffColor +
                "Buff: " +
                ChatColor.WHITE +
                (int) (damageMultiplier * 100) +
                "%" +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.LIGHT_PURPLE +
                "Hatred: " +
                apathyStatus +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.RED +
                "Killstreak: " +
                ChatColor.WHITE +
                killstreak +
                "/4"
        );

        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 0)
        );
        // mana boost
        if (killstreak >= 3) p.addPotionEffect(
            new PotionEffect(PotionEffectType.HASTE, 100, 0)
        );
        // mana boost 2
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        incrementProgress(p);
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        incrementProgress(p);
    }

    private void incrementProgress(Player p) {
        if (hasApathyPotion(p)) return;
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int progress =
            pdc.getOrDefault(progressKey, PersistentDataType.INTEGER, 0) +
            (killstreak >= 4 ? 3 : killstreak >= 2 ? 2 : 1);
        if (progress >= 25) {
            pdc.set(progressKey, PersistentDataType.INTEGER, 25);
            p.getInventory().addItem(apathyPotion.clone());
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
        } else {
            pdc.set(progressKey, PersistentDataType.INTEGER, progress);
        }
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (
            e.getAction() != Action.RIGHT_CLICK_AIR &&
            e.getAction() != Action.RIGHT_CLICK_BLOCK
        ) return;
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (
            meta instanceof PotionMeta &&
            meta.getDisplayName().contains("Apathy")
        ) {
            // Let vanilla potion consumption handle effects automatically, reset progress counter so they can farm another one
            PersistentDataContainer pdc = p.getPersistentDataContainer();
            pdc.set(progressKey, PersistentDataType.INTEGER, 0);
        }
    }

    private boolean hasApathyPotion(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (
                item != null &&
                item.hasItemMeta() &&
                item.getItemMeta() instanceof PotionMeta meta
            ) {
                if (
                    meta.hasDisplayName() &&
                    meta.getDisplayName().contains("Hatred")
                ) {
                    return true;
                }
            }
        }
        return false;
    }

    private void applyAttributes(
        Player p,
        double damageBonus,
        double speedBonus
    ) {
        AttributeInstance damageAttr = p.getAttribute(Attribute.ATTACK_DAMAGE);
        AttributeInstance speedAttr = p.getAttribute(Attribute.MOVEMENT_SPEED);

        if (damageAttr != null) {
            damageAttr
                .getModifiers()
                .stream()
                .filter(m -> m.getKey().equals(violenceDamageModKey))
                .forEach(damageAttr::removeModifier);
            if (damageBonus > 0) {
                damageAttr.addModifier(
                    new AttributeModifier(
                        violenceDamageModKey,
                        damageBonus,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1
                    )
                );
            }
        }

        if (speedAttr != null) {
            speedAttr
                .getModifiers()
                .stream()
                .filter(m -> m.getKey().equals(violenceSpeedModKey))
                .forEach(speedAttr::removeModifier);
            if (speedBonus > 0) {
                speedAttr.addModifier(
                    new AttributeModifier(
                        violenceSpeedModKey,
                        speedBonus,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1
                    )
                );
            }
        }
    }

    private void removeAttributes(Player p) {
        AttributeInstance damageAttr = p.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr
                .getModifiers()
                .stream()
                .filter(m -> m.getKey().equals(violenceDamageModKey))
                .forEach(damageAttr::removeModifier);
        }

        AttributeInstance speedAttr = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr
                .getModifiers()
                .stream()
                .filter(m -> m.getKey().equals(violenceSpeedModKey))
                .forEach(speedAttr::removeModifier);
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.POTION);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        resetKit(p);
    }

    public void onDeath(Player p, PlayerDeathEvent e) {
        PlayerInventory inv = p.getInventory();
        if (
            !inv.contains(Material.TRIDENT) &&
            inv.getItemInOffHand().getType() != Material.TRIDENT
        ) retrieveTrident(p);
    }

    private void retrieveTrident(Player p) {
        p.getInventory().addItem(trident);
        for (Entity e : p.getWorld().getEntities())
            if (e instanceof Trident t) if (
                t.getOwnerUniqueId().equals(p.getUniqueId())
            ) t.remove();
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        if (p.isDead() || !p.getScoreboardTags().contains("combat")) return;
        p.addPotionEffect(
            new PotionEffect(PotionEffectType.RESISTANCE, 100, 4)
        );
        p.setHealth(1);

        if (!hasApathyPotion(p)) {
            p.getPersistentDataContainer().set(
                progressKey,
                PersistentDataType.INTEGER,
                25
            );
            incrementProgress(p);
        }
    }
}
