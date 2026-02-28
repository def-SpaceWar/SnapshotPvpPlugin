package com.defspacemine.snapshotpvp.manakit;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

import java.util.Random;

public class Gambler extends ManaKit {

    // ── Dice Roll: one potion every 15 s (300 ticks) ──────────────────────
    final int diceRollRestock = 300;
    final NamespacedKey diceRollCounter = ManaKitListener.MANA_KIT_DATA0;

    // ── Lucky Charm: charges on hits ──────────────────────────────────────
    final int luckyStrikeCharges = 15;
    final NamespacedKey luckyStrikeCounter = ManaKitListener.MANA_KIT_DATA1;

    // ── All-In Token: one token every 60 s (1200 ticks) ──────────────────
    final int allInRestock = 1200;
    final NamespacedKey allInCounter = ManaKitListener.MANA_KIT_DATA2;

    // ── Slot-Machine combo (passive) ──────────────────────────────────────
    final NamespacedKey slotMachineCombo = ManaKitListener.MANA_KIT_DATA3;

    // ── Explosive Cards: 3 cards every 30 s (600 ticks) ──────────────────
    final int explosiveCardRestock = 600;
    final NamespacedKey explosiveCardCounter = ManaKitListener.MANA_KIT_DATA4;

    private final Random random;

    private final ItemStack diceRollPotion;
    private final ItemStack luckyCharm;
    private final ItemStack allInToken;
    private final ItemStack explosiveCard;

    // ─────────────────────────────────────────────────────────────────────
    public Gambler() {
        super("gambler", "Gambler", "[Hell no]", 5);
        this.random = new Random();

        // Dice Roll Potion — gold colour; real effect applied in onConsume
        diceRollPotion = new ItemStack(Material.POTION);
        PotionMeta diceMeta = (PotionMeta) diceRollPotion.getItemMeta();
        diceMeta.setDisplayName(ChatColor.GOLD + "🎲 Dice Roll");
        diceMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        diceMeta.setColor(Color.fromRGB(0xFFD700));
        // Dummy 1-tick effect so Bukkit renders the colour
        diceMeta.addCustomEffect(new PotionEffect(
                PotionEffectType.LUCK, 1, 0, false, false, false), true);
        diceRollPotion.setItemMeta(diceMeta);

        // Lucky Charm
        luckyCharm = new ItemStack(Material.GHAST_TEAR, 1);
        luckyCharm.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
        ItemMeta charmMeta = luckyCharm.getItemMeta();
        charmMeta.setDisplayName(ChatColor.GREEN + "🍀 Lucky Charm");
        luckyCharm.setItemMeta(charmMeta);

        // All-In Token
        allInToken = new ItemStack(Material.NETHER_STAR, 1);
        allInToken.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
        ItemMeta allInMeta = allInToken.getItemMeta();
        allInMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "💎 All-In Token");
        allInToken.setItemMeta(allInMeta);

