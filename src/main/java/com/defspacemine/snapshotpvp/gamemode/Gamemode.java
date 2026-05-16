package com.defspacemine.snapshotpvp.gamemode;

import org.bukkit.World;

import com.defspacemine.snapshotpvp.lobby.LobbyType.LobbyInstance;

public abstract class Gamemode {
    private LobbyInstance lobby;
    private World world;

    public abstract void configure();
    public abstract void onStart();
    public abstract void onTick();
    public abstract boolean isOver();
    public abstract String getGameOverMessage();
    public abstract void onDestroy();
}
