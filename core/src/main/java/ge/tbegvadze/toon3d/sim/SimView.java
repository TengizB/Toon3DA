package ge.tbegvadze.toon3d.sim;

import java.util.List;

import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.enemy.IntentVerb;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * The read-only picture of the world a {@link PlayerPolicy} decides from (new-game-balancr order 9).
 *
 * <p>Everything here is a QUERY: a policy may read the level, the roster, its own vitals and its own
 * ammo, exactly like a player reading the screen. It may not mutate anything — every change flows
 * back through a {@link ge.tbegvadze.toon3d.input.touch.TouchAction} that {@link SimWorld} feeds to
 * the real {@code PlayerController}, so a policy can never take a move the game itself would refuse.
 *
 * <p>One instance is built per floor and re-read each turn (the underlying managers are the live
 * ones), so no per-turn allocation happens here.
 */
public final class SimView {

    private final Level        level;
    private final DoorManager  doorManager;
    private final EnemyManager enemyManager;
    private final Player       player;
    private final Inventory    itemInventory;
    private final SimWorld     world;

    SimView(Level level, DoorManager doorManager, EnemyManager enemyManager,
            Player player, Inventory itemInventory, SimWorld world) {
        this.level         = level;
        this.doorManager   = doorManager;
        this.enemyManager  = enemyManager;
        this.player        = player;
        this.itemInventory = itemInventory;
        this.world         = world;
    }

    // -------------------------------------------------------------------------
    // World
    // -------------------------------------------------------------------------

    public Level        level()        { return level; }
    public DoorManager  doorManager()  { return doorManager; }
    public EnemyManager enemyManager() { return enemyManager; }

    /** Every enemy on the floor, alive or dead — callers filter with {@link Enemy#isAlive()}. */
    public List<Enemy> enemies() { return enemyManager.getEnemies(); }

    public int  depth()            { return world.getCurrentDepth(); }
    public boolean isBossFloor()   { return world.isBossFloor(); }
    /** Player actions spent on this floor so far — a policy's clock for "am I lost?". */
    public int  turnsOnFloor()     { return world.getTurnsOnFloor(); }

    // -------------------------------------------------------------------------
    // The marine
    // -------------------------------------------------------------------------

    public int playerTileColumn() { return GameMath.worldToTile(player.positionX); }
    public int playerTileRow()    { return GameMath.worldToTile(player.positionY); }

    /** Facing as a cardinal step: one of (1,0), (-1,0), (0,1), (0,-1). */
    public int facingStepColumn() { return Math.round(player.directionX); }
    public int facingStepRow()    { return Math.round(player.directionY); }

    public int   health()          { return player.getHealth(); }
    public int   armor()           { return player.getArmor(); }
    public float healthFraction()  { return player.getHealthFraction(); }

    /**
     * Health + armour as a fraction of the full pool — the "how close am I to dying" number a policy
     * should react to, since armour absorbs a fixed share of every hit.
     */
    public float vitalityFraction() {
        return GameMath.vitalityFraction(player.getHealth(), player.getArmor(),
                                         player.getMaxHealth(), player.getMaxArmor());
    }

    public int medkitCount() {
        return itemInventory.countOf(ItemType.MEDKIT_SMALL) + itemInventory.countOf(ItemType.MEDKIT_LARGE);
    }

    // -------------------------------------------------------------------------
    // The gun
    // -------------------------------------------------------------------------

    public Weapon equippedWeapon() { return world.getEquippedWeapon(); }

    /** Reserve rounds left for the equipped weapon's ammo type (0 for melee, or when out). */
    public int reserveAmmoForEquipped() {
        Weapon weapon = equippedWeapon();
        if (weapon == null) return 0;
        AmmoType ammoType = weapon.getAmmoType();
        if (ammoType == null) return 0;
        return itemInventory.countOf(ammoType.getItemType());
    }

    /** True when the equipped weapon has neither a loaded round nor a reserve to reload from. */
    public boolean equippedWeaponIsDry() {
        Weapon weapon = equippedWeapon();
        if (weapon == null) return true;
        if (weapon.getAmmoType() == null) return false;    // melee never runs dry
        return weapon.getShotsInClip() <= 0 && reserveAmmoForEquipped() <= 0;
    }

