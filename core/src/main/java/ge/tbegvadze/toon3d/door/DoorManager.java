package ge.tbegvadze.toon3d.door;

import ge.tbegvadze.toon3d.level.KeycardColor;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.Constants;

import java.util.*;

/**
 * Scans the level on construction and registers every 'd' cell as a Door.
 * Single authority over each door's DoorState and animationProgress.
 * No allocations in update().
 *
 * Render fast-path: openFractionAtFast() and blocksSightFast() read flat arrays
 * indexed by (tileRow * snapshotWidth + tileColumn) instead of HashMap lookup +
 * autoboxing. The snapshot is rebuilt once per frame in update() by iterating the
 * small door map (typically ~10 doors), eliminating up to 1280 boxed-Long allocations
 * per frame that previously occurred in WallRenderer's per-column DDA loop.
 */
public class DoorManager {

    // Packed long key (high 32 bits = column, low 32 bits = row) avoids wrapper allocation on lookup.
    private final Map<Long, Door>         doorsByPackedKey;
    // Keycard color required per locked door tile ('R'/'Y'/'B'). Empty = plain door.
    private final Map<Long, KeycardColor> lockedDoorColors;
    // Set of locked-door keys that the player has permanently unlocked this level.
    private final Set<Long>               unlockedDoors;

    // Flat per-cell arrays for the render hot path — indexed by (row * snapshotWidth + column).
    // Refreshed in update() after advancing animations; WallRenderer reads these directly.
    private final float[]   openFractionSnapshot;
    private final boolean[] blocksSightSnapshot;
    private final int       snapshotWidth;

