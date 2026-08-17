package com.defspacemine.snapshotpvp.map;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.PointedDripstone;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public abstract class RegenerativeGameMap extends GameMap implements Listener {

    private static final BlockFace[] CARDINAL_PLUS_UP_DOWN = {
        BlockFace.UP,
        BlockFace.DOWN,
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST,
    };

    private static class DelayedRollback
        implements Comparable<DelayedRollback>
    {

        private final Location location;
        private final long targetTick;
        private final int sortOrder;
        private final int deferCount;

        public DelayedRollback(
            Location location,
            long targetTick,
            int sortOrder,
            int deferCount
        ) {
            this.location = location;
            this.targetTick = targetTick;
            this.sortOrder = sortOrder;
            this.deferCount = deferCount;
        }

        @Override
        public int compareTo(DelayedRollback other) {
            int timeComp = Long.compare(this.targetTick, other.targetTick);
            if (timeComp != 0) return timeComp;
            return Integer.compare(this.sortOrder, other.sortOrder);
        }
    }

    private final Map<Location, BlockState> originalBlockStates =
        new HashMap<>();
    private final PriorityQueue<DelayedRollback> rollbackQueue =
        new PriorityQueue<>();
    private final Map<UUID, Integer> regenSuffocationTicks = new HashMap<>();

    private long currentTick = 0;

    protected int blocksPerTick = 25;
    protected long minDelayTicks = 100;
    protected long maxDelayTicks = 200;

    @Override
    public void onCreate() {
        Plugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onTick() {
        currentTick++;

        processRegenSuffocation();

        int restoredThisTick = 0;
        int maxAttempts = rollbackQueue.size();

        while (maxAttempts > 0) {
            DelayedRollback next = rollbackQueue.peek();
            if (next == null || currentTick < next.targetTick) {
                break;
            }
            maxAttempts--;

            BlockState originalState = originalBlockStates.get(next.location);
            if (originalState == null) {
                rollbackQueue.poll();
                continue;
            }

            Block currentWorldBlock = next.location.getBlock();
            boolean isFluidRelated =
                isFluid(currentWorldBlock.getType()) ||
                isFluid(originalState.getType());

            Set<Location> anchors = getRequiredAnchorLocations(
                next.location,
                originalState,
                currentWorldBlock
            );
            boolean isReady = true;
            boolean hasAtLeastOneSolidAnchor = anchors.isEmpty();

            for (Location anchorLoc : anchors) {
                boolean anchorIsPending = originalBlockStates.containsKey(
                    anchorLoc
                );
                boolean anchorIsSolidInWorld = isSuitableSupport(
                    anchorLoc.getBlock()
                );
                if (anchorIsPending) isReady = false;
                if (anchorIsSolidInWorld) hasAtLeastOneSolidAnchor = true;
            }

            boolean anchorSatisfied =
                anchors.isEmpty() || hasAtLeastOneSolidAnchor;
            boolean blockedOnDependency = !isReady || !anchorSatisfied;

            if (blockedOnDependency && next.deferCount < 150) {
                rollbackQueue.poll();
                rollbackQueue.add(
                    new DelayedRollback(
                        next.location,
                        currentTick + 2,
                        next.sortOrder,
                        next.deferCount + 1
                    )
                );
                continue;
            }

            if (!isFluidRelated && restoredThisTick >= blocksPerTick) {
                rollbackQueue.poll();
                rollbackQueue.add(
                    new DelayedRollback(
                        next.location,
                        currentTick + 1,
                        next.sortOrder,
                        next.deferCount
                    )
                );
                continue;
            }

            rollbackQueue.poll();
            originalBlockStates.remove(next.location);

            boolean applyPhysics = !isUnsafeToUpdate(
                originalState,
                currentWorldBlock
            );
            originalState.update(true, applyPhysics);

            if (!isFluidRelated) {
                restoredThisTick++;
            }
        }
    }

    private boolean isSuitableSupport(Block supportBlock) {
        if (supportBlock == null) return false;
        Material type = supportBlock.getType();
        return !type.isAir() && !isFluid(type);
    }

    private void processRegenSuffocation() {
        if (this.world == null) return;

        for (Player player : this.world.getPlayers()) {
            if (
                player.getGameMode() == GameMode.SPECTATOR ||
                player.getGameMode() == GameMode.CREATIVE
            ) {
                regenSuffocationTicks.remove(player.getUniqueId());
                continue;
            }

            Location feetBlockLoc = player
                .getLocation()
                .getBlock()
                .getLocation();
            Location headBlockLoc = player
                .getEyeLocation()
                .getBlock()
                .getLocation();

            boolean inRegenZone =
                originalBlockStates.containsKey(feetBlockLoc) ||
                originalBlockStates.containsKey(headBlockLoc);
            boolean inSolidBlock = headBlockLoc.getBlock().getType().isSolid();

            if (inRegenZone || inSolidBlock) {
                int ticks =
                    regenSuffocationTicks.getOrDefault(
                        player.getUniqueId(),
                        0
                    ) + 1;
                regenSuffocationTicks.put(player.getUniqueId(), ticks);

                if (ticks > 200 && ticks % 20 == 0) {
                    double damage = Math.pow(1.25, (ticks - 200.) / 20. - 1);
                    player.setNoDamageTicks(0);
                    player.damage(
                        damage,
                        DamageSource.builder(DamageType.IN_WALL).build()
                    );
                    player.addPotionEffect(
                        new PotionEffect(
                            PotionEffectType.GLOWING,
                            200,
                            0,
                            true,
                            false,
                            false
                        )
                    );
                }
            } else {
                regenSuffocationTicks.remove(player.getUniqueId());
            }
        }
    }

    @Override
    public void onDestroy() {
        HandlerList.unregisterAll(this);
        rollbackQueue.clear();
        originalBlockStates.clear();
        regenSuffocationTicks.clear();
    }

    private boolean isFluid(Material type) {
        return type == Material.WATER || type == Material.LAVA;
    }

    private boolean isUnsafeToUpdate(
        BlockState originalState,
        Block currentWorldBlock
    ) {
        Material originalType = originalState.getType();
        Material currentType = currentWorldBlock.getType();

        if (isFluid(currentType) || isFluid(originalType)) return true;

        String name = originalType.name();
        return (
            originalType.hasGravity() ||
            name.contains("CHORUS") ||
            name.contains("VINE") ||
            name.contains("CARPET") ||
            name.contains("TORCH") ||
            name.contains("PLANT") ||
            name.contains("FLOWER") ||
            name.contains("MUSHROOM") ||
            name.contains("DOOR") ||
            name.contains("RAIL") ||
            name.contains("REDSTONE") ||
            name.contains("BUTTON") ||
            name.contains("PRESSURE_PLATE") ||
            name.contains("DRIPLEAF") ||
            name.contains("LEVER") ||
            name.contains("COMPARATOR") ||
            name.contains("REPEATER") ||
            name.contains("TRIPWIRE") ||
            name.contains("SUGAR_CANE") ||
            name.contains("CACTUS") ||
            name.contains("BAMBOO") ||
            name.contains("SAPLING") ||
            name.contains("AMETHYST") ||
            name.contains("HANGING_ROOTS")
        );
    }

    private Set<Location> getRequiredAnchorLocations(
        Location loc,
        BlockState state,
        Block currentWorldBlock
    ) {
        Set<Location> anchors = new HashSet<>();
        Material type = state.getType();
        BlockData data = state.getBlockData();

        if (data instanceof FaceAttachable) {
            FaceAttachable attachable = (FaceAttachable) data;
            switch (attachable.getAttachedFace()) {
                case CEILING:
                    anchors.add(loc.clone().add(0, 1, 0));
                    break;
                case FLOOR:
                    anchors.add(loc.clone().add(0, -1, 0));
                    break;
                case WALL:
                    if (data instanceof Directional) anchors.add(
                        loc
                            .clone()
                            .add(
                                ((Directional) data)
                                    .getFacing()
                                    .getOppositeFace()
                                    .getDirection()
                            )
                    );
                    break;
            }
            return anchors;
        }

        if (
            type.name().endsWith("_WALL_SIGN") ||
            type.name().contains("WALL_TORCH") ||
            type.name().contains("WALL_FAN") ||
            type.name().endsWith("_WALL_HEAD") ||
            type.name().endsWith("_WALL_BANNER")
        ) {
            if (data instanceof Directional) {
                anchors.add(
                    loc
                        .clone()
                        .add(
                            ((Directional) data)
                                .getFacing()
                                .getOppositeFace()
                                .getDirection()
                        )
                );
                return anchors;
            }
        }

        if (data instanceof Lantern) {
            if (((Lantern) data).isHanging()) anchors.add(
                loc.clone().add(0, 1, 0)
            );
            else anchors.add(loc.clone().add(0, -1, 0));
            return anchors;
        }

        if (data instanceof PointedDripstone) {
            if (
                ((PointedDripstone) data).getVerticalDirection() ==
                BlockFace.DOWN
            ) anchors.add(loc.clone().add(0, 1, 0));
            else anchors.add(loc.clone().add(0, -1, 0));
            return anchors;
        }

        if (
            data instanceof Bisected &&
            !type.name().contains("TRAPDOOR") &&
            !type.name().contains("STAIRS")
        ) {
            anchors.add(loc.clone().add(0, -1, 0));
            return anchors;
        }

        if (
            type == Material.WEEPING_VINES ||
            type == Material.WEEPING_VINES_PLANT ||
            type == Material.HANGING_ROOTS ||
            type == Material.CAVE_VINES ||
            type == Material.CAVE_VINES_PLANT
        ) {
            anchors.add(loc.clone().add(0, 1, 0));
            return anchors;
        }

        if (isGroundDependent(type) || type.hasGravity()) {
            anchors.add(loc.clone().add(0, -1, 0));
            return anchors;
        }

        return anchors;
    }

    private boolean isGroundDependent(Material type) {
        if (type.isAir()) return false;
        String name = type.name();
        return (
            name.contains("CARPET") ||
            name.contains("DRIPLEAF") ||
            name.contains("DOOR") ||
            name.contains("RAIL") ||
            name.contains("BED") ||
            name.contains("REPEATER") ||
            name.contains("COMPARATOR") ||
            name.contains("PRESSURE_PLATE") ||
            name.endsWith("_BUTTON") ||
            (name.contains("TORCH") && !name.contains("WALL")) ||
            type == Material.REDSTONE_WIRE ||
            type == Material.CACTUS ||
            type == Material.SUGAR_CANE ||
            type == Material.BAMBOO ||
            type == Material.TWISTING_VINES ||
            type == Material.TWISTING_VINES_PLANT ||
            type == Material.KELP ||
            type == Material.KELP_PLANT ||
            type == Material.SEAGRASS ||
            type == Material.TALL_SEAGRASS ||
            name.contains("MUSHROOM") ||
            name.contains("SAPLING")
        );
    }

    private long calculateTargetTick(
        Block block,
        BlockState state,
        long baseDelay
    ) {
        int y = block.getLocation().getBlockY();
        Material type = state.getType();

        if (isFluid(type) || isFluid(block.getType())) {
            return currentTick + 1;
        }

        if (type.isAir()) {
            return currentTick + baseDelay + (319 - y) * 2L;
        }

        if (
            type == Material.WEEPING_VINES ||
            type == Material.WEEPING_VINES_PLANT ||
            type == Material.HANGING_ROOTS ||
            type == Material.CAVE_VINES ||
            type == Material.CAVE_VINES_PLANT
        ) {
            return currentTick + baseDelay + (319 - y) * 2L;
        }

        if (state.getBlockData() instanceof PointedDripstone) {
            PointedDripstone dripstone =
                (PointedDripstone) state.getBlockData();
            if (dripstone.getVerticalDirection() == BlockFace.DOWN) {
                return currentTick + baseDelay + (319 - y) * 2L;
            }
        }

        return currentTick + baseDelay + y * 2L;
    }

    private void recordBlockAndDependents(
        Block initialBlock,
        BlockState initialOriginalState
    ) {
        if (
            this.world == null || !initialBlock.getWorld().equals(this.world)
        ) return;

        Set<Block> visited = new HashSet<>();
        Queue<Block> toScan = new LinkedList<>();

        toScan.add(initialBlock);
        visited.add(initialBlock);

        long baseDelay =
            minDelayTicks >= maxDelayTicks
                ? minDelayTicks
                : ThreadLocalRandom.current().nextLong(
                      minDelayTicks,
                      maxDelayTicks + 1
                  );

        while (!toScan.isEmpty()) {
            Block current = toScan.poll();
            Location loc = current.getLocation();

            if (!originalBlockStates.containsKey(loc)) {
                BlockState stateToSave = current.equals(initialBlock)
                    ? initialOriginalState
                    : current.getState();
                originalBlockStates.put(loc, stateToSave);

                Material type = stateToSave.getType();
                int y = loc.getBlockY();

                boolean isDestructive =
                    type.isAir() ||
                    (isFluid(current.getType()) && !isFluid(type));
                long targetTick = calculateTargetTick(
                    current,
                    stateToSave,
                    baseDelay
                );
                if (targetTick == -1) continue;
                int sortOrder;

                if (isDestructive) {
                    sortOrder = -y;
                } else {
                    if (
                        type == Material.WEEPING_VINES ||
                        type == Material.WEEPING_VINES_PLANT ||
                        type == Material.HANGING_ROOTS ||
                        type == Material.CAVE_VINES ||
                        type == Material.CAVE_VINES_PLANT ||
                        (stateToSave.getBlockData() instanceof
                            PointedDripstone &&
                            (
                                (PointedDripstone) stateToSave.getBlockData()
                            ).getVerticalDirection() == BlockFace.DOWN)
                    ) {
                        sortOrder = -y;
                    } else {
                        sortOrder = y;
                    }
                }

                rollbackQueue.add(
                    new DelayedRollback(loc, targetTick, sortOrder, 0)
                );
            }

            for (Block neighbor : findConnectedDependents(current)) {
                if (visited.add(neighbor)) {
                    toScan.add(neighbor);
                }
            }
        }
    }

    private Set<Block> findConnectedDependents(Block block) {
        Set<Block> neighbors = new HashSet<>();
        for (BlockFace face : CARDINAL_PLUS_UP_DOWN) {
            Block relative = block.getRelative(face);
            Material type = relative.getType();
            if (type.isAir()) continue;
            String name = type.name();

            if (
                face == BlockFace.UP &&
                (isGroundDependent(type) ||
                    type.hasGravity() ||
                    name.contains("SNOW"))
            ) neighbors.add(relative);
            if (
                face == BlockFace.DOWN &&
                (type == Material.WEEPING_VINES ||
                    type == Material.WEEPING_VINES_PLANT ||
                    type == Material.HANGING_ROOTS)
            ) neighbors.add(relative);
            if (
                face != BlockFace.UP &&
                face != BlockFace.DOWN &&
                (name.contains("WALL_") ||
                    name.contains("LADDER") ||
                    name.contains("VINE") ||
                    name.contains("BUTTON") ||
                    name.contains("LEVER") ||
                    name.contains("TRIPWIRE"))
            ) neighbors.add(relative);
            if (
                type == Material.WEEPING_VINES ||
                type == Material.WEEPING_VINES_PLANT ||
                type == Material.TWISTING_VINES ||
                type == Material.TWISTING_VINES_PLANT ||
                type == Material.CHORUS_PLANT ||
                type == Material.CHORUS_FLOWER ||
                name.contains("DRIPLEAF") ||
                type == Material.POINTED_DRIPSTONE ||
                type == Material.SUGAR_CANE ||
                type == Material.CACTUS ||
                type == Material.BAMBOO
            ) {
                neighbors.add(relative);
            }
        }
        return neighbors;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;
        recordBlockAndDependents(event.getBlock(), event.getBlock().getState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;
        recordBlockAndDependents(
            event.getBlock(),
            event.getBlockReplacedState()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (
            this.world == null ||
            !event.getLocation().getWorld().equals(this.world)
        ) return;
        for (Block block : event.blockList())
            recordBlockAndDependents(block, block.getState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;
        for (Block block : event.blockList())
            recordBlockAndDependents(block, block.getState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;
        recordBlockAndDependents(event.getBlock(), event.getBlock().getState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;
        recordBlockAndDependents(
            event.getToBlock(),
            event.getToBlock().getState()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (
            this.world == null ||
            !event.getLocation().getWorld().equals(this.world)
        ) return;
        if (event.getEntityType() == EntityType.FALLING_BLOCK) {
            FallingBlock fallingBlock = (FallingBlock) event.getEntity();
            Block block = fallingBlock.getLocation().getBlock();
            recordBlockAndDependents(block, block.getState());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;
        if (
            event.getEntityType() == EntityType.FALLING_BLOCK &&
            event.getTo() != Material.AIR
        ) {
            recordBlockAndDependents(
                event.getBlock(),
                event.getBlock().getState()
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;
        recordBlockAndDependents(event.getBlock(), event.getBlock().getState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (
            this.world == null ||
            !event.getBlockClicked().getWorld().equals(this.world)
        ) return;
        recordBlockAndDependents(
            event.getBlockClicked(),
            event.getBlockClicked().getState()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;
        recordBlockAndDependents(event.getBlock(), event.getBlock().getState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;

        Material type = event.getBlock().getType();
        if (type == Material.FIRE || type == Material.SOUL_FIRE) {
            return;
        }

        recordBlockAndDependents(event.getBlock(), event.getBlock().getState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;

        Material type = event.getBlock().getType();
        if (type == Material.FIRE || type == Material.SOUL_FIRE) {
            return;
        }

        recordBlockAndDependents(event.getBlock(), event.getBlock().getState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpongeAbsorb(SpongeAbsorbEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;
        for (BlockState state : event.getBlocks())
            recordBlockAndDependents(state.getBlock(), state);
    }

    protected int fluidFlowMinY = Integer.MIN_VALUE;

    public void setFluidFlowMinY(int y) {
        this.fluidFlowMinY = y;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFluidFlowLimit(BlockFromToEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;

        Material fluidType = event.getBlock().getType();
        if (!isFluid(fluidType)) return;

        if (event.getToBlock().getLocation().getBlockY() <= fluidFlowMinY) {
            event.setCancelled(true);
        }
    }
}
