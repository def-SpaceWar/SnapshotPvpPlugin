package com.defspacemine.snapshotpvp.map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.defspacemine.snapshotpvp.gamemode.Gamemode;
import com.defspacemine.snapshotpvp.lobby.LobbyType.LobbyInstance;
import com.sk89q.worldedit.math.BlockVector3;

public abstract class GameMap {
    protected LobbyInstance lobby;
    protected World world;
    protected World shadowWorld;
    protected Gamemode gamemode; // set through `configure`

    public abstract void configure();
    public abstract void onCreate();
    public abstract void onTick();
    public abstract void onDestroy();
    public abstract Location[] getSpawnPoints();

    public final void setShadowWorld(World shadowWorld) {
        this.shadowWorld = shadowWorld;
    }

    protected final void cloneMapToShadow(
        World sourceWorld,
        BlockVector3 min,
        BlockVector3 max,
        BlockVector3 targetLoc
    ) {
        if (shadowWorld != null) GameMapUtil.cloneMap(
            sourceWorld,
            min,
            max,
            shadowWorld,
            targetLoc
        );
    }

    protected final void removeShadowMap(
        BlockVector3 min,
        BlockVector3 max,
        BlockVector3 targetLoc
    ) {
        if (shadowWorld != null) GameMapUtil.removeMap(
            min,
            max,
            shadowWorld,
            targetLoc
        );
    }

    protected final void mirrorBlockToShadow(Block source) {
        if (shadowWorld == null) return;
        var target = shadowWorld.getBlockAt(
            source.getX(),
            source.getY(),
            source.getZ()
        );
        source.getState().copy(target.getLocation()).update(true, false);
    }
}
