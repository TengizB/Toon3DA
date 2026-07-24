package ge.tbegvadze.toon3d.progression;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * Tracks the player's experience points, player level, and accumulated stat bonuses
 * from level-up reward choices.  Owned by {@code World}; read by {@code HudRenderer}
 * (via {@code HudState}) and consulted by the level-up overlay.
 *
 * <p>Calling {@link #addXp(int)} may set {@link #hasPendingLevelUp()} to {@code true}.
 * When that flag is set, World pauses the game and presents the level-up card overlay.
 * After the player picks a card, World applies its stat deltas and calls {@link #advanceLevel()}
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
     *
     * <p>Applies the CATCH-UP rubber band (new-game-balancr order 4): while the player is more than one
     * level BELOW the expected level for their current floor, incoming XP is scaled up by
     * {@link BalanceConfig#XP_CATCHUP_MULTIPLIER} so a fallen-behind run can recover. It is forward-only
     * — an at-or-ahead player is never slowed. See {@link GameMath#catchUpScaledXp}.</p>
     */
    public void addXp(int amount) {
        if (amount <= 0) return;
        int expectedLevel = GameMath.expectedLevelAtDepth(BalanceConfig.EXPECTED_LEVELS_PER_DEPTH, currentFloor);
        int awardedXp = GameMath.catchUpScaledXp(amount, playerLevel, expectedLevel,
                BalanceConfig.XP_CATCHUP_MULTIPLIER);
        currentXp += awardedXp;
        if (!pendingLevelUp && currentXp >= xpForNextLevel) {
            pendingLevelUp = true;
        }
    }

    // =========================================================================
    // Level-up resolution
    // =========================================================================

    /**
     * Advances the player level, recalculates the next XP threshold, and clears
     * {@link #hasPendingLevelUp()}. Call once after the chosen upgrade card's stat deltas
     * have been applied (World owns applying the deltas to the Player and stats).
     *
     * <p>Excess XP (beyond the threshold) carries over so fast killers are not
     * penalised for overkill XP.</p>
     */
    public void advanceLevel() {
        currentXp      = Math.max(0, currentXp - xpForNextLevel);
        playerLevel++;
        xpForNextLevel  = GameBalance.xpRequiredForLevel(playerLevel);
        pendingLevelUp  = false;
    }

    /**
     * Accumulates a flat per-shot damage bonus from an upgrade card (e.g. Hollow Points,
     * Glass Cannon). The running total is pushed to the EnemyManager by World so every shot
     * reflects it. Negative deltas are permitted for symmetry but no current card subtracts here.
     */
    public void addFlatDamageBonus(int amount) {
        flatDamageBonus += amount;
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
