package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.entity.MedicalTier;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.Collections;
import java.util.List;
import ge.tbegvadze.toon3d.util.ItemConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;

/**
 * Tile-based level grid. Coordinates are tile indices.
 * matrix[row][col] — row 0 is the bottom row, matching world Y-up semantics: (0,0) = bottom-left tile.
 */
public class Level {
    protected final char[][]               matrix;
    private final   List<EnemySpawnPoint>  enemySpawnPoints;
    private final   List<WeaponSpawnPoint> weaponSpawnPoints;
    /**
     * Interactive heal-station tiles (route-map order-8 MED-BAY auto-doc). Each entry is a
     * {@code {tileColumn, tileRow}} pair a bespoke generator (e.g. {@code MedBayGenerator}) registered
     * so {@code World} can layer one-time-heal interactivity onto that tile without special-casing the
     * node type. Empty on every ordinary floor. Populated post-construction via {@link #markHealStation}.
     */
    private final   List<int[]>            healStationTiles = new java.util.ArrayList<>();

    Level(char[][] matrix, List<EnemySpawnPoint> enemySpawnPoints,
          List<WeaponSpawnPoint> weaponSpawnPoints) {
        this.matrix            = matrix;
        this.enemySpawnPoints  = Collections.unmodifiableList(enemySpawnPoints);
        this.weaponSpawnPoints = Collections.unmodifiableList(weaponSpawnPoints);
    }

    /**
     * Registers a heal-station (auto-doc) tile so {@code World} can attach a one-time heal to it.
     * Called by a bespoke generator right after it stamps the station prop. No-op for out-of-bounds
     * coordinates.
     */
    public void markHealStation(int tileColumn, int tileRow) {
        if (tileColumn < 0 || tileColumn >= getWidth() || tileRow < 0 || tileRow >= getHeight()) return;
        healStationTiles.add(new int[]{tileColumn, tileRow});
    }

    /**
     * The heal-station tiles registered on this floor ({@code {tileColumn, tileRow}} pairs). Empty on
     * ordinary floors. The returned list is unmodifiable; {@code World} copies it to track charge state.
     */
    public List<int[]> getHealStationTiles() {
        return Collections.unmodifiableList(healStationTiles);
    }

    /** Returns the read-only list of enemy spawn points extracted by LevelLoader. */
    public List<EnemySpawnPoint> getEnemySpawnPoints() {
        return enemySpawnPoints;
    }

    /** Returns the read-only list of weapon pickup spawn points placed by LevelGenerator. */
    public List<WeaponSpawnPoint> getWeaponSpawnPoints() {
        return weaponSpawnPoints;
    }

    /**
     * Overwrites one tile in the grid at runtime (e.g. stamping a corpse 'm' on enemy death).
     * No-op if the coordinates are out of bounds.
     */
    public void setCell(int tileColumn, int tileRow, char value) {
        if (tileColumn < 0 || tileColumn >= getWidth() || tileRow < 0 || tileRow >= getHeight()) return;
        matrix[tileRow][tileColumn] = value;
    }

    public char getCell(int x, int y) {
        if (x < 0 || x >= getWidth() || y < 0 || y >= getHeight()) return '\0';
        return matrix[y][x];
    }

    /** Returns true for any symbol that represents a solid wall tile. */
    public static boolean isWall(char cell) {
        return cell == 'x' || cell == 'c' || cell == 'v' || cell == 't' || cell == 'w' || cell == 'h'
            || cell == 'j' || cell == 'G' || cell == 'k'
            || cell == 'N' || cell == 'Q' || cell == 'S' || cell == 'M'
            || cell == 'Z' || cell == 'U' || cell == 'X'
            || cell == 'D' || cell == 'F';
    }

    /** Returns true for any symbol that represents a door tile (plain or keycard-locked). */
    public static boolean isDoor(char cell) {
        return cell == 'd' || isLockedDoor(cell);
    }

    /** Returns true for keycard-locked door tiles ('R'=red, 'Y'=yellow, 'B'=blue). */
    public static boolean isLockedDoor(char cell) {
        return cell == 'R' || cell == 'Y' || cell == 'B';
    }

    /**
     * Returns the keycard color required to unlock the given door tile.
     * Caller must guard with isLockedDoor() first.
     */
    public static KeycardColor keycardColorOfDoor(char cell) {
        switch (cell) {
            case 'R': return KeycardColor.RED;
            case 'Y': return KeycardColor.YELLOW;
            default:  return KeycardColor.BLUE;
        }
    }

    /** Returns true for floor-decal keycard pickups ('r'=red, 'y'=yellow, 'b'=blue). */
    public static boolean isKeycardPickup(char cell) {
        return cell == 'r' || cell == 'y' || cell == 'b';
    }

