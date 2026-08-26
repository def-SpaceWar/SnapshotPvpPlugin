package com.defspacemine.snapshotpvp.gamemode;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.lobby.LobbyType.LobbyInstance;
import com.defspacemine.snapshotpvp.scoreboard.GlobalScoreboardManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class Streakmatch extends Gamemode {

    public int streakRequired = 5; // -1 | 5 | 10 | 20
    public int timeLimit = 18000; // ticks ~ 15 minutess

    public Streakmatch(LobbyInstance lobby, World world) {
        this.lobby = lobby;
        this.world = world;

        // int[] arr = { -1, 5, 10, 20 };
        // streakRequired = arr[(int) Math.floor(Math.random() * 4)];
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
        GlobalScoreboardManager.registerBoardHandler(
            lobby.getWorld().getName(),
            this::scoreboardHandler
        );

        GlobalScoreboardManager.registerBoardHandler(
            world.getName(),
            this::scoreboardHandler
        );
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
            Team team = SnapshotPvpPlugin.getTeam(p);
            ChatColor nameColor =
                team != null
                    ? ChatColor.valueOf(team.getColor().name())
                    : ChatColor.WHITE;
            String prefix = team == null ? "" : team.getPrefix();
            String leftSide = nameColor + prefix + p.getName();
            String rightSide;

            if (p.getScoreboardTags().contains("fighting")) {
                int ks = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
                int hp = (int) p.getHealth();
                int absorp = (int) p.getAbsorptionAmount();
                String hpText = "" + ChatColor.GREEN + hp + "❤";
                if (absorp > 0) hpText += " " + ChatColor.YELLOW + absorp + "❤";

                rightSide =
                    ChatColor.GOLD +
                    (streakRequired == -1
                        ? "" + ks
                        : ks + "/" + streakRequired) +
                    "🔥" +
                    ChatColor.GRAY +
                    " | " +
                    hpText;
            } else {
                rightSide = ChatColor.AQUA + "IN LOBBY";
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
    }

    @Override
    public boolean isOver() {
        if (streakRequired != -1) for (Player p : world.getPlayers()) {
            int ks = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
            if (ks >= streakRequired) return true;
        }
        if (timeLimit <= 0) return true;
        return false;
    }

    @Override
    public void sendGameOverMessage(Player player) {
        for (Player p : world.getPlayers()) {
            player.sendMessage(p.getName() + ": " + SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak"));
        }
    }

    @Override
    public void onDestroy() {
        GlobalScoreboardManager.removeBoardHandler(lobby.getWorld().getName());
        GlobalScoreboardManager.removeBoardHandler(world.getName());
    }
}
