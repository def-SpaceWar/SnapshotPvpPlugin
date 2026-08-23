package com.defspacemine.snapshotpvp.map;

import com.defspacemine.snapshotpvp.lobby.LobbyType.LobbyInstance;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class BobInvasionRemaster extends RegenerativeGameMap {

    public static final BlockVector3 WORLD_MIN = BlockVector3.at(-47, 27, -86);
    public static final BlockVector3 WORLD_MAX = BlockVector3.at(47, 83, 86);
    public static BlockVector3 worldLoc = WORLD_MIN;

    public static World getSourceWorld() {
        return Bukkit.getWorld("bob_invasion_remaster");
    }

    public BobInvasionRemaster(LobbyInstance lobby, World world) {
        this.lobby = lobby;
        this.world = world;
    }

    @Override
    public void configure() {
        // Setup/get gamemode settings or rules
        throw new UnsupportedOperationException(
            "Unimplemented method 'configure'"
        );
    }

    @Override
    public void onCreate() {
        super.onCreate();

        World source = getSourceWorld();
        if (source != null) GameMapUtil.cloneMap(
            source,
            WORLD_MIN,
            WORLD_MAX,
            world,
            worldLoc
        );

        setFluidFlowMinY(27);
    }

    @Override
    public void onTick() {
        super.onTick();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        GameMapUtil.removeMap(WORLD_MIN, WORLD_MAX, world, worldLoc);
    }
}
