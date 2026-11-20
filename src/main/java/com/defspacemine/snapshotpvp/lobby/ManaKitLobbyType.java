package com.defspacemine.snapshotpvp.lobby;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.gamemode.Deathmatch;
import com.defspacemine.snapshotpvp.gamemode.Gamemode;
import com.defspacemine.snapshotpvp.gamemode.Streakmatch;
import com.defspacemine.snapshotpvp.manakit.ManaKit;
import com.defspacemine.snapshotpvp.manakit.ManaKitListener;
import com.defspacemine.snapshotpvp.map.BobInvasionRemaster;
import com.defspacemine.snapshotpvp.map.BobLands;
import com.defspacemine.snapshotpvp.map.GameMap;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitRunnable;

public class ManaKitLobbyType extends LobbyType {

    public static boolean isInManaKitLobby(Player player) {
        return getLobbyForPlayer(player) != null;
    }

    public static ManaKitLobbyInstance getLobbyForPlayer(Player player) {
        if (LobbyManager.instance == null) return null;
        return LobbyManager.instance.getLobbyForPlayer(
            player,
            ManaKitLobbyInstance.class
        );
    }

    public String getPrefix() {
        return "manakit";
    }

    public LobbyInstance make(World world) {
        SnapshotPvpPlugin.server.sendMessage(
            Component.text(
                "Mana Kit Lobby! :D (regenerative)" + world.getName()
            )
        );
        return new ManaKitLobbyInstance(world);
    }

    enum LobbyState {
        WAITING, // everyone needs to ready up
        VOTE_MAP,
        VOTE_MODE,
        VOTE_CONFIG,
        VOTE_KIT_SELECTION,
        TEAM_SELECTION,
        KIT_SELECTION, // choose kits
        IN_GAME,
    }

    private enum MapChoice {
        BOB_LANDS,
        BOB_INVASION_REMASTER,
    }

    private enum ModeChoice {
        DEATHMATCH,
        STREAKMATCH,
    }

    private enum TeamChoice {
        RED(ChatColor.RED, Material.RED_WOOL, "a"),
        DARK_RED(ChatColor.DARK_RED, Material.REDSTONE_BLOCK, "b"),
        YELLOW(ChatColor.YELLOW, Material.YELLOW_WOOL, "c"),
        GOLD(ChatColor.GOLD, Material.ORANGE_WOOL, "d"),
        BLUE(ChatColor.BLUE, Material.BLUE_WOOL, "e"),
        DARK_BLUE(ChatColor.DARK_BLUE, Material.LAPIS_BLOCK, "f"),
        GREEN(ChatColor.GREEN, Material.LIME_WOOL, "g"),
        DARK_GREEN(ChatColor.DARK_GREEN, Material.GREEN_WOOL, "h"),
        LIGHT_PURPLE(ChatColor.LIGHT_PURPLE, Material.MAGENTA_WOOL, "i"),
        DARK_PURPLE(ChatColor.DARK_PURPLE, Material.PURPLE_WOOL, "j"),
        AQUA(ChatColor.AQUA, Material.LIGHT_BLUE_WOOL, "k"),
        DARK_AQUA(ChatColor.DARK_AQUA, Material.CYAN_WOOL, "l");

        private final ChatColor color;
        private final Material icon;
        private final String teamSuffix;

        TeamChoice(ChatColor color, Material icon, String teamSuffix) {
            this.color = color;
            this.icon = icon;
            this.teamSuffix = teamSuffix;
        }

        private String displayName() {
            String[] words = name().toLowerCase().split("_");
            StringBuilder result = new StringBuilder();
            for (String word : words) {
                if (!result.isEmpty()) result.append(' ');
                result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
            }
            return result.toString();
        }

        private String scoreboardTeamName(String teamScope) {
            return "mk_" + teamScope + teamSuffix;
        }
    }

