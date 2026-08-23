package com.defspacemine.scoreboard;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class GlobalScoreboardManager {

    public static GlobalScoreboardManager instance;
    private final JavaPlugin plugin;

    public interface ScoreboardHandler {
        void setScoreboard(Player viewer);
    }

    public static final Map<
        String,
        ScoreboardHandler
    > scoreboardHandlerRegistry = new HashMap<String, ScoreboardHandler>();

    public GlobalScoreboardManager(JavaPlugin plugin) {
        this.plugin = plugin;

        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    List<Player> playersInWorld = world.getPlayers();

                    for (Player viewer : playersInWorld) {
                        if (viewer == null || !viewer.isOnline()) continue;

                        ScoreboardHandler handler =
                            scoreboardHandlerRegistry.get(world.getName());
                        if (handler != null) {
                            handler.setScoreboard(viewer);
                        } else if (
                            viewer.getScoreboardTags().contains("fighting")
                        ) {
                            ffaWorldScoreboard(viewer, playersInWorld);
                        } else if (
                            viewer.getScoreboard() !=
                            Bukkit.getScoreboardManager().getMainScoreboard()
                        ) viewer.setScoreboard(
                            Bukkit.getScoreboardManager().getMainScoreboard()
                        );
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    public static void registerBoardHandler(
        String worldName,
        ScoreboardHandler scoreboardHandler
    ) {
        scoreboardHandlerRegistry.put(worldName, scoreboardHandler);
    }

    public static void removeBoardHandler(String worldName) {
        scoreboardHandlerRegistry.remove(worldName);
    }

    private void ffaWorldScoreboard(
        Player viewer,
        List<Player> playersInWorld
    ) {
        Scoreboard board = makeNewScoreboard(viewer, playersInWorld);
        setupScoreboardHP(board, playersInWorld);
        Objective obj = initializeCombatList(board);
        obj.displayName(
            Component.text("---- FFA STATS ----")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
        );

        List<Player> sortedPlayers = new ArrayList<>(playersInWorld);
        sortedPlayers.removeIf(p -> {
            return !p.getScoreboardTags().contains("fighting");
        });
        sortedPlayers.sort((p1, p2) -> {
            int ks1 = SnapshotPvpPlugin.getPlayerScore(p1, "dummyKillstreak");
            int ks2 = SnapshotPvpPlugin.getPlayerScore(p2, "dummyKillstreak");
            if (ks1 != ks2) return Integer.compare(ks2, ks1);

            double hp1 = p1.getHealth();
            double hp2 = p2.getHealth();
            if (hp1 != hp2) return Double.compare(hp2, hp1);

            return p1.getName().compareToIgnoreCase(p2.getName());
        });

        int position = 100;
        setHiddenScore(obj, "", position--);

        for (Player p : sortedPlayers) {
            int ks = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");

            int hp = (int) p.getHealth();
            int absorp = (int) p.getAbsorptionAmount();
            Team team = SnapshotPvpPlugin.getTeam(p);
            ChatColor nameColor =
                team != null
                    ? ChatColor.valueOf(team.getColor().name())
                    : ChatColor.WHITE;
            String prefix = team == null ? "" : team.getPrefix();

            String hpText = "" + ChatColor.GREEN + hp + "❤";
            if (absorp > 0) hpText += " " + ChatColor.YELLOW + absorp + "❤";

            String leftSide = nameColor + prefix + p.getName();
            String rightSide =
                "" +
                ChatColor.GOLD +
                ks +
                "🔥" +
                ChatColor.GRAY +
                " | " +
                hpText;
            setHiddenScore(obj, leftSide, rightSide, position--);
        }

        setHiddenScore(obj, " ", position--);
        setHiddenScore(
            obj,
            "  ",
            ChatColor.GRAY +
                "Players: " +
                ChatColor.WHITE +
                sortedPlayers.size(),
            position--
        );
    }

    public static Scoreboard makeNewScoreboard(
        Player viewer,
        List<Player> playersInWorld
    ) {
        Scoreboard mainBoard =
            Bukkit.getScoreboardManager().getMainScoreboard();
        Scoreboard board = viewer.getScoreboard();

        if (board == mainBoard) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            viewer.setScoreboard(board);
        }

        for (Team mainTeam : mainBoard.getTeams()) {
            if (mainTeam.getName() == null) continue;

            Team localTeam = board.getTeam(mainTeam.getName());
            if (localTeam == null) {
                localTeam = board.registerNewTeam(mainTeam.getName());
            }

            localTeam.setColor(mainTeam.getColor());
            localTeam.setPrefix(mainTeam.getPrefix());
            localTeam.setAllowFriendlyFire(mainTeam.allowFriendlyFire());
            localTeam.setCanSeeFriendlyInvisibles(
                mainTeam.canSeeFriendlyInvisibles()
            );

            World viewerWorld = viewer.getWorld();
            for (String entry : mainTeam.getEntries()) {
                Entity entity = SnapshotPvpPlugin.getEntityFromEntry(entry);
                if (
                    !localTeam.hasEntry(entry) &&
                    entity != null &&
                    viewerWorld.equals(entity.getWorld())
                ) localTeam.addEntry(entry);
                else if (
                    localTeam.hasEntry(entry) &&
                    (entity == null || !viewerWorld.equals(entity.getWorld()))
                ) localTeam.removeEntry(entry);
            }
        }

        return board;
    }

    public static void setupScoreboardHP(
        Scoreboard board,
        List<Player> playersInWorld
    ) {
        Objective healthObj = board.getObjective("health");
        if (healthObj == null) {
            healthObj = board.registerNewObjective(
                "health",
                Criteria.DUMMY,
                Component.text("❤").color(NamedTextColor.RED)
            );
            healthObj.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }

        Objective healthObjTablist = board.getObjective("healthTablist");
        if (healthObjTablist == null) {
            healthObjTablist = board.registerNewObjective(
                "healthTablist",
                Criteria.DUMMY,
                Component.text("❤ (tablist)").color(NamedTextColor.RED)
            );
            healthObjTablist.setDisplaySlot(DisplaySlot.PLAYER_LIST);
            healthObjTablist.setRenderType(RenderType.HEARTS);
        }

        for (Player p : playersInWorld) {
            healthObj
                .getScore(p)
                .setScore((int) (p.getHealth() + p.getAbsorptionAmount()));
            healthObjTablist
                .getScore(p)
                .setScore((int) (p.getHealth() + p.getAbsorptionAmount()));
        }
    }

    public static void setHiddenScore(
        Objective obj,
        String leftText,
        String rightText,
        int value
    ) {
        if (obj == null || leftText == null) return;
        Score score = obj.getScore(leftText);
        score.setScore(value);
        if (rightText != null) score.numberFormat(
            NumberFormat.fixed(Component.text(rightText))
        );
        else score.numberFormat(NumberFormat.blank());
    }

    public static void setHiddenScore(Objective obj, String text, int value) {
        setHiddenScore(obj, text, null, value);
    }

    public static Objective initializeCombatList(Scoreboard board) {
        Objective obj = board.getObjective("combat_list");
        if (obj == null) {
            obj = board.registerNewObjective("combat_list", Criteria.DUMMY, "");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        for (String entry : board.getEntries())
            obj.getScore(entry).resetScore();

        return obj;
    }
}
