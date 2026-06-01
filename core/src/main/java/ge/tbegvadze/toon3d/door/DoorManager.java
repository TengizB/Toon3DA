package ge.tbegvadze.toon3d.door;

import ge.tbegvadze.toon3d.level.KeycardColor;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.Constants;

import java.util.*;

/**
 * Scans the level on construction and registers every 'd' cell as a Door.
 * Single authority over each door's DoorState and animationProgress.
 * No allocations in update().
 */
public class DoorManager {

    // Packed long key (high 32 bits = column, low 32 bits = row) avoids wrapper allocation on lookup.
    private final Map<Long, Door>          doorsByPackedKey;
    // Keycard color required per locked door tile ('R'/'Y'/'B'). Empty = plain door.
    private final Map<Long, KeycardColor>  lockedDoorColors;
    // Set of locked-door keys that the player has permanently unlocked this level.
    private final Set<Long>                unlockedDoors;

    public DoorManager(Level level) {
        doorsByPackedKey = new HashMap<>();
        lockedDoorColors = new HashMap<>();
        unlockedDoors    = new HashSet<>();
        int levelWidth  = level.getWidth();
        int levelHeight = level.getHeight();
        for (int tileRow = 0; tileRow < levelHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < levelWidth; tileColumn++) {
                char cell = level.getCell(tileColumn, tileRow);
                if (Level.isDoor(cell)) {
                    long key = packKey(tileColumn, tileRow);
                    doorsByPackedKey.put(key, new Door(tileColumn, tileRow));
                    if (Level.isLockedDoor(cell)) {
                        lockedDoorColors.put(key, Level.keycardColorOfDoor(cell));
                    }
                }
            }
        }
    }

    private static long packKey(int tileColumn, int tileRow) {
        return (long) tileColumn << 32 | ((long) tileRow & 0xFFFFFFFFL);
    }

    /** Advances every animating door by deltaTime seconds. */
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
