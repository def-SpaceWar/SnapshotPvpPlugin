package com.defspacemine.snapshotpvp.gamemode;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.lobby.ManaKitLobbyType.ManaKitLobbyInstance;
import com.defspacemine.snapshotpvp.manakit.ManaKit;
import com.defspacemine.snapshotpvp.manakit.ManaKitListener;
import com.defspacemine.snapshotpvp.scoreboard.GlobalScoreboardManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

public class KitRotation extends Gamemode implements Listener {

    public int lives = 3;
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> deaths = new HashMap<>();
    private final Map<UUID, Set<String>> usedKits = new HashMap<>();
    private final Map<UUID, Player> participants = new HashMap<>();

    public KitRotation(ManaKitLobbyInstance lobby, World world) {
        this.lobby = lobby;
        this.world = world;
    }

    @Override
    public void configure() {
        throw new UnsupportedOperationException(
            "Configure Kit Rotation through the lobby vote."
        );
    }

    @Override
    public void onStart() {
        SnapshotPvpPlugin.pluginManager.registerEvents(
            this,
            SnapshotPvpPlugin.instance
        );
        for (Player player : lobby.getPlayers()) {
            UUID id = player.getUniqueId();
            participants.put(id, player);
            kills.put(id, 0);
            deaths.put(id, 0);
            usedKits.put(id, new HashSet<>());
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

    public boolean hasUsedKit(Player player, String kitId) {
        return usedKits
            .getOrDefault(player.getUniqueId(), Set.of())
            .contains(kitId);
    }

    public int getDeaths(Player player) {
        return deaths.getOrDefault(player.getUniqueId(), 0);
    }

    public boolean isEliminated(Player player) {
        if (lives == -1) {
            return usedKits
                .getOrDefault(player.getUniqueId(), Set.of())
                .size() >= ManaKitListener.instance.manaKitRegistry.size();
        }
        return getDeaths(player) >= lives;
    }

    private boolean isAlive(Player player) {
        return (
            lobby.isMatchPlayer(player) &&
            !isEliminated(player)
        );
    }

    private String teamKey(Player player) {
        Team team = SnapshotPvpPlugin.getTeam(player);
        return team == null
            ? player.getUniqueId().toString()
            : team.getName();
    }

    private void scoreboardHandler(Player viewer) {
        List<Player> players = lobby
            .getPlayers()
            .stream()
            .filter(player -> participants.containsKey(player.getUniqueId()))
            .toList();
        Scoreboard board = GlobalScoreboardManager.makeNewScoreboard(
            viewer,
            players
        );
        GlobalScoreboardManager.setupScoreboardHP(board, players);
        Objective objective = GlobalScoreboardManager.initializeCombatList(
            board
        );
        objective.displayName(
            Component.text("---- KIT ROTATION ----")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
        );

        List<Player> sorted = new ArrayList<>(players);
        sorted.sort((first, second) -> {
            int killOrder = Integer.compare(
                kills.getOrDefault(second.getUniqueId(), 0),
                kills.getOrDefault(first.getUniqueId(), 0)
            );
            if (killOrder != 0) return killOrder;
            int deathOrder = Integer.compare(
                deaths.getOrDefault(first.getUniqueId(), 0),
                deaths.getOrDefault(second.getUniqueId(), 0)
            );
            if (deathOrder != 0) return deathOrder;
            return first.getName().compareToIgnoreCase(second.getName());
        });

        int position = 100;
        GlobalScoreboardManager.setHiddenScore(objective, "", position--);
        for (Player player : sorted) {
            Team team = SnapshotPvpPlugin.getTeam(player);
            ChatColor nameColor = team == null
                ? ChatColor.WHITE
                : ChatColor.valueOf(team.getColor().name());
            String prefix = team == null ? "" : team.getPrefix();
            String left = nameColor + prefix + player.getName();

            int killCount = kills.getOrDefault(player.getUniqueId(), 0);
            int deathCount = deaths.getOrDefault(player.getUniqueId(), 0);
            boolean inCombat =
                player.getScoreboardTags().contains("fighting") &&
                player.getScoreboardTags().contains("combat") &&
                player.getGameMode() != GameMode.SPECTATOR;
            int streak = inCombat
                ? SnapshotPvpPlugin.getPlayerScore(player, "dummyKillstreak")
                : 0;
            String deathText = ChatColor.GRAY + "" + deathCount;
            if (lives != -1) deathText += "/" + lives;

            String status = player.getGameMode() == GameMode.SPECTATOR
                ? ChatColor.YELLOW + "SPECTATOR"
                : ChatColor.AQUA + "IN LOBBY";
            int hp = (int) player.getHealth();
            int absorption = (int) player.getAbsorptionAmount();
            String hpText = ChatColor.GREEN + "" + hp + "❤";
            if (absorption > 0) {
                hpText += " " + ChatColor.YELLOW + absorption + "❤";
            }

            String right =
                ChatColor.RED +
                "" +
                killCount +
                "🗡" +
                ChatColor.GRAY +
                " | " +
                deathText +
                "☠" +
                ChatColor.GRAY +
                " | " +
                ChatColor.GOLD +
                streak +
                "🔥";
            if (inCombat) {
                right += ChatColor.GRAY + " | " + hpText;
            } else {
                right += ChatColor.GRAY + " | " + status;
            }
            GlobalScoreboardManager.setHiddenScore(
                objective,
                left,
                right,
                position--
            );
        }
    }

    @Override
    public void onTick() {
        for (Player player : lobby.getPlayers()) {
            if (!lobby.isMatchPlayer(player)) continue;
            UUID id = player.getUniqueId();
            if (participants.putIfAbsent(id, player) == null) {
                kills.put(id, 0);
                deaths.put(id, 0);
                usedKits.put(id, new HashSet<>());
            }
        }
    }

    @Override
    public boolean isOver() {
        Set<String> survivingTeams = new HashSet<>();
        for (Player player : participants.values()) {
            if (!isAlive(player)) continue;
            survivingTeams.add(teamKey(player));
        }
        return survivingTeams.size() <= 1;
    }

    @Override
    public boolean allowsEarlyEndVote() {
        return false;
    }

    @Override
    public void sendGameOverMessage(Player recipient) {
        Set<String> winningTeams = new HashSet<>();
        for (Player player : participants.values()) {
            if (isAlive(player)) winningTeams.add(teamKey(player));
        }

        List<Player> winners = participants
            .values()
            .stream()
            .filter(player ->
                lobby.isMatchPlayer(player) &&
                winningTeams.contains(teamKey(player))
            )
            .sorted((first, second) ->
                first.getName().compareToIgnoreCase(second.getName())
            )
            .toList();
        recipient.sendMessage(ChatColor.GOLD + "==== Kit Rotation Results ====");
        if (!winners.isEmpty()) {
            StringBuilder result = new StringBuilder(
                ChatColor.GREEN +
                    "Winner" +
                    (winningTeams.size() == 1 ? ": " : "s: ")
            );
            for (int i = 0; i < winners.size(); i++) {
                Player winner = winners.get(i);
                if (i > 0) result.append(ChatColor.GRAY).append(", ");
                Team team = SnapshotPvpPlugin.getTeam(winner);
                result
                    .append(team == null ? ChatColor.WHITE : team.getColor())
                    .append(winner.getName());
            }
            recipient.sendMessage(result.toString());
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        if (
            !participants.containsKey(victim.getUniqueId()) ||
            !lobby.isMatchPlayer(victim)
        ) return;

        deaths.merge(victim.getUniqueId(), 1, Integer::sum);
        ManaKit kit = ManaKitListener.instance.getPlayerKit(victim);
        if (kit != null) {
            usedKits
                .computeIfAbsent(victim.getUniqueId(), ignored -> new HashSet<>())
                .add(kit.getId());
        }
        ManaKitListener.clearManaKitData(victim);

        Player attacker = victim.getKiller();
        if (
            attacker == null ||
            !participants.containsKey(attacker.getUniqueId()) ||
            !lobby.isMatchPlayer(attacker)
        ) return;
        Team victimTeam = SnapshotPvpPlugin.getTeam(victim);
        if (
            victimTeam != null &&
            victimTeam.equals(SnapshotPvpPlugin.getTeam(attacker))
        ) return;
        kills.merge(attacker.getUniqueId(), 1, Integer::sum);
    }
}
