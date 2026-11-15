package com.defspacemine.snapshotpvp.manakit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public abstract class ManaKit {

    private static final String MAP_SUFFIX = "_map";
    private static final String SHADOW_SUFFIX = "_map_shadow";

    public static final NamespacedKey SHADOW_WORLD_TIMER = new NamespacedKey(
        "defspacemine",
        "shadow_world_timer"
    );

    protected final String id;
    protected final String displayName;
    protected final String description;
    protected final int stars;

    public ManaKit(
        String id,
        String displayName,
        String description,
        int stars
    ) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.stars = stars;
    }

    public final String getId() {
        return id;
    }

    public final String getDisplayName() {
        return displayName;
    }

    public final String getDescription() {
        return description;
    }

    public static boolean isInShadowWorld(Player player) {
        return player.getWorld().getName().endsWith(SHADOW_SUFFIX);
    }

    public static int getShadowWorldTimer(Player player) {
        return player
            .getPersistentDataContainer()
            .getOrDefault(SHADOW_WORLD_TIMER, PersistentDataType.INTEGER, 0);
    }

    public static void setShadowWorldTimer(Player player, int ticks) {
        if (ticks < 0) throw new IllegalArgumentException(
            "Shadow world timer cannot be negative"
        );
        player
            .getPersistentDataContainer()
            .set(SHADOW_WORLD_TIMER, PersistentDataType.INTEGER, ticks);
    }

    public static boolean enterShadowWorld(Player player, int durationTicks) {
        if (durationTicks <= 0) throw new IllegalArgumentException(
            "Shadow world duration must be positive"
        );

        String normalWorldName = player.getWorld().getName();
        if (
            isInShadowWorld(player) || !normalWorldName.endsWith(MAP_SUFFIX)
        ) return false;

        World shadowWorld = Bukkit.getWorld(normalWorldName + "_shadow");
        if (shadowWorld == null) throw new IllegalStateException(
            "Shadow world world is not loaded: " +
            normalWorldName +
            "_shadow"
        );

        Location current = player.getLocation();
        boolean teleported = player.teleport(
            new Location(
                shadowWorld,
                current.getX(),
                current.getY(),
                current.getZ(),
                current.getYaw(),
                current.getPitch()
            )
        );
        if (!teleported) return false;

        setShadowWorldTimer(player, durationTicks);
        return true;
    }

    public static boolean returnFromShadowWorld(Player player) {
        if (!isInShadowWorld(player)) return false;

        String shadowWorldName = player.getWorld().getName();
        String normalWorldName = shadowWorldName.substring(
            0,
            shadowWorldName.length() - "_shadow".length()
        );
        World normalWorld = Bukkit.getWorld(normalWorldName);
        if (normalWorld == null) throw new IllegalStateException(
            "Normal map world is not loaded: " + normalWorldName
        );

        Location current = player.getLocation();
        boolean teleported = player.teleport(
            new Location(
                normalWorld,
                current.getX(),
                current.getY(),
                current.getZ(),
                current.getYaw(),
                current.getPitch()
            )
        );
        if (!teleported) return false;

        player.getPersistentDataContainer().remove(SHADOW_WORLD_TIMER);
        return true;
    }

    protected final boolean canUseTeleportAbility(
        Player player,
        Location destination
    ) {
        return (
            destination.getWorld() != null &&
            player.getWorld().equals(destination.getWorld())
        );
    }

    protected final void removeOwnedTridents(Player player) {
        World currentWorld = player.getWorld();
        String normalWorldName = currentWorld.getName();
        if (normalWorldName.endsWith(SHADOW_SUFFIX)) {
            normalWorldName = normalWorldName.substring(
                0,
                normalWorldName.length() - SHADOW_SUFFIX.length()
            );
        }

        if (!normalWorldName.endsWith(MAP_SUFFIX)) {
            removeOwnedTridents(currentWorld, player);
            return;
        }

        World normalWorld = Bukkit.getWorld(normalWorldName);
        World shadowWorld = Bukkit.getWorld(normalWorldName + SHADOW_SUFFIX);
        if (normalWorld != null) removeOwnedTridents(normalWorld, player);
        if (shadowWorld != null) removeOwnedTridents(shadowWorld, player);
    }

    private void removeOwnedTridents(World world, Player player) {
        for (Entity entity : world.getEntities()) {
            if (
                entity instanceof Trident trident &&
                player.getUniqueId().equals(trident.getOwnerUniqueId())
            ) trident.remove();
        }
    }

    public void giveKit(Player p) {}

    public void resetKit(Player p) {}

    public void onCombatTick(Player p) {}

    public void onShadowTick(Player p) {
        p.sendActionBar(
            Component.text("Shadow World: ", NamedTextColor.LIGHT_PURPLE).append(
                Component.text(
                    getShadowWorldTimer(p),
                    NamedTextColor.WHITE
                )
            )
        );
    }

    public void onIdleTick(Player p) {}

    public void onDeath(Player p, PlayerDeathEvent e) {}

    public void onDeathShadow(Player p, PlayerDeathEvent e) {}

    public void onKill(Player p, PlayerDeathEvent e) {}

    public void onKillShadow(Player p, PlayerDeathEvent e) {}

    public void onEnterCombat(Player p) {}

    public void onLeaveCombat(Player p) {}

    public void onDamageInWorld(
        Player p,
        Player attacker,
        Entity victim,
        EntityDamageByEntityEvent e
    ) {}

    public void onDamageInWorldShadow(
        Player p,
        Player attacker,
        Entity victim,
        EntityDamageByEntityEvent e
    ) {}

    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {}

    public void onDamageDealtShadow(Player p, EntityDamageByEntityEvent e) {}

    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {}

    public void onDamageTakenShadow(Player p, EntityDamageByEntityEvent e) {}

    public void onInteract(Player p, PlayerInteractEvent e) {}

    public void onInteractShadow(Player p, PlayerInteractEvent e) {}

    public void onInteractEntity(Player p, PlayerInteractEntityEvent e) {}

    public void onInteractEntityShadow(Player p, PlayerInteractEntityEvent e) {}

    public void onConsume(Player p, PlayerItemConsumeEvent e) {}

    public void onConsumeShadow(Player p, PlayerItemConsumeEvent e) {}

    public void onProjectileHit(Player p, ProjectileHitEvent e) {}

    public void onProjectileHitShadow(Player p, ProjectileHitEvent e) {}

    public void onShootBow(Player p, EntityShootBowEvent e) {}

    public void onShootBowShadow(Player p, EntityShootBowEvent e) {}

    public void onToggleGlide(Player p, EntityToggleGlideEvent e) {}

    public void onToggleGlideShadow(Player p, EntityToggleGlideEvent e) {}

    protected final void playItemGainFeedback(Player p) {
        playFeedback(p, 12, 0.9f);
    }

    protected final void playAbilityFeedback(Player p) {
        playFeedback(p, 24, 1.0f);
    }

    private void playFeedback(Player p, int particleCount, float volume) {
        Location center = p.getLocation().add(0, 1, 0);
        p.getWorld().spawnParticle(
            Particle.DUST,
            center,
            particleCount,
            0.35,
            0.45,
            0.35,
            0,
            new Particle.DustOptions(feedbackColor(), 1.2f)
        );
        p.playSound(center, feedbackSound(), volume, feedbackPitch());
    }

    private Color feedbackColor() {
        return switch (id) {
            case "atlantian_prince", "tridentite", "voodoo" -> Color.fromRGB(
                0x28BEDC
            );
            case
                "berserk",
                "barbarian",
                "violence",
                "buckshot_viking" -> Color.fromRGB(0xDC2D2D);
            case "colossus", "juggernaut", "shulkian", "titan" -> Color.fromRGB(
                0x9169CD
            );
            case
                "frog",
                "jade_trio",
                "cactus_champion",
                "poacher",
                "slime_king" -> Color.fromRGB(0x46DC69);
            case
                "icbm",
                "fire_in_the_hole",
                "incendiary",
                "hellbound_ronin",
                "headless_horseman" -> Color.fromRGB(0xFF7D23);
            case
                "mercenary",
                "japanese_goblin",
                "ranger",
                "deadeye" -> Color.fromRGB(0xFFCD46);
            case "pharmacist" -> Color.fromRGB(0x50E1D2);
            case "cultist" -> Color.fromRGB(0x914BBE);
            case "light_paladin" -> Color.fromRGB(0xFFDC50);
            case
                "squire",
                "breezy",
                "monk",
                "hacker",
                "engineer",
                "two_timer" -> Color.fromRGB(0x50AFFF);
            case "gambler", "thief" -> Color.fromRGB(0xFFC341);
            case "jockey" -> Color.fromRGB(0xA07346);
            case "sculk_warrior" -> Color.fromRGB(0x2DCDBE);
            default -> Color.fromRGB(0xBE96FF);
        };
    }

    private Sound feedbackSound() {
        return switch (id) {
            case
                "atlantian_prince",
                "tridentite",
                "voodoo" -> Sound.ENTITY_DOLPHIN_SPLASH;
            case
                "berserk",
                "barbarian",
                "violence",
                "buckshot_viking",
                "colossus",
                "juggernaut" -> Sound.ENTITY_PLAYER_ATTACK_SWEEP;
            case
                "icbm",
                "fire_in_the_hole",
                "incendiary",
                "hellbound_ronin",
                "headless_horseman" -> Sound.ENTITY_FIREWORK_ROCKET_BLAST;
            case
                "frog",
                "jade_trio",
                "cactus_champion",
                "poacher",
                "slime_king",
                "breezy" -> Sound.BLOCK_GRASS_PLACE;
            case "pharmacist" -> Sound.BLOCK_BREWING_STAND_BREW;
            case "cultist" -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case
                "mercenary",
                "japanese_goblin",
                "ranger",
                "deadeye",
                "shulkian" -> Sound.ENTITY_ARROW_HIT_PLAYER;
            case "sculk_warrior" -> Sound.ENTITY_WARDEN_HEARTBEAT;
            case
                "hacker",
                "engineer",
                "two_timer",
                "gambler",
                "thief",
                "monk" -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case "jockey" -> Sound.ENTITY_HORSE_AMBIENT;
            case "squire", "light_paladin" -> Sound.ENTITY_PLAYER_LEVELUP;
            case "titan" -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            default -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        };
    }

    private float feedbackPitch() {
        return switch (id) {
            case "atlantian_prince", "tridentite", "voodoo" -> 1.2f;
            case
                "berserk",
                "barbarian",
                "violence",
                "buckshot_viking",
                "colossus",
                "juggernaut" -> 0.8f;
            case
                "icbm",
                "fire_in_the_hole",
                "incendiary",
                "hellbound_ronin",
                "headless_horseman" -> 0.9f;
            default -> 1.0f;
        };
    }

    /**
     * Whether a player using this kit may drop the given item.
     * Defaults to false; override to allow specific items.
     */
    public boolean canDropItem(Player p, ItemStack item) {
        return false;
    }

    /**
     * Whether a player using this kit may craft the given result item.
     * Defaults to false (nothing is craftable). Kits such as a Soup kit can
     * override this, e.g. {@code return result.getType() == Material.MUSHROOM_STEW;}
     */
    public boolean canCraft(Player p, ItemStack result) {
        return false;
    }

    // add more later
}
