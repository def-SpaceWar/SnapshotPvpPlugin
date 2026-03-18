package com.defspacemine.snapshotpvp.manakit;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class Gambler extends ManaKit {
    final int arrowRestock = 6; // 6 hits for betting arrow
    final NamespacedKey arrowRestockCounter = ManaKitListener.MANA_KIT_DATA0;
    final int moneyMax = 1_000_000; // 2x damage
    final NamespacedKey moneyCounter = ManaKitListener.MANA_KIT_DATA1;
    final NamespacedKey activeBet = ManaKitListener.MANA_KIT_DATASTR0;
    final int betLength = 400; // 20 second long bets
    final NamespacedKey betCounter = ManaKitListener.MANA_KIT_DATA2;
    final NamespacedKey comboCounter = ManaKitListener.MANA_KIT_DATA3;
    final int card = 200; // 10 seconds per card
    final NamespacedKey cardCounter = ManaKitListener.MANA_KIT_DATA4;

    private ItemStack bettingArrow;
    private ItemStack wildCard;

    final DustOptions dust = new DustOptions(Color.LIME, 1.5f);

    private double getMultiplier(int money) {
        if (money <= 0)
            return 1.0;
        if (money == moneyMax)
            return 2.5;
        double multiplier = 1 + Math.sqrt(Math.sqrt(1 - (Math.log10(moneyMax - money + 1) / 6.0)));
        return Math.max(1.0, Math.min(2.0, multiplier));
    }

    public Gambler() {
        super("gambler", "Gambler", "[Melee Damage]", 2);

        {
            bettingArrow = new ItemStack(Material.TIPPED_ARROW, 1);
            PotionMeta meta = (PotionMeta) bettingArrow.getItemMeta();
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.setDisplayName(ChatColor.GREEN + "Double or Nothing");
            meta.setColor(Color.fromRGB(0x00FF00));
            bettingArrow.setItemMeta(meta);
        }

        {
            wildCard = new ItemStack(Material.SNOWBALL);
            ItemMeta meta = wildCard.getItemMeta();
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Wild Card");
            meta.setItemModel(new NamespacedKey("defspacemine", "gambler_card"));
            wildCard.setItemMeta(meta);
        }
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
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
        pdc.set(moneyCounter, PersistentDataType.INTEGER, 0);
        pdc.set(activeBet, PersistentDataType.STRING, "undefined");
        pdc.set(betCounter, PersistentDataType.INTEGER, 0);
        pdc.set(comboCounter, PersistentDataType.INTEGER, 0);
        pdc.set(cardCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int arrowRestockC = pdc.get(arrowRestockCounter, PersistentDataType.INTEGER);
        int moneyC = pdc.get(moneyCounter, PersistentDataType.INTEGER);
        String bettingPlayer = pdc.get(activeBet, PersistentDataType.STRING);
        Player bettedPlayer = null;
        int betC = pdc.get(betCounter, PersistentDataType.INTEGER);
        int comboC = pdc.get(comboCounter, PersistentDataType.INTEGER);
        int cardC = pdc.get(cardCounter, PersistentDataType.INTEGER);

        String displayMessage = ChatColor.AQUA + "Kill-Bet: " +
                ChatColor.WHITE + arrowRestockC + "/" + arrowRestock +
                ChatColor.GRAY + "  |  " +
                (moneyC == moneyMax ? (ChatColor.GREEN + "" + ChatColor.BOLD) : (ChatColor.GREEN + "")) + "$"
                + String.format("%,d", moneyC) +
                " (" + String.format("%.2f", getMultiplier(moneyC)) + "x) " +
                (comboC > 0 ? ChatColor.GOLD + "[" + comboC + "x]" : "") +
                ChatColor.GRAY + "  |  " +
                ChatColor.LIGHT_PURPLE + ChatColor.MAGIC + "*" + ChatColor.RESET + ChatColor.LIGHT_PURPLE + "Card" +
                ChatColor.MAGIC + "*" + ChatColor.RESET + ChatColor.LIGHT_PURPLE + ": " +
                ChatColor.WHITE + cardC + "/" + card +
                ChatColor.GRAY + "  |  " +
                ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/2";
        if (!bettingPlayer.equals("undefined")) {
            bettedPlayer = Bukkit.getPlayer(UUID.fromString(bettingPlayer));
            if (bettedPlayer != null) {
                displayMessage = ChatColor.DARK_RED + "Target: " +
                        ChatColor.RED + bettedPlayer.getName() + " " + betC +
                        ChatColor.GRAY + "  |  " + displayMessage;
            }
        }
        p.sendActionBar(displayMessage);

        if (moneyC == moneyMax)
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 3, .2, .2, .2, 3, dust);

        if (killstreak == 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 100, 0));
        if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 100, 1));

        pdc.set(cardCounter, PersistentDataType.INTEGER, cardC + 1);

        PlayerInventory inv = p.getInventory();
        if (arrowRestockC >= arrowRestock) {
            SnapshotPvpPlugin.clearInv(inv, Material.TIPPED_ARROW);
            inv.addItem(bettingArrow);
            pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
        }

        if (cardC >= card) {
            SnapshotPvpPlugin.clearInv(inv, Material.SNOWBALL);
            p.getInventory().addItem(wildCard);
            pdc.set(cardCounter, PersistentDataType.INTEGER, 0);
        }

        if (bettedPlayer != null) {
            if (betC <= 0) {
                pdc.set(activeBet, PersistentDataType.STRING, "undefined");
                pdc.set(moneyCounter, PersistentDataType.INTEGER, 0);
                p.sendMessage(ChatColor.RED + "BET LOST: Money Gone!");
            } else if (!bettedPlayer.getScoreboardTags().contains("combat")) {
                pdc.set(activeBet, PersistentDataType.STRING, "undefined");
                pdc.set(moneyCounter, PersistentDataType.INTEGER, Math.min(moneyMax, moneyC * 2));
                p.sendMessage(ChatColor.GOLD + "BET WON: Money Doubled!");
            } else {
                bettedPlayer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20, 0));
                pdc.set(betCounter, PersistentDataType.INTEGER, betC - 1);
            }
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.TIPPED_ARROW);
        SnapshotPvpPlugin.clearInv(inv, Material.SNOWBALL);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, arrowRestock);
        pdc.set(moneyCounter, PersistentDataType.INTEGER, 0);
        pdc.set(activeBet, PersistentDataType.STRING, "undefined");
        pdc.set(betCounter, PersistentDataType.INTEGER, 0);
        pdc.set(comboCounter, PersistentDataType.INTEGER, 0);
        pdc.set(cardCounter, PersistentDataType.INTEGER, card);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player))
            return;
        DamageType type = e.getDamageSource().getDamageType();
        if (type == DamageType.MOB_ATTACK ||
                type == DamageType.EXPLOSION ||
                type == DamageType.PLAYER_EXPLOSION)
            return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int luckLevel = p.hasPotionEffect(PotionEffectType.LUCK)
                ? p.getPotionEffect(PotionEffectType.LUCK).getAmplifier() + 1
                : 0;

        int comboC = pdc.get(comboCounter, PersistentDataType.INTEGER);
        int moneyC = pdc.get(moneyCounter, PersistentDataType.INTEGER);

        int gain = (int) ((20 * Math.pow(1.15, comboC + luckLevel))
                + (Math.random() * (3 + luckLevel) + 1));
        pdc.set(moneyCounter, PersistentDataType.INTEGER, Math.min(moneyMax, moneyC + gain));
        pdc.set(comboCounter, PersistentDataType.INTEGER, comboC + 1);

        e.setDamage(e.getDamage() * getMultiplier(moneyC));

        if (comboC > 0 && comboC % 4 == 0) {
            int comboRoll = (int) (Math.random() * 100) + (comboC * 2) + (luckLevel * 10);

            if (comboRoll >= 140) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 500, 4));
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0));
                p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "★ ROYAL FLUSH ★");
            } else if (comboRoll >= 100) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 250, 2));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 1));
                p.sendMessage(ChatColor.AQUA + "Full House!");
            } else if (comboRoll >= 60) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 125, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 0));
                p.sendMessage(ChatColor.GREEN + "Two Pair!");
            } else if (comboRoll < 20) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 5));
                p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 1));
                p.sendMessage(ChatColor.DARK_RED + "Snake Eyes... Unlucky.");
            }
        }

        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER,
                pdc.get(arrowRestockCounter, PersistentDataType.INTEGER) + 1);
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int luckLevel = p.hasPotionEffect(PotionEffectType.LUCK)
                ? p.getPotionEffect(PotionEffectType.LUCK).getAmplifier() + 1
                : 0;

        int moneyC = pdc.get(moneyCounter, PersistentDataType.INTEGER);
        int currentCombo = pdc.get(comboCounter, PersistentDataType.INTEGER);

        double loseComboChance = 0.15 - (luckLevel * 0.05);
        if (Math.random() < Math.max(0.05, loseComboChance) && currentCombo > 0) {
            pdc.set(comboCounter, PersistentDataType.INTEGER, 0);
            p.sendMessage(ChatColor.DARK_RED + "Combo Broken!");
        }

        int loss = (int) (moneyC * 0.05);
        pdc.set(moneyCounter, PersistentDataType.INTEGER, Math.max(0, moneyC - loss));
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int luckLevel = 0;
        if (p.hasPotionEffect(PotionEffectType.LUCK)) {
            luckLevel = p.getPotionEffect(PotionEffectType.LUCK).getAmplifier() + 1;
        }

        int roll = (int) (Math.random() * 100) + (luckLevel * 10);
        int payout;
        String message;

        if (roll >= 95) {
            payout = 100_000;
            message = ChatColor.GOLD + "" + ChatColor.BOLD + " JACKPOT! +$100,000";
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
        } else if (roll >= 70) {
            payout = 30000;
            message = ChatColor.YELLOW + " Big Win! +$30,000";
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
        } else if (roll >= 30) {
            payout = 10000;
            message = ChatColor.GREEN + " Winner! +$10,000";
        } else {
            payout = 1000;
            message = ChatColor.RED + " Small Pot... +$1,000";
        }

        int currentMoney = pdc.get(moneyCounter, PersistentDataType.INTEGER);
        pdc.set(moneyCounter, PersistentDataType.INTEGER, Math.min(moneyMax, currentMoney + payout));
        pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, arrowRestock);
        pdc.set(cardCounter, PersistentDataType.INTEGER, card);

        p.sendMessage(message);
    }

    @Override
    public void onProjectileHit(Player p, ProjectileHitEvent e) {
        if (e.getEntity() instanceof Arrow arrow) {
            if (!(e.getHitEntity() instanceof Player victim))
                return;

            PersistentDataContainer pdc = p.getPersistentDataContainer();
            String currentBet = pdc.get(activeBet, PersistentDataType.STRING);

            pdc.set(betCounter, PersistentDataType.INTEGER, betLength);
            if (currentBet != null && !currentBet.equals("undefined")
                    && currentBet.equals(victim.getUniqueId().toString()))
                return;
            e.setCancelled(true);
            pdc.set(activeBet, PersistentDataType.STRING, victim.getUniqueId().toString());
            arrow.remove();
        } else if (e.getEntity() instanceof Snowball ball) {
            Location hitLoc = e.getHitEntity() != null ? e.getHitEntity().getLocation() : e.getEntity().getLocation();
            int luck = p.hasPotionEffect(PotionEffectType.LUCK)
                    ? p.getPotionEffect(PotionEffectType.LUCK).getAmplifier() + 1
                    : 0;

            double jackpotChance = 0.01 + (luck * 0.02);
            if (Math.random() < jackpotChance) {
                p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "!!! JACKPOT !!!");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

                triggerExplosion(p, hitLoc, luck + 5);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        triggerTeleport(p, hitLoc, luck);
                        triggerZombies(p, hitLoc, luck, true);
                    }
                }.runTaskLater(SnapshotPvpPlugin.instance, 5);

                return;
            }

            int roll = (int) (Math.random() * 3);
            switch (roll) {
                case 0 -> triggerExplosion(p, hitLoc, luck);
                case 1 -> triggerTeleport(p, hitLoc, luck);
                case 2 -> triggerZombies(p, hitLoc, luck, false);
            }

        }
    }

    private void triggerExplosion(Player p, Location loc, int luck) {
        float radius = 1.5f + (float) Math.sqrt(Math.random() * (1 + luck));
        TNTPrimed tnt = (TNTPrimed) loc.getWorld().spawnEntity(loc, EntityType.TNT);
        tnt.setFuseTicks(0);
        tnt.setYield(radius);
        tnt.setIsIncendiary(false);
        tnt.setSource(p);
    }

    private void triggerTeleport(Player p, Location loc, int luck) {
        Location oldLoc = p.getLocation().clone();
        p.teleport(loc.setDirection(oldLoc.getDirection().normalize()));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        int roll = (int) (Math.random() * 3);
        switch (roll) {
            case 0 -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2));
            case 1 -> p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 2));
            case 2 -> p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 2));
        }
    }

    private void triggerZombies(Player p, Location loc, int luck, boolean isJackpot) {
        Material[][] tiers = {
                { Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                        Material.LEATHER_BOOTS, Material.WOODEN_SWORD },
                { Material.COPPER_HELMET, Material.COPPER_CHESTPLATE, Material.COPPER_LEGGINGS, Material.COPPER_BOOTS,
                        Material.COPPER_SWORD },
                { Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS,
                        Material.CHAINMAIL_BOOTS, Material.STONE_SWORD },
                { Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
                        Material.GOLDEN_SWORD },
                { Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                        Material.IRON_SWORD },
                { Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS,
                        Material.DIAMOND_BOOTS, Material.DIAMOND_SWORD },
                { Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS,
                        Material.NETHERITE_BOOTS, Material.NETHERITE_SWORD }
        };

        int luckBonus = luck + (isJackpot ? 2 : 0);
        int rollBase = (int) (Math.random() * 5); // 0 to 4
        int mobsToSpawn = Math.min(6, rollBase + luckBonus);

        for (int i = 0; i < mobsToSpawn; i++) {
            Zombie zombie = loc.getWorld().spawn(loc, Zombie.class);
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, card * 2, 2));
            SnapshotPvpPlugin.addToTeam(p, zombie);
            CustomEggListener.injectOwner(zombie, p);

            zombie.customName(Component.text("Debt Collector")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));

            rollBase = (int) (Math.random() * 5); // 0 to 4
            Material[] selected = tiers[Math.min(6, rollBase + luckBonus)];

            zombie.getEquipment().setHelmet(new ItemStack(selected[0]));
            zombie.getEquipment().setChestplate(new ItemStack(selected[1]));
            zombie.getEquipment().setLeggings(new ItemStack(selected[2]));
            zombie.getEquipment().setBoots(new ItemStack(selected[3]));
            zombie.getEquipment().setItemInMainHand(new ItemStack(selected[4]));

            if (isJackpot) {
                for (ItemStack item : zombie.getEquipment().getArmorContents())
                    if (item != null && item.getType() != Material.AIR)
                        item.addUnsafeEnchantment(Enchantment.PROTECTION, 5);
            } else
                for (ItemStack item : zombie.getEquipment().getArmorContents())
                    if (item != null && item.getType() != Material.AIR)
                        item.addUnsafeEnchantment(Enchantment.PROTECTION, (int) (Math.random() * 5));

            zombie.getEquipment().setHelmetDropChance(0f);
            zombie.getEquipment().setChestplateDropChance(0f);
            zombie.getEquipment().setLeggingsDropChance(0f);
            zombie.getEquipment().setBootsDropChance(0f);
            zombie.getEquipment().setItemInMainHandDropChance(0f);

            zombie.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, card * 2, 0));

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (zombie != null && !zombie.isDead())
                        zombie.remove();
                }
            }.runTaskLater(SnapshotPvpPlugin.instance, card * 2);
        }
    }

    public void onDeath(Player p, PlayerDeathEvent e) {
        for (org.bukkit.entity.Entity entity : p.getWorld().getEntities())
            if (entity instanceof Snowball snowball)
                if (snowball.getShooter() instanceof Player shooter && shooter.equals(p))
                    snowball.remove();
    }
}
