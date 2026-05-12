package com.defspacemine.snapshotpvp.lobby;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class LobbyManager {
    public static LobbyManager instance;

    private final JavaPlugin plugin;
    private List<LobbyType> registeredLobbies;
    private List<LobbyType.LobbyInstance> activeLobbies;

    public LobbyManager(JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;
        registeredLobbies = new ArrayList<>();
        activeLobbies = new ArrayList<>();

        registeredLobbies.add(new ManaKitLobbyType());

        new BukkitRunnable() {
            @Override
            public void run() {
                activeLobbies.removeIf((active) -> active.getPlayers().size() == 0);

                for (World w : Bukkit.getWorlds()) {
                    String wName = w.getName();
                    loop: for (LobbyType lobbyType : registeredLobbies) {
                        if (!wName.startsWith(lobbyType.getPrefix()))
                            return;
                        for (LobbyType.LobbyInstance active : activeLobbies)
                            if (active.getWorld().equals(w))
                                continue loop;
                        activeLobbies.add(lobbyType.make(w));
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }
}
