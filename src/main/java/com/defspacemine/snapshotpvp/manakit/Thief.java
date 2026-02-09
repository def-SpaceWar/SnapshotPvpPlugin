package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public class Thief extends ManaKit {
    final int ammoRestock = 10; // 10 attacks for 3 ammo (it resets itself)
    final NamespacedKey ammoRestockCounter = ManaKitListener.MANA_KIT_DATA0;
    final int smokeRestock = 5; // 5 hits below 6 hearts gives smoke bomb
    final int smokeBombRadius = 8;
    final NamespacedKey smokeRestockCounter = ManaKitListener.MANA_KIT_DATA1;
    final int pearlRestock = 1000; // 1 enderpearl every 50 seconds
    final NamespacedKey pearlRestockCounter = ManaKitListener.MANA_KIT_DATA2;

    private ItemStack ammo;
    private ItemStack smokeBomb;
    private ItemStack enderPearl;

    public Thief() {
        super("thief", "Thief", "[Melee Assassin]", 3);

        ammo = new ItemStack(Material.TIPPED_ARROW, 3);
        ammo.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
        PotionMeta meta = (PotionMeta) ammo.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + "Ammo");
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        meta.setColor(Color.fromRGB(0xFFCC44));
        meta.addCustomEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                1600,
                5,
                false,
                true,
                true), true);
        ammo.setItemMeta(meta);

        smokeBomb = new ItemStack(Material.CHORUS_FRUIT, 1);
        smokeBomb.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);

        enderPearl = new ItemStack(Material.ENDER_PEARL, 1);
        enderPearl.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
    }

    @Override
    public void giveKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        resetKit(p);

        // give items
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(ammoRestockCounter, PersistentDataType.INTEGER, 0);
        pdc.set(smokeRestockCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int ammoRestockC = pdc.get(ammoRestockCounter, PersistentDataType.INTEGER);
        int smokeRestockC = pdc.get(smokeRestockCounter, PersistentDataType.INTEGER);
        int pearlRestockC = pdc.get(pearlRestockCounter, PersistentDataType.INTEGER);

        String displayMessage = ChatColor.GOLD + "Ammo Restock: " + ChatColor.WHITE + ammoRestockC + "/" + ammoRestock
                + ChatColor.GRAY + " | "
                + ChatColor.DARK_AQUA + "Pearl Restock: " + ChatColor.WHITE + pearlRestockC + "/" + pearlRestock
                + ChatColor.GRAY + " | "
                + ChatColor.RED + "Killstreak: " + ChatColor.WHITE + killstreak + "/4";

        PlayerInventory inv = p.getInventory();
        if (p.getHealth() >= 12)
            pdc.set(smokeRestockCounter, PersistentDataType.INTEGER, 0);
        if (inv.contains(Material.CHORUS_FRUIT) ||
                inv.getItemInOffHand().getType() == Material.CHORUS_FRUIT) {
            displayMessage = ChatColor.LIGHT_PURPLE + "Smoke Bomb!"
                    + ChatColor.GRAY + " | " + displayMessage;
        } else if (p.getHealth() < 12) {
            displayMessage = ChatColor.DARK_PURPLE + "Smoke Bomb: " + ChatColor.WHITE + smokeRestockC + "/"
                    + smokeRestock
                    + ChatColor.GRAY + " | " + displayMessage;
        }

        p.sendActionBar(displayMessage);

        if (killstreak == 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
        else if (killstreak == 2) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0));
        } else if (killstreak >= 3) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0));
        }

        if (killstreak >= 4)
            pdc.set(pearlRestockCounter, PersistentDataType.INTEGER, pearlRestockC + 2);
        else
            pdc.set(pearlRestockCounter, PersistentDataType.INTEGER, pearlRestockC + 1);

        if (ammoRestockC >= ammoRestock) {
            SnapshotPvpPlugin.clearInv(inv, Material.TIPPED_ARROW);
            p.getInventory().addItem(ammo);
            pdc.set(ammoRestockCounter, PersistentDataType.INTEGER, 0);
        }

        if (smokeRestockC >= smokeRestock) {
            SnapshotPvpPlugin.clearInv(inv, Material.CHORUS_FRUIT);
            p.getInventory().addItem(smokeBomb);
            pdc.set(smokeRestockCounter, PersistentDataType.INTEGER, 0);
        }

        if (pearlRestockC >= pearlRestock) {
            SnapshotPvpPlugin.clearInv(inv, Material.ENDER_PEARL);
            p.getInventory().addItem(enderPearl);
            pdc.set(pearlRestockCounter, PersistentDataType.INTEGER, 0);
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.TIPPED_ARROW);
        SnapshotPvpPlugin.clearInv(inv, Material.CHORUS_FRUIT);
        SnapshotPvpPlugin.clearInv(inv, Material.ENDER_PEARL);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ammoRestockCounter, PersistentDataType.INTEGER, ammoRestock);
        pdc.set(smokeRestockCounter, PersistentDataType.INTEGER, 0);
        pdc.set(pearlRestockCounter, PersistentDataType.INTEGER, pearlRestock);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) {
            int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
            PersistentDataContainer pdc = p.getPersistentDataContainer();

            if (killstreak >= 4)
                pdc.set(ammoRestockCounter, PersistentDataType.INTEGER,
                        pdc.get(ammoRestockCounter, PersistentDataType.INTEGER) + 2);
            else
                pdc.set(ammoRestockCounter, PersistentDataType.INTEGER,
                        pdc.get(ammoRestockCounter, PersistentDataType.INTEGER) + 1);
        }
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        if (p.getHealth() >= 12)
            return;

        PlayerInventory inv = p.getInventory();
        if (inv.contains(Material.CHORUS_FRUIT) ||
                inv.getItemInOffHand().getType() == Material.CHORUS_FRUIT)
            return;

        if (e.getDamageSource().getCausingEntity() instanceof Player) {
            int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
            PersistentDataContainer pdc = p.getPersistentDataContainer();

            if (killstreak >= 4)
                pdc.set(ammoRestockCounter, PersistentDataType.INTEGER,
                        pdc.get(ammoRestockCounter, PersistentDataType.INTEGER) + 2);
            else
                pdc.set(smokeRestockCounter, PersistentDataType.INTEGER,
                        pdc.get(smokeRestockCounter, PersistentDataType.INTEGER) + 1);
        }
    }

    @Override
    public void onConsume(Player p, PlayerItemConsumeEvent e) {
        {
            ItemStack item = e.getItem();
            if (item.getType() != Material.CHORUS_FRUIT)
                return;
        }

        p.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED,
                160,
                2,
                false,
                false,
                true));
        p.addPotionEffect(new PotionEffect(
                PotionEffectType.HASTE,
                160,
                2,
                false,
                false,
                true));

        for (Player target : p.getWorld().getNearbyPlayers(p.getLocation(), smokeBombRadius)) {
            if (target.equals(p) || target.getGameMode() != GameMode.ADVENTURE)
                continue;

            Team eaterTeam = SnapshotPvpPlugin.scoreboard.getEntryTeam(p.getName());
            Team targetTeam = SnapshotPvpPlugin.scoreboard.getEntryTeam(target.getName());

            if (eaterTeam != null && eaterTeam.equals(targetTeam))
                continue;

            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.BLINDNESS,
                    300,
                    0,
                    false,
                    false,
                    true));
        }

        p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation(), 80,
                2, 1, 2, 0.02);
    }
}
