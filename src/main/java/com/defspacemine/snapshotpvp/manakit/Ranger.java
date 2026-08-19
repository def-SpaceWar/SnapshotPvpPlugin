package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Ranger extends ManaKit {

    final int arrowRestock = 160; // arrow restock every 8 seconds. only runs when doesn't have an arrow
    final NamespacedKey arrowRestockCounter = ManaKitListener.MANA_KIT_DATA0;

    private ItemStack arrow;

    public Ranger() {
        super("ranger", "Ranger", "[Ranged Sniper]", 0);

        arrow = new ItemStack(Material.SPECTRAL_ARROW, 1);
        arrow.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -184,
            10,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int arrowRestockC = pdc.get(
            arrowRestockCounter,
            PersistentDataType.INTEGER
        );

        String displayMessage =
            ChatColor.RED +
            "Killstreak: " +
            ChatColor.WHITE +
            killstreak +
            "/2";

        PlayerInventory inv = p.getInventory();
        if (!inv.contains(Material.SPECTRAL_ARROW)) {
            displayMessage =
                ChatColor.YELLOW +
                "Arrow: " +
                ChatColor.WHITE +
                arrowRestockC +
                "/" +
                arrowRestock +
                ChatColor.GRAY +
                " | " +
                displayMessage;

            pdc.set(
                arrowRestockCounter,
                PersistentDataType.INTEGER,
                arrowRestockC + (killstreak >= 1 ? 2 : 1)
            );
        }

        p.sendActionBar(displayMessage);

        if (killstreak >= 0) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 1)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 2)
        );

        if (arrowRestockC >= arrowRestock) {
            inv.addItem(arrow);
            pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.SPECTRAL_ARROW);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, arrowRestock);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PlayerInventory inv = p.getInventory();
        inv.addItem(arrow, arrow, arrow);
    }

    @Override
    public void onProjectileHit(Player p, ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof SpectralArrow arrow)) return;
        if (!(e.getHitEntity() instanceof LivingEntity target)) return;
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, arrowRestock);
    }
}
