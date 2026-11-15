package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.MannequinFighter;
import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public class TwoTimer extends ManaKit {

    private static final int SWORD_TP_HITS = 5;
    private static final double TP_DISTANCE = 3.5;

    private static final int TWIN_TOY_COST = 12; // 12 attacks to summon Twin Toy
    private static final int SWAP_COOLDOWN_TICKS = 100; // 5 seconds between swaps
    private static final int JACK_RESTOCK_TICKS = 200; // 10 seconds per Jack in the Box
    private static final int OUT_OF_COMBAT_HEAL_TICKS = 300; // 15 seconds out of combat[cite: 2]
    private static final int TWIN_KILL_REGEN_TICKS = 200; // 10 seconds of regen after a twin kill

    private static final int BUILD_RESTOCK_TICKS = 60; // 3 seconds per block batch
    private static final int MAX_BUILD_BLOCKS = 16; // Caps at 16 planks and 16 stone
    private static final int PLANK_BREAK_TICKS = 100; // 5s auto-break
    private static final int STONE_BREAK_TICKS = 160; // 8s auto-break

    // Player PDC counters
    private final NamespacedKey leftHitsKey = ManaKitListener.MANA_KIT_DATA0;
    private final NamespacedKey rightHitsKey = ManaKitListener.MANA_KIT_DATA1;
    private final NamespacedKey twinChargeKey = ManaKitListener.MANA_KIT_DATA2;
    private final NamespacedKey swapCooldownKey =
        ManaKitListener.MANA_KIT_DATA3;
    private final NamespacedKey jackTimerKey = ManaKitListener.MANA_KIT_DATA4;
    private final NamespacedKey outOfCombatKey = ManaKitListener.MANA_KIT_DATA5;
    private final NamespacedKey buildTimerKey = ManaKitListener.MANA_KIT_DATA6;

    private final NamespacedKey twinUUIDKey = ManaKitListener.MANA_KIT_DATASTR0;
    private final NamespacedKey twinUpgradedKey =
        ManaKitListener.MANA_KIT_DATABOOL0;

    // Item & Entity PDC tags
    private final NamespacedKey leftSwordTag = new NamespacedKey(
        "defspacemine",
        "twotimer_left_sword"
    );
    private final NamespacedKey rightSwordTag = new NamespacedKey(
        "defspacemine",
        "twotimer_right_sword"
    );
    private final NamespacedKey twinEntityOwnerTag = new NamespacedKey(
        "defspacemine",
        "twotimer_twin_owner"
    );

    // Tracks placed Build & Play blocks per player for cleanup and auto-breaking
    private final Map<UUID, Set<Block>> placedBlocksMap = new HashMap<>();

    // Live Twin Toy fighters per owner
    private final Map<UUID, MannequinFighter> twins = new HashMap<>();

    public TwoTimer() {
        super("two_timer", "Two Timer", "[Melee Damage]", 0);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -185,
            16,
            -185
        );

        ensureSwordsTagged(p);
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(leftHitsKey, PersistentDataType.INTEGER, 0);
        pdc.set(rightHitsKey, PersistentDataType.INTEGER, 0);
        pdc.set(twinChargeKey, PersistentDataType.INTEGER, 0);
        pdc.set(swapCooldownKey, PersistentDataType.INTEGER, 0);
        pdc.set(jackTimerKey, PersistentDataType.INTEGER, 0);
        pdc.set(
            outOfCombatKey,
            PersistentDataType.INTEGER,
            OUT_OF_COMBAT_HEAL_TICKS
        );
        pdc.set(buildTimerKey, PersistentDataType.INTEGER, 0);
        pdc.set(twinUpgradedKey, PersistentDataType.BOOLEAN, false);

        removeTwinToy(p);
        cleanupPlacedBlocks(p);

        SnapshotPvpPlugin.clearInv(p, Material.ARMOR_STAND);
        SnapshotPvpPlugin.clearInv(p, Material.ENDER_EYE);
        SnapshotPvpPlugin.clearInv(p, Material.JUKEBOX);
        SnapshotPvpPlugin.clearInv(p, Material.OAK_PLANKS);
        SnapshotPvpPlugin.clearInv(p, Material.STONE);
    }

    @Override
    public void onEnterCombat(Player p) {
        resetKit(p);
        ensureSwordsTagged(p);
        p.getPersistentDataContainer().set(
            outOfCombatKey,
            PersistentDataType.INTEGER,
            0
        );
    }

    @Override
    public void onLeaveCombat(Player p) {
        p.clearActivePotionEffects();
        resetKit(p);
    }

    @Override
    public void onDeath(Player p, PlayerDeathEvent e) {
        removeTwinToy(p);
        cleanupPlacedBlocks(p);
    }

    @Override
    public void onIdleTick(Player p) {
        handleHungerAndOutOfCombatHeal(p);
    }

    @Override
    public void onCombatTick(Player p) {
        ensureSwordsTagged(p);

        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        int leftHits = pdc.getOrDefault(
            leftHitsKey,
            PersistentDataType.INTEGER,
            0
        );
        int rightHits = pdc.getOrDefault(
            rightHitsKey,
            PersistentDataType.INTEGER,
            0
        );
        int twinCharge = pdc.getOrDefault(
            twinChargeKey,
            PersistentDataType.INTEGER,
            0
        );
        int swapCd = pdc.getOrDefault(
            swapCooldownKey,
            PersistentDataType.INTEGER,
            0
        );
        int jackTimer = pdc.getOrDefault(
            jackTimerKey,
            PersistentDataType.INTEGER,
            0
        );
        int buildTimer = pdc.getOrDefault(
            buildTimerKey,
            PersistentDataType.INTEGER,
            0
        );

        handleHungerAndOutOfCombatHeal(p);

        if (killstreak >= 3) {
            p.addPotionEffect(
                new PotionEffect(PotionEffectType.STRENGTH, 100, 0)
            );
        }
        if (killstreak >= 4) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
        }

        Mannequin twin = getTwinToy(p);
        if (twin != null) {
            syncTwinToy(p, twin, killstreak);
            getTwinFighter(p, twin); // Ensure MannequinFighter brain is active
            if (swapCd > 0) {
                swapCd--;
                pdc.set(swapCooldownKey, PersistentDataType.INTEGER, swapCd);
            }
            if (!hasTwinItem(p)) {
                giveTwinSwapItem(p);
            }
        } else {
            twins.remove(p.getUniqueId());
            SnapshotPvpPlugin.clearInv(p, Material.ENDER_EYE);
            if (twinCharge >= TWIN_TOY_COST && !hasTwinItem(p)) {
                pdc.set(twinChargeKey, PersistentDataType.INTEGER, 0);
                giveTwinSummonItem(p);
            }
        }

        if (SnapshotPvpPlugin.countInv(p, Material.JUKEBOX) == 0) {
            jackTimer++;
            if (jackTimer >= JACK_RESTOCK_TICKS) {
                jackTimer = 0;
                giveJackInTheBox(p);
            }
            pdc.set(jackTimerKey, PersistentDataType.INTEGER, jackTimer);
        }

        buildTimer++;
        if (buildTimer >= BUILD_RESTOCK_TICKS) {
            buildTimer = 0;
            restockBuildBlocks(p, killstreak >= 2);
        }
        pdc.set(buildTimerKey, PersistentDataType.INTEGER, buildTimer);

        String twinStatus;
        if (twin != null) {
            twinStatus =
                swapCd <= 0
                    ? ChatColor.GREEN + "Swap READY"
                    : ChatColor.YELLOW +
                      "Swap " +
                      String.format("%.1fs", swapCd / 20.0);
        } else if (hasTwinItem(p)) {
            twinStatus = ChatColor.GREEN + "READY";
        } else {
            twinStatus =
                ChatColor.WHITE + "" + twinCharge + "/" + TWIN_TOY_COST;
        }

        String jackStatus =
            SnapshotPvpPlugin.countInv(p, Material.JUKEBOX) > 0
                ? ChatColor.GREEN + "READY"
                : ChatColor.WHITE +
                  "" +
                  (jackTimer * 100) / JACK_RESTOCK_TICKS +
                  "%";

        int outOfCombat = pdc.getOrDefault(
            outOfCombatKey,
            PersistentDataType.INTEGER,
            0
        );
        String healStatus =
            outOfCombat >= OUT_OF_COMBAT_HEAL_TICKS
                ? ChatColor.GREEN + "FULL"
                : ChatColor.WHITE +
                  String.format(
                      "%ds",
                      (OUT_OF_COMBAT_HEAL_TICKS - outOfCombat + 19) / 20
                  );

        p.sendActionBar(
            ChatColor.AQUA +
                "L: " +
                ChatColor.WHITE +
                leftHits +
                "/" +
                SWORD_TP_HITS +
                ChatColor.GRAY +
                " " +
                ChatColor.GOLD +
                "R: " +
                ChatColor.WHITE +
                rightHits +
                "/" +
                SWORD_TP_HITS +
                ChatColor.GRAY +
                " | " +
                ChatColor.LIGHT_PURPLE +
                "Twin: " +
                twinStatus +
                ChatColor.GRAY +
                " | " +
                ChatColor.YELLOW +
                "Box: " +
                jackStatus +
                ChatColor.GRAY +
                " | " +
                ChatColor.GREEN +
                "Rest: " +
                healStatus +
                ChatColor.GRAY +
                " | " +
                ChatColor.RED +
                "KS: " +
                ChatColor.WHITE +
                killstreak +
                "/4"
        );
    }

    private void handleHungerAndOutOfCombatHeal(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int outOfCombat = pdc.getOrDefault(
            outOfCombatKey,
            PersistentDataType.INTEGER,
            0
        );

        if (outOfCombat < OUT_OF_COMBAT_HEAL_TICKS) {
            outOfCombat++;
            pdc.set(outOfCombatKey, PersistentDataType.INTEGER, outOfCombat);

            if (outOfCombat == OUT_OF_COMBAT_HEAL_TICKS) {
                p.setHealth(p.getMaxHealth());
                p.setFoodLevel(20);
                p.setSaturation(20f);
                p.getWorld().playSound(
                    p.getLocation(),
                    Sound.ENTITY_PLAYER_LEVELUP,
                    0.8f,
                    1.2f
                );
                p.getWorld().spawnParticle(
                    Particle.HEART,
                    p.getLocation().add(0, 1.5, 0),
                    10,
                    0.4,
                    0.4,
                    0.4,
                    0.05
                );
            }
        } else {
            // Out of combat: keep hunger and saturation infinite
            if (p.getFoodLevel() < 20) {
                p.setFoodLevel(20);
            }
            if (p.getSaturation() < 20f) {
                p.setSaturation(20f);
            }
        }
    }

    @Override
    public void onConsume(Player p, PlayerItemConsumeEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int outOfCombat = pdc.getOrDefault(
            outOfCombatKey,
            PersistentDataType.INTEGER,
            0
        );
        if (outOfCombat < OUT_OF_COMBAT_HEAL_TICKS) {
            Bukkit.getScheduler().runTask(SnapshotPvpPlugin.instance, () ->
                p.setSaturation(0f)
            );
        }
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        p.getPersistentDataContainer().set(
            outOfCombatKey,
            PersistentDataType.INTEGER,
            0
        );
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (victim.equals(p)) return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(outOfCombatKey, PersistentDataType.INTEGER, 0);

        if (
            victim instanceof Player && getTwinToy(p) == null && !hasTwinItem(p)
        ) {
            int twinCharge =
                pdc.getOrDefault(twinChargeKey, PersistentDataType.INTEGER, 0) +
                1;
            pdc.set(twinChargeKey, PersistentDataType.INTEGER, twinCharge);
        }

        ItemStack held = p.getInventory().getItemInMainHand();
        if (held == null || !held.hasItemMeta()) return;

        PersistentDataContainer itemPdc = held
            .getItemMeta()
            .getPersistentDataContainer();

        if (itemPdc.has(leftSwordTag, PersistentDataType.BYTE)) {
            int hits =
                pdc.getOrDefault(leftHitsKey, PersistentDataType.INTEGER, 0) +
                1;
            if (hits >= SWORD_TP_HITS) {
                pdc.set(leftHitsKey, PersistentDataType.INTEGER, 0);
                teleportSideways(p, victim, true);
            } else {
                pdc.set(leftHitsKey, PersistentDataType.INTEGER, hits);
            }
        } else if (itemPdc.has(rightSwordTag, PersistentDataType.BYTE)) {
            int hits =
                pdc.getOrDefault(rightHitsKey, PersistentDataType.INTEGER, 0) +
                1;
            if (hits >= SWORD_TP_HITS) {
                pdc.set(rightHitsKey, PersistentDataType.INTEGER, 0);
                teleportSideways(p, victim, false);
            } else {
                pdc.set(rightHitsKey, PersistentDataType.INTEGER, hits);
            }
        }
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (
            e.getAction() != Action.RIGHT_CLICK_AIR &&
            e.getAction() != Action.RIGHT_CLICK_BLOCK
        ) return;

        ItemStack item = e.getItem();
        if (item == null) return;

        if (
            e.getAction() == Action.RIGHT_CLICK_BLOCK &&
            e.getClickedBlock() != null &&
            (item.getType() == Material.OAK_PLANKS ||
                item.getType() == Material.STONE)
        ) {
            e.setCancelled(true);
            placeAutoBreakingBlock(
                p,
                item,
                e.getClickedBlock(),
                e.getBlockFace()
            );
            return;
        }

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Material itemType = item.getType();

        if (
            itemType == Material.ARMOR_STAND || itemType == Material.ENDER_EYE
        ) {
            e.setCancelled(true);
            Mannequin twin = getTwinToy(p);

            if (twin == null) {
                Location spawnLoc =
                    e.getClickedBlock() != null
                        ? e
                              .getClickedBlock()
                              .getRelative(e.getBlockFace())
                              .getLocation()
                              .add(0.5, 0, 0.5)
                        : p.getLocation();
                item.setAmount(item.getAmount() - 1);
                spawnTwinToy(p, spawnLoc);
                giveTwinSwapItem(p);
            } else {
                int swapCd = pdc.getOrDefault(
                    swapCooldownKey,
                    PersistentDataType.INTEGER,
                    0
                );
                if (swapCd > 0) {
                    p.playSound(
                        p.getLocation(),
                        Sound.ENTITY_VILLAGER_NO,
                        0.6f,
                        1.2f
                    );
                    return;
                }
                swapWithTwin(p, twin);
                pdc.set(
                    swapCooldownKey,
                    PersistentDataType.INTEGER,
                    SWAP_COOLDOWN_TICKS
                );
            }
            return;
        }

        if (itemType == Material.JUKEBOX) {
            e.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
            activateJackInTheBox(p);
            return;
        }

        if (!item.hasItemMeta()) return;
        PersistentDataContainer itemPdc = item
            .getItemMeta()
            .getPersistentDataContainer();

        if (itemPdc.has(leftSwordTag, PersistentDataType.BYTE)) {
            if (p.hasCooldown(item.getType())) return;
            int hits = pdc.getOrDefault(
                leftHitsKey,
                PersistentDataType.INTEGER,
                0
            );
            if (hits < SWORD_TP_HITS - 1) {
                pdc.set(leftHitsKey, PersistentDataType.INTEGER, hits + 1);
                p.setCooldown(item.getType(), 5);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.6f);
            }
            return;
        }

        if (itemPdc.has(rightSwordTag, PersistentDataType.BYTE)) {
            if (p.hasCooldown(item.getType())) return;
            int hits = pdc.getOrDefault(
                rightHitsKey,
                PersistentDataType.INTEGER,
                0
            );
            if (hits < SWORD_TP_HITS - 1) {
                pdc.set(rightHitsKey, PersistentDataType.INTEGER, hits + 1);
                p.setCooldown(item.getType(), 5);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
            }
            return;
        }
    }

    private void ensureSwordsTagged(Player p) {
        PlayerInventory inv = p.getInventory();
        boolean hasLeft = false;
        boolean hasRight = false;

        for (ItemStack item : inv.getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            PersistentDataContainer pdc = item
                .getItemMeta()
                .getPersistentDataContainer();
            if (pdc.has(leftSwordTag, PersistentDataType.BYTE)) hasLeft = true;
            if (pdc.has(rightSwordTag, PersistentDataType.BYTE)) hasRight =
                true;
        }

        if (hasLeft && hasRight) return;

        for (ItemStack item : inv.getContents()) {
            if (
                item == null || !item.getType().name().endsWith("_SWORD")
            ) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (
                pdc.has(leftSwordTag, PersistentDataType.BYTE) ||
                pdc.has(rightSwordTag, PersistentDataType.BYTE)
            ) continue;

            if (!hasLeft) {
                pdc.set(leftSwordTag, PersistentDataType.BYTE, (byte) 1);
                if (!meta.hasDisplayName()) {
                    meta.displayName(
                        Component.text("Left Step Blade", NamedTextColor.AQUA)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false)
                    );
                }
                item.setItemMeta(meta);
                hasLeft = true;
            } else if (!hasRight) {
                pdc.set(rightSwordTag, PersistentDataType.BYTE, (byte) 1);
                if (!meta.hasDisplayName()) {
                    meta.displayName(
                        Component.text("Right Step Blade", NamedTextColor.GOLD)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false)
                    );
                }
                item.setItemMeta(meta);
                hasRight = true;
            }
        }

        if (!hasLeft) {
            ItemStack left = new ItemStack(Material.IRON_SWORD);
            ItemMeta meta = left.getItemMeta();
            meta.displayName(
                Component.text("Left Step Blade", NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false)
            );
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.addEnchant(Enchantment.SHARPNESS, 2, true);
            meta.addEnchant(Enchantment.BREACH, 2, true);
            meta.getPersistentDataContainer().set(
                leftSwordTag,
                PersistentDataType.BYTE,
                (byte) 1
            );
            left.setItemMeta(meta);
            inv.addItem(left);
        }

        if (!hasRight) {
            ItemStack right = new ItemStack(Material.IRON_SWORD);
            ItemMeta meta = right.getItemMeta();
            meta.displayName(
                Component.text("Right Step Blade", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false)
            );
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.addEnchant(Enchantment.SHARPNESS, 2, true);
            meta.addEnchant(Enchantment.BREACH, 2, true);
            meta.getPersistentDataContainer().set(
                rightSwordTag,
                PersistentDataType.BYTE,
                (byte) 1
            );
            right.setItemMeta(meta);
            inv.addItem(right);
        }
    }

    private void teleportSideways(
        Player p,
        LivingEntity target,
        boolean goLeft
    ) {
        Location start = p.getLocation();
        Vector forward = start.getDirection().setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            forward = new Vector(0, 0, 1);
        } else {
            forward.normalize();
        }

        Vector sideDir = goLeft
            ? new Vector(forward.getZ(), 0, -forward.getX()).normalize()
            : new Vector(-forward.getZ(), 0, forward.getX()).normalize();

        Location dest = start.clone();
        for (double d = 0.5; d <= TP_DISTANCE; d += 0.5) {
            Location check = start.clone().add(sideDir.clone().multiply(d));
            if (
                !check.getBlock().isPassable() ||
                !check.clone().add(0, 1, 0).getBlock().isPassable()
            ) {
                break;
            }
            dest = check;
        }

        if (target != null) {
            Vector toTarget = target
                .getLocation()
                .toVector()
                .subtract(dest.toVector());
            if (toTarget.lengthSquared() > 1.0E-4) {
                float pitch = start.getPitch();
                dest.setDirection(toTarget.normalize());
                dest.setPitch(pitch);
            }
        }

        DustOptions dust = new DustOptions(
            goLeft ? Color.AQUA : Color.ORANGE,
            1.3f
        );
        start
            .getWorld()
            .spawnParticle(
                Particle.DUST,
                start.clone().add(0, 1, 0),
                18,
                0.3,
                0.5,
                0.3,
                0,
                dust
            );

        p.teleport(dest);

        dest.getWorld().spawnParticle(
            Particle.DUST,
            dest.clone().add(0, 1, 0),
            18,
            0.3,
            0.5,
            0.3,
            0,
            dust
        );
        dest.getWorld().playSound(
            dest,
            Sound.ENTITY_ENDERMAN_TELEPORT,
            0.9f,
            goLeft ? 1.4f : 0.8f
        );
    }

    private void spawnTwinToy(Player owner, Location loc) {
        removeTwinToy(owner);

        int killstreak = SnapshotPvpPlugin.getPlayerScore(
            owner,
            "dummyKillstreak"
        );
        boolean upgraded = killstreak >= 1;
        double hpRatio = upgraded ? 0.75 : 0.50;
        double maxHp = Math.max(2.0, owner.getMaxHealth() * hpRatio);

        Mannequin twin = loc.getWorld().spawn(loc, Mannequin.class);

        twin.getPersistentDataContainer().set(
            twinEntityOwnerTag,
            PersistentDataType.STRING,
            owner.getUniqueId().toString()
        );

        SnapshotPvpPlugin.addToTeam(owner, twin);
        CustomEggListener.injectOwner(twin, owner);

        if (twin.getAttribute(Attribute.MAX_HEALTH) != null) {
            twin.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHp);
        }
        twin.setHealth(maxHp);

        configureTwinToy(owner, twin);

        // Initialize and track MannequinFighter
        MannequinFighter fighter = new MannequinFighter(
            SnapshotPvpPlugin.instance,
            twin,
            MannequinFighter.Difficulty.HARD
        );
        twins.put(owner.getUniqueId(), fighter);

        PersistentDataContainer pdc = owner.getPersistentDataContainer();
        pdc.set(
            twinUUIDKey,
            PersistentDataType.STRING,
            twin.getUniqueId().toString()
        );
        pdc.set(twinUpgradedKey, PersistentDataType.BOOLEAN, upgraded);

        syncTwinToy(owner, twin, killstreak);

        loc.getWorld().playSound(
            loc,
            Sound.ENTITY_ILLUSIONER_MIRROR_MOVE,
            1.0f,
            1.1f
        );
        loc.getWorld().spawnParticle(
            Particle.CLOUD,
            loc.clone().add(0, 1, 0),
            25,
            0.3,
            0.5,
            0.3,
            0.05
        );
    }

    private void configureTwinToy(Player owner, Mannequin twin) {
        twin.setImmovable(false);
        twin.setProfile(
            ResolvableProfile.resolvableProfile().name(owner.getName()).build()
        );

        EntityEquipment ownerEq = owner.getEquipment();
        EntityEquipment twinEq = twin.getEquipment();

        if (ownerEq != null && twinEq != null) {
            twinEq.setHelmet(ownerEq.getHelmet());
            twinEq.setChestplate(ownerEq.getChestplate());
            twinEq.setLeggings(ownerEq.getLeggings());
            twinEq.setBoots(ownerEq.getBoots());
            twinEq.setItemInMainHand(ownerEq.getItemInMainHand());
            twinEq.setItemInOffHand(ownerEq.getItemInOffHand());
        }
    }

    private void syncTwinToy(Player owner, Mannequin twin, int killstreak) {
        PersistentDataContainer pdc = owner.getPersistentDataContainer();
        boolean wasUpgraded = pdc.getOrDefault(
            twinUpgradedKey,
            PersistentDataType.BOOLEAN,
            false
        );

        if (killstreak >= 1 && !wasUpgraded) {
            pdc.set(twinUpgradedKey, PersistentDataType.BOOLEAN, true);
            double newMax = owner.getMaxHealth() * 0.75;
            if (twin.getAttribute(Attribute.MAX_HEALTH) != null) {
                double oldMax = twin
                    .getAttribute(Attribute.MAX_HEALTH)
                    .getBaseValue();
                double ratio = oldMax > 0 ? twin.getHealth() / oldMax : 1.0;
                twin.getAttribute(Attribute.MAX_HEALTH).setBaseValue(newMax);
                twin.setHealth(Math.min(newMax, Math.max(1.0, newMax * ratio)));
            }
        }

        EntityEquipment equip = twin.getEquipment();
        if (equip != null) {
            equip.setArmorContents(
                owner.getInventory().getArmorContents().clone()
            );
            equip.setItemInMainHand(
                owner.getInventory().getItemInMainHand().clone()
            );
            equip.setItemInOffHand(
                owner.getInventory().getItemInOffHand().clone()
            );
        }

        int hp = (int) Math.ceil(twin.getHealth());
        int maxHp = (int) Math.ceil(
            twin.getAttribute(Attribute.MAX_HEALTH) != null
                ? twin.getAttribute(Attribute.MAX_HEALTH).getBaseValue()
                : 10.0
        );
        twin.customName(
            Component.text(
                owner.getName() + "'s Twin Toy ",
                NamedTextColor.LIGHT_PURPLE
            ).append(
                Component.text(hp + "/" + maxHp + " HP", NamedTextColor.RED)
            )
        );
        twin.setCustomNameVisible(true);
    }

    private MannequinFighter getTwinFighter(Player owner, Mannequin twin) {
        MannequinFighter fighter = twins.get(owner.getUniqueId());
        if (fighter == null || !fighter.mannequin.equals(twin)) {
            fighter = new MannequinFighter(SnapshotPvpPlugin.instance, twin);
            twins.put(owner.getUniqueId(), fighter);
        }
        return fighter;
    }

    private void swapWithTwin(Player p, Mannequin twin) {
        Location pLoc = p.getLocation().clone();
        Location tLoc = twin.getLocation().clone();
        Vector pVel = p.getVelocity().clone();
        Vector tVel = twin.getVelocity().clone();

        p.getWorld().spawnParticle(
            Particle.PORTAL,
            pLoc.clone().add(0, 1, 0),
            25,
            0.3,
            0.5,
            0.3,
            0.1
        );
        p.getWorld().spawnParticle(
            Particle.PORTAL,
            tLoc.clone().add(0, 1, 0),
            25,
            0.3,
            0.5,
            0.3,
            0.1
        );

        p.teleport(tLoc);
        twin.teleport(pLoc);
        p.setVelocity(tVel);
        twin.setVelocity(pVel);

        p.getWorld().playSound(
            tLoc,
            Sound.ENTITY_ENDERMAN_TELEPORT,
            1.0f,
            1.2f
        );
        p.getWorld().playSound(
            pLoc,
            Sound.ENTITY_ENDERMAN_TELEPORT,
            1.0f,
            1.2f
        );
    }

    private Mannequin getTwinToy(Player owner) {
        PersistentDataContainer pdc = owner.getPersistentDataContainer();
        String uuidStr = pdc.get(twinUUIDKey, PersistentDataType.STRING);
        if (uuidStr == null || uuidStr.isEmpty()) return null;
        try {
            Entity ent = Bukkit.getEntity(UUID.fromString(uuidStr));
            if (ent instanceof Mannequin m && m.isValid() && !m.isDead()) {
                return m;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void removeTwinToy(Player owner) {
        Mannequin twin = getTwinToy(owner);
        if (twin != null) {
            twin.remove();
        }
        owner
            .getPersistentDataContainer()
            .set(twinUUIDKey, PersistentDataType.STRING, "");
    }

    private void activateJackInTheBox(Player p) {
        Location loc = p.getLocation().add(0, 1, 0);
        World world = p.getWorld();

        world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
        world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.7f, 1.4f);
        world.spawnParticle(Particle.NOTE, loc, 12, 0.4, 0.5, 0.4, 1.0);
        world.spawnParticle(
            Particle.TOTEM_OF_UNDYING,
            loc,
            15,
            0.3,
            0.4,
            0.3,
            0.1
        );

        int roll = ThreadLocalRandom.current().nextInt(6);
        switch (roll) {
            case 0 -> {
                p.addPotionEffect(
                    new PotionEffect(PotionEffectType.SATURATION, 120, 0)
                );
                p.sendMessage(
                    ChatColor.YELLOW +
                        "Jack in the Box: " +
                        ChatColor.AQUA +
                        "Saturation (6s)!"
                );
            }
            case 1 -> {
                p.addPotionEffect(
                    new PotionEffect(PotionEffectType.HASTE, 120, 0)
                );
                p.sendMessage(
                    ChatColor.YELLOW +
                        "Jack in the Box: " +
                        ChatColor.GOLD +
                        "Haste I (6s)!"
                );
            }
            case 2 -> {
                p.addPotionEffect(
                    new PotionEffect(PotionEffectType.REGENERATION, 100, 0)
                );
                p.sendMessage(
                    ChatColor.YELLOW +
                        "Jack in the Box: " +
                        ChatColor.LIGHT_PURPLE +
                        "Regeneration I (5s)!"
                );
            }
            case 3 -> {
                p.addPotionEffect(
                    new PotionEffect(PotionEffectType.RESISTANCE, 100, 0)
                );
                p.sendMessage(
                    ChatColor.YELLOW +
                        "Jack in the Box: " +
                        ChatColor.BLUE +
                        "Resistance I (5s)!"
                );
            }
            case 4 -> {
                p.addPotionEffect(
                    new PotionEffect(PotionEffectType.ABSORPTION, 160, 0)
                );
                p.sendMessage(
                    ChatColor.YELLOW +
                        "Jack in the Box: " +
                        ChatColor.GREEN +
                        "Absorption I (8s)!"
                );
            }
            case 5 -> {
                p.addPotionEffect(
                    new PotionEffect(PotionEffectType.JUMP_BOOST, 120, 1)
                );
                p.sendMessage(
                    ChatColor.YELLOW +
                        "Jack in the Box: " +
                        ChatColor.WHITE +
                        "Jump Boost II (6s)!"
                );
            }
        }
    }

    private void restockBuildBlocks(Player p, boolean includeStone) {
        PlayerInventory inv = p.getInventory();
        int planks = SnapshotPvpPlugin.countInv(p, Material.OAK_PLANKS);
        if (planks < MAX_BUILD_BLOCKS) {
            int toAdd = Math.min(2, MAX_BUILD_BLOCKS - planks);
            ItemStack plankStack = new ItemStack(Material.OAK_PLANKS, toAdd);
            ItemMeta meta = plankStack.getItemMeta();
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            plankStack.setItemMeta(meta);
            inv.addItem(plankStack);
        }

        if (includeStone) {
            int stones = SnapshotPvpPlugin.countInv(p, Material.STONE);
            if (stones < MAX_BUILD_BLOCKS) {
                int toAdd = Math.min(2, MAX_BUILD_BLOCKS - stones);
                ItemStack stoneStack = new ItemStack(Material.STONE, toAdd);
                ItemMeta meta = stoneStack.getItemMeta();
                meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
                stoneStack.setItemMeta(meta);
                inv.addItem(stoneStack);
            }
        }
    }

    private void placeAutoBreakingBlock(
        Player p,
        ItemStack handItem,
        Block clickedBlock,
        org.bukkit.block.BlockFace face
    ) {
        Block targetBlock = clickedBlock.isReplaceable()
            ? clickedBlock
            : clickedBlock.getRelative(face);

        if (!targetBlock.getType().isAir() && !targetBlock.isReplaceable()) {
            return;
        }

        World world = targetBlock.getWorld();
        BoundingBox box = BoundingBox.of(targetBlock);
        if (
            !world
                .getNearbyEntities(
                    box,
                    ent ->
                        ent instanceof LivingEntity &&
                        !(ent instanceof ArmorStand)
                )
                .isEmpty()
        ) {
            return;
        }

        Material placedType = handItem.getType();
        targetBlock.setType(placedType);

        if (p.getGameMode() != GameMode.CREATIVE) {
            handItem.setAmount(handItem.getAmount() - 1);
        }

        world.playSound(
            targetBlock.getLocation().add(0.5, 0.5, 0.5),
            placedType == Material.STONE
                ? Sound.BLOCK_STONE_PLACE
                : Sound.BLOCK_WOOD_PLACE,
            1.0f,
            1.0f
        );

        Set<Block> playerBlocks = placedBlocksMap.computeIfAbsent(
            p.getUniqueId(),
            k -> new HashSet<>()
        );
        playerBlocks.add(targetBlock);

        final int totalTicks =
            placedType == Material.STONE
                ? STONE_BREAK_TICKS
                : PLANK_BREAK_TICKS;
        final int sourceId = ThreadLocalRandom.current().nextInt(
            1_000_000,
            2_000_000
        );
        final Location blockLoc = targetBlock.getLocation();

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (targetBlock.getType() != placedType) {
                    clearCrackAnimation(world, blockLoc, sourceId);
                    playerBlocks.remove(targetBlock);
                    this.cancel();
                    return;
                }

                if (elapsed >= totalTicks) {
                    clearCrackAnimation(world, blockLoc, sourceId);
                    BlockData data = targetBlock.getBlockData();
                    world.spawnParticle(
                        Particle.BLOCK,
                        blockLoc.clone().add(0.5, 0.5, 0.5),
                        20,
                        0.25,
                        0.25,
                        0.25,
                        0.05,
                        data
                    );
                    world.playSound(
                        blockLoc.clone().add(0.5, 0.5, 0.5),
                        placedType == Material.STONE
                            ? Sound.BLOCK_STONE_BREAK
                            : Sound.BLOCK_WOOD_BREAK,
                        0.9f,
                        1.0f
                    );
                    targetBlock.setType(Material.AIR);
                    playerBlocks.remove(targetBlock);
                    this.cancel();
                    return;
                }

                float progress = Math.min(
                    0.99f,
                    (float) elapsed / (float) totalTicks
                );
                for (Player viewer : world.getPlayers()) {
                    viewer.sendBlockDamage(blockLoc, progress, sourceId);
                }

                elapsed += 5;
            }
        }.runTaskTimer(SnapshotPvpPlugin.instance, 0L, 5L);
    }

    private void clearCrackAnimation(
        World world,
        Location blockLoc,
        int sourceId
    ) {
        for (Player viewer : world.getPlayers()) {
            viewer.sendBlockDamage(blockLoc, 0.0f, sourceId);
        }
    }

    private void cleanupPlacedBlocks(Player p) {
        Set<Block> blocks = placedBlocksMap.remove(p.getUniqueId());
        if (blocks == null) return;
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (
                b.getType() == Material.OAK_PLANKS ||
                b.getType() == Material.STONE
            ) {
                b.setType(Material.AIR);
            }
        }
    }

    private void giveTwinSummonItem(Player p) {
        SnapshotPvpPlugin.clearInv(p, Material.ARMOR_STAND);
        ItemStack item = new ItemStack(Material.ARMOR_STAND, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Twin Toy"
            );
            meta.setLore(
                List.of(
                    ChatColor.GRAY + "Right-click to summon a Twin Toy clone",
                    ChatColor.GRAY + "that copies your armor and held weapon."
                )
            );
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            item.setItemMeta(meta);
        }
        p.getInventory().addItem(item);
        playItemGainFeedback(p);
    }

    private void giveTwinSwapItem(Player p) {
        SnapshotPvpPlugin.clearInv(p, Material.ENDER_EYE);
        ItemStack item = new ItemStack(Material.ENDER_EYE, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Twin Swap"
            );
            meta.setLore(
                List.of(
                    ChatColor.GRAY + "Right-click to swap positions with your",
                    ChatColor.LIGHT_PURPLE +
                        "Twin Toy " +
                        ChatColor.GRAY +
                        "(5s cooldown)."
                )
            );
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            item.setItemMeta(meta);
        }
        p.getInventory().addItem(item);
        playItemGainFeedback(p);
    }

    private void giveJackInTheBox(Player p) {
        SnapshotPvpPlugin.clearInv(p, Material.JUKEBOX);
        ItemStack item = new ItemStack(Material.JUKEBOX, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(
                ChatColor.GOLD + "" + ChatColor.BOLD + "Jack in the Box"
            );
            meta.setLore(
                List.of(
                    ChatColor.GRAY + "Right-click to pop the box for",
                    ChatColor.YELLOW + "a random minor buff!"
                )
            );
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            item.setItemMeta(meta);
        }
        p.getInventory().addItem(item);
        playItemGainFeedback(p);
    }

    private boolean hasTwinItem(Player p) {
        return (
            SnapshotPvpPlugin.countInv(p, Material.ARMOR_STAND) > 0 ||
            SnapshotPvpPlugin.countInv(p, Material.ENDER_EYE) > 0
        );
    }
}
