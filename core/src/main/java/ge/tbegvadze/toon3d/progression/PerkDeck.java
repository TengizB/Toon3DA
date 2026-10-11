package ge.tbegvadze.toon3d.progression;

import ge.tbegvadze.toon3d.util.BalanceConfig;

import java.util.EnumSet;
import java.util.Random;

/**
 * Draws the level-up PERK offers (balance-overhaul order 5, rule B3) — the replacement for the retired
 * budget-equal card deck.
 *
 * <h2>What every draw guarantees</h2>
 * <ul>
 *   <li>Up to {@link BalanceConfig#PERKS_OFFERED} perks, all distinct, none already at its max rank.</li>
 *   <li>At least one SYNERGY perk — its tag is one the player's carried weapons belong to
 *       ({@link #isSynergy}). EDGE CASE: when every perk of every synergy tag is maxed (or nothing is
 *       carried), that slot falls back to any unmaxed perk.</li>
 *   <li>At least one PIVOT perk — from a tag the player holds NO ranks in. The deck prefers a pivot that is
 *       not also a synergy tag (a real change of course), then any zero-rank tag, then any unmaxed perk.</li>
 *   <li>The remaining slot(s) are weighted toward the tags the player already leans into:
 *       weight = 1 + ranks held in the perk's tag x {@link BalanceConfig#PERK_DRAW_BIAS_PER_PICK} (the old
 *       card deck's draw bias, kept as a weight).</li>
 * </ul>
 * The drawn perks are then shuffled so the synergy slot is not always the left-most plate.
 *
 * <p>Seeded (same salt World / SimWorld used for the card deck) so a run replays its offers exactly.
 * Allocation-free per draw: the weight and taken scratch arrays are sized once. Never called from render.
 */
public final class PerkDeck {

    private static final Perk[] ALL_PERKS = Perk.values();

    /**
     * Which perks a slot of the offer accepts. Per-constant bodies (the EnemyType idiom), so the offer rules
     * are data the draw loops over — never a switch on a perk.
     */
    private enum OfferSlot {
        /** A perk whose tag a carried weapon belongs to. */
        SYNERGY {
            @Override boolean accepts(Perk perk, PlayerBuild build, EnumSet<BuildTag> carriedTags) {
                return carriedTags.contains(perk.tag);
            }
        },
        /** A real pivot: a tag with no ranks that is not a carried weapon's tag either. */
        PIVOT_AWAY {
            @Override boolean accepts(Perk perk, PlayerBuild build, EnumSet<BuildTag> carriedTags) {
                return build.ranksInTag(perk.tag) == 0 && !carriedTags.contains(perk.tag);
            }
        },
        /** Any tag with no ranks. */
        PIVOT {
            @Override boolean accepts(Perk perk, PlayerBuild build, EnumSet<BuildTag> carriedTags) {
                return build.ranksInTag(perk.tag) == 0;
            }
        },
        /** Any unmaxed perk (the weighted remainder and every fallback). */
        ANY {
            @Override boolean accepts(Perk perk, PlayerBuild build, EnumSet<BuildTag> carriedTags) {
                return true;
            }
        };

        abstract boolean accepts(Perk perk, PlayerBuild build, EnumSet<BuildTag> carriedTags);
    }

    private final Random random;

    // Pre-allocated scratch reused across draws — no per-draw allocation.
    private final float[]   perkWeights = new float[ALL_PERKS.length];
    private final boolean[] perkTaken   = new boolean[ALL_PERKS.length];

    public PerkDeck(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Fills {@code out[0..n-1]} with the B3 offer and returns n (at most {@link BalanceConfig#PERKS_OFFERED},
     * fewer only when fewer unmaxed perks remain; 0 when every perk is maxed).
     *
     * @param out               caller-owned output array (length >= PERKS_OFFERED)
     * @param build             the run's build (ranks held, maxed perks)
     * @param carriedWeaponTags the tags of the weapons the player carries (SYNERGY test)
     */
    public int drawPerks(Perk[] out, PlayerBuild build, EnumSet<BuildTag> carriedWeaponTags) {
        java.util.Arrays.fill(perkTaken, false);
        int wanted = Math.min(out.length, BalanceConfig.PERKS_OFFERED);
        int drawn = 0;
        if (drawn < wanted) drawn = drawInto(out, drawn, build, carriedWeaponTags, OfferSlot.SYNERGY, OfferSlot.ANY);
        if (drawn < wanted) drawn = drawInto(out, drawn, build, carriedWeaponTags, OfferSlot.PIVOT_AWAY, OfferSlot.PIVOT);
        while (drawn < wanted) {
            int before = drawn;
            drawn = drawInto(out, drawn, build, carriedWeaponTags, OfferSlot.ANY, OfferSlot.ANY);
            if (drawn == before) break;   // no unmaxed perk left
        }
        shuffle(out, drawn);
        return drawn;
    }

    /** True when the perk's tag is one a carried weapon belongs to (the level-up plate's SYNERGY marker). */
    public static boolean isSynergy(Perk perk, EnumSet<BuildTag> carriedWeaponTags) {
        return perk != null && carriedWeaponTags != null && carriedWeaponTags.contains(perk.tag);
    }

    /** Draws one perk for a slot (preferred rule, then its fallback, then ANY) and appends it. */
    private int drawInto(Perk[] out, int drawn, PlayerBuild build, EnumSet<BuildTag> carriedTags,
                         OfferSlot preferred, OfferSlot fallback) {
        int picked = pickWeighted(build, carriedTags, preferred);
        if (picked < 0) picked = pickWeighted(build, carriedTags, fallback);
        if (picked < 0) picked = pickWeighted(build, carriedTags, OfferSlot.ANY);
        if (picked < 0) return drawn;
        perkTaken[picked] = true;
        out[drawn] = ALL_PERKS[picked];
        return drawn + 1;
    }

    /** Weighted pick among the not-taken, unmaxed perks the slot accepts; -1 when none qualifies. */
    private int pickWeighted(PlayerBuild build, EnumSet<BuildTag> carriedTags, OfferSlot slot) {
        float totalWeight = 0f;
        for (int perkIndex = 0; perkIndex < ALL_PERKS.length; perkIndex++) {
            Perk perk = ALL_PERKS[perkIndex];
            float weight = 0f;
            if (!perkTaken[perkIndex] && !build.isMaxed(perk) && slot.accepts(perk, build, carriedTags)) {
                weight = 1f + build.ranksInTag(perk.tag) * BalanceConfig.PERK_DRAW_BIAS_PER_PICK;
            }
            perkWeights[perkIndex] = weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0f) return -1;
        float roll = random.nextFloat() * totalWeight;
        float cumulative = 0f;
        for (int perkIndex = 0; perkIndex < ALL_PERKS.length; perkIndex++) {
            cumulative += perkWeights[perkIndex];
            if (roll < cumulative && perkWeights[perkIndex] > 0f) return perkIndex;
        }
        // Floating-point guard: the last eligible perk if the roll edged past the sum.
        for (int perkIndex = ALL_PERKS.length - 1; perkIndex >= 0; perkIndex--) {
            if (perkWeights[perkIndex] > 0f) return perkIndex;
        }
        return -1;
    }

    /** Fisher-Yates over the first {@code count} entries (presentation order only). */
    private void shuffle(Object[] values, int count) {
        for (int index = count - 1; index > 0; index--) {
            int swapIndex = random.nextInt(index + 1);
            Object held = values[index];
            values[index] = values[swapIndex];
            values[swapIndex] = held;
        }
    }
}
