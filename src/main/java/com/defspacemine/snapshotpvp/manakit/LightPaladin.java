package com.defspacemine.snapshotpvp.manakit;

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
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import com.defspacemine.snapshotpvp.customegg.CustomEggListener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class LightPaladin extends ManaKit {
    public static final float BLINDING_LIGHT_RADIUS = 6;

    final int holyShieldRestock = 40; // 40 attacks given or taken
    final NamespacedKey holyShieldRestockCounter = ManaKitListener.MANA_KIT_DATA0;

    private ItemStack holyShield;
    private ItemStack blindingLight;

    public LightPaladin() {
        super("light_paladin", "Light Paladin", "[Melee Damage]", 2);

        {
            blindingLight = new ItemStack(Material.CROSSBOW, 1);
            CrossbowMeta meta = (CrossbowMeta) blindingLight.getItemMeta();
            meta.displayName(Component.text("Blinding Light")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            ((Damageable) meta).setDamage(462);
            ItemStack rocket = new ItemStack(Material.FIREWORK_ROCKET, 1);
            FireworkMeta fwMeta = (FireworkMeta) rocket.getItemMeta();
            fwMeta.setPower(1);
            fwMeta.getPersistentDataContainer().set(CustomEggListener.BLINDING_LIGHT, PersistentDataType.INTEGER, 1);
            rocket.setItemMeta(fwMeta);
            meta.addChargedProjectile(rocket);
            blindingLight.setItemMeta(meta);
        }

        {
            holyShield = new ItemStack(Material.SPLASH_POTION);
            PotionMeta meta = (PotionMeta) holyShield.getItemMeta();
            meta.displayName(Component.text("Holy Shield")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.setColor(Color.fromRGB(16776960));
            meta.addCustomEffect(new PotionEffect(
                    PotionEffectType.ABSORPTION,
                    -1,
                    4,
                    true,
                    true,
                    true), true);
            holyShield.setItemMeta(meta);
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
        pdc.set(holyShieldRestockCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int holyShieldRestockC = pdc.get(holyShieldRestockCounter, PersistentDataType.INTEGER);

        p.sendActionBar(
                ChatColor.GOLD + "Holy Shield: " +
                        ChatColor.WHITE + holyShieldRestockC + "/" + holyShieldRestock +
                        ChatColor.GRAY + "  |  " +
                        ChatColor.RED + "Killstreak: " +
                        ChatColor.WHITE + killstreak + "/2");

        if (killstreak == 1 && !p.hasPotionEffect(PotionEffectType.REGENERATION))
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, -1, 0));
        if (killstreak >= 2 && !p.hasPotionEffect(PotionEffectType.REGENERATION))
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, -1, 1));

        PlayerInventory inv = p.getInventory();
        if (holyShieldRestockC >= holyShieldRestock) {
            SnapshotPvpPlugin.clearInv(inv, Material.SPLASH_POTION);
            p.getInventory().addItem(holyShield);
            pdc.set(holyShieldRestockCounter, PersistentDataType.INTEGER, 0);
        }
    }

    public void onKill(Player p, PlayerDeathEvent e) {
        PotionEffect potionEffect = p.getPotionEffect(PotionEffectType.REGENERATION);
        if (potionEffect != null && potionEffect.getAmplifier() < 1)
            p.removePotionEffect(PotionEffectType.REGENERATION);
        SnapshotPvpPlugin.clearInv(p.getInventory(), Material.CROSSBOW);
        p.getInventory().addItem(blindingLight);
    }

    @Override
    public void onLeaveCombat(Player p) {
        PlayerInventory inv = p.getInventory();
        SnapshotPvpPlugin.clearInv(inv, Material.CROSSBOW);
        SnapshotPvpPlugin.clearInv(inv, Material.SPLASH_POTION);
        p.clearActivePotionEffects();
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(holyShieldRestockCounter, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) {
            PersistentDataContainer pdc = p.getPersistentDataContainer();
            pdc.set(holyShieldRestockCounter, PersistentDataType.INTEGER,
                    pdc.get(holyShieldRestockCounter, PersistentDataType.INTEGER) + 1);
        }
    }

    @Override
    public void onDamageTaken(Player p, EntityDamageByEntityEvent e) {
        if (e.getDamageSource().getCausingEntity() instanceof Player) {
            PersistentDataContainer pdc = p.getPersistentDataContainer();
            pdc.set(holyShieldRestockCounter, PersistentDataType.INTEGER,
                    pdc.get(holyShieldRestockCounter, PersistentDataType.INTEGER) + 1);
        }
    }
}
