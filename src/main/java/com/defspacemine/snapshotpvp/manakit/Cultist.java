package com.defspacemine.snapshotpvp.manakit;

import java.util.ArrayList;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.WeatherManager;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class Cultist extends ManaKit {
    final int COST_NECRO = 30;
    final int COST_DARK = 60;
    final int COST_DOOM = 80;
    final int COST_DIVINE = 160;

    final NamespacedKey SOUL_POWER = ManaKitListener.MANA_KIT_DATA0;
    private final Random random = new Random();

    private ItemStack ritualDoom, ritualNecro, ritualDivine, ritualDark;

    public Cultist() {
        super("cultist", "Cultist", "[Ritual Master]", 3);
        initRitualItems();
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(p, "goopshotpeshvp", -184, 4, -185);
    }

    private void initRitualItems() {
        ritualNecro = createRitualItem(Material.ROTTEN_FLESH, "Necromancy Ritual", NamedTextColor.DARK_GREEN,
                "defspacemine:undead_sigil");
        ritualDoom = createRitualItem(Material.DRIED_KELP, "Doom Ritual", NamedTextColor.RED, "defspacemine:pentagram");
        ritualDark = createRitualItem(Material.COOKIE, "Dark Ritual", NamedTextColor.DARK_PURPLE,
                "defspacemine:pentagram_sculk");
        ritualDivine = createRitualItem(Material.GOLDEN_APPLE, "Divine Ritual", NamedTextColor.YELLOW,
                "defspacemine:pentagram_divine");
    }

    private ItemStack createRitualItem(Material mat, String name, NamedTextColor color, String model) {
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).color(color).decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        meta.setItemModel(NamespacedKey.fromString(model));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int power = pdc.getOrDefault(SOUL_POWER, PersistentDataType.INTEGER, 0);

        String nextRitualStatus = "";
        if (power < COST_NECRO) {
            nextRitualStatus = ChatColor.DARK_GREEN + "Necromancy " + ChatColor.GREEN + "Ritual: " + ChatColor.WHITE
                    + power + "/" + COST_NECRO;
        } else if (power < COST_DARK) {
            nextRitualStatus = ChatColor.DARK_PURPLE + "Dark " + ChatColor.GREEN + "Ritual: " + ChatColor.WHITE + power
                    + "/" + COST_DARK;
        } else if (power < COST_DOOM) {
            nextRitualStatus = ChatColor.RED + "Doom " + ChatColor.GREEN + "Ritual: " + ChatColor.WHITE + power + "/"
                    + COST_DOOM;
        } else if (power < COST_DIVINE) {
            nextRitualStatus = ChatColor.YELLOW + "Divine " + ChatColor.GREEN + "Ritual: " + ChatColor.WHITE + power
                    + "/" + COST_DIVINE;
        } else {
            nextRitualStatus = ChatColor.GOLD + "[MAX RITUALS] " + ChatColor.WHITE + power;
        }

        p.sendActionBar(nextRitualStatus + ChatColor.GRAY + " | " +
                ChatColor.RED + "Killstreak: " + ChatColor.WHITE + killstreak + "/2");
        p.removePotionEffect(PotionEffectType.WITHER);
        p.removePotionEffect(PotionEffectType.POISON);

        if (killstreak == 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
        if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 1, false, false));

        PlayerInventory inv = p.getInventory();
        if (power >= COST_NECRO && !inv.contains(Material.ROTTEN_FLESH))
            inv.addItem(ritualNecro);
        if (power >= COST_DARK && !inv.contains(Material.COOKIE))
            inv.addItem(ritualDark);
        if (power >= COST_DOOM && !inv.contains(Material.DRIED_KELP))
            inv.addItem(ritualDoom);
        if (power >= COST_DIVINE && !inv.contains(Material.GOLDEN_APPLE))
            inv.addItem(ritualDivine);
    }

    private boolean hasAnyRitual(PlayerInventory inv) {
        return inv.contains(Material.ROTTEN_FLESH) || inv.contains(Material.DRIED_KELP) ||
                inv.contains(Material.COOKIE) || inv.contains(Material.GOLDEN_APPLE);
    }

    @Override
    public void onConsume(Player p, PlayerItemConsumeEvent e) {
        Material type = e.getItem().getType();
        Location loc = p.getLocation();
        World world = loc.getWorld();
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");

        switch (type) {
            case ROTTEN_FLESH: // Necromancy
                for (int i = 0; i < Math.min(8, killstreak) + 2; i++) {
                    EntityType et = (i % 2 == 0) ? EntityType.ZOMBIE : EntityType.SKELETON;
                    LivingEntity ent = (LivingEntity) world.spawnEntity(
                            loc.clone().add(random.nextDouble() * 3 - 1.5, 0, random.nextDouble() * 3 - 1.5), et);

                    // Equipment to prevent sun-burning and add protection
                    EntityEquipment equip = ent.getEquipment();
                    if (equip != null) {
                        equip.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                        equip.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                        equip.setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
                        equip.setBoots(new ItemStack(Material.LEATHER_BOOTS));
                        equip.setHelmetDropChance(0);
                        equip.setChestplateDropChance(0);
                        equip.setLeggingsDropChance(0);
                        equip.setBootsDropChance(0);
                    }

                    ent.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, -1, 1));
                    ent.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, -1, 0));
                    CustomEggListener.injectOwner(ent, p);
                    SnapshotPvpPlugin.addToTeam(p, ent);

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (ent != null && !ent.isDead())
                                ent.remove();
                        }
                    }.runTaskLater(SnapshotPvpPlugin.instance, COST_NECRO * 20);
                }
                world.spawnParticle(Particle.SOUL, loc, 50, 1, 1, 1, 0.1);
                break;

            case DRIED_KELP: // Doom
                spawnPentagram(world, loc, Color.RED);
                applyDoomBuffs(p);
                break;

            case COOKIE: // Dark Ritual
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, 4));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 400, 2));
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 400, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 400, 0));

                new BukkitRunnable() {
                    int ticks = 0;

                    @Override
                    public void run() {
                        if (ticks >= 400 || !p.getScoreboardTags().contains("combat")) {
                            cancel();
                            return;
                        }

                        for (Entity ent : p.getNearbyEntities(30, 30, 30))
                            if (ent instanceof Player victim) {
                                victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 0));
                                if (!victim.equals(p))
                                    victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0));
                            }
                        ticks++;
                    }
                }.runTaskTimer(SnapshotPvpPlugin.instance, 0, 1);

                world.spawnParticle(Particle.LARGE_SMOKE, loc, 100, 2, 2, 2, 0.05);
                break;

            case GOLDEN_APPLE: // Divine Ritual
                WeatherManager.instance.queueWeather(world, WeatherManager.WeatherType.STORM, 600L);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 600, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 14));
                for (Entity ent : p.getNearbyEntities(100, 100, 100))
                    if (ent instanceof Player other)
                        if (other != p && SnapshotPvpPlugin.getTeam(p).equals(SnapshotPvpPlugin.getTeam(other))) {
                            other.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 1));
                            other.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 600, 1));
                        } else {
                            other.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 600, 1));
                            SnapshotPvpPlugin.strikeLightning(other, p);
                        }
                world.spawnParticle(Particle.END_ROD, loc, 60, 1, 1, 1, 0.05);
                break;

            default:
                return;
        }

        resetRitualProgress(p);
    }

    private void resetRitualProgress(Player p) {
        p.getPersistentDataContainer().set(SOUL_POWER, PersistentDataType.INTEGER, 0);
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.ROTTEN_FLESH);
        SnapshotPvpPlugin.clearInv(inv, Material.DRIED_KELP);
        SnapshotPvpPlugin.clearInv(inv, Material.COOKIE);
        SnapshotPvpPlugin.clearInv(inv, Material.GOLDEN_APPLE);
    }

    private void applyDoomBuffs(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 4));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 400, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 400, 9));
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 400, 0));
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        addPower(p, 1);
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        if (e.getDamageSource().getDamageType() == DamageType.MAGIC)
            e.setCancelled(true);
        addPower(p, 1);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        addPower(p, 10);
    }

    private void addPower(Player p, int amount) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int current = pdc.getOrDefault(SOUL_POWER, PersistentDataType.INTEGER, 0);
        pdc.set(SOUL_POWER, PersistentDataType.INTEGER, current + amount);
    }

    @Override
    public void resetKit(Player p) {
        p.getPersistentDataContainer().set(SOUL_POWER, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onLeaveCombat(Player p) {
        resetRitualProgress(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        resetKit(p);
    }

    private void spawnPentagram(World world, Location center, Color color) {
        double radius = 2.5;
        int points = 5;
        ArrayList<Location> vertices = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            double angle = Math.toRadians((360.0 / points) * i - 90);
            vertices.add(center.clone().add(radius * Math.cos(angle), 0.1, radius * Math.sin(angle)));
        }
        int[][] connections = { { 0, 2 }, { 2, 4 }, { 4, 1 }, { 1, 3 }, { 3, 0 } };
        DustOptions dust = new DustOptions(color, 1.5f);
        for (int[] line : connections)
            drawLine(world, vertices.get(line[0]), vertices.get(line[1]), dust);
        drawCircle(world, center.clone().add(0, 0.1, 0), radius, dust);
    }

    private void drawLine(World world, Location start, Location end, DustOptions dust) {
        double dist = start.distance(end);
        Vector dir = end.toVector().subtract(start.toVector()).normalize();
        for (double i = 0; i < dist; i += 0.2) {
            world.spawnParticle(Particle.DUST, start.clone().add(dir.clone().multiply(i)), 1, 0, 0, 0, 0, dust);
        }
    }

    private void drawCircle(World world, Location center, double radius, Particle.DustOptions dust) {
        for (double i = 0; i < 360; i += 5) {
            double angle = Math.toRadians(i);
            world.spawnParticle(Particle.DUST,
                    center.clone().add(radius * Math.cos(angle), 0, radius * Math.sin(angle)), 1, 0, 0, 0, 0, dust);
        }
    }
}
