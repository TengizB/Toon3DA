package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The deterministic, legal-tile-only placement engine that turns a {@link GuaranteedContent} promise
 * (e.g. "3 bullet boxes near the exit") into concrete stamped tiles on a freshly generated
 * {@link Level}. It is the orthogonality trick that lets ANY generator host ANY node's guarantees:
 * the profile biases a generic generator, then this stamper writes the guaranteed symbols afterward.
 *
 * <p><b>Legal tiles only.</b> A symbol is stamped only on a walkable, non-conflicting floor tile —
 * never over a wall, prop, door, column, shop machine, the player start ('p'), the exit ('&gt;'), or an
 * existing pickup. Only characters already defined in {@code docs/tile-symbols.txt} may be passed in;
 * this class introduces no new symbols.
 *
 * <p><b>Deterministic.</b> All ordering and tie-breaking flows through a {@link Random} seeded from
 * the floor seed, so a given (floor seed, guarantee) pair always lands on the same tiles.
 *
 * <p>Pure / headless — no LibGDX imports. Stateless: every method is static.
 */
public final class GuaranteedContentStamper {

    /** Distinct seed salts so two guarantees of the same placement on one floor don't co-locate. */
    private static final long SEED_SALT = 0x9E3779B97F4A7C15L;

    private GuaranteedContentStamper() {}

    /**
     * Stamps {@code symbol} onto up to {@code count} legal tiles chosen by {@code placement}. Fewer
     * than {@code count} are written if the floor lacks enough legal tiles — a guarantee never
     * clobbers a wall/prop/pickup to hit its quota.
     *
     * @param level     the generated floor to augment, in place
     * @param symbol    a tile symbol from {@code docs/tile-symbols.txt}
     * @param count     how many tiles to stamp (values &lt;= 0 are a no-op)
     * @param placement where on the floor the tiles are chosen from
     * @param seed      the floor seed; salted per call so distinct guarantees don't overlap
     * @return the number of tiles actually stamped
     */
    public static int stamp(Level level, char symbol, int count, Placement placement, long seed) {
        if (level == null || count <= 0) return 0;
        Random random = new Random(seed ^ (SEED_SALT * (symbol + 1L)));
        List<int[]> ordered = orderedLegalTiles(level, placement, random);
        int stamped = 0;
        for (int index = 0; index < ordered.size() && stamped < count; index++) {
            int[] tile = ordered.get(index);
            // Re-check legality: an earlier guarantee in the same plan may have taken this tile.
            if (!isLegalStampTile(level, tile[0], tile[1])) continue;
            level.setCell(tile[0], tile[1], symbol);
            stamped++;
        }
        return stamped;
    }

    /** All legal tiles ordered per the placement intent (deterministic given {@code random}). */
    private static List<int[]> orderedLegalTiles(Level level, Placement placement, Random random) {
        List<int[]> legal = collectLegalTiles(level);
        if (legal.isEmpty()) return legal;

        switch (placement) {
            case NEAR_SPAWN:
                sortByDistanceTo(legal, findSymbol(level, 'p'), true);
                return legal;
            case NEAR_EXIT:
                sortByDistanceTo(legal, findStairsDown(level), true);
                return legal;
            case FARTHEST_ROOM:
            case GATED_ROOM: // no explicit gated-room metadata yet — deepest tile is the best proxy.
                sortByDistanceTo(legal, findSymbol(level, 'p'), false);
                return legal;
            case CENTER_ROOM:
                sortByDistanceTo(legal, walkableCentroid(legal), true);
                return legal;
            case SCATTERED:
            default:
                return scatter(legal, random);
        }
    }

    /**
     * Well-separated pick order: seeded shuffle, then greedily accept tiles that are at least
     * {@link #minScatterSpacing} tiles (Manhattan) from every already-accepted tile, appending the
     * rejected remainder so the caller can still fill its quota on a small floor.
     */
    private static List<int[]> scatter(List<int[]> legal, Random random) {
        List<int[]> shuffled = new ArrayList<>(legal);
        Collections.shuffle(shuffled, random);
        int spacing = minScatterSpacing(legal.size());
        List<int[]> spread = new ArrayList<>(shuffled.size());
        List<int[]> leftover = new ArrayList<>();
        for (int[] tile : shuffled) {
            boolean farEnough = true;
            for (int[] accepted : spread) {
                int manhattan = Math.abs(accepted[0] - tile[0]) + Math.abs(accepted[1] - tile[1]);
                if (manhattan < spacing) { farEnough = false; break; }
            }
            if (farEnough) spread.add(tile); else leftover.add(tile);
        }
        spread.addAll(leftover);
        return spread;
    }

