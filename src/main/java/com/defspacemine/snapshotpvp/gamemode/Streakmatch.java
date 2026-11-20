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
        SnapshotPvpPlugin.pluginManager.registerEvents(
            this,
            SnapshotPvpPlugin.instance
        );
        for (Player player : lobby.getPlayers()) {
            UUID playerId = player.getUniqueId();
            participants.put(playerId, player);
            highestStreaks.put(playerId, 0);
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
                int ks = streakRequired == -1
                    ? highestStreaks.getOrDefault(p.getUniqueId(), 0)
                    : SnapshotPvpPlugin.getPlayerScore(
                        p,
                        "dummyKillstreak"
                    );
                int hp = (int) p.getHealth();
                int absorp = (int) p.getAbsorptionAmount();
                String hpText = "" + ChatColor.GREEN + hp + "❤";
                if (absorp > 0) hpText += " " + ChatColor.YELLOW + absorp + "❤";

                rightSide =
                    ChatColor.GOLD +
                    (streakRequired == -1
                        ? ks + " best"
                        : ks + "/" + streakRequired) +
                    "🔥" +
                    ChatColor.GRAY +
                    " | " +
                    hpText;
            } else {
                rightSide =
                    ChatColor.AQUA +
                    "IN LOBBY" +
                    (streakRequired == -1
                        ? ChatColor.GRAY +
                          " | " +
                          ChatColor.GOLD +
                          highestStreaks.getOrDefault(p.getUniqueId(), 0) +
                          " best"
                        : "");
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

    private void updateHighestStreaks() {
        for (Player player : lobby.getPlayers()) {
            if (!lobby.isMatchPlayer(player)) continue;
            UUID playerId = player.getUniqueId();
            participants.putIfAbsent(playerId, player);
            int currentStreak = SnapshotPvpPlugin.getPlayerScore(
                player,
                "dummyKillstreak"
            );
            highestStreaks.merge(
                playerId,
                currentStreak,
                Math::max
            );
        }
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
            return first
                .getValue()
                .getName()
                .compareToIgnoreCase(second.getValue().getName());
        });

        player.sendMessage(ChatColor.GOLD + "==== Streakmatch Results ====");
        if (!leaderboard.isEmpty()) {
            int winningStreak = highestStreaks.getOrDefault(
                leaderboard.getFirst().getKey(),
                0
            );
            List<String> winners = leaderboard
                .stream()
                .filter(entry ->
                    highestStreaks.getOrDefault(entry.getKey(), 0) ==
                    winningStreak
                )
                .map(entry -> entry.getValue().getName())
                .toList();
            player.sendMessage(
                ChatColor.GREEN +
                    (winners.size() == 1 ? "Winner: " : "Winners: ") +
                    String.join(", ", winners) +
                    ChatColor.GRAY +
                    " (best streak: " +
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
                    " best streak"
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
        highestStreaks.merge(
            playerId,
            SnapshotPvpPlugin.getPlayerScore(attacker, "dummyKillstreak"),
            Math::max
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
