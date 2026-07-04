package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.util.EnemyConstants;

/**
 * A tiny fixed-size ring buffer of an enemy's most recent SPECIAL-ability picks
 * (strategy-combat-order-5). This is the "no-repeat" memory that turns a move-set from RNG soup
 * into a readable, learnable rhythm (the Slay-the-Spire staple): when an enemy chooses its next
 * special it prefers the ability it has used LEAST recently, so a multi-ability caster rotates its
 * script rather than spamming one move, while a single-ability summoner is never blocked from acting.
 *
 * <p>One instance is created per {@link Enemy} at construction and reused for the enemy's whole
 * lifetime — {@link #record(SpecialAbility)} mutates the ring in place, so no allocation ever happens
 * inside {@code EnemyManager.takeTurn()} (project rule: no allocations in the turn loop).
 */
public final class MoveHistory {

    private final SpecialAbility[] recent;
    private int writeIndex = 0;

    public MoveHistory() {
        this.recent = new SpecialAbility[EnemyConstants.MOVE_HISTORY_SIZE];
    }

    /** Records the ability just committed, overwriting the oldest slot. */
    public void record(SpecialAbility ability) {
        recent[writeIndex] = ability;
        writeIndex = (writeIndex + 1) % recent.length;
    }

    /** Returns how many of the last {@code recent.length} picks were the given ability (0..size). */
    public int recentUses(SpecialAbility ability) {
        int count = 0;
        for (int index = 0; index < recent.length; index++) {
            if (recent[index] == ability) count++;
        }
        return count;
    }
}
