package ge.tbegvadze.toon3d.level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WHERE a {@link PropKitRow} may place (level-generation-overhaul R17). Each constant carries its own
 * candidate-tile rule (the WaveformKind idiom — never a switch): it returns candidate LINES, each an ordered list
 * of {@code {column, row}} interior tiles (a wall line runs along its wall; a single-tile anchor returns one line
 * per tile). The {@link RoomDresser} applies the row's mode to the lines and every R18 check to each tile.
 * Headless — no LibGDX.
 */
public enum PropAnchor {

    /** Flush tiles on the side opposite the room's primary socket; SIDE_WALL when that side is too short. */
    BACK_WALL {
        @Override
        List<List<int[]>> lines(RoomDresser.Dressing dressing, PropKitRow row) {
            List<int[]> back = dressing.flushLine(dressing.backSide());
            int needed = row.mode == PropKitRow.Mode.SPACED ? row.minCount * 2 - 1 : row.minCount;
            if (back.size() < needed) return SIDE_WALL.lines(dressing, row);
            return Collections.singletonList(back);
        }
    },

    /** Flush tiles on the two sides perpendicular to the primary socket's side. */
    SIDE_WALL {
        @Override
        List<List<int[]>> lines(RoomDresser.Dressing dressing, PropKitRow row) {
            List<List<int[]>> lines = new ArrayList<>();
            for (DoorSocket.Side side : dressing.perpendicularSides()) lines.add(dressing.flushLine(side));
            return lines;
        }
    },

    /** Flush tiles on the primary socket's side (the checks keep them out of every clearance zone). */
    ENTRY_WALL {
        @Override
        List<List<int[]>> lines(RoomDresser.Dressing dressing, PropKitRow row) {
            return Collections.singletonList(dressing.flushLine(dressing.entrySide()));
        }
    },

    /** Any flush tile, one line per wall. */
    ANY_WALL {
        @Override
        List<List<int[]>> lines(RoomDresser.Dressing dressing, PropKitRow row) {
            List<List<int[]>> lines = new ArrayList<>();
            for (DoorSocket.Side side : DoorSocket.Side.values()) lines.add(dressing.flushLine(side));
            return lines;
        }
    },

    /** Interior tiles with two shell neighbours (one line per corner). */
    CORNER {
        @Override
        List<List<int[]>> lines(RoomDresser.Dressing dressing, PropKitRow row) {
            List<List<int[]>> lines = new ArrayList<>();
            for (int[] corner : dressing.cornerTiles()) lines.add(Collections.singletonList(corner));
            return lines;
        }
    },

    /** The interior centre footprint the dresser reserved for the room's CENTRE row. */
    CENTRE {
        @Override
        List<List<int[]>> lines(RoomDresser.Dressing dressing, PropKitRow row) {
            return Collections.singletonList(dressing.centreFootprint());
        }
    },

    /**
     * DENSE aisles: parallel lines along the long axis one tile in from the walls, a cross aisle through the
     * HUB (a RELAXED room keeps every second line, at most two). The lanes break the lines in front of every
     * socket through the lane check.
     */
    ROWS {
        @Override
        List<List<int[]>> lines(RoomDresser.Dressing dressing, PropKitRow row) {
            return dressing.rackLines();
        }
    },

    /** Named tiles the room's architecture exported (observatory core and spokes, the lab alcove). */
    FEATURE {
        @Override
        List<List<int[]>> lines(RoomDresser.Dressing dressing, PropKitRow row) {
            return Collections.singletonList(dressing.feature(row.feature));
        }
    },

    /** Within Chebyshev 1 of a prop this kit already placed, of one of the row's named symbols. */
    NEAR {
        @Override
        List<List<int[]>> lines(RoomDresser.Dressing dressing, PropKitRow row) {
            return Collections.singletonList(dressing.nearTiles(row.nearSymbols));
        }
    },

    /** (Decals only.) Any open interior tile — the one density-based anchor; decals are walkable. */
    FIELD {
        @Override
        List<List<int[]>> lines(RoomDresser.Dressing dressing, PropKitRow row) {
            return Collections.singletonList(row.nearSymbols != null ? dressing.nearTiles(row.nearSymbols)
                                                                      : dressing.openTiles());
        }
    },

    /** (Decals only.) Tiles along a lane, from the HUB side toward a socket. */
    TRAIL {
        @Override
        List<List<int[]>> lines(RoomDresser.Dressing dressing, PropKitRow row) {
            return Collections.singletonList(dressing.trail());
        }
    };

    /** The anchor's candidate lines for this room. */
    abstract List<List<int[]>> lines(RoomDresser.Dressing dressing, PropKitRow row);
}
