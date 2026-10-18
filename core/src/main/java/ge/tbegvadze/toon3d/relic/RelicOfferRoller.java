package ge.tbegvadze.toon3d.relic;

import ge.tbegvadze.toon3d.progression.BuildStats;
import ge.tbegvadze.toon3d.progression.BuildTag;

import java.util.EnumSet;

/** Rolls an offer deterministically (R4-R6). CP1 ships the signature only; CP3 implements the roll. */
public final class RelicOfferRoller {

    private RelicOfferRoller() { }

    public static void roll(RelicSource source, int depth, int bossIndex, RelicHand hand, BuildStats stats,
                            EnumSet<BuildTag> carriedOrRankedTags, long seed, RelicOffer out) {
        out.clear();
        out.source = source;
        out.depth  = depth;
        // CP3: R4 card counts and rarity weights, R5 eligibility and fallback, R6 seeding. count stays 0 until then.
    }
}