    /** The loaded+reserve rounds for a specific slot's weapon — used when deciding to switch guns. */
    public int roundsAvailableFor(Weapon weapon) {
        if (weapon == null) return 0;
        AmmoType ammoType = weapon.getAmmoType();
        if (ammoType == null) return Integer.MAX_VALUE;     // melee: always "available"
        return weapon.getShotsInClip() + itemInventory.countOf(ammoType.getItemType());
    }

    // -------------------------------------------------------------------------
    // Threat reading — the same information the HUD and the intent glyphs show
    // -------------------------------------------------------------------------

    /** The closest living enemy by Chebyshev tile distance, or null when the floor is clear. */
    public Enemy nearestEnemy() {
        Enemy nearest = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Enemy enemy : enemies()) {
            if (!enemy.isAlive()) continue;
            int distance = tileDistanceTo(enemy);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest      = enemy;
            }
        }
        return nearest;
    }

    /** Chebyshev (king-move) tile distance from the marine to an enemy. */
    public int tileDistanceTo(Enemy enemy) {
        return GameMath.chebyshevDistanceTiles(playerTileColumn(), playerTileRow(),
                                               enemy.tileColumn, enemy.tileRow);
    }

    /** Living enemies remaining on the floor. */
    public int liveEnemyCount() {
        int count = 0;
        for (Enemy enemy : enemies()) if (enemy.isAlive()) count++;
        return count;
    }

    /**
     * True when a living enemy has COMMITTED an attack intent for its next turn — the readable
     * "incoming damage" tell (strategy-combat intent system). This is what makes a death fair: the
     * player was shown the hit before it landed.
     */
    public boolean telegraphedThreatVisible() {
        for (Enemy enemy : enemies()) {
            if (!enemy.isAlive() || !enemy.plannedAction.committed) continue;
            IntentVerb verb = enemy.plannedAction.verb;
            if (verb == IntentVerb.ATTACK_MELEE || verb == IntentVerb.ATTACK_RANGED
                    || verb == IntentVerb.WIND_UP || verb == IntentVerb.SPECIAL) {
                return true;
            }
        }
        return false;
    }

    /** Total damage every committed enemy intent promises to land next turn. */
    public int incomingTelegraphedDamage() {
        int total = 0;
        for (Enemy enemy : enemies()) {
            if (!enemy.isAlive() || !enemy.plannedAction.committed) continue;
            total += Math.max(0, enemy.plannedAction.predictedDamage);
        }
        return total;
    }

    /**
     * True when a ranged enemy can actually SHOOT the marine where it stands: awake, in range, with a
     * clear line, and sharing a row or column (the cardinal-line constraint enforced by
     * {@code EnemyManager.isSameCardinalLine}). Breaking that line is the counterplay a TACTICAL
     * player is supposed to use — but only against a shooter that is genuinely aiming, otherwise a
     * policy sidesteps away from a sniper three rooms away for the rest of the floor.
     */
    public boolean standingOnARangedEnemyLine() {
        return rangedEnemyThreatensTile(playerTileColumn(), playerTileRow());
    }

    /** Whether any awake, in-range ranged enemy holds a clear cardinal line on the given tile. */
    public boolean rangedEnemyThreatensTile(int tileColumn, int tileRow) {
        for (Enemy enemy : enemies()) {
            if (!enemy.isAlive() || !enemy.type.isRanged() || !enemy.isAlerted()) continue;
            if (enemy.tileColumn != tileColumn && enemy.tileRow != tileRow) continue;
            int distance = GameMath.chebyshevDistanceTiles(tileColumn, tileRow,
                                                           enemy.tileColumn, enemy.tileRow);
            if (distance > enemy.type.attackRangeTiles()) continue;
            if (!GameMath.tileLineOfSightClear(tileColumn, tileRow, enemy.tileColumn, enemy.tileRow,
                    (column, row) -> Level.isWall(level.getCell(column, row))
                                     || Level.isPropSolid(level.getCell(column, row))
                                     || (doorManager != null && doorManager.blocksSight(column, row)))) {
                continue;
            }
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Floor structure the policy is allowed to plan against
    // -------------------------------------------------------------------------

    /** Shortest-path helper for this floor (see {@link SimNavigator}). */
    SimNavigator navigator() { return world.navigator(); }

    /** The floor's stairs-down tile as {column, row}, or null when it has none. */
    int[] exitTile() { return world.exitTile(); }

    /**
     * The nearest keycard pickup still lying on the floor as {column, row}, or null when there is
     * none. What a player looks for when the way down is behind a locked door.
     */
    int[] nearestKeycardTile() {
        return nearestTileMatching(Level::isKeycardPickup);
    }

    /**
     * The nearest ammo / medical / armour pickup still on the floor as {column, row}, or null. A
     * floor's supplies are half its economy — a policy that walks past them is not playing the game
     * the resource model describes.
     */
    int[] nearestSupplyTile() {
        int[] nearest = nearestUsableTileMatching(cell -> Level.isAmmoPickup(cell)
                                                       || Level.isMedicalPickup(cell)
                                                       || Level.isArmourPickup(cell));
        if (nearest == null) return null;
        // Supplies are worth a DETOUR, not an expedition: a pickup on the far side of the floor costs
        // more turns (and more woken enemies) than it pays back, and chasing one across the map is
        // how a scripted player ends up walking past the stairs forever.
        int distance = GameMath.chebyshevDistanceTiles(playerTileColumn(), playerTileRow(),
                                                       nearest[0], nearest[1]);
        return distance <= BalanceConfig.SIM_SUPPLY_DETOUR_RANGE_TILES ? nearest : null;
    }

    /** Predicate over a tile SYMBOL — avoids the int/char widening a generic Predicate would force. */
    private interface CellTest {
        boolean matches(char cell);
    }

    /** As {@link #nearestTileMatching}, skipping pickups already proven untakeable this floor. */
    private int[] nearestUsableTileMatching(CellTest cellTest) {
        int[] nearest    = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int tileRow = 0; tileRow < level.getHeight(); tileRow++) {
            for (int tileColumn = 0; tileColumn < level.getWidth(); tileColumn++) {
                if (!cellTest.matches(level.getCell(tileColumn, tileRow))) continue;
                if (world.isSupplyTileExhausted(tileColumn, tileRow)) continue;
                int distance = GameMath.chebyshevDistanceTiles(playerTileColumn(), playerTileRow(),
                                                               tileColumn, tileRow);
                if (distance > 0 && distance < bestDistance) {
                    bestDistance = distance;
                    nearest      = new int[]{tileColumn, tileRow};
                }
            }
        }
        return nearest;
    }

    /** Scans the grid for the closest tile whose symbol matches, by Chebyshev distance. */
    private int[] nearestTileMatching(CellTest cellTest) {
        int[] nearest    = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int tileRow = 0; tileRow < level.getHeight(); tileRow++) {
            for (int tileColumn = 0; tileColumn < level.getWidth(); tileColumn++) {
                if (!cellTest.matches(level.getCell(tileColumn, tileRow))) continue;
                int distance = GameMath.chebyshevDistanceTiles(playerTileColumn(), playerTileRow(),
                                                               tileColumn, tileRow);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    nearest      = new int[]{tileColumn, tileRow};
                }
            }
        }
        return nearest;
    }

    /** The marine's weapon slots — read when deciding whether to switch guns. */
    public ge.tbegvadze.toon3d.entity.Loadout loadout() { return world.loadout(); }

    /**
     * True when nothing solid stands between the marine and a tile — the same supercover walk the
     * enemy AI uses for its own line of sight ({@link GameMath#tileLineOfSightClear}), with closed
     * doors counted as sight-blockers.
     */
    public boolean hasClearShotAt(int targetColumn, int targetRow) {
        return GameMath.tileLineOfSightClear(playerTileColumn(), playerTileRow(),
                targetColumn, targetRow,
                (column, row) -> Level.isWall(level.getCell(column, row))
                                 || Level.isPropSolid(level.getCell(column, row))
                                 || (doorManager != null && doorManager.blocksSight(column, row)));
    }

    /** The living enemy directly in the marine's facing line within {@code maxTiles}, or null. */
    public Enemy enemyInFacingLine(int maxTiles) {
        int stepColumn = facingStepColumn();
        int stepRow    = facingStepRow();
        int column     = playerTileColumn();
        int row        = playerTileRow();
        for (int step = 1; step <= maxTiles; step++) {
            column += stepColumn;
            row    += stepRow;
            if (Level.isWall(level.getCell(column, row))) return null;
            if (doorManager != null && doorManager.blocksSight(column, row)) return null;
            for (Enemy enemy : enemies()) {
                if (enemy.isAlive() && enemy.tileColumn == column && enemy.tileRow == row) return enemy;
            }
        }
        return null;
    }
}
