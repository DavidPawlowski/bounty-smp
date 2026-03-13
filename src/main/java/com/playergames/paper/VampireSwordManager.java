package com.playergames.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Manages the Vampire Sword weapon functionality.
 *
 * UPDATED MECHANIC (player-only):
 * - The sword deals normal damage on every hit against players.
 * - A per-player hit counter tracks successive hits on ANY player.
 * - Every 5th hit triggers a special drain effect:
 *     • The target takes an ADDITIONAL 4 HP of damage (2 hearts).
 *     • The attacker heals for 4 HP (2 hearts).
 * - After the effect triggers the counter resets to 0 and starts again.
 * - Hitting mobs does NOT count and does NOT trigger the effect.
 */
public class VampireSwordManager implements Listener {
    private static final String VAMPIRE_SWORD_KEY = "vampire_sword";

    /** Number of player-hits before the drain triggers */
    private static final int HITS_TO_DRAIN = 5;
    /** Extra damage dealt to the victim AND health restored to attacker on trigger */
    private static final double DRAIN_AMOUNT = 4.0; // 4 HP = 2 hearts

    private final PGPlugin plugin;
    private NamespacedKey currentRecipeKey;

    /**
     * Simple per-attacker hit counter.  We no longer track per-target;
     * any player-hit increments the counter.  (UUID → cumulative hits)
     */
    private final Map<UUID, Integer> hitCounters = new HashMap<>();

    // ── Customizable recipe ──
    private String[] recipeShape = new String[]{
        " R ",
        "RBR",
        " R "
    };
    private char[] recipeChars = new char[]{' ', 'R', ' ', 'R', 'B', 'R', ' ', 'R', ' '};
    private Material recipeMaterial = Material.REDSTONE;
    private Material recipeHandle = Material.BLAZE_ROD;

    public VampireSwordManager(PGPlugin plugin) {
        this.plugin = plugin;
        this.currentRecipeKey = new NamespacedKey(plugin, "vampire_sword_recipe");
        registerRecipe();
    }

    // ─────────────────────────── Item helpers ───────────────────────────

    /** Create a fresh Vampire Sword ItemStack. */
    @SuppressWarnings("deprecation")
    public ItemStack createVampireSword() {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Vampire Sword").color(NamedTextColor.DARK_RED));
            meta.setCustomModelData(1001);

            // Base attack-damage bonus
            AttributeModifier damageMod = new AttributeModifier(
                UUID.randomUUID(),
                "vampire_sword_damage",
                3.0, // +3 damage
                AttributeModifier.Operation.ADD_NUMBER
            );
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageMod);

            // Lore explaining the mechanic
            meta.lore(List.of(
                Component.text("Hit players to build up blood.", NamedTextColor.GRAY),
                Component.text("Every 5th hit drains 4 HP", NamedTextColor.GRAY),
                Component.text("and heals you for 4 HP!", NamedTextColor.RED)
            ));

