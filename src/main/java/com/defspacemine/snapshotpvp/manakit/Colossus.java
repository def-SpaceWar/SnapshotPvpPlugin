package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public class Colossus extends ManaKit {
    private static final int TRAMPLE_COOLDOWN = 200;
    final NamespacedKey trampleCooldown = ManaKitListener.MANA_KIT_DATA2;
    @Override
    public org.bukkit.inventory.ItemStack getIcon() {
        return getIconFromShulker(-186, 7, -185);
    }

    public Colossus() {
        super("colossus", "Colossus", "[Melee Knockback Tank]", 1);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

		ManaKitListener.giveItemsFromShulker(p, "goopshotpeshvp", -186, 7, -185);
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(trampleCooldown, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int cooldown = pdc.getOrDefault(
            trampleCooldown,
            PersistentDataType.INTEGER,
            0
        );
        if (cooldown > 0) {
            pdc.set(
                trampleCooldown,
                PersistentDataType.INTEGER,
                cooldown - 1
            );
        }

        p.sendActionBar(ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/2");

        if (killstreak == 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 0));
        else if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 1));
    }

    @Override
    public void onEnterCombat(Player p) {
        p.getPersistentDataContainer().set(
            trampleCooldown,
            PersistentDataType.INTEGER,
            0
        );
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (
            e.getAction() != Action.RIGHT_CLICK_AIR &&
            e.getAction() != Action.RIGHT_CLICK_BLOCK
        ) return;
        if (e.getItem() == null) return;
        Material held = e.getItem().getType();
        if (
            held != Material.NETHERITE_AXE &&
            held != Material.NETHERITE_SWORD &&
            held != Material.MACE
        ) return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int cooldown = pdc.getOrDefault(
            trampleCooldown,
            PersistentDataType.INTEGER,
            0
        );
        if (cooldown > 0) return;
        pdc.set(trampleCooldown, PersistentDataType.INTEGER, TRAMPLE_COOLDOWN);
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
                if (
                    ticks >= 30 ||
                    !p.isOnline() ||
                    p.isDead()
                ) {
                    cancel();
                    return;
                }

                p.setVelocity(direction.clone().multiply(0.75));
                org.bukkit.Location foot = p.getLocation();
                Vector side = new Vector(-direction.getZ(), 0, direction.getX());
                if (side.lengthSquared() == 0) side = new Vector(1, 0, 0);
                side.normalize();

                for (int y = -1; y <= 1; y++) {
                    for (int x = -1; x <= 1; x++) {
                        org.bukkit.Location blockLocation = foot.clone()
                            .add(direction.clone().multiply(1.5))
                            .add(side.clone().multiply(x))
                            .add(0, y, 0);
                        Block block = blockLocation.getBlock();
                        if (
                            block.getType() == Material.AIR ||
                            block.getType() == Material.CAVE_AIR ||
                            block.getType() == Material.VOID_AIR ||
                            block.getType() == Material.BEDROCK ||
                            block.getType() == Material.BARRIER ||
                            block.getType() == Material.END_PORTAL ||
                            block.getType() == Material.END_PORTAL_FRAME ||
                            block.getType() == Material.END_GATEWAY
                        ) continue;
                        block.setType(Material.AIR);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(SnapshotPvpPlugin.instance, 0L, 1L);
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        p.clearActivePotionEffects();
        resetKit(p);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        playAbilityFeedback(p);
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0));
    }
}
