package ge.tbegvadze.toon3d.relic;

import ge.tbegvadze.toon3d.progression.BuildStats;
import ge.tbegvadze.toon3d.progression.BuildTag;
import ge.tbegvadze.toon3d.progression.PerkEffect;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Rolls an offer deterministically (R4-R6). Offers are rare (a handful per run, never per frame), so each call
 * builds a seeded {@link Random} and a few small lists; the result lands in the caller's pre-allocated
 * {@link RelicOffer}.
 *
 * <p>Fallback (R5): when the rolled rarity has no eligible relic left, the card falls back to RARE, then COMMON,
 * then LEGENDARY, but only among the rarities the source allows (a RUN_START offer is COMMON only, a MYSTERY
 * offer never turns LEGENDARY). CURSED is never a stand-in for anything and nothing stands in for it: a
 * QUARANTINE_LOCKER offer with too few eligible CURSED relics simply shows fewer cards.
 */
public final class RelicOfferRoller {

    /** The R5 fallback order. */
    private static final RelicRarity[] FALLBACK_ORDER = {RelicRarity.RARE, RelicRarity.COMMON, RelicRarity.LEGENDARY};

    private RelicOfferRoller() { }

    /**
     * R6: the offer seed. {@code floorSeed(runSeed ^ RELIC_OFFER_SEED_SALT, depth)} mixed with the source ordinal
     * through the same floorSeed mixer, so two sources at one depth draw independent streams.
     */
    public static long seedFor(long runSeed, int depth, RelicSource source) {
        long floorStream = GameMath.floorSeed(runSeed ^ BalanceConfig.RELIC_OFFER_SEED_SALT, depth);
        return GameMath.floorSeed(floorStream, source.ordinal());
    }

    /**
     * Fills {@code out} with the offer for {@code source}. {@code carriedOrRankedTags} is every BuildTag the
     * player carries a weapon of or holds a perk rank in (affinity gate); {@code seed} is {@link #seedFor}.
     * Leaves {@code out.count == 0} when the hand is full or nothing is eligible.
     */
    public static void roll(RelicSource source, int depth, int bossIndex, RelicHand hand, BuildStats stats,
                            EnumSet<BuildTag> carriedOrRankedTags, long seed, RelicOffer out, RelicRegistry registry) {
        out.clear();
        out.source = source;
        out.depth  = depth;
        if (hand.count() >= BalanceConfig.RELIC_MAX_HELD) return;

        Random random = new Random(seed);
        RelicRarity[] allowed = source.allowedRarities();
        float[] weights = source.rarityWeightsFor(bossIndex);
        int wanted = Math.min(source.cardCount(), out.cards.length);

        for (int cardIndex = 0; cardIndex < wanted; cardIndex++) {
            RelicRarity rolled = rollRarity(random, allowed, weights);
            RelicDefinition card = pick(random, registry, rolled, allowed, hand, stats, carriedOrRankedTags, out, cardIndex);
            if (card == null) break;
            out.cards[cardIndex] = card;
            out.count = cardIndex + 1;
        }

        if (source.guaranteesRareOrBetter() && out.count > 0 && !hasRareOrBetter(out)) {
            int last = out.count - 1;
            out.cards[last] = null;
            RelicDefinition replacement = pickOfRarity(random, registry, RelicRarity.RARE, hand, stats,
                    carriedOrRankedTags, out, last);
            if (replacement == null) {
                replacement = pickOfRarity(random, registry, RelicRarity.LEGENDARY, hand, stats,
                        carriedOrRankedTags, out, last);
            }
            out.cards[last] = replacement; // null only when no RARE/LEGENDARY is eligible: shrink the offer
            if (replacement == null) out.count = last;
        }
    }

    /** One weighted draw over the source's allowed rarities. */
    private static RelicRarity rollRarity(Random random, RelicRarity[] allowed, float[] weights) {
        float total = 0f;
        for (int index = 0; index < allowed.length; index++) total += weights[index];
        float draw = random.nextFloat() * total;
        float running = 0f;
        for (int index = 0; index < allowed.length; index++) {
            running += weights[index];
            if (draw < running) return allowed[index];
        }
        return allowed[allowed.length - 1];
    }

    private static RelicDefinition pick(Random random, RelicRegistry registry, RelicRarity rolled, RelicRarity[] allowed,
                                        RelicHand hand, BuildStats stats, EnumSet<BuildTag> tags, RelicOffer out, int filled) {
        RelicDefinition card = pickOfRarity(random, registry, rolled, hand, stats, tags, out, filled);
        if (card != null || rolled.isCursed()) return card;
        for (RelicRarity fallback : FALLBACK_ORDER) {
            if (fallback == rolled || !contains(allowed, fallback)) continue;
            card = pickOfRarity(random, registry, fallback, hand, stats, tags, out, filled);
            if (card != null) return card;
        }
        return null;
    }

    private static RelicDefinition pickOfRarity(Random random, RelicRegistry registry, RelicRarity rarity, RelicHand hand,
                                                BuildStats stats, EnumSet<BuildTag> tags, RelicOffer out, int filled) {
        List<RelicDefinition> candidates = new ArrayList<>();
        for (RelicDefinition definition : registry.all()) {
            if (definition.rarity() != rarity) continue;
            if (!isEligible(definition, hand, stats, tags)) continue;
            if (alreadyInOffer(definition, out, filled)) continue;
            candidates.add(definition);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }

    /** R5: not held; affinity satisfied; not every effect a boolean the build already has. */
    public static boolean isEligible(RelicDefinition definition, RelicHand hand, BuildStats stats, EnumSet<BuildTag> tags) {
        if (hand.holds(definition)) return false;
        if (definition.affinity() != null && !tags.contains(definition.affinity())) return false;
        for (PerkEffect effect : definition.effects()) {
            if (!effect.kind.isSatisfiedBy(stats)) return true;
        }
        return false;
    }

    private static boolean alreadyInOffer(RelicDefinition definition, RelicOffer out, int filled) {
        for (int index = 0; index < filled; index++) {
            if (out.cards[index] == definition) return true;
        }
        return false;
    }

    private static boolean hasRareOrBetter(RelicOffer out) {
        for (int index = 0; index < out.count; index++) {
            RelicRarity rarity = out.cards[index].rarity();
            if (rarity == RelicRarity.RARE || rarity == RelicRarity.LEGENDARY) return true;
        }
        return false;
    }

    private static boolean contains(RelicRarity[] rarities, RelicRarity wanted) {
        for (RelicRarity rarity : rarities) {
            if (rarity == wanted) return true;
        }
        return false;
    }
}
