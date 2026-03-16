package com.defspacemine.snapshotpvp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public class WeatherManager {
    public static WeatherManager instance;

    public enum WeatherType {
        RAIN(1, ChatColor.GRAY + "Rain"),
        STORM(2, ChatColor.DARK_AQUA + "a Storm");

        public final int priority;
        public final String displayName;

        WeatherType(int priority, String displayName) {
            this.priority = priority;
            this.displayName = displayName;
        }
    }

    private final JavaPlugin plugin;
    private final Map<UUID, List<ActiveWeather>> worldEvents = new HashMap<>();
    private final Map<UUID, WeatherType> lastWeather = new HashMap<>();

    public WeatherManager(JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;
        startUpdateTask();
    }

    public void queueWeather(World world, WeatherType type, long durationTicks) {
        worldEvents.computeIfAbsent(world.getUID(), k -> new ArrayList<>())
                .add(new ActiveWeather(type, durationTicks));
    }

    private void startUpdateTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                updateWorldWeather(world);
            }
        }, 0L, 1L);
    }

    private void updateWorldWeather(World world) {
        UUID uuid = world.getUID();
        List<ActiveWeather> events = worldEvents.get(uuid);
        WeatherType activeType = null;
        long activeDuration = 0;

        if (events != null && !events.isEmpty()) {
            events.removeIf(e -> e.remainingTicks <= 0);

            for (ActiveWeather event : events) {
                if (activeType == null || event.type.priority > activeType.priority) {
                    activeType = event.type;
                    activeDuration = event.remainingTicks;
                }
                event.remainingTicks--;
            }
        }

        // --- TRANSITION MESSAGING ---
        WeatherType previous = lastWeather.get(uuid);
        if (activeType != previous) {
            if (activeType != null) {
                String timeStr = formatTime(activeDuration);
                world.sendMessage(Component.text(ChatColor.YELLOW + "The weather is shifting to " +
                        activeType.displayName + ChatColor.YELLOW + " for " + timeStr + "!"));
            } else if (previous != null) {
                world.sendMessage(Component.text(ChatColor.YELLOW + "The skies are clearing up..."));
            }
            lastWeather.put(uuid, activeType);
        }

        // --- APPLY WEATHER ---
        if (activeType == WeatherType.STORM) {
            if (!world.hasStorm() || !world.isThundering()) {
                world.setStorm(true);
                world.setThundering(true);
                world.setWeatherDuration(20);
            }
        } else if (activeType == WeatherType.RAIN) {
            if (!world.hasStorm() || world.isThundering()) {
                world.setStorm(true);
                world.setThundering(false);
                world.setWeatherDuration(20);
            }
        } else {
            if (world.hasStorm()) {
                world.setStorm(false);
                world.setThundering(false);
                world.setWeatherDuration(20);
            }
        }
    }

    private String formatTime(long ticks) {
        long seconds = ticks / 20;
        if (seconds < 60)
            return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    private static class ActiveWeather {
        WeatherType type;
        long remainingTicks;

        ActiveWeather(WeatherType type, long duration) {
            this.type = type;
            this.remainingTicks = duration;
        }
    }
}
