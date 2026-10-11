package ge.tbegvadze.toon3d.progression;

import ge.tbegvadze.toon3d.util.BalanceConfig;

import java.util.EnumSet;
import java.util.Random;

/**
 * Draws the level-up PERK offers (balance-overhaul order 5, rule B3) — the replacement for the retired
 * budget-equal card deck — and, on a keystone level, the KEYSTONE offer instead (rule B4,
 * {@link #drawKeystones}).
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

    // Keystone offer (B4) scratch — sized once.
    private static final Keystone[] ALL_KEYSTONES = Keystone.values();
    private final float[]   keystoneWeights = new float[ALL_KEYSTONES.length];
    private final boolean[] keystoneTaken   = new boolean[ALL_KEYSTONES.length];

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

    /**
     * Fills {@code out[0..n-1]} with the B4 keystone offer and returns n (at most
     * {@link BalanceConfig#KEYSTONES_OFFERED}; fewer only if fewer untaken keystones remain — never with the
     * shipped 9 keystones over 4 keystone levels):
     * <ul>
     *   <li>never a keystone already taken;</li>
     *   <li>at least one MATCHING the build's {@link PlayerBuild#highestRankTag()}. FALLBACK (decided): when that
     *       keystone is taken, or the build holds no ranks, the matching slot goes to the untaken keystone whose
     *       tag holds the most ranks (ties: catalog order) — the closest thing to "your build" still on offer;</li>
     *   <li>at least one NOT matching that tag (any other untaken keystone);</li>
     *   <li>the rest weighted like the perks: 1 + ranks in the keystone's tag x PERK_DRAW_BIAS_PER_PICK.</li>
     * </ul>
     * Shuffled for presentation. Allocation-free.
     */
    public int drawKeystones(Keystone[] out, PlayerBuild build) {
        java.util.Arrays.fill(keystoneTaken, false);
        int wanted = Math.min(out.length, BalanceConfig.KEYSTONES_OFFERED);
        int drawn = 0;
        BuildTag leading = build.highestRankTag();
        int matching = -1;
        int bestRanks = -1;
        for (int index = 0; index < ALL_KEYSTONES.length; index++) {
            Keystone keystone = ALL_KEYSTONES[index];
            if (build.hasKeystone(keystone)) continue;
            int ranks = keystone.tag == leading ? Integer.MAX_VALUE : build.ranksInTag(keystone.tag);
            if (ranks > bestRanks) {
                bestRanks = ranks;
                matching = index;
            }
        }
        if (matching >= 0 && drawn < wanted) {
            keystoneTaken[matching] = true;
            out[drawn++] = ALL_KEYSTONES[matching];
        }
        BuildTag matchedTag = matching >= 0 ? ALL_KEYSTONES[matching].tag : null;
        while (drawn < wanted) {
            int picked = pickWeightedKeystone(build, matchedTag, drawn == 1);
            if (picked < 0) picked = pickWeightedKeystone(build, matchedTag, false);
            if (picked < 0) break;
            keystoneTaken[picked] = true;
            out[drawn++] = ALL_KEYSTONES[picked];
        }
        shuffle(out, drawn);
        return drawn;
    }

    /** True when reaching {@code characterLevel} offers keystones instead of perks (B4, AS2). */
    public static boolean isKeystoneLevel(int characterLevel) {
        for (int level : BalanceConfig.KEYSTONE_CHARACTER_LEVELS) {
            if (level == characterLevel) return true;
        }
        return false;
    }

    /** Weighted pick among untaken, unoffered keystones; {@code otherTagOnly} excludes the matched tag. */
    private int pickWeightedKeystone(PlayerBuild build, BuildTag matchedTag, boolean otherTagOnly) {
        float totalWeight = 0f;
        for (int index = 0; index < ALL_KEYSTONES.length; index++) {
            Keystone keystone = ALL_KEYSTONES[index];
            float weight = 0f;
            if (!keystoneTaken[index] && !build.hasKeystone(keystone)
                    && !(otherTagOnly && keystone.tag == matchedTag)) {
                weight = 1f + build.ranksInTag(keystone.tag) * BalanceConfig.PERK_DRAW_BIAS_PER_PICK;
            }
            keystoneWeights[index] = weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0f) return -1;
        float roll = random.nextFloat() * totalWeight;
        float cumulative = 0f;
        for (int index = 0; index < ALL_KEYSTONES.length; index++) {
            cumulative += keystoneWeights[index];
            if (roll < cumulative && keystoneWeights[index] > 0f) return index;
        }
        for (int index = ALL_KEYSTONES.length - 1; index >= 0; index--) {
            if (keystoneWeights[index] > 0f) return index;
        }
        return -1;
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
