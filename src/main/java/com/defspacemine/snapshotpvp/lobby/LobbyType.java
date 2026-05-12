package com.defspacemine.snapshotpvp.lobby;

import java.util.List;

import org.bukkit.World;
import org.bukkit.entity.Player;

public abstract class LobbyType {
    public abstract String getPrefix();
    public abstract LobbyInstance make(World world);

    public abstract class LobbyInstance {
        public abstract World getWorld();
        public abstract List<Player> getPlayers();
        public abstract void onDestroy();
    }
}
