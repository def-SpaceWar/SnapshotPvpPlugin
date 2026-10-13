package com.defspacemine.snapshotpvp.gamemode;

import com.defspacemine.snapshotpvp.scoreboard.GlobalScoreboardManager;
import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.lobby.LobbyType.LobbyInstance;
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

public class Deathmatch extends Gamemode implements Listener {

    public int killsRequired = -1; // -1 | 5 | 10 | 20
    public int timeLimit = 18000; // ticks ~ 15 minutess
    // public int timeLimit = 912000000;
    public Map<Player, Integer> killCounts = new HashMap<Player, Integer>();

    public Deathmatch(LobbyInstance lobby, World world) {
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

            int ks1 = p1.getScoreboardTags().contains("fighting") ? SnapshotPvpPlugin.getPlayerScore(p1, "dummyKillstreak") : -1;
            int ks2 = p2.getScoreboardTags().contains("fighting") ? SnapshotPvpPlugin.getPlayerScore(p2, "dummyKillstreak") : -1;
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
                    ChatColor.RED +
                    "" +
                    killCount +
                    (killsRequired == -1 ? "" : "/" + killsRequired) +
                    "🗡" +
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
                    ChatColor.AQUA +
                    "IN LOBBY";
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
        if (killsRequired != -1) for (Integer i : killCounts.values())
            if (i >= killsRequired) return true;
        if (timeLimit <= 0) return true;
        return false;
    }

    @Override
    public void sendGameOverMessage(Player p) {
        p.sendMessage(
            "Bro the game finished. Someone shoulda payed attention to who won, just check chat. :|"
        );
    }

    @Override
    public void onDestroy() {
        GlobalScoreboardManager.removeBoardHandler(lobby.getWorld().getName());
        GlobalScoreboardManager.removeBoardHandler(world.getName());
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player attacker = victim.getKiller();

        Team vTeam = SnapshotPvpPlugin.getTeam(victim);
        if (
            attacker != null &&
            vTeam != null &&
            vTeam.equals(SnapshotPvpPlugin.getTeam(attacker))
        ) return;

        if (attacker == null) return;
        Integer killCount = killCounts.get(attacker);
        if (killCount == null) killCount = 0;
        killCounts.put(attacker, killCount + 1);
    }
}
