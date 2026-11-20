package com.defspacemine.snapshotpvp.lobby;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.gamemode.Deathmatch;
import com.defspacemine.snapshotpvp.gamemode.Gamemode;
import com.defspacemine.snapshotpvp.gamemode.Streakmatch;
import com.defspacemine.snapshotpvp.manakit.ManaKitListener;
import com.defspacemine.snapshotpvp.map.BobInvasionRemaster;
import com.defspacemine.snapshotpvp.map.BobLands;
import com.defspacemine.snapshotpvp.map.GameMap;
import java.util.List;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class ManaKitLobbyType extends LobbyType {

    public static boolean isInManaKitLobby(Player player) {
        return LobbyManager.instance.isPlayerInLobby(
            player,
            ManaKitLobbyInstance.class
        );
    }

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

    public class ManaKitLobbyInstance
        extends LobbyType.LobbyInstance
        implements Listener
    {

        // TODO: register world in ManaKitListener
        // (after most lobby stuff is done this will happen)
        // ManaKitListeners should only be running in certain worlds.
        // or manakitlistener has an array of all the worlds to run in?
        private World world;
        private World mapWorld;
        private World shadowMap;
        private GameMap gameMap;
        private Gamemode gamemode;

        LobbyState lobbyState;

        public ManaKitLobbyInstance(World world) {
            this.world = world;
            mapWorld = Bukkit.getWorld(world.getName() + "_map");
            if (mapWorld == null) {
                throw new IllegalStateException(
                    "Map world is not loaded: " + world.getName() + "_map"
                );
            }
            String shadowMapName = world.getName() + "_map_shadow";
            shadowMap = Bukkit.getWorld(shadowMapName);
            if (shadowMap == null) {
                shadowMap = Bukkit.createWorld(new WorldCreator(shadowMapName));
            }
            if (shadowMap == null) {
                throw new IllegalStateException(
                    "Could not load or create shadow map world: " +
                    shadowMapName
                );
            }
            lobbyState = LobbyState.IN_GAME; // debug purposes, havent setup a proper lobby loop yet

            // debug purposes
            gameMap =
                Math.random() > 0.5
                    ? new BobLands(this, mapWorld)
                    : new BobInvasionRemaster(this, mapWorld);
            gameMap.setShadowWorld(shadowMap);
            gamemode =
                Math.random() > 0.5
                    ? new Deathmatch(this, mapWorld)
                    : new Streakmatch(this, mapWorld);
            gamemode.setShadowWorld(shadowMap);
            // gamemode = new Streakmatch(this, mapWorld);
            gameMap.onCreate();
            gamemode.onStart();

            SnapshotPvpPlugin.pluginManager.registerEvents(
                this,
                SnapshotPvpPlugin.instance
            );
        }

        public World getWorld() {
            return world;
        }

        public List<Player> getPlayers() {
            List<Player> players = Stream.concat(
                world.getPlayers().stream(),
                mapWorld.getPlayers().stream()
            ).toList();
            players = Stream.concat(
                players.stream(),
                shadowMap.getPlayers().stream()
            ).toList();

            if (gamemode == null || !gamemode.isOver()) return players;

            for (Player p : players) gamemode.sendGameOverMessage(p);
            for (Player p : players)
                p.teleport(new Location(world, 0.5, 65, 0.5));

            // returning null in `getPlayers` ends the lobby.
            return null;
        }

        public void onTick() {
            if (lobbyState == LobbyState.IN_GAME) {
                if (gameMap != null) {
                    for (Player p : mapWorld.getPlayers()) {
                        if (!p.getScoreboardTags().contains("fighting")) {
                            p.addScoreboardTag("fighting");
                            Location[] spawnPoints = gameMap.getSpawnPoints();
                            p.teleport(
                                spawnPoints[
                                    (int) Math.floor(
                                        Math.random() * spawnPoints.length
                                    )
                                ]
                            );
                        }
                    }
                    for (Player shadowPlayer : shadowMap.getPlayers()) {
                        if (
                            !shadowPlayer.getScoreboardTags().contains("fighting")
                        ) {
                            shadowPlayer.addScoreboardTag("fighting");
                            Location[] spawnPoints = gameMap.getSpawnPoints();
                            Location spawnPoint =
                                spawnPoints[
                                    (int) Math.floor(
                                        Math.random() * spawnPoints.length
                                    )
                                ];
                            shadowPlayer.teleport(
                                new Location(
                                    shadowMap,
                                    spawnPoint.getX(),
                                    spawnPoint.getY(),
                                    spawnPoint.getZ(),
                                    spawnPoint.getYaw(),
                                    spawnPoint.getPitch()
                                )
                            );
                        }
                    }
                    for (Player p : world.getPlayers())
                        p.removeScoreboardTag("fighting");
                    gameMap.onTick();
                }
                if (gamemode != null) gamemode.onTick();
            }
        }

        public void onDestroy() {
            if (gameMap != null) gameMap.onDestroy();
            if (gamemode != null) gamemode.onDestroy();
            HandlerList.unregisterAll(this);

            SnapshotPvpPlugin.server.sendMessage(
                Component.text("No more lobby. ;( " + world.getName())
            );
        }

        @Override
        public void onLeaveLobby(Player player) {
            super.onLeaveLobby(player);
            ManaKitListener.clearManaKitData(player);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onPlayerDeath(PlayerDeathEvent event) {
            Player p = event.getPlayer();
            if (
                !p.getWorld().equals(mapWorld) &&
                !p.getWorld().equals(shadowMap)
            ) return;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (
                        p.teleport(new Location(world, 0.5, 65, 0.5)) ||
                        !p.isOnline()
                    ) cancel();
                }
            }.runTaskTimer(SnapshotPvpPlugin.instance, 0, 1);
        }
    }
}
