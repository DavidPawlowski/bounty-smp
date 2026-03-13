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
import java.util.LinkedHashMap;
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
    
    // Default recipe: 3 netherite scrap in top row, 1 in middle center, 1 stick in bottom center
    // Customizable via command (9 slots, index 0–8 = top-left to bottom-right)
    private Material[] slots = new Material[]{
        Material.NETHERITE_SCRAP, Material.NETHERITE_SCRAP, Material.NETHERITE_SCRAP,
        null,                     Material.NETHERITE_SCRAP, null,
        null,                     Material.STICK,           null
    };
    
    public StunAxeManager(PGPlugin plugin) {
        this.plugin = plugin;
        this.currentRecipeKey = new NamespacedKey(plugin, "stun_axe_recipe");
        registerRecipe();
    }
    
    /**
     * Create the Stun Axe item stack
     */
    public ItemStack createStunAxe() {
        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
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
        if (item == null || item.getType() != Material.NETHERITE_AXE) {
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
        plugin.getServer().removeRecipe(currentRecipeKey);
        ShapedRecipe recipe = new ShapedRecipe(currentRecipeKey, createStunAxe());

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
        plugin.getLogger().info("Stun Axe recipe registered");
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