package com.defspacemine.snapshotpvp.lobby;

import org.bukkit.World;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

import net.kyori.adventure.text.Component;

public class ManaKitLobbyType extends LobbyType {
    public String getPrefix() {
        return "manakit";
    }

    public LobbyInstance make(World world) {
        SnapshotPvpPlugin.server.sendMessage(Component.text("Mana Kit Lobby!!!")); 
        return new ManaKitLobbyInstance();
    }

    public class ManaKitLobbyInstance extends LobbyType.LobbyInstance {
    }
}