    /** Modest spacing that scales with floor size; at least 2 so scattered items never touch. */
    private static int minScatterSpacing(int legalTileCount) {
        return Math.max(2, (int) Math.round(Math.sqrt(legalTileCount) / 3.0));
    }

    /** Every walkable, non-conflicting tile, in stable row-major order. */
    private static List<int[]> collectLegalTiles(Level level) {
        List<int[]> tiles = new ArrayList<>();
        for (int tileRow = 0; tileRow < level.getHeight(); tileRow++) {
            for (int tileColumn = 0; tileColumn < level.getWidth(); tileColumn++) {
                if (isLegalStampTile(level, tileColumn, tileRow)) {
                    tiles.add(new int[]{tileColumn, tileRow});
                }
            }
        }
        return tiles;
    }

    /**
     * True when a guarantee may be stamped on this tile: walkable floor that is not a wall, prop,
     * door, column, shop machine, the player start, the exit, or an existing pickup.
     */
    public static boolean isLegalStampTile(Level level, int tileColumn, int tileRow) {
        char cell = level.getCell(tileColumn, tileRow);
        if (cell == '\0') return false;                 // out of bounds
        if (cell == 'p') return false;                  // player start
        if (Level.isWall(cell)) return false;
        if (Level.isPropSolid(cell)) return false;
        if (Level.isColumn(cell)) return false;         // 'P'
        if (Level.isDoor(cell)) return false;
        if (Level.isStairsDown(cell)) return false;     // '>'
        if (Level.isShop(cell)) return false;           // '@' vending machine
        if (Level.isKeycardPickup(cell)) return false;
        if (Level.isMedicalPickup(cell)) return false;
        if (Level.isArmourPickup(cell)) return false;
        if (Level.isAmmoPickup(cell)) return false;
        return true;
    }

    // -------------------------------------------------------------------------
    // Anchors + ordering
    // -------------------------------------------------------------------------

    /** Sorts tiles by squared Euclidean distance to {@code anchor}; ascending = nearest first. */
    private static void sortByDistanceTo(List<int[]> tiles, int[] anchor, boolean ascending) {
        if (anchor == null) return; // no anchor found — leave the stable row-major order as-is.
        tiles.sort((left, right) -> {
            long leftDistance  = squaredDistance(left, anchor);
            long rightDistance = squaredDistance(right, anchor);
            int comparison = Long.compare(leftDistance, rightDistance);
            return ascending ? comparison : -comparison;
        });
    }

    private static long squaredDistance(int[] tile, int[] anchor) {
        long differenceX = tile[0] - anchor[0];
        long differenceY = tile[1] - anchor[1];
        return differenceX * differenceX + differenceY * differenceY;
    }

    /** The centroid of the walkable set, rounded to the nearest tile. */
    private static int[] walkableCentroid(List<int[]> tiles) {
        long sumColumn = 0;
        long sumRow = 0;
        for (int[] tile : tiles) {
            sumColumn += tile[0];
            sumRow += tile[1];
        }
        return new int[]{(int) Math.round((double) sumColumn / tiles.size()),
                         (int) Math.round((double) sumRow / tiles.size())};
    }

    /** First tile carrying {@code symbol} (row-major), or null if none. */
    private static int[] findSymbol(Level level, char symbol) {
        for (int tileRow = 0; tileRow < level.getHeight(); tileRow++) {
            for (int tileColumn = 0; tileColumn < level.getWidth(); tileColumn++) {
                if (level.getCell(tileColumn, tileRow) == symbol) {
                    return new int[]{tileColumn, tileRow};
                }
            }
        }
        return null;
    }

    /** First stairs-down ('&gt;') tile (row-major), or null if the floor has no marked exit. */
    private static int[] findStairsDown(Level level) {
        for (int tileRow = 0; tileRow < level.getHeight(); tileRow++) {
            for (int tileColumn = 0; tileColumn < level.getWidth(); tileColumn++) {
                if (Level.isStairsDown(level.getCell(tileColumn, tileRow))) {
                    return new int[]{tileColumn, tileRow};
                }
            }
        }
        return null;
    }
}