    public DoorManager(Level level) {
        doorsByPackedKey = new HashMap<>();
        lockedDoorColors = new HashMap<>();
        unlockedDoors    = new HashSet<>();
        int levelWidth  = level.getWidth();
        int levelHeight = level.getHeight();
        snapshotWidth        = levelWidth;
        openFractionSnapshot = new float[levelWidth * levelHeight];
        blocksSightSnapshot  = new boolean[levelWidth * levelHeight];
        for (int tileRow = 0; tileRow < levelHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < levelWidth; tileColumn++) {
                char cell = level.getCell(tileColumn, tileRow);
                if (Level.isDoor(cell)) {
                    long key = packKey(tileColumn, tileRow);
                    doorsByPackedKey.put(key, new Door(tileColumn, tileRow));
                    if (Level.isLockedDoor(cell)) {
                        lockedDoorColors.put(key, Level.keycardColorOfDoor(cell));
                    }
                    // All doors start CLOSED: openFraction = 0 (array default), blocksSight = true.
                    blocksSightSnapshot[tileRow * levelWidth + tileColumn] = true;
                }
            }
        }
    }

    private static long packKey(int tileColumn, int tileRow) {
        return (long) tileColumn << 32 | ((long) tileRow & 0xFFFFFFFFL);
    }

    /**
     * Advances every animating door by deltaTime seconds, then refreshes the flat snapshot
     * arrays so the render hot-path reads current values without any HashMap lookup.
     */
    public void update(float deltaTime) {
        for (Door door : doorsByPackedKey.values()) {
            switch (door.state) {
                case OPENING:
                    door.animationProgress += deltaTime / Constants.DOOR_OPEN_DURATION;
                    if (door.animationProgress >= 1f) {
                        door.animationProgress = 0f;
                        door.state             = DoorState.OPEN;
                    }
                    break;
                case CLOSING:
                    door.animationProgress += deltaTime / Constants.DOOR_CLOSE_DURATION;
                    if (door.animationProgress >= 1f) {
                        door.animationProgress = 0f;
                        door.state             = DoorState.CLOSED;
                    }
                    break;
                case OPEN:
                case CLOSED:
                    break;
            }
        }
        refreshSnapshot();
    }

    private void refreshSnapshot() {
        for (Door door : doorsByPackedKey.values()) {
            int snapshotIndex = door.tileRow * snapshotWidth + door.tileColumn;
            switch (door.state) {
                case CLOSED:
                    openFractionSnapshot[snapshotIndex] = 0f;
                    blocksSightSnapshot[snapshotIndex]  = true;
                    break;
                case OPENING:
                    openFractionSnapshot[snapshotIndex] = door.animationProgress;
                    blocksSightSnapshot[snapshotIndex]  = false;
                    break;
                case OPEN:
                    openFractionSnapshot[snapshotIndex] = 1f;
                    blocksSightSnapshot[snapshotIndex]  = false;
                    break;
                case CLOSING:
                    openFractionSnapshot[snapshotIndex] = 1f - door.animationProgress;
                    blocksSightSnapshot[snapshotIndex]  = true;
                    break;
            }
        }
    }

    /**
     * O(1) open-fraction lookup for the render hot path.
     * Reads the flat snapshot array; no HashMap lookup, no autoboxing.
     * Returns 0 for non-door tiles or out-of-bounds positions.
     */
    public float openFractionAtFast(int tileColumn, int tileRow) {
        if (tileColumn < 0 || tileRow < 0) return 0f;
        int snapshotIndex = tileRow * snapshotWidth + tileColumn;
        if (snapshotIndex >= openFractionSnapshot.length) return 0f;
        return openFractionSnapshot[snapshotIndex];
    }

    /**
     * O(1) sight-blocking lookup for the render hot path.
     * Reads the flat snapshot array; no HashMap lookup, no autoboxing.
     * Returns false for non-door tiles or out-of-bounds positions.
     */
    public boolean blocksSightFast(int tileColumn, int tileRow) {
        if (tileColumn < 0 || tileRow < 0) return false;
        int snapshotIndex = tileRow * snapshotWidth + tileColumn;
        if (snapshotIndex >= blocksSightSnapshot.length) return false;
        return blocksSightSnapshot[snapshotIndex];
    }

    /** Returns the door's state, or CLOSED if no door exists at the given position. */
    public DoorState getStateAt(int tileColumn, int tileRow) {
        Door door = doorsByPackedKey.get(packKey(tileColumn, tileRow));
        if (door == null) return DoorState.CLOSED;
        return door.state;
    }

    /** Returns open fraction in [0,1] (0=closed, 1=fully open), or 0 if no door here. */
    public float getOpenFractionAt(int tileColumn, int tileRow) {
        Door door = doorsByPackedKey.get(packKey(tileColumn, tileRow));
        if (door == null) return 0f;
        switch (door.state) {
            case CLOSED:  return 0f;
            case OPENING: return door.animationProgress;
            case OPEN:    return 1f;
            case CLOSING: return 1f - door.animationProgress;
            default:      return 0f;
        }
    }

    /**
     * Returns the keycard color required by a locked door, or null if this door is plain.
     * Returns null for unknown tile positions.
     */
    public KeycardColor getRequiredKeycard(int tileColumn, int tileRow) {
        return lockedDoorColors.get(packKey(tileColumn, tileRow));
    }

    /** Returns true if this locked door has been permanently unlocked by the player this level. */
    public boolean isUnlocked(int tileColumn, int tileRow) {
        long key = packKey(tileColumn, tileRow);
        return !lockedDoorColors.containsKey(key) || unlockedDoors.contains(key);
    }

    /**
     * Permanently unlocks a keycard door. After this call the door behaves exactly like
     * a plain 'd' door for the rest of the level.
     */
    public void unlock(int tileColumn, int tileRow) {
        unlockedDoors.add(packKey(tileColumn, tileRow));
    }

    /**
     * Returns true if the door is OPEN or CLOSING (panel retracted enough to pass through).
     * Locked doors that have not been unlocked are never passable.
     */
    public boolean isPassable(int tileColumn, int tileRow) {
        long key = packKey(tileColumn, tileRow);
        if (lockedDoorColors.containsKey(key) && !unlockedDoors.contains(key)) return false;
        Door door = doorsByPackedKey.get(key);
        if (door == null) return false;
        return door.state == DoorState.OPEN || door.state == DoorState.CLOSING;
    }

    /**
     * Requests the door begin opening. CLOSING doors reverse seamlessly:
     * animationProgress carries over as (1 - progress) to avoid a visual jump.
     */
    public void requestOpen(int tileColumn, int tileRow) {
        Door door = doorsByPackedKey.get(packKey(tileColumn, tileRow));
        if (door == null) return;
        switch (door.state) {
            case CLOSED:
                door.state             = DoorState.OPENING;
                door.animationProgress = 0f;
                break;
            case CLOSING:
                door.animationProgress = 1f - door.animationProgress;
                door.state             = DoorState.OPENING;
                break;
            case OPENING:
            case OPEN:
                break;
        }
    }

    /**
     * Called when the player settles on a new tile. Every OPEN door not under
     * the player transitions to CLOSING (auto-close design rule).
     */
    public void notifyPlayerSettled(int playerTileColumn, int playerTileRow) {
        for (Door door : doorsByPackedKey.values()) {
            if (door.state != DoorState.OPEN) continue;
            if (door.tileColumn != playerTileColumn || door.tileRow != playerTileRow) {
                door.state             = DoorState.CLOSING;
                door.animationProgress = 0f;
            }
        }
    }

    /**
     * Returns true if this door tile blocks line-of-sight (CLOSED or CLOSING).
     * OPEN and OPENING doors allow sight through them.
     * Non-door tiles return false.
     */
    public boolean blocksSight(int tileColumn, int tileRow) {
        Door door = doorsByPackedKey.get(packKey(tileColumn, tileRow));
        if (door == null) return false;
        return door.state == DoorState.CLOSED || door.state == DoorState.CLOSING;
    }

    /** Live collection of all Door objects; used by renderers (e.g. mini-map). */
    public Collection<Door> getAllDoors() {
        return doorsByPackedKey.values();
    }
}
