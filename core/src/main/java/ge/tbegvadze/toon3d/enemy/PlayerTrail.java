package ge.tbegvadze.toon3d.enemy;

/**
 * THE TRAIL of a HUNTED floor (floor-twists R10): every tile the player settled on, in order, as a
 * pre-allocated ring buffer. Headless, no allocation after construction.
 *
 * <p>Entries are addressed by a monotonically increasing SEQUENCE number (0 = the arrival tile), never by
 * buffer slot, so a reader (the trail hunter) can tell "the entry I stood on" from "an entry that has since
 * been overwritten" — a sequence below {@link #oldestSequence()} is gone (R11f: the hunter falls back to
 * greedy). A tile is appended only when it differs from the newest entry, so a turn spent standing still
 * (fire, reload, rotate, skip) records nothing.
 */
public final class PlayerTrail {

    private final int[] columns;
    private final int[] rows;
    private final int   capacity;
    /** Sequence number the NEXT recorded entry will receive; equals the total ever recorded. */
    private int nextSequence = 0;

    public PlayerTrail(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.columns  = new int[this.capacity];
        this.rows     = new int[this.capacity];
    }

    /** Forgets the whole trail (a new floor). */
    public void clear() {
        nextSequence = 0;
    }

    /** Appends the tile if it differs from the newest entry. */
    public void record(int tileColumn, int tileRow) {
        if (nextSequence > 0) {
            int newestSlot = slotOf(nextSequence - 1);
            if (columns[newestSlot] == tileColumn && rows[newestSlot] == tileRow) return;
        }
        int slot = slotOf(nextSequence);
        columns[slot] = tileColumn;
        rows[slot]    = tileRow;
        nextSequence++;
    }

    public boolean isEmpty() {
        return nextSequence == 0;
    }

    /** The newest entry's sequence number, or -1 when empty. */
    public int newestSequence() {
        return nextSequence - 1;
    }

    /** The oldest entry still held (older ones were overwritten when the buffer wrapped). */
    public int oldestSequence() {
        return Math.max(0, nextSequence - capacity);
    }

    /** True when the entry with this sequence number is still in the buffer. */
    public boolean holds(int sequence) {
        return sequence >= oldestSequence() && sequence < nextSequence;
    }

    /** Column of a held entry (caller checks {@link #holds}). */
    public int columnAt(int sequence) {
        return columns[slotOf(sequence)];
    }

    /** Row of a held entry (caller checks {@link #holds}). */
    public int rowAt(int sequence) {
        return rows[slotOf(sequence)];
    }

    private int slotOf(int sequence) {
        return sequence % capacity;
    }
}
