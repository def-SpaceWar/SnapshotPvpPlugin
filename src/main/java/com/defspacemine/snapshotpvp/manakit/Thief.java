package com.defspacemine.snapshotpvp.manakit;

import com.defspacemine.snapshotpvp.SnapshotPvpPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Thief extends ManaKit {

    final double CLOSE_RADIUS = 1;

    final int ammoRestock = 8; // 8 attacks for 3 ammo (it resets itself)
    final NamespacedKey ammoRestockCounter = ManaKitListener.MANA_KIT_DATA0;
    final int shadowStepRestock = 750;
    final int shadowRealmDuration = 100;
    final NamespacedKey shadowStepRestockCounter =
        ManaKitListener.MANA_KIT_DATA2;
    private final NamespacedKey shadowStepKey = new NamespacedKey(
        "defspacemine",
        "thief_shadow_step"
    );

    private ItemStack ammo;
    private ItemStack shadowStep;

    public Thief() {
        super("thief", "Thief", "[Melee Assassin]", 3);

        {
            ammo = new ItemStack(Material.TIPPED_ARROW, 3);
            ammo.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
            PotionMeta meta = (PotionMeta) ammo.getItemMeta();

            meta.setDisplayName(ChatColor.GOLD + "Ammo");
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            meta.setColor(Color.fromRGB(0xFFCC44));
            meta.addCustomEffect(
                new PotionEffect(
                    PotionEffectType.SLOWNESS,
                    1600,
                    5,
                    false,
                    true,
                    true
                ),
                true
            );
            ammo.setItemMeta(meta);
        }

        shadowStep = new ItemStack(Material.ENDER_EYE, 1);
        ItemMeta shadowStepMeta = shadowStep.getItemMeta();
        shadowStepMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Shadow Step");
        shadowStepMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        shadowStepMeta
            .getPersistentDataContainer()
            .set(shadowStepKey, PersistentDataType.BOOLEAN, true);
        shadowStep.setItemMeta(shadowStepMeta);
    }

    @Override
    public void giveKit(Player p) {
        resetKit(p);

        ManaKitListener.giveItemsFromShulker(
            p,
            "goopshotpeshvp",
            -187,
            1,
            -185
        );
        SnapshotPvpPlugin.clearInv(p, Material.CHORUS_FRUIT);
        SnapshotPvpPlugin.clearInv(p, Material.ENDER_PEARL);
        SnapshotPvpPlugin.clearInv(p, Material.ENDER_EYE);
    }

    @Override
    public void resetKit(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ManaKitListener.MANA_KIT, PersistentDataType.STRING, this.id);
        pdc.set(ammoRestockCounter, PersistentDataType.INTEGER, 0);
        pdc.remove(ManaKitListener.MANA_KIT_DATA1);
    }

    @Override
    public void onCombatTick(Player p) {
        int killstreak = SnapshotPvpPlugin.getPlayerScore(p, "dummyKillstreak");
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int ammoRestockC = pdc.get(
            ammoRestockCounter,
            PersistentDataType.INTEGER
        );
        int shadowStepRestockC = pdc.get(
            shadowStepRestockCounter,
            PersistentDataType.INTEGER
        );

        String displayMessage =
            ChatColor.GOLD +
            "Ammo Restock: " +
            ChatColor.WHITE +
            ammoRestockC +
            "/" +
            ammoRestock +
            ChatColor.GRAY +
            " | " +
            ChatColor.LIGHT_PURPLE +
            "Shadow Step: " +
            ChatColor.WHITE +
            shadowStepRestockC +
            "/" +
            shadowStepRestock +
            ChatColor.GRAY +
            " | " +
            ChatColor.RED +
            "Killstreak: " +
            ChatColor.WHITE +
            killstreak +
            "/4";

        p.sendActionBar(displayMessage);

        if (killstreak == 1) p.addPotionEffect(
            new PotionEffect(PotionEffectType.SPEED, 100, 0)
        );
        else if (killstreak == 2) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
            p.addPotionEffect(
                new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0)
            );
        } else if (killstreak >= 3) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
            p.addPotionEffect(
                new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0)
            );
        }

        if (killstreak >= 4) pdc.set(
            shadowStepRestockCounter,
            PersistentDataType.INTEGER,
            shadowStepRestockC + 2
        );
        else pdc.set(
            shadowStepRestockCounter,
            PersistentDataType.INTEGER,
            shadowStepRestockC + 1
        );

        PlayerInventory inv = p.getInventory();
        if (ammoRestockC >= ammoRestock) {
            SnapshotPvpPlugin.clearInv(p, Material.TIPPED_ARROW);
            inv.addItem(ammo);
            playItemGainFeedback(p);
            pdc.set(
                ammoRestockCounter,
                PersistentDataType.INTEGER,
                ammoRestockC - ammoRestock
            );
        }

        if (shadowStepRestockC >= shadowStepRestock) {
            SnapshotPvpPlugin.clearInv(p, Material.ENDER_PEARL);
            inv.addItem(shadowStep);
            playItemGainFeedback(p);
            pdc.set(
                shadowStepRestockCounter,
                PersistentDataType.INTEGER,
                0
            );
        }

        Location loc = p.getLocation();
        for (Player _p : p.getWorld().getNearbyPlayers(loc, CLOSE_RADIUS))
            if (!_p.equals(p)) p.addPotionEffect(
                new PotionEffect(PotionEffectType.STRENGTH, 5, 1)
            );
    }

    @Override
    public void onLeaveCombat(Player p) {
        SnapshotPvpPlugin.clearInv(p, Material.TIPPED_ARROW);
        SnapshotPvpPlugin.clearInv(p, Material.CHORUS_FRUIT);
        SnapshotPvpPlugin.clearInv(p, Material.ENDER_PEARL);
        SnapshotPvpPlugin.clearInv(p, Material.ENDER_EYE);
        resetKit(p);
    }

    @Override
    public void onEnterCombat(Player p) {
        SnapshotPvpPlugin.clearInv(p, Material.CHORUS_FRUIT);
        SnapshotPvpPlugin.clearInv(p, Material.ENDER_PEARL);
        SnapshotPvpPlugin.clearInv(p, Material.ENDER_EYE);
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ammoRestockCounter, PersistentDataType.INTEGER, ammoRestock);
        pdc.remove(ManaKitListener.MANA_KIT_DATA1);
        pdc.set(
            shadowStepRestockCounter,
            PersistentDataType.INTEGER,
            shadowStepRestock
        );
    }

    @Override
    public void onDamageDealt(Player p, EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) {
            int killstreak = SnapshotPvpPlugin.getPlayerScore(
                p,
                "dummyKillstreak"
            );
            PersistentDataContainer pdc = p.getPersistentDataContainer();

            if (killstreak >= 4) pdc.set(
                ammoRestockCounter,
                PersistentDataType.INTEGER,
                pdc.get(ammoRestockCounter, PersistentDataType.INTEGER) + 2
            );
            else pdc.set(
                ammoRestockCounter,
                PersistentDataType.INTEGER,
                pdc.get(ammoRestockCounter, PersistentDataType.INTEGER) + 1
            );
        }
    }

    @Override
    public void onKill(Player p, PlayerDeathEvent e) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(ammoRestockCounter, PersistentDataType.INTEGER, ammoRestock);
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (
            event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK
        ) return;

        ItemStack item = event.getItem();
        if (
            item == null ||
            !item.hasItemMeta() ||
            !item
                .getItemMeta()
                .getPersistentDataContainer()
                .has(shadowStepKey, PersistentDataType.BOOLEAN)
        ) return;

        event.setCancelled(true);
        if (!p.getWorld().getName().endsWith("_map")) {
            p.sendMessage(
                ChatColor.RED + "Shadow Step can only be used on the game map."
            );
            return;
        }
        if (!ManaKit.enterShadowRealm(p, shadowRealmDuration)) {
            p.sendMessage(ChatColor.RED + "Could not enter the shadow realm.");
            return;
        }

        p.getInventory().setItemInMainHand(null);
    }
}
