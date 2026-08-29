package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.ZombieHorse;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class HeadlessHorseman extends ManaKit {

    final int midnightRide = 10; // 10 attacks landed earns the Pale Steed egg
    final NamespacedKey midnightRideCounter = ManaKitListener.MANA_KIT_DATA0;
    final NamespacedKey horseUUIDKey = ManaKitListener.MANA_KIT_DATASTR0;

    private ItemStack undyingSteedEgg;
    private ItemStack jackOLantern;

    public HeadlessHorseman() {
        super("headless_horseman", "Headless Horseman", "[Melee Damage]", 0);

        undyingSteedEgg = new ItemStack(Material.ZOMBIE_HORSE_SPAWN_EGG);
        ItemMeta eggMeta = undyingSteedEgg.getItemMeta();
        eggMeta.displayName(
            Component.text("Undying Steed")
                .color(NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
        );
        eggMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        undyingSteedEgg.setItemMeta(eggMeta);

        jackOLantern = new ItemStack(Material.JACK_O_LANTERN);
        ItemMeta lanternMeta = jackOLantern.getItemMeta();
        lanternMeta.addEnchant(Enchantment.PROTECTION, 10, true);
        lanternMeta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        jackOLantern.setItemMeta(lanternMeta);
    }

    private ItemStack getOriginalHelm() {
        return ManaKitListener.getItemFromShulker(
            1,
            "goopshotpeshvp",
            -182,
            13,
            -185
        );
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -182,
            13,
            -185
        );
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(midnightRideCounter, PersistentDataType.INTEGER, 0);
        pdc.set(horseUUIDKey, PersistentDataType.STRING, "");
        removeHorseAndRestoreHelm(p);
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.ZOMBIE_HORSE_SPAWN_EGG);
        resetKit(p);
    }

    @Override
    public void onCombatTick(Player p) {
        checkHorseState(p);

        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int midnightRideC = pdc.get(midnightRideCounter, PersistentDataType.INTEGER);
        boolean hasEgg = p
            .getInventory()
            .contains(Material.ZOMBIE_HORSE_SPAWN_EGG);
        ZombieHorse horse = getTrackedHorse(p);

        String rideStatus;
        NamedTextColor rideColor;
        if (horse != null) {
            rideStatus = "Riding";
            rideColor = NamedTextColor.GOLD;
        } else if (hasEgg) {
            rideStatus = "Ready!";
            rideColor = NamedTextColor.GREEN;
        } else {
            rideStatus = midnightRideC + "/" + midnightRide;
            rideColor = NamedTextColor.WHITE;
        }

        p.sendActionBar(
            Component.text()
                .append(
                    Component.text(
                        "Midnight Ride: ",
                        NamedTextColor.DARK_PURPLE
                    )
                )
                .append(Component.text(rideStatus, rideColor))
                .append(Component.text("  |  ", NamedTextColor.GRAY))
                .append(Component.text("Killstreak: ", NamedTextColor.RED))
                .append(Component.text(killstreak + "/3", NamedTextColor.WHITE))
                .build()
        );

        p.addPotionEffect(
            new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0)
        );
        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 1)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.STRENGTH, 100, 1)
        );

        if (midnightRideC >= midnightRide && !hasEgg && horse == null) {
            p.getInventory().addItem(undyingSteedEgg.clone());
            pdc.set(midnightRideCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;

        ZombieHorse horse = getTrackedHorse(p);
        if (horse != null) return;

        boolean hasEgg = p
            .getInventory()
            .contains(Material.ZOMBIE_HORSE_SPAWN_EGG);
        if (hasEgg) return;

        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(
            midnightRideCounter,
            PersistentDataType.INTEGER,
            pdc.get(midnightRideCounter, PersistentDataType.INTEGER) +
                (killstreak >= 3 ? 2 : 1)
        );
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (!e.getAction().isRightClick()) return;
        if (
            e.getItem() == null ||
            e.getItem().getType() != Material.ZOMBIE_HORSE_SPAWN_EGG
        ) return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (getTrackedHorse(p) != null) return;

        e.setCancelled(true);
        consumeOneFromHand(p, e.getHand());

        Location spawnLoc =
            e.getClickedBlock() != null
                ? e
                      .getClickedBlock()
                      .getRelative(e.getBlockFace())
                      .getLocation()
                      .add(0.5, 0, 0.5)
                : p.getLocation();
        World world = spawnLoc.getWorld();

        ZombieHorse horse = (ZombieHorse) world.spawnEntity(
            spawnLoc,
            EntityType.ZOMBIE_HORSE
        );
        SnapshotPvpPlugin.addToTeam(p, horse);
        horse.setAdult();
        horse.setTamed(true);
        horse.setOwner(p);
        horse.setRemoveWhenFarAway(false);
        horse.setPersistent(true);
        horse.customName(
            Component.text("Undying Steed")
                .color(NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
        );

        if (horse.getAttribute(Attribute.MAX_HEALTH) != null) {
            horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(30.0);
        }
        horse.setHealth(30.0);
        if (horse.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.3375);
        }
        if (horse.getAttribute(Attribute.JUMP_STRENGTH) != null) {
            horse.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(1.0);
        }

        EntityEquipment equipment = horse.getEquipment();
        equipment.setItem(
            EquipmentSlot.BODY,
            new ItemStack(Material.NETHERITE_HORSE_ARMOR)
        );
        equipment.setItem(EquipmentSlot.SADDLE, new ItemStack(Material.SADDLE));

        world.playSound(
            spawnLoc,
            Sound.ENTITY_ZOMBIE_HORSE_AMBIENT,
            1.0f,
            0.7f
        );
        world.spawnParticle(
            Particle.SOUL,
            spawnLoc.clone().add(0, 1, 0),
            25,
            0.4,
            0.6,
            0.4,
            0.03
        );

        horse.addPassenger(p);

        pdc.set(
            horseUUIDKey,
            PersistentDataType.STRING,
            horse.getUniqueId().toString()
        );

        p.getInventory().setHelmet(jackOLantern.clone());
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(midnightRideCounter, PersistentDataType.INTEGER, midnightRide);
    }

    private void checkHorseState(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        String uuidStr = pdc.get(horseUUIDKey, PersistentDataType.STRING);
        if (uuidStr == null || uuidStr.isEmpty()) return;

        ZombieHorse horse = getTrackedHorse(p);

        boolean stillRiding =
            horse != null &&
            p.getVehicle() != null &&
            p.getVehicle().getUniqueId().equals(horse.getUniqueId());

        if (!stillRiding) {
            if (horse != null && horse.isValid()) horse.remove();
            pdc.set(horseUUIDKey, PersistentDataType.STRING, "");
            p.getInventory().setHelmet(getOriginalHelm());
        }
    }

    private void consumeOneFromHand(Player p, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND) {
            ItemStack offHand = p.getInventory().getItemInOffHand();
            offHand.setAmount(offHand.getAmount() - 1);
            p.getInventory().setItemInOffHand(offHand);
        } else {
            ItemStack mainHand = p.getInventory().getItemInMainHand();
            mainHand.setAmount(mainHand.getAmount() - 1);
            p.getInventory().setItemInMainHand(mainHand);
        }
    }

    private void removeHorseAndRestoreHelm(Player p) {
        ZombieHorse horse = getTrackedHorse(p);
        if (horse != null) horse.remove();

        p.getPersistentDataContainer().set(
            horseUUIDKey,
            PersistentDataType.STRING,
            ""
        );
        p.getInventory().setHelmet(getOriginalHelm());
    }

    private ZombieHorse getTrackedHorse(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (!pdc.has(horseUUIDKey, PersistentDataType.STRING)) return null;
        String uuidStr = pdc.get(horseUUIDKey, PersistentDataType.STRING);
        if (uuidStr == null || uuidStr.isEmpty()) return null;
        try {
            Entity entity = Bukkit.getEntity(UUID.fromString(uuidStr));
            if (
                entity instanceof ZombieHorse horse && horse.isValid()
            ) return horse;
        } catch (Exception ignored) {}
        return null;
    }
}