        // Explosive Card (Paper stack of 3)
        explosiveCard = new ItemStack(Material.PAPER, 3);
        explosiveCard.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
        ItemMeta cardMeta = explosiveCard.getItemMeta();
        cardMeta.setDisplayName(ChatColor.RED + "🃏 Explosive Card");
        explosiveCard.setItemMeta(cardMeta);
    }

    // ─────────────────────────────────────────────────────────────────────
    @Override
    public void giveKit(Player p) {
        resetKit(p);
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT,  PersistentDataType.STRING,  this.id);
        pdc.set(diceRollCounter,           PersistentDataType.INTEGER, 0);
        pdc.set(luckyStrikeCounter,        PersistentDataType.INTEGER, 0);
        pdc.set(allInCounter,              PersistentDataType.INTEGER, 0);
        pdc.set(slotMachineCombo,          PersistentDataType.INTEGER, 0);
        pdc.set(explosiveCardCounter,      PersistentDataType.INTEGER, 0);
    }

    // ─────────────────────────────────────────────────────────────────────
    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        int diceRollC      = pdc.get(diceRollCounter,       PersistentDataType.INTEGER);
        int luckyStrikeC   = pdc.get(luckyStrikeCounter,    PersistentDataType.INTEGER);
        int allInC         = pdc.get(allInCounter,           PersistentDataType.INTEGER);
        int explosiveCardC = pdc.get(explosiveCardCounter,   PersistentDataType.INTEGER);
        int combo          = pdc.get(slotMachineCombo,       PersistentDataType.INTEGER);

        PlayerInventory inv = p.getInventory();

        // ── Cooldown display (convert ticks → seconds remaining) ──────────
        int diceSeconds = Math.max(0, (diceRollRestock      - diceRollC)      / 20);
        int cardSeconds = Math.max(0, (explosiveCardRestock - explosiveCardC) / 20);
        int allInSecs   = Math.max(0, (allInRestock         - allInC)         / 20);

        String diceDisplay  = diceRollC      >= diceRollRestock
                ? ChatColor.GREEN        + "🎲 READY"
                : ChatColor.GOLD         + "🎲 " + diceSeconds + "s";

        String cardDisplay  = explosiveCardC >= explosiveCardRestock
                ? ChatColor.GREEN + "🃏 READY"
                : ChatColor.RED   + "🃏 " + cardSeconds + "s";

        String allInDisplay = allInC         >= allInRestock
                ? ChatColor.GREEN        + "💎 READY"
                : ChatColor.LIGHT_PURPLE + "💎 " + allInSecs + "s";

        String luckyDisplay = ChatColor.AQUA   + "🍀 " + luckyStrikeC + "/" + luckyStrikeCharges;
        String comboDisplay = ChatColor.YELLOW + "Combo " + ChatColor.WHITE + combo;

        String bar = diceDisplay
                + ChatColor.GRAY + " | " + cardDisplay
                + ChatColor.GRAY + " | " + allInDisplay
                + ChatColor.GRAY + " | " + luckyDisplay
                + ChatColor.GRAY + " | " + comboDisplay;

        if (inv.contains(Material.NETHER_STAR)
                || inv.getItemInOffHand().getType() == Material.NETHER_STAR) {
            bar = ChatColor.RED + "⚡ ALL-IN! " + ChatColor.GRAY + "| " + bar;
        }

        p.sendActionBar(bar);

        // ── Killstreak luck bonuses ────────────────────────────────────────
        if (killstreak >= 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 100, 0));
        if (killstreak >= 2) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK,  100, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
        }
        if (killstreak >= 3) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK,     100, 2));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,    100, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0));
        }

        // ── Increment counters ────────────────────────────────────────────
        pdc.set(diceRollCounter,      PersistentDataType.INTEGER, diceRollC      + 1);
        pdc.set(allInCounter,         PersistentDataType.INTEGER, allInC         + 1);
        pdc.set(explosiveCardCounter, PersistentDataType.INTEGER, explosiveCardC + 1);

        // ── Restock: Dice Roll Potion ─────────────────────────────────────
        if (diceRollC >= diceRollRestock) {
            inv.addItem(diceRollPotion.clone());
            pdc.set(diceRollCounter, PersistentDataType.INTEGER, 0);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            p.sendMessage(ChatColor.GOLD + "🎲 Dice Roll ready — drink for a random effect!");
        }

        // ── Restock: Lucky Charm ──────────────────────────────────────────
        if (luckyStrikeC >= luckyStrikeCharges) {
            SnapshotPvpPlugin.clearInv(inv, Material.GHAST_TEAR);
            inv.addItem(luckyCharm.clone());
            pdc.set(luckyStrikeCounter, PersistentDataType.INTEGER, 0);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f);
            p.sendMessage(ChatColor.GREEN + "🍀 Lucky Charm ready!");
        }

        // ── Restock: Explosive Cards ──────────────────────────────────────
        if (explosiveCardC >= explosiveCardRestock) {
            SnapshotPvpPlugin.clearInv(inv, Material.PAPER);
            inv.addItem(explosiveCard.clone());
            pdc.set(explosiveCardCounter, PersistentDataType.INTEGER, 0);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            p.sendMessage(ChatColor.RED + "🃏 Explosive Cards ready — right-click to throw!");
        }

        // ── Restock: All-In Token ─────────────────────────────────────────
        if (allInC >= allInRestock) {
            SnapshotPvpPlugin.clearInv(inv, Material.NETHER_STAR);
            inv.addItem(allInToken.clone());
            pdc.set(allInCounter, PersistentDataType.INTEGER, 0);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f);
            p.sendMessage(ChatColor.LIGHT_PURPLE + "💎 All-In Token ready!");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.POTION);
        SnapshotPvpPlugin.clearInv(inv, Material.GHAST_TEAR);
        SnapshotPvpPlugin.clearInv(inv, Material.NETHER_STAR);
        SnapshotPvpPlugin.clearInv(inv, Material.PAPER);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        // Start halfway so the first potion arrives quickly
        pdc.set(diceRollCounter,      PersistentDataType.INTEGER, diceRollRestock / 2);
        pdc.set(luckyStrikeCounter,   PersistentDataType.INTEGER, 0);
        pdc.set(allInCounter,         PersistentDataType.INTEGER, 0);
        pdc.set(slotMachineCombo,     PersistentDataType.INTEGER, 0);
        pdc.set(explosiveCardCounter, PersistentDataType.INTEGER, 0);
    }

    // ══════════════════════════════════════════════════════════════════════
    // onConsume — intercept Dice Roll Potion and apply the real random effect
    // ══════════════════════════════════════════════════════════════════════
    @Override
    public void onConsume(Player p, PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        if (item.getType() != Material.POTION) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        if (!meta.getDisplayName().equals(ChatColor.GOLD + "🎲 Dice Roll")) return;

        // Cancel the vanilla drink; we apply effects manually
        rollDice(p, false);
    }

    // ══════════════════════════════════════════════════════════════════════
    // onInteract — Explosive Card throw / Lucky Charm / All-In Token
    // ══════════════════════════════════════════════════════════════════════
    @Override
    public void onInteract(Player p, PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null) return;

        // ── Explosive Card ────────────────────────────────────────────────
        if (item.getType() == Material.PAPER) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return;
            if (!meta.getDisplayName().equals(ChatColor.RED + "🃏 Explosive Card")) return;
            e.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
            throwExplosiveCard(p);
            return;
        }

        // ── Lucky Charm ───────────────────────────────────────────────────
        if (item.getType() == Material.GHAST_TEAR) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return;
            if (!meta.getDisplayName().equals(ChatColor.GREEN + "🍀 Lucky Charm")) return;
            e.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
            // rollDice with guaranteed=true forces a good/jackpot outcome
            rollDice(p, true);
            p.sendMessage(ChatColor.GREEN + "🍀 Lucky Charm used — guaranteed fortune!");
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation(), 20, 0.5, 1, 0.5, 0.1);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
            return;
        }

        // ── All-In Token ──────────────────────────────────────────────────
        if (item.getType() == Material.NETHER_STAR) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return;
            if (!meta.getDisplayName().equals(ChatColor.LIGHT_PURPLE + "💎 All-In Token")) return;
            e.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
            triggerAllIn(p);
            return;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // onDamageDealt — lucky-strike charge & slot-machine combo & crit
    // ══════════════════════════════════════════════════════════════════════
    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Player target = (Player) e.getEntity();

        // Build lucky-strike charges
        int luckyStrikeC = pdc.get(luckyStrikeCounter, PersistentDataType.INTEGER);
        pdc.set(luckyStrikeCounter, PersistentDataType.INTEGER, luckyStrikeC + 1);

        // Slot-machine combo
        int combo = pdc.get(slotMachineCombo, PersistentDataType.INTEGER);
        int roll  = random.nextInt(100);

        if (roll < 15) {
            combo++;
            pdc.set(slotMachineCombo, PersistentDataType.INTEGER, combo);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + combo * 0.2f);
            p.sendMessage(ChatColor.YELLOW + "🎰 Combo: " + combo + "x");

            if (combo == 3) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1));
                p.sendMessage(ChatColor.GOLD + "🎰 777! Strength II burst!");
                p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation(), 20, 0.5, 1, 0.5, 0.1);
            } else if (combo >= 5) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,  200, 2));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,200, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,     200, 1));
                p.sendMessage(ChatColor.GOLD + "🎰🎰🎰 JACKPOT! Triple buffs!");
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation(), 50, 1, 1, 1, 0.2);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                pdc.set(slotMachineCombo, PersistentDataType.INTEGER, 0);
            }
        } else if (roll > 85) {
            if (combo > 0) {
                p.sendMessage(ChatColor.RED + "🎰 Combo lost!");
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 0.5f);
            }
            pdc.set(slotMachineCombo, PersistentDataType.INTEGER, 0);
        }

        // Luck-scaled crit chance
        int critChance = 10;
        if (p.hasPotionEffect(PotionEffectType.LUCK))
            critChance += (p.getPotionEffect(PotionEffectType.LUCK).getAmplifier() + 1) * 5;

        if (random.nextInt(100) < critChance) {
            double extra = e.getDamage() * 0.5;
            e.setDamage(e.getDamage() + extra);
            p.sendMessage(ChatColor.GOLD + "💥 Crit! +" + String.format("%.1f", extra) + " dmg");
            p.getWorld().spawnParticle(Particle.CRIT, target.getLocation(), 10, 0.3, 0.5, 0.3, 0.1);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.2f);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        p.sendMessage(ChatColor.GOLD + "💰 Jackpot kill — bonus rolls!");
        rollDice(p, false);
        rollDice(p, false);
        pdc.set(slotMachineCombo,   PersistentDataType.INTEGER, 0);
        pdc.set(luckyStrikeCounter, PersistentDataType.INTEGER, luckyStrikeCharges / 2);
    }

    // ══════════════════════════════════════════════════════════════════════
    // DICE ROLL  —  d6 weighted by Luck level
    //
    //  Face 1 (bad)     — Slowness II 5s
    //  Face 2 (bad)     — Weakness II 5s
    //  Face 3 (neutral) — Resistance I + Speed I 4s
    //  Face 4 (good)    — Strength II + Speed I 8s
    //  Face 5 (good)    — Regen II + Resistance II 8s
    //  Face 6 (jackpot) — Strength III + Speed II + Resistance II + Regen I 15s
    //
    //  Base weights: bad=2, neutral=1, good=2, jackpot=1
    //  Each Luck level shifts 1 weight from bad → good (floor 0 bad)
    //  guaranteed=true (Lucky Charm) forces roll into good/jackpot tier
    // ══════════════════════════════════════════════════════════════════════
    private void rollDice(Player p, boolean guaranteed) {
        int luckLevel = 0;
        if (p.hasPotionEffect(PotionEffectType.LUCK))
            luckLevel = p.getPotionEffect(PotionEffectType.LUCK).getAmplifier() + 1;

        int badWeight     = Math.max(0, 2 - luckLevel);
        int neutralWeight = 1;
        int goodWeight    = 2 + luckLevel;
        int jackpotWeight = 1;
        int total         = badWeight + neutralWeight + goodWeight + jackpotWeight;

        // Guaranteed forces the random value into the good+jackpot band
        int roll = guaranteed ? (badWeight + neutralWeight) : random.nextInt(total);

        if (roll < badWeight) {
            // ── Bad (face 1 or 2) ──────────────────────────────────────────
            if (random.nextBoolean()) {
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, 100, 1, false, true, true));
                p.sendMessage(ChatColor.RED + "🎲 Rolled ⚀  — Slowness II (5s)");
            } else {
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, 100, 1, false, true, true));
                p.sendMessage(ChatColor.RED + "🎲 Rolled ⚁  — Weakness II (5s)");
            }
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);

        } else if (roll < badWeight + neutralWeight) {
            // ── Neutral (face 3) ───────────────────────────────────────────
            p.addPotionEffect(new PotionEffect(
                    PotionEffectType.RESISTANCE, 80, 0, false, true, true));
            p.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED,      80, 0, false, true, true));
            p.sendMessage(ChatColor.YELLOW + "🎲 Rolled ⚂  — Resistance I + Speed I (4s)");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);

        } else if (roll < badWeight + neutralWeight + goodWeight) {
            // ── Good (face 4 or 5) ─────────────────────────────────────────
            if (random.nextBoolean()) {
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.STRENGTH, 160, 1, false, true, true));
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED,    160, 0, false, true, true));
                p.sendMessage(ChatColor.GREEN + "🎲 Rolled ⚃  — Strength II + Speed I (8s)");
            } else {
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.REGENERATION, 160, 1, false, true, true));
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE,   160, 1, false, true, true));
                p.sendMessage(ChatColor.GREEN + "🎲 Rolled ⚄  — Regen II + Resistance II (8s)");
            }
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation(), 15, 0.5, 1, 0.5, 0.05);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);

        } else {
            // ── Jackpot (face 6) ───────────────────────────────────────────
            p.addPotionEffect(new PotionEffect(
                    PotionEffectType.STRENGTH,     300, 2, false, true, true));
            p.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED,        300, 1, false, true, true));
            p.addPotionEffect(new PotionEffect(
                    PotionEffectType.RESISTANCE,   300, 1, false, true, true));
            p.addPotionEffect(new PotionEffect(
                    PotionEffectType.REGENERATION, 300, 0, false, true, true));
            p.sendMessage(ChatColor.GOLD + "🎲 Rolled ⚅  — JACKPOT! All buffs (15s)!");
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation(), 50, 1, 1, 1, 0.2);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXPLOSIVE CARD — SmallFireball; explodes natively on impact
    // ══════════════════════════════════════════════════════════════════════
    private void throwExplosiveCard(Player p) {
        Vector dir = p.getLocation().getDirection().normalize();

        // Spawn just ahead of the player's eyes so it clears their hitbox
        p.getWorld().spawn(
                p.getEyeLocation().add(dir.clone().multiply(1.2)),
                SmallFireball.class,
                fb -> {
                    fb.setShooter(p);
                    fb.setDirection(dir);
                    fb.setVelocity(dir.clone().multiply(1.8));
                    fb.setYield(2.5f);         // blast radius in blocks
                    fb.setIsIncendiary(false);  // no lingering fire
                });

        p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.5f);
        p.sendMessage(ChatColor.RED + "🃏 Explosive Card thrown!");
    }

    // ══════════════════════════════════════════════════════════════════════
    // ALL-IN TOKEN — 50 / 50
    // ══════════════════════════════════════════════════════════════════════
    private void triggerAllIn(Player p) {
        p.sendMessage(ChatColor.LIGHT_PURPLE + "💎 ALL-IN! Rolling...");
        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 50, 1, 1, 1, 0.2);

        if (random.nextBoolean()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,     300, 3, false, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,   300, 2, false, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,        300, 2, false, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 300, 1, false, true, true));
            p.setHealth(Math.min(p.getHealth() + 10, p.getMaxHealth()));
            p.sendMessage(ChatColor.GOLD + "🎰 WINNER! Maximum power for 15s!");
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation(), 100, 2, 2, 2, 0.3);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        } else {
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,      200, 1, false, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,      200, 1, false, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,200, 1, false, true, true));
            p.setHealth(Math.max(p.getHealth() - 6, 1));
            p.sendMessage(ChatColor.DARK_RED + "💀 BUST! House always wins...");
            p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation(), 50, 1, 1, 1, 0.1);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 0.5f);
        }
    }
}
