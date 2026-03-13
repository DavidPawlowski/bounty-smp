package com.playergames.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for Bounty SMP.
 *
 * Changes in this iteration:
 * - Added ChargeBowManager (custom bow with 4-hit charge → Power 20 shot).
 * - Added /playergames chargebow subcommand for recipe management.
 * - Added /playergames reload subcommand to hot-reload config.yml recipes.
 * - Recipes for the Charge Bow are persisted to config.yml.
 * - Updated quit handler to clean up Charge Bow data.
 */
public final class PGPlugin extends JavaPlugin implements CommandExecutor {
    private static final String COMMAND_NAME = "playergames";

    private BountyManager bountyManager;
    private ChargedMaceManager chargedMaceManager;
    private VampireSwordManager vampireSwordManager;
    private StunAxeManager stunAxeManager;
    private ThunderTridentManager thunderTridentManager;
    private ChargeBowManager chargeBowManager;           // ← NEW

    @Override
    public void onEnable() {
        getLogger().info(String.format("Enabling Bounty SMP plugin v%s for Paper",
            getPluginMeta().getVersion()));

        // ── Save default config.yml so the file exists on first run ──
        saveDefaultConfig();

        // Initialize all weapon managers
        bountyManager = new BountyManager(this);
        getServer().getPluginManager().registerEvents(bountyManager, this);

        chargedMaceManager = new ChargedMaceManager(this);
        getServer().getPluginManager().registerEvents(chargedMaceManager, this);

        vampireSwordManager = new VampireSwordManager(this);
        getServer().getPluginManager().registerEvents(vampireSwordManager, this);

        stunAxeManager = new StunAxeManager(this);
        getServer().getPluginManager().registerEvents(stunAxeManager, this);

        thunderTridentManager = new ThunderTridentManager(this);
        getServer().getPluginManager().registerEvents(thunderTridentManager, this);

        // ── NEW: Charge Bow manager ──
        chargeBowManager = new ChargeBowManager(this);
        getServer().getPluginManager().registerEvents(chargeBowManager, this);

        // Load saved recipes from config.yml (Charge Bow recipe)
        loadRecipesFromConfig();

        // Register quit listener to clean up all per-player data
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent event) {
                bountyManager.handlePlayerQuit(event.getPlayer());
                chargedMaceManager.handlePlayerQuit(event.getPlayer());
                vampireSwordManager.handlePlayerQuit(event.getPlayer());
                stunAxeManager.handlePlayerQuit(event.getPlayer());
                thunderTridentManager.handlePlayerQuit(event.getPlayer());
                chargeBowManager.handlePlayerQuit(event.getPlayer());   // ← NEW
            }
        }, this);

        PluginCommand command = getCommand(COMMAND_NAME);
        if (command != null) {
            command.setExecutor(this);
        } else {
            getLogger().warning("Command /" + COMMAND_NAME + " missing from plugin.yml");
        }

        getLogger().info("Bounty SMP enabled! Systems: Bounty, Charged Mace, Vampire Sword, Stun Axe, Thunder Trident, Charge Bow");
    }

    // ════════════════════════════════════════════════════════════════════
    //  Config.yml persistence for recipes
    // ════════════════════════════════════════════════════════════════════

    /**
     * Load Charge Bow recipe values from config.yml.
     * If the keys don't exist yet the current defaults are kept.
     */
    private void loadRecipesFromConfig() {
        FileConfiguration cfg = getConfig();

        // ── Charge Bow recipe ──
        String bowIng    = cfg.getString("chargebow.recipe.ingredient");
        String bowCore   = cfg.getString("chargebow.recipe.core");
        String bowHandle = cfg.getString("chargebow.recipe.handle");

        if (bowIng != null && bowCore != null && bowHandle != null) {
            Material ingMat    = Material.getMaterial(bowIng.toUpperCase());
            Material coreMat   = Material.getMaterial(bowCore.toUpperCase());
            Material handleMat = Material.getMaterial(bowHandle.toUpperCase());

            if (ingMat != null && coreMat != null && handleMat != null) {
                chargeBowManager.loadRecipeFromConfig(ingMat, coreMat, handleMat);
                getLogger().info("Loaded Charge Bow recipe from config: " + ingMat + " + " + coreMat + " + " + handleMat);
            } else {
                getLogger().warning("Invalid Charge Bow recipe materials in config.yml — using defaults.");
            }
        }
    }

    /**
     * Save the current Charge Bow recipe to config.yml so it persists across restarts.
     */
    private void saveChargeBowRecipeToConfig() {
        FileConfiguration cfg = getConfig();
        cfg.set("chargebow.recipe.ingredient", chargeBowManager.getRecipeIngredient().name());
        cfg.set("chargebow.recipe.core",       chargeBowManager.getRecipeCore().name());
        cfg.set("chargebow.recipe.handle",     chargeBowManager.getRecipeHandle().name());
        saveConfig();
    }

    // ════════════════════════════════════════════════════════════════════
    //  Command handling
    // ════════════════════════════════════════════════════════════════════

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!COMMAND_NAME.equalsIgnoreCase(command.getName())) return false;

        // ── Base command — show help ──
        if (args.length == 0) {
            sender.sendMessage(Component.text("This server is running Bounty SMP - a bounty hunter plugin"));
            sender.sendMessage(Component.text("Use /playergames mace to manage Charged Mace recipes"));
            sender.sendMessage(Component.text("Use /playergames vampire to manage Vampire Sword recipes"));
            sender.sendMessage(Component.text("Use /playergames stunaxe to manage Stun Axe recipes"));
            sender.sendMessage(Component.text("Use /playergames thundertrident to manage Thunder Trident recipes"));
            sender.sendMessage(Component.text("Use /playergames chargebow to manage Charge Bow recipes"));   // NEW
            sender.sendMessage(Component.text("Use /playergames reload to reload recipes from config"));       // NEW
            return true;
        }

        // ══════════════════════════════════════════════════════════════
        //  /playergames reload  — hot-reload recipes from config.yml
        // ══════════════════════════════════════════════════════════════
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("playergames.reload")) {
                sender.sendMessage(Component.text("§cYou don't have permission to use this command"));
                return true;
            }

            // Re-read config.yml from disk, then re-apply recipes
            reloadConfig();
            loadRecipesFromConfig();

            sender.sendMessage(Component.text("§aRecipes reloaded from config.yml!").color(NamedTextColor.GREEN));
            return true;
        }

        // ══════════════════════════════════════════════════════════════
        //  /playergames chargebow [set <ingredient> <core> <handle>]
        // ══════════════════════════════════════════════════════════════
        if (args[0].equalsIgnoreCase("chargebow")) {
            if (!sender.hasPermission("playergames.chargebow")) {
                sender.sendMessage(Component.text("§cYou don't have permission to use this command"));
                return true;
            }

            if (args.length == 1) {
                // Show current recipe
                sender.sendMessage(Component.text("§d--- Charge Bow Recipe ---"));
                sender.sendMessage(Component.text(chargeBowManager.getRecipeInfo()));
                sender.sendMessage(Component.text("§eUsage: /playergames chargebow set <ingredient> <core> <handle>"));
                return true;
            }

            if (args.length == 5 && args[1].equalsIgnoreCase("set")) {
                String ingredient = args[2];
                String core       = args[3];
                String handle     = args[4];

                if (chargeBowManager.setRecipe(ingredient, core, handle)) {
                    // Persist to config.yml so it survives restarts
                    saveChargeBowRecipeToConfig();

                    sender.sendMessage(Component.text("§aRecipe updated! §eNew recipe: " + ingredient + " + " + core + " + " + handle));
                    getServer().broadcast(Component.text(
                        "§d§lThe Charge Bow recipe has been changed! §eNew: " + ingredient + " + " + core + " + " + handle
                    ));
                } else {
                    sender.sendMessage(Component.text("§cFailed to set recipe. Check material names."));
                }
                return true;
            }

            sender.sendMessage(Component.text("§eUsage: /playergames chargebow set <ingredient> <core> <handle>"));
            return true;
        }

        // ══════════════════════════════════════════════════════════════
        //  Existing subcommands (unchanged)
        // ══════════════════════════════════════════════════════════════

        // Handle Charged Mace subcommands
        if (args[0].equalsIgnoreCase("mace")) {
            if (!sender.hasPermission("playergames.mace")) {
                sender.sendMessage(Component.text("§cYou don't have permission to use this command"));
                return true;
            }

            if (args.length == 1) {
                sender.sendMessage(Component.text("§6--- Charged Mace Recipe ---"));
                sender.sendMessage(Component.text(chargedMaceManager.getRecipeInfo()));
                sender.sendMessage(Component.text("§eUsage: /playergames mace set <ingredient> <handle>"));
                return true;
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
                String ingredient = args[2];
                String handle = args[3];

                if (chargedMaceManager.setRecipe(ingredient, handle)) {
                    sender.sendMessage(Component.text("§aRecipe updated! §eNew recipe: " + ingredient + " + " + handle));
                    getServer().broadcast(Component.text("§6§lThe Charged Mace recipe has been changed! §eNew: " + ingredient + " + " + handle));
                } else {
                    sender.sendMessage(Component.text("§cFailed to set recipe. Check material names."));
                }
                return true;
            }

            sender.sendMessage(Component.text("§eUsage: /playergames mace set <ingredient> <handle>"));
            return true;
        }

        // Handle Vampire Sword subcommands
        if (args[0].equalsIgnoreCase("vampire")) {
            if (!sender.hasPermission("playergames.vampire")) {
                sender.sendMessage(Component.text("§cYou don't have permission to use this command"));
                return true;
            }

            if (args.length == 1) {
                sender.sendMessage(Component.text("§4--- Vampire Sword Recipe ---"));
                sender.sendMessage(Component.text(vampireSwordManager.getRecipeInfo()));
                sender.sendMessage(Component.text("§eUsage: /playergames vampire set <material> <handle>"));
                return true;
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
                String material = args[2];
                String handle = args[3];

                if (vampireSwordManager.setRecipe(material, handle)) {
                    sender.sendMessage(Component.text("§aRecipe updated! §eNew recipe: " + material + " + " + handle));
                    getServer().broadcast(Component.text("§4§lThe Vampire Sword recipe has been changed! §eNew: " + material + " + " + handle));
                } else {
                    sender.sendMessage(Component.text("§cFailed to set recipe. Check material names."));
                }
                return true;
            }

            sender.sendMessage(Component.text("§eUsage: /playergames vampire set <material> <handle>"));
            return true;
        }

        // Handle Stun Axe subcommands
        if (args[0].equalsIgnoreCase("stunaxe")) {
            if (!sender.hasPermission("playergames.stunaxe")) {
                sender.sendMessage(Component.text("§cYou don't have permission to use this command"));
                return true;
            }

            if (args.length == 1) {
                sender.sendMessage(Component.text("§6--- Stun Axe Recipe ---"));
                sender.sendMessage(Component.text(stunAxeManager.getRecipeInfo()));
                sender.sendMessage(Component.text("§eUsage: /playergames stunaxe set <ingredient> <handle>"));
                return true;
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
                String ingredient = args[2];
                String handle = args[3];

                if (stunAxeManager.setRecipe(ingredient, handle)) {
                    sender.sendMessage(Component.text("§aRecipe updated! §eNew recipe: " + ingredient + " + " + handle));
                    getServer().broadcast(Component.text("§6§lThe Stun Axe recipe has been changed! §eNew: " + ingredient + " + " + handle));
                } else {
                    sender.sendMessage(Component.text("§cFailed to set recipe. Check material names."));
                }
                return true;
            }

            sender.sendMessage(Component.text("§eUsage: /playergames stunaxe set <ingredient> <handle>"));
            return true;
        }

        // Handle Thunder Trident subcommands
        if (args[0].equalsIgnoreCase("thundertrident")) {
            if (!sender.hasPermission("playergames.thundertrident")) {
                sender.sendMessage(Component.text("§cYou don't have permission to use this command"));
                return true;
            }

            if (args.length == 1) {
                sender.sendMessage(Component.text("§6--- Thunder Trident Recipe ---"));
                sender.sendMessage(Component.text(thunderTridentManager.getRecipeInfo()));
                sender.sendMessage(Component.text("§eUsage: /playergames thundertrident set <ingredient> <core> <handle>"));
                return true;
            }

            if (args.length == 5 && args[1].equalsIgnoreCase("set")) {
                String ingredient = args[2];
                String core = args[3];
                String handle = args[4];

                if (thunderTridentManager.setRecipe(ingredient, core, handle)) {
                    sender.sendMessage(Component.text("§aRecipe updated! §eNew recipe: " + ingredient + " + " + core + " + " + handle));
                    getServer().broadcast(Component.text("§6§lThe Thunder Trident recipe has been changed! §eNew: " + ingredient + " + " + core + " + " + handle));
                } else {
                    sender.sendMessage(Component.text("§cFailed to set recipe. Check material names."));
                }
                return true;
            }

            sender.sendMessage(Component.text("§eUsage: /playergames thundertrident set <ingredient> <core> <handle>"));
            return true;
        }

        sender.sendMessage(Component.text("This server is running Bounty SMP - a bounty hunter plugin"));
        return true;
    }
}
