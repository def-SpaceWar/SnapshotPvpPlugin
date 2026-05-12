package com.defspacemine.snapshotpvp.lobby;

import org.bukkit.World;

public abstract class LobbyType {
    public abstract String getPrefix();
    public abstract LobbyInstance make(World world);

    public abstract class LobbyInstance {
    }
}
