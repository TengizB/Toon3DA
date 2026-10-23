package ge.tbegvadze.toon3d.level;

import java.util.Random;

/**
 * One row of a {@link PropKit} (level-generation-overhaul R16): WHAT to place (a symbol, optionally with weighted
 * alternatives), WHERE (a {@link PropAnchor}, with an optional fallback anchor), HOW MANY (a count range), HOW
 * OFTEN (a row chance, 1.0 by default) and in what SHAPE (a {@link Mode}). Row 1 of a special room's kit is its
 * SIGNATURE. Immutable once built; build with {@link #place} and the fluent setters. Headless — no LibGDX.
 */
public final class PropKitRow {

    /** The shape a row's placements take. */
    public enum Mode {
        /** Independent tiles anywhere among the anchor's candidates. */
        SINGLE,
        /** A contiguous run along one wall line. */
        RUN,
        /** Every second tile along one wall line. */
        SPACED,
        /** A contiguous blob grown from one seed tile. */
        CLUSTER
    }

    char[]     symbols   = new char[0];
    float[]    weights   = new float[0];
    PropAnchor anchor    = PropAnchor.ANY_WALL;
    PropAnchor fallback;
    int        minCount  = 1;
    int        maxCount  = 1;
    float      chance    = 1f;
    Mode       mode      = Mode.SINGLE;
    String     feature;
    String     nearSymbols;
    float      density;
    int        minimumShortSide;
    int[][]    centreFootprints = { { 1, 1 } };

    private PropKitRow() {}

    /** A row placing one symbol. */
    public static PropKitRow place(char symbol) {
        PropKitRow row = new PropKitRow();
        row.symbols = new char[]{ symbol };
        row.weights = new float[]{ 1f };
        return row;
    }

    /** A row placing one of several symbols per tile, by weight (weights in the same order as the symbols). */
    public static PropKitRow place(String symbols, float... weights) {
        if (symbols.length() != weights.length) throw new IllegalArgumentException("one weight per symbol");
        PropKitRow row = new PropKitRow();
        row.symbols = symbols.toCharArray();
        row.weights = weights.clone();
        return row;
    }

    public PropKitRow at(PropAnchor anchor)           { this.anchor = anchor; return this; }
    public PropKitRow orAt(PropAnchor fallbackAnchor) { this.fallback = fallbackAnchor; return this; }
    public PropKitRow count(int exactly)              { return count(exactly, exactly); }
    public PropKitRow count(int minimum, int maximum) { this.minCount = minimum; this.maxCount = maximum; return this; }
    public PropKitRow chance(float rowChance)         { this.chance = rowChance; return this; }
    public PropKitRow as(Mode shape)                  { this.mode = shape; return this; }
    /** FEATURE anchor: the architecture slot the row places into. */
    public PropKitRow feature(String featureName)     { this.feature = featureName; return this; }
    /** NEAR anchor (or a FIELD filter): tiles within Chebyshev 1 of a placed prop of one of these symbols. */
    public PropKitRow near(String symbolsNearby)      { this.nearSymbols = symbolsNearby; return this; }
    /** FIELD anchor: per-tile density (decals only; 0 = place the count instead). */
    public PropKitRow density(float perTile)          { this.density = perTile; return this; }
    /** The row only applies when the room's shorter interior side is at least this long. */
    public PropKitRow whenShortSideAtLeast(int tiles) { this.minimumShortSide = tiles; return this; }
    /** CENTRE anchor: the footprints ({@code {width, height}} along the long axis) one is drawn from. */
    public PropKitRow footprints(int[]... options)    { this.centreFootprints = options; return this; }

    public PropAnchor anchor()    { return anchor; }
    public int        minCount()  { return minCount; }
    public int        maxCount()  { return maxCount; }
    public float      chance()    { return chance; }
    public Mode       mode()      { return mode; }

    /** The row's symbols (its alternatives). */
    public char[] symbols() {
        return symbols.clone();
    }

    /** The first (heaviest-listed) symbol — a signature row's identity. */
    public char primarySymbol() {
        return symbols[0];
    }

    /** Whether every symbol of the row is a walkable decal. */
    public boolean placesDecals() {
        for (char symbol : symbols) if (!Level.isPropDecal(symbol)) return false;
        return true;
    }

    /** Draws one of the row's symbols by weight. */
    char drawSymbol(Random random) {
        if (symbols.length == 1) return symbols[0];
        float total = 0f;
        for (float weight : weights) total += weight;
        float roll = random.nextFloat() * total;
        for (int index = 0; index < symbols.length; index++) {
            roll -= weights[index];
            if (roll < 0f) return symbols[index];
        }
        return symbols[symbols.length - 1];
    }
}