    public class ManaKitLobbyInstance
        extends LobbyType.LobbyInstance
        implements Listener
    {

        private static final int PHASE_DURATION_TICKS = 30 * 20;
        private static final NamespacedKey LOBBY_ITEM = new NamespacedKey(
            "defspacemine",
            "manakit_lobby_item"
        );

        private final World world;
        private final World mapWorld;
        private final World shadowMap;
        private final String teamScope;
        private final BossBar phaseBossBar = BossBar.bossBar(
            Component.text("Waiting - 0:30", NamedTextColor.GOLD),
            1f,
            BossBar.Color.YELLOW,
            BossBar.Overlay.PROGRESS
        );
        private GameMap gameMap;
        private Gamemode gamemode;
        private LobbyState lobbyState = LobbyState.WAITING;
        private int phaseTicksRemaining = PHASE_DURATION_TICKS;
        private MapChoice selectedMap;
        private ModeChoice selectedMode;
        private int selectedWinCondition = -1;
        private boolean kitSelectionLimited;
        private final Map<UUID, MapChoice> mapVotes = new HashMap<>();
        private final Map<UUID, ModeChoice> modeVotes = new HashMap<>();
        private final Map<UUID, Integer> configVotes = new HashMap<>();
        private final Map<UUID, Boolean> kitSelectionVotes = new HashMap<>();
        private final Map<UUID, TeamChoice> teamSelections = new HashMap<>();
        private final Map<UUID, String> previousTeamNames = new HashMap<>();
        private final Map<UUID, Inventory> choiceGuis = new HashMap<>();
        private final Set<UUID> skipVotes = new HashSet<>();
        private final Set<UUID> forceNextVote = new HashSet<>();
        private final Set<UUID> kitGuiOpened = new HashSet<>();
        private final Set<UUID> matchPlayers = new HashSet<>();
        private final Set<UUID> pendingJoiners = new HashSet<>();
        private final Set<UUID> earlyEndVotes = new HashSet<>();
        private final Set<UUID> eliminatedPlayers = new HashSet<>();

        public ManaKitLobbyInstance(World world) {
            this.world = world;
            String encodedTeamScope = new BigInteger(
                world.getUID().toString().replace("-", ""),
                16
            ).toString(36);
            teamScope = encodedTeamScope.length() >= 11
                ? encodedTeamScope.substring(0, 11)
                : "0".repeat(11 - encodedTeamScope.length()) + encodedTeamScope;
            mapWorld = Bukkit.getWorld(world.getName() + "_map");
            if (mapWorld == null) {
                throw new IllegalStateException(
                    "Map world is not loaded: " + world.getName() + "_map"
                );
            }
            String shadowMapName = world.getName() + "_map_shadow";
            World loadedShadowMap = Bukkit.getWorld(shadowMapName);
            if (loadedShadowMap == null) {
                loadedShadowMap = Bukkit.createWorld(
                    new WorldCreator(shadowMapName)
                );
            }
            if (loadedShadowMap == null) {
                throw new IllegalStateException(
                    "Could not load or create shadow map world: " +
                    shadowMapName
                );
            }
            shadowMap = loadedShadowMap;
            SnapshotPvpPlugin.pluginManager.registerEvents(
                this,
                SnapshotPvpPlugin.instance
            );
            broadcast(ChatColor.GOLD + "Lobby phase: Waiting (30 seconds)");
            updatePhaseBossBar();
        }

        public World getWorld() {
            return world;
        }

        public List<Player> getPlayers() {
            List<Player> players = Stream.concat(
                world.getPlayers().stream(),
                mapWorld.getPlayers().stream()
            ).toList();
            return Stream.concat(
                players.stream(),
                shadowMap.getPlayers().stream()
            ).toList();
        }

        public boolean isMatchPlayer(Player player) {
            return matchPlayers.contains(player.getUniqueId());
        }

        public void onTick() {
            for (Player player : getPlayers()) {
                if (!player.getScoreboardTags().contains("fighting")) {
                    showSelectedKit(player);
                }
            }

            if (lobbyState == LobbyState.IN_GAME) {
                if (gamemode != null && gamemode.isOver()) {
                    finishMatch(false);
                    return;
                }

                for (Player player : mapWorld.getPlayers()) {
                    if (eliminatedPlayers.contains(player.getUniqueId())) continue;
                    if (!player.getScoreboardTags().contains("fighting")) {
                        spawnPlayer(player);
                    }
                }
                for (Player player : shadowMap.getPlayers()) {
                    if (eliminatedPlayers.contains(player.getUniqueId())) continue;
                    if (!player.getScoreboardTags().contains("fighting")) {
                        spawnPlayer(player, shadowMap);
                    }
                }
                for (Player player : world.getPlayers()) {
                    player.removeScoreboardTag("fighting");
                }
                if (matchPlayers.isEmpty() && !pendingJoiners.isEmpty()) {
                    finishMatch(
                        true,
                        "The match ended because all participants left."
                    );
                    return;
                }
                if (gameMap != null) gameMap.onTick();
                if (gamemode != null) gamemode.onTick();
                return;
            }

            phaseTicksRemaining--;
            if (phaseTicksRemaining <= 0) advancePhase();
            updatePhaseBossBar();
        }

        public void onDestroy() {
            choiceGuis.clear();
            for (Player player : getPlayers()) {
                player.closeInventory();
                player.hideBossBar(phaseBossBar);
                restorePlayerTeam(player);
            }
            removeEmptyLobbyTeams();
            destroyMatch();
            HandlerList.unregisterAll(this);

            SnapshotPvpPlugin.server.sendMessage(
                Component.text("No more lobby. ;( " + world.getName())
            );
        }

        @Override
        public void onLeaveLobby(Player player) {
            super.onLeaveLobby(player);
            ManaKitListener.clearManaKitData(player);
            UUID playerId = player.getUniqueId();
            matchPlayers.remove(playerId);
            pendingJoiners.remove(playerId);
            earlyEndVotes.remove(playerId);
            eliminatedPlayers.remove(playerId);
            mapVotes.remove(playerId);
            modeVotes.remove(playerId);
            configVotes.remove(playerId);
            kitSelectionVotes.remove(playerId);
            skipVotes.remove(playerId);
            forceNextVote.remove(playerId);
            choiceGuis.remove(playerId);
            restorePlayerTeam(player);
            player.hideBossBar(phaseBossBar);
            if (
                lobbyState == LobbyState.IN_GAME &&
                !pendingJoiners.isEmpty()
            ) {
                if (matchPlayers.isEmpty()) {
                    finishMatch(
                        true,
                        "The match ended because all participants left."
                    );
                } else if (earlyEndVotes.containsAll(matchPlayers)) {
                    finishMatch(true);
                }
            }
        }

        @Override
        public void onLobbyJoin(Player player) {
            UUID playerId = player.getUniqueId();
            player.removeScoreboardTag("fighting");
            player.removeScoreboardTag("combat");
            if (lobbyState == LobbyState.IN_GAME) {
                player.hideBossBar(phaseBossBar);
                if (kitSelectionLimited) {
                    pendingJoiners.add(playerId);
                    if (!player.getWorld().equals(world)) {
                        player.teleport(new Location(world, 0.5, 65, 0.5));
                    }
                    player.getInventory().clear();
                    player.sendMessage(
                        ChatColor.YELLOW +
                            "A match is in progress. You can join after " +
                            "everyone votes to end it early."
                    );
                    promptEarlyEndVote();
                    return;
                }

                matchPlayers.add(playerId);
                if (!teamSelections.containsKey(playerId)) {
                    TeamChoice[] choices = TeamChoice.values();
                    setPlayerTeam(
                        player,
                        choices[
                            ThreadLocalRandom.current().nextInt(choices.length)
                        ]
                    );
                }
                if (ManaKitListener.instance.getPlayerKit(player) == null) {
                    ManaKitListener.instance.selectRandomKit(player);
                }
                spawnPlayer(player);
                return;
            }

            if (lobbyState == LobbyState.KIT_SELECTION) {
                player.showBossBar(phaseBossBar);
                updatePhaseBossBar();
                player.getInventory().clear();
                if (kitSelectionLimited) {
                    player
                        .getPersistentDataContainer()
                        .remove(ManaKitListener.MANA_KIT);
                    openLimitedKitGui(player);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Use /kit to choose your kit.");
                }
                return;
            }

            player.showBossBar(phaseBossBar);
            updatePhaseBossBar();
            setupPhaseGui(player);
            player.sendMessage(
                ChatColor.GOLD +
                    "Current lobby phase: " +
                    phaseTitle(lobbyState) +
                    " (" +
                    phaseTicksRemaining / 20 +
                    " seconds remaining)."
            );
        }

        public boolean canSelectKit(Player player) {
            return (
                !kitSelectionLimited ||
                lobbyState == LobbyState.KIT_SELECTION
            );
        }

        public boolean tryOpenKitGui(Player player) {
            if (!canSelectKit(player)) return false;
            return (
                !kitSelectionLimited ||
                kitGuiOpened.add(player.getUniqueId())
            );
        }

        public void voteToEndMatch(Player player) {
            UUID playerId = player.getUniqueId();
            if (
                lobbyState != LobbyState.IN_GAME ||
                !kitSelectionLimited ||
                pendingJoiners.isEmpty() ||
                !matchPlayers.contains(playerId)
            ) {
                player.sendMessage(
                    ChatColor.RED + "There is no match-end vote for you to join."
                );
                return;
            }

            earlyEndVotes.add(playerId);
            if (earlyEndVotes.containsAll(matchPlayers)) {
                finishMatch(true);
                return;
            }

            player.sendMessage(
                ChatColor.GREEN +
                    "Your vote to end the match early was recorded (" +
                    earlyEndVotes.size() +
                    "/" +
                    matchPlayers.size() +
                    ")."
            );
        }

        public void skipCurrentPhase(Player player) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "Only operators can skip phases.");
                return;
            }

            if (lobbyState == LobbyState.IN_GAME) {
                finishMatch(true, "The match was ended early by an operator.");
                return;
            }

            player.sendMessage(
                ChatColor.YELLOW + "Skipping " + phaseTitle(lobbyState) + "."
            );
            advancePhase();
        }

        private void showSelectedKit(Player player) {
            ManaKit kit = ManaKitListener.instance.getPlayerKit(player);
            player.sendActionBar(
                Component.text(
                    "Selected kit: " +
                        (kit == null ? "None" : kit.getDisplayName())
                )
            );
        }

        private void spawnPlayer(Player player) {
            spawnPlayer(player, mapWorld);
        }

        private void spawnPlayer(Player player, World targetWorld) {
            if (gameMap == null) return;
            Location[] spawnPoints = gameMap.getSpawnPoints();
            Location spawnPoint = spawnPoints[
                ThreadLocalRandom.current().nextInt(spawnPoints.length)
            ];
            player.removeScoreboardTag("combat");
            player.addScoreboardTag("fighting");
            player.teleport(
                new Location(
                    targetWorld,
                    spawnPoint.getX(),
                    spawnPoint.getY(),
                    spawnPoint.getZ(),
                    spawnPoint.getYaw(),
                    spawnPoint.getPitch()
                )
            );
        }

        private void beginPhase(LobbyState state) {
            lobbyState = state;
            phaseTicksRemaining = PHASE_DURATION_TICKS;
            skipVotes.clear();
            forceNextVote.clear();
            mapVotes.clear();
            modeVotes.clear();
            configVotes.clear();
            kitSelectionVotes.clear();
            choiceGuis.clear();

            if (state == LobbyState.KIT_SELECTION) {
                kitGuiOpened.clear();
                broadcast(
                    ChatColor.GOLD +
                        "Kit selection is open for 30 seconds."
                );
                for (Player player : getPlayers()) {
                    player.closeInventory();
                    player.getInventory().clear();
                    if (kitSelectionLimited) {
                        player
                            .getPersistentDataContainer()
                            .remove(ManaKitListener.MANA_KIT);
                        openLimitedKitGui(player);
                    } else {
                        player.sendMessage(
                            ChatColor.YELLOW +
                                "Kit selection is unlimited. Use /kit to choose."
                        );
                    }
                }
                updatePhaseBossBar();
                return;
            }

            for (Player player : getPlayers()) setupPhaseGui(player);
            updatePhaseBossBar();
            broadcast(
                ChatColor.GOLD + "Lobby phase: " + phaseTitle(state) +
                    " (30 seconds)"
            );
        }

        private void openLimitedKitGui(Player player) {
            if (!kitGuiOpened.add(player.getUniqueId())) return;
            ManaKitListener.instance.showKits(player);
        }

        private void advancePhase() {
            switch (lobbyState) {
                case WAITING -> beginPhase(LobbyState.VOTE_MAP);
                case VOTE_MAP -> {
                    selectedMap = resolveVote(
                        mapVotes,
                        List.of(
                            MapChoice.BOB_LANDS,
                            MapChoice.BOB_INVASION_REMASTER
                        )
                    );
                    announceResult("Map", mapName(selectedMap));
                    beginPhase(LobbyState.VOTE_MODE);
                }
                case VOTE_MODE -> {
                    selectedMode = resolveVote(
                        modeVotes,
                        List.of(ModeChoice.DEATHMATCH, ModeChoice.STREAKMATCH)
                    );
                    announceResult("Gamemode", modeName(selectedMode));
                    beginPhase(LobbyState.VOTE_CONFIG);
                }
                case VOTE_CONFIG -> {
                    selectedWinCondition = resolveVote(
                        configVotes,
                        winConditionOptions()
                    );
                    announceResult(
                        "Win condition",
                        winConditionName(selectedWinCondition)
                    );
                    beginPhase(LobbyState.VOTE_KIT_SELECTION);
                }
                case VOTE_KIT_SELECTION -> {
                    kitSelectionLimited = resolveVote(
                        kitSelectionVotes,
                        List.of(Boolean.TRUE, Boolean.FALSE)
                    );
                    announceResult(
                        "Kit selection",
                        kitSelectionLimited ? "Limited" : "Unlimited"
                    );
                    beginPhase(LobbyState.TEAM_SELECTION);
                }
                case TEAM_SELECTION -> {
                    assignMissingTeams();
                    beginPhase(LobbyState.KIT_SELECTION);
                }
                case KIT_SELECTION -> beginMatch();
                case IN_GAME -> {}
            }
        }

        private void beginMatch() {
            assignMissingTeams();
            for (Player player : getPlayers()) {
                player.closeInventory();
                clearLobbyItems(player);
                if (ManaKitListener.instance.getPlayerKit(player) == null) {
                    ManaKitListener.instance.selectRandomKit(player);
                }
            }

            gameMap = selectedMap == MapChoice.BOB_LANDS
                ? new BobLands(this, mapWorld)
                : new BobInvasionRemaster(this, mapWorld);
            gameMap.setShadowWorld(shadowMap);
            gameMap.onCreate();

            if (selectedMode == ModeChoice.DEATHMATCH) {
                Deathmatch deathmatch = new Deathmatch(this, mapWorld);
                deathmatch.killsRequired = selectedWinCondition;
                gamemode = deathmatch;
            } else {
                Streakmatch streakmatch = new Streakmatch(this, mapWorld);
                streakmatch.streakRequired = selectedWinCondition;
                gamemode = streakmatch;
            }
            gamemode.setShadowWorld(shadowMap);
            gamemode.onStart();

            lobbyState = LobbyState.IN_GAME;
            matchPlayers.clear();
            eliminatedPlayers.clear();
            pendingJoiners.clear();
            earlyEndVotes.clear();
            for (Player player : getPlayers()) {
                matchPlayers.add(player.getUniqueId());
                if (player.getWorld().equals(world)) spawnPlayer(player);
            }
            for (Player player : getPlayers()) player.hideBossBar(phaseBossBar);
            broadcast(
                ChatColor.GREEN +
                    "The match has started on " +
                    mapName(selectedMap) +
                    " (" +
                    modeName(selectedMode) +
                    ")."
            );
        }

        private void finishMatch(boolean early) {
            finishMatch(
                early,
                early
                    ? "The match ended early by unanimous vote. A new match will begin soon."
                    : null
            );
        }

        private void finishMatch(boolean early, String earlyMessage) {
            if (!early && gamemode != null) {
                for (Player player : getPlayers()) {
                    gamemode.sendGameOverMessage(player);
                }
            }

            for (Player player : getPlayers()) {
                if (
                    player.getWorld().equals(mapWorld) ||
                    player.getWorld().equals(shadowMap)
                ) player.teleport(new Location(world, 0.5, 65, 0.5));
                player.getInventory().clear();
                player.clearActivePotionEffects();
                player.removeScoreboardTag("fighting");
                player.removeScoreboardTag("combat");
                restorePlayerTeam(player);
            }
            destroyMatch();
            matchPlayers.clear();
            pendingJoiners.clear();
            earlyEndVotes.clear();
            eliminatedPlayers.clear();
            kitGuiOpened.clear();
            beginPhase(LobbyState.WAITING);
            if (earlyMessage != null) broadcast(ChatColor.YELLOW + earlyMessage);
        }

        private void destroyMatch() {
            if (gamemode != null) {
                gamemode.onDestroy();
                gamemode = null;
            }
            if (gameMap != null) {
                gameMap.onDestroy();
                gameMap = null;
            }
        }

        private void promptEarlyEndVote() {
            Component prompt = Component.text(
                "A player wants to join. "
            ).append(
                Component.text(
                    "[Vote to end the match and let them join]",
                    net.kyori.adventure.text.format.NamedTextColor.GREEN
                ).clickEvent(ClickEvent.runCommand("/matchend"))
            );
            for (UUID playerId : matchPlayers) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) player.sendMessage(prompt);
            }
        }

        private void broadcast(String message) {
            for (Player player : getPlayers()) player.sendMessage(message);
        }

        private void announceResult(String category, String result) {
            broadcast(
                ChatColor.GREEN + category + " vote result: " + ChatColor.GOLD + result
            );
        }

        private String phaseTitle(LobbyState state) {
            return switch (state) {
                case WAITING -> "Waiting";
                case VOTE_MAP -> "Map vote";
                case VOTE_MODE -> "Gamemode vote for " + mapName(selectedMap);
                case VOTE_CONFIG ->
                    "Win condition vote for " + modeName(selectedMode);
                case VOTE_KIT_SELECTION -> "Kit selection vote";
                case TEAM_SELECTION -> "Team selection";
                case KIT_SELECTION -> "Kit selection";
                case IN_GAME -> "In game";
            };
        }

        private void updatePhaseBossBar() {
            if (lobbyState == LobbyState.IN_GAME) {
                for (Player player : getPlayers()) {
                    player.hideBossBar(phaseBossBar);
                }
                return;
            }

            int ticksRemaining = phaseTicksRemaining;
            String title = phaseTitle(lobbyState) + " - " + formatTime(ticksRemaining);
            phaseBossBar.name(Component.text(title, NamedTextColor.GOLD));
            phaseBossBar.progress(
                Math.max(
                    0f,
                    Math.min(
                        1f,
                        ticksRemaining / (float) PHASE_DURATION_TICKS
                    )
                )
            );
            for (Player player : getPlayers()) player.showBossBar(phaseBossBar);
        }

        private String formatTime(int ticks) {
            int seconds = Math.max(0, ticks) / 20;
            return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
        }

        private String mapName(MapChoice map) {
            return map == MapChoice.BOB_LANDS
                ? "Bob Lands"
                : "Bob Invasion Remaster";
        }

        private String modeName(ModeChoice mode) {
            return mode == ModeChoice.DEATHMATCH ? "Deathmatch" : "Streakmatch";
        }

        private String winConditionName(int value) {
            return value == -1 ? "No limit" : value + " " +
                (selectedMode == ModeChoice.DEATHMATCH ? "kills" : "streak");
        }

        private List<Integer> winConditionOptions() {
            return selectedMode == ModeChoice.DEATHMATCH
                ? List.of(-1, 10, 20)
                : List.of(-1, 5, 10);
        }

        private <T> T resolveVote(Map<UUID, T> votes, List<T> options) {
            Map<T, Integer> counts = new HashMap<>();
            for (T option : options) counts.put(option, 0);
            for (T vote : votes.values()) counts.merge(vote, 1, Integer::sum);

            int highestCount = counts.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
            List<T> winners = options.stream()
                .filter(option -> counts.get(option) == highestCount)
                .toList();
            return winners.get(ThreadLocalRandom.current().nextInt(winners.size()));
        }

        private void setupPhaseGui(Player player) {
            if (
                lobbyState == LobbyState.KIT_SELECTION ||
                lobbyState == LobbyState.IN_GAME
            ) return;
            player.closeInventory();
            player.getInventory().clear();
            player.getInventory().setItem(
                8,
                lobbyItem(
                    Material.CHEST,
                    "Reopen " + phaseTitle(lobbyState),
                    "reopen"
                )
            );
            openChoiceGui(player);
        }

        private void openChoiceGui(Player player) {
            if (
                lobbyState == LobbyState.KIT_SELECTION ||
                lobbyState == LobbyState.IN_GAME
            ) return;
            Inventory gui = Bukkit.createInventory(
                null,
                27,
                phaseTitle(lobbyState)
            );

            switch (lobbyState) {
                case WAITING -> {}
                case VOTE_MAP -> {
                    gui.setItem(
                        11,
                        lobbyItem(
                            Material.GRASS_BLOCK,
                            "Bob Lands",
                            "map:bob_lands"
                        )
                    );
                    gui.setItem(
                        15,
                        lobbyItem(
                            Material.IRON_SWORD,
                            "Bob Invasion Remaster",
                            "map:bob_invasion_remaster"
                        )
                    );
                }
                case VOTE_MODE -> {
                    gui.setItem(
                        11,
                        lobbyItem(
                            Material.IRON_SWORD,
                            "Deathmatch",
                            "mode:deathmatch"
                        )
                    );
                    gui.setItem(
                        15,
                        lobbyItem(
                            Material.BLAZE_POWDER,
                            "Streakmatch",
                            "mode:streakmatch"
                        )
                    );
                }
                case VOTE_CONFIG -> {
                    List<Integer> options = winConditionOptions();
                    for (int i = 0; i < options.size(); i++) {
                        int value = options.get(i);
                        gui.setItem(
                            10 + i * 3,
                            lobbyItem(
                                Material.PAPER,
                                winConditionName(value),
                                "config:" + value
                            )
                        );
                    }
                }
                case VOTE_KIT_SELECTION -> {
                    gui.setItem(
                        11,
                        lobbyItem(
                            Material.BARRIER,
                            "Limited kit selection",
                            "kit_selection:limited"
                        )
                    );
                    gui.setItem(
                        15,
                        lobbyItem(
                            Material.CHEST,
                            "Unlimited kit selection",
                            "kit_selection:unlimited"
                        )
                    );
                }
                case TEAM_SELECTION -> {
                    int[] slots = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23 };
                    TeamChoice[] choices = TeamChoice.values();
                    for (int i = 0; i < choices.length; i++) {
                        TeamChoice choice = choices[i];
                        gui.setItem(
                            slots[i],
                            lobbyItem(
                                choice.icon,
                                choice.color + choice.displayName(),
                                "team:" + choice.name()
                            )
                        );
                    }
                }
                case KIT_SELECTION, IN_GAME -> {}
            }

            gui.setItem(
                lobbyState == LobbyState.TEAM_SELECTION ? 25 : 22,
                lobbyItem(
                    Material.CLOCK,
                    player.isOp()
                        ? "Skip phase"
                        : "Vote to skip this phase",
                    "skip"
                )
            );
            if (
                player.isOp() &&
                lobbyState != LobbyState.WAITING &&
                lobbyState != LobbyState.TEAM_SELECTION
            ) {
                gui.setItem(
                    26,
                    lobbyItem(
                        Material.NETHER_STAR,
                        "Force next choice",
                        "force"
                    )
                );
            }

            choiceGuis.put(player.getUniqueId(), gui);
            player.openInventory(gui);
        }

        private boolean requiresChoice(LobbyState state) {
            return state == LobbyState.VOTE_MAP ||
                state == LobbyState.VOTE_MODE ||
                state == LobbyState.VOTE_CONFIG ||
                state == LobbyState.VOTE_KIT_SELECTION ||
                state == LobbyState.TEAM_SELECTION;
        }

        private boolean hasMadeChoice(Player player, LobbyState state) {
            UUID playerId = player.getUniqueId();
            return switch (state) {
                case VOTE_MAP -> mapVotes.containsKey(playerId);
                case VOTE_MODE -> modeVotes.containsKey(playerId);
                case VOTE_CONFIG -> configVotes.containsKey(playerId);
                case VOTE_KIT_SELECTION ->
                    kitSelectionVotes.containsKey(playerId);
                case TEAM_SELECTION -> teamSelections.containsKey(playerId);
                default -> true;
            };
        }

        private void reopenChoiceGui(Player player) {
            if (
                lobbyState == LobbyState.KIT_SELECTION ||
                lobbyState == LobbyState.IN_GAME
            ) return;
            openChoiceGui(player);
        }

        private void clearLobbyItems(Player player) {
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                if (isLobbyItem(player.getInventory().getItem(slot))) {
                    player.getInventory().setItem(slot, null);
                }
            }
        }

        private ItemStack lobbyItem(
            Material material,
            String name,
            String action
        ) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + name);
            meta.setLore(List.of(ChatColor.GRAY + "Click or right-click to vote."));
            meta.getPersistentDataContainer().set(
                LOBBY_ITEM,
                PersistentDataType.STRING,
                action
            );
            item.setItemMeta(meta);
            return item;
        }

        @EventHandler(ignoreCancelled = true)
        public void onLobbyItemInteract(PlayerInteractEvent event) {
            Player player = event.getPlayer();
            if (
                event.getHand() != EquipmentSlot.HAND ||
                !isLobbyWorld(player.getWorld())
            ) return;
            ItemStack item = event.getItem();
            if (item == null || !item.hasItemMeta()) return;

            String action = item.getItemMeta()
                .getPersistentDataContainer()
                .get(LOBBY_ITEM, PersistentDataType.STRING);
            if (action == null) return;

            event.setCancelled(true);
            handleLobbyItem(player, action);
        }

        @EventHandler(ignoreCancelled = true)
        public void onLobbyItemClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            Inventory gui = choiceGuis.get(player.getUniqueId());
            if (
                gui != null &&
                event.getView().getTopInventory().equals(gui)
            ) {
                event.setCancelled(true);
                if (event.getClickedInventory() != gui) return;

                String guiAction = lobbyItemAction(event.getCurrentItem());
                if (guiAction == null) return;
                LobbyState clickedState = lobbyState;
                handleLobbyItem(player, guiAction);
                if (
                    lobbyState == clickedState &&
                    requiresChoice(clickedState) &&
                    hasMadeChoice(player, clickedState)
                ) player.closeInventory();
                return;
            }

            if (!isLobbyWorld(player.getWorld())) return;
            String action = lobbyItemAction(event.getCurrentItem());
            if (action != null) {
                event.setCancelled(true);
                handleLobbyItem(player, action);
                return;
            }
            if (isLobbyItem(event.getCursor())) event.setCancelled(true);
        }

        @EventHandler
        public void onChoiceGuiClose(InventoryCloseEvent event) {
            if (!(event.getPlayer() instanceof Player player)) return;
            UUID playerId = player.getUniqueId();
            Inventory gui = choiceGuis.get(playerId);
            if (gui == null || !gui.equals(event.getInventory())) return;

            choiceGuis.remove(playerId);
            LobbyState closedState = lobbyState;
            if (
                !requiresChoice(closedState) ||
                hasMadeChoice(player, closedState)
            ) return;

            Bukkit.getScheduler().runTask(SnapshotPvpPlugin.instance, () -> {
                if (
                    player.isOnline() &&
                    lobbyState == closedState &&
                    isLobbyWorld(player.getWorld()) &&
                    requiresChoice(closedState) &&
                    !hasMadeChoice(player, closedState)
                ) openChoiceGui(player);
            });
        }

        @EventHandler(ignoreCancelled = true)
        public void onChoiceGuiDrag(InventoryDragEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            Inventory gui = choiceGuis.get(player.getUniqueId());
            if (
                gui != null &&
                event.getView().getTopInventory().equals(gui)
            ) event.setCancelled(true);
        }

        @EventHandler(ignoreCancelled = true)
        public void onLobbyItemDrop(PlayerDropItemEvent event) {
            if (
                isLobbyWorld(event.getPlayer().getWorld()) &&
                isLobbyItem(event.getItemDrop().getItemStack())
            ) event.setCancelled(true);
        }

        @EventHandler(ignoreCancelled = true)
        public void onLobbyItemSwap(PlayerSwapHandItemsEvent event) {
            if (!isLobbyWorld(event.getPlayer().getWorld())) return;
            if (
                isLobbyItem(event.getMainHandItem()) ||
                isLobbyItem(event.getOffHandItem())
            ) event.setCancelled(true);
        }

        private boolean isLobbyWorld(World candidate) {
            return (
                candidate.equals(world) ||
                candidate.equals(mapWorld) ||
                candidate.equals(shadowMap)
            );
        }

        private boolean isLobbyItem(ItemStack item) {
            return (
                item != null &&
                item.hasItemMeta() &&
                item.getItemMeta()
                    .getPersistentDataContainer()
                    .has(LOBBY_ITEM, PersistentDataType.STRING)
            );
        }

        private String lobbyItemAction(ItemStack item) {
            if (!isLobbyItem(item)) return null;
            return item.getItemMeta()
                .getPersistentDataContainer()
                .get(LOBBY_ITEM, PersistentDataType.STRING);
        }

        private void handleLobbyItem(Player player, String action) {
            UUID playerId = player.getUniqueId();
            if (action.equals("skip")) {
                if (player.isOp()) {
                    skipCurrentPhase(player);
                    return;
                }
                if (lobbyState == LobbyState.IN_GAME) return;
                if (
                    lobbyState == LobbyState.WAITING &&
                    getPlayers().size() <= 1
                ) {
                    player.sendMessage(
                        ChatColor.RED +
                            "At least two players are required to skip waiting."
                    );
                    return;
                }
                skipVotes.add(playerId);
                player.sendMessage(
                    ChatColor.GREEN +
                        "Skip vote recorded (" +
                        skipVotes.size() +
                        "/" +
                        getPlayers().size() +
                        ")."
                );
                if (skipVotes.size() * 2 > getPlayers().size()) advancePhase();
                return;
            }
            if (action.equals("reopen")) {
                reopenChoiceGui(player);
                return;
            }
            if (action.equals("force")) {
                if (!player.isOp()) return;
                if (forceNextVote.remove(playerId)) {
                    player.sendMessage(ChatColor.YELLOW + "Force vote disabled.");
                } else {
                    forceNextVote.add(playerId);
                    player.sendMessage(
                        ChatColor.YELLOW +
                            "Your next choice will be forced and advance the phase."
                    );
                }
                return;
            }

            switch (lobbyState) {
                case VOTE_MAP -> voteForMap(player, action);
                case VOTE_MODE -> voteForMode(player, action);
                case VOTE_CONFIG -> voteForConfig(player, action);
                case VOTE_KIT_SELECTION -> voteForKitSelection(player, action);
                case TEAM_SELECTION -> voteForTeam(player, action);
                default -> {}
            }
        }

        private void voteForTeam(Player player, String action) {
            if (!action.startsWith("team:")) return;
            TeamChoice choice;
            try {
                choice = TeamChoice.valueOf(action.substring("team:".length()));
            } catch (IllegalArgumentException exception) {
                return;
            }
            setPlayerTeam(player, choice);
        }

        private void setPlayerTeam(Player player, TeamChoice choice) {
            UUID playerId = player.getUniqueId();
            Team currentTeam = SnapshotPvpPlugin.getTeam(player);
            previousTeamNames.putIfAbsent(
                playerId,
                currentTeam == null ? "" : currentTeam.getName()
            );
            if (currentTeam != null) currentTeam.removeEntry(player.getName());

            Team team = SnapshotPvpPlugin.scoreboard.getTeam(
                choice.scoreboardTeamName(teamScope)
            );
            if (team == null) {
                team = SnapshotPvpPlugin.scoreboard.registerNewTeam(
                    choice.scoreboardTeamName(teamScope)
                );
            }
            team.setColor(choice.color);
            team.addEntry(player.getName());
            teamSelections.put(playerId, choice);
            player.sendMessage(
                ChatColor.GREEN +
                    "You joined the " +
                    choice.color +
                    choice.displayName() +
                    ChatColor.GREEN +
                    " team."
            );
        }

        private void assignMissingTeams() {
            for (Player player : getPlayers()) {
                if (teamSelections.containsKey(player.getUniqueId())) continue;
                TeamChoice[] choices = TeamChoice.values();
                setPlayerTeam(
                    player,
                    choices[ThreadLocalRandom.current().nextInt(choices.length)]
                );
            }
        }

        private void restorePlayerTeam(Player player) {
            UUID playerId = player.getUniqueId();
            TeamChoice selected = teamSelections.remove(playerId);
            if (selected != null) {
                Team team = SnapshotPvpPlugin.scoreboard.getTeam(
                    selected.scoreboardTeamName(teamScope)
                );
                if (team != null) team.removeEntry(player.getName());
            }

            String previousTeamName = previousTeamNames.remove(playerId);
            if (previousTeamName == null || previousTeamName.isEmpty()) return;
            Team previousTeam = SnapshotPvpPlugin.scoreboard.getTeam(
                previousTeamName
            );
            if (previousTeam != null) previousTeam.addEntry(player.getName());
        }

        private void removeEmptyLobbyTeams() {
            for (TeamChoice choice : TeamChoice.values()) {
                Team team = SnapshotPvpPlugin.scoreboard.getTeam(
                    choice.scoreboardTeamName(teamScope)
                );
                if (team != null && team.getEntries().isEmpty()) {
                    team.unregister();
                }
            }
        }

        private boolean consumeForceVote(Player player) {
            return player.isOp() && forceNextVote.remove(player.getUniqueId());
        }

        private void voteForMap(Player player, String action) {
            MapChoice choice = switch (action) {
                case "map:bob_lands" -> MapChoice.BOB_LANDS;
                case "map:bob_invasion_remaster" ->
                    MapChoice.BOB_INVASION_REMASTER;
                default -> null;
            };
            if (choice == null) return;
            if (consumeForceVote(player)) {
                selectedMap = choice;
                announceResult("Map", mapName(choice));
                beginPhase(LobbyState.VOTE_MODE);
            } else {
                mapVotes.put(player.getUniqueId(), choice);
            }
        }

        private void voteForMode(Player player, String action) {
            ModeChoice choice = switch (action) {
                case "mode:deathmatch" -> ModeChoice.DEATHMATCH;
                case "mode:streakmatch" -> ModeChoice.STREAKMATCH;
                default -> null;
            };
            if (choice == null) return;
            if (consumeForceVote(player)) {
                selectedMode = choice;
                announceResult("Gamemode", modeName(choice));
                beginPhase(LobbyState.VOTE_CONFIG);
            } else {
                modeVotes.put(player.getUniqueId(), choice);
            }
        }

        private void voteForConfig(Player player, String action) {
            if (!action.startsWith("config:")) return;
            int value;
            try {
                value = Integer.parseInt(action.substring("config:".length()));
            } catch (NumberFormatException exception) {
                return;
            }
            if (!winConditionOptions().contains(value)) return;
            if (consumeForceVote(player)) {
                selectedWinCondition = value;
                announceResult("Win condition", winConditionName(value));
                beginPhase(LobbyState.VOTE_KIT_SELECTION);
            } else {
                configVotes.put(player.getUniqueId(), value);
            }
        }

        private void voteForKitSelection(Player player, String action) {
            Boolean limited = switch (action) {
                case "kit_selection:limited" -> true;
                case "kit_selection:unlimited" -> false;
                default -> null;
            };
            if (limited == null) return;
            if (consumeForceVote(player)) {
                kitSelectionLimited = limited;
                announceResult(
                    "Kit selection",
                    limited ? "Limited" : "Unlimited"
                );
                beginPhase(LobbyState.KIT_SELECTION);
            } else {
                kitSelectionVotes.put(player.getUniqueId(), limited);
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onPlayerDeath(PlayerDeathEvent event) {
            Player p = event.getPlayer();
            if (
                lobbyState != LobbyState.IN_GAME ||
                !p.getWorld().equals(mapWorld) &&
                !p.getWorld().equals(shadowMap)
            ) return;
            event.getDrops().clear();
            eliminatedPlayers.add(p.getUniqueId());
            p.removeScoreboardTag("fighting");
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!p.isOnline()) {
                        cancel();
                        return;
                    }
                    if (!p.teleport(new Location(world, 0.5, 65, 0.5))) {
                        return;
                    }
                    p.getInventory().clear();
                    p.clearActivePotionEffects();
                    p.removeScoreboardTag("fighting");
                    showSelectedKit(p);
                    cancel();
                }
            }.runTaskTimer(SnapshotPvpPlugin.instance, 0, 1);
        }
    }
}