    /**
     * Returns the keycard color of the given pickup decal.
     * Caller must guard with isKeycardPickup() first.
     */
    public static KeycardColor keycardColorOfPickup(char cell) {
        switch (cell) {
            case 'r': return KeycardColor.RED;
            case 'y': return KeycardColor.YELLOW;
            default:  return KeycardColor.BLUE;
        }
    }

    /**
     * Removes a keycard pickup from the grid (replaces with lit-floor space).
     * No-op if the cell is not a keycard pickup.
     */
    public void consumeKeycardAt(int tileColumn, int tileRow) {
        if (tileColumn < 0 || tileColumn >= getWidth() || tileRow < 0 || tileRow >= getHeight()) return;
        if (isKeycardPickup(matrix[tileRow][tileColumn])) {
            matrix[tileRow][tileColumn] = ' ';
        }
    }

    /** Returns true for a sub-cell cylindrical column tile ('P').
     *  The DDA passes through the cell and uses ray-circle intersection to find the
     *  precise hit; the cell is still movement-blocked via isBlockedAt. */
    public static boolean isColumn(char cell) {
        return cell == 'P';
    }

    /** Returns true for floor-decal medical pickups ('+' stim-pack, 'H' field medkit). */
    public static boolean isMedicalPickup(char cell) {
        return cell == '+' || cell == 'H';
    }

    /**
     * Returns the MedicalTier for a medical pickup cell.
     * Caller must guard with isMedicalPickup() first.
     */
    public static MedicalTier medicalTierOfPickup(char cell) {
        return cell == '+' ? MedicalTier.STIM : MedicalTier.FIELD_MEDKIT;
    }

    /** Returns true for floor-decal armour pickups ('a' shard, 'A' security vest). */
    public static boolean isArmourPickup(char cell) {
        return cell == 'a' || cell == 'A';
    }

    /**
     * Returns true for ammo-box pickup tiles ('6'=bullets, '7'=shells, '8'=cells,
     * '9'=rockets, '0'=slugs). These are walkable; the player collects them on step.
     */
    public static boolean isAmmoPickup(char cell) {
        return cell == '6' || cell == '7' || cell == '8' || cell == '9' || cell == '0';
    }

    /**
     * Returns the AmmoType for the given ammo-pickup tile.
     * Caller must guard with isAmmoPickup() first.
     */
    public static AmmoType ammoTypeOfPickup(char cell) {
        AmmoType type = AmmoType.fromPickupChar(cell);
        if (type == null) throw new IllegalArgumentException("Not an ammo pickup: " + cell);
        return type;
    }

    /**
     * Returns the armour points granted by an armour pickup cell.
     * Caller must guard with isArmourPickup() first.
     */
    public static int armourRestoreOfPickup(char cell) {
        return cell == 'a' ? ItemConstants.ARMOUR_SHARD_VALUE : ItemConstants.ARMOUR_VEST_VALUE;
    }

    /**
     * Removes a pickup from the grid (replaces with lit-floor space).
     * No-op if out of bounds.
     */
    public void consumePickupAt(int tileColumn, int tileRow) {
        if (tileColumn < 0 || tileColumn >= getWidth() || tileRow < 0 || tileRow >= getHeight()) return;
        matrix[tileRow][tileColumn] = ' ';
    }

    /** Returns true when the cell is a stairs-down exit tile (classic roguelike '>' glyph). */
    public static boolean isStairsDown(char cell) {
        return cell == RenderConstants.STAIRS_DOWN_CHAR;
    }

    /**
     * Returns true for any symbol that represents a prop (billboard sprite on a floor tile).
     * 'g' = radioactive barrel (dark green); 'E' = explosive barrel (orange-red).
     */
    public static boolean isProp(char cell) {
        return cell == 'g' || cell == 'E' || cell == 'T' || cell == 'L' || cell == 'C'
            || cell == '#' || cell == '%' || cell == '&' || cell == '=' || cell == '@'
            || cell == 'I' || cell == 'J' || cell == 'W'
            || cell == 'm' || cell == 's' || cell == '.' || cell == 'O' || cell == 'e'
            || isHazardDecal(cell)
            || isKeycardPickup(cell) || isMedicalPickup(cell) || isArmourPickup(cell)
            || isAmmoPickup(cell)
            || isStairsDown(cell);
    }

    /** Returns true for a fire hazard floor tile ('i'). Walkable; ticks BURNING via HazardManager. */
    public static boolean isHazardFire(char cell) {
        return cell == 'i';
    }

    /** Returns true for a toxic hazard pool tile ('q'). Walkable; ticks POISONED via HazardManager. */
    public static boolean isHazardToxic(char cell) {
        return cell == 'q';
    }

    /** Returns true for any dynamic hazard floor decal (fire 'i' or toxic 'q'). */
    public static boolean isHazardDecal(char cell) {
        return isHazardFire(cell) || isHazardToxic(cell);
    }

