package com.defspacemine.snapshotpvp.manakit;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;

public class Pharmacist extends ManaKit {
    final NamespacedKey potionStockCounter = ManaKitListener.MANA_KIT_DATA0;
    final NamespacedKey currentPotionCounter = ManaKitListener.MANA_KIT_DATA1;

    final int TA_SEDATIVE_SHOT = 0;
    final ItemStack sedativeShot;
    final int P_ANABOLIC_STEROIDS = 1;
    final ItemStack anabolicSteroids;
    final int SP_PANACEA_RUSH = 2;
    final ItemStack panaceaRush;
    final int LP_MUSTARD_GAS = 3;
    final ItemStack mustardGas;
    final int TA_LETHAL_INJECTION = 4;
    final ItemStack lethalInjection;

    final List<Integer> TA = List.of(TA_SEDATIVE_SHOT, TA_LETHAL_INJECTION);
    final List<Integer> P = List.of(P_ANABOLIC_STEROIDS);
    final List<Integer> SP = List.of(SP_PANACEA_RUSH);
    final List<Integer> LP = List.of(LP_MUSTARD_GAS);

    int chooseRandomFromList(List<Integer> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    int randomAny() {
        return chooseRandomFromList(List.of(randomTipped(), randomPotion(), randomSplash(), randomLingering()));
    }

    int randomTipped() {
        return chooseRandomFromList(TA);
    }

    int randomPotion() {
        return chooseRandomFromList(P);
    }

    int randomSplash() {
        return chooseRandomFromList(SP);
    }

    int randomLingering() {
        return chooseRandomFromList(LP);
    }

    int potionStock(int currentPotion) {
        switch (currentPotion) {
            case TA_SEDATIVE_SHOT:
                return 5;
            case P_ANABOLIC_STEROIDS:
                return 8;
            case SP_PANACEA_RUSH:
                return 7;
            case LP_MUSTARD_GAS:
                return 6;
            case TA_LETHAL_INJECTION:
                return 10;
        }
        return -1;
    }

    String potionText(int currentPotion) {
        switch (currentPotion) {
            case TA_SEDATIVE_SHOT:
                return ChatColor.BLUE + "(TA) Sedative Shot: ";
            case P_ANABOLIC_STEROIDS:
                return ChatColor.DARK_RED + "(P) Anabolic Steroids: ";
            case SP_PANACEA_RUSH:
                return ChatColor.YELLOW + "(SP) Panacea Rush: ";
            case LP_MUSTARD_GAS:
                return ChatColor.GOLD + "(LP) Mustard Gas: ";
            case TA_LETHAL_INJECTION:
                return ChatColor.BLACK + "(TA) Lethal Injection: ";
        }
        return ChatColor.GRAY + "" + ChatColor.MAGIC + "?????" + ChatColor.RESET + "" + ChatColor.GRAY + ": ";
    }

    void givePotion(Player p, int currentPotion) {
        PlayerInventory inv = p.getInventory();
        switch (currentPotion) {
            case TA_SEDATIVE_SHOT:
                SnapshotPvpPlugin.clearInv(inv, Material.TIPPED_ARROW);
                inv.addItem(sedativeShot);
                break;
            case P_ANABOLIC_STEROIDS:
                SnapshotPvpPlugin.clearInv(inv, Material.POTION);
                inv.addItem(anabolicSteroids);
                break;
            case SP_PANACEA_RUSH:
                SnapshotPvpPlugin.clearInv(inv, Material.SPLASH_POTION);
                inv.addItem(panaceaRush);
                break;
            case LP_MUSTARD_GAS:
                SnapshotPvpPlugin.clearInv(inv, Material.LINGERING_POTION);
                inv.addItem(mustardGas);
                break;
            case TA_LETHAL_INJECTION:
                SnapshotPvpPlugin.clearInv(inv, Material.TIPPED_ARROW);
                inv.addItem(lethalInjection);
                break;
        }
    }

    public Pharmacist() {
        super("pharmacist", "Pharmacist", "[Melee Damage]", 0);

        {
            sedativeShot = new ItemStack(Material.TIPPED_ARROW, 1);
            PotionMeta meta = (PotionMeta) sedativeShot.getItemMeta();
            meta.setDisplayName(ChatColor.BLUE + "Sedative Shot");
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.setColor(Color.fromRGB(0x5555FF));
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS,
                    800,
                    5,
                    false,
                    true,
                    true), true);
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.MINING_FATIGUE,
                    800,
                    5,
                    false,
                    true,
                    true), true);
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.WEAKNESS,
                    800,
                    2,
                    false,
                    true,
                    true), true);
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.SLOW_FALLING,
                    800,
                    5,
                    false,
                    true,
                    true), true);
            sedativeShot.setItemMeta(meta);
        }

        {
            anabolicSteroids = new ItemStack(Material.POTION, 1);
            PotionMeta meta = (PotionMeta) anabolicSteroids.getItemMeta();
            meta.setDisplayName(ChatColor.DARK_RED + "Anabolic Steroids");
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.setColor(Color.fromRGB(0xAA0000));
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.SPEED,
                    400,
                    0,
                    false,
                    true,
                    true), true);
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.HASTE,
                    400,
                    0,
                    false,
                    true,
                    true), true);
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.STRENGTH,
                    400,
                    1,
                    false,
                    true,
                    true), true);
            anabolicSteroids.setItemMeta(meta);
        }

        {
            panaceaRush = new ItemStack(Material.SPLASH_POTION, 1);
            PotionMeta meta = (PotionMeta) panaceaRush.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + "Panacea Rush");
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.setColor(Color.fromRGB(0xEEFF55));
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.REGENERATION,
                    400,
                    0,
                    false,
                    true,
                    true), true);
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.RESISTANCE,
                    400,
                    0,
                    false,
                    true,
                    true), true);
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.INSTANT_HEALTH,
                    1,
                    1,
                    false,
                    true,
                    true), true);
            panaceaRush.setItemMeta(meta);
        }

        {
            mustardGas = new ItemStack(Material.LINGERING_POTION, 1);
            PotionMeta meta = (PotionMeta) mustardGas.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + "Mustard Gas");
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.setColor(Color.fromRGB(0xFFAA66));
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.BLINDNESS,
                    400,
                    0,
                    false,
                    true,
                    true), true);
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.WEAKNESS,
                    400,
                    0,
                    false,
                    true,
                    true), true);
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS,
                    400,
                    5,
                    false,
                    true,
                    true), true);
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.POISON,
                    400,
                    1,
                    false,
                    true,
                    true), true);
            mustardGas.setItemMeta(meta);
        }

        {
            lethalInjection = new ItemStack(Material.TIPPED_ARROW, 1);
            PotionMeta meta = (PotionMeta) lethalInjection.getItemMeta();
            meta.setDisplayName(ChatColor.BLACK + "Lethal Injection");
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.setColor(Color.fromRGB(0x000000));
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.WITHER,
                    800,
                    3,
                    false,
                    true,
                    true), true);
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.INSTANT_DAMAGE,
                    1,
                    1,
                    false,
                    true,
                    true), true);
            lethalInjection.setItemMeta(meta);
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
        pdc.set(potionStockCounter, PersistentDataType.INTEGER, 0);
        pdc.set(currentPotionCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int potionStockC = pdc.get(potionStockCounter, PersistentDataType.INTEGER);
        int currentPotionC = pdc.get(currentPotionCounter, PersistentDataType.INTEGER);
        int potionStock = potionStock(currentPotionC);

        p.sendActionBar(potionText(currentPotionC) +
                ChatColor.WHITE + potionStockC + "/" + potionStock +
                ChatColor.GRAY + "  |  " +
                ChatColor.RED + "Killstreak: " +
                ChatColor.WHITE + killstreak + "/3");

        if (killstreak >= 2)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 0));
        // mana boost I @ killstreak = 1
        // mana boost II @ killstreak = 3

        if (potionStockC >= potionStock) {
            givePotion(p, currentPotionC);
            pdc.set(potionStockCounter, PersistentDataType.INTEGER, potionStockC - potionStock);
            pdc.set(currentPotionCounter, PersistentDataType.INTEGER, randomAny());
        }
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.TIPPED_ARROW);
        SnapshotPvpPlugin.clearInv(inv, Material.POTION);
        SnapshotPvpPlugin.clearInv(inv, Material.SPLASH_POTION);
        SnapshotPvpPlugin.clearInv(inv, Material.LINGERING_POTION);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(potionStockCounter, PersistentDataType.INTEGER, 0);
        pdc.set(currentPotionCounter, PersistentDataType.INTEGER, randomAny());
        givePotion(p, randomTipped());
        givePotion(p, randomPotion());
        givePotion(p, randomSplash());
        givePotion(p, randomLingering());
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(potionStockCounter, PersistentDataType.INTEGER,
                pdc.get(potionStockCounter, PersistentDataType.INTEGER) +
                        ((killstreak >= 3) ? 3 : (killstreak >= 1) ? 2 : 1));
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        givePotion(p, randomTipped());
        givePotion(p, randomPotion());
        givePotion(p, randomSplash());
        givePotion(p, randomLingering());
    }
}
