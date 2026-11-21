package com.defspacemine.snapshotpvp.lobby;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.gamemode.Gamemode;
import com.defspacemine.snapshotpvp.gamemode.GamemodeRegistry;
import com.defspacemine.snapshotpvp.gamemode.KitRotation;
import com.defspacemine.snapshotpvp.gamemode.GamemodeRegistry.GamemodeDefinition;
import com.defspacemine.snapshotpvp.manakit.ManaKit;
import com.defspacemine.snapshotpvp.manakit.ManaKitListener;
import com.defspacemine.snapshotpvp.map.GameMap;
import com.defspacemine.snapshotpvp.map.GameMapRegistry;
import com.defspacemine.snapshotpvp.map.GameMapRegistry.MapDefinition;
import com.defspacemine.snapshotpvp.map.GameMapRegistry.GamemodeCompatibility;
import java.math.BigInteger;
import java.util.Arrays;
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
import org.bukkit.GameMode;
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
import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
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
        VOTE_TEAM_CHANGE,
        TEAM_SELECTION,
        KIT_SELECTION, // choose kits
        IN_GAME,
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

        private static final int PHASE_DURATION_TICKS = 20 * 20;
        private static final NamespacedKey LOBBY_ITEM = new NamespacedKey(
            "defspacemine",
            "manakit_lobby_item"
        );

        private final World world;
        private final World mapWorld;
        private final World shadowMap;
        private final String teamScope;
        private final BossBar phaseBossBar = BossBar.bossBar(
            Component.text("Waiting - 0:20", NamedTextColor.GOLD),
            1f,
            BossBar.Color.YELLOW,
            BossBar.Overlay.PROGRESS
        );
        private GameMap gameMap;
        private Gamemode gamemode;
        private LobbyState lobbyState = LobbyState.WAITING;
        private int phaseTicksRemaining = PHASE_DURATION_TICKS;
        private MapDefinition selectedMap;
        private GamemodeDefinition selectedMode;
        private int selectedWinCondition = -1;
        private boolean kitSelectionLimited;
        private boolean teamChangeLimited;
        private final Map<UUID, MapDefinition> mapVotes = new HashMap<>();
        private final Map<UUID, GamemodeDefinition> modeVotes = new HashMap<>();
        private final Map<UUID, Integer> configVotes = new HashMap<>();
        private final Map<UUID, Boolean> kitSelectionVotes = new HashMap<>();
        private final Map<UUID, Boolean> teamChangeVotes = new HashMap<>();
        private final Map<UUID, TeamChoice> teamSelections = new HashMap<>();
        private final Map<UUID, String> previousTeamNames = new HashMap<>();
        private final Map<UUID, Inventory> choiceGuis = new HashMap<>();
        private final Set<UUID> skipVotes = new HashSet<>();
        private final Set<UUID> readyPlayers = new HashSet<>();
        private final Set<UUID> forceNextVote = new HashSet<>();
        private final Set<UUID> kitGuiOpened = new HashSet<>();
        private final Set<UUID> lateJoinTeamSelectionAvailable = new HashSet<>();
        private final Set<UUID> matchPlayers = new HashSet<>();
        private final Set<UUID> pendingJoiners = new HashSet<>();
        private final Set<UUID> earlyEndVotes = new HashSet<>();
        private final Set<UUID> eliminatedSpectators = new HashSet<>();
        private int tabUpdateTicks;

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
            clearCombatWorldBlocks();
            SnapshotPvpPlugin.pluginManager.registerEvents(
                this,
                SnapshotPvpPlugin.instance
            );
            broadcast(ChatColor.GOLD + "Lobby phase: Waiting (20 seconds)");
            updatePhaseBossBar();
        }

        private void clearCombatWorldBlocks() {
            for (World combatWorld : List.of(mapWorld, shadowMap)) {
                for (MapDefinition map : GameMapRegistry.options()) {
                    map.clear(combatWorld);
                }
            }
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

        private void updateLobbyTabVisibility() {
            List<Player> lobbyPlayers = getPlayers();
            for (Player viewer : lobbyPlayers) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (lobbyPlayers.contains(other)) {
                        viewer.showPlayer(SnapshotPvpPlugin.instance, other);
                    } else {
                        viewer.hidePlayer(SnapshotPvpPlugin.instance, other);
                    }
                }
            }
        }

        private void updateLobbyTabList() {
            String map = selectedMap == null ? "Voting" : mapName(selectedMap);
            String mode = selectedMode == null ? "Voting" : modeName(selectedMode);
            Component header = Component.text()
                .append(Component.text("MANA KIT", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("Lobby", NamedTextColor.AQUA))
                .build();
            Component footer = Component.text()
                .append(Component.text("Map: ", NamedTextColor.GRAY))
                .append(Component.text(map, NamedTextColor.WHITE))
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Mode: ", NamedTextColor.GRAY))
                .append(Component.text(mode, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Phase: ", NamedTextColor.GRAY))
                .append(Component.text(phaseTitle(lobbyState), NamedTextColor.YELLOW))
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Players: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(getPlayers().size()), NamedTextColor.GREEN))
                .build();

            for (Player player : getPlayers()) {
                player.sendPlayerListHeaderAndFooter(header, footer);
            }
        }

        public void onTick() {
            tabUpdateTicks++;
            if (tabUpdateTicks >= 20) {
                tabUpdateTicks = 0;
                updateLobbyTabList();
            }
            if (
                lobbyState == LobbyState.KIT_SELECTION ||
                lobbyState == LobbyState.IN_GAME
            ) {
                for (Player player : getPlayers()) {
                    if (!player.getScoreboardTags().contains("fighting")) {
                        showSelectedKit(player);
                    }
                }
            }

            if (lobbyState == LobbyState.IN_GAME) {
                if (gamemode != null && gamemode.isOver()) {
                    finishMatch(false);
                    return;
                }

                for (UUID spectatorId : eliminatedSpectators) {
                    Player spectator = Bukkit.getPlayer(spectatorId);
                    if (
                        spectator == null ||
                        !matchPlayers.contains(spectatorId) ||
                        !spectator.getWorld().equals(mapWorld) &&
                        !spectator.getWorld().equals(shadowMap)
                    ) continue;
                    if (spectator.getGameMode() != GameMode.SPECTATOR) {
                        spectator.setGameMode(GameMode.SPECTATOR);
                    }
                    spectator.removeScoreboardTag("fighting");
                    spectator.removeScoreboardTag("combat");
                    spectator
                        .getPersistentDataContainer()
                        .remove(ManaKitListener.MANA_KIT);
                }

                for (Player player : mapWorld.getPlayers()) {
                    if (
                        !matchPlayers.contains(player.getUniqueId()) ||
                        player.isDead() ||
                        player.getGameMode() == GameMode.SPECTATOR
                    ) continue;
                    if (!player.getScoreboardTags().contains("fighting")) {
                        spawnPlayer(player);
                    }
                }
                for (Player player : shadowMap.getPlayers()) {
                    if (
                        !matchPlayers.contains(player.getUniqueId()) ||
                        player.isDead() ||
                        player.getGameMode() == GameMode.SPECTATOR
                    ) continue;
                    if (!player.getScoreboardTags().contains("fighting")) {
                        spawnPlayer(player, shadowMap);
                    }
                }
                boolean shadowCombatantsPresent = shadowMap
                    .getPlayers()
                    .stream()
                    .anyMatch(player ->
                        player.getScoreboardTags().contains("combat") &&
                        player.getScoreboardTags().contains("fighting")
                    );
                if (!shadowCombatantsPresent) {
                    for (Player player : List.copyOf(shadowMap.getPlayers())) {
                        if (player.getGameMode() == GameMode.SPECTATOR) {
                            player.setSpectatorTarget(null);
                            player.teleport(
                                new Location(
                                    mapWorld,
                                    player.getLocation().getX(),
                                    player.getLocation().getY(),
                                    player.getLocation().getZ(),
                                    player.getLocation().getYaw(),
                                    player.getLocation().getPitch()
                                )
                            );
                        }
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
            readyPlayers.clear();
            for (Player player : getPlayers()) {
                player.closeInventory();
                player.hideBossBar(phaseBossBar);
                restorePlayerTeam(player);
            }
            eliminatedSpectators.clear();
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    player.showPlayer(SnapshotPvpPlugin.instance, other);
                }
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
            readyPlayers.remove(playerId);
            pendingJoiners.remove(playerId);
            earlyEndVotes.remove(playerId);
            mapVotes.remove(playerId);
            modeVotes.remove(playerId);
            configVotes.remove(playerId);
            kitSelectionVotes.remove(playerId);
            teamChangeVotes.remove(playerId);
            skipVotes.remove(playerId);
            forceNextVote.remove(playerId);
            kitGuiOpened.remove(playerId);
            eliminatedSpectators.remove(playerId);
            lateJoinTeamSelectionAvailable.remove(playerId);
            choiceGuis.remove(playerId);
            restorePlayerTeam(player);
            player.hideBossBar(phaseBossBar);
            for (Player other : Bukkit.getOnlinePlayers()) {
                player.showPlayer(SnapshotPvpPlugin.instance, other);
                other.showPlayer(SnapshotPvpPlugin.instance, player);
            }
            updateLobbyTabVisibility();
            updateLobbyTabList();
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
            readyPlayers.remove(playerId);
            player.removeScoreboardTag("fighting");
            player.removeScoreboardTag("combat");
            updateLobbyTabVisibility();
            updateLobbyTabList();
            if (lobbyState == LobbyState.IN_GAME) {
                player.hideBossBar(phaseBossBar);
                matchPlayers.add(playerId);
                if (!player.getWorld().equals(world) &&
                    !player.teleport(new Location(world, 0.5, 65, 0.5))) {
                    matchPlayers.remove(playerId);
                    player.sendMessage(
                        ChatColor.RED +
                            "Could not move you to the match lobby."
                    );
                    return;
                }
                if (!teamSelections.containsKey(playerId)) {
                    TeamChoice[] choices = TeamChoice.values();
                    setPlayerTeam(
                        player,
                        choices[
                            ThreadLocalRandom.current().nextInt(choices.length)
                        ]
                    );
                }
                if (kitSelectionLimited) {
                    player
                        .getPersistentDataContainer()
                        .remove(ManaKitListener.MANA_KIT);
                }
                if (ManaKitListener.instance.getPlayerKit(player) == null) {
                    ManaKitListener.instance.selectRandomKit(player);
                }
                player.getInventory().clear();
                player.getInventory().setItem(7, teamChangeItem());
                player.getInventory().setItem(8, kitGuiReopenItem());
                if (kitSelectionLimited) {
                    player.sendMessage(
                        ChatColor.YELLOW +
                            "Choose a team and kit here, or enter the arena " +
                            "with random selections."
                    );
                    openKitSelectionGui(player);
                } else {
                    player.sendMessage(
                        ChatColor.YELLOW +
                            "Choose a team and kit here, or enter the arena " +
                            "with your current selections."
                    );
                }
                return;
            }

            if (lobbyState == LobbyState.KIT_SELECTION) {
                player.showBossBar(phaseBossBar);
                updatePhaseBossBar();
                player.getInventory().clear();
                if (!teamSelections.containsKey(playerId)) {
                    TeamChoice[] choices = TeamChoice.values();
                    setPlayerTeam(
                        player,
                        choices[
                            ThreadLocalRandom.current().nextInt(choices.length)
                        ]
                    );
                }
                if (kitSelectionLimited) {
                    player
                        .getPersistentDataContainer()
                        .remove(ManaKitListener.MANA_KIT);
                }
                setKitGuiReopenItem(player);
                player.getInventory().setItem(7, teamChangeItem());
                if (teamChangeLimited) {
                    lateJoinTeamSelectionAvailable.add(playerId);
                }
                player.sendMessage(
                    ChatColor.YELLOW +
                        "Use /kit or the chest to choose or change your kit; " +
                        "use the team item to choose your team."
                );
                openKitSelectionGui(player);
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
                lobbyState == LobbyState.KIT_SELECTION ||
                isInMatchLobby(player)
            ) &&
                !(
                    gamemode instanceof KitRotation rotation &&
                    kitSelectionLimited &&
                    rotation.getDeaths(player) > 0
                );
        }

        public boolean isKitAvailable(Player player, String kitId) {
            return !(
                gamemode instanceof KitRotation rotation &&
                rotation.hasUsedKit(player, kitId)
            );
        }

        public boolean sendSpectatorToLobby(Player player) {
            UUID playerId = player.getUniqueId();
            if (
                lobbyState != LobbyState.IN_GAME ||
                !matchPlayers.contains(playerId) ||
                !eliminatedSpectators.contains(playerId)
            ) return false;

            player.setSpectatorTarget(null);
            player.removeScoreboardTag("fighting");
            player.removeScoreboardTag("combat");
            player
                .getPersistentDataContainer()
                .remove(ManaKitListener.MANA_KIT);
            if (!player.teleport(new Location(world, 0.5, 65, 0.5))) {
                return false;
            }
            return true;
        }

        public boolean tryOpenKitGui(Player player) {
            return canSelectKit(player);
        }

        private boolean canChangeTeam(Player player) {
            if (lobbyState == LobbyState.TEAM_SELECTION) return true;
            if (lobbyState == LobbyState.IN_GAME) return isInMatchLobby(player);
            if (lobbyState != LobbyState.KIT_SELECTION) return false;
            return (
                !teamChangeLimited ||
                lateJoinTeamSelectionAvailable.contains(player.getUniqueId())
            );
        }

        private boolean isInMatchLobby(Player player) {
            return lobbyState == LobbyState.IN_GAME &&
                matchPlayers.contains(player.getUniqueId()) &&
                player.getWorld().equals(world);
        }

        public void voteToEndMatch(Player player) {
            UUID playerId = player.getUniqueId();
            if (
                lobbyState != LobbyState.IN_GAME ||
                gamemode == null ||
                !gamemode.allowsEarlyEndVote() ||
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
            skipPhase();
        }

        private void showSelectedKit(Player player) {
            ManaKit kit = ManaKitListener.instance.getPlayerKit(player);
            if (kit == null) {
                player.sendActionBar(Component.text("Kit: None"));
                return;
            }
            player.sendActionBar(
                Component.text("Kit: ", NamedTextColor.WHITE)
                    .append(
                        Component.text(
                            kit.getDisplayName(),
                            NamedTextColor.GOLD
                        )
                    )
                    .append(
                        Component.text(
                            " ",
                            NamedTextColor.WHITE
                        )
                    )
                    .append(kit.getStarRatingComponent())
                    .append(
                        Component.text(
                            " - " + kit.getDescription(),
                            NamedTextColor.GRAY
                        )
                    )
            );
        }

        private void spawnPlayer(Player player) {
            spawnPlayer(player, mapWorld);
        }

        private void spawnPlayer(Player player, World targetWorld) {
            if (gameMap == null) return;
            if (
                gamemode instanceof KitRotation rotation &&
                rotation.isEliminated(player)
            ) {
                boolean newlyEliminated =
                    player.getGameMode() != GameMode.SPECTATOR;
                eliminatedSpectators.add(player.getUniqueId());
                player.removeScoreboardTag("fighting");
                player.removeScoreboardTag("combat");
                player.getInventory().clear();
                player
                    .getPersistentDataContainer()
                    .remove(ManaKitListener.MANA_KIT);
                player.setGameMode(GameMode.SPECTATOR);
                player.setSpectatorTarget(null);
                if (newlyEliminated) {
                    player.sendMessage(
                        ChatColor.YELLOW +
                            "You are out of lives and now spectating."
                    );
                }
                return;
            }

            if (gamemode instanceof KitRotation rotation) {
                ManaKit selectedKit = ManaKitListener.instance.getPlayerKit(
                    player
                );
                if (
                    (kitSelectionLimited && rotation.getDeaths(player) > 0) ||
                    selectedKit == null ||
                    !isKitAvailable(player, selectedKit.getId())
                ) {
                    player
                        .getPersistentDataContainer()
                        .remove(ManaKitListener.MANA_KIT);
                    ManaKitListener.instance.selectRandomKit(player);
                }
            }
            Location[] spawnPoints = gameMap.getSpawnPoints();
            Location spawnPoint = spawnPoints[
                ThreadLocalRandom.current().nextInt(spawnPoints.length)
            ];
            player.removeScoreboardTag("combat");
            boolean teleported = player.teleport(
                new Location(
                    targetWorld,
                    spawnPoint.getX(),
                    spawnPoint.getY(),
                    spawnPoint.getZ(),
                    spawnPoint.getYaw(),
                    spawnPoint.getPitch()
                )
            );
            if (!teleported) return;
            player.closeInventory();
            clearLobbyItems(player);
            player.addScoreboardTag("fighting");
            if (targetWorld.equals(mapWorld)) {
                ManaKitListener.instance.enterCombat(player);
            }
        }

        private void beginPhase(LobbyState state) {
            lobbyState = state;
            phaseTicksRemaining = PHASE_DURATION_TICKS;
            readyPlayers.clear();
            skipVotes.clear();
            forceNextVote.clear();
            mapVotes.clear();
            modeVotes.clear();
            configVotes.clear();
            kitSelectionVotes.clear();
            teamChangeVotes.clear();
            choiceGuis.clear();

            if (state == LobbyState.KIT_SELECTION) {
                kitGuiOpened.clear();
                broadcast(
                    ChatColor.GOLD +
                        "Kit selection is open for 20 seconds."
                );
                for (Player player : getPlayers()) {
                    player.closeInventory();
                    player.getInventory().clear();
                    if (kitSelectionLimited) {
                        player
                            .getPersistentDataContainer()
                            .remove(ManaKitListener.MANA_KIT);
                    }
                    setKitGuiReopenItem(player);
                    if (!teamChangeLimited) {
                        player.getInventory().setItem(7, teamChangeItem());
                    }
                    player.sendMessage(
                        ChatColor.YELLOW +
                            "Use /kit or the chest to choose or change your kit."
                    );
                    openKitSelectionGui(player);
                }
                updatePhaseBossBar();
                return;
            }

            for (Player player : getPlayers()) setupPhaseGui(player);
            updatePhaseBossBar();
            broadcast(
                ChatColor.GOLD + "Lobby phase: " + phaseTitle(state) +
                    " (20 seconds)"
            );
        }

        private void setKitGuiReopenItem(Player player) {
            player.getInventory().setItem(8, kitGuiReopenItem());
        }

        private ItemStack kitGuiReopenItem() {
            return lobbyItem(Material.CHEST, "Open kit selection", "kit_gui");
        }

        private ItemStack teamChangeItem() {
            return lobbyItem(
                Material.WHITE_BANNER,
                "Change team",
                "team_gui"
            );
        }

        private void openKitSelectionGui(Player player) {
            if (!kitGuiOpened.add(player.getUniqueId())) return;
            ManaKitListener.instance.showKits(player);
        }

        private void reopenKitSelectionGui(Player player) {
            if (canSelectKit(player)) ManaKitListener.instance.showKits(player);
        }

        private void skipPhase() {
            if (lobbyState == LobbyState.VOTE_KIT_SELECTION) {
                kitSelectionLimited = false;
                announceResult("Kit selection", "Unlimited");
                beginPhase(LobbyState.VOTE_TEAM_CHANGE);
                return;
            }
            if (lobbyState == LobbyState.VOTE_TEAM_CHANGE) {
                teamChangeLimited = false;
                announceResult("Team changes", "Unlimited");
                beginPhase(LobbyState.TEAM_SELECTION);
                return;
            }
            if (lobbyState == LobbyState.KIT_SELECTION) {
                kitSelectionLimited = false;
                beginMatch();
                return;
            }
            advancePhase();
        }

        private void advancePhase() {
            switch (lobbyState) {
                case WAITING -> beginPhase(LobbyState.VOTE_MAP);
                case VOTE_MAP -> {
                    selectedMap = resolveVote(
                        mapVotes,
                        Arrays.asList(GameMapRegistry.options())
                    );
                    announceResult("Map", mapName(selectedMap));
                    beginPhase(LobbyState.VOTE_MODE);
                }
                case VOTE_MODE -> {
                    selectedMode = resolveVote(
                        modeVotes,
                        availableGamemodes()
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
                    kitSelectionLimited = kitSelectionVotes.isEmpty()
                        ? false
                        : resolveVote(
                            kitSelectionVotes,
                            List.of(Boolean.TRUE, Boolean.FALSE)
                        );
                    announceResult(
                        "Kit selection",
                        kitSelectionLimited ? "Limited" : "Unlimited"
                    );
                    beginPhase(LobbyState.VOTE_TEAM_CHANGE);
                }
                case VOTE_TEAM_CHANGE -> {
                    teamChangeLimited = teamChangeVotes.isEmpty()
                        ? false
                        : resolveVote(
                            teamChangeVotes,
                            List.of(Boolean.TRUE, Boolean.FALSE)
                        );
                    announceResult(
                        "Team changes",
                        teamChangeLimited ? "Limited" : "Unlimited"
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

            gameMap = selectedMap.create(this, mapWorld);
            gameMap.setShadowWorld(shadowMap);
            gameMap.onCreate();

            gamemode = selectedMode.create(this, mapWorld);
            selectedMode.applyConfig(gamemode, selectedWinCondition);
            gamemode.setShadowWorld(shadowMap);
            gamemode.onStart();

            lobbyState = LobbyState.IN_GAME;
            matchPlayers.clear();
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
            kitGuiOpened.clear();
            lateJoinTeamSelectionAvailable.clear();
            eliminatedSpectators.clear();
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
                case VOTE_TEAM_CHANGE -> "Team change vote";
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
            String title = lobbyState == LobbyState.WAITING
                ? "Waiting - " + readyPlayers.size() + "/" + getPlayers().size() + " ready"
                : phaseTitle(lobbyState) + " - " + formatTime(ticksRemaining);
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

        private String mapName(MapDefinition map) {
            return map == null ? "Voting" : map.displayName();
        }

        private String modeName(GamemodeDefinition mode) {
            return mode == null ? "Voting" : mode.displayName();
        }

        private String winConditionName(int value) {
            if (
                value == -1 &&
                selectedMode != null &&
                selectedMode.id().equals("kit_rotation")
            ) return "All kits";
            return value == -1
                ? "No limit"
                : value + " " + selectedMode.configLabel();
        }

        private List<Integer> winConditionOptions() {
            if (selectedMap == null || selectedMode == null) return List.of();
            for (GamemodeCompatibility compatibility :
                selectedMap.gamemodes()) {
                if (compatibility.gamemodeId().equals(selectedMode.id())) {
                    return compatibility.winConditions();
                }
            }
            return List.of();
        }

        private List<GamemodeDefinition> availableGamemodes() {
            if (selectedMap == null) return List.of();
            List<GamemodeDefinition> modes = new java.util.ArrayList<>();
            for (GamemodeCompatibility compatibility :
                selectedMap.gamemodes()) {
                GamemodeDefinition mode =
                    GamemodeRegistry.findById(compatibility.gamemodeId());
                if (mode != null) modes.add(mode);
            }
            return modes;
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
            if (lobbyState == LobbyState.TEAM_SELECTION) {
                player.getInventory().setItem(7, teamChangeItem());
            }
            openChoiceGui(player);
        }

        private void openChoiceGui(Player player) {
            if (
                lobbyState == LobbyState.KIT_SELECTION ||
                lobbyState == LobbyState.IN_GAME
            ) return;
            boolean compactChoices =
                lobbyState == LobbyState.VOTE_MAP ||
                lobbyState == LobbyState.VOTE_MODE;
            Inventory gui = Bukkit.createInventory(
                null,
                compactChoices ? 18 : 27,
                phaseTitle(lobbyState)
            );

            switch (lobbyState) {
                case WAITING -> gui.setItem(
                    13,
                    lobbyItem(
                        readyPlayers.contains(player.getUniqueId())
                            ? Material.RED_DYE
                            : Material.LIME_DYE,
                        readyPlayers.contains(player.getUniqueId())
                            ? "Unready"
                            : "Ready up",
                        "ready"
                    )
                );
                case VOTE_MAP -> {
                    MapDefinition[] maps = GameMapRegistry.options();
                    int startSlot = (9 - maps.length) / 2;
                    for (int i = 0; i < maps.length; i++) {
                        MapDefinition map = GameMapRegistry.options()[i];
                        gui.setItem(
                            startSlot + i,
                            choiceItem(
                                player,
                                map.icon(),
                                map.displayName(),
                                map.voteAction()
                            )
                        );
                    }
                }
                case VOTE_MODE -> {
                    List<GamemodeDefinition> modes = availableGamemodes();
                    int startSlot = (9 - modes.size()) / 2;
                    for (int i = 0; i < modes.size(); i++) {
                        GamemodeDefinition mode = modes.get(i);
                        gui.setItem(
                            startSlot + i,
                            choiceItem(
                                player,
                                mode.icon(),
                                mode.displayName(),
                                mode.voteAction()
                            )
                        );
                    }
                }
                case VOTE_CONFIG -> {
                    List<Integer> options = winConditionOptions();
                    for (int i = 0; i < options.size(); i++) {
                        int value = options.get(i);
                        gui.setItem(
                            10 + i * 3,
                            choiceItem(
                                player,
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
                        choiceItem(
                            player,
                            Material.BARRIER,
                            "Limited kit selection",
                            "kit_selection:limited"
                        )
                    );
                    gui.setItem(
                        15,
                        choiceItem(
                            player,
                            Material.CHEST,
                            "Unlimited kit selection",
                            "kit_selection:unlimited"
                        )
                    );
                }
                case VOTE_TEAM_CHANGE -> {
                    gui.setItem(
                        11,
                        choiceItem(
                            player,
                            Material.BARRIER,
                            "Limited team changes",
                            "team_change:limited"
                        )
                    );
                    gui.setItem(
                        15,
                        choiceItem(
                            player,
                            Material.WHITE_BANNER,
                            "Unlimited team changes",
                            "team_change:unlimited"
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
                            choiceItem(
                                player,
                                choice.icon,
                                choice.color + choice.displayName(),
                                "team:" + choice.name()
                            )
                        );
                    }
                }
                case KIT_SELECTION, IN_GAME -> {}
            }

            if (lobbyState != LobbyState.WAITING) {
                gui.setItem(
                    compactChoices
                        ? 13
                        : lobbyState == LobbyState.TEAM_SELECTION ? 25 : 22,
                    lobbyItem(
                        Material.CLOCK,
                        player.isOp()
                            ? "Skip phase"
                            : "Vote to skip this phase",
                        "skip"
                    )
                );
            }
            if (
                player.isOp() &&
                lobbyState != LobbyState.WAITING &&
                lobbyState != LobbyState.TEAM_SELECTION
            ) {
                gui.setItem(
                    compactChoices ? 17 : 26,
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

        private boolean hasMadeChoice(Player player, LobbyState state) {
            UUID playerId = player.getUniqueId();
            return switch (state) {
                case WAITING -> false;
                case VOTE_MAP -> mapVotes.containsKey(playerId);
                case VOTE_MODE -> modeVotes.containsKey(playerId);
                case VOTE_CONFIG -> configVotes.containsKey(playerId);
                case VOTE_KIT_SELECTION ->
                    kitSelectionVotes.containsKey(playerId);
                case VOTE_TEAM_CHANGE ->
                    teamChangeVotes.containsKey(playerId);
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

        private void openTeamSelectionGui(Player player) {
            if (!canChangeTeam(player)) return;
            if (lobbyState == LobbyState.TEAM_SELECTION) {
                reopenChoiceGui(player);
                return;
            }
            if (
                lobbyState != LobbyState.KIT_SELECTION &&
                lobbyState != LobbyState.IN_GAME
            ) return;

            Inventory gui = Bukkit.createInventory(null, 27, "Change team");
            int[] slots = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23 };
            TeamChoice[] choices = TeamChoice.values();
            for (int i = 0; i < choices.length; i++) {
                TeamChoice choice = choices[i];
                gui.setItem(
                    slots[i],
                    choiceItem(
                        player,
                        choice.icon,
                        choice.color + choice.displayName(),
                        "team:" + choice.name()
                    )
                );
            }
            choiceGuis.put(player.getUniqueId(), gui);
            player.openInventory(gui);
        }

        private void clearLobbyItems(Player player) {
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                if (isLobbyItem(player.getInventory().getItem(slot))) {
                    player.getInventory().setItem(slot, null);
                }
            }
        }

        private ItemStack choiceItem(
            Player player,
            Material material,
            String name,
            String action
        ) {
            return lobbyItem(
                material,
                name,
                action,
                isOptionSelected(player, action)
            );
        }

        private boolean isOptionSelected(Player player, String action) {
            UUID playerId = player.getUniqueId();
            if (action.startsWith("config:")) {
                return action.equals("config:" + configVotes.get(playerId));
            }
            if (action.startsWith("team:")) {
                TeamChoice selected = teamSelections.get(playerId);
                return selected != null &&
                    action.equals("team:" + selected.name());
            }
            if (action.startsWith("map:")) {
                MapDefinition selected = mapVotes.get(playerId);
                return selected != null &&
                    action.equals(selected.voteAction());
            }
            if (action.startsWith("mode:")) {
                GamemodeDefinition selected = modeVotes.get(playerId);
                return selected != null &&
                    action.equals(selected.voteAction());
            }

            return switch (action) {
                case "kit_selection:limited" ->
                    Boolean.TRUE.equals(kitSelectionVotes.get(playerId));
                case "kit_selection:unlimited" ->
                    Boolean.FALSE.equals(kitSelectionVotes.get(playerId));
                case "team_change:limited" ->
                    Boolean.TRUE.equals(teamChangeVotes.get(playerId));
                case "team_change:unlimited" ->
                    Boolean.FALSE.equals(teamChangeVotes.get(playerId));
                default -> false;
            };
        }

        private ItemStack lobbyItem(
            Material material,
            String name,
            String action
        ) {
            return lobbyItem(material, name, action, false);
        }

        private ItemStack lobbyItem(
            Material material,
            String name,
            String action,
            boolean selected
        ) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            String displayName = selected
                ? ChatColor.stripColor(name)
                : name;
            meta.setDisplayName(
                (selected ? ChatColor.GREEN : ChatColor.GOLD) +
                    displayName +
                    (selected ? " [Selected]" : "")
            );
            String lore = selected
                ? "Already selected."
                : switch (action) {
                    case "reopen" -> "Click, right-click, or drop to reopen.";
                    case "ready" -> "Click to ready up or cancel readiness.";
                    case "kit_gui" ->
                        "Click, right-click, or drop to open kit selection.";
                    case "team_gui" ->
                        "Click, right-click, or drop to change teams.";
                    default -> "Click or right-click to vote.";
                };
            meta.setLore(
                List.of((selected ? ChatColor.GREEN : ChatColor.GRAY) + lore)
            );
            meta.getPersistentDataContainer().set(
                LOBBY_ITEM,
                PersistentDataType.STRING,
                action
            );
            item.setItemMeta(meta);
            return item;
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onLobbyItemInteract(PlayerInteractEvent event) {
            Player player = event.getPlayer();
            if (event.getHand() == EquipmentSlot.OFF_HAND ||
                !isLobbyWorld(player.getWorld())) return;
            ItemStack item = event.getItem();
            if (item == null) item = player.getInventory().getItemInMainHand();
            if (item == null || !item.hasItemMeta()) return;

            String action = item.getItemMeta()
                .getPersistentDataContainer()
                .get(LOBBY_ITEM, PersistentDataType.STRING);
            if (action == null) return;

            event.setCancelled(true);
            handleLobbyItem(player, action);
        }

        @EventHandler
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
                    guiAction.startsWith("team:") &&
                    clickedState != LobbyState.TEAM_SELECTION
                ) {
                    player.closeInventory();
                } else if (
                    lobbyState == clickedState &&
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

        @EventHandler
        public void onSpectatorStartSpectating(
            PlayerStartSpectatingEntityEvent event
        ) {
            Player spectator = event.getPlayer();
            if (
                spectator.getGameMode() != GameMode.SPECTATOR ||
                !matchPlayers.contains(spectator.getUniqueId())
            ) return;
            if (
                !(event.getNewSpectatorTarget() instanceof Player target) ||
                !matchPlayers.contains(target.getUniqueId()) ||
                !target.getScoreboardTags().contains("fighting") ||
                !target.getScoreboardTags().contains("combat") ||
                !isLobbyWorld(target.getWorld())
            ) event.setCancelled(true);
        }

        @EventHandler(ignoreCancelled = true)
        public void onLobbyItemDrop(PlayerDropItemEvent event) {
            Player player = event.getPlayer();
            if (!isLobbyWorld(player.getWorld())) return;
            String action = lobbyItemAction(event.getItemDrop().getItemStack());
            if (action == null) return;
            event.setCancelled(true);
            if (
                action.equals("reopen") ||
                action.equals("kit_gui") ||
                action.equals("team_gui")
            ) {
                handleLobbyItem(player, action);
            }
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
            if (action.equals("ready")) {
                if (lobbyState != LobbyState.WAITING) return;
                if (!readyPlayers.add(playerId)) readyPlayers.remove(playerId);
                boolean ready = readyPlayers.contains(playerId);
                List<Player> players = getPlayers();
                long readyCount = players
                    .stream()
                    .filter(candidate ->
                        readyPlayers.contains(candidate.getUniqueId())
                    )
                    .count();
                player.sendMessage(
                    (ready ? ChatColor.GREEN : ChatColor.YELLOW) +
                        (ready ? "You are ready" : "You are no longer ready") +
                        " (" +
                        readyCount +
                        "/" +
                        players.size() +
                        ")."
                );
                Inventory gui = choiceGuis.get(playerId);
                if (gui != null) {
                    gui.setItem(
                        13,
                        lobbyItem(
                            ready ? Material.RED_DYE : Material.LIME_DYE,
                            ready ? "Unready" : "Ready up",
                            "ready"
                        )
                    );
                }
                updatePhaseBossBar();
                if (
                    !players.isEmpty() &&
                    readyCount == players.size()
                ) {
                    broadcast(
                        ChatColor.GREEN +
                            "Everyone is ready. Lobby setup is starting."
                    );
                    beginPhase(LobbyState.VOTE_MAP);
                }
                return;
            }
            if (action.equals("skip")) {
                if (player.isOp()) {
                    skipCurrentPhase(player);
                    return;
                }
                if (lobbyState == LobbyState.IN_GAME) return;
                if (lobbyState == LobbyState.WAITING) return;
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
                if (skipVotes.size() * 2 > getPlayers().size()) skipPhase();
                return;
            }
            if (action.equals("reopen")) {
                reopenChoiceGui(player);
                return;
            }
            if (action.equals("kit_gui")) {
                reopenKitSelectionGui(player);
                return;
            }
            if (action.equals("team_gui")) {
                openTeamSelectionGui(player);
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
                LobbyState phase = lobbyState;
                player.closeInventory();
                Bukkit.getScheduler().runTask(
                    SnapshotPvpPlugin.instance,
                    () -> {
                        if (
                            player.isOnline() &&
                            lobbyState == phase &&
                            isLobbyWorld(player.getWorld())
                        ) openChoiceGui(player);
                    }
                );
                return;
            }

            switch (lobbyState) {
                case VOTE_MAP -> voteForMap(player, action);
                case VOTE_MODE -> voteForMode(player, action);
                case VOTE_CONFIG -> voteForConfig(player, action);
                case VOTE_KIT_SELECTION -> voteForKitSelection(player, action);
                case VOTE_TEAM_CHANGE -> voteForTeamChange(player, action);
                case TEAM_SELECTION -> {}
                default -> {}
            }
            if (action.startsWith("team:") && canChangeTeam(player)) {
                voteForTeam(player, action);
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
            team.setAllowFriendlyFire(false);
            team.setColor(choice.color);
            team.addEntry(player.getName());
            teamSelections.put(playerId, choice);
            if (
                lobbyState == LobbyState.KIT_SELECTION &&
                teamChangeLimited
            ) {
                lateJoinTeamSelectionAvailable.remove(playerId);
                player.getInventory().setItem(7, null);
            }
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
            String id = action.startsWith("map:")
                ? action.substring("map:".length())
                : "";
            MapDefinition choice = GameMapRegistry.findById(id);
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
            String id = action.startsWith("mode:")
                ? action.substring("mode:".length())
                : "";
            GamemodeDefinition choice = GamemodeRegistry.findById(id);
            if (
                choice == null ||
                availableGamemodes().stream().noneMatch(
                    available -> available.id().equals(choice.id())
                )
            ) return;
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
                beginPhase(LobbyState.VOTE_TEAM_CHANGE);
            } else {
                kitSelectionVotes.put(player.getUniqueId(), limited);
            }
        }

        private void voteForTeamChange(Player player, String action) {
            Boolean limited = switch (action) {
                case "team_change:limited" -> true;
                case "team_change:unlimited" -> false;
                default -> null;
            };
            if (limited == null) return;
            if (consumeForceVote(player)) {
                teamChangeLimited = limited;
                announceResult(
                    "Team changes",
                    limited ? "Limited" : "Unlimited"
                );
                beginPhase(LobbyState.TEAM_SELECTION);
            } else {
                teamChangeVotes.put(player.getUniqueId(), limited);
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
            p.removeScoreboardTag("fighting");
            p.removeScoreboardTag("combat");
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
                    p.getInventory().setItem(7, teamChangeItem());
                    p.getInventory().setItem(8, kitGuiReopenItem());
                    // p.sendMessage(
                    //     ChatColor.YELLOW +
                    //         "Reselect your team or kit here, then enter the " +
                    //         "arena to rejoin."
                    // );
                    if (
                        lobbyState == LobbyState.KIT_SELECTION ||
                        lobbyState == LobbyState.IN_GAME
                    ) showSelectedKit(p);
                    cancel();
                }
            }.runTaskTimer(SnapshotPvpPlugin.instance, 0, 1);
        }
    }
}
