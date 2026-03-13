package com.playergames.paper;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Stores bounty data for a player
 */
public class BountyPlayer {
    private final UUID playerId;
    private UUID targetId;      // The player this player is hunting
    private UUID hunterId;      // The player hunting this player
    private int bounty;         // Current bounty level (0-5)
    private int streak;         // Consecutive target kills

    public BountyPlayer(UUID playerId) {
        this.playerId = playerId;
        this.targetId = null;
        this.hunterId = null;
        this.bounty = 0;
        this.streak = 0;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }

    public UUID getHunterId() {
        return hunterId;
    }

    public void setHunterId(UUID hunterId) {
        this.hunterId = hunterId;
    }

    public int getBounty() {
        return bounty;
    }

    public void setBounty(int bounty) {
        this.bounty = Math.min(bounty, 5);  // Max bounty is 5
    }

    public void incrementBounty() {
        if (bounty < 5) {
            bounty++;
        }
    }

    public void decrementBounty() {
        if (bounty > 0) {
            bounty--;
        }
    }

    public void resetBounty() {
        this.bounty = 0;
        this.streak = 0;
    }

    public int getStreak() {
        return streak;
    }

    public void incrementStreak() {
        this.streak++;
    }

    public void resetStreak() {
        this.streak = 0;
    }

    /**
     * Get the Player object for this bounty player's ID
     */
    public Player getPlayer() {
        return playerId != null ? org.bukkit.Bukkit.getPlayer(playerId) : null;
    }

    /**
     * Get the Player object for the target
     */
    public Player getTarget() {
        return targetId != null ? org.bukkit.Bukkit.getPlayer(targetId) : null;
    }

    /**
     * Get the Player object for the hunter
     */
    public Player getHunter() {
        return hunterId != null ? org.bukkit.Bukkit.getPlayer(hunterId) : null;
    }
}
