package ge.tbegvadze.toon3d.level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A ROOM as the shared layout engine sees it (level-generation-overhaul): an interior rectangle plus its
 * one-tile wall ring (the SHELL), its role, its blueprint, and its sockets. Every generator hands its rooms
 * to {@link DoorwayRules} (and, from the prop engine on, to the room dresser) as LayoutRooms; a generator
 * may keep a private room class of its own, but the engine sees only these. Bounds are SHELL bounds (walls
 * inclusive). Headless — no LibGDX.
 */
public final class LayoutRoom {

    public final int leftColumn;
    public final int bottomRow;
    public final int rightColumn;
    public final int topRow;
    public final LevelLayoutReport.Role role;
    /** The blueprint id (RoomBlueprints.ID_*); never null. */
    public final String blueprintId;

    private final List<DoorSocket> sockets = new ArrayList<>();
    /** The socket on this room's corridor toward the entrance (the entrance's first socket); null until set. */
    private DoorSocket primarySocket;

    // Architecture exports (R17 FEATURE anchors) and the centre a feature claims (the HUB becomes its ring).
    private final Map<String, List<int[]>> features = new LinkedHashMap<>();
    private List<int[]> centreFeature;

    // What the dresser did (R16-R21): the kit, whether it dressed DENSE (a TIGHT room), the lane band it kept.
    private String      kitId;
    private boolean     dense;
    private List<int[]> laneBand;

    public LayoutRoom(int leftColumn, int bottomRow, int rightColumn, int topRow,
                      LevelLayoutReport.Role role, String blueprintId) {
        this.leftColumn  = leftColumn;
        this.bottomRow   = bottomRow;
        this.rightColumn = rightColumn;
        this.topRow      = topRow;
        this.role        = role;
        this.blueprintId = blueprintId;
    }

    public int interiorWidth()  { return rightColumn - leftColumn - 1; }
    public int interiorHeight() { return topRow - bottomRow - 1; }
    public int centerColumn()   { return (leftColumn + rightColumn) / 2; }
    public int centerRow()      { return (bottomRow + topRow) / 2; }

    /** Whether the tile lies strictly inside the shell. */
    public boolean containsInterior(int tileColumn, int tileRow) {
        return tileColumn > leftColumn && tileColumn < rightColumn && tileRow > bottomRow && tileRow < topRow;
    }

    /** Whether the tile lies on the shell ring. */
    public boolean isShellTile(int tileColumn, int tileRow) {
        boolean inside = tileColumn >= leftColumn && tileColumn <= rightColumn
                && tileRow >= bottomRow && tileRow <= topRow;
        return inside && !containsInterior(tileColumn, tileRow);
    }

    /** Whether the tile is inside the shell bounds (shell or interior). */
    public boolean containsTile(int tileColumn, int tileRow) {
        return tileColumn >= leftColumn && tileColumn <= rightColumn && tileRow >= bottomRow && tileRow <= topRow;
    }

    /** The side a non-corner shell tile sits on, or null for a corner or a tile off the shell. */
    public DoorSocket.Side sideOf(int tileColumn, int tileRow) {
        if (!isShellTile(tileColumn, tileRow)) return null;
        boolean columnEdge = tileColumn == leftColumn || tileColumn == rightColumn;
        boolean rowEdge    = tileRow == bottomRow || tileRow == topRow;
        if (columnEdge && rowEdge) return null;
        if (tileRow == topRow)       return DoorSocket.Side.NORTH;
        if (tileRow == bottomRow)    return DoorSocket.Side.SOUTH;
        if (tileColumn == rightColumn) return DoorSocket.Side.EAST;
        return DoorSocket.Side.WEST;
    }

    public List<DoorSocket> sockets() {
        return Collections.unmodifiableList(sockets);
    }

    void addSocket(DoorSocket socket) {
        sockets.add(socket);
        if (primarySocket == null) primarySocket = socket;
    }

    /** The socket at a shell tile, or null. */
    public DoorSocket socketAt(int tileColumn, int tileRow) {
        for (DoorSocket socket : sockets) {
            if (socket.tileColumn == tileColumn && socket.tileRow == tileRow) return socket;
        }
        return null;
    }

    public DoorSocket primarySocket() {
        return primarySocket;
    }

    void setPrimarySocket(DoorSocket socket) {
        this.primarySocket = socket;
    }

    /** Exports named architecture tiles for FEATURE anchors (e.g. "core", "spokes", "alcove"). */
    public void exportFeature(String name, List<int[]> tiles) {
        features.put(name, new ArrayList<>(tiles));
    }

    /** The tiles a feature exported, or an empty list. */
    public List<int[]> feature(String name) {
        List<int[]> tiles = features.get(name);
        return tiles == null ? Collections.emptyList() : Collections.unmodifiableList(tiles);
    }

    /** Claims the room's centre for an architecture feature: the HUB becomes the ring around these tiles. */
    public void claimCentre(List<int[]> tiles) {
        this.centreFeature = new ArrayList<>(tiles);
    }

    /** The architecture's centre claim, or null. */
    public List<int[]> centreFeature() {
        return centreFeature == null ? null : Collections.unmodifiableList(centreFeature);
    }

    void recordDressing(String dressedKitId, boolean dressedDense, List<int[]> band) {
        this.kitId    = dressedKitId;
        this.dense    = dressedDense;
        this.laneBand = band == null ? null : new ArrayList<>(band);
    }

    public String      kitId()    { return kitId; }
    public boolean     dense()    { return dense; }
    public List<int[]> laneBand() { return laneBand == null ? null : Collections.unmodifiableList(laneBand); }

    /** This room as the layout report records it. */
    public LevelLayoutReport.RoomRecord toRecord() {
        List<int[]> socketTiles = new ArrayList<>();
        for (DoorSocket socket : sockets) socketTiles.add(new int[]{ socket.tileColumn, socket.tileRow });
        return new LevelLayoutReport.RoomRecord(leftColumn, bottomRow, rightColumn, topRow, role, blueprintId,
                kitId, dense, socketTiles, laneBand);
    }
}
