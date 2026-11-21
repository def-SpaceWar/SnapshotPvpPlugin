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
import org.bukkit.GameMode;
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

public class Deathmatch extends Gamemode implements Listener {

    public int killsRequired = -1; // -1 | 5 | 10 | 20
    public int timeLimit = 18000; // ticks ~ 15 minutess
    // public int timeLimit = 912000000;
    public Map<Player, Integer> killCounts = new HashMap<Player, Integer>();
    private final Map<UUID, Integer> deaths = new HashMap<>();

    public Deathmatch(ManaKitLobbyInstance lobby, World world) {
        this.lobby = lobby;
        this.world = world;

        int[] arr = { -1, 5, 10, 20 };
        killsRequired = arr[(int) Math.floor(Math.random() * 4)];
    }

    @Override
    public void configure() {
        // set the amount of kills required and time limit
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
            killCounts.putIfAbsent(player, 0);
            deaths.putIfAbsent(player.getUniqueId(), 0);
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
            Component.text("---- DEATHMATCH ----")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
        );

        for (String entry : board.getEntries())
            obj.getScore(entry).resetScore();

        List<Player> sortedPlayers = new ArrayList<>(playersInGame);
        sortedPlayers.sort((p1, p2) -> {
            Integer kills1 = killCounts.get(p1);
            if (kills1 == null) kills1 = 0;
            Integer kills2 = killCounts.get(p2);
            if (kills2 == null) kills2 = 0;
            if (kills1 != kills2) return Integer.compare(kills2, kills1);

            int ks1 = p1.getScoreboardTags().contains("fighting")
                ? SnapshotPvpPlugin.getPlayerScore(p1, "dummyKillstreak")
                : -1;
            int ks2 = p2.getScoreboardTags().contains("fighting")
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
            Integer killCount = killCounts.get(p);
            if (killCount == null) killCount = 0;
            int deathCount = deaths.getOrDefault(p.getUniqueId(), 0);

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

            if (
                p.getScoreboardTags().contains("fighting") &&
                p.getGameMode() != GameMode.SPECTATOR
            ) {
                int ks = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
                int hp = (int) p.getHealth();
                int absorp = (int) p.getAbsorptionAmount();
                String hpText = "" + ChatColor.GREEN + hp + "❤";
                if (absorp > 0) hpText += " " + ChatColor.YELLOW + absorp + "❤";

                rightSide =
                    ChatColor.RED +
                    "" +
                    killCount +
                    (killsRequired == -1 ? "" : "/" + killsRequired) +
                    "🗡" +
                    ChatColor.GRAY +
                    " | " +
                    deathCount +
                    "☠" +
                    ChatColor.GRAY +
                    " | " +
                    ChatColor.GOLD +
                    ks +
                    "🔥" +
                    ChatColor.GRAY +
                    " | " +
                    hpText;
            } else {
                rightSide =
                    ChatColor.RED +
                    "" +
                    killCount +
                    (killsRequired == -1 ? "" : "/" + killsRequired) +
                    "🗡" +
                    ChatColor.GRAY +
                    " | " +
                    deathCount +
                    "☠" +
                    ChatColor.GRAY +
                    " | " +
                    (p.getGameMode() == GameMode.SPECTATOR
                        ? ChatColor.YELLOW + "SPECTATOR"
                        : ChatColor.AQUA + "IN LOBBY");
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
        for (Player player : lobby.getPlayers()) {
            if (lobby.isMatchPlayer(player)) {
                killCounts.putIfAbsent(player, 0);
                deaths.putIfAbsent(player.getUniqueId(), 0);
            }
        }
    }

    @Override
    public boolean isOver() {
        if (killsRequired != -1) for (Integer i : killCounts.values())
            if (i >= killsRequired) return true;
        if (timeLimit <= 0) return true;
        return false;
    }

    @Override
    public void sendGameOverMessage(Player p) {
        for (Player player : lobby.getPlayers()) {
            if (lobby.isMatchPlayer(player)) killCounts.putIfAbsent(player, 0);
        }
        List<Map.Entry<Player, Integer>> leaderboard = new ArrayList<>(
            killCounts.entrySet()
        );
        leaderboard.sort((first, second) -> {
            int kills = Integer.compare(second.getValue(), first.getValue());
            if (kills != 0) return kills;
            return first
                .getKey()
                .getName()
                .compareToIgnoreCase(second.getKey().getName());
        });

        p.sendMessage(ChatColor.GOLD + "==== Deathmatch Results ====");
        if (!leaderboard.isEmpty()) {
            int winningScore = leaderboard.getFirst().getValue();
            List<String> winners = leaderboard
                .stream()
                .filter(entry -> entry.getValue() == winningScore)
                .map(entry -> entry.getKey().getName())
                .toList();
            p.sendMessage(
                ChatColor.GREEN +
                    (winners.size() == 1 ? "Winner: " : "Winners: ") +
                    String.join(", ", winners) +
                    ChatColor.GRAY +
                    " (" +
                    winningScore +
                    " kills)"
            );
        }
        for (int i = 0; i < leaderboard.size(); i++) {
            Map.Entry<Player, Integer> entry = leaderboard.get(i);
            Team team = SnapshotPvpPlugin.getTeam(entry.getKey());
            ChatColor color = team == null
                ? ChatColor.WHITE
                : team.getColor();
            p.sendMessage(
                "" +
                ChatColor.YELLOW +
                    (i + 1) +
                    ". " +
                    color +
                    entry.getKey().getName() +
                    ChatColor.GRAY +
                    " - " +
                    entry.getValue() +
                    " kills"
            );
        }
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        if (lobby.isMatchPlayer(victim)) {
            deaths.merge(victim.getUniqueId(), 1, Integer::sum);
        }
        Player attacker = victim.getKiller();

        Team vTeam = SnapshotPvpPlugin.getTeam(victim);
        if (
            attacker != null &&
            vTeam != null &&
            vTeam.equals(SnapshotPvpPlugin.getTeam(attacker))
        ) return;

        if (
            attacker == null ||
            !lobby.isMatchPlayer(victim) ||
            !lobby.isMatchPlayer(attacker)
        ) return;
        Integer killCount = killCounts.get(attacker);
        if (killCount == null) killCount = 0;
        killCounts.put(attacker, killCount + 1);
    }
}
