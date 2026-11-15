package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
import org.bukkit.util.BoundingBox;

public class JadeTrio extends ManaKit {

    private static final int PORTAL_CHARGE_HITS = 20;
    private static final int PORTAL_DURATION_TICKS = 200;
    private static final double PORTAL_RADIUS = 5.0;
    private static final int BLOCK_RESTOCK_TICKS = 60;
    private static final int MAX_EMERALD_BLOCKS = 16;
    private static final int EMERALD_BLOCKS_PER_RESTOCK = 2;
    private static final int EMERALD_BLOCK_BREAK_TICKS = 160;
    private static final String SHADOW_SUFFIX = "_shadow";

    private final NamespacedKey portalChargeCounter =
        ManaKitListener.MANA_KIT_DATA0;
    private final NamespacedKey blockRestockCounter =
        ManaKitListener.MANA_KIT_DATA1;
    private final NamespacedKey portalItemTag = new NamespacedKey(
        "defspacemine",
        "jade_trio_portal_item"
    );
    private final NamespacedKey emeraldBlockItemTag = new NamespacedKey(
        "defspacemine",
        "jade_trio_emerald_block"
    );

    private final ItemStack portalItem;
    private final ItemStack emeraldBlockItem;
    private final Map<UUID, ActivePortal> activePortals = new HashMap<>();
    private final Map<UUID, Set<Block>> placedBlocks = new HashMap<>();

