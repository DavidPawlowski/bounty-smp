package com.playergames.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Manages the bounty system
 */
public class BountyManager implements Listener {
    private final PGPlugin plugin;
    private final Map<UUID, BountyPlayer> bountyPlayers = new HashMap<>();

    public BountyManager(PGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Get or create bounty data for a player
     */
    public BountyPlayer getBountyPlayer(Player player) {
        return bountyPlayers.computeIfAbsent(player.getUniqueId(), BountyPlayer::new);
    }

    /**
     * Assign a random target to a player
     */
    private void assignTarget(Player player) {
        BountyPlayer bp = getBountyPlayer(player);
        
        // Get all online players who don't have a hunter yet
        List<Player> availableTargets = new ArrayList<>();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) {
                continue; // Can't target yourself
            }
            BountyPlayer targetBp = getBountyPlayer(online);
            // Only target players who don't have a hunter
            if (targetBp.getHunterId() == null) {
                availableTargets.add(online);
            }
        }

        if (availableTargets.isEmpty()) {
            // No available targets, clear target
            bp.setTargetId(null);
            return;
        }

        // Pick a random target
        Random random = new Random();
        Player target = availableTargets.get(random.nextInt(availableTargets.size()));

        // Safety guard — should never trigger given the filter above, but belt-and-suspenders
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.getLogger().warning("[Bounty] Self-target detected and blocked for " + player.getName());
            bp.setTargetId(null);
            return;
        }

        bp.setTargetId(target.getUniqueId());
        
        // Set the player as the target's hunter
        BountyPlayer targetBp = getBountyPlayer(target);
        targetBp.setHunterId(player.getUniqueId());

        // Notify both players
        player.sendMessage(Component.text("§cYou have been assigned a bounty target: §e" + target.getName())
            .color(NamedTextColor.RED));
        target.sendMessage(Component.text("§c§lWARNING: §e" + player.getName() + " §chas been assigned to hunt you!")
            .color(NamedTextColor.RED));

        // Also remind the target who THEY are hunting
        if (targetBp.getTargetId() != null) {
            Player theirTarget = plugin.getServer().getPlayer(targetBp.getTargetId());
            if (theirTarget != null) {
                target.sendMessage(Component.text("§cYour target is: §e" + theirTarget.getName()));
            }
        }
    }

    /**
     * Apply bounty effects to a player based on their current bounty level.
     * Level 1: Fire Resistance I + Speed I
     * Level 2: Strength I
     * Level 3: +5 hearts (max health 30)
     * Level 4: Strength II
     * Level 5: +10 hearts total (max health 40) + Glowing
     */
    private void applyBountyEffects(Player player) {
        BountyPlayer bp = getBountyPlayer(player);
        int bounty = Math.min(bp.getBounty(), 5);

        removeBountyEffects(player);

        if (bounty == 0) return;

        if (bounty >= 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, true, false));
        }

        if (bounty >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, true, false));
        }

        if (bounty >= 3) {
            Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(30.0);
        }

        if (bounty >= 4) {
            player.removePotionEffect(PotionEffectType.STRENGTH);
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, true, false));
        }

        if (bounty >= 5) {
            Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(40.0);
            player.setGlowing(true);
        }
    }

    /**
     * Remove all bounty-related effects and reset max health to normal (20 HP / 10 hearts).
     */
    private void removeBountyEffects(Player player) {
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.setGlowing(false);

        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(20.0);
        }
    }

    /**
     * Handle player joining
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Assign a target if player doesn't have one
        BountyPlayer bp = getBountyPlayer(player);
        if (bp.getTargetId() == null && plugin.getServer().getOnlinePlayers().size() >= 2) {
            assignTarget(player);
        }

        // Re-apply bounty effects if they have bounty
        if (bp.getBounty() > 0) {
            applyBountyEffects(player);
        }
    }

    /**
     * Handle player death
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        BountyPlayer victimBp = getBountyPlayer(victim);

        // Capture state before reset
        int victimBounty = victimBp.getBounty();
        UUID victimHunterId = victimBp.getHunterId();

        // Always reset victim on any death (PvP or otherwise)
        victimBp.resetBounty();
        removeBountyEffects(victim);
        victimBp.setTargetId(null);
        victimBp.setHunterId(null);

        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }

        BountyPlayer killerBp = getBountyPlayer(killer);

        // Check if the victim was the killer's target (valid bounty kill)
        if (killerBp.getTargetId() != null && killerBp.getTargetId().equals(victim.getUniqueId())) {
            // Killer killed their target — gain bounty + take victim's bounty
            killerBp.incrementBounty();
            killerBp.incrementStreak();
            killerBp.setBounty(killerBp.getBounty() + victimBounty);

            killer.sendMessage(Component.text("§a§lBOUNTY CLAIMED! §7You killed your target. Your bounty is now §e" + killerBp.getBounty() + "§7. Streak: §e" + killerBp.getStreak())
                .color(NamedTextColor.GREEN));

            // Victim becomes the killer's new hunter
            killerBp.setHunterId(victim.getUniqueId());

            // Apply effects to killer and assign new target
            applyBountyEffects(killer);
            assignTarget(killer);
        }
        // Check if the victim was killed by their hunter (using captured value)
        else if (victimHunterId != null && victimHunterId.equals(killer.getUniqueId())) {
            // Victim was killed by their hunter - lose all bounty
            victim.sendMessage(Component.text("§c§lHUNTED DOWN! §7You were killed by your hunter and lost all your bounty (§e" + victimBounty + "§7).")
                .color(NamedTextColor.RED));

            // Hunter gains 1 bounty
            killerBp.incrementBounty();

            killer.sendMessage(Component.text("§a§lHUNT SUCCESSFUL! §7You killed your prey. Their bounty has been added to yours. Total: §e" + killerBp.getBounty())
                .color(NamedTextColor.GREEN));

            // Apply effects to hunter and assign new target
            applyBountyEffects(killer);
            assignTarget(killer);
        }
    }

    /**
     * Handle player respawn - re-apply effects if they have bounty
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        BountyPlayer bp = getBountyPlayer(player);
        
        if (bp.getBounty() > 0) {
            // Apply effects after a short delay to ensure respawn is complete
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                applyBountyEffects(player);
            }, 1L);
        }
    }

    /**
     * Handle player quitting - clear their hunter/target relationships
     */
    public void handlePlayerQuit(Player player) {
        BountyPlayer bp = getBountyPlayer(player);
        
        // If player had a target, clear that target's hunter
        if (bp.getTargetId() != null) {
            BountyPlayer targetBp = bountyPlayers.get(bp.getTargetId());
            if (targetBp != null && targetBp.getHunterId() != null && targetBp.getHunterId().equals(player.getUniqueId())) {
                targetBp.setHunterId(null);
            }
        }
        
        // If player had a hunter, clear that hunter's target
        if (bp.getHunterId() != null) {
            BountyPlayer hunterBp = bountyPlayers.get(bp.getHunterId());
            if (hunterBp != null && hunterBp.getTargetId() != null && hunterBp.getTargetId().equals(player.getUniqueId())) {
                hunterBp.setTargetId(null);
            }
        }
    }
}
