package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public abstract class ManaKit {
    protected final String id;
    protected final String displayName;
    protected final String description;
    protected final int stars;

    public ManaKit(String id, String displayName, String description, int stars) {
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

    public void giveKit(Player p) {
    }

    public void resetKit(Player p) {
    }

    public void onCombatTick(Player p) {
    }

    public void onIdleTick(Player p) {
    }

    public void onDeath(Player p, PlayerDeathEvent e) {
    }

    public void onKill(Player p, PlayerDeathEvent e) {
    }

    public void onEnterCombat(Player p) {
    }

    public void onLeaveCombat(Player p) {
    }

    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
    }

    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
    }

    public void onInteract(Player p, PlayerInteractEvent e) {
    }

    // add more later
}
