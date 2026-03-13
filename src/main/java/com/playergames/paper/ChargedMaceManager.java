package com.playergames.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the Charged Mace weapon functionality
 */
public class ChargedMaceManager implements Listener {
    private static final String MACE_ITEM_KEY = "charged_mace";
    private static final long COOLDOWN_TICKS = 100L; // 5 seconds (20 ticks per second)
    
    private final PGPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private NamespacedKey currentRecipeKey;
    
    // Default recipe: 3 iron ingots in top row, 1 iron ingot in center, 1 stick in bottom center
    // This is customizable via command
    private String[] recipeShape = new String[] {
        "III",
        " I ",
        " S "
    };
    private char[] recipeChars = new char[] {'I', 'I', 'I', ' ', 'I', ' ', ' ', 'S', ' '};
    private Material recipeIngredient = Material.IRON_INGOT;
    private Material recipeHandle = Material.STICK;
    
    public ChargedMaceManager(PGPlugin plugin) {
        this.plugin = plugin;
        this.currentRecipeKey = new NamespacedKey(plugin, "charged_mace_recipe");
        registerRecipe();
    }
    
    /**
     * Create the Charged Mace item stack
     */
    public ItemStack createChargedMace() {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta meta = mace.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Charged Mace").color(NamedTextColor.AQUA));
            meta.setCustomModelData(1);
            mace.setItemMeta(meta);
        }
        return mace;
    }
    
    /**
     * Check if an item is a Charged Mace
     */
    public boolean isChargedMace(ItemStack item) {
        if (item == null || item.getType() != Material.MACE) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        return meta.displayName().toString().contains("Charged Mace");
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
        ShapedRecipe recipe = new ShapedRecipe(currentRecipeKey, createChargedMace());
        
        // Determine shape based on configuration
        recipe.shape(new String(recipeChars, 0, 3), 
                     new String(recipeChars, 3, 3), 
                     new String(recipeChars, 6, 3));
        
        // Set ingredient mappings
        if (recipeIngredient != null) {
            recipe.setIngredient('I', recipeIngredient);
        }
        if (recipeHandle != null) {
            recipe.setIngredient('S', recipeHandle);
        }
        
        plugin.getServer().addRecipe(recipe);
        plugin.getLogger().info("Charged Mace recipe registered with ingredient: " + recipeIngredient);
    }
    
    /**
     * Change the crafting recipe via command
     */
    public boolean setRecipe(String ingredient, String handle) {
        try {
            Material ingotMat = Material.getMaterial(ingredient.toUpperCase());
            Material handleMat = Material.getMaterial(handle.toUpperCase());
            
            if (ingotMat == null || handleMat == null) {
                return false;
            }
            
            this.recipeIngredient = ingotMat;
            this.recipeHandle = handleMat;
            
            registerRecipe();
            plugin.getLogger().info("Charged Mace recipe changed to: " + ingotMat + " + " + handleMat);
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
        return "Current recipe: " + recipeIngredient + " + " + recipeHandle;
    }
    
    /**
     * Handle right-click to launch wind charge
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Check if player is right-clicking with a Charged Mace
        ItemStack item = event.getItem();
        if (item == null || !isChargedMace(item)) {
            return;
        }
        
        // Check for cooldown
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        Long lastUsed = cooldowns.get(playerId);
        if (lastUsed != null && (currentTime - lastUsed) < (COOLDOWN_TICKS * 50)) {
            // Still on cooldown
            player.sendMessage(Component.text("§cCharged Mace is on cooldown!").color(NamedTextColor.RED));
            return;
        }
        
        // Launch Wind Charge
        WindCharge windCharge = player.launchProjectile(WindCharge.class);
        windCharge.setVelocity(player.getLocation().getDirection().multiply(1.5));
        
        // Set cooldown
        cooldowns.put(playerId, currentTime);
        
        // Send feedback
        player.sendMessage(Component.text("§aWind Charge launched!").color(NamedTextColor.GREEN));
        
        // Cancel the event to prevent conflicts with other right-click handlers
        event.setCancelled(true);
    }
    
    /**
     * Handle crafting event - announce when Charged Mace is crafted
     */
    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        // Check if the crafted item is a Charged Mace
        ItemStack result = event.getRecipe().getResult();
        if (!isChargedMace(result)) {
            return;
        }
        
        // Get the player who crafted it
        if (event.getWhoClicked() instanceof Player) {
            Player crafter = (Player) event.getWhoClicked();
            
            // Broadcast to all players
            String announcement = String.format("§6§l★ §e%s §6has crafted a §bCharged Mace§6! §6§l★");
            plugin.getServer().broadcast(Component.text(announcement));
            
            plugin.getLogger().info(crafter.getName() + " crafted a Charged Mace");
        }
    }
    
    /**
     * Clean up player cooldowns on quit
     */
    public void handlePlayerQuit(Player player) {
        cooldowns.remove(player.getUniqueId());
    }
}