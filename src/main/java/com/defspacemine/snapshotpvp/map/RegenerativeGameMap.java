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
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
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
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
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

    private static class FluidDrain implements Comparable<FluidDrain> {

        private final Location location;
        private final long tick;

        public FluidDrain(Location location, long tick) {
            this.location = location;
            this.tick = tick;
        }

        @Override
        public int compareTo(FluidDrain other) {
            return Long.compare(this.tick, other.tick);
        }
    }

    private final Map<Location, BlockState> originalBlockStates =
        new HashMap<>();
    private final PriorityQueue<DelayedRollback> rollbackQueue =
        new PriorityQueue<>();
    // The single live queue entry per location. Any other entry sitting in
    // rollbackQueue is stale (rescheduled or already finished) and is skipped.
    private final Map<Location, DelayedRollback> queuedRollbacks =
        new HashMap<>();
    // Liquid that should not exist (player-placed, or flowed into a spot that
    // was not liquid originally) gets drained after foreignFluidLifetimeTicks.
    private final PriorityQueue<FluidDrain> drainQueue = new PriorityQueue<>();
    private final Set<Location> pendingDrains = new HashSet<>();
    private final Map<UUID, Integer> regenSuffocationTicks = new HashMap<>();

    private long currentTick = 0;

    protected int blocksPerTick = 25;
    protected long minDelayTicks = 100;
    protected long maxDelayTicks = 200;

    /** How long liquid that isn't part of the map is allowed to exist. */
    protected long foreignFluidLifetimeTicks = 60;

    @Override
    public void onCreate() {
        Plugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onTick() {
        currentTick++;

        processRegenSuffocation();
        processFluidDrains();

        int restoredThisTick = 0;
        int maxAttempts = rollbackQueue.size();

        while (maxAttempts > 0) {
            DelayedRollback next = rollbackQueue.peek();
            if (next == null || currentTick < next.targetTick) {
                break;
            }
            maxAttempts--;

            // Superseded by a newer entry for this location (rescheduled or finished)
            if (queuedRollbacks.get(next.location) != next) {
                rollbackQueue.poll();
                continue;
            }

            BlockState originalState = originalBlockStates.get(next.location);
            if (originalState == null) {
                rollbackQueue.poll();
                queuedRollbacks.remove(next.location);
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
                enqueue(
                    next.location,
                    currentTick + 2,
                    next.sortOrder,
                    next.deferCount + 1
                );
                continue;
            }

            if (!isFluidRelated && restoredThisTick >= blocksPerTick) {
                rollbackQueue.poll();
                enqueue(
                    next.location,
                    currentTick + 1,
                    next.sortOrder,
                    next.deferCount
                );
                continue;
            }

            rollbackQueue.poll();
            finishRollback(next.location);

            restoreBlockState(next.location, originalState);

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

            Location headBlockLoc = player
                .getEyeLocation()
                .getBlock()
                .getLocation();

            boolean inRegenZone = originalBlockStates.containsKey(headBlockLoc);
            boolean inSolidBlock = headBlockLoc
                .getBlock()
                .getType()
                .isOccluding();

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

        Set<Location> restored = new HashSet<>();

        for (Map.Entry<
            Location,
            BlockState
        > entry : originalBlockStates.entrySet()) {
            Location loc = entry.getKey();
            BlockState state = entry.getValue();
            if (
                state.getType().isSolid() &&
                !isUnsafeToUpdate(state, loc.getBlock())
            ) {
                restoreBlockState(loc, state);
                restored.add(loc);
            }
        }

        for (Map.Entry<
            Location,
            BlockState
        > entry : originalBlockStates.entrySet()) {
            Location loc = entry.getKey();
            if (!restored.contains(loc)) {
                restoreBlockState(loc, entry.getValue());
            }
        }

        rollbackQueue.clear();
        queuedRollbacks.clear();
        drainQueue.clear();
        pendingDrains.clear();
        originalBlockStates.clear();
        regenSuffocationTicks.clear();

        world.getEntities().forEach(e -> {
            e.remove();
        });
    }

    private void restoreBlockState(Location loc, BlockState originalState) {
        Block currentWorldBlock = loc.getBlock();
        BlockData data = originalState.getBlockData();
        boolean dataModified = false;

        if (data instanceof Powerable) {
            Powerable powerable = (Powerable) data;
            if (powerable.isPowered()) {
                powerable.setPowered(false);
                dataModified = true;
            }
        }

        if (data instanceof AnaloguePowerable) {
            AnaloguePowerable analogue = (AnaloguePowerable) data;
            if (analogue.getPower() > 0) {
                analogue.setPower(0);
                dataModified = true;
            }
        }

        if (dataModified) {
            originalState.setBlockData(data);
        }

        boolean applyPhysics = !isUnsafeToUpdate(
            originalState,
            currentWorldBlock
        );
        originalState.update(true, applyPhysics);
    }

    private boolean isFluid(Material type) {
        return type == Material.WATER || type == Material.LAVA;
    }

    /** Buckets that put water or lava into the world (fish buckets place water). */
    private boolean placesLiquid(Material bucket) {
        return (
            bucket.name().endsWith("_BUCKET") &&
            bucket != Material.POWDER_SNOW_BUCKET &&
            bucket != Material.MILK_BUCKET
        );
    }

    private void enqueue(
        Location loc,
        long targetTick,
        int sortOrder,
        int deferCount
    ) {
        DelayedRollback entry = new DelayedRollback(
            loc,
            targetTick,
            sortOrder,
            deferCount
        );
        queuedRollbacks.put(loc, entry);
        rollbackQueue.add(entry);
    }

    /** Forget a location without restoring it. Its old queue entry becomes stale. */
    private void finishRollback(Location loc) {
        originalBlockStates.remove(loc);
        queuedRollbacks.remove(loc);
    }

    /**
     * If this spot was liquid originally and something (a block a player placed
     * into it) is now being removed, bring the liquid back on the next tick
     * instead of waiting for the block's normal regen timer.
     */
    private void restoreLiquidSoon(Location loc) {
        BlockState original = originalBlockStates.get(loc);
        DelayedRollback queued = queuedRollbacks.get(loc);
        if (
            original == null || queued == null || !isFluid(original.getType())
        ) return;

        long soon = currentTick + 1;
        if (queued.targetTick > soon) {
            enqueue(loc, soon, queued.sortOrder, queued.deferCount);
        }
    }

    private void scheduleFluidDrain(Block block) {
        Location loc = block.getLocation();
        if (pendingDrains.add(loc)) {
            drainQueue.add(
                new FluidDrain(loc, currentTick + foreignFluidLifetimeTicks)
            );
        }
    }

    private void processFluidDrains() {
        while (!drainQueue.isEmpty() && drainQueue.peek().tick <= currentTick) {
            FluidDrain drain = drainQueue.poll();
            pendingDrains.remove(drain.location);
            drainForeignFluid(drain.location);
        }
    }

    /**
     * Removes liquid from a spot that was not liquid originally. Map liquid
     * (original is liquid) and liquid we know nothing about are left alone.
     */
    private void drainForeignFluid(Location loc) {
        BlockState original = originalBlockStates.get(loc);
        if (original == null || isFluid(original.getType())) return;

        Block block = loc.getBlock();

        if (isFluid(block.getType())) {
            block.setType(Material.AIR, false);
            // Air is what was here originally, so there is nothing left to roll back.
            // Anything else (a crater, a washed-away torch) stays queued as before.
            if (original.getType().isAir()) finishRollback(loc);
            return;
        }

        // Liquid that waterlogged a block which wasn't waterlogged originally
        if (
            block.getBlockData() instanceof Waterlogged now &&
            now.isWaterlogged() &&
            original.getType() == block.getType() &&
            original.getBlockData() instanceof Waterlogged before &&
            !before.isWaterlogged()
        ) {
            now.setWaterlogged(false);
            block.setBlockData(now, false);
        }
    }

    /**
     * Vanilla turns the block into air *before* the falling block entity spawns,
     * so at spawn time the cell already reads as air. The entity still carries
     * the block it came from, so use that as the original. Without this, any
     * gravity block whose cell was not already recorded (for example one that
     * regen restored and which later fell again) got "air" recorded as its
     * original and was never restored.
     */
    private void recordFallingBlockOrigin(FallingBlock falling) {
        Location at = falling.getLocation();
        Block block = at.getBlock();
        BlockState state = block.getState();
        Material now = state.getType();

        boolean cleared = now.isAir() || isFluid(now);
        boolean centered =
            Math.abs(at.getX() - (block.getX() + 0.5)) < 0.01 &&
            Math.abs(at.getZ() - (block.getZ() + 0.5)) < 0.01;

        if (cleared && centered) {
            state.setBlockData(falling.getBlockData());
        }
        recordBlockAndDependents(block, state);
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
        long baseDelay,
        boolean blockReplacesLiquid
    ) {
        int y = block.getLocation().getBlockY();
        Material type = state.getType();

        if (isFluid(type)) {
            // A block placed into map liquid lives like any other placed block and
            // the liquid comes back when it goes. Anything else that disturbs map
            // liquid (buckets, sponges, flow, decay) is undone right away.
            return blockReplacesLiquid
                ? currentTick + baseDelay + y * 2L
                : currentTick + 1;
        }

        if (isFluid(block.getType())) {
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
        recordBlockAndDependents(initialBlock, initialOriginalState, Map.of());
    }

    /**
     * @param knownOriginals pre-change states for additional blocks that are
     *     part of the same change (e.g. the other half of a placed door).
     *     These are used instead of the block's current state.
     */
    private void recordBlockAndDependents(
        Block initialBlock,
        BlockState initialOriginalState,
        Map<Block, BlockState> knownOriginals
    ) {
        recordBlockAndDependents(
            initialBlock,
            initialOriginalState,
            knownOriginals,
            false
        );
    }

    /**
     * @param blockReplacesLiquid true when a solid block is being put into a spot
     *     that holds liquid (placement, a falling block landing). The liquid is
     *     then restored when that block goes away rather than on the next tick.
     */
    private void recordBlockAndDependents(
        Block initialBlock,
        BlockState initialOriginalState,
        Map<Block, BlockState> knownOriginals,
        boolean blockReplacesLiquid
    ) {
        if (
            this.world == null || !initialBlock.getWorld().equals(this.world)
        ) return;

        Set<Block> visited = new HashSet<>();
        Queue<Block> toScan = new LinkedList<>();

        toScan.add(initialBlock);
        visited.add(initialBlock);
        for (Block extra : knownOriginals.keySet()) {
            if (visited.add(extra)) toScan.add(extra);
        }

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
                BlockState stateToSave;
                if (current.equals(initialBlock)) {
                    stateToSave = initialOriginalState;
                } else {
                    BlockState known = knownOriginals.get(current);
                    stateToSave = known != null ? known : current.getState();
                }
                originalBlockStates.put(loc, stateToSave);

                Material type = stateToSave.getType();
                int y = loc.getBlockY();

                boolean isDestructive =
                    type.isAir() ||
                    (isFluid(current.getType()) && !isFluid(type));
                long targetTick = calculateTargetTick(
                    current,
                    stateToSave,
                    baseDelay,
                    blockReplacesLiquid
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

                enqueue(loc, targetTick, sortOrder, 0);
            }

            for (Block neighbor : findConnectedDependents(current)) {
                if (visited.add(neighbor)) {
                    toScan.add(neighbor);
                }
            }
        }
    }

    /** The second half of a door, bed or tall plant, or null if this isn't one. */
    private Block getOtherHalf(Block block) {
        BlockData data = block.getBlockData();
        Block other = null;

        if (data instanceof Bed bed) {
            other = block.getRelative(
                bed.getPart() == Bed.Part.FOOT
                    ? bed.getFacing()
                    : bed.getFacing().getOppositeFace()
            );
        } else if (data instanceof Bisected bisected) {
            String name = block.getType().name();
            if (name.contains("TRAPDOOR") || name.contains("STAIRS")) {
                return null;
            }
            other = block.getRelative(
                bisected.getHalf() == Bisected.Half.BOTTOM
                    ? BlockFace.UP
                    : BlockFace.DOWN
            );
        }

        return other != null && other.getType() == block.getType()
            ? other
            : null;
    }

    /** A snapshot of this position as air, used as the "before" state of a placed second half. */
    private BlockState asAir(Block block) {
        BlockState state = block.getState();
        state.setBlockData(Material.AIR.createBlockData());
        return state;
    }

    private Set<Block> findConnectedDependents(Block block) {
        Set<Block> neighbors = new HashSet<>();

        Block otherHalf = getOtherHalf(block);
        if (otherHalf != null) neighbors.add(otherHalf);

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
        restoreLiquidSoon(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;
        Block placed = event.getBlock();
        Map<Block, BlockState> originals = new HashMap<>();

        // Real pre-placement snapshots when the server reports a multi-block placement
        if (event instanceof BlockMultiPlaceEvent multi) {
            for (BlockState replaced : multi.getReplacedBlockStates()) {
                originals.put(replaced.getBlock(), replaced);
            }
        }

        // Fallback: the other half (door top, bed head, ...) was empty before placement
        Block other = getOtherHalf(placed);
        if (other != null) originals.putIfAbsent(other, asAir(other));

        recordBlockAndDependents(
            placed,
            event.getBlockReplacedState(),
            originals,
            true
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (
            this.world == null ||
            !event.getLocation().getWorld().equals(this.world)
        ) return;
        for (Block block : event.blockList()) {
            recordBlockAndDependents(block, block.getState());
            restoreLiquidSoon(block.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;
        for (Block block : event.blockList()) {
            recordBlockAndDependents(block, block.getState());
            restoreLiquidSoon(block.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;
        recordBlockAndDependents(event.getBlock(), event.getBlock().getState());
        restoreLiquidSoon(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        if (
            this.world == null ||
            !event.getBlock().getWorld().equals(this.world)
        ) return;
        Block to = event.getToBlock();
        recordBlockAndDependents(to, to.getState());
        // Liquid is about to occupy this spot; make sure it doesn't stay long
        if (isFluid(event.getBlock().getType())) scheduleFluidDrain(to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (
            this.world == null ||
            !event.getLocation().getWorld().equals(this.world)
        ) return;
        if (event.getEntity() instanceof FallingBlock fallingBlock) {
            recordFallingBlockOrigin(fallingBlock);
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
                event.getBlock().getState(),
                Map.of(),
                true
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
        if (placesLiquid(event.getBucket())) scheduleFluidDrain(
            event.getBlock()
        );
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

        // Ice melting into water
        if (isFluid(event.getNewState().getType())) scheduleFluidDrain(
            event.getBlock()
        );
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block dispenser = event.getBlock();
        if (
            this.world == null || !dispenser.getWorld().equals(this.world)
        ) return;
        if (!placesLiquid(event.getItem().getType())) return;
        if (!(dispenser.getBlockData() instanceof Directional directional)) {
            return;
        }

        Block target = dispenser.getRelative(directional.getFacing());
        if (target.getType().isSolid()) return; // can't be emptied here

        recordBlockAndDependents(target, target.getState());
        scheduleFluidDrain(target);
    }

    /**
     * Liquid that disappears or decays without any block event. If nothing
     * records it, the spot reads as air by the time something else does, and
     * the "original" saved there is wrong, so the liquid never comes back.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFluidLevelChange(FluidLevelChangeEvent event) {
        Block block = event.getBlock();
        if (this.world == null || !block.getWorld().equals(this.world)) return;
        if (!isFluid(block.getType())) return;
        if (isFluid(event.getNewData().getMaterial())) return; // just a level change

        Location loc = block.getLocation();
        BlockState original = originalBlockStates.get(loc);
        if (original != null) {
            // Liquid that wasn't originally here is going away by itself;
            // if air was underneath, nothing is left to roll back.
            if (original.getType().isAir()) finishRollback(loc);
            return;
        }
        recordBlockAndDependents(block, block.getState());
    }

    protected int fluidFlowMinY = Integer.MIN_VALUE;

    public void setFluidFlowMinY(int y) {
        fluidFlowMinY = y;
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
