package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

public abstract class ManaKit {

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

    public void giveKit(Player p) {}

    public void resetKit(Player p) {}

    public void onCombatTick(Player p) {}

    public void onIdleTick(Player p) {}

    public void onDeath(Player p, PlayerDeathEvent e) {}

    public void onKill(Player p, PlayerDeathEvent e) {}

    public void onEnterCombat(Player p) {}

    public void onLeaveCombat(Player p) {}

    public void onDamageInWorld(Player p, Player attacker, Entity victim, EntityDamageByEntityEvent e) {}

    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {}

    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {}

    public void onInteract(Player p, PlayerInteractEvent e) {}

    public void onInteractEntity(Player p, PlayerInteractEntityEvent e) {}

    public void onConsume(Player p, PlayerItemConsumeEvent e) {}

    public void onProjectileHit(Player p, ProjectileHitEvent e) {}

    public void onShootBow(Player p, EntityShootBowEvent e) {}

    public void onToggleGlide(Player p, EntityToggleGlideEvent e) {}

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
            case "atlantian_prince", "tridentite", "voodoo" -> Color.fromRGB(40, 190, 220);
            case "berserk", "barbarian", "violence", "buckshot_viking" -> Color.fromRGB(220, 45, 45);
            case "colossus", "juggernaut", "shulkian", "titan" -> Color.fromRGB(145, 105, 205);
            case "frog", "jade_trio", "cactus_champion", "poacher", "slime_king" -> Color.fromRGB(70, 220, 105);
            case "icbm", "fire_in_the_hole", "incendiary", "hellbound_ronin", "headless_horseman" -> Color.fromRGB(255, 125, 35);
            case "mercenary", "japanese_goblin", "ranger", "deadeye" -> Color.fromRGB(255, 205, 70);
            case "pharmacist" -> Color.fromRGB(80, 225, 210);
            case "cultist" -> Color.fromRGB(145, 75, 190);
            case "light_paladin", "squire" -> Color.fromRGB(255, 220, 80);
            case "breezy", "monk", "hacker", "engineer", "two_timer" -> Color.fromRGB(80, 175, 255);
            case "gambler", "thief" -> Color.fromRGB(255, 195, 65);
            case "jockey" -> Color.fromRGB(160, 115, 70);
            case "sculk_warrior" -> Color.fromRGB(45, 205, 190);
            default -> Color.fromRGB(190, 150, 255);
        };
    }

    private Sound feedbackSound() {
        return switch (id) {
            case "atlantian_prince", "tridentite", "voodoo" -> Sound.ENTITY_DOLPHIN_SPLASH;
            case "berserk", "barbarian", "violence", "buckshot_viking", "colossus", "juggernaut" -> Sound.ENTITY_PLAYER_ATTACK_SWEEP;
            case "icbm", "fire_in_the_hole", "incendiary", "hellbound_ronin", "headless_horseman" -> Sound.ENTITY_FIREWORK_ROCKET_BLAST;
            case "frog", "jade_trio", "cactus_champion", "poacher", "slime_king", "breezy" -> Sound.BLOCK_GRASS_PLACE;
            case "pharmacist" -> Sound.BLOCK_BREWING_STAND_BREW;
            case "cultist" -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case "mercenary", "japanese_goblin", "ranger", "deadeye", "shulkian" -> Sound.ENTITY_ARROW_HIT_PLAYER;
            case "sculk_warrior" -> Sound.ENTITY_WARDEN_HEARTBEAT;
            case "hacker", "engineer", "two_timer", "gambler", "thief", "monk" -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case "jockey" -> Sound.ENTITY_HORSE_AMBIENT;
            case "squire", "light_paladin" -> Sound.ENTITY_PLAYER_LEVELUP;
            case "titan" -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            default -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        };
    }

    private float feedbackPitch() {
        return switch (id) {
            case "atlantian_prince", "tridentite", "voodoo" -> 1.2f;
            case "berserk", "barbarian", "violence", "buckshot_viking", "colossus", "juggernaut" -> 0.8f;
            case "icbm", "fire_in_the_hole", "incendiary", "hellbound_ronin", "headless_horseman" -> 0.9f;
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