    public JadeTrio() {
        super("jade_trio", "Jade Trio", "[Melee Movement]", 4);
        portalItem = createPortalItem();
        emeraldBlockItem = createEmeraldBlockItem();
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -187,
            4,
            -185
        );
        giveEmeraldBlocks(p, EMERALD_BLOCKS_PER_RESTOCK);
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(portalChargeCounter, PersistentDataType.INTEGER, 0);
        pdc.set(blockRestockCounter, PersistentDataType.INTEGER, 0);
        activePortals.remove(p.getUniqueId());
        cleanupPlacedBlocks(p);
        clearKitItems(p);
    }

    @Override
    public void onCombatTick(Player p) {
        restockEmeraldBlocks(p);
        grantPortalIfCharged(p);

        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        int portalCharge = p
            .getPersistentDataContainer()
            .getOrDefault(portalChargeCounter, PersistentDataType.INTEGER, 0);
        String portalStatus = hasPortalItem(p)
            ? ChatColor.GREEN + "READY"
            : ChatColor.WHITE.toString() +
              portalCharge +
              "/" +
              PORTAL_CHARGE_HITS;

        p.sendActionBar(
            ChatColor.GREEN +
                "Jade Portal: " +
                portalStatus +
                ChatColor.GRAY +
                " | " +
                ChatColor.RED +
                "Killstreak: " +
                ChatColor.WHITE +
                killstreak +
                "/3"
        );

        if (killstreak >= 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 0)
        );
        if (killstreak >= 2) p.addPotionEffect(
            new PotionEffect(PotionEffectType.RESISTANCE, 100, 0)
        );
        if (killstreak >= 3) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SATURATION, 100, 0)
        );
    }

    @Override
    public void onShadowTick(Player p) {
        super.onShadowTick(p);
        restockEmeraldBlocks(p);
        grantPortalIfCharged(p);

        ActivePortal portal = activePortals.get(p.getUniqueId());
        if (portal == null) return;

        if (Bukkit.getCurrentTick() % 5 == 0) renderPortal(portal.center);
        pushPlayersIntoShadowWorld(portal.center);

        portal.ticksRemaining--;
        if (portal.ticksRemaining <= 0) {
            activePortals.remove(p.getUniqueId());
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        p.clearActivePotionEffects();
        resetKit(p);
    }

    @Override
    public void onDeath(Player p, PlayerDeathEvent e) {
        cleanupPlacedBlocks(p);
    }

    @Override
    public void onDeathShadow(Player p, PlayerDeathEvent e) {
        cleanupPlacedBlocks(p);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        addPortalCharge(p);
    }

    @Override
    public void onDamageDealtShadow(Player p, EntityDamageByEntityEvent e) {
        addPortalCharge(p);
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        playAbilityFeedback(p);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 200, 1));
        p.setHealth(p.getHealth() + (p.getMaxHealth() - p.getHealth()) / 2);
    }

    @Override
    public void onKillShadow(Player p, PlayerDeathEvent e) {
        onKill(p, e);
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        handleInteract(p, e);
    }

    @Override
    public void onInteractShadow(Player p, PlayerInteractEvent e) {
        handleInteract(p, e);
    }

    private ItemStack createPortalItem() {
        ItemStack item = new ItemStack(Material.DEEPSLATE_EMERALD_ORE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(ChatColor.GREEN + "Jade Portal"));
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        meta.getPersistentDataContainer().set(
            portalItemTag,
            PersistentDataType.BYTE,
            (byte) 1
        );
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEmeraldBlockItem() {
        ItemStack item = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        meta.getPersistentDataContainer().set(
            emeraldBlockItemTag,
            PersistentDataType.BYTE,
            (byte) 1
        );
        item.setItemMeta(meta);
        return item;
    }

    private void addPortalCharge(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int hits = pdc.getOrDefault(
            portalChargeCounter,
            PersistentDataType.INTEGER,
            0
        );
        pdc.set(
            portalChargeCounter,
            PersistentDataType.INTEGER,
            Math.min(PORTAL_CHARGE_HITS, hits + 1)
        );
    }

    private void grantPortalIfCharged(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int charge = pdc.getOrDefault(
            portalChargeCounter,
            PersistentDataType.INTEGER,
            0
        );
        if (charge < PORTAL_CHARGE_HITS) return;

        if (!hasPortalItem(p)) {
            for (ItemStack leftover : p
                .getInventory()
                .addItem(portalItem.clone())
                .values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
            playItemGainFeedback(p);
        }
        pdc.set(portalChargeCounter, PersistentDataType.INTEGER, 0);
    }

    private void restockEmeraldBlocks(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int ticks = pdc.getOrDefault(
            blockRestockCounter,
            PersistentDataType.INTEGER,
            0
        );
        ticks++;

        if (ticks >= BLOCK_RESTOCK_TICKS) {
            ticks = 0;
            int currentBlocks = SnapshotPvpPlugin.countInv(
                p,
                Material.EMERALD_BLOCK
            );
            int toAdd = Math.min(
                EMERALD_BLOCKS_PER_RESTOCK,
                MAX_EMERALD_BLOCKS - currentBlocks
            );
            if (toAdd > 0) {
                giveEmeraldBlocks(p, toAdd);
                playItemGainFeedback(p);
            }
        }

        pdc.set(blockRestockCounter, PersistentDataType.INTEGER, ticks);
    }

    private void giveEmeraldBlocks(Player p, int amount) {
        if (amount <= 0) return;

        ItemStack blocks = emeraldBlockItem.clone();
        blocks.setAmount(amount);
        for (ItemStack leftover : p.getInventory().addItem(blocks).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), leftover);
        }
    }

    private boolean hasPortalItem(Player p) {
        PlayerInventory inventory = p.getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (isPortalItem(item)) return true;
        }
        return (
            isPortalItem(inventory.getItemInOffHand()) ||
            isPortalItem(p.getItemOnCursor())
        );
    }

    private boolean isPortalItem(ItemStack item) {
        return hasTag(item, Material.DEEPSLATE_EMERALD_ORE, portalItemTag);
    }

    private boolean isEmeraldBlockItem(ItemStack item) {
        return hasTag(item, Material.EMERALD_BLOCK, emeraldBlockItemTag);
    }

    private boolean hasTag(
        ItemStack item,
        Material material,
        NamespacedKey tag
    ) {
        return (
            item != null &&
            item.getType() == material &&
            item.hasItemMeta() &&
            item
                .getItemMeta()
                .getPersistentDataContainer()
                .has(tag, PersistentDataType.BYTE)
        );
    }

    private void handleInteract(Player p, PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (
            e.getAction() != Action.RIGHT_CLICK_AIR &&
            e.getAction() != Action.RIGHT_CLICK_BLOCK
        ) return;

        ItemStack item = e.getItem();
        if (item == null) return;

        if (isPortalItem(item)) {
            e.setCancelled(true);
            activatePortal(p, item);
            return;
        }

        if (
            e.getAction() == Action.RIGHT_CLICK_BLOCK &&
            e.getClickedBlock() != null &&
            isEmeraldBlockItem(item)
        ) {
            e.setCancelled(true);
            placeAutoBreakingBlock(
                p,
                item,
                e.getClickedBlock(),
                e.getBlockFace()
            );
        }
    }

    private void activatePortal(Player p, ItemStack item) {
        if (ManaKit.isInShadowWorld(p)) return;

        Location center = getNormalWorldLocation(p);
        if (center == null) {
            p.sendMessage(
                ChatColor.RED + "The portal's map world is not loaded."
            );
            return;
        }

        if (ManaKit.isInShadowWorld(p)) {
            ManaKit.setShadowWorldTimer(p, PORTAL_DURATION_TICKS);
        } else if (!ManaKit.enterShadowWorld(p, PORTAL_DURATION_TICKS)) {
            p.sendMessage(ChatColor.RED + "Could not enter the shadow world.");
            return;
        }

        item.setAmount(item.getAmount() - 1);
        activePortals.put(
            p.getUniqueId(),
            new ActivePortal(center, PORTAL_DURATION_TICKS)
        );
        playAbilityFeedback(p);
    }

    private Location getNormalWorldLocation(Player p) {
        Location current = p.getLocation();
        if (!ManaKit.isInShadowWorld(p)) return current.clone();

        String shadowWorldName = p.getWorld().getName();
        String normalWorldName = shadowWorldName.substring(
            0,
            shadowWorldName.length() - SHADOW_SUFFIX.length()
        );
        World normalWorld = Bukkit.getWorld(normalWorldName);
        if (normalWorld == null) return null;
        return new Location(
            normalWorld,
            current.getX(),
            current.getY(),
            current.getZ(),
            current.getYaw(),
            current.getPitch()
        );
    }

    private void renderPortal(Location center) {
        World world = center.getWorld();
        DustOptions edgeDust = new DustOptions(Color.fromRGB(0x20FF20), 1.4f);
        DustOptions innerDust = new DustOptions(Color.fromRGB(0xA020F0), 1.2f);

        for (int i = 0; i < 64; i++) {
            double angle = (Math.PI * 2 * i) / 64;
            Location point = center
                .clone()
                .add(
                    Math.cos(angle) * PORTAL_RADIUS,
                    0.05,
                    Math.sin(angle) * PORTAL_RADIUS
                );
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, edgeDust);
        }

        for (double x = -4.5; x <= 4.5; x += 0.75) {
            for (double z = -4.5; z <= 4.5; z += 0.75) {
                if (x * x + z * z >= PORTAL_RADIUS * PORTAL_RADIUS) continue;
                Location point = center.clone().add(x, 0.05, z);
                world.spawnParticle(
                    Particle.DUST,
                    point,
                    1,
                    0,
                    0,
                    0,
                    0,
                    innerDust
                );
            }
        }
    }

    private void pushPlayersIntoShadowWorld(Location center) {
        for (Player target : new ArrayList<>(center.getWorld().getPlayers())) {
            if (!intersectsPortal(target, center)) continue;
            ManaKit.enterShadowWorld(target, PORTAL_DURATION_TICKS);
        }
    }

    private boolean intersectsPortal(Player target, Location center) {
        BoundingBox bounds = target.getBoundingBox();
        if (
            center.getY() < bounds.getMinY() || center.getY() > bounds.getMaxY()
        ) return false;

        double nearestX = Math.max(
            bounds.getMinX(),
            Math.min(center.getX(), bounds.getMaxX())
        );
        double nearestZ = Math.max(
            bounds.getMinZ(),
            Math.min(center.getZ(), bounds.getMaxZ())
        );
        double dx = nearestX - center.getX();
        double dz = nearestZ - center.getZ();
        return dx * dx + dz * dz <= PORTAL_RADIUS * PORTAL_RADIUS;
    }

    private void placeAutoBreakingBlock(
        Player p,
        ItemStack handItem,
        Block clickedBlock,
        BlockFace face
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
                    entity ->
                        entity instanceof LivingEntity &&
                        !(entity instanceof ArmorStand)
                )
                .isEmpty()
        ) return;

        targetBlock.setType(Material.EMERALD_BLOCK);
        if (p.getGameMode() != GameMode.CREATIVE) {
            handItem.setAmount(handItem.getAmount() - 1);
        }

        Location blockLoc = targetBlock.getLocation();
        world.playSound(
            blockLoc.clone().add(0.5, 0.5, 0.5),
            Sound.BLOCK_STONE_PLACE,
            1.0f,
            1.0f
        );

        Set<Block> playerBlocks = placedBlocks.computeIfAbsent(
            p.getUniqueId(),
            ignored -> new HashSet<>()
        );
        playerBlocks.add(targetBlock);

        int sourceId = ThreadLocalRandom.current().nextInt(
            1_000_000,
            2_000_000
        );
        new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                if (targetBlock.getType() != Material.EMERALD_BLOCK) {
                    clearCrackAnimation(world, blockLoc, sourceId);
                    playerBlocks.remove(targetBlock);
                    cancel();
                    return;
                }

                if (elapsed >= EMERALD_BLOCK_BREAK_TICKS) {
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
                        Sound.BLOCK_STONE_BREAK,
                        0.9f,
                        1.0f
                    );
                    targetBlock.setType(Material.AIR);
                    playerBlocks.remove(targetBlock);
                    cancel();
                    return;
                }

                float progress = Math.min(
                    0.99f,
                    (float) elapsed / EMERALD_BLOCK_BREAK_TICKS
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
        Set<Block> blocks = placedBlocks.remove(p.getUniqueId());
        if (blocks == null) return;
        for (Block block : blocks) {
            if (block.getType() == Material.EMERALD_BLOCK) {
                block.setType(Material.AIR);
            }
        }
    }

    private void clearKitItems(Player p) {
        PlayerInventory inventory = p.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (
                isPortalItem(contents[slot]) ||
                isEmeraldBlockItem(contents[slot])
            ) inventory.setItem(slot, null);
        }

        ItemStack offhand = inventory.getItemInOffHand();
        if (isPortalItem(offhand) || isEmeraldBlockItem(offhand)) {
            inventory.setItemInOffHand(null);
        }

        ItemStack cursor = p.getItemOnCursor();
        if (isPortalItem(cursor) || isEmeraldBlockItem(cursor)) {
            p.setItemOnCursor(null);
        }
    }

    private static final class ActivePortal {

        private final Location center;
        private int ticksRemaining;

        private ActivePortal(Location center, int ticksRemaining) {
            this.center = center.clone();
            this.ticksRemaining = ticksRemaining;
        }
    }
}
