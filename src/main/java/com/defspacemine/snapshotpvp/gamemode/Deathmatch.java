package com.defspacemine.snapshotpvp.gamemode;

import com.defspacemine.scoreboard.GlobalScoreboardManager;
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

    public int killsRequired = 10;
    public int timeLimit = 12000; // ticks ~ 10 minutess
    public Map<Player, Integer> killCounts = new HashMap<Player, Integer>();

    public Deathmatch(LobbyInstance lobby, World world) {
        this.lobby = lobby;
        this.world = world;
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
            world.getName(),
            (viewer, playersInWorld) -> {
                Scoreboard board = GlobalScoreboardManager.makeNewScoreboard(
                    viewer,
                    playersInWorld
                );
                GlobalScoreboardManager.setupScoreboardHP(
                    board,
                    playersInWorld
                );

                Objective obj = board.getObjective("combat_list");
                if (obj == null) {
                    obj = board.registerNewObjective(
                        "combat_list",
                        Criteria.DUMMY,
                        Component.text("---- DEATHMATCH ----")
                            .color(NamedTextColor.GOLD)
                            .decorate(TextDecoration.BOLD)
                    );
                    obj.setDisplaySlot(DisplaySlot.SIDEBAR);
                }

                for (String entry : board.getEntries())
                    obj.getScore(entry).resetScore();

                List<Player> sortedPlayers = new ArrayList<>(playersInWorld);
                sortedPlayers.removeIf(p -> {
                    return !p.getScoreboardTags().contains("fighting");
                });
                sortedPlayers.sort((p1, p2) -> {
                    Integer kills1 = killCounts.get(p1);
                    if (kills1 == null) kills1 = 0;
                    Integer kills2 = killCounts.get(p2);
                    if (kills2 == null) kills2 = 0;
                    if (kills1 != kills2) return Integer.compare(
                        kills2,
                        kills1
                    );

                    int ks1 = SnapshotPvpPlugin.getPlayerScore(
                        p1,
                        "dummyKillstreak"
                    );
                    int ks2 = SnapshotPvpPlugin.getPlayerScore(
                        p2,
                        "dummyKillstreak"
                    );
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

                    int ks = SnapshotPvpPlugin.getPlayerScore(
                        p,
                        "dummyKillstreak"
                    );

                    int hp = (int) p.getHealth();
                    int absorp = (int) p.getAbsorptionAmount();
                    Team team = SnapshotPvpPlugin.getTeam(p);
                    ChatColor nameColor =
                        team != null
                            ? ChatColor.valueOf(team.getColor().name())
                            : ChatColor.WHITE;

                    String prefix = team == null ? "" : team.getPrefix();
                    String hpText = "" + ChatColor.GREEN + hp + "❤";
                    if (absorp > 0) hpText +=
                        " " + ChatColor.YELLOW + absorp + "❤";

                    String leftSide = nameColor + prefix + p.getName();
                    String rightSide =
                        ChatColor.RED +
                        "🗡 " +
                        killCount +
                        "/" +
                        killsRequired +
                        ChatColor.GRAY +
                        " | " +
                        ChatColor.GOLD +
                        ks +
                        "🔥" +
                        ChatColor.GRAY +
                        " | " +
                        hpText;
                    GlobalScoreboardManager.setHiddenScore(
                        obj,
                        leftSide,
                        rightSide,
                        position--
                    );
                }

                GlobalScoreboardManager.setHiddenScore(obj, " ", position--);
                GlobalScoreboardManager.setHiddenScoreR(
                    obj,
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
        );
    }

    @Override
    public void onTick() {
        timeLimit--;
    }

    @Override
    public boolean isOver() {
        for (Integer i : killCounts.values())
            if (i >= killsRequired) return true;
        if (timeLimit <= 0) return true;
        return false;
    }

    @Override
    public void sendGameOverMessage(Player p) {
        p.sendMessage("Bro the game finished. Someone shoulda payed attention to who won, just check chat. :|");
    }

    @Override
    public void onDestroy() {
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
