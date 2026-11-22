package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class Colossus extends ManaKit {

    private static final int TRAMPLE_TICKS = 400;
    final NamespacedKey trampleCounter = ManaKitListener.MANA_KIT_DATA2;
    private final ItemStack trampleHorn;

    @Override
    public org.bukkit.inventory.ItemStack getIcon() {
        return getIconFromShulker(-186, 7, -185);
    }

    public Colossus() {
        super("colossus", "Colossus", "[Melee Knockback Tank]", 1);
        trampleHorn = new ItemStack(Material.GOAT_HORN);
        ItemMeta meta = trampleHorn.getItemMeta();
        meta.displayName(
            Component.text("Trample", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        meta.lore(
            java.util.List.of(
                Component.text(
                    "Right-click to destroy the terrain.",
                    NamedTextColor.GRAY
                )
            )
        );
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, false);
        trampleHorn.setItemMeta(meta);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -186,
            7,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(trampleCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int trampleTicks = pdc.getOrDefault(
            trampleCounter,
            PersistentDataType.INTEGER,
            0
        );
        PlayerInventory inv = p.getInventory();
        if (
            SnapshotPvpPlugin.countInv(p, Material.GOAT_HORN) == 0 &&
            trampleTicks < TRAMPLE_TICKS
        ) {
            pdc.set(
                trampleCounter,
                PersistentDataType.INTEGER,
                trampleTicks + 1
            );
            trampleTicks++;
        }

        if (trampleTicks >= TRAMPLE_TICKS) {
            if (SnapshotPvpPlugin.countInv(p, Material.GOAT_HORN) == 0) {
                inv.addItem(trampleHorn.clone());
                playItemGainFeedback(p);
            }
            pdc.set(trampleCounter, PersistentDataType.INTEGER, TRAMPLE_TICKS);
        }

        p.sendActionBar(
            ChatColor.GOLD +
                "Trample: " +
                (trampleTicks >= TRAMPLE_TICKS
                    ? ChatColor.GREEN + "READY"
                    : "" +
                      ChatColor.WHITE +
                      trampleTicks +
                      "/" +
                      TRAMPLE_TICKS) +
                ChatColor.GRAY +
                " | " +
                ChatColor.RED +
                "Killstreak: " +
                ChatColor.WHITE +
                killstreak +
                "/2"
        );

        if (killstreak == 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.HASTE, 100, 0)
        );
        else if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.HASTE, 100, 1)
        );
    }

    @Override
    public void onEnterCombat(Player p) {
        p.getPersistentDataContainer().set(
            trampleCounter,
            PersistentDataType.INTEGER,
            TRAMPLE_TICKS
        );
        p.getInventory().addItem(trampleHorn.clone());
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (
            e.getAction() != Action.RIGHT_CLICK_AIR &&
            e.getAction() != Action.RIGHT_CLICK_BLOCK
        ) return;
        if (e.getItem() == null) return;
        if (e.getItem().getType() != Material.GOAT_HORN) return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (
            pdc.getOrDefault(trampleCounter, PersistentDataType.INTEGER, 0) <
            TRAMPLE_TICKS
        ) return;
        pdc.set(trampleCounter, PersistentDataType.INTEGER, 0);
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            e.getItem().setAmount(e.getItem().getAmount() - 1);
        }
        e.setCancelled(true);

        Vector facing = p.getLocation().getDirection().setY(0).normalize();
        if (facing.lengthSquared() == 0) facing = new Vector(0, 0, 1);
        final Vector direction = facing.clone();

        p.getWorld().playSound(
            p.getLocation(),
            org.bukkit.Sound.ENTITY_RAVAGER_STEP,
            1.2f,
            0.7f
        );

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 30 || !p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }

                p.setVelocity(direction.clone().multiply(0.75));
                Location explosionLocation = p
                    .getLocation()
                    .add(direction.clone().multiply(1.5))
                    .add(0, .25, 0);
                triggerExplosion(p, explosionLocation);
                ticks++;
            }
        }.runTaskTimer(SnapshotPvpPlugin.instance, 0L, 1L);
    }

    private void triggerExplosion(Player p, Location location) {
        TNTPrimed tnt = (TNTPrimed) location
            .getWorld()
            .spawnEntity(location, EntityType.TNT);
        tnt.setFuseTicks(0);
        tnt.setYield(3.5f);
        tnt.setIsIncendiary(false);
        PersistentDataContainer pdc = tnt.getPersistentDataContainer();
        pdc.set(CustomEggListener.CUSTOM_DAMAGE, PersistentDataType.FLOAT, 1f);
        CustomEggListener.injectOwner(tnt, p);
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        p.clearActivePotionEffects();
        SnapshotPvpPlugin.clearInv(p, Material.GOAT_HORN);
        resetKit(p);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        playAbilityFeedback(p);
        p.addPotionEffect(
            new PotionEffect(PotionEffectType.REGENERATION, 200, 0)
        );
    }
}
