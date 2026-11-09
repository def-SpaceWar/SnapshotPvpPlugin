package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public class Tridentite extends ManaKit {
    final NamespacedKey hasTridentCounter = ManaKitListener.MANA_KIT_DATA0;
    final int tridentReturn = 600; // 30 seconds before trident auto return
    final NamespacedKey tridentReturnCounter = ManaKitListener.MANA_KIT_DATA1;

    private ItemStack trident;

    public Tridentite() {
        super("tridentite", "Tridentite", "[Melee Movement]", 3);

        trident = new ItemStack(Material.TRIDENT);
        ItemMeta meta = trident.getItemMeta();
        meta.addEnchant(Enchantment.IMPALING, 5, true);
        meta.setUnbreakable(true);
        trident.setItemMeta(meta);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

		ManaKitListener.giveItemsFromShulker(p, "goopshotpeshvp", -189, 1, -185);
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(tridentReturnCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int hasTridentC = pdc.get(hasTridentCounter, PersistentDataType.INTEGER);
        int tridentReturnC = pdc.get(tridentReturnCounter, PersistentDataType.INTEGER);

        String displayMessage = ChatColor.RED + "Killstreak: " + ChatColor.WHITE + killstreak + "/2";

        if (SnapshotPvpPlugin.countInv(p, Material.TRIDENT) > 0) {
            displayMessage = ChatColor.AQUA + "Trident!"
                    + ChatColor.GRAY + " | " + displayMessage;
            pdc.set(tridentReturnCounter, PersistentDataType.INTEGER, 0);
            pdc.set(hasTridentCounter, PersistentDataType.INTEGER, 1);

            if (hasTridentC == 0) {
                double health = p.getHealth();
                double maxHealth = p.getMaxHealth();
                p.setHealth(Math.min(maxHealth, health + (maxHealth - health) * .35));
            }
        } else {
            displayMessage = ChatColor.DARK_AQUA + "Trident: " + ChatColor.WHITE + tridentReturnC + "/"
                    + tridentReturn
                    + ChatColor.GRAY + " | " + displayMessage;
            pdc.set(tridentReturnCounter, PersistentDataType.INTEGER, tridentReturnC + (killstreak >= 2 ? 3 : 1));
            pdc.set(hasTridentCounter, PersistentDataType.INTEGER, 0);

            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 80, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2));
        }

        p.sendActionBar(displayMessage);

        if (killstreak >= 1 && !p.hasPotionEffect(PotionEffectType.REGENERATION))
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, -1, 1));
        // if (killstreak >= 2)
        //     p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0));

        if (tridentReturnC >= tridentReturn) {
            SnapshotPvpPlugin.clearInv(p, Material.CHORUS_FRUIT);
            retrieveTrident(p);
            pdc.set(tridentReturnCounter, PersistentDataType.INTEGER, 0);
        }
    }

    public void onDeath(Player p, PlayerDeathEvent e) {
        if (SnapshotPvpPlugin.countInv(p, Material.TRIDENT) == 0)
            retrieveTrident(p);
    }

    @Override
    public void onProjectileHit(Player p, ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Trident thrown)) return;
        if (!p.getUniqueId().equals(thrown.getOwnerUniqueId())) return;

        thrown.getWorld().spawnParticle(
            Particle.DUST,
            thrown.getLocation(),
            12,
            0.25,
            0.25,
            0.25,
            0,
            new Particle.DustOptions(Color.fromRGB(40, 190, 220), 1.2f)
        );
        thrown.getWorld().playSound(
            thrown.getLocation(),
            Sound.ITEM_TRIDENT_HIT,
            0.9f,
            1.2f
        );
    }

    @Override
    public void onLeaveCombat(Player p) {
        p.clearActivePotionEffects();
        resetKit(p);
    }

    private void retrieveTrident(Player p) {
        p.getInventory().addItem(trident);
        playItemGainFeedback(p);
        for (Entity e : p.getWorld().getEntities())
            if (e instanceof Trident t)
                if (t.getOwnerUniqueId().equals(p.getUniqueId()))
                    t.remove();
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(hasTridentCounter, PersistentDataType.INTEGER, 1);
        pdc.set(tridentReturnCounter, PersistentDataType.INTEGER, 0);
    }
}
