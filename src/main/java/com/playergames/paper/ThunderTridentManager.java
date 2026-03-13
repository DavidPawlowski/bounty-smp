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
    
    // Default recipe: 3 tridents in a row for the blade, 1 lightning rod in center, 1 stick at bottom
    // This is customizable via command
    private char[] recipeChars = new char[] {'T', 'T', 'T', ' ', 'L', ' ', ' ', 'S', ' '};
    private Material recipeIngredient = Material.TRIDENT;
    private Material recipeCore = Material.LIGHTNING_ROD;
    private Material recipeHandle = Material.STICK;
    
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
        // Remove existing recipe if any
        Recipe existing = plugin.getServer().getRecipe(currentRecipeKey);
        if (existing != null) {
            plugin.getServer().removeRecipe(currentRecipeKey);
        }
        
        // Create recipe with customizable shape
        ShapedRecipe recipe = new ShapedRecipe(currentRecipeKey, createThunderTrident());
        
        // Determine shape based on configuration
        recipe.shape(new String(recipeChars, 0, 3), 
                     new String(recipeChars, 3, 3), 
                     new String(recipeChars, 6, 3));
        
        // Set ingredient mappings
        if (recipeIngredient != null) {
            recipe.setIngredient('T', recipeIngredient);
        }
        if (recipeCore != null) {
            recipe.setIngredient('L', recipeCore);
        }
        if (recipeHandle != null) {
            recipe.setIngredient('S', recipeHandle);
        }
        
        plugin.getServer().addRecipe(recipe);
        plugin.getLogger().info("Thunder Trident recipe registered with: " + recipeIngredient + " + " + recipeCore + " + " + recipeHandle);
    }
    
    /**
     * Change the crafting recipe via command
     */
    public boolean setRecipe(String ingredient, String core, String handle) {
        try {
            Material ingotMat = Material.getMaterial(ingredient.toUpperCase());
            Material coreMat = Material.getMaterial(core.toUpperCase());
            Material handleMat = Material.getMaterial(handle.toUpperCase());
            
            if (ingotMat == null || coreMat == null || handleMat == null) {
                return false;
            }
            
            this.recipeIngredient = ingotMat;
            this.recipeCore = coreMat;
            this.recipeHandle = handleMat;
            
            registerRecipe();
            plugin.getLogger().info("Thunder Trident recipe changed to: " + ingotMat + " + " + coreMat + " + " + handleMat);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to set recipe: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get current recipe info
     */
    public String getRecipeInfo() {
        return "Current recipe: " + recipeIngredient + " + " + recipeCore + " + " + recipeHandle;
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