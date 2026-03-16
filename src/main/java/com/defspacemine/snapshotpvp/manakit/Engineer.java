package com.defspacemine.snapshotpvp.manakit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;

public class Engineer extends ManaKit {
    final NamespacedKey fuelCounter = ManaKitListener.MANA_KIT_DATA0;
    final int bot = 25; // 25 attacks per bots
    final NamespacedKey botChargeCounter = ManaKitListener.MANA_KIT_DATA1;
    final int tpCooldown = 200; // 10 seconds between teleport
    final NamespacedKey tpCooldownCounter = ManaKitListener.MANA_KIT_DATA2;
    final int tpRestockTimer = 200; // 10 seconds per teleporter item
    final NamespacedKey tpRestockCounter = ManaKitListener.MANA_KIT_DATA3;

    final NamespacedKey teleA_UUID = ManaKitListener.MANA_KIT_DATASTR0;
    final NamespacedKey teleB_UUID = ManaKitListener.MANA_KIT_DATASTR1;
    final NamespacedKey teleToggleKey = ManaKitListener.MANA_KIT_DATABOOL0;

    private ItemStack trident;
    private final ItemStack droid;
    private final ItemStack sentry;
    private final ItemStack teleporterItem;

    public Engineer() {
        super("engineer", "Engineer", "[Utility Armada]", 0);

        trident = new ItemStack(Material.TRIDENT);
        ItemMeta meta = trident.getItemMeta();
        meta.addEnchant(Enchantment.LOYALTY, 3, true);
        meta.addEnchant(Enchantment.CHANNELING, 1, true);
        meta.setUnbreakable(true);
        trident.setItemMeta(meta);

        droid = new ItemStack(Material.WITHER_SKELETON_SPAWN_EGG);
        ItemMeta dMeta = droid.getItemMeta();
        dMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, false);
        dMeta.setDisplayName(ChatColor.DARK_GRAY + "Droid");
        dMeta.getPersistentDataContainer().set(CustomEggListener.CUSTOM_EGG, PersistentDataType.STRING,
                "engineer_droid");
        droid.setItemMeta(dMeta);

