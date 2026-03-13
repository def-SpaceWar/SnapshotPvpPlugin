package com.defspacemine.snapshotpvp.manakit;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Arrow;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public class Gambler extends ManaKit {
    final int arrowRestock = 10; // 10 hits for betting arrow
    final NamespacedKey arrowRestockCounter = ManaKitListener.MANA_KIT_DATA0;
    final int MONEY_SCALE = 30; // 1 + (sqrt(money) / MONEY_SCALE) is the damage and defence factor
    final int moneyMax = 900; // 2x damage and defence
    final NamespacedKey moneyCounter = ManaKitListener.MANA_KIT_DATA1;
    final NamespacedKey activeBet = ManaKitListener.MANA_KIT_DATASTR0;
    final int betLength = 400; // 20 second long bets
    final NamespacedKey betCounter = ManaKitListener.MANA_KIT_DATA2;
    final NamespacedKey comboCounter = ManaKitListener.MANA_KIT_DATA3;

    private ItemStack bettingArrow;
    private ItemStack honorPotion;

    public Gambler() {
        super("gambler", "Gambler", "[Melee Damage]", 0);

        bettingArrow = new ItemStack(Material.TIPPED_ARROW, 1);
        PotionMeta meta = (PotionMeta) bettingArrow.getItemMeta();
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        meta.setDisplayName(ChatColor.GREEN + "Double or Half");
        meta.setColor(Color.fromRGB(0x00FF00));
        bettingArrow.setItemMeta(meta);
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

        String displayMessage = ChatColor.AQUA + "Kill-Bet: " +
                ChatColor.WHITE + arrowRestockC + "/" + arrowRestock +
                ChatColor.GRAY + "  |  " +
                (moneyC == moneyMax ? (ChatColor.GREEN + "" + ChatColor.BOLD) : (ChatColor.GREEN + "")) + "$" + moneyC +
                " (" + String.format("%.2f", 1 + Math.sqrt(moneyC)/MONEY_SCALE) + "x)" +
                ChatColor.RESET + "" + ChatColor.GRAY + "  |  " +
                ChatColor.GOLD + "Combo: " +
                ChatColor.WHITE + comboC +
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

        if (killstreak == 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 100, 0));
        if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 100, 1));

        PlayerInventory inv = p.getInventory();
        if (arrowRestockC >= arrowRestock) {
            SnapshotPvpPlugin.clearInv(inv, Material.TIPPED_ARROW);
            inv.addItem(bettingArrow);
            pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 0);
        }

        if (bettedPlayer != null) {
            if (betC <= 0) {
                pdc.set(activeBet, PersistentDataType.STRING, "undefined");
                pdc.set(moneyCounter, PersistentDataType.INTEGER, moneyC / 2);
            } else if (!bettedPlayer.getScoreboardTags().contains("combat")) {
    			pdc.set(activeBet, PersistentDataType.STRING, "undefined");
                pdc.set(moneyCounter, PersistentDataType.INTEGER, Math.min(moneyMax, moneyC * 2));
            } else {
                bettedPlayer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20, 0));
                pdc.set(betCounter, PersistentDataType.INTEGER, betC - 1);
            }
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.ARROW);
        SnapshotPvpPlugin.clearInv(inv, Material.POTION);
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
    }

    @Override
	public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
	    if (!(e.getEntity() instanceof Player)) return;

	    PersistentDataContainer pdc = p.getPersistentDataContainer();
	    int luckLevel = p.hasPotionEffect(PotionEffectType.LUCK) ? 
	                    p.getPotionEffect(PotionEffectType.LUCK).getAmplifier() + 1 : 0;

	    int currentCombo = pdc.get(comboCounter, PersistentDataType.INTEGER);
	    int moneyC = pdc.get(moneyCounter, PersistentDataType.INTEGER);

	    int comboGain = (int) Math.sqrt(Math.random() * (2 + luckLevel)); 

	    double loseChance = 0.15 - (luckLevel * 0.05);
	    if (Math.random() < Math.max(0.02, loseChance) && currentCombo > 0) {
	        pdc.set(comboCounter, PersistentDataType.INTEGER, 0);
	        p.sendMessage(ChatColor.RED + "" + ChatColor.ITALIC + "The streak went cold...");
	        currentCombo = 0;
	    } else {
	        currentCombo += comboGain;
	        pdc.set(comboCounter, PersistentDataType.INTEGER, currentCombo);
	        if (comboGain > 1) {
	            p.sendMessage(ChatColor.GOLD + "Hot Streak! +" + comboGain + " Combo");
	        }
	    }

	    e.setDamage(e.getDamage() * (1 + Math.sqrt(moneyC) / MONEY_SCALE));

	    if (currentCombo > 0 && currentCombo % 4 == 0) {
	        int comboRoll = (int) (Math.random() * 100) + (currentCombo * 2) + (luckLevel * 10);

	        if (comboRoll >= 140) {
	            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 100, 4));
	            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, 1));
	            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));
	            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0));
	            p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "★ ROYAL FLUSH ★");
	        } 
	        else if (comboRoll >= 100) {
	            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
	            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 1));
	            p.sendMessage(ChatColor.AQUA + "Full House!");
	        } 
	        else if (comboRoll >= 60) {
	            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0));
	            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 0));
	            p.sendMessage(ChatColor.GREEN + "Two Pair!");
	        }
	        else if (comboRoll < 20) {
	            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 5));
	            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 1));
	            p.sendMessage(ChatColor.DARK_RED + "Snake Eyes... Unlucky.");
	        }
	    }

	    int moneyGain = (int) (Math.random() * (3 + luckLevel)) + 1;
	    pdc.set(moneyCounter, PersistentDataType.INTEGER, Math.min(moneyMax, moneyC + moneyGain));

	    pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, 
	            pdc.get(arrowRestockCounter, PersistentDataType.INTEGER) + 1);
	}

	@Override
	public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
	    PersistentDataContainer pdc = p.getPersistentDataContainer();
	    int luckLevel = p.hasPotionEffect(PotionEffectType.LUCK) ? 
	                    p.getPotionEffect(PotionEffectType.LUCK).getAmplifier() + 1 : 0;

	    int moneyC = pdc.get(moneyCounter, PersistentDataType.INTEGER);
	    int currentCombo = pdc.get(comboCounter, PersistentDataType.INTEGER);

	    double multiplier = (1 + Math.sqrt(moneyC) / MONEY_SCALE);
	    e.setDamage(e.getDamage() / multiplier);

	    double loseComboChance = 0.25 - (luckLevel * 0.10);
	    if (Math.random() < Math.max(0.05, loseComboChance) && currentCombo > 0) {
	        pdc.set(comboCounter, PersistentDataType.INTEGER, 0);
	        p.sendMessage(ChatColor.DARK_RED + "Combo Broken!");
	    }

	    int loss = (int) (e.getDamage() * multiplier * multiplier * multiplier);
	    int newBalance = Math.max(0, moneyC - loss);
	    pdc.set(moneyCounter, PersistentDataType.INTEGER, newBalance);
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
	        payout = 150;
	        message = ChatColor.GOLD + "" + ChatColor.BOLD + " JACKPOT! +$150";
	        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
	        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
	    } else if (roll >= 70) {
	        payout = 60;
	        message = ChatColor.YELLOW + " Big Win! +$60";
	        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
	    } else if (roll >= 30) {
	        payout = 30;
	        message = ChatColor.GREEN + " Winner! +$30";
	    } else {
	        payout = 10;
	        message = ChatColor.RED + " Small Pot... +$10";
	    }

	    int currentMoney = pdc.get(moneyCounter, PersistentDataType.INTEGER);
	    pdc.set(moneyCounter, PersistentDataType.INTEGER, Math.min(moneyMax, currentMoney + payout));
	    pdc.set(arrowRestockCounter, PersistentDataType.INTEGER, arrowRestock);

	    p.sendMessage(message);
	}

    @Override
    public void onProjectileHit(Player p, ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow))
            return;
        if (!(e.getHitEntity() instanceof Player victim))
            return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(activeBet, PersistentDataType.STRING, victim.getUniqueId().toString());
        pdc.set(betCounter, PersistentDataType.INTEGER, betLength);
    }
}
