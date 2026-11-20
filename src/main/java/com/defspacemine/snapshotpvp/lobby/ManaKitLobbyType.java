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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
        private final Set<UUID> skipVotes = new HashSet<>();
        private final Set<UUID> forceNextVote = new HashSet<>();
        private final Set<UUID> kitGuiOpened = new HashSet<>();
        private final Set<UUID> matchPlayers = new HashSet<>();
        private final Set<UUID> pendingJoiners = new HashSet<>();
        private final Set<UUID> earlyEndVotes = new HashSet<>();
        private final Set<UUID> eliminatedPlayers = new HashSet<>();

        public ManaKitLobbyInstance(World world) {
            this.world = world;
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
                    finishMatch(true);
                    return;
                }
                if (gameMap != null) gameMap.onTick();
                if (gamemode != null) gamemode.onTick();
                return;
            }

            phaseTicksRemaining--;
            if (phaseTicksRemaining <= 0) advancePhase();
        }

        public void onDestroy() {
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
            if (
                lobbyState == LobbyState.IN_GAME &&
                !pendingJoiners.isEmpty() &&
                (
                    matchPlayers.isEmpty() ||
                    earlyEndVotes.containsAll(matchPlayers)
                )
            ) finishMatch(true);
        }

        @Override
        public void onLobbyJoin(Player player) {
            UUID playerId = player.getUniqueId();
            player.removeScoreboardTag("fighting");
            player.removeScoreboardTag("combat");
            if (lobbyState == LobbyState.IN_GAME) {
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
                if (ManaKitListener.instance.getPlayerKit(player) == null) {
                    ManaKitListener.instance.selectRandomKit(player);
                }
                spawnPlayer(player);
                return;
            }

            if (lobbyState == LobbyState.KIT_SELECTION) {
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

            setupVotingInventory(player);
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
                return;
            }

            for (Player player : getPlayers()) setupVotingInventory(player);
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
                    beginPhase(LobbyState.KIT_SELECTION);
                }
                case KIT_SELECTION -> beginMatch();
                case IN_GAME -> {}
            }
        }

        private void beginMatch() {
            for (Player player : getPlayers()) {
                player.closeInventory();
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
            }
            destroyMatch();
            matchPlayers.clear();
            pendingJoiners.clear();
            earlyEndVotes.clear();
            eliminatedPlayers.clear();
            kitGuiOpened.clear();
            beginPhase(LobbyState.WAITING);
            if (early) {
                broadcast(
                    ChatColor.YELLOW +
                        "The match ended early by unanimous vote. A new match will begin soon."
                );
            }
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
                case KIT_SELECTION -> "Kit selection";
                case IN_GAME -> "In game";
            };
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

        private void setupVotingInventory(Player player) {
            player.getInventory().clear();
            switch (lobbyState) {
                case WAITING -> {}
                case VOTE_MAP -> {
                    player.getInventory().setItem(
                        20,
                        lobbyItem(
                            Material.GRASS_BLOCK,
                            "Bob Lands",
                            "map:bob_lands"
                        )
                    );
                    player.getInventory().setItem(
                        24,
                        lobbyItem(
                            Material.IRON_SWORD,
                            "Bob Invasion Remaster",
                            "map:bob_invasion_remaster"
                        )
                    );
                }
                case VOTE_MODE -> {
                    player.getInventory().setItem(
                        20,
                        lobbyItem(
                            Material.IRON_SWORD,
                            "Deathmatch",
                            "mode:deathmatch"
                        )
                    );
                    player.getInventory().setItem(
                        24,
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
                        player.getInventory().setItem(
                            20 + i * 2,
                            lobbyItem(
                                Material.PAPER,
                                winConditionName(value),
                                "config:" + value
                            )
                        );
                    }
                }
                case VOTE_KIT_SELECTION -> {
                    player.getInventory().setItem(
                        20,
                        lobbyItem(
                            Material.BARRIER,
                            "Limited kit selection",
                            "kit_selection:limited"
                        )
                    );
                    player.getInventory().setItem(
                        24,
                        lobbyItem(
                            Material.CHEST,
                            "Unlimited kit selection",
                            "kit_selection:unlimited"
                        )
                    );
                }
                case KIT_SELECTION, IN_GAME -> {}
            }

            if (
                lobbyState != LobbyState.KIT_SELECTION &&
                lobbyState != LobbyState.IN_GAME
            ) {
                player.getInventory().setItem(
                    45,
                    lobbyItem(Material.CLOCK, "Vote to skip this phase", "skip")
                );
                if (player.isOp() && lobbyState != LobbyState.WAITING) {
                    player.getInventory().setItem(
                        53,
                        lobbyItem(
                            Material.NETHER_STAR,
                            "Force next choice",
                            "force"
                        )
                    );
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
            if (!isLobbyWorld(player.getWorld())) return;
            String action = lobbyItemAction(event.getCurrentItem());
            if (action != null) {
                event.setCancelled(true);
                handleLobbyItem(player, action);
                return;
            }
            if (isLobbyItem(event.getCursor())) event.setCancelled(true);
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
                if (lobbyState == LobbyState.IN_GAME) return;
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
                default -> {}
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
