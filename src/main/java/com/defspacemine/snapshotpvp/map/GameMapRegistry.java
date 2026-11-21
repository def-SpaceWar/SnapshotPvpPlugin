package com.defspacemine.snapshotpvp.map;

import com.defspacemine.snapshotpvp.gamemode.GamemodeRegistry;
import com.defspacemine.snapshotpvp.lobby.LobbyType.LobbyInstance;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Material;
import org.bukkit.World;
import java.util.List;

public final class GameMapRegistry {

    public interface MapFactory {
        GameMap create(LobbyInstance lobby, World world);
    }

    public record GamemodeCompatibility(
        String gamemodeId,
        List<Integer> winConditions
    ) {}

    public record MapDefinition(
        String id,
        String displayName,
        Material icon,
        BlockVector3 worldMin,
        BlockVector3 worldMax,
        BlockVector3 worldLocation,
        List<GamemodeCompatibility> gamemodes,
        MapFactory factory
    ) {
        public MapDefinition {
            if (gamemodes == null || gamemodes.isEmpty()) {
                throw new IllegalArgumentException(
                    "Map " + id + " must support at least one gamemode"
                );
            }
            for (GamemodeCompatibility compatibility : gamemodes) {
                if (
                    GamemodeRegistry.findById(compatibility.gamemodeId()) == null
                ) {
                    throw new IllegalArgumentException(
                        "Map " + id + " references unknown gamemode " +
                        compatibility.gamemodeId()
                    );
                }
                if (
                    compatibility.winConditions() == null ||
                    compatibility.winConditions().isEmpty()
                ) {
                    throw new IllegalArgumentException(
                        "Map " + id + " has no win conditions for gamemode " +
                        compatibility.gamemodeId()
                    );
                }
            }
        }

        public void clear(World combatWorld) {
            GameMapUtil.removeMap(
                worldMin,
                worldMax,
                combatWorld,
                worldLocation
            );
        }

        public GameMap create(LobbyInstance lobby, World world) {
            return factory.create(lobby, world);
        }

        public String voteAction() {
            return "map:" + id;
        }
    }

    public static final MapDefinition[] MAPS = {
        new MapDefinition(
            "bob_lands",
            "Bob Lands",
            Material.GRASS_BLOCK,
            BobLands.WORLD_MIN,
            BobLands.WORLD_MAX,
            BobLands.worldLoc,
            List.of(
                new GamemodeCompatibility("deathmatch", List.of(-1, 10, 20)),
                new GamemodeCompatibility("streakmatch", List.of(-1, 5, 10)),
                new GamemodeCompatibility("kit_rotation", List.of(5, 10, -1))
            ),
            BobLands::new
        ),
        new MapDefinition(
            "bob_invasion_remaster",
            "Bob Invasion Remaster",
            Material.IRON_SWORD,
            BobInvasionRemaster.WORLD_MIN,
            BobInvasionRemaster.WORLD_MAX,
            BobInvasionRemaster.worldLoc,
            List.of(
                new GamemodeCompatibility("deathmatch", List.of(-1, 10, 20)),
                new GamemodeCompatibility("streakmatch", List.of(-1, 5, 10)),
                new GamemodeCompatibility("kit_rotation", List.of(5, 10, -1))
            ),
            BobInvasionRemaster::new
        )
    };

    private GameMapRegistry() {}

    public static MapDefinition findById(String id) {
        for (MapDefinition map : MAPS) {
            if (map.id().equals(id)) return map;
        }
        return null;
    }

    public static MapDefinition[] options() {
        return MAPS;
    }
}
