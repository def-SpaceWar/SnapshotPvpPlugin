package com.defspacemine.snapshotpvp.manakit;

import java.util.ArrayList;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class Cultist extends ManaKit {
    final int doomRitual = 50; // 50 attacks, getting hit 50 times, or 5 kills
    final NamespacedKey doomRitualCounter = ManaKitListener.MANA_KIT_DATA0;

    private ItemStack ritualDoom;

    public Cultist() {
        super("cultist", "Cultist", "[Melee Damage]", 3);

        {
            ritualDoom = new ItemStack(Material.DRIED_KELP, 1);
            ItemMeta meta = ritualDoom.getItemMeta();
            meta.displayName(
                    Component.text("Doom Ritual")
                            .color(NamedTextColor.RED)
                            .decorate(TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false));
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.setItemModel(NamespacedKey.fromString("defspacemine:pentagram"));
            ritualDoom.setItemMeta(meta);
        }
    }

    @Override
    public void giveKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        resetKit(p);

        // give items
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(doomRitualCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int doomRitualC = pdc.get(doomRitualCounter, PersistentDataType.INTEGER);

        String displayMessage = ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/2";

        PlayerInventory inv = p.getInventory();
        if (inv.contains(Material.DRIED_KELP) ||
                inv.getItemInOffHand().getType() == Material.DRIED_KELP) {
            displayMessage = ChatColor.DARK_RED + "Doom Ritual!"
                    + ChatColor.GRAY + " | " + displayMessage;
            pdc.set(doomRitualCounter, PersistentDataType.INTEGER, 0);
        } else
            displayMessage = ChatColor.DARK_RED + "Doom Ritual: " + ChatColor.WHITE + doomRitualC + "/"
                    + doomRitual + ChatColor.GRAY + " | " + displayMessage;

        p.sendActionBar(displayMessage);

        if (killstreak == 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0));
        if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1));

        if (doomRitualC >= doomRitual) {
            inv.addItem(ritualDoom);
            pdc.set(doomRitualCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        clearRituals(p);
        resetKit(p);
    }

    private void clearRituals(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.DRIED_KELP);
    }

    private void clearRituals(PlayerInventory inv) {
        SnapshotPvpPlugin.clearInv(inv, Material.DRIED_KELP);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(doomRitualCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(doomRitualCounter, PersistentDataType.INTEGER,
                pdc.get(doomRitualCounter, PersistentDataType.INTEGER) + 1);
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(doomRitualCounter, PersistentDataType.INTEGER,
                pdc.get(doomRitualCounter, PersistentDataType.INTEGER) + 1);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(doomRitualCounter, PersistentDataType.INTEGER,
                pdc.get(doomRitualCounter, PersistentDataType.INTEGER) + 10);
    }

    @Override
    public void onConsume(Player p, PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        switch (item.getType()) {
            case DRIED_KELP:
                clearRituals(p);
                Location loc = p.getLocation();
                World world = loc.getWorld();
                world.spawnEntity(loc, EntityType.LIGHTNING_BOLT);
                spawnPentagram(world, loc, Color.RED);
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.HASTE,
                        400,
                        2));
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED,
                        400,
                        2));
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.JUMP_BOOST,
                        400,
                        0));
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.HEALTH_BOOST,
                        800,
                        9));
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.GLOWING,
                        800,
                        0));
                break;
            default:
                return;
        }
    }

    private void spawnPentagram(World world, Location center, Color color) {
        double radius = 2.5;
        int points = 5;

        ArrayList<Location> vertices = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            double angle = Math.toRadians((360.0 / points) * i - 90);
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            vertices.add(center.clone().add(x, 0.1, z));
        }

        int[][] connections = {
                { 0, 2 },
                { 2, 4 },
                { 4, 1 },
                { 1, 3 },
                { 3, 0 }
        };

        DustOptions redDust = new DustOptions(color, 1.5f);
        for (int[] line : connections)
            drawLine(world, vertices.get(line[0]), vertices.get(line[1]), redDust);
        drawCircle(world, center.clone().add(0, 0.1, 0), radius, redDust);
    }

    private void drawLine(World world, Location start, Location end, DustOptions dust) {
        double distance = start.distance(end);
        Vector direction = end.toVector().subtract(start.toVector()).normalize();

        for (double i = 0; i < distance; i += 0.1) {
            Location point = start.clone().add(direction.clone().multiply(i));
            world.spawnParticle(Particle.DUST, point, 5, 0, 0, 0, 0, dust);
        }
    }

    private void drawCircle(World world, Location center, double radius, Particle.DustOptions dust) {
        int SMOOTHNESS = (int)Math.floor(radius / .1 * 2 * Math.PI);
        for (int i = 0; i < SMOOTHNESS; i++) {
            double angle = 2 * Math.PI * i / SMOOTHNESS;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            Location point = center.clone().add(x, 0, z);
            world.spawnParticle(Particle.DUST, point, 5, 0, 0, 0, 0, dust);
        }
    }
}
