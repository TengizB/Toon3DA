package ge.tbegvadze.toon3d.progression;

import ge.tbegvadze.toon3d.util.GameBalance;

/**
 * Tracks the player's experience points, player level, and accumulated stat bonuses
 * from level-up reward choices.  Owned by {@code World}; read by {@code HudRenderer}
 * (via {@code HudState}) and consulted by the level-up overlay.
 *
 * <p>Calling {@link #addXp(int)} may set {@link #hasPendingLevelUp()} to {@code true}.
 * When that flag is set, World pauses the game and presents the reward overlay.
 * After the player picks a reward, World calls {@link #applyLevelUpReward(LevelUpReward)}
 * to advance the level and clear the flag.</p>
 */
public final class PlayerProgress {

    private int     playerLevel     = 1;
    private int     currentXp       = 0;
    private int     xpForNextLevel;
    private boolean pendingLevelUp  = false;
    private int     currentFloor    = 1;

    // Accumulated stat bonuses from all previous level-up choices
    private int flatDamageBonus = 0;

    public PlayerProgress() {
        xpForNextLevel = GameBalance.xpRequiredForLevel(playerLevel);
    }

    // =========================================================================
    // XP income
    // =========================================================================

    /**
     * Adds the given XP to the current pool.  Sets {@link #hasPendingLevelUp()} when
     * the threshold is crossed.  Does not advance level immediately — World does that
     * after the player picks a reward.
     */
    public void addXp(int amount) {
        if (amount <= 0) return;
        currentXp += amount;
        if (!pendingLevelUp && currentXp >= xpForNextLevel) {
            pendingLevelUp = true;
        }
    }

    // =========================================================================
    // Level-up resolution
    // =========================================================================

    /**
     * Applies the player's chosen reward, advances the player level, recalculates
     * the next XP threshold, and clears {@link #hasPendingLevelUp()}.
     *
     * <p>Excess XP (beyond the threshold) carries over so fast killers are not
     * penalised for overkill XP.</p>
     */
    public void applyLevelUpReward(LevelUpReward reward) {
        currentXp      = Math.max(0, currentXp - xpForNextLevel);
        playerLevel++;
        xpForNextLevel  = GameBalance.xpRequiredForLevel(playerLevel);
        pendingLevelUp  = false;

        if (reward == LevelUpReward.DAMAGE_BOOST) {
            flatDamageBonus += GameBalance.LEVEL_UP_DAMAGE_BONUS;
        }
        // HP_BOOST and ARMOR_BOOST are applied directly to the Player by World.
    }

    // =========================================================================
    // Queries
    // =========================================================================

    /** True when enough XP has been collected and the reward overlay should be shown. */
    public boolean hasPendingLevelUp() { return pendingLevelUp; }

    /** Current player level (starts at 1). */
    public int getPlayerLevel()        { return playerLevel; }

    /** XP collected toward the next level. */
    public int getCurrentXp()          { return currentXp; }

    /** XP required to trigger the next level-up. */
    public int getXpForNextLevel()     { return xpForNextLevel; }

    /**
     * Fraction of XP progress toward next level in [0, 1].
     * Used by the HUD XP bar fill calculation.
     */
    public float getXpFraction() {
        return xpForNextLevel == 0 ? 1f : Math.min(1f, (float) currentXp / xpForNextLevel);
    }

    /** Accumulated flat damage bonus (added to every weapon shot). */
    public int getFlatDamageBonus()    { return flatDamageBonus; }

    /** Current dungeon floor (1-based). Updated by World on each floor descent. */
    public int getFloorDepth()         { return currentFloor; }

    /** Called by World each time the player descends to a new floor. */
    public void setFloorDepth(int floor) { this.currentFloor = floor; }
}