    /**
     * Returns true for a plain floor or flat stain tile that fire may spread onto and that a
     * hazard may overlay (then restore). Excludes walls, doors, solid props, columns, pickups,
     * keycards and stairs so a hazard never destroys a meaningful tile.
     */
    public static boolean isHazardSpreadable(char cell) {
        return cell == ' ' || cell == 'l' || cell == 'u' || cell == 'f'
            || cell == 'm' || cell == 's' || cell == '.' || cell == 'O' || cell == 'e';
    }

    /** Returns true for solid props that block player movement (barrels, terminals, lockers, crates, new equipment). */
    public static boolean isPropSolid(char cell) {
        return cell == 'g' || cell == 'E' || cell == 'T' || cell == 'L' || cell == 'C'
            || cell == '#' || cell == '%' || cell == '&' || cell == '=' || cell == '@'
            || cell == 'I' || cell == 'J' || cell == 'W';
    }

    /** Returns true for a shop vending machine tile ('@'), stamped solid by World.placeMachine. */
    public static boolean isShop(char cell) {
        return cell == '@';
    }

    /** Returns true for walkable decal props (corpses, dropped items, stains, keycard pickups, medical, armour, ammo pickups, stairs). */
    public static boolean isPropDecal(char cell) {
        return cell == 'm' || cell == 's' || cell == '.' || cell == 'O' || cell == 'e'
            || isHazardDecal(cell)
            || isKeycardPickup(cell) || isMedicalPickup(cell) || isArmourPickup(cell)
            || isAmmoPickup(cell)
            || isStairsDown(cell);
    }

    /**
     * Returns true for props whose billboard sprite should write to the z-buffer, so that
     * EnemyRenderer correctly occludes enemies that stand behind them.
     *
     * Excludes the five flat floor stains/decals that enemies visually walk over:
     * corpse ('m'), dropped-shotgun decal ('s'), blood ('.'), oil ('O'), energy scorch ('e').
     * All other props — solid equipment, pickup items, keycards, stairs — are elevated
     * billboards that should block sprites drawn later in the render order.
     */
    public static boolean isPropOccluder(char cell) {
        return isPropSolid(cell)
            || isKeycardPickup(cell)
            || isMedicalPickup(cell)
            || isArmourPickup(cell)
            || isAmmoPickup(cell)
            || isStairsDown(cell);
    }

    /**
     * Unified movement-blocking check. Returns true if the player cannot step into this cell.
     * Combines static wall and solid-prop solidity with dynamic door state.
     *
     * @param tileColumn  tile-grid column to test (X axis, 0 = left edge)
     * @param tileRow     tile-grid row    to test (Y axis, 0 = bottom edge, Y-up)
     * @param doorManager live door state authority; queried only when the cell is a door tile
     * @return true if the cell is impassable to the player right now
     */
    public boolean isBlockedAt(int tileColumn, int tileRow, DoorManager doorManager) {
        char cell = getCell(tileColumn, tileRow);
        if (isWall(cell)) return true;
        if (isDoor(cell)) return !doorManager.isPassable(tileColumn, tileRow);
        if (isPropSolid(cell)) return true;
        if (isColumn(cell)) return true;
        return false;
    }

    /**
     * Returns the ambient brightness multiplier for the given floor tile.
     * Solid walls and out-of-bounds tiles return BASE_TILE_BRIGHTNESS (1.0).
     *
     * @param tileColumn  tile-grid column (X axis)
     * @param tileRow     tile-grid row    (Y axis, Y-up: 0 = bottom)
     * @param timeSeconds monotonically increasing facility clock, used only for 'f' tiles
     * @return brightness multiplier to compose with distance shading
     */
    public float getTileBrightness(int tileColumn, int tileRow, float timeSeconds) {
        char tile = getCell(tileColumn, tileRow);
        if (tile == RenderConstants.LIT_TILE_CHAR)        return RenderConstants.LIT_TILE_BRIGHTNESS;
        if (tile == RenderConstants.UNLIT_TILE_CHAR)      return RenderConstants.UNLIT_TILE_BRIGHTNESS;
        if (tile == RenderConstants.FLICKERING_TILE_CHAR) return GameMath.flickerMultiplier(tileColumn, tileRow, timeSeconds);
        // Stairs tile is always fully lit so it's never lost in dark zones.
        if (tile == RenderConstants.STAIRS_DOWN_CHAR)     return RenderConstants.LIT_TILE_BRIGHTNESS;
        return RenderConstants.BASE_TILE_BRIGHTNESS;
    }

    public int getWidth() {
        return matrix.length == 0 ? 0 : matrix[0].length;
    }

    public int getHeight() {
        return matrix.length;
    }
}
