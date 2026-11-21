package com.defspacemine.snapshotpvp.gamemode;

import com.defspacemine.snapshotpvp.lobby.ManaKitLobbyType.ManaKitLobbyInstance;
import org.bukkit.World;
import org.bukkit.Material;

public final class GamemodeRegistry {

    public interface GamemodeFactory {
        Gamemode create(ManaKitLobbyInstance lobby, World world);
    }

    public interface GamemodeConfigurer {
        void configure(Gamemode gamemode, int winCondition);
    }

    public record GamemodeDefinition(
        String id,
        String displayName,
        Material icon,
        String configLabel,
        GamemodeFactory factory,
        GamemodeConfigurer configurer
    ) {
        public String voteAction() {
            return "mode:" + id;
        }

        public Gamemode create(ManaKitLobbyInstance lobby, World world) {
            return factory.create(lobby, world);
        }

        public void applyConfig(Gamemode gamemode, int winCondition) {
            configurer.configure(gamemode, winCondition);
        }
    }

    public static final GamemodeDefinition[] GAMEMODES = {
        new GamemodeDefinition(
            "deathmatch",
            "Deathmatch",
            Material.IRON_SWORD,
            "kills",
            Deathmatch::new,
            (gamemode, winCondition) ->
                ((Deathmatch) gamemode).killsRequired = winCondition
        ),
        new GamemodeDefinition(
            "streakmatch",
            "Streakmatch",
            Material.BLAZE_POWDER,
            "streak",
            Streakmatch::new,
            (gamemode, winCondition) ->
                ((Streakmatch) gamemode).streakRequired = winCondition
        ),
        new GamemodeDefinition(
            "kit_rotation",
            "Kit Rotation",
            Material.SPECTRAL_ARROW,
            "lives",
            KitRotation::new,
            (gamemode, winCondition) ->
                ((KitRotation) gamemode).lives = winCondition
        )
    };

    private GamemodeRegistry() {}

    public static GamemodeDefinition findById(String id) {
        for (GamemodeDefinition gamemode : GAMEMODES) {
            if (gamemode.id().equals(id)) return gamemode;
        }
        return null;
    }
}
