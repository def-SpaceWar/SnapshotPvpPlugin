package com.defspacemine.snapshotpvp.map;

import org.bukkit.World;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;

public class GameMapUtil {
    public static void cloneMap(
            World sourceWorld, BlockVector3 min, BlockVector3 max,
            World destWorld, BlockVector3 targetLoc) {
        CuboidRegion region = new CuboidRegion(
                BukkitAdapter.adapt(sourceWorld),
                min,
                max);

        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);

        try (EditSession sourceSession = WorldEdit.getInstance().getEditSessionFactory()
                .getEditSession(BukkitAdapter.adapt(sourceWorld), -1)) {
            ForwardExtentCopy copy = new ForwardExtentCopy(
                    sourceSession, region, clipboard, region.getMinimumPoint());
            copy.setCopyingBiomes(true);
            Operations.complete(copy);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try (EditSession destSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(destWorld))
                .build()) {
            ClipboardHolder holder = new ClipboardHolder(
                    clipboard);

            var pasteOp = holder.createPaste(destSession)
                    .to(targetLoc)
                    .copyBiomes(true)
                    .ignoreAirBlocks(false)
                    .build();

            Operations.complete(pasteOp);
            holder.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void removeMap(BlockVector3 min, BlockVector3 max, World destWorld, BlockVector3 targetLoc) {
        BlockVector3 destMin = targetLoc;
        BlockVector3 destMax = targetLoc.add(max.subtract(min));

        CuboidRegion destRegion = new CuboidRegion(
                BukkitAdapter.adapt(destWorld),
                destMin,
                destMax);

        try (EditSession destSession = WorldEdit.getInstance().getEditSessionFactory()
                .getEditSession(BukkitAdapter.adapt(destWorld), -1)) {
            destSession.setBlocks((Region) destRegion, BlockTypes.AIR.getDefaultState());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
