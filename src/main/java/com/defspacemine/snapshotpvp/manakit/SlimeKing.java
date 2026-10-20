package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class SlimeKing extends ManaKit implements Listener {

    final int SLIME_COLOR = 0xAAFF99;
    final int MAGMA_COLOR = 0xAA3322;
    final int MINION_DURATION = 400;
    final int SPAWN_COOLDOWN = 200;

    final double SLIME_METER_RATE = 1.5; // blocks per 1% slime meter
    final double SLIME_METER_RATE_SQ = SLIME_METER_RATE * SLIME_METER_RATE;

    private final NamespacedKey meterKey = ManaKitListener.MANA_KIT_DATA0;
    private final NamespacedKey spawnTimerKey = ManaKitListener.MANA_KIT_DATA1;
    private final NamespacedKey isMagmaModeKey =
        ManaKitListener.MANA_KIT_DATABOOL0;
    private final NamespacedKey lastLocXKey = ManaKitListener.MANA_KIT_DATADBL0;
    private final NamespacedKey lastLocYKey = ManaKitListener.MANA_KIT_DATADBL1;
    private final NamespacedKey lastLocZKey = ManaKitListener.MANA_KIT_DATADBL2;

    public SlimeKing() {
        super("slime_king", "Slime King", "[Utility Armada]", 0);
        SnapshotPvpPlugin plugin = SnapshotPvpPlugin.getPlugin(
            SnapshotPvpPlugin.class
        );
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -183,
            13,
            -185
        );

        updateArmorColor(p, Color.fromRGB(SLIME_COLOR));
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(meterKey, PersistentDataType.INTEGER, 0);
        pdc.set(spawnTimerKey, PersistentDataType.INTEGER, 0);
        pdc.set(isMagmaModeKey, PersistentDataType.BOOLEAN, false);

        Location loc = p.getLocation();
        pdc.set(lastLocXKey, PersistentDataType.DOUBLE, loc.getX());
        pdc.set(lastLocYKey, PersistentDataType.DOUBLE, loc.getY());
        pdc.set(lastLocZKey, PersistentDataType.DOUBLE, loc.getZ());

        updateArmorColor(p, Color.fromRGB(SLIME_COLOR));
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        int meter = pdc.getOrDefault(meterKey, PersistentDataType.INTEGER, 0);
        int spawnTimer = pdc.getOrDefault(
            spawnTimerKey,
            PersistentDataType.INTEGER,
            0
        );
        boolean isMagma = pdc.getOrDefault(
            isMagmaModeKey,
            PersistentDataType.BOOLEAN,
            false
        );

        if (spawnTimer > 0) {
            pdc.set(spawnTimerKey, PersistentDataType.INTEGER, spawnTimer - 1);
        }

        Location currentLoc = p.getLocation();
        double lastX = pdc.getOrDefault(
            lastLocXKey,
            PersistentDataType.DOUBLE,
            currentLoc.getX()
        );
        double lastY = pdc.getOrDefault(
            lastLocYKey,
            PersistentDataType.DOUBLE,
            currentLoc.getY()
        );
        double lastZ = pdc.getOrDefault(
            lastLocZKey,
            PersistentDataType.DOUBLE,
            currentLoc.getZ()
        );

        double distSq =
            Math.pow(currentLoc.getX() - lastX, 2) +
            Math.pow(currentLoc.getY() - lastY, 2) +
            Math.pow(currentLoc.getZ() - lastZ, 2);

        if (distSq >= SLIME_METER_RATE_SQ) {
            pdc.set(lastLocXKey, PersistentDataType.DOUBLE, currentLoc.getX());
            pdc.set(lastLocYKey, PersistentDataType.DOUBLE, currentLoc.getY());
            pdc.set(lastLocZKey, PersistentDataType.DOUBLE, currentLoc.getZ());

            if (meter < 100) {
                meter = Math.min(100, meter + 1);
                pdc.set(meterKey, PersistentDataType.INTEGER, meter);
            }
        }

        if (meter >= 75) {
            if (isMagma) {
                p.addPotionEffect(
                    new PotionEffect(PotionEffectType.STRENGTH, 100, 1)
                );
            } else {
                p.addPotionEffect(
                    new PotionEffect(PotionEffectType.RESISTANCE, 100, 1)
                );
            }
        }

        if (isMagma) {
            p.addPotionEffect(
                new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0)
            );
        } else {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
            p.addPotionEffect(
                new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 1)
            );
        }

        String meterName = isMagma
            ? ChatColor.GOLD + "Magma Meter"
            : ChatColor.GREEN + "Slime Meter";
        ChatColor meterColor = meter >= 75 ? ChatColor.AQUA : ChatColor.WHITE;

        String cooldownText =
            spawnTimer > 0
                ? "" +
                  ChatColor.RED +
                  (SPAWN_COOLDOWN - spawnTimer) +
                  "/" +
                  SPAWN_COOLDOWN
                : ChatColor.GREEN + "READY";

        p.sendActionBar(
            meterName +
                ": " +
                meterColor +
                meter +
                "%" +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.YELLOW +
                "Spawn: " +
                cooldownText +
                ChatColor.WHITE +
                ChatColor.GRAY +
                "  |  " +
                ChatColor.RED +
                "Killstreak: " +
                ChatColor.WHITE +
                killstreak +
                "/2"
        );

        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SATURATION, 100, 0)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.HASTE, 100, 1)
        );
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (
            e.getDamageSource().getDamageType() != DamageType.PLAYER_ATTACK
        ) return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int spawnTimer = pdc.getOrDefault(
            spawnTimerKey,
            PersistentDataType.INTEGER,
            0
        );

        int currentMeter = pdc.getOrDefault(
            meterKey,
            PersistentDataType.INTEGER,
            0
        );

        int newMeter = Math.max(0, currentMeter - 8);
        pdc.set(meterKey, PersistentDataType.INTEGER, newMeter);

        if (spawnTimer > 0) return;

        boolean isMagma = pdc.getOrDefault(
            isMagmaModeKey,
            PersistentDataType.BOOLEAN,
            false
        );

        spawnSlimeOrMagma(
            e.getEntity().getLocation(),
            isMagma,
            p,
            currentMeter
        );

        pdc.set(spawnTimerKey, PersistentDataType.INTEGER, SPAWN_COOLDOWN);
    }

    private void spawnSlimeOrMagma(
        Location loc,
        boolean isMagma,
        Player p,
        int currentMeter
    ) {
        var slimeOrMagma = isMagma
            ? loc.getWorld().spawn(loc, MagmaCube.class)
            : loc.getWorld().spawn(loc, Slime.class);

        int size = (int) Math.max(1, Math.floor(Math.sqrt(currentMeter / 4) - 1));
        if (slimeOrMagma instanceof Slime slime) {
            slime.setSize(size + 2);
        } else if (slimeOrMagma instanceof MagmaCube magma) {
            magma.setSize(size);
        }

        configureMinion(slimeOrMagma, p, isMagma);
    }

    private void configureMinion(
        LivingEntity minion,
        Player owner,
        boolean isMagma
    ) {
        SnapshotPvpPlugin.addToTeam(owner, minion);
        CustomEggListener.injectOwner(minion, owner);

        minion.customName(
            Component.text(isMagma ? "Magma Minion" : "Slime Minion")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
        );
        minion.setCustomNameVisible(true);

        minion.addPotionEffect(
            new PotionEffect(PotionEffectType.GLOWING, MINION_DURATION, 0)
        );

        if (isMagma) minion.addPotionEffect(
            new PotionEffect(PotionEffectType.STRENGTH, MINION_DURATION, 3)
        );
        else minion.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, MINION_DURATION, 3)
        );

        new BukkitRunnable() {
            int timeLeft = MINION_DURATION;

            @Override
            public void run() {
                if (minion == null || !minion.isValid() || minion.isDead()) {
                    if (minion != null) {
                        int currentSize = 1;
                        if (minion instanceof Slime s) currentSize =
                            s.getSize();
                        else if (minion instanceof MagmaCube m) currentSize =
                            m.getSize();

                        if (currentSize > 1) {
                            int childSize = currentSize / 2;
                            Location loc = minion.getLocation();
                            for (int i = 0; i < 2; i++) {
                                LivingEntity child = isMagma
                                    ? loc.getWorld().spawn(loc, MagmaCube.class)
                                    : loc.getWorld().spawn(loc, Slime.class);

                                if (child instanceof Slime cs) cs.setSize(
                                    childSize
                                );
                                if (child instanceof MagmaCube cm) cm.setSize(
                                    childSize
                                );

                                configureMinion(child, owner, isMagma);
                            }
                        }
                    }
                    this.cancel();
                    return;
                }

                timeLeft--;
                if (timeLeft <= 0) {
                    minion.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(SnapshotPvpPlugin.instance, 0L, 1L);
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (
            e.getAction() != Action.RIGHT_CLICK_AIR &&
            e.getAction() != Action.RIGHT_CLICK_BLOCK
        ) return;

        ItemStack item = e.getItem();
        if (item == null) return;

        boolean isSlimeItem = item.getType() == Material.SLIME_BALL;
        boolean isMagmaItem = item.getType() == Material.MAGMA_CREAM;

        if (!isSlimeItem && !isMagmaItem) return;

        e.setCancelled(true);

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        boolean isMagma = pdc.getOrDefault(
            isMagmaModeKey,
            PersistentDataType.BOOLEAN,
            false
        );

        if (isMagma && isSlimeItem) {
            setNormalMode(p);
        } else if (!isMagma && isMagmaItem) {
            setMagmaMode(p);
        }
    }

    private void setMagmaMode(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        int currentMeter = pdc.getOrDefault(
            meterKey,
            PersistentDataType.INTEGER,
            0
        );
        int newMeter = currentMeter / 2;

        pdc.set(isMagmaModeKey, PersistentDataType.BOOLEAN, true);
        pdc.set(meterKey, PersistentDataType.INTEGER, newMeter);

        updateArmorColor(p, Color.fromRGB(MAGMA_COLOR));

        p.getWorld().playSound(
            p.getLocation(),
            Sound.ENTITY_MAGMA_CUBE_SQUISH,
            1.0f,
            0.8f
        );
        p.getWorld().spawnParticle(
            Particle.FLAME,
            p.getLocation().add(0, 1.0, 0),
            20,
            0.4,
            0.5,
            0.4,
            0.05
        );
    }

    private void setNormalMode(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        int currentMeter = pdc.getOrDefault(
            meterKey,
            PersistentDataType.INTEGER,
            0
        );
        int newMeter = currentMeter / 2;

        pdc.set(isMagmaModeKey, PersistentDataType.BOOLEAN, false);
        pdc.set(meterKey, PersistentDataType.INTEGER, newMeter);

        updateArmorColor(p, Color.fromRGB(SLIME_COLOR));

        p.getWorld().playSound(
            p.getLocation(),
            Sound.ENTITY_SLIME_SQUISH,
            1.0f,
            1.0f
        );
        p.getWorld().spawnParticle(
            Particle.ITEM_SLIME,
            p.getLocation().add(0, 1.0, 0),
            20,
            0.4,
            0.5,
            0.4,
            0.05
        );
    }

    private void updateArmorColor(Player p, Color color) {
        ItemStack[] armor = p.getInventory().getArmorContents();

        for (ItemStack item : armor) {
            if (item != null) {
                if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
                    meta.setColor(color);
                    item.setItemMeta(meta);
                }

                if (item.getType().name().endsWith("_HELMET") && item.getItemMeta() instanceof ArmorMeta armorMeta) {
                    TrimMaterial material = color.asRGB() == SLIME_COLOR ? TrimMaterial.DIAMOND : TrimMaterial.NETHERITE;
                    armorMeta.setTrim(new ArmorTrim(material, TrimPattern.FLOW));
                    item.setItemMeta(armorMeta);
                }
            }
        }

        p.getInventory().setArmorContents(armor);
    }

    @Override
    public void onLeaveCombat(Player p) {
        updateArmorColor(p, Color.fromRGB(SLIME_COLOR));
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        resetKit(p);
    }
}
