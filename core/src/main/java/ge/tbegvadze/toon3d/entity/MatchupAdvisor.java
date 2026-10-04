package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyTrait;
import ge.tbegvadze.toon3d.level.Level;

import java.util.List;

/**
 * THE SWITCH HINT'S BRAIN (balance-overhaul order 3, C4). Headless and shared by the played game
 * ({@code World}) and the balance simulator ({@code SimWorld}) so both read the same answer to
 * "is there a better gun in the bag for what I am facing?".
 *
 * <p>Two pure queries, neither of which allocates:
 * <ul>
 *   <li>{@link #findTarget} - the nearest AWAKE living enemy in the player's facing lane within the
 *       equipped weapon's effective range, stopping at the first wall, solid prop or closed door.</li>
 *   <li>{@link #hintWeapon} - the carried weapon the SWITCH button should jump to, or null. A hint
 *       exists only when the equipped weapon is RESISTED by the target's trait and a carried ranged
 *       weapon that still has ammo (clip or reserve) is EFFECTIVE against it and can reach it.
 *       Highest multiplier wins; a tie goes to the weapon with more total ammo, then to the lower
 *       slot (the stable order).</li>
 * </ul>
 * The melee slot is never offered: it is reached by the ordinary cycle, and a hint that walks the
 * player into the target is not the "better answer in your bag" the hint promises.
 */
public final class MatchupAdvisor {

    private MatchupAdvisor() {}

    /**
     * The nearest awake living enemy straight ahead of (playerTileColumn, playerTileRow) along
     * (stepColumn, stepRow) within {@code range} tiles, or null. Sleeping (DORMANT) enemies are
     * ignored: a hint about something that has not noticed you is noise.
     */
    public static Enemy findTarget(List<Enemy> enemies, Level level, DoorManager doorManager,
                                   int playerTileColumn, int playerTileRow,
                                   int stepColumn, int stepRow, int range) {
        if (enemies == null || level == null || range <= 0) return null;
        if (stepColumn == 0 && stepRow == 0) return null;
        int column = playerTileColumn;
        int row    = playerTileRow;
        for (int step = 1; step <= range; step++) {
            column += stepColumn;
            row    += stepRow;
            if (isBlocked(level, doorManager, column, row)) return null;
            for (int index = 0; index < enemies.size(); index++) {
                Enemy enemy = enemies.get(index);
                if (enemy.isAlive() && enemy.isAlerted()
                        && enemy.tileColumn == column && enemy.tileRow == row) {
                    return enemy;
                }
            }
        }
        return null;
    }

    /**
     * The weapon the SWITCH button should jump to against {@code target}, or null for "no hint".
     * {@code distanceTiles} is the target's lane distance; a candidate must reach that far.
     */
    public static Weapon hintWeapon(Weapon equipped, Loadout loadout, EnemyTrait trait, int distanceTiles) {
        if (equipped == null || loadout == null || trait == null) return null;
        MatchupTable table = MatchupCatalog.shared();
        if (table.classify(equipped.damageClass(), trait) != MatchupOutcome.RESISTED) return null;

        Weapon bestWeapon     = null;
        float  bestMultiplier = 0f;
        int    bestAmmo       = -1;
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            Weapon candidate = loadout.getSlot(slotIndex);
            if (candidate == null || candidate == equipped) continue;
            if (loadout.isSlotLocked(slotIndex)) continue;
            if (table.classify(candidate.damageClass(), trait) != MatchupOutcome.EFFECTIVE) continue;
            if (candidate.getEffectiveRange() < distanceTiles) continue;
            int totalAmmo = totalAmmoOf(candidate);
            if (totalAmmo <= 0) continue;
            float multiplier = table.multiplier(candidate.damageClass(), trait);
            if (bestWeapon == null || multiplier > bestMultiplier
                    || (multiplier == bestMultiplier && totalAmmo > bestAmmo)) {
                bestWeapon     = candidate;
                bestMultiplier = multiplier;
                bestAmmo       = totalAmmo;
            }
        }
        return bestWeapon;
    }

    /** Rounds in the clip plus the reserve; an untracked reserve (-1) counts as none, an infinite-ammo weapon as plenty. */
    private static int totalAmmoOf(Weapon weapon) {
        if (weapon.getAmmoType() == null) return Integer.MAX_VALUE;
        return weapon.getShotsInClip() + Math.max(0, weapon.getReserveAmmo());
    }

    private static boolean isBlocked(Level level, DoorManager doorManager, int column, int row) {
        if (doorManager == null) {
            char cell = level.getCell(column, row);
            return Level.isWall(cell) || Level.isPropSolid(cell);
        }
        return level.isBlockedAt(column, row, doorManager);
    }
}
