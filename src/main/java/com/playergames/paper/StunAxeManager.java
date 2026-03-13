package com.playergames.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the Stun Axe weapon functionality
 * Right-clicking a player stuns them for 1 second with 10-second cooldown
 */
public class StunAxeManager implements Listener {
    private static final String STUN_AXE_KEY = "stun_axe";
    private static final long COOLDOWN_MILLIS = 10000L; // 10 seconds
    private static final int STUN_DURATION_TICKS = 20; // 1 second (20 ticks per second)
    
    private final PGPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private NamespacedKey currentRecipeKey;
    
    // Default recipe: 3 diamonds in top row, 2 diamonds in middle row (center), 1 stick in bottom center
    // Customizable via command
    private char[] recipeChars = new char[] {'D', 'D', 'D', ' ', 'D', ' ', ' ', 'S', ' '};
    private Material recipeIngredient = Material.DIAMOND;
    private Material recipeHandle = Material.STICK;
    
    public StunAxeManager(PGPlugin plugin) {
        this.plugin = plugin;
        this.currentRecipeKey = new NamespacedKey(plugin, "stun_axe_recipe");
        registerRecipe();
    }
    
    /**
     * Create the Stun Axe item stack
     */
    public ItemStack createStunAxe() {
        ItemStack axe = new ItemStack(Material.DIAMOND_AXE);
        ItemMeta meta = axe.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Stun Axe").color(NamedTextColor.YELLOW));
            meta.setCustomModelData(2001);
            axe.setItemMeta(meta);
        }
        return axe;
    }
    
    /**
     * Check if an item is a Stun Axe
     */
    public boolean isStunAxe(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_AXE) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        return meta.displayName().toString().contains("Stun Axe");
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
        ShapedRecipe recipe = new ShapedRecipe(currentRecipeKey, createStunAxe());
        
        // Determine shape based on configuration
        recipe.shape(new String(recipeChars, 0, 3), 
                     new String(recipeChars, 3, 3), 
                     new String(recipeChars, 6, 3));
        
        // Set ingredient mappings
        if (recipeIngredient != null) {
            recipe.setIngredient('D', recipeIngredient);
        }
        if (recipeHandle != null) {
            recipe.setIngredient('S', recipeHandle);
        }
        
        plugin.getServer().addRecipe(recipe);
        plugin.getLogger().info("Stun Axe recipe registered with ingredient: " + recipeIngredient);
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
            plugin.getLogger().info("Stun Axe recipe changed to: " + ingotMat + " + " + handleMat);
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
     * Handle right-click to stun target player
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Check if player is right-clicking with a Stun Axe
        ItemStack item = event.getItem();
        if (item == null || !isStunAxe(item)) {
            return;
        }
        
        // Check for cooldown
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        Long lastUsed = cooldowns.get(playerId);
        if (lastUsed != null && (currentTime - lastUsed) < COOLDOWN_MILLIS) {
            // Still on cooldown
            long remainingSeconds = (COOLDOWN_MILLIS - (currentTime - lastUsed)) / 1000;
            player.sendMessage(Component.text("Stun Axe is on cooldown! Wait " + remainingSeconds + " seconds.").color(NamedTextColor.RED));
            return;
        }
        
        // Get the target player (the entity the player is looking at)
        org.bukkit.entity.Entity targetEntity = player.getTargetEntity(10);
        
        if (!(targetEntity instanceof Player)) {
            player.sendMessage(Component.text("No player in sight to stun!").color(NamedTextColor.RED));
            return;
        }
        
        Player target = (Player) targetEntity;
        
        // Cannot stun yourself
        if (target.getUniqueId().equals(playerId)) {
            player.sendMessage(Component.text("You cannot stun yourself!").color(NamedTextColor.RED));
            return;
        }
        
        // Apply stun effect - use SLOWNESS to simulate stun (immobilizes player)
        // Using slowness 255 for 1 second makes it very difficult to move
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, STUN_DURATION_TICKS, 255, true, true));
        // Also add weakness to prevent attacking while stunned
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, STUN_DURATION_TICKS, 10, true, true));
        
        // Set cooldown
        cooldowns.put(playerId, currentTime);
        
        // Send feedback
        player.sendMessage(Component.text("You stunned " + target.getName() + " for 1 second!").color(NamedTextColor.YELLOW));
        target.sendMessage(Component.text("You have been stunned by " + player.getName() + "!").color(NamedTextColor.RED));
        
        // Cancel the event to prevent conflicts with other right-click handlers
        event.setCancelled(true);
    }
    
    /**
     * Handle crafting event - announce when Stun Axe is crafted
     */
    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        // Check if the crafted item is a Stun Axe
        ItemStack result = event.getRecipe().getResult();
        if (!isStunAxe(result)) {
            return;
        }
        
        // Get the player who crafted it
        if (event.getWhoClicked() instanceof Player) {
            Player crafter = (Player) event.getWhoClicked();
            
            // Broadcast to all players
            String announcement = String.format("%s has crafted a %s!", crafter.getName(), "Stun Axe");
            plugin.getServer().broadcast(Component.text(announcement).color(NamedTextColor.GOLD));
            
            plugin.getLogger().info(crafter.getName() + " crafted a Stun Axe");
        }
    }
    
    /**
     * Clean up player cooldowns on quit
     */
    public void handlePlayerQuit(Player player) {
        cooldowns.remove(player.getUniqueId());
    }
}