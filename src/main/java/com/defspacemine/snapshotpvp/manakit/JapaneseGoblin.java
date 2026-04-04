package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
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

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class JapaneseGoblin extends ManaKit {
    final static double KUNAI_DAMAGE = 8;
    final static int KUNAI_TICKS = 40;
    final static int KUNAI_RADIUS = 4;

    final int kunaiRestock = 6; // 6 player hits for kunai
    final NamespacedKey kunaiRestockCounter = ManaKitListener.MANA_KIT_DATA0;

    private ItemStack kunai;

    public JapaneseGoblin() {
        super("japanese_goblin", "Japanese Goblin", "[Melee Assassin]", 3);

        kunai = new ItemStack(Material.SNOWBALL, 4);
        ItemMeta meta = kunai.getItemMeta();
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        meta.setItemModel(new NamespacedKey("defspacemine", "japgob_kunai"));
        meta.itemName(Component.text("Kun").color(NamedTextColor.RED)
                .append(Component.text("ai").color(NamedTextColor.YELLOW)));
        kunai.setItemMeta(meta);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

		ManaKitListener.giveItemsFromShulker(p, "goopshotpeshvp", -182, 7, -185);
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int kunaiRestockC = pdc.get(kunaiRestockCounter, PersistentDataType.INTEGER);

        p.sendActionBar(ChatColor.RED + "Kun" + ChatColor.YELLOW + "ai" + ChatColor.GREEN + ": " +
                ChatColor.WHITE + kunaiRestockC + "/" + kunaiRestock +
                ChatColor.GRAY + "  |  " +
                ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/2");

        if (killstreak >= 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0));
        if (killstreak >= 2 && !p.hasPotionEffect(PotionEffectType.REGENERATION))
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, -1, 0));

        PlayerInventory inv = p.getInventory();
        if (kunaiRestockC >= kunaiRestock) {
            SnapshotPvpPlugin.clearInv(inv, Material.SNOWBALL);
            inv.addItem(kunai);
            pdc.set(kunaiRestockCounter, PersistentDataType.INTEGER, kunaiRestockC - kunaiRestock);
        }
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(kunaiRestockCounter, PersistentDataType.INTEGER, kunaiRestock);
    }

    @Override
    public void onLeaveCombat(Player p) {
        p.clearActivePotionEffects();
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.SNOWBALL);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(kunaiRestockCounter, PersistentDataType.INTEGER, kunaiRestock);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (e.getDamageSource().getDamageType() == DamageType.EXPLOSION)
            return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(kunaiRestockCounter, PersistentDataType.INTEGER,
                pdc.get(kunaiRestockCounter, PersistentDataType.INTEGER) + 1);
    }

    @Override
    public void onProjectileHit(Player p, ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball snowball))
            return;

        Location loc = snowball.getLocation();
        if (e.getHitEntity() instanceof LivingEntity victim)
            victim.damage(KUNAI_DAMAGE, DamageSource.builder(DamageType.MOB_PROJECTILE)
                    .withDirectEntity(snowball)
                    .withCausingEntity(p)
                    .build());
        else
            loc = e.getHitBlock().getRelative(e.getHitBlockFace()).getLocation().add(.5, .01, .5);

        final Location l = loc;
        Bukkit.getScheduler().runTaskLater(SnapshotPvpPlugin.instance, () -> {
            Creeper creeper = (Creeper) l.getWorld().spawnEntity(l.add(0, 1, 0), EntityType.CREEPER);
            CustomEggListener.injectOwner(creeper, p);
            creeper.setCustomName(ChatColor.RED + "Kun" + ChatColor.YELLOW + "ai");
            creeper.setCustomNameVisible(true);
            creeper.setExplosionRadius(KUNAI_RADIUS);
            creeper.explode();
        }, KUNAI_TICKS);
    }
}
