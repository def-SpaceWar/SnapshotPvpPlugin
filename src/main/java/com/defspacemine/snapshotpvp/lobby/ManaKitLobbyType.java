package com.defspacemine.snapshotpvp.lobby;

import java.util.List;

import org.bukkit.World;
import org.bukkit.entity.Player;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

import net.kyori.adventure.text.Component;

public class ManaKitLobbyType extends LobbyType {
    public String getPrefix() {
        return "manakit";
    }

    public LobbyInstance make(World world) {
        SnapshotPvpPlugin.server.sendMessage(Component.text("Mana Kit Lobby! :D " + world.getName()));
        return new ManaKitLobbyInstance(world);
    }

    public class ManaKitLobbyInstance extends LobbyType.LobbyInstance {
        // TODO: register world in ManaKitListener
        // (after most lobby stuff is done this will happen)
        private World world;

        public ManaKitLobbyInstance(World world) {
            this.world = world;
        }

        public World getWorld() {
            return world;
        }

        public List<Player> getPlayers() {
            return world.getPlayers();
        }

        public void onDestroy() {
            SnapshotPvpPlugin.server.sendMessage(Component.text("No more lobby. ;( " + world.getName()));
        }
    }
}
