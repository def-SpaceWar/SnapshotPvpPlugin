package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public class Titan extends ManaKit {
    private static final NamespacedKey FURY_SPEED = new NamespacedKey("defspacemine", "titan_speed");
    private static final NamespacedKey FURY_ATTACK_SPEED = new NamespacedKey("defspacemine", "titan_attack_speed");
    private static final NamespacedKey FURY_ATTACK_DAMAGE = new NamespacedKey("defspacemine", "titan_attack_damage");
    final NamespacedKey furyCounter = ManaKitListener.MANA_KIT_DATA0;

    private int getStage(int fury) {
        if (fury < 200)
            return 0;
        if (fury < 600)
            return 1;
        if (fury < 1200)
            return 2;
        if (fury < 2000)
            return 3;
        if (fury < 3000)
            return 4;
        return 5;
    }

    private void setFuryAttributes(Player p, int stage, int furyC) {
        AttributeInstance speedAttr = p.getAttribute(Attribute.MOVEMENT_SPEED);
        AttributeInstance attackSpeedAttr = p.getAttribute(Attribute.ATTACK_SPEED);
        AttributeInstance attackDamageAttr = p.getAttribute(Attribute.ATTACK_DAMAGE);

        if (speedAttr == null || attackSpeedAttr == null || attackDamageAttr == null)
            return;

        speedAttr.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(FURY_SPEED))
                .forEach(speedAttr::removeModifier);

        attackSpeedAttr.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(FURY_ATTACK_SPEED))
                .forEach(attackSpeedAttr::removeModifier);

        attackDamageAttr.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(FURY_ATTACK_DAMAGE))
                .forEach(attackDamageAttr::removeModifier);

        if (stage <= 0)
            return;

        double speedBonus = 0.02 * stage;
        double attackSpeedBonus = 0.5 * stage;

        AttributeModifier speedMod = new AttributeModifier(
                FURY_SPEED,
                speedBonus,
                AttributeModifier.Operation.ADD_NUMBER);

        AttributeModifier attackSpeedMod = new AttributeModifier(
                FURY_ATTACK_SPEED,
                attackSpeedBonus,
                AttributeModifier.Operation.ADD_NUMBER);

        speedAttr.addModifier(speedMod);
        attackSpeedAttr.addModifier(attackSpeedMod);

        if (stage < 5)
            return;

        AttributeModifier attackDamageMod = new AttributeModifier(
                FURY_ATTACK_DAMAGE,
                furyC / 500 - 5,
                AttributeModifier.Operation.ADD_NUMBER);
        attackDamageAttr.addModifier(attackDamageMod);
    }

    public Titan() {
        super("titan", "Titan", "[Melee Damage Tank]", 2);
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
        pdc.set(furyCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int furyC = pdc.get(furyCounter, PersistentDataType.INTEGER);
        int stage = getStage(furyC);
        setFuryAttributes(p, stage, furyC);

        String displayMessage = ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/2";

        String color = ChatColor.WHITE.toString();
        Location loc = p.getLocation().add(0, 1, 0);
        World world = p.getWorld();
        Color dustColor = Color.WHITE;

        switch (stage) {
            case 0:
                color = ChatColor.GRAY.toString();
                dustColor = Color.BLACK;
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 5));
                break;
            case 1:
                color = ChatColor.GREEN.toString();
                dustColor = Color.GREEN;
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 3));
                break;
            case 2:
                color = ChatColor.YELLOW.toString();
                dustColor = Color.YELLOW;
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 3));
                break;
            case 3:
                color = ChatColor.GOLD.toString();
                dustColor = Color.ORANGE;
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 3));
                break;
            case 4:
                color = ChatColor.RED.toString();
                dustColor = Color.RED;
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 3));
                break;
            case 5:
                color = ChatColor.WHITE.toString() + ChatColor.BOLD.toString();
                dustColor = Color.AQUA;
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 3));
                break;
        }

        DustOptions dust = new DustOptions(dustColor, 1.5f);
        world.spawnParticle(Particle.DUST, loc, stage + 1, .2, .2, .2, stage + 1, dust);

        displayMessage = ChatColor.DARK_RED + "Fury: " +
                color + furyC + ChatColor.RESET +
                ChatColor.GRAY + "  |  " + displayMessage;
        p.sendActionBar(displayMessage);

        pdc.set(furyCounter, PersistentDataType.INTEGER, Math.max(furyC - stage - 1, 0));
        if (killstreak >= 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
    }

    @Override
    public void onLeaveCombat(Player p) {
        setFuryAttributes(p, 0, 0);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(furyCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (p.getInventory().getItem(EquipmentSlot.HAND).getType() != Material.STONE_AXE)
            return;
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        pdc.set(furyCounter, PersistentDataType.INTEGER,
                pdc.get(furyCounter, PersistentDataType.INTEGER) + ((killstreak > 1) ? 160 : 80));
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        pdc.set(furyCounter, PersistentDataType.INTEGER,
                pdc.get(furyCounter, PersistentDataType.INTEGER) + ((killstreak > 1) ? 100 : 50));
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int furyC = pdc.get(furyCounter, PersistentDataType.INTEGER);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, -1, getStage(furyC)));
        pdc.set(furyCounter, PersistentDataType.INTEGER, 0);
    }
}
