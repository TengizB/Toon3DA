package ge.tbegvadze.toon3d.progression;

import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * THE RUN'S BUILD (balance-overhaul order 5) — the perk ranks and keystones picked this run, plus the
 * cached {@link BuildStats} aggregate every runtime seam reads. Recomputed on every pick (perks first,
 * keystones after, though the aggregate's accessors already make keystone multipliers order-free). A run's
 * build dies with the run: nothing here persists.
 *
 * <p>Allocation-free after construction: the rank and keystone arrays are sized once from the cached
 * enum tables, and a recompute only walks them. Headless: no LibGDX imports.
 */
public final class PlayerBuild {

    private static final Perk[]     PERKS     = Perk.values();
    private static final Keystone[] KEYSTONES = Keystone.values();
    private static final BuildTag[] TAGS      = BuildTag.values();

    private final int[]      perkRanks       = new int[PERKS.length];
    private final boolean[]  keystonesPicked = new boolean[KEYSTONES.length];
    private final BuildStats stats           = new BuildStats();
    /** The effect sets of the relics bonded this run, in bond order (relics-run-changing-artifacts). */
    private final PerkEffect[][] relicEffectSets = new PerkEffect[BalanceConfig.RELIC_MAX_HELD][];
    private int relicEffectSetCount;

    /** Adds one rank to a perk (capped at its max rank). Returns false when it was already maxed. */
    public boolean addPerkRank(Perk perk) {
        int index = perk.ordinal();
        if (perkRanks[index] >= perk.maxRank) return false;
        perkRanks[index]++;
        recompute();
        return true;
    }

    /** Picks a keystone. Returns false when it was already picked (a keystone is taken once). */
    public boolean addKeystone(Keystone keystone) {
        int index = keystone.ordinal();
        if (keystonesPicked[index]) return false;
        keystonesPicked[index] = true;
        recompute();
        return true;
    }

    /**
     * Folds one relic's effects into the build (applied at rank 1, after the perks and before the keystones).
     * Returns false when RELIC_MAX_HELD sets are already held. PlayerBuild never learns relic ids.
     */
    public boolean addRelicEffects(PerkEffect[] effects) {
        if (relicEffectSetCount >= relicEffectSets.length) return false;
        relicEffectSets[relicEffectSetCount++] = effects;
        recompute();
        return true;
    }

    /** Number of relic effect sets folded into the build. */
    public int relicEffectSetCount() {
        return relicEffectSetCount;
    }

    /** Ranks held in a perk (0 = not picked). */
    public int rankOf(Perk perk) {
        return perkRanks[perk.ordinal()];
    }

    /** True when the perk is at its max rank (B3: a maxed perk is never offered). */
    public boolean isMaxed(Perk perk) {
        return perkRanks[perk.ordinal()] >= perk.maxRank;
    }

    /** True when the keystone was picked this run. */
    public boolean hasKeystone(Keystone keystone) {
        return keystonesPicked[keystone.ordinal()];
    }

    /** Number of keystones picked this run. */
    public int keystoneCount() {
        int count = 0;
        for (boolean picked : keystonesPicked) {
            if (picked) count++;
        }
        return count;
    }

    /** Total perk ranks held in a tag (B3 pivot test; B4 highest-rank tag). */
    public int ranksInTag(BuildTag tag) {
        int ranks = 0;
        for (Perk perk : PERKS) {
            if (perk.tag == tag) ranks += perkRanks[perk.ordinal()];
        }
        return ranks;
    }

    /** The tag holding the most perk ranks (ties: the earlier tag), or null when no rank is held. */
    public BuildTag highestRankTag() {
        BuildTag best = null;
        int bestRanks = 0;
        for (BuildTag tag : TAGS) {
            int ranks = ranksInTag(tag);
            if (ranks > bestRanks) {
                bestRanks = ranks;
                best = tag;
            }
        }
        return best;
    }

    /** Attribute points the picked perks grant to one attribute. */
    public int attributeGrantTotal(Attribute attribute) {
        return stats.attributeGrant(attribute);
    }

    /** The cached aggregate (never null; recomputed on every pick). Read-only by convention. */
    public BuildStats stats() {
        return stats;
    }

    /** Clears every pick (a new run). */
    public void reset() {
        java.util.Arrays.fill(perkRanks, 0);
        java.util.Arrays.fill(keystonesPicked, false);
        java.util.Arrays.fill(relicEffectSets, null);
        relicEffectSetCount = 0;
        recompute();
    }

    private void recompute() {
        stats.reset();
        for (Perk perk : PERKS) {
            perk.applyRanks(stats, perkRanks[perk.ordinal()]);
        }
        for (int setIndex = 0; setIndex < relicEffectSetCount; setIndex++) {
            for (PerkEffect effect : relicEffectSets[setIndex]) effect.applyAt(stats, 1);
        }
        for (Keystone keystone : KEYSTONES) {
            if (keystonesPicked[keystone.ordinal()]) keystone.apply(stats);
        }
    }
}
