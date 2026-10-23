package ge.tbegvadze.toon3d.level;

/**
 * A SOCKET (level-generation-overhaul R10/R11): one tile of a room's shell that is a doorway — a door leaf
 * or an open doorway floor tile. Its OUTER tile is the corridor tile straight out from it; its INNER tile is
 * the room's interior tile straight in. Headless — no LibGDX.
 */
public final class DoorSocket {

    /** The room side a socket sits on, with the outward step (one constant per side, no switch). */
    public enum Side {
        NORTH(0, 1), SOUTH(0, -1), EAST(1, 0), WEST(-1, 0);

        /** The outward step from the room's interior through this side. */
        public final int stepColumn;
        public final int stepRow;

        Side(int stepColumn, int stepRow) {
            this.stepColumn = stepColumn;
            this.stepRow    = stepRow;
        }

        /** Whether the side runs along a row (NORTH / SOUTH), so its doorway's passage axis is vertical. */
        public boolean isHorizontalWall() {
            return stepRow != 0;
        }
    }

    /** What fills the socket tile. */
    public enum Leaf { DOOR, OPEN }

    public final int  tileColumn;
    public final int  tileRow;
    public final Side side;
    private Leaf      leaf = Leaf.OPEN;

    public DoorSocket(int tileColumn, int tileRow, Side side) {
        this.tileColumn = tileColumn;
        this.tileRow    = tileRow;
        this.side       = side;
    }

    public int outerColumn() { return tileColumn + side.stepColumn; }
    public int outerRow()    { return tileRow + side.stepRow; }
    public int innerColumn() { return tileColumn - side.stepColumn; }
    public int innerRow()    { return tileRow - side.stepRow; }

    public Leaf leaf() { return leaf; }

    void setLeaf(Leaf leaf) {
        this.leaf = leaf;
    }
}
