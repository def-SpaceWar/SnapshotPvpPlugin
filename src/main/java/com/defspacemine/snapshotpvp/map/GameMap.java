package com.defspacemine.snapshotpvp.map;

import org.bukkit.World;

import com.defspacemine.snapshotpvp.gamemode.Gamemode;
import com.defspacemine.snapshotpvp.lobby.LobbyType.LobbyInstance;

public abstract class GameMap {
    protected LobbyInstance lobby;
    protected World world;
    protected Gamemode gamemode; // set through `configure`

    public abstract void configure();
    public abstract void onCreate();
    public abstract void onTick();
    public abstract void onDestroy();
}
