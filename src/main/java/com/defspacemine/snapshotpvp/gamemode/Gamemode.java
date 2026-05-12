package com.defspacemine.snapshotpvp.gamemode;

import org.bukkit.World;

import com.defspacemine.snapshotpvp.lobby.Lobby;

public abstract class Gamemode {
    public abstract void configure(Lobby lobby);
    public abstract void update(World world);
}
