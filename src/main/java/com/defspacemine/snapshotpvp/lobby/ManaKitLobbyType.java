package com.defspacemine.snapshotpvp.lobby;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.map.BobLands;
import com.defspacemine.snapshotpvp.map.GameMap;
import java.util.List;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class ManaKitLobbyType extends LobbyType {

    public String getPrefix() {
        return "manakit";
    }

    public LobbyInstance make(World world) {
        SnapshotPvpPlugin.server.sendMessage(
            Component.text(
                "Mana Kit Lobby! :D (regenerative)" + world.getName()
            )
        );
        return new ManaKitLobbyInstance(world);
    }

    enum LobbyState {
        WAITING, // everyone needs to ready up
        VOTE_MAP,
        VOTE_MODE,
        VOTE_CONFIG, // Map settings (choose if kit swapping is allowed) and teams (if applicable)
        KIT_SELECTION, // choose kits
        IN_GAME,
    }

    public class ManaKitLobbyInstance extends LobbyType.LobbyInstance {

        // TODO: register world in ManaKitListener
        // (after most lobby stuff is done this will happen)
        // ManaKitListeners should only be running in certain worlds.
        // or manakitlistener has an array of all the worlds to run in?
        private World world;
        private World mapWorld;
        private GameMap gameMap;

        LobbyState lobbyState;

        public ManaKitLobbyInstance(World world) {
            this.world = world;
            mapWorld = Bukkit.getWorld(world.getName() + "map");
            lobbyState = LobbyState.WAITING;

            // debug purposes
            gameMap = new BobLands(this, mapWorld);
            gameMap.onCreate();
        }

        public World getWorld() {
            return world;
        }

        public List<Player> getPlayers() {
            return Stream.concat(
                world.getPlayers().stream(),
                mapWorld.getPlayers().stream()
            ).toList();
        }

        public void onTick() {
            gameMap.onTick();
        }

        public void onDestroy() {
            if (gameMap != null) gameMap.onDestroy();
            SnapshotPvpPlugin.server.sendMessage(
                Component.text("No more lobby. ;( " + world.getName())
            );
        }
    }
}
