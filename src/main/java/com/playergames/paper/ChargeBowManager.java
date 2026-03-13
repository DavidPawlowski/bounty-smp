package com.playergames.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

/**
 * Manages the Charge Bow weapon functionality.
 *
 * Mechanic:
 * - Each successful arrow hit on a PLAYER increments a charge counter (0→4).
 * - After 4 player-hits the bow becomes "fully charged".
 * - The next arrow fired from a fully-charged bow is a Power 20 arrow.
 * - After the Power 20 arrow is fired, the counter resets to 0.
 * - Hitting mobs does NOT count toward the charge.
 * - The player sees an actionbar message with their current charge progress.
 * - A sound + particle effect plays when the bow becomes fully charged.
 */
public class ChargeBowManager implements Listener {

    private static final String CHARGE_BOW_KEY = "charge_bow";
    /** Number of player-hits needed to fully charge the bow */
    private static final int HITS_TO_CHARGE = 4;
    /** Power enchantment level applied to the charged shot */
    private static final int CHARGED_POWER_LEVEL = 20;

    /**
     * Metadata key placed on arrows shot from a Charge Bow so we can
     * identify them when they land on a target.
     */
    private static final String ARROW_META_KEY = "charge_bow_arrow";
    /**
     * Metadata key placed on the Power-20 arrow so we know not to
     * count that hit toward the next charge cycle.
     */
    private static final String POWERED_ARROW_META = "charge_bow_powered";

    private final PGPlugin plugin;
    private NamespacedKey currentRecipeKey;

    // Per-player charge counter (UUID → current charge 0-4)
    private final Map<UUID, Integer> chargeCounters = new HashMap<>();

    // ── Recipe configuration (9 slots, index 0–8 = top-left to bottom-right) ──
    // Default shape:  B_B / BEB / _S_
    private Material[] slots = new Material[]{
        Material.BONE, null,            Material.BONE,
        Material.BONE, Material.ENDER_EYE, Material.BONE,
        null,          Material.STICK,  null
    };

    public ChargeBowManager(PGPlugin plugin) {
        this.plugin = plugin;
        this.currentRecipeKey = new NamespacedKey(plugin, "charge_bow_recipe");
        registerRecipe();
    }

    // ─────────────────────────── Item helpers ───────────────────────────

