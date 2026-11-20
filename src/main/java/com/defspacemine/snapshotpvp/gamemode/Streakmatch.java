package com.defspacemine.snapshotpvp.gamemode;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.lobby.ManaKitLobbyType.ManaKitLobbyInstance;
import com.defspacemine.snapshotpvp.scoreboard.GlobalScoreboardManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class Streakmatch extends Gamemode implements Listener {

    public int streakRequired = 5; // -1 | 5 | 10 | 20
    public int timeLimit = 18000; // ticks ~ 15 minutess
    private final Map<UUID, Player> participants = new HashMap<>();
    private final Map<UUID, Integer> highestStreaks = new HashMap<>();
    private final Map<UUID, Long> highestStreakAchievedAt = new HashMap<>();
    private long matchStartNanos;

    public Streakmatch(ManaKitLobbyInstance lobby, World world) {
        this.lobby = lobby;
        this.world = world;

        int[] arr = { -1, 5, 10, 20 };
        streakRequired = arr[(int) Math.floor(Math.random() * 4)];
    }

    @Override
    public void configure() {
        // set the killstreak required and time limit
        throw new UnsupportedOperationException(
            "Unimplemented method 'configure'"
        );
    }

    @Override
    public void onStart() {
        matchStartNanos = System.nanoTime();
        SnapshotPvpPlugin.pluginManager.registerEvents(
            this,
            SnapshotPvpPlugin.instance
        );
        for (Player player : lobby.getPlayers()) {
            UUID playerId = player.getUniqueId();
            participants.put(playerId, player);
            highestStreaks.put(playerId, 0);
            highestStreakAchievedAt.put(playerId, 0L);
            SnapshotPvpPlugin.setPlayerScore(player, "dummyKillstreak", 0);
        }

        GlobalScoreboardManager.registerBoardHandler(
            lobby.getWorld().getName(),
            this::scoreboardHandler
        );

        GlobalScoreboardManager.registerBoardHandler(
            world.getName(),
            this::scoreboardHandler
        );
        if (shadowWorld != null) {
            GlobalScoreboardManager.registerBoardHandler(
                shadowWorld.getName(),
                this::scoreboardHandler
            );
        }
    }

    private void scoreboardHandler(Player viewer) {
        List<Player> playersInGame = lobby.getPlayers();
        if (playersInGame == null) return;

        Scoreboard board = GlobalScoreboardManager.makeNewScoreboard(
            viewer,
            playersInGame
        );
        GlobalScoreboardManager.setupScoreboardHP(board, playersInGame);
        Objective obj = GlobalScoreboardManager.initializeCombatList(board);
        obj.displayName(
            Component.text("---- STREAKMATCH ----")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
        );

        for (String entry : board.getEntries())
            obj.getScore(entry).resetScore();

        List<Player> sortedPlayers = new ArrayList<>(playersInGame);
        sortedPlayers.sort((p1, p2) -> {
            int ks1 = streakRequired == -1
                ? highestStreaks.getOrDefault(p1.getUniqueId(), 0)
                : p1.getScoreboardTags().contains("fighting")
                ? SnapshotPvpPlugin.getPlayerScore(p1, "dummyKillstreak")
                : -1;
            int ks2 = streakRequired == -1
                ? highestStreaks.getOrDefault(p2.getUniqueId(), 0)
                : p2.getScoreboardTags().contains("fighting")
                ? SnapshotPvpPlugin.getPlayerScore(p2, "dummyKillstreak")
                : -1;
            if (ks1 != ks2) return Integer.compare(ks2, ks1);
            long achieved1 = highestStreakAchievedAt.getOrDefault(
                p1.getUniqueId(),
                Long.MAX_VALUE
            );
            long achieved2 = highestStreakAchievedAt.getOrDefault(
                p2.getUniqueId(),
                Long.MAX_VALUE
            );
            int chronological = Long.compare(achieved1, achieved2);
            if (chronological != 0) return chronological;

            double hp1 = p1.getHealth();
            double hp2 = p2.getHealth();
            if (hp1 != hp2) return Double.compare(hp2, hp1);

            return p1.getName().compareToIgnoreCase(p2.getName());
        });

        int position = 100;
        GlobalScoreboardManager.setHiddenScore(obj, "", position--);

        for (Player p : sortedPlayers) {
            Team team = SnapshotPvpPlugin.getTeam(p);
            ChatColor nameColor =
                team != null
                    ? ChatColor.valueOf(team.getColor().name())
                    : ChatColor.WHITE;
            String prefix = team == null ? "" : team.getPrefix();
            String shadowIndicator = p
                .getWorld()
                .getName()
                .endsWith("_map_shadow")
                ? ChatColor.LIGHT_PURPLE + "✦ "
                : "";
            String leftSide =
                shadowIndicator + nameColor + prefix + p.getName();
            String rightSide;

            if (p.getScoreboardTags().contains("fighting")) {
                int currentStreak = SnapshotPvpPlugin.getPlayerScore(
                    p,
                    "dummyKillstreak"
                );
                int bestStreak = streakRequired == -1
                    ? highestStreaks.getOrDefault(p.getUniqueId(), 0)
                    : highestStreaks.getOrDefault(p.getUniqueId(), 0);
                int hp = (int) p.getHealth();
                int absorp = (int) p.getAbsorptionAmount();
                String hpText = "" + ChatColor.GREEN + hp + "❤";
                if (absorp > 0) hpText += " " + ChatColor.YELLOW + absorp + "❤";

                rightSide =
                    ChatColor.GOLD +
                    streakDisplay(currentStreak, bestStreak) +
                    "🔥" +
                    ChatColor.GRAY +
                    " | " +
                    hpText;
            } else {
                rightSide =
                    ChatColor.AQUA +
                    "IN LOBBY" +
                    ChatColor.GRAY +
                    " | " +
                    ChatColor.GOLD +
                    highestStreaks.getOrDefault(p.getUniqueId(), 0) +
                    "🔥";
            }

            GlobalScoreboardManager.setHiddenScore(
                obj,
                leftSide,
                rightSide,
                position--
            );
        }

        GlobalScoreboardManager.setHiddenScore(obj, " ", position--);
        GlobalScoreboardManager.setHiddenScore(
            obj,
            "  ",
            ChatColor.GRAY +
                "Time Left: " +
                ChatColor.WHITE +
                timeLimit / 1200 +
                "m" +
                (timeLimit % 1200) / 20 +
                "s",
            position--
        );
    }

    @Override
    public void onTick() {
        timeLimit--;
        updateHighestStreaks();
    }

    private String streakDisplay(int currentStreak, int bestStreak) {
        if (currentStreak == bestStreak) return String.valueOf(currentStreak);
        return currentStreak + "/" + bestStreak;
    }

    private void updateHighestStreaks() {
        for (Player player : lobby.getPlayers()) {
            if (!lobby.isMatchPlayer(player)) continue;
            UUID playerId = player.getUniqueId();
            participants.putIfAbsent(playerId, player);
            int currentStreak = SnapshotPvpPlugin.getPlayerScore(
                player,
                "dummyKillstreak"
            );
            recordHighestStreak(
                playerId,
                currentStreak,
                System.nanoTime() - matchStartNanos
            );
        }
    }

    private void recordHighestStreak(UUID playerId, int streak, long achievedAt) {
        int previousBest = highestStreaks.getOrDefault(playerId, 0);
        if (streak <= previousBest) return;
        highestStreaks.put(playerId, streak);
        highestStreakAchievedAt.put(playerId, achievedAt);
    }

    @Override
    public boolean isOver() {
        if (streakRequired != -1) {
            for (int streak : highestStreaks.values()) {
                if (streak >= streakRequired) return true;
            }
        }
        if (timeLimit <= 0) return true;
        return false;
    }

    @Override
    public void sendGameOverMessage(Player player) {
        updateHighestStreaks();
        List<Map.Entry<UUID, Player>> leaderboard = new ArrayList<>(
            participants.entrySet()
        );
        leaderboard.sort((first, second) -> {
            int streak = Integer.compare(
                highestStreaks.getOrDefault(second.getKey(), 0),
                highestStreaks.getOrDefault(first.getKey(), 0)
            );
            if (streak != 0) return streak;
            long firstAchieved = highestStreakAchievedAt.getOrDefault(
                first.getKey(),
                Long.MAX_VALUE
            );
            long secondAchieved = highestStreakAchievedAt.getOrDefault(
                second.getKey(),
                Long.MAX_VALUE
            );
            int chronological = Long.compare(firstAchieved, secondAchieved);
            if (chronological != 0) return chronological;
            return first.getValue().getName().compareToIgnoreCase(
                second.getValue().getName()
            );
        });

        player.sendMessage(ChatColor.GOLD + "==== Streakmatch Results ====");
        if (!leaderboard.isEmpty()) {
            int winningStreak = highestStreaks.getOrDefault(
                leaderboard.getFirst().getKey(),
                0
            );
            player.sendMessage(
                ChatColor.GREEN +
                    "Winner: " +
                    leaderboard.getFirst().getValue().getName() +
                    ChatColor.GRAY +
                    " (streak: " +
                    winningStreak +
                    ")"
            );
        }
        for (int i = 0; i < leaderboard.size(); i++) {
            Map.Entry<UUID, Player> entry = leaderboard.get(i);
            Team team = SnapshotPvpPlugin.getTeam(entry.getValue());
            ChatColor color = team == null
                ? ChatColor.WHITE
                : team.getColor();
            player.sendMessage(
                "" +
                ChatColor.YELLOW +
                    (i + 1) +
                    ". " +
                    color +
                    entry.getValue().getName() +
                    ChatColor.GRAY +
                    " - " +
                    highestStreaks.getOrDefault(entry.getKey(), 0) +
                    " streak"
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player attacker = event.getEntity().getKiller();
        if (
            attacker == null ||
            !lobby.isMatchPlayer(event.getEntity()) ||
            !lobby.isMatchPlayer(attacker)
        ) return;

        UUID playerId = attacker.getUniqueId();
        participants.putIfAbsent(playerId, attacker);
        recordHighestStreak(
            playerId,
            SnapshotPvpPlugin.getPlayerScore(attacker, "dummyKillstreak"),
            System.nanoTime() - matchStartNanos
        );
    }

    @Override
    public void onDestroy() {
        GlobalScoreboardManager.removeBoardHandler(lobby.getWorld().getName());
        GlobalScoreboardManager.removeBoardHandler(world.getName());
        if (shadowWorld != null) {
            GlobalScoreboardManager.removeBoardHandler(shadowWorld.getName());
        }
        HandlerList.unregisterAll(this);
    }
}
