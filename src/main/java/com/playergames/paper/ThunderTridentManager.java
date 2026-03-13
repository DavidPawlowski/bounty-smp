package com.playergames.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the Thunder Trident weapon functionality
 * Left-clicking a player strikes lightning on them, even without a thunderstorm
 */
public class ThunderTridentManager implements Listener {
    private static final String THUNDER_TRIDENT_KEY = "thunder_trident";
    private static final long COOLDOWN_MILLIS = 5000L; // 5 seconds cooldown
    
    private final PGPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private NamespacedKey currentRecipeKey;
    
    // Default recipe: 3 tridents in top row, lightning rod in middle center, blaze rod in bottom center
    // Customizable via command (9 slots, index 0–8 = top-left to bottom-right)
    private Material[] slots = new Material[]{
        Material.TRIDENT, Material.TRIDENT,      Material.TRIDENT,
        null,             Material.LIGHTNING_ROD, null,
        null,             Material.BLAZE_ROD,     null
    };
    
    public ThunderTridentManager(PGPlugin plugin) {
        this.plugin = plugin;
        this.currentRecipeKey = new NamespacedKey(plugin, "thunder_trident_recipe");
        registerRecipe();
    }
    
    /**
     * Create the Thunder Trident item stack
     */
    public ItemStack createThunderTrident() {
        ItemStack trident = new ItemStack(Material.TRIDENT);
        ItemMeta meta = trident.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Thunder Trident").color(NamedTextColor.YELLOW));
            meta.setCustomModelData(3001);
            trident.setItemMeta(meta);
        }
        return trident;
    }
    
    /**
     * Check if an item is a Thunder Trident
     */
    public boolean isThunderTrident(ItemStack item) {
        if (item == null || item.getType() != Material.TRIDENT) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        return meta.displayName().toString().contains("Thunder Trident");
    }
    
    /**
     * Register the crafting recipe
     */
    private void registerRecipe() {
        plugin.getServer().removeRecipe(currentRecipeKey);
        ShapedRecipe recipe = new ShapedRecipe(currentRecipeKey, createThunderTrident());

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
        plugin.getLogger().info("Thunder Trident recipe registered");
    }

    /**
     * Change the crafting recipe via command (9 slot names)
     */
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

    /**
     * Get current recipe info
     */
    public String getRecipeInfo() {
        String[] names = new String[9];
        for (int i = 0; i < 9; i++) names[i] = slots[i] == null ? "AIR" : slots[i].name();
        return String.format("Row1: %s %s %s | Row2: %s %s %s | Row3: %s %s %s",
            names[0], names[1], names[2], names[3], names[4], names[5], names[6], names[7], names[8]);
    }
    
    /**
     * Handle left-click attack to strike lightning on target
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Check if a player attacked with a Thunder Trident
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        
        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (!isThunderTrident(item)) {
            return;
        }
        
        // Check for cooldown
        UUID playerId = attacker.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        if (cooldowns.containsKey(playerId)) {
            long lastUse = cooldowns.get(playerId);
            if (currentTime - lastUse < COOLDOWN_MILLIS) {
                // Still on cooldown
                return;
            }
        }
        
        // Get the entity being attacked
        Entity target = event.getEntity();
        if (target == null) {
            return;
        }
        
        // Strike lightning effect at the target's location (works even without thunderstorm)
        // Using strikeLightningEffect to avoid conflicts with plugins that modify lightning/damage events
        World world = target.getWorld();
        if (world != null) {
            // Use strikeLightningEffect which creates visual lightning without dealing damage
            world.strikeLightningEffect(target.getLocation());
            
            // Update cooldown
            cooldowns.put(playerId, currentTime);
            
            // Send message to attacker
            attacker.sendMessage(Component.text("⚡ You struck lightning on your target!").color(NamedTextColor.YELLOW));
        }
    }
    
    /**
     * Handle crafting event - broadcast when Thunder Trident is crafted
     */
    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        // Check if the crafted item is a Thunder Trident
        ItemStack result = event.getRecipe().getResult();
        if (!isThunderTrident(result)) {
            return;
        }
        
        // Get the player who crafted it
        if (event.getWhoClicked() instanceof Player crafter) {
            String playerName = crafter.getName();
            
            // Broadcast the crafting to the server
            Component broadcastMessage = Component.text("⚡ ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text(playerName).color(NamedTextColor.GOLD))
                .append(Component.text(" has crafted a Thunder Trident!").color(NamedTextColor.YELLOW));
            
            plugin.getServer().broadcast(broadcastMessage);
            plugin.getLogger().info(playerName + " crafted a Thunder Trident!");
        }
    }
    
    /**
     * Handle player quit - clean up cooldowns
     */
    public void handlePlayerQuit(Player player) {
        cooldowns.remove(player.getUniqueId());
    }
}