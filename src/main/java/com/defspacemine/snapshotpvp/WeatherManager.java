package com.defspacemine.snapshotpvp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
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
        RAIN(1, ChatColor.BLUE + "Rainy", BarColor.BLUE),
        STORM(2, ChatColor.DARK_AQUA + "Stormy", BarColor.PURPLE);

        public final int priority;
        public final String displayName;
        public final BarColor barColor;

        WeatherType(int priority, String displayName, BarColor barColor) {
            this.priority = priority;
            this.displayName = displayName;
            this.barColor = barColor;
        }
    }

    private final JavaPlugin plugin;
    private final Map<UUID, List<ActiveWeather>> worldEvents = new HashMap<>();
    private final Map<UUID, WeatherType> lastWeather = new HashMap<>();
    private final Map<UUID, Long> lastDuration = new HashMap<>();
    private final Map<UUID, Long> maxDuration = new HashMap<>();
    private final Map<UUID, BossBar> worldBars = new HashMap<>();

    public WeatherManager(JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;
        startUpdateTask();
    }

    public void queueWeather(World world, WeatherType type, long durationTicks) {
        worldEvents.computeIfAbsent(world.getUID(), k -> new ArrayList<>())
                .add(new ActiveWeather(type, durationTicks));
        maxDuration.put(world.getUID(), Math.max(maxDuration.getOrDefault(world.getUID(), 0L), durationTicks));
    }

    public void clearWeather(World world) {
        UUID uuid = world.getUID();
        if (worldEvents.containsKey(uuid))
            worldEvents.get(uuid).clear();
    }

    private void startUpdateTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World world : Bukkit.getWorlds())
                updateWorldWeather(world);
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
                } else if (event.type == activeType)
                    activeDuration = Math.max(activeDuration, event.remainingTicks);
                event.remainingTicks--;
            }
        }

        WeatherType previousType = lastWeather.get(uuid);
        long previousDuration = lastDuration.getOrDefault(uuid, 0L);
        boolean durationIncreased = activeType != null && activeDuration > (previousDuration + 1);

        if (activeType != previousType) {
            if (activeType != null) {
                String timeStr = formatTime(activeDuration);
                world.sendMessage(Component.text(ChatColor.YELLOW + "The weather is shifting to " +
                        activeType.displayName + ChatColor.YELLOW + " for " + timeStr + "!"));
            } else if (previousType != null) {
                world.sendMessage(Component.text(ChatColor.YELLOW + "The skies are clearing up..."));
                maxDuration.put(uuid, 0L);
            }
            lastWeather.put(uuid, activeType);
        } else if (durationIncreased) {
            String timeStr = formatTime(activeDuration);
            world.sendMessage(Component.text(ChatColor.AQUA + "The " + activeType.displayName +
                    ChatColor.AQUA + " weather has been extended! " + ChatColor.YELLOW + "Remaining: " + timeStr));
        }

        lastDuration.put(uuid, activeDuration);
        updateBossBar(world, activeType, activeDuration);
        applyWorldPhysics(world, activeType);
    }

    private void applyWorldPhysics(World world, WeatherType activeType) {
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

    private void updateBossBar(World world, WeatherType type, long duration) {
        UUID uuid = world.getUID();
        BossBar bar = worldBars.get(uuid);

        if (type == null) {
            if (bar != null) {
                bar.removeAll();
                worldBars.remove(uuid);
            }
            return;
        }

        if (bar == null) {
            bar = Bukkit.createBossBar("", type.barColor, BarStyle.SOLID);
            worldBars.put(uuid, bar);
        }

        bar.setTitle(type.displayName + ChatColor.WHITE + " (" + formatTime(duration) + ")");
        bar.setStyle(type == WeatherType.STORM ? BarStyle.SEGMENTED_6 : BarStyle.SOLID);
        bar.setColor(type.barColor);

        double max = maxDuration.getOrDefault(uuid, 1L);
        double progress = Math.min(1.0, Math.max(0.0, (double) duration / max));
        bar.setProgress(progress);

        for (Player p : world.getPlayers())
            if (!bar.getPlayers().contains(p))
                bar.addPlayer(p);
        for (Player p : new ArrayList<>(bar.getPlayers()))
            if (!p.getWorld().equals(world))
                bar.removePlayer(p);
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
