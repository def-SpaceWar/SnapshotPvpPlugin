package com.defspacemine.snapshotpvp.map;

import com.defspacemine.snapshotpvp.lobby.LobbyType.LobbyInstance;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class BobLands extends RegenerativeGameMap {

    public static final BlockVector3 WORLD_MIN = BlockVector3.at(-61, 40, -61);
    public static final BlockVector3 WORLD_MAX = BlockVector3.at(61, 100, 61);
    public static BlockVector3 worldLoc = WORLD_MIN;

    public static World getSourceWorld() {
        return Bukkit.getWorld("bob_lands");
    }

    public BobLands(LobbyInstance lobby, World world) {
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

        setFluidFlowMinY(55);
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

    @Override
    public Location[] getSpawnPoints() {
        return new Location[] {
            new Location(world, -19.5, 65, 41.5),
            new Location(world, -34.5, 63, 20.5),
            new Location(world, -48.5, 65, -6.5),
            new Location(world, -19.5, 63, -44.5),
            new Location(world, 19.5, 64, -50.5),
            new Location(world, 41.5, 63, -16.5),
            new Location(world, 44.5, 63, 22.5),
            new Location(world, 18.5, 77, 39.5),
        };
    }
}
