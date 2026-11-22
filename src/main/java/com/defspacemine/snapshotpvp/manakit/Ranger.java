package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
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

    @Override
    public org.bukkit.inventory.ItemStack getIcon() {
        return getIconFromShulker(-184, 10, -185);
    }

    private static final int MAX_ARROWS = 3;
    private static final int ACCURATE_SHOT_DELAY_TICKS = 20;
    private static final int ACCURATE_SHOT_TICKS_PER_PERCENT = 1;

    final int arrowRestock = 120; // arrow restock every 6 seconds. only runs when doesn't have an arrow
    final NamespacedKey arrowRestockCounter = ManaKitListener.MANA_KIT_DATA0;
    final NamespacedKey accurateShotCharge = ManaKitListener.MANA_KIT_DATA5;
    final NamespacedKey accurateShotBonus = new NamespacedKey(
        "defspacemine",
        "ranger_accurate_shot_bonus"
    );

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
            d.setMaxDamage(1);
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
        pdc.set(accurateShotCharge, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int charge = pdc.getOrDefault(
            accurateShotCharge,
            PersistentDataType.INTEGER,
            0
        );
        if (
            p.getInventory().getItemInMainHand().getType() == Material.BOW &&
            p.isHandRaised()
        ) {
            charge++;
            pdc.set(accurateShotCharge, PersistentDataType.INTEGER, charge);
        } else if (
            p.getInventory().getItemInMainHand().getType() != Material.BOW
        ) {
            pdc.set(accurateShotCharge, PersistentDataType.INTEGER, 0);
        }

        int accurateShotPercent =
            charge <= ACCURATE_SHOT_DELAY_TICKS
                ? 0
                : Math.min(
                      100,
                      (charge - ACCURATE_SHOT_DELAY_TICKS) /
                          ACCURATE_SHOT_TICKS_PER_PERCENT
                  );
        int arrowRestockC = pdc.get(
            arrowRestockCounter,
            PersistentDataType.INTEGER
        );

        String displayMessage =
            ChatColor.GREEN +
            "Accurate Shot: " +
            ChatColor.WHITE +
            accurateShotPercent +
            "%" +
            ChatColor.GRAY +
            " | " +
            ChatColor.RED +
            "Killstreak: " +
            ChatColor.WHITE +
            killstreak +
            "/2";

        PlayerInventory inv = p.getInventory();
        if (SnapshotPvpPlugin.countInv(p, Material.SPECTRAL_ARROW) == 0) {
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
            for (int i = 0; i < MAX_ARROWS; i++) inv.addItem(arrow);
            playItemGainFeedback(p);
            pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        SnapshotPvpPlugin.clearInv(p, Material.SPECTRAL_ARROW);
        SnapshotPvpPlugin.clearInv(p, Material.IRON_SWORD);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
        PlayerInventory inv = p.getInventory();
        int arrowCount = SnapshotPvpPlugin.countInv(p, Material.SPECTRAL_ARROW);
        boolean gainedArrows = arrowCount < MAX_ARROWS;
        while (arrowCount < MAX_ARROWS) {
            inv.addItem(arrow);
            arrowCount++;
        }
        if (gainedArrows) playItemGainFeedback(p);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PlayerInventory inv = p.getInventory();
        int arrowCount = SnapshotPvpPlugin.countInv(p, Material.SPECTRAL_ARROW);
        while (arrowCount < MAX_ARROWS) {
            inv.addItem(arrow);
            arrowCount++;
        }
        SnapshotPvpPlugin.clearInv(p, Material.IRON_SWORD);
        inv.addItem(kbSword);
        playAbilityFeedback(p);
    }

    @Override
    public void onProjectileHit(Player p, ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof SpectralArrow arrow)) return;
        if (!(e.getHitEntity() instanceof LivingEntity target)) return;
        playAbilityFeedback(p);
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
        PlayerInventory inv = p.getInventory();
        int arrowCount = SnapshotPvpPlugin.countInv(p, Material.SPECTRAL_ARROW);
        if (arrowCount >= MAX_ARROWS) return;
        inv.addItem(this.arrow);
        arrowCount++;
        if (arrowCount >= MAX_ARROWS) return;
        inv.addItem(this.arrow);
    }

    @Override
    public void onItemHeldChange(Player p, PlayerItemHeldEvent e) {
        p.getPersistentDataContainer().set(
            accurateShotCharge,
            PersistentDataType.INTEGER,
            0
        );
    }

    @Override
    public void onShootBow(Player p, EntityShootBowEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int charge = pdc.getOrDefault(
            accurateShotCharge,
            PersistentDataType.INTEGER,
            0
        );
        int delay = ACCURATE_SHOT_DELAY_TICKS;
        int ticksPerPercent = ACCURATE_SHOT_TICKS_PER_PERCENT;
        ticksPerPercent = Math.max(1, ticksPerPercent);

        int percent =
            charge <= delay
                ? 0
                : Math.min(100, (charge - delay) / ticksPerPercent);

        if (percent > 0) {
            PersistentDataContainer projectilePdc = e
                .getProjectile()
                .getPersistentDataContainer();
            projectilePdc.set(
                accurateShotBonus,
                PersistentDataType.INTEGER,
                percent
            );

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (
                        !e.getProjectile().isValid() ||
                        e.getProjectile().isDead()
                    ) {
                        cancel();
                        return;
                    }
                    e.getProjectile()
                        .getWorld()
                        .spawnParticle(
                            Particle.HAPPY_VILLAGER,
                            e.getProjectile().getLocation(),
                            2,
                            0.08,
                            0.08,
                            0.08,
                            0
                        );
                }
            }.runTaskTimer(SnapshotPvpPlugin.instance, 0L, 1L);
        }

        pdc.set(accurateShotCharge, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Projectile projectile)) return;
        PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        int percent = pdc.getOrDefault(
            accurateShotBonus,
            PersistentDataType.INTEGER,
            0
        );
        if (percent <= 0) return;
        e.setDamage(e.getDamage() * (1.0 + percent / 100.0));
        pdc.remove(accurateShotBonus);
    }
}
