package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public abstract class ManaKit {
    protected final String id;
    protected final String displayName;
    protected final String description;

    public ManaKit(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public abstract void giveKit(Player p);

    public abstract void resetKit(Player p);

    public abstract void onCombatTick(Player p);

    public abstract void onIdleTick(Player p);

    public abstract void onDeath(Player p, PlayerDeathEvent e);

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