    /** Create a fresh Charge Bow ItemStack. */
    public ItemStack createChargeBow() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Charge Bow").color(NamedTextColor.LIGHT_PURPLE));
            // Lore explaining the mechanic
            meta.lore(List.of(
                Component.text("Hit players with arrows to charge.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("After 4 hits, your next arrow", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("gains Power 20!", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)
            ));
            meta.setCustomModelData(4001);
            bow.setItemMeta(meta);
        }
        return bow;
    }

    /** Check whether an ItemStack is a Charge Bow. */
    public boolean isChargeBow(ItemStack item) {
        if (item == null || item.getType() != Material.BOW) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        return meta.displayName().toString().contains("Charge Bow");
    }

    // ─────────────────────────── Recipe management ──────────────────────

    private void registerRecipe() {
        plugin.getServer().removeRecipe(currentRecipeKey);
        ShapedRecipe recipe = new ShapedRecipe(currentRecipeKey, createChargeBow());

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
        plugin.getLogger().info("Charge Bow recipe registered");
    }

    /** Called from the command handler to update the recipe at runtime. */
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

    /** Human-readable recipe info for the /playergames chargebow command. */
    public String getRecipeInfo() {
        String[] names = new String[9];
        for (int i = 0; i < 9; i++) names[i] = slots[i] == null ? "AIR" : slots[i].name();
        return String.format("Row1: %s %s %s | Row2: %s %s %s | Row3: %s %s %s",
            names[0], names[1], names[2], names[3], names[4], names[5], names[6], names[7], names[8]);
    }

    // ─────────────────────────── Event handlers ─────────────────────────

    /**
     * When a player shoots an arrow from a Charge Bow, tag the arrow with
     * metadata so we can track it on hit.  If the bow is fully charged,
     * add Power 20 to the arrow and mark it as a powered shot.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) return;
        ItemStack bow = event.getBow();
        if (bow == null || !isChargeBow(bow)) return;

        // Tag every arrow from this bow so we can identify it on hit
        Entity projectile = event.getProjectile();
        projectile.setMetadata(ARROW_META_KEY, new FixedMetadataValue(plugin, shooter.getUniqueId().toString()));

        int charge = chargeCounters.getOrDefault(shooter.getUniqueId(), 0);

        // If fully charged (4 hits accumulated), make this the Power 20 shot
        if (charge >= HITS_TO_CHARGE) {
            if (projectile instanceof Arrow arrow) {
                // Apply Power 20 by adding the enchantment effect to the arrow
                // Arrow damage is base 2.0; Power adds (level+1)*0.5 per API, but
                // we use the Arrow#setDamage shortcut for a cleaner result.
                double baseDamage = arrow.getDamage();
                // Power formula: damage * (1 + 0.25*(level+1))  — mirrors vanilla Power calc
                arrow.setDamage(baseDamage * (1 + 0.25 * (CHARGED_POWER_LEVEL + 1)));
            }
            // Mark as powered so the hit-handler does NOT count it toward next charge
            projectile.setMetadata(POWERED_ARROW_META, new FixedMetadataValue(plugin, true));

            // Reset charge counter
            chargeCounters.put(shooter.getUniqueId(), 0);

            // Feedback: let the shooter know they fired the charged shot
            shooter.sendMessage(Component.text("⚡ CHARGED SHOT FIRED! ⚡").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            shooter.playSound(shooter.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.8f);

            // Update actionbar to show reset counter
            sendChargeActionBar(shooter, 0);
        }
    }

    /**
     * When an arrow from a Charge Bow hits a PLAYER, increment the charge
     * counter.  If the counter reaches 4, notify the shooter that the bow
     * is now fully charged with sound + particles.
     */
    @EventHandler(ignoreCancelled = true)
    public void onArrowHitPlayer(EntityDamageByEntityEvent event) {
        // Only care about arrows hitting players
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        // Check that this arrow came from a Charge Bow
        if (!arrow.hasMetadata(ARROW_META_KEY)) return;

        // Don't count the powered shot itself toward the next charge cycle
        if (arrow.hasMetadata(POWERED_ARROW_META)) return;

        // Retrieve the shooter's UUID from arrow metadata
        String shooterUUID = arrow.getMetadata(ARROW_META_KEY).get(0).asString();
        Player shooter = plugin.getServer().getPlayer(UUID.fromString(shooterUUID));
        if (shooter == null || !shooter.isOnline()) return;

        // Don't count self-damage (shouldn't normally happen, but just in case)
        if (shooter.getUniqueId().equals(victim.getUniqueId())) return;

        // ── Increment charge ──
        int newCharge = chargeCounters.getOrDefault(shooter.getUniqueId(), 0) + 1;
        chargeCounters.put(shooter.getUniqueId(), newCharge);

        // Show actionbar charge progress
        sendChargeActionBar(shooter, newCharge);

        if (newCharge >= HITS_TO_CHARGE) {
            // ── Bow is now fully charged! ──
            shooter.sendMessage(
                Component.text("✦ Charge Bow FULLY CHARGED! ✦")
                    .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
            );
            // Play a distinctive sound so the player knows
            shooter.playSound(shooter.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f);
            shooter.playSound(shooter.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);

            // Spawn particle effect around the shooter
            shooter.getWorld().spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                shooter.getLocation().add(0, 1, 0),
                30, 0.5, 0.5, 0.5, 0.1
            );
        } else {
            // Tick sound for each charge increment
            shooter.playSound(shooter.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.5f + (newCharge * 0.4f));
        }
    }

    // ─────────────────────────── Actionbar display ──────────────────────

    /** Send the player an actionbar message showing their current charge level. */
    private void sendChargeActionBar(Player player, int charge) {
        // Build a visual progress bar: ■ ■ ■ ■  (filled = gold, empty = gray)
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < HITS_TO_CHARGE; i++) {
            bar.append(i < charge ? "§6■ " : "§7□ ");
        }

        String status = charge >= HITS_TO_CHARGE ? "§6§lFULLY CHARGED!" : "§eCharge: §f" + charge + "/" + HITS_TO_CHARGE;
        player.sendActionBar(Component.text(bar.toString().trim() + "  " + status));
    }

    // ─────────────────────────── Crafting announcement ──────────────────

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (!isChargeBow(result)) return;

        if (event.getWhoClicked() instanceof Player crafter) {
            Component msg = Component.text("✦ ").color(NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(crafter.getName()).color(NamedTextColor.GOLD))
                .append(Component.text(" has crafted a ").color(NamedTextColor.LIGHT_PURPLE))
                .append(Component.text("Charge Bow").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
                .append(Component.text("! ✦").color(NamedTextColor.LIGHT_PURPLE));
            plugin.getServer().broadcast(msg);
            plugin.getLogger().info(crafter.getName() + " crafted a Charge Bow");
        }
    }

    // ─────────────────────────── Cleanup ─────────────────────────────────

    /** Remove charge counter when a player disconnects. */
    public void handlePlayerQuit(Player player) {
        chargeCounters.remove(player.getUniqueId());
    }
}
