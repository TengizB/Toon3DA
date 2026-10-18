package ge.tbegvadze.toon3d.relic;

import ge.tbegvadze.toon3d.util.BalanceConfig;

/** A rolled offer: mutable and pre-allocated; the roller fills it and it is never allocated per offer. */
public final class RelicOffer {

    public RelicSource source;
    public int         depth;
    public final RelicDefinition[] cards = new RelicDefinition[BalanceConfig.RELIC_OFFER_MAX_CARDS];
    public int         count;

    public void clear() {
        source = null;
        depth  = 0;
        java.util.Arrays.fill(cards, null);
        count  = 0;
    }
}
