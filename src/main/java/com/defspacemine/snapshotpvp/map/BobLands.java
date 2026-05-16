package com.defspacemine.snapshotpvp.map;

import org.bukkit.Bukkit;
import org.bukkit.World;

import com.defspacemine.snapshotpvp.lobby.LobbyType.LobbyInstance;
import com.sk89q.worldedit.math.BlockVector3;

public class BobLands extends GameMap {
    public static BlockVector3 worldMin = BlockVector3.at(-61, 40, -61);
    public static BlockVector3 worldMax = BlockVector3.at(61, 100, 61);
    public static BlockVector3 worldLoc = worldMin;

    public static World getSourceWorld() {
        return Bukkit.getWorld("bob_lands");
    }

    public BobLands(LobbyInstance lobby, World world) {
        this.lobby = lobby;
        this.world = world;
    }

    public void configure() {
    }

    public void onCreate() {
        GameMapUtil.cloneMap(getSourceWorld(), worldMin, worldMax, world, worldLoc); 
    }

    public void onTick() {
        // slowly regenerate map and other stuff
    }

    public void onDestroy() {
        GameMapUtil.removeMap(worldMin, worldMax, world, worldLoc); 
    }
}
