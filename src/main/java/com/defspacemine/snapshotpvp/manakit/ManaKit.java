package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
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
