package ge.tbegvadze.toon3d.level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a generator says about the LAYOUT it built (level-generation-overhaul): every room it placed
 * (shell rectangle, role, blueprint and kit, sockets, the lane band its dresser kept clear), the special
 * rooms it planned, and how often its last-resort guards fired. Attached to the {@link Level} like the
 * {@link FloorContentReport}; read only by the dev {@code LevelQualityAudit} instrument and never by the
 * game. Rooms are reported with their SHELL bounds (walls inclusive). Headless — no LibGDX.
 */
public final class LevelLayoutReport {

    /** A room's part in the layout. */
    public enum Role { ENTRANCE, REGULAR, SPECIAL }

    /** One placed room, as its generator saw it. */
    public static final class RoomRecord {
        public final int    leftColumn;
        public final int    bottomRow;
        public final int    rightColumn;
        public final int    topRow;
        public final Role   role;
        /** The room's blueprint id (or a generator-local type id); never null. */
        public final String blueprintId;
        /** The prop kit the room was dressed from, or null when no kit dressed it. */
        public final String kitId;
        /** Whether the room was dressed DENSE (a TIGHT room). */
        public final boolean dense;
        /** The room's sockets as {@code {column, row}} shell tiles (empty = not reported). */
        public final List<int[]> sockets;
        /** The lane band the dresser kept clear, as {@code {column, row}} tiles; null = not reported. */
        public final List<int[]> laneBand;

        public RoomRecord(int leftColumn, int bottomRow, int rightColumn, int topRow, Role role,
                          String blueprintId, String kitId, boolean dense,
                          List<int[]> sockets, List<int[]> laneBand) {
            this.leftColumn  = leftColumn;
            this.bottomRow   = bottomRow;
            this.rightColumn = rightColumn;
            this.topRow      = topRow;
            this.role        = role;
            this.blueprintId = blueprintId;
            this.kitId       = kitId;
            this.dense       = dense;
            this.sockets     = sockets == null ? Collections.emptyList()
                                               : Collections.unmodifiableList(new ArrayList<>(sockets));
            this.laneBand    = laneBand == null ? null : Collections.unmodifiableList(new ArrayList<>(laneBand));
        }

        /** A room known only by its bounds, role and blueprint (no kit, sockets or lanes reported). */
        public static RoomRecord bare(int leftColumn, int bottomRow, int rightColumn, int topRow, Role role,
                                      String blueprintId) {
            return new RoomRecord(leftColumn, bottomRow, rightColumn, topRow, role, blueprintId, null, false,
                    null, null);
        }

        /** Whether the tile lies strictly inside the shell. */
        public boolean containsInterior(int tileColumn, int tileRow) {
            return tileColumn > leftColumn && tileColumn < rightColumn && tileRow > bottomRow && tileRow < topRow;
        }

        /** Whether the tile lies on the shell ring. */
        public boolean isShellTile(int tileColumn, int tileRow) {
            boolean insideBounds = tileColumn >= leftColumn && tileColumn <= rightColumn
                    && tileRow >= bottomRow && tileRow <= topRow;
            return insideBounds && !containsInterior(tileColumn, tileRow);
        }
    }

    private final List<RoomRecord> rooms = new ArrayList<>();
    private List<String> plannedSpecials;
    private int repairCarves;
    private int doorwayDemotions;
    private int doorwayFlankFailures;

    /** Adds a placed room. */
    public LevelLayoutReport room(RoomRecord room) {
        rooms.add(room);
        return this;
    }

    /** Records the special types the floor's plan asked for (null = the generator plans none). */
    public LevelLayoutReport plannedSpecials(List<String> plannedSpecialIds) {
        this.plannedSpecials = plannedSpecialIds == null ? null
                : Collections.unmodifiableList(new ArrayList<>(plannedSpecialIds));
        return this;
    }

    /** Records how many connectivity-repair carves fired while the floor was built. */
    public LevelLayoutReport repairCarves(int count) {
        this.repairCarves = count;
        return this;
    }

    /** Records the doorway validator's demotions and the doors that failed the flank rule before it. */
    public LevelLayoutReport doorwayValidation(int flankFailures, int demotions) {
        this.doorwayFlankFailures = flankFailures;
        this.doorwayDemotions     = demotions;
        return this;
    }

    public List<RoomRecord> rooms()            { return Collections.unmodifiableList(rooms); }
    /** The planned special ids, or null when the generator plans no specials. */
    public List<String>     plannedSpecials()  { return plannedSpecials; }
    public int              repairCarves()     { return repairCarves; }
    public int              doorwayDemotions() { return doorwayDemotions; }
    public int              doorwayFlankFailures() { return doorwayFlankFailures; }
}
