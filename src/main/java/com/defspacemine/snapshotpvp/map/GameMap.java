package com.defspacemine.snapshotpvp.map;

import org.bukkit.World;

import com.defspacemine.snapshotpvp.gamemode.Gamemode;
import com.defspacemine.snapshotpvp.lobby.Lobby;

public abstract class GameMap {
    public abstract void configure(Lobby lobby);
    public abstract void update(World world, Gamemode gamemode);
}
