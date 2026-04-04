package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Breezy extends ManaKit {
    private final int glide = 200;
    private final NamespacedKey glideCounter = ManaKitListener.MANA_KIT_DATA0;

    public Breezy() {
        super("breezy", "Breezy", "[Melee Movement]", 2);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

		ManaKitListener.giveItemsFromShulker(p, "goopshotpeshvp", -185, 7, -185);
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(glideCounter, PersistentDataType.INTEGER, glide);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int glideC = pdc.getOrDefault(glideCounter, PersistentDataType.INTEGER, 0);

        if (p.isGliding())
            glideC = Math.max(0, glideC - 1);
        pdc.set(glideCounter, PersistentDataType.INTEGER, glideC);
        updateChestplateGlider(p, glideC > 0);

        p.sendActionBar(ChatColor.AQUA + "Glide: " +
                ChatColor.WHITE + glideC + "/" + glide +
                ChatColor.GRAY + "  |  " +
                ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/2");

        if (killstreak >= 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
        if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 0));
    }

    private void updateChestplateGlider(Player p, boolean canGlide) {
        ItemStack chest = p.getInventory().getChestplate();
        ArmorMeta meta = (ArmorMeta) chest.getItemMeta();
        if (meta.isGlider() == canGlide)
            return;
        meta.setGlider(canGlide);
        chest.setItemMeta(meta);
    }

    @Override
    public void onLeaveCombat(Player p) {
        p.clearActivePotionEffects();
        resetKit(p);
        updateChestplateGlider(p, true);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (e.getDamageSource().getDamageType() != DamageType.PLAYER_ATTACK
                || !(e.getEntity() instanceof Player))
            return;
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(glideCounter, PersistentDataType.INTEGER,
                Math.min(glide, pdc.get(glideCounter, PersistentDataType.INTEGER) + 20));
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(glideCounter, PersistentDataType.INTEGER, glide);
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, -1, 1));
    }
}
