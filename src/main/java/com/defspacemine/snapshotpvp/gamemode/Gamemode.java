package com.defspacemine.snapshotpvp.gamemode;

import com.defspacemine.snapshotpvp.lobby.ManaKitLobbyType.ManaKitLobbyInstance;
import org.bukkit.World;
import org.bukkit.entity.Player;

public abstract class Gamemode {

    protected ManaKitLobbyInstance lobby;
    protected World world;
    protected World shadowWorld;

    public final void setShadowWorld(World shadowWorld) {
        this.shadowWorld = shadowWorld;
    }

    public abstract void configure();

    public abstract void onStart();

    public abstract void onTick();

    public abstract boolean isOver();

    public abstract void sendGameOverMessage(Player p);

    public abstract void onDestroy();
}
