package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Deadeye extends ManaKit {

    final double MARK_MULTIPLIER = 2;

    final NamespacedKey attackCounter = ManaKitListener.MANA_KIT_DATA0;
    final NamespacedKey markedUntilKey = ManaKitListener.MANA_KIT_DATA1;
    final NamespacedKey markedTargetKey = ManaKitListener.MANA_KIT_DATASTR0;

    final int requiredAttacks = 16;
    final int maxArrows = 20;
    final int markDurationTicks = 400;

    private final ItemStack normalArrowItem;
    private final ItemStack spectralArrowItem;

    public Deadeye() {
        super("deadeye", "Deadeye", "[Ranged Marksman]", 0);

        normalArrowItem = new ItemStack(Material.ARROW, 1);
        ItemMeta normalMeta = normalArrowItem.getItemMeta();
        if (normalMeta != null) {
            normalMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            normalArrowItem.setItemMeta(normalMeta);
        }

        spectralArrowItem = new ItemStack(Material.SPECTRAL_ARROW, 1);
        ItemMeta spectralMeta = spectralArrowItem.getItemMeta();
        if (spectralMeta != null) {
            spectralMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Precise Arrow");
            spectralMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            spectralArrowItem.setItemMeta(spectralMeta);
        }
    }

    private void capAndRestockArrows(Player p) {
        PlayerInventory inv = p.getInventory();
        int currentArrows = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == Material.ARROW) {
                currentArrows += item.getAmount();
            }
        }

        if (currentArrows < maxArrows) {
            ItemStack arrowsToAdd = normalArrowItem.clone();
            arrowsToAdd.setAmount(maxArrows - currentArrows);
            inv.addItem(arrowsToAdd);
        }
    }

    private void capAndRestockArrows(PlayerInventory inv) {
        int currentArrows = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == Material.ARROW) {
                currentArrows += item.getAmount();
            }
        }

        if (currentArrows < maxArrows) {
            ItemStack arrowsToAdd = normalArrowItem.clone();
            arrowsToAdd.setAmount(maxArrows - currentArrows);
            inv.addItem(arrowsToAdd);
        }
    }

    private void clearMark(Player deadeye) {
        if (deadeye == null) return;
        PersistentDataContainer pdc = deadeye.getPersistentDataContainer();
        pdc.remove(markedTargetKey);
        pdc.remove(markedUntilKey);
    }

    private boolean isMarked(Player p, LivingEntity entity) {
        if (p == null || entity == null || entity.isDead()) {
            if (p != null) clearMark(p);
            return false;
        }

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        String targetUuidStr = pdc.get(markedTargetKey, PersistentDataType.STRING);
        Integer expireTick = pdc.get(markedUntilKey, PersistentDataType.INTEGER);

        if (targetUuidStr == null || expireTick == null) return false;

        if (!targetUuidStr.equals(entity.getUniqueId().toString())) return false;

        if (Bukkit.getCurrentTick() >= expireTick) {
            clearMark(p);
            return false;
        }

        return true;
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);
        ManaKitListener.giveItemsFromShulker(p, "goopshotpeshvp", -188, 10, -185);
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(attackCounter, PersistentDataType.INTEGER, 0);
        clearMark(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        resetKit(p);
        capAndRestockArrows(p);
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.ARROW);
        SnapshotPvpPlugin.clearInv(inv, Material.SPECTRAL_ARROW);
        resetKit(p);
    }

    @Override
    public void onCombatTick(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        Integer attackC = pdc.getOrDefault(attackCounter, PersistentDataType.INTEGER, 0);
        String targetUuidStr = pdc.get(markedTargetKey, PersistentDataType.STRING);
        Integer expireTick = pdc.get(markedUntilKey, PersistentDataType.INTEGER);

        Component markedPrefix = Component.text().build();
        if (targetUuidStr != null && expireTick != null) {
            try {
                UUID targetUuid = UUID.fromString(targetUuidStr);
                Entity targetEntity = Bukkit.getEntity(targetUuid);

                if (targetEntity instanceof LivingEntity victim && isMarked(p, victim)) {
                    int remainingTicks = expireTick - Bukkit.getCurrentTick();
                    double remainingSec = remainingTicks / 20.0;
                    TextColor victimColor = NamedTextColor.WHITE;
                    if (victim instanceof Player targetPlayer) {
                        TextColor color = SnapshotPvpPlugin.getTeamColor(targetPlayer);
                        if (color != null) {
                            victimColor = color;
                        }
                    }

                    markedPrefix = Component.text("Marked ", NamedTextColor.RED)
                        .append(Component.text(victim.getName(), victimColor))
                        .append(Component.text(" (" + remainingTicks + ")", NamedTextColor.GRAY))
                        .append(Component.text("  |  ", NamedTextColor.GRAY));

                    TextColor teamColor = SnapshotPvpPlugin.getTeamColor(p);
                    Color particleColor = teamColor != null
                            ? Color.fromRGB(teamColor.red(), teamColor.green(), teamColor.blue())
                            : Color.RED;

                    Particle.DustOptions dustOptions = new Particle.DustOptions(particleColor, 1.2f);
                    victim.getWorld().spawnParticle(
                            Particle.DUST,
                            victim.getLocation().add(0, victim.getHeight() + 0.3, 0),
                            2,
                            0.1, 0.05, 0.1, 0.0,
                            dustOptions
                    );
                } else {
                    clearMark(p);
                }
            } catch (IllegalArgumentException ignored) {
                clearMark(p);
            }
        }

        Component actionBar = markedPrefix
            .append(Component.text("Pinpoint Precision: ", NamedTextColor.GOLD))
            .append(Component.text(attackC + "/" + requiredAttacks, NamedTextColor.WHITE))
            .append(Component.text("  |  ", NamedTextColor.GRAY))
            .append(Component.text("Killstreak: ", NamedTextColor.RED))
            .append(Component.text(killstreak + "/3", NamedTextColor.WHITE));

        p.sendActionBar(actionBar);

        if (killstreak == 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0));
        if (killstreak >= 3)
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1));

        PlayerInventory inv = p.getInventory();
        if (attackC >= requiredAttacks) {
            capAndRestockArrows(inv);
            SnapshotPvpPlugin.clearInv(inv, Material.SPECTRAL_ARROW);
            inv.addItem(spectralArrowItem);
            pdc.set(attackCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        Integer attackC = pdc.get(attackCounter, PersistentDataType.INTEGER);
        if (attackC == null) attackC = 0;
        pdc.set(attackCounter, PersistentDataType.INTEGER, attackC + (killstreak > 0 ? 2 : 1));

        if (e.getEntity() instanceof LivingEntity victim && isMarked(p, victim)) {
            e.setDamage(e.getDamage() * MARK_MULTIPLIER);
        }
    }

    @Override
    public void onDamageInWorld(Player p, Player attacker, Entity victim, EntityDamageByEntityEvent e) {
        if (victim instanceof LivingEntity l && isMarked(p, l)) {
            e.setDamage(e.getDamage() * MARK_MULTIPLIER);
        }
    }

    @Override
    public void onProjectileHit(Player p, ProjectileHitEvent e) {
        if (e.getEntity() instanceof SpectralArrow && e.getHitEntity() instanceof LivingEntity victim) {
            int expireTick = Bukkit.getCurrentTick() + markDurationTicks;

            PersistentDataContainer pdc = p.getPersistentDataContainer();
            pdc.set(markedUntilKey, PersistentDataType.INTEGER, expireTick);
            pdc.set(markedTargetKey, PersistentDataType.STRING, victim.getUniqueId().toString());

            victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, markDurationTicks, 0, false, false));
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 0.5f);
        }
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        capAndRestockArrows(p);
    }
}
