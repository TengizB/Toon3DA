package ge.tbegvadze.toon3d.level;

import java.util.Random;

/**
 * A room's SIZE WINDOW (level-generation-overhaul R4): the interior a room of this blueprint may have, min and
 * max width x height, rotatable unless SQUARE. Every minimum is chosen so the room's signature row fits with
 * three sockets present. Immutable. Headless — no LibGDX.
 */
public final class RoomSizeWindow {

    public final int     minWidth;
    public final int     minHeight;
    public final int     maxWidth;
    public final int     maxHeight;
    /** A square room keeps width == height and is never rotated. */
    public final boolean square;

    private RoomSizeWindow(int minWidth, int minHeight, int maxWidth, int maxHeight, boolean square) {
        if (minWidth < 1 || minHeight < 1 || maxWidth < minWidth || maxHeight < minHeight) {
            throw new IllegalArgumentException("bad size window " + minWidth + "x" + minHeight
                    + " -> " + maxWidth + "x" + maxHeight);
        }
        this.minWidth  = minWidth;
        this.minHeight = minHeight;
        this.maxWidth  = maxWidth;
        this.maxHeight = maxHeight;
        this.square    = square;
    }

    /** A rotatable window: a room may be stamped W x H or H x W. */
    public static RoomSizeWindow of(int minWidth, int minHeight, int maxWidth, int maxHeight) {
        return new RoomSizeWindow(minWidth, minHeight, maxWidth, maxHeight, false);
    }

    /** A square, unrotated window. */
    public static RoomSizeWindow square(int minSide, int maxSide) {
        return new RoomSizeWindow(minSide, minSide, maxSide, maxSide, true);
    }

    /**
     * Sizes one room for a footprint attempt (R4): each axis uniform in
     * [min, min + (max - min) x clamp(footprintScale, 0, 1)], then (when rotatable) a coin flip swaps the axes.
     * A square window draws one side. Returns {@code {interiorWidth, interiorHeight}}.
     */
    public int[] size(Random random, float footprintScale) {
        float scale = Math.max(0f, Math.min(1f, footprintScale));
        int width = minWidth + random.nextInt(Math.round((maxWidth - minWidth) * scale) + 1);
        if (square) return new int[]{ width, width };
        int height = minHeight + random.nextInt(Math.round((maxHeight - minHeight) * scale) + 1);
        return random.nextBoolean() ? new int[]{ width, height } : new int[]{ height, width };
    }

    /** The window minimum, unrotated ({@code {minWidth, minHeight}}). */
    public int[] minimum() {
        return new int[]{ minWidth, minHeight };
    }
}