        sentry = new ItemStack(Material.SKELETON_SPAWN_EGG);
        ItemMeta sMeta = sentry.getItemMeta();
        sMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, false);
        sMeta.setDisplayName(ChatColor.GRAY + "Sentry");
        sMeta.getPersistentDataContainer().set(CustomEggListener.CUSTOM_EGG, PersistentDataType.STRING,
                "engineer_sentry");
        sentry.setItemMeta(sMeta);

        teleporterItem = new ItemStack(Material.BEACON);
        ItemMeta tMeta = teleporterItem.getItemMeta();
        tMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, false);
        tMeta.setDisplayName(ChatColor.AQUA + "Teleporter Node");
        teleporterItem.setItemMeta(tMeta);
    }

    @Override
    public void onCombatTick(Player p) {
        for (Entity e : p.getWorld().getEntities())
            if (p.equals(CustomEggListener.getOwner(e)))
                if (!SnapshotPvpPlugin.getTeam(p).equals(SnapshotPvpPlugin.getTeamE(e)))
                    SnapshotPvpPlugin.addToTeam(p, e);

        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int fuelC = pdc.getOrDefault(fuelCounter, PersistentDataType.INTEGER, 0);
        int botChargeC = pdc.getOrDefault(botChargeCounter, PersistentDataType.INTEGER, 0);
        int tpCooldownC = pdc.getOrDefault(tpCooldownCounter, PersistentDataType.INTEGER, 0);
        int tpRestockC = pdc.getOrDefault(tpRestockCounter, PersistentDataType.INTEGER, 0);

        if (!p.isOnGround() && p.isSneaking() && fuelC > 0 && !p.isFlying()) {
            Vector vel = p.getVelocity();
            p.setVelocity(vel.add(new Vector(0, 0.2, 0)));

            pdc.set(fuelCounter, PersistentDataType.INTEGER, fuelC - 1);
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().subtract(0, 0.2, 0), 2, 0.1, 0, 0.1, 0.05, null);

            if (p.getTicksLived() % 10 == 0)
                p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.4f, 1.2f);

            p.setFallDistance(0);
        }

        if (killstreak >= 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
        if (killstreak >= 3 && !p.hasPotionEffect(PotionEffectType.REGENERATION))
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, -1, 0));

        PlayerInventory inv = p.getInventory();
        if (botChargeC >= bot) {
            SnapshotPvpPlugin.clearInv(inv, Material.WITHER_SKELETON_SPAWN_EGG);
            SnapshotPvpPlugin.clearInv(inv, Material.SKELETON_SPAWN_EGG);
            inv.addItem(CustomEggListener.injectOwner(droid, p));
            inv.addItem(CustomEggListener.injectOwner(sentry, p));
            pdc.set(botChargeCounter, PersistentDataType.INTEGER, 0);
        }

        if (tpRestockC >= tpRestockTimer) {
            SnapshotPvpPlugin.clearInv(inv, Material.BEACON);
            inv.addItem(teleporterItem);
            pdc.set(tpRestockCounter, PersistentDataType.INTEGER, 0);
        } else if (!inv.contains(Material.BEACON))
            pdc.set(tpRestockCounter, PersistentDataType.INTEGER, tpRestockC + 1 * (killstreak >= 2 ? 2 : 1));

        if (tpCooldownC < tpCooldown)
            pdc.set(tpCooldownCounter, PersistentDataType.INTEGER, tpCooldownC + 1 * (killstreak >= 2 ? 2 : 1));
        else
            checkTeamTeleportation(p, new ArrayList<Player>());

        showNodeVisuals(p, tpCooldownC >= tpCooldown);

        ChatColor fuelColor;
        if (fuelC > 60)
            fuelColor = ChatColor.GREEN;
        else if (fuelC > 25)
            fuelColor = ChatColor.YELLOW;
        else
            fuelColor = ChatColor.RED;
        String cdDisplay = tpCooldownC >= tpCooldown ? ChatColor.GREEN + "READY"
                : ChatColor.RED + "" + tpCooldownC + "/" + tpCooldown;
        p.sendActionBar(ChatColor.YELLOW + "Jetpack: " + fuelColor + fuelC + "%" +
                ChatColor.GRAY + " | " + ChatColor.DARK_GRAY + "Bots: " + ChatColor.WHITE + botChargeC + "/" + bot +
                ((getEntityFromPDC(pdc, teleA_UUID) != null && getEntityFromPDC(pdc, teleB_UUID) != null)
                        ? ChatColor.GRAY + " | " + ChatColor.AQUA + "TP: " + cdDisplay
                        : "")
                +
                (inv.contains(Material.BEACON) ? ""
                        : ChatColor.GRAY + " | " + ChatColor.DARK_AQUA + "TPNode: " +
                                ChatColor.WHITE + tpRestockC + "/" + tpRestockTimer)
                +
                ChatColor.GRAY + " | " + ChatColor.RED + "Killstreak: " + ChatColor.WHITE + killstreak + "/3");
    }

    private void showNodeVisuals(Player engineer, boolean ready) {
        PersistentDataContainer pdc = engineer.getPersistentDataContainer();

        Entity entA = getEntityFromPDC(pdc, teleA_UUID);
        if (entA != null) {
            DustOptions red = new Particle.DustOptions(ready ? Color.RED : Color.GRAY, 1.2f);
            entA.getWorld().spawnParticle(Particle.DUST, entA.getLocation().add(0, 1.2, 0), 3, 0.1, 0.1, 0.1, 0, red);
        }

        Entity entB = getEntityFromPDC(pdc, teleB_UUID);
        if (entB != null) {
            DustOptions blue = new Particle.DustOptions(ready ? Color.BLUE : Color.GRAY, 1.2f);
            entB.getWorld().spawnParticle(Particle.DUST, entB.getLocation().add(0, 1.2, 0), 3, 0.1, 0.1, 0.1, 0, blue);
        }
    }

    private void checkTeamTeleportation(Player engineer, List<Player> alreadyTeleported) {
        PersistentDataContainer engPdc = engineer.getPersistentDataContainer();

        Entity entA = getEntityFromPDC(engPdc, teleA_UUID);
        Entity entB = getEntityFromPDC(engPdc, teleB_UUID);
        double distanceSq = 1 + alreadyTeleported.size() * 2;
        distanceSq *= distanceSq;

        if (entA == null || entB == null)
            return;

        for (Player nearby : engineer.getWorld().getPlayers()) {
            if (nearby.equals(engineer)
                    || SnapshotPvpPlugin.getTeam(engineer).equals(SnapshotPvpPlugin.getTeam(nearby))) {
                if (alreadyTeleported.contains(nearby))
                    continue;

                if (nearby.getLocation().distanceSquared(entA.getLocation()) < distanceSq) {
                    performTeleport(engineer, nearby, entB.getLocation());
                    alreadyTeleported.add(nearby);
                    checkTeamTeleportation(engineer, alreadyTeleported);
                    return;
                } else if (nearby.getLocation().distanceSquared(entB.getLocation()) < distanceSq) {
                    performTeleport(engineer, nearby, entA.getLocation());
                    alreadyTeleported.add(nearby);
                    checkTeamTeleportation(engineer, alreadyTeleported);
                    return;
                }
            }
        }
    }

    private Entity getEntityFromPDC(PersistentDataContainer pdc, NamespacedKey key) {
        if (!pdc.has(key, PersistentDataType.STRING))
            return null;
        try {
            UUID id = UUID.fromString(pdc.get(key, PersistentDataType.STRING));
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid()) {
                return null;
            }
            return e;
        } catch (Exception ex) {
            return null;
        }
    }

    private void performTeleport(Player engineer, Player target, Location destination) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 0));
        target.teleport(destination.clone().add(0, 1.1, 0).setDirection(target.getLocation().getDirection()));
        target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.1f);
        target.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0, 1, 0), 20, 0.2, 0.5, 0.2, 0.1,
                null);
        engineer.getPersistentDataContainer().set(tpCooldownCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(fuelCounter, PersistentDataType.INTEGER, 50);
        pdc.set(botChargeCounter, PersistentDataType.INTEGER, 0);
        pdc.set(tpCooldownCounter, PersistentDataType.INTEGER, 0);
        pdc.set(tpRestockCounter, PersistentDataType.INTEGER, 0);
        pdc.set(teleToggleKey, PersistentDataType.BOOLEAN, false);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(fuelCounter, PersistentDataType.INTEGER, 100);
        pdc.set(botChargeCounter, PersistentDataType.INTEGER, bot);
        pdc.set(tpCooldownCounter, PersistentDataType.INTEGER, 0);
        pdc.set(tpRestockCounter, PersistentDataType.INTEGER, tpRestockTimer);
        pdc.set(teleToggleKey, PersistentDataType.BOOLEAN, false);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (p.equals(CustomEggListener.getOwner(e.getEntity())))
            return;
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        pdc.set(botChargeCounter, PersistentDataType.INTEGER,
                pdc.getOrDefault(botChargeCounter, PersistentDataType.INTEGER, 0) + 1 * (killstreak >= 2 ? 2 : 1));

        DamageType d = e.getDamageSource().getDamageType();
        if (d != DamageType.PLAYER_ATTACK && d != DamageType.TRIDENT)
            return;
        pdc.set(fuelCounter, PersistentDataType.INTEGER,
                Math.min(100,
                        pdc.getOrDefault(fuelCounter, PersistentDataType.INTEGER, 0) + 5 * (killstreak >= 2 ? 2 : 1)));
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(fuelCounter, PersistentDataType.INTEGER, 100);
        pdc.set(botChargeCounter, PersistentDataType.INTEGER, bot);
    }

    @Override
    public void onLeaveCombat(Player p) {
        cleanupNodes(p);
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.BEACON);
        SnapshotPvpPlugin.clearInv(inv, Material.WITHER_SKELETON_SPAWN_EGG);
        SnapshotPvpPlugin.clearInv(inv, Material.SKELETON_SPAWN_EGG);
        if (!inv.contains(Material.TRIDENT) &&
                inv.getItemInOffHand().getType() != Material.TRIDENT)
            retrieveTrident(p);
        p.clearActivePotionEffects();
        for (Entity e : p.getWorld().getEntities())
            if (p.equals(CustomEggListener.getOwner(e)))
                e.remove();

        resetKit(p);
    }

    private void retrieveTrident(Player p) {
        p.getInventory().addItem(trident);
        for (Entity e : p.getWorld().getEntities())
            if (e instanceof Trident t)
                if (t.getOwnerUniqueId().equals(p.getUniqueId()))
                    t.remove();
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.BEACON)
            return;

        e.setCancelled(true);
        placeTeleNode(p, e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(0.5, 0, 0.5));
        if (p.getGameMode() != GameMode.CREATIVE)
            item.setAmount(item.getAmount() - 1);
    }

    public void placeTeleNode(Player p, Location loc) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        boolean toggle = pdc.getOrDefault(teleToggleKey, PersistentDataType.BOOLEAN, false);
        NamespacedKey targetKey = toggle ? teleB_UUID : teleA_UUID;

        Entity old = getEntityFromPDC(pdc, targetKey);
        if (old != null)
            old.remove();

        ArmorStand node = (ArmorStand) loc.getWorld().spawnEntity(loc.subtract(0, 0.5, 0), EntityType.ARMOR_STAND);
        node.setInvisible(true);
        node.setGravity(false);
        node.setMarker(true);
        node.setCustomName((toggle ? ChatColor.BLUE : ChatColor.RED) + p.getName() + "'s Node " + (toggle ? "B" : "A"));
        node.setCustomNameVisible(true);

        pdc.set(targetKey, PersistentDataType.STRING, node.getUniqueId().toString());
        pdc.set(teleToggleKey, PersistentDataType.BOOLEAN, !toggle);
        pdc.set(tpCooldownCounter, PersistentDataType.INTEGER, 0);

        p.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f);
        loc.getWorld().spawnParticle(Particle.WAX_ON, loc.add(0, 0.5, 0), 10, 0.2, 0.2, 0.2, 0.1, null);
    }

    private void cleanupNodes(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Entity a = getEntityFromPDC(pdc, teleA_UUID);
        if (a != null)
            a.remove();

        Entity b = getEntityFromPDC(pdc, teleB_UUID);
        if (b != null)
            b.remove();
    }
}