            sword.setItemMeta(meta);
        }
        return sword;
    }

    /** Check whether an ItemStack is a Vampire Sword. */
    public boolean isVampireSword(ItemStack item) {
        if (item == null || item.getType() != Material.IRON_SWORD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        return meta.displayName().toString().contains("Vampire Sword");
    }

    // ─────────────────────────── Recipe management ──────────────────────

    private void registerRecipe() {
        Recipe existing = plugin.getServer().getRecipe(currentRecipeKey);
        if (existing != null) {
            plugin.getServer().removeRecipe(currentRecipeKey);
        }

        ShapedRecipe recipe = new ShapedRecipe(currentRecipeKey, createVampireSword());
        recipe.shape(
            new String(recipeChars, 0, 3),
            new String(recipeChars, 3, 3),
            new String(recipeChars, 6, 3)
        );

        if (recipeMaterial != null) recipe.setIngredient('R', recipeMaterial);
        if (recipeHandle  != null) recipe.setIngredient('B', recipeHandle);

        plugin.getServer().addRecipe(recipe);
        plugin.getLogger().info("Vampire Sword recipe registered with material: " + recipeMaterial);
    }

    public boolean setRecipe(String material, String handle) {
        try {
            Material mat       = Material.getMaterial(material.toUpperCase());
            Material handleMat = Material.getMaterial(handle.toUpperCase());
            if (mat == null || handleMat == null) return false;

            this.recipeMaterial = mat;
            this.recipeHandle   = handleMat;

            registerRecipe();
            plugin.getLogger().info("Vampire Sword recipe changed to: " + mat + " + " + handleMat);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to set recipe: " + e.getMessage());
            return false;
        }
    }

    public String getRecipeInfo() {
        return "Current recipe: " + recipeMaterial + " + " + recipeHandle;
    }

    // ─────────────────────────── Combat handler ─────────────────────────

    /**
     * Handles every melee hit with the Vampire Sword.
     *
     * Key changes from original:
     * - ONLY counts hits against other Players (mobs are ignored entirely).
     * - Uses a single global counter per attacker (not per-target).
     * - Every 5th hit deals +4 HP bonus damage to the target and heals
     *   the attacker for 4 HP.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Must be a player attacking
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!isVampireSword(weapon)) return;

        // ── CHANGED: only works on other players, NOT mobs ──
        if (!(event.getEntity() instanceof Player victim)) return;

        // Don't count self-damage
        if (victim.getUniqueId().equals(attacker.getUniqueId())) return;

        UUID attackerId = attacker.getUniqueId();

        // Increment the attacker's cumulative hit counter
        int hits = hitCounters.getOrDefault(attackerId, 0) + 1;

        if (hits >= HITS_TO_DRAIN) {
            // ── 5th hit: trigger the drain effect ──

            // Reset counter first
            hitCounters.put(attackerId, 0);

            // Deal bonus damage to the victim (on top of the normal sword damage)
            double victimHealth = victim.getHealth();
            double newVictimHealth = Math.max(0.0, victimHealth - DRAIN_AMOUNT);
            victim.setHealth(newVictimHealth);

            // Heal the attacker (capped at max health)
            double maxHealth = Objects.requireNonNull(
                attacker.getAttribute(Attribute.MAX_HEALTH)).getValue();
            double newAttackerHealth = Math.min(attacker.getHealth() + DRAIN_AMOUNT, maxHealth);
            attacker.setHealth(newAttackerHealth);

            // ── Visual & audio feedback ──
            attacker.sendMessage(
                Component.text("§4§l☠ §cVampire Drain! §a+" + (int) DRAIN_AMOUNT + " HP §4§l☠")
            );
            victim.sendMessage(
                Component.text("§4" + attacker.getName() + "'s Vampire Sword drained your life!")
            );

            // Sound effects
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_WITHER_HURT, 0.7f, 1.2f);
            victim.playSound(victim.getLocation(), Sound.ENTITY_WITHER_HURT, 0.7f, 0.8f);

            // Red particle burst on the victim
            victim.getWorld().spawnParticle(
                Particle.DAMAGE_INDICATOR,
                victim.getLocation().add(0, 1, 0),
                15, 0.4, 0.5, 0.4, 0.1
            );

            // Green particles on the attacker (healing visual)
            attacker.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                attacker.getLocation().add(0, 1, 0),
                10, 0.3, 0.5, 0.3, 0.0
            );

            // Actionbar update showing reset
            sendDrainActionBar(attacker, 0);
        } else {
            // Normal hit — just update the counter
            hitCounters.put(attackerId, hits);

            // Show progress on actionbar
            sendDrainActionBar(attacker, hits);

            // Subtle tick sound
            attacker.playSound(attacker.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.6f + (hits * 0.2f));
        }
    }

    // ─────────────────────────── Actionbar display ──────────────────────

    /** Show the attacker their current drain-counter progress. */
    private void sendDrainActionBar(Player player, int hits) {
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < HITS_TO_DRAIN; i++) {
            bar.append(i < hits ? "§4■ " : "§8□ ");
        }
        String status = "§cBlood: §f" + hits + "/" + HITS_TO_DRAIN;
        player.sendActionBar(Component.text(bar.toString().trim() + "  " + status));
    }

    // ─────────────────────────── Crafting announcement ──────────────────

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (!isVampireSword(result)) return;

        if (event.getWhoClicked() instanceof Player crafter) {
            String announcement = String.format("§4§l☠ §c%s §4has crafted a §cVampire Sword§4! §4§l☠", crafter.getName());
            plugin.getServer().broadcast(Component.text(announcement));
            plugin.getLogger().info(crafter.getName() + " crafted a Vampire Sword");
        }
    }

    // ─────────────────────────── Cleanup ─────────────────────────────────

    public void handlePlayerQuit(Player player) {
        hitCounters.remove(player.getUniqueId());
    }
}
