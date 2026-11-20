package com.defspacemine.snapshotpvp.lobby;

import com.defspacemine.snapshotpvp.lobby.LobbyType.LobbyInstance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class LobbyManager {

    public static LobbyManager instance;

    private final JavaPlugin plugin;
    public static List<LobbyType> registeredLobbies = new ArrayList<>();
    private List<LobbyInstance> activeLobbies;
    private final Map<LobbyInstance, Map<UUID, Player>> lobbyPlayers =
        new HashMap<>();

    public LobbyManager(JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;
        activeLobbies = new ArrayList<>();

        registeredLobbies.add(new ManaKitLobbyType());

        new BukkitRunnable() {
            @Override
            public void run() {
                activeLobbies.removeIf(active -> {
                    List<Player> players = active.getPlayers();
                    if (players == null) players = List.of();
                    updateLobbyPlayers(active, players);

                    boolean remove = players.isEmpty();
                    if (remove) active.onDestroy();
                    else active.onTick();
                    if (remove) lobbyPlayers.remove(active);
                    return remove;
                });

                for (World world : Bukkit.getWorlds()) {
                    if (world.getPlayerCount() == 0) continue;
                    String wName = world.getName();
                    loop: for (LobbyType lobbyType : registeredLobbies) {
                        if (
                            !wName.startsWith(lobbyType.getPrefix()) ||
                            wName.contains("_")
                        ) continue;
                        for (LobbyInstance active : activeLobbies)
                            if (active.getWorld().equals(world)) continue loop;
                        LobbyInstance active = lobbyType.make(world);
                        activeLobbies.add(active);
                        List<Player> players = active.getPlayers();
                        if (players != null) updateLobbyPlayers(active, players);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    public boolean isPlayerInLobby(
        Player player,
        Class<? extends LobbyInstance> lobbyClass
    ) {
        UUID playerId = player.getUniqueId();
        return lobbyPlayers
            .entrySet()
            .stream()
            .anyMatch(entry ->
                lobbyClass.isInstance(entry.getKey()) &&
                entry.getValue().containsKey(playerId)
            );
    }

    private void updateLobbyPlayers(
        LobbyInstance lobby,
        List<Player> players
    ) {
        Map<UUID, Player> trackedPlayers = lobbyPlayers.computeIfAbsent(
            lobby,
            ignored -> new LinkedHashMap<>()
        );
        Map<UUID, Player> currentPlayers = new LinkedHashMap<>();
        for (Player player : players) {
            currentPlayers.put(player.getUniqueId(), player);
        }

        for (UUID playerId : new ArrayList<>(trackedPlayers.keySet())) {
            if (currentPlayers.containsKey(playerId)) continue;
            Player player = trackedPlayers.remove(playerId);
            lobby.onLeaveLobby(player);
        }

        for (Map.Entry<UUID, Player> entry : currentPlayers.entrySet()) {
            if (trackedPlayers.putIfAbsent(entry.getKey(), entry.getValue()) == null) {
                lobby.onLobbyJoin(entry.getValue());
            }
        }
    }

    public void onDestroy() {
        for (LobbyInstance active : new ArrayList<>(activeLobbies)) {
            updateLobbyPlayers(active, List.of());
            active.onDestroy();
        }
        activeLobbies.clear();
        lobbyPlayers.clear();
    }
}
