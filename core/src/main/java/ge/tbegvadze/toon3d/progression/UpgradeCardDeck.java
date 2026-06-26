package ge.tbegvadze.toon3d.progression;

import ge.tbegvadze.toon3d.util.GameBalance;

import java.util.Random;

/**
 * Draws the level-up card offers (idea 5: build diversity & synergy).
 *
 * <h2>What it guarantees per draw</h2>
 * <ul>
 *   <li>Exactly {@code count} cards, all distinct.</li>
 *   <li>No two share a {@link UpgradeCard.Lever} — so the choice is always between distinct build
 *       directions, never two cards that do the same thing (anti-redundancy, design pillar 4/5).</li>
 *   <li>Cards are weighted toward pools the player has already invested in
 *       ({@link GameBalance#LEVEL_UP_DRAW_BIAS_PER_PICK} per prior pick), so a run's identity
 *       deepens as it goes instead of staying a random scatter.</li>
 * </ul>
 *
 * <h2>Budget invariant</h2>
 * The deck only ever draws from {@link UpgradeCard} values, every one of which is priced at the
 * fixed level-up budget. It performs no power arithmetic itself — the invariant "total PP at level
 * L = L × budget, build-independent" holds purely because the deck cannot offer an off-budget card.
 *
 * <h2>Allocation</h2>
 * The deck pre-allocates its scratch arrays once. {@link #draw(UpgradeCard[], int)} writes into a
 * caller-owned output array and allocates nothing, so it is safe to call on a level-up without
 * churning the heap. (It is never called from {@code render()} regardless.)
 */
public final class UpgradeCardDeck {

    private static final UpgradeCard[] ALL_CARDS = UpgradeCard.values();
    private static final int POOL_COUNT = UpgradeCardPool.values().length;
    private static final int LEVER_COUNT = UpgradeCard.Lever.values().length;

    private final Random random;

    /** How many times the player has picked a card from each pool, indexed by pool ordinal. */
    private final int[] poolPickCounts = new int[POOL_COUNT];

    // Pre-allocated scratch reused across draws — no per-draw allocation.
    private final float[] cardWeights   = new float[ALL_CARDS.length];
    private final boolean[] cardTaken    = new boolean[ALL_CARDS.length];
    private final boolean[] leverTaken   = new boolean[LEVER_COUNT];

    public UpgradeCardDeck(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Fills {@code out[0..count-1]} with distinct, distinct-lever cards biased toward the player's
     * emerging build. {@code count} is clamped to what the lever pool can actually supply.
     *
     * @param out   caller-owned output array (length ≥ count)
     * @param count how many cards to draw (typically {@link GameBalance#LEVEL_UP_CARDS_OFFERED})
     * @return the number of cards actually written (may be < count only if the roster is tiny)
     */
    public int draw(UpgradeCard[] out, int count) {
        for (int cardIndex = 0; cardIndex < cardTaken.length; cardIndex++) {
            cardTaken[cardIndex] = false;
        }
        for (int leverIndex = 0; leverIndex < leverTaken.length; leverIndex++) {
            leverTaken[leverIndex] = false;
        }

        int drawn = 0;
        while (drawn < count) {
            int picked = pickWeightedCard();
            if (picked < 0) {
                break; // no eligible card remains (all levers exhausted)
            }
            UpgradeCard card = ALL_CARDS[picked];
            cardTaken[picked]                = true;
            leverTaken[card.lever.ordinal()] = true;
            out[drawn++] = card;
        }
        return drawn;
    }

    /**
     * Records that the player chose a card, so future draws lean toward its pool. Call exactly once
     * per resolved level-up, after the chosen card's effect has been applied.
     */
    public void registerPick(UpgradeCard chosen) {
        poolPickCounts[chosen.pool.ordinal()]++;
    }

    // -------------------------------------------------------------------------
    // Weighted selection — every not-yet-taken card whose lever is still free is a
    // candidate; its weight grows with how often the player has picked its pool.
    // -------------------------------------------------------------------------
    private int pickWeightedCard() {
        float totalWeight = 0f;
        for (int cardIndex = 0; cardIndex < ALL_CARDS.length; cardIndex++) {
            UpgradeCard card = ALL_CARDS[cardIndex];
            boolean eligible = !cardTaken[cardIndex] && !leverTaken[card.lever.ordinal()];
            float weight = 0f;
            if (eligible) {
                // Base weight 1.0, plus a bonus per prior pick from this card's pool.
                weight = 1f + poolPickCounts[card.pool.ordinal()] * GameBalance.LEVEL_UP_DRAW_BIAS_PER_PICK;
            }
            cardWeights[cardIndex] = weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0f) {
            return -1;
        }
        float roll = random.nextFloat() * totalWeight;
        float cumulative = 0f;
        for (int cardIndex = 0; cardIndex < ALL_CARDS.length; cardIndex++) {
            cumulative += cardWeights[cardIndex];
            if (roll < cumulative && cardWeights[cardIndex] > 0f) {
                return cardIndex;
            }
        }
        // Floating-point guard: return the last eligible card if the roll edged past the sum.
        for (int cardIndex = ALL_CARDS.length - 1; cardIndex >= 0; cardIndex--) {
            if (cardWeights[cardIndex] > 0f) {
                return cardIndex;
            }
        }
        return -1;
    }
}
