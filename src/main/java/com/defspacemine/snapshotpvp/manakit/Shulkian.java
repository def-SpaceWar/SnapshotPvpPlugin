package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class Shulkian extends ManaKit {
    @Override
    public org.bukkit.inventory.ItemStack getIcon() {
        return getIconFromShulker(-189, 4, -185);
    }


    final int arrowRestock = 120; // 4 arrows every 6 seconds, they auto reset
    final NamespacedKey arrowRestockCounter = ManaKitListener.MANA_KIT_DATA0;
    private static final int DAMAGE_HITS_FOR_BOW = 10;

    private final NamespacedKey teleportArrowKey = new NamespacedKey(
        "defspacemine",
        "shulkian_teleport_arrow"
    );
    private final NamespacedKey knockbackBowKey = new NamespacedKey(
        "defspacemine",
        "shulkian_knockback_bow"
    );
    private final NamespacedKey damageHitsCounter =
        ManaKitListener.MANA_KIT_DATA1;

    private ItemStack arrows;
    private final ItemStack teleportArrow;
    private final ItemStack knockbackBow;

    public Shulkian() {
        super("shulkian", "Shulkian", "[Ranged Sniper Tank]", 3);

        {
            arrows = new ItemStack(Material.TIPPED_ARROW, 4);
            PotionMeta meta = (PotionMeta) arrows.getItemMeta();
            meta.displayName(
                Component.text("Arrow of Levitation").decoration(
                    TextDecoration.ITALIC,
                    false
                )
            );
            meta.addCustomEffect(
                new PotionEffect(PotionEffectType.LEVITATION, 480, 1),
                true
            );
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            arrows.setItemMeta(meta);
        }

        teleportArrow = new ItemStack(Material.TIPPED_ARROW, 3);
        PotionMeta teleportArrowMeta = (PotionMeta) teleportArrow.getItemMeta();
        teleportArrowMeta.displayName(
            Component.text("Arrow of Teleportation")
                .decoration(TextDecoration.ITALIC, false)
                .color(NamedTextColor.GOLD)
        );
        teleportArrowMeta.addCustomEffect(
            new PotionEffect(PotionEffectType.LEVITATION, 480, 1),
            true
        );
        teleportArrowMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        teleportArrowMeta.setColor(Color.fromRGB(0x9B59B6));
        teleportArrowMeta
            .getPersistentDataContainer()
            .set(teleportArrowKey, PersistentDataType.BYTE, (byte) 1);
        teleportArrow.setItemMeta(teleportArrowMeta);

        knockbackBow = new ItemStack(Material.BOW);
        Damageable knockbackBowMeta = (Damageable) knockbackBow.getItemMeta();
        knockbackBowMeta.displayName(
            Component.text("Super Heavy Shot")
                .decoration(TextDecoration.ITALIC, false)
                .color(NamedTextColor.YELLOW)
        );
        knockbackBowMeta.addEnchant(Enchantment.KNOCKBACK, 10, true);
        knockbackBowMeta.addEnchant(Enchantment.PUNCH, 10, true);
        knockbackBowMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        knockbackBowMeta.setMaxDamage(1);
        knockbackBowMeta
            .getPersistentDataContainer()
            .set(knockbackBowKey, PersistentDataType.BYTE, (byte) 1);
        knockbackBow.setItemMeta(knockbackBowMeta);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -189,
            4,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, arrowRestock);
        pdc.set(damageHitsCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        applyShiftResistance(p);

        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int arrowRestockC = pdc.get(
            arrowRestockCounter,
            PersistentDataType.INTEGER
        );
        int bowDamageHits = pdc.getOrDefault(
            damageHitsCounter,
            PersistentDataType.INTEGER,
            0
        );
        String bowProgress = hasKnockbackBow(p)
            ? ChatColor.GREEN + "READY"
            : ChatColor.WHITE.toString() +
              bowDamageHits +
              "/" +
              DAMAGE_HITS_FOR_BOW;

        p.sendActionBar(
            ChatColor.LIGHT_PURPLE +
                "Reload: " +
                ChatColor.WHITE +
                arrowRestockC +
                "/" +
                arrowRestock +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.YELLOW +
                "Super Heavy Shot: " +
                bowProgress +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.RED +
                "Killstreak: " +
                ChatColor.WHITE +
                killstreak +
                "/2"
        );

        p.addPotionEffect(
            new PotionEffect(
                PotionEffectType.RESISTANCE,
                100,
                killstreak >= 1 ? 1 : 0
            )
        );
        if (
            killstreak >= 2 && !p.hasPotionEffect(PotionEffectType.HEALTH_BOOST)
        ) p.addPotionEffect(
            new PotionEffect(PotionEffectType.HEALTH_BOOST, -1, 1)
        );

        pdc.set(
            arrowRestockCounter,
            PersistentDataType.INTEGER,
            arrowRestockC + 1
        );

        PlayerInventory inv = p.getInventory();
        if (arrowRestockC >= arrowRestock) {
            SnapshotPvpPlugin.clearInv(p, arrows);
            inv.addItem(arrows);
            playItemGainFeedback(p);
            pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onShadowTick(Player p) {
        super.onShadowTick(p);
        applyShiftResistance(p);
    }

    private void applyShiftResistance(Player p) {
        if (p.isSneaking()) p.addPotionEffect(
            new PotionEffect(PotionEffectType.RESISTANCE, 2, 2)
        );
    }

    @Override
    public void onLeaveCombat(Player p) {
        SnapshotPvpPlugin.clearInv(p, Material.TIPPED_ARROW);
        removeKnockbackBow(p);
        p.clearActivePotionEffects();
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, arrowRestock);
        pdc.set(damageHitsCounter, PersistentDataType.INTEGER, 0);
        p.getInventory().addItem(teleportArrow);
        playItemGainFeedback(p);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        SnapshotPvpPlugin.clearInv(p, teleportArrow);
        p.getInventory().addItem(teleportArrow);
        playItemGainFeedback(p);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int previousHits = pdc.getOrDefault(
            damageHitsCounter,
            PersistentDataType.INTEGER,
            0
        );
        int hits = Math.min(DAMAGE_HITS_FOR_BOW, previousHits + 1);
        if (hits < DAMAGE_HITS_FOR_BOW) {
            pdc.set(damageHitsCounter, PersistentDataType.INTEGER, hits);
            return;
        }

        if (hasKnockbackBow(p)) {
            pdc.set(damageHitsCounter, PersistentDataType.INTEGER, 0);
            return;
        }

        p.getInventory().addItem(knockbackBow.clone()).isEmpty();
        pdc.set(damageHitsCounter, PersistentDataType.INTEGER, 0);
        SnapshotPvpPlugin.clearInv(p, teleportArrow);
        p.getInventory().addItem(teleportArrow);
        playItemGainFeedback(p);
    }

    @Override
    public void onProjectileHit(Player p, ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow)) return;
        if (
            !arrow
                .getPersistentDataContainer()
                .has(teleportArrowKey, PersistentDataType.BYTE)
        ) return;

        Entity hitEntity = e.getHitEntity();
        Location destination =
            hitEntity == null ? arrow.getLocation() : hitEntity.getLocation();
        if (hitEntity != null) {
            Location playerLocation = p.getLocation();
            destination.setYaw(playerLocation.getYaw());
            destination.setPitch(playerLocation.getPitch());
        }
        if (!canUseTeleportAbility(p, destination)) return;

        if (p.teleport(destination)) {
            arrow.remove();
            playAbilityFeedback(p);
        }
    }

    private boolean hasKnockbackBow(Player p) {
        PlayerInventory inventory = p.getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (isKnockbackBow(item)) return true;
        }
        return (
            isKnockbackBow(inventory.getItemInOffHand()) ||
            isKnockbackBow(p.getItemOnCursor())
        );
    }

    private void removeKnockbackBow(Player p) {
        PlayerInventory inventory = p.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isKnockbackBow(contents[slot])) inventory.setItem(slot, null);
        }
        if (isKnockbackBow(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
        if (isKnockbackBow(p.getItemOnCursor())) p.setItemOnCursor(null);
    }

    private boolean isKnockbackBow(ItemStack item) {
        return (
            item != null &&
            item.getType() == Material.BOW &&
            item.hasItemMeta() &&
            item
                .getItemMeta()
                .getPersistentDataContainer()
                .has(knockbackBowKey, PersistentDataType.BYTE)
        );
    }

    @Override
    public void onShootBow(Player p, EntityShootBowEvent e) {
        if (
            isTeleportArrow(e.getConsumable()) &&
            e.getProjectile() instanceof AbstractArrow projectile
        ) {
            projectile
                .getPersistentDataContainer()
                .set(teleportArrowKey, PersistentDataType.BYTE, (byte) 1);
            projectile.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
    }

    private boolean isTeleportArrow(ItemStack item) {
        return (
            item != null &&
            item.getType() == Material.TIPPED_ARROW &&
            item.hasItemMeta() &&
            item
                .getItemMeta()
                .getPersistentDataContainer()
                .has(teleportArrowKey, PersistentDataType.BYTE)
        );
    }
}
