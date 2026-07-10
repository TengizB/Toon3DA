package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.Level;

/**
 * Factory for the concrete {@link GuaranteedContent} KINDS a {@link NodeLevelProfile} attaches to a
 * {@link LevelPlan}. Each factory captures the floor seed at resolve time (so the promise is
 * deterministic) and delegates placement to {@link GuaranteedContentStamper}. Keeping construction
 * here — rather than in every profile — means the stamping rules live in exactly one place.
 *
 * <p>Order-7 ships the two symbol-stamping kinds that reuse the existing tile set: {@link #pickup}
 * and {@link #prop}. Richer kinds from the idea (STATION heal/vending, forced SPAWN, TILE_THEME
 * repaint) arrive with the profiles that need them (order-8/9) so no unused machinery ships early.
 *
 * <p>Only symbols from {@code docs/tile-symbols.txt} may be passed. Pure / headless — no LibGDX.
 */
public final class Guarantees {

    private Guarantees() {}

    /**
     * A PICKUP guarantee: stamps {@code count} copies of a walkable pickup {@code symbol}
     * (e.g. '6' bullet box, 'H' medkit, 'A' armour) at {@code placement}.
     *
     * @param seed the floor seed captured from {@link NodeLevelProfile#resolve}
     */
    public static GuaranteedContent pickup(char symbol, int count, Placement placement, long seed) {
        return level -> GuaranteedContentStamper.stamp(level, symbol, count, placement, seed);
    }

    /**
     * A PROP guarantee: stamps {@code count} copies of a prop {@code symbol} (e.g. '=' weapon rack,
     * 'C' crate, '&amp;' bio-pod) at {@code placement}. Solid props are placed on walkable tiles that
     * then become solid — the stamper picks legal tiles only.
     *
     * @param seed the floor seed captured from {@link NodeLevelProfile#resolve}
     */
    public static GuaranteedContent prop(char symbol, int count, Placement placement, long seed) {
        return level -> GuaranteedContentStamper.stamp(level, symbol, count, placement, seed);
    }

    /**
     * A TILE_THEME guarantee (order-8): repaints the floor lighting so a CALM node reads as an
     * unambiguously bright, safe space no matter which generator built it. Every unlit ('u') and
     * failing-light flicker ('f') floor tile is promoted to a steady lit floor ('l'). It touches
     * ONLY those two lighting variants — walls, props, pickups, the spawn, and the exit are all left
     * untouched — so it is order-independent with the pickup/prop stamps and never clobbers content.
     *
     * <p>No new symbols; both the read and written tiles are from {@code docs/tile-symbols.txt}.
     */
    public static GuaranteedContent brightenFloors() {
        return level -> brightenAllFloors(level);
    }

    /** Promotes every 'u'/'f' floor tile to a steady lit 'l' tile, in place. */
    private static void brightenAllFloors(Level level) {
        if (level == null) return;
        for (int tileRow = 0; tileRow < level.getHeight(); tileRow++) {
            for (int tileColumn = 0; tileColumn < level.getWidth(); tileColumn++) {
                char cell = level.getCell(tileColumn, tileRow);
                if (cell == 'u' || cell == 'f') {
                    level.setCell(tileColumn, tileRow, 'l');
                }
            }
        }
    }
}
