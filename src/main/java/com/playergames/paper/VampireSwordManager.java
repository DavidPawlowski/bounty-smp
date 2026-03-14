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

import org.bukkit.configuration.ConfigurationSection;

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

    // ── Customizable recipe (9 slots, index 0–8 = top-left to bottom-right) ──
    private Material[] slots = new Material[]{
        null,             Material.REDSTONE,  null,
        Material.REDSTONE, Material.BLAZE_ROD, Material.REDSTONE,
        null,             Material.REDSTONE,  null
    };

    public VampireSwordManager(PGPlugin plugin) {
        this.plugin = plugin;
        this.currentRecipeKey = new NamespacedKey(plugin, "vampire_sword_recipe");
        registerRecipe();
    }

    // ─────────────────────────── Item helpers ───────────────────────────

    /** Create a fresh Vampire Sword ItemStack. */
    @SuppressWarnings("deprecation")
    public ItemStack createVampireSword() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
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
        if (item == null || item.getType() != Material.NETHERITE_SWORD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        return meta.displayName().toString().contains("Vampire Sword");
    }

    // ─────────────────────────── Recipe management ──────────────────────

    private void registerRecipe() {
        plugin.getServer().removeRecipe(currentRecipeKey);
        ShapedRecipe recipe = new ShapedRecipe(currentRecipeKey, createVampireSword());

        Map<Material, Character> charMap = new LinkedHashMap<>();
        char nextChar = 'A';
        char[][] grid = new char[3][3];
        for (int i = 0; i < 9; i++) {
            Material mat = slots[i];
            if (mat == null) {
                grid[i / 3][i % 3] = ' ';
            } else {
                charMap.putIfAbsent(mat, nextChar++);
                grid[i / 3][i % 3] = charMap.get(mat);
            }
        }

        recipe.shape(new String(grid[0]), new String(grid[1]), new String(grid[2]));
        charMap.forEach((mat, ch) -> recipe.setIngredient(ch, mat));
        plugin.getServer().addRecipe(recipe);
        plugin.getLogger().info("Vampire Sword recipe registered");
    }

    public boolean setRecipe(String[] slotNames) {
        if (slotNames.length != 9) return false;
        Material[] newSlots = new Material[9];
        for (int i = 0; i < 9; i++) {
            if (slotNames[i].equalsIgnoreCase("AIR") || slotNames[i].equalsIgnoreCase("NONE")) {
                newSlots[i] = null;
            } else {
                newSlots[i] = Material.getMaterial(slotNames[i].toUpperCase());
                if (newSlots[i] == null) return false;
            }
        }
        this.slots = newSlots;
        registerRecipe();
        return true;
    }

    public String getRecipeInfo() {
        String[] names = new String[9];
        for (int i = 0; i < 9; i++) names[i] = slots[i] == null ? "AIR" : slots[i].name();
        return String.format("Row1: %s %s %s | Row2: %s %s %s | Row3: %s %s %s",
            names[0], names[1], names[2], names[3], names[4], names[5], names[6], names[7], names[8]);
    }

    /**
     * Load the recipe from a config section using 'shape' and 'ingredients' keys.
     * shape: list of 3 strings (3 chars each); space = empty slot.
     * ingredients: map of single char → Bukkit Material name.
     * Logs a warning and keeps the current recipe if anything is invalid.
     */
    public void loadRecipeFromConfig(ConfigurationSection section) {
        if (section == null) return;
        List<String> shapeList = section.getStringList("shape");
        if (shapeList.size() != 3) {
            plugin.getLogger().warning("[VampireSword] 'shape' must have exactly 3 rows in config.yml — keeping current recipe.");
            return;
        }
        ConfigurationSection ingSection = section.getConfigurationSection("ingredients");
        if (ingSection == null) {
            plugin.getLogger().warning("[VampireSword] 'ingredients' section missing in config.yml — keeping current recipe.");
            return;
        }
        Map<Character, Material> ingredientMap = new LinkedHashMap<>();
        for (String key : ingSection.getKeys(false)) {
            if (key.length() != 1) {
                plugin.getLogger().warning("[VampireSword] Ingredient key '" + key + "' must be a single character — keeping current recipe.");
                return;
            }
            String matName = ingSection.getString(key, "");
            try {
                ingredientMap.put(key.charAt(0), Material.valueOf(matName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[VampireSword] Invalid material '" + matName + "' for ingredient '" + key + "' — keeping current recipe.");
                return;
            }
        }
        Material[] newSlots = new Material[9];
        for (int row = 0; row < 3; row++) {
            String rowStr = shapeList.get(row);
            while (rowStr.length() < 3) rowStr += " ";
            for (int col = 0; col < 3; col++) {
                char ch = rowStr.charAt(col);
                if (ch == ' ') {
                    newSlots[row * 3 + col] = null;
                } else if (ingredientMap.containsKey(ch)) {
                    newSlots[row * 3 + col] = ingredientMap.get(ch);
                } else {
                    plugin.getLogger().warning("[VampireSword] Shape character '" + ch + "' has no matching ingredient — keeping current recipe.");
                    return;
                }
            }
        }
        this.slots = newSlots;
        registerRecipe();
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
