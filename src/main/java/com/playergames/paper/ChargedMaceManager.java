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
import java.util.LinkedHashMap;
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
    
    // Default recipe: 3 iron ingots in top row, 1 in middle center, 1 stick in bottom center
    // Customizable via command (9 slots, index 0–8 = top-left to bottom-right)
    private Material[] slots = new Material[]{
        Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT,
        null,                Material.IRON_INGOT, null,
        null,                Material.STICK,      null
    };
    
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
        plugin.getServer().removeRecipe(currentRecipeKey);
        ShapedRecipe recipe = new ShapedRecipe(currentRecipeKey, createChargedMace());

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
        plugin.getLogger().info("Charged Mace recipe registered");
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
            String announcement = String.format("§6§l★ §e%s §6has crafted a §bCharged Mace§6! §6§l★", crafter.getName());
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