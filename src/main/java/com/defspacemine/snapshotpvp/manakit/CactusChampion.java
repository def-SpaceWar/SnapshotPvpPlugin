package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class CactusChampion extends ManaKit {

    final int CACTUS_TOTEM_HP = 200;
    final int CACTUS_TOTEM_RADIUS = 8;
    final int CACTUS_TOTEM_RADIUS_SQUARED =
        CACTUS_TOTEM_RADIUS * CACTUS_TOTEM_RADIUS;

    final double COUNTER_SPIKES_ARROW_DAMAGE = 4.0;
    final int COUNTER_SPIKES_ARROW_COUNT = 50;
    final double COUNTER_SPIKES_ARROW_SPEED = 1.3;

    final int counterSpikes = 100; // 100 damage sustained = launch cactus spikes and get temporary absorption 5
    final NamespacedKey counterSpikesCounter = ManaKitListener.MANA_KIT_DATA0;
    final int cactusTotem = 60; // 60 attacks/thornsdmg = 1 Cactus Totem
    final NamespacedKey cactusTotemCounter = ManaKitListener.MANA_KIT_DATA1;

    final NamespacedKey cactusTotemUUID = ManaKitListener.MANA_KIT_DATASTR0;
    final NamespacedKey spikeArrowKey = new NamespacedKey(
        "snapshotpvp",
        "counter_spike_arrow"
    );

    private final ItemStack cactusTotemItem;

    public CactusChampion() {
        super("cactus_champion", "Cactus Champion", "[Melee Damage Tank]", 2);

        cactusTotemItem = new ItemStack(Material.CACTUS);
        ItemMeta meta = cactusTotemItem.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Cactus Totem");
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, false);
        cactusTotemItem.setItemMeta(meta);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -182,
            10,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(counterSpikesCounter, PersistentDataType.INTEGER, 0);
        pdc.set(cactusTotemCounter, PersistentDataType.INTEGER, 0);
        pdc.set(
            cactusTotemUUID,
            PersistentDataType.STRING,
            ""
        );
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int counterSpikesC = pdc.getOrDefault(
            counterSpikesCounter,
            PersistentDataType.INTEGER,
            0
        );
        int cactusTotemC = pdc.getOrDefault(
            cactusTotemCounter,
            PersistentDataType.INTEGER,
            0
        );

        p.sendActionBar(
            ChatColor.LIGHT_PURPLE +
                "Counter Spikes: " +
                ChatColor.WHITE +
                counterSpikesC +
                "/" +
                counterSpikes +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.GREEN +
                "Cactus Totem: " +
                ChatColor.WHITE +
                cactusTotemC +
                "/" +
                cactusTotem +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.RED +
                "Killstreak: " +
                ChatColor.WHITE +
                killstreak +
                "/3"
        );

        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.HASTE, 100, 1)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 0)
        );
        if (killstreak >= 3) p.addPotionEffect(
            new PotionEffect(PotionEffectType.STRENGTH, 100, 0)
        );

        PlayerInventory inv = p.getInventory();

        if (counterSpikesC >= counterSpikes) {
            launchCounterSpikes(p);
            pdc.set(counterSpikesCounter, PersistentDataType.INTEGER, 0);
        }

        if (cactusTotemC >= cactusTotem) {
            SnapshotPvpPlugin.clearInv(inv, Material.CACTUS);
            inv.addItem(cactusTotemItem);
            pdc.set(cactusTotemCounter, PersistentDataType.INTEGER, 0);
        }

        Entity kitEntity = getEntityFromPDC(pdc, cactusTotemUUID);
        if (kitEntity != null && kitEntity.isValid()) {
            if (kitEntity instanceof LivingEntity living) {
                updateHealthBar(living);
            }

            if (
                !SnapshotPvpPlugin.getTeam(p).equals(
                    SnapshotPvpPlugin.getTeamE(kitEntity)
                )
            ) {
                SnapshotPvpPlugin.addToTeam(p, kitEntity);
            }

            showParticleCircle(kitEntity.getLocation(), CACTUS_TOTEM_RADIUS);

            for (Player nearby : kitEntity.getWorld().getPlayers()) {
                if (
                    nearby.equals(p) ||
                    SnapshotPvpPlugin.getTeam(p).equals(
                        SnapshotPvpPlugin.getTeam(nearby)
                    )
                ) {
                    if (
                        nearby
                            .getLocation()
                            .distanceSquared(kitEntity.getLocation()) <=
                        CACTUS_TOTEM_RADIUS_SQUARED
                    ) {
                        nearby.addPotionEffect(
                            new PotionEffect(PotionEffectType.RESISTANCE, 40, 1)
                        );
                    }
                }
            }
        }
    }

    private void launchCounterSpikes(Player p) {
        p.addPotionEffect(
            new PotionEffect(PotionEffectType.ABSORPTION, 200, 4)
        );

        Location center = p.getEyeLocation();
        center
            .getWorld()
            .playSound(center, Sound.ENTITY_ITEM_BREAK, 1.5f, 0.5f);
        center
            .getWorld()
            .playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);

        double goldenRatio = (1.0 + Math.sqrt(5.0)) / 2.0;
        double angleIncrement = Math.PI * 2.0 * goldenRatio;

        for (int i = 0; i < COUNTER_SPIKES_ARROW_COUNT; i++) {
            double t = (double) i / COUNTER_SPIKES_ARROW_COUNT;
            double inclination = Math.acos(1.0 - 2.0 * t);
            double azimuth = angleIncrement * i;

            double x = Math.sin(inclination) * Math.cos(azimuth);
            double y = Math.cos(inclination);
            double z = Math.sin(inclination) * Math.sin(azimuth);

            Vector velocity = new Vector(x, y, z)
                .normalize()
                .multiply(COUNTER_SPIKES_ARROW_SPEED);

            p.getWorld().spawn(center, Arrow.class, arrow -> {
                arrow.setShooter(p);
                arrow.setVelocity(velocity);
                arrow.setDamage(COUNTER_SPIKES_ARROW_DAMAGE);
                arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                arrow
                    .getPersistentDataContainer()
                    .set(spikeArrowKey, PersistentDataType.BYTE, (byte) 1);
            });
        }
    }

    @Override
    public void onProjectileHit(Player p, ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow)) return;

        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        if (!pdc.has(spikeArrowKey, PersistentDataType.BYTE)) return;

        arrow
            .getWorld()
            .spawnParticle(
                Particle.ITEM,
                arrow.getLocation(),
                8,
                0.1,
                0.1,
                0.1,
                0.05,
                new ItemStack(Material.CACTUS)
            );

        if (e.getHitEntity() instanceof LivingEntity target) {
            if (
                target.equals(p) ||
                SnapshotPvpPlugin.getTeam(p).equals(
                    SnapshotPvpPlugin.getTeamE(target)
                )
            ) {
                e.setCancelled(true);
            }
        }
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (
            e.getAction() != Action.RIGHT_CLICK_BLOCK &&
            e.getAction() != Action.RIGHT_CLICK_AIR
        ) return;
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.CACTUS) return;

        e.setCancelled(true);
        Location spawnLoc =
            e.getClickedBlock() != null
                ? e
                      .getClickedBlock()
                      .getRelative(e.getBlockFace())
                      .getLocation()
                      .add(0.5, 0, 0.5)
                : p.getLocation();

        spawnCactusTotem(p, spawnLoc);

        if (p.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    private void spawnCactusTotem(Player owner, Location loc) {
        PersistentDataContainer pdc = owner.getPersistentDataContainer();
        Entity old = getEntityFromPDC(pdc, cactusTotemUUID);
        if (old != null) {
            old.remove();
        }

        Mannequin mannequin = (Mannequin) loc
            .getWorld()
            .spawnEntity(loc, EntityType.MANNEQUIN);
        mannequin.setAI(false);
        mannequin.setCanPickupItems(false);
        mannequin.setProfile(
            ResolvableProfile.resolvableProfile().name("Cactus").build()
        );
        if (mannequin.getEquipment() != null) {
            mannequin.getEquipment().setHelmet(new ItemStack(Material.CACTUS));
        }

        if (mannequin.getAttribute(Attribute.MAX_HEALTH) != null) {
            mannequin
                .getAttribute(Attribute.MAX_HEALTH)
                .setBaseValue(CACTUS_TOTEM_HP);
        }
        mannequin.setHealth(CACTUS_TOTEM_HP);

        updateHealthBar(mannequin);
        mannequin.setCustomNameVisible(true);

        pdc.set(
            cactusTotemUUID,
            PersistentDataType.STRING,
            mannequin.getUniqueId().toString()
        );
        SnapshotPvpPlugin.addToTeam(owner, mannequin);
    }

    private void updateHealthBar(LivingEntity entity) {
        int currentHp = (int) Math.ceil(entity.getHealth());
        entity.setCustomName(
            ChatColor.GREEN +
                "Cactus Totem " +
                ChatColor.RED +
                currentHp +
                "/" +
                CACTUS_TOTEM_HP +
                " HP"
        );
        entity.addPotionEffect(
            new PotionEffect(PotionEffectType.GLOWING, 100, 0)
        );
    }

    private void showParticleCircle(Location center, double radius) {
        DustOptions greenDust = new DustOptions(Color.GREEN, 1.2f);
        int points = 36;
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location pLoc = new Location(
                center.getWorld(),
                x,
                center.getY() + 0.1,
                z
            );
            center
                .getWorld()
                .spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0, greenDust);
        }
    }

    private Entity getEntityFromPDC(
        PersistentDataContainer pdc,
        NamespacedKey key
    ) {
        if (!pdc.has(key, PersistentDataType.STRING)) return null;
        try {
            UUID id = UUID.fromString(pdc.get(key, PersistentDataType.STRING));
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid()) return null;
            return e;
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.CACTUS);

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Entity kitEntity = getEntityFromPDC(pdc, cactusTotemUUID);
        if (kitEntity != null) {
            kitEntity.remove();
        }

        p.clearActivePotionEffects();
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(counterSpikesCounter, PersistentDataType.INTEGER, 0);
        pdc.set(cactusTotemCounter, PersistentDataType.INTEGER, 0);
        pdc.set(
            cactusTotemUUID,
            PersistentDataType.STRING,
            ""
        );
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) {
            PersistentDataContainer pdc = p.getPersistentDataContainer();
            pdc.set(
                cactusTotemCounter,
                PersistentDataType.INTEGER,
                pdc.getOrDefault(
                    cactusTotemCounter,
                    PersistentDataType.INTEGER,
                    0
                ) + 1
            );
        }
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(
            counterSpikesCounter,
            PersistentDataType.INTEGER,
            (int) (pdc.getOrDefault(
                counterSpikesCounter,
                PersistentDataType.INTEGER,
                0
            ) + Math.max(0, e.getDamage()))
        );
    }
}
