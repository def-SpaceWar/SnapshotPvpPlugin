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
    private Map<String, LobbyType.LobbyInstance> activeLobbies;

    public LobbyManager(JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;
        registeredLobbies = new ArrayList<>();
        activeLobbies = new HashMap<>();

        registeredLobbies.add(new ManaKitLobbyType());

        new BukkitRunnable() {
            @Override
            public void run() {
                for (World w : Bukkit.getWorlds()) {
                    String wName = w.getName();
                    loop: for (LobbyType lobbyType : registeredLobbies) {
                        if (!wName.startsWith(lobbyType.getPrefix()))
                            return;
                        for (String active : activeLobbies.keySet())
                            if (active.equals(wName))
                                continue loop;
                        activeLobbies.put(wName, lobbyType.make(w));
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }
}
