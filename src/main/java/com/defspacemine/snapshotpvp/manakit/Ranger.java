package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.w3c.dom.Text;

public class Ranger extends ManaKit {

    private static final int MAX_ARROWS = 3;

    final int arrowRestock = 120; // arrow restock every 6 seconds. only runs when doesn't have an arrow
    final NamespacedKey arrowRestockCounter = ManaKitListener.MANA_KIT_DATA0;

    private ItemStack arrow;
    private ItemStack kbSword;

    public Ranger() {
        super("ranger", "Ranger", "[Ranged Sniper]", 3);

        arrow = new ItemStack(Material.SPECTRAL_ARROW, 1);
        arrow.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);

        {
            kbSword = new ItemStack(Material.IRON_SWORD, 1);
            ItemMeta meta = kbSword.getItemMeta();
            Damageable d = (Damageable) meta;
            d.setDamage(249);
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, false);
            meta.addEnchant(Enchantment.KNOCKBACK, 10, true);
            meta.customName(
                Component.text("The Ranginator")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false)
            );
            kbSword.setItemMeta(meta);
        }
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
            inv.addItem(arrow, arrow);
            pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.SPECTRAL_ARROW);
        SnapshotPvpPlugin.clearInv(inv, Material.IRON_SWORD);
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
        int arrowCount = countArrows(inv);
        while (arrowCount < MAX_ARROWS) {
            inv.addItem(arrow);
            arrowCount++;
        }
        SnapshotPvpPlugin.clearInv(inv, Material.IRON_SWORD);
        inv.addItem(kbSword);
    }

    @Override
    public void onProjectileHit(Player p, ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof SpectralArrow arrow)) return;
        if (!(e.getHitEntity() instanceof LivingEntity target)) return;
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
        PlayerInventory inv = p.getInventory();
        int arrowCount = countArrows(inv);
        if (arrowCount >= MAX_ARROWS) return;
        inv.addItem(this.arrow);
        arrowCount++;
        if (arrowCount >= MAX_ARROWS) return;
        inv.addItem(this.arrow);
    }

    private int countArrows(PlayerInventory inv) {
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == Material.SPECTRAL_ARROW) {
                count += item.getAmount();
            }
        }
        return count;
    }
}
