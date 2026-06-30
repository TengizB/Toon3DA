package ge.tbegvadze.toon3d.hazard;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.status.StatusEffectController;
import ge.tbegvadze.toon3d.status.StatusHost;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.EffectConstants;

import java.util.List;
import java.util.Random;

/**
 * Terrain-hazard simulation (balance idea 4, Pillar 3 — the two-sided chain-reaction engine).
 *
 * Owns the per-tile FIRE / TOXIC hazard state and advances it once per world turn. A hazard tile
 * ticks the existing BURNING (fire) or POISONED (toxic) status effect onto ANY host standing on
 * it — the player AND enemies — so a hazard can win the fight for you OR kill you if you
 * misposition. That two-sidedness is the whole tactic (idea 4 balance note).
 *
 * Behaviour:
 *   - FIRE spreads each turn (chance) to one eligible cardinal-neighbour floor/stain tile, and
 *     chain-detonates explosive barrels it reaches (fire → explosive). A detonating barrel can
 *     ignite fire back onto its neighbours (explosive → fire) via {@link #igniteFire}.
 *   - TOXIC is a static area-denial pool that creeps only rarely; a Plague Hulk leaves one where
 *     it dies (Pillar 2 area-denial verb).
 *   - Every hazard tile expires after its lifetime, restoring the floor tile it overlaid.
 *
 * Hazard state lives in this class (NOT the level grid) — the level cell is only stamped with the
 * 'i'/'q' decal char so the existing PropRenderer dynamic-prop path can draw it; the stamp auto-
 * culls when restored. The simulation is the source of truth for damage, spread and lifetime.
 *
 * Zero allocation per turn: the per-turn snapshot and pending-ignition buffers are pre-allocated.
 * Holds no GPU resources, so it needs no dispose().
 */
public final class HazardManager {

    /** Stamped onto the level grid for a fire tile so PropRenderer can draw it. */
    private static final char FIRE_CHAR  = 'i';
    /** Stamped onto the level grid for a toxic tile so PropRenderer can draw it. */
    private static final char TOXIC_CHAR = 'q';

    private static final byte NONE  = 0;
    private static final byte FIRE  = 1;
    private static final byte TOXIC = 2;

    // Cardinal neighbour offsets: East, West, North, South.
    private static final int[] STEP_COLUMNS = {  1, -1,  0,  0 };
    private static final int[] STEP_ROWS    = {  0,  0,  1, -1 };

    /** Notified whenever a hazard tile appears so a renderer can register its decal. */
    public interface HazardVisualListener {
        void onHazardTilePlaced(int tileColumn, int tileRow, char hazardChar);
    }

    private final Level                  level;
    private final EnemyManager           enemyManager;
    private final StatusEffectController statusEffectController;
    private final int                    gridWidth;
    private final int                    gridHeight;
    private final Random                 hazardRandom;

    private final byte[][] hazardType;
    private final short[][] remainingTurns;
    private final char[][] savedUnderChar;
    // True once a tile's fire has burned out. Spread can never re-ignite a burned-out tile, which is
    // what stops a dense fire from sustaining itself forever (cleared tiles being relit by their still-
    // burning neighbours). A tile becomes eligible again only via a DELIBERATE ignition (barrel blast,
    // enemy death, level seed) — ignite() clears the flag when it places fresh fire there.
    private final boolean[][] fireBurnedOut;

    // Per-turn snapshot of active tiles (rebuilt each tick; spread/decay read this, never the live
    // arrays, so chain ignitions added mid-tick are not re-processed in the same turn).
    private final int[]  snapshotColumns;
    private final int[]  snapshotRows;
    private final byte[] snapshotTypes;
    private int          snapshotCount;

    // Fire spreads onto explosive barrels by detonating them; the chain may ignite more fire.
    private ExplosiveBarrelManager explosiveBarrelManager;
    private HazardVisualListener   visualListener;

    public HazardManager(Level level, EnemyManager enemyManager,
                         StatusEffectController statusEffectController) {
        this.level                 = level;
        this.enemyManager          = enemyManager;
        this.statusEffectController = statusEffectController;
        this.gridWidth             = level.getWidth();
        this.gridHeight            = level.getHeight();
        this.hazardRandom          = new Random();
        this.hazardType            = new byte[gridHeight][gridWidth];
        this.remainingTurns        = new short[gridHeight][gridWidth];
        this.savedUnderChar        = new char[gridHeight][gridWidth];
        this.fireBurnedOut         = new boolean[gridHeight][gridWidth];
        int capacity               = Math.max(1, gridWidth * gridHeight);
        this.snapshotColumns       = new int[capacity];
        this.snapshotRows          = new int[capacity];
        this.snapshotTypes         = new byte[capacity];
        seedFromLevel();
    }

    /** Wires the barrel manager so fire can chain-detonate barrels it spreads into. */
    public void setExplosiveBarrelManager(ExplosiveBarrelManager manager) {
        this.explosiveBarrelManager = manager;
    }

    /** Wires the renderer hook so each new hazard tile registers its billboard decal. */
    public void setHazardVisualListener(HazardVisualListener listener) {
        this.visualListener = listener;
    }

    // -------------------------------------------------------------------------
    // Public ignition API — used by enemy deaths, barrel chains and level seeding
    // -------------------------------------------------------------------------

    /** Ignites (or refreshes) a fire tile, if the tile is an eligible floor/stain cell. */
    public void igniteFire(int tileColumn, int tileRow) {
        ignite(tileColumn, tileRow, FIRE, BalanceConfig.HAZARD_FIRE_LIFETIME_TURNS);
    }

    /** Spawns (or refreshes) a toxic pool, if the tile is an eligible floor/stain cell. */
    public void igniteToxic(int tileColumn, int tileRow) {
        ignite(tileColumn, tileRow, TOXIC, BalanceConfig.HAZARD_TOXIC_LIFETIME_TURNS);
    }

    /**
     * Spawns a toxic cloud centred on a tile (a Plague Hulk's death cloud, Pillar 2).
     * The centre plus every spreadable cardinal tile within {@code radius} becomes toxic.
     */
    public void spawnToxicCloud(int centerColumn, int centerRow, int radius) {
        igniteToxic(centerColumn, centerRow);
        for (int offset = 1; offset <= radius; offset++) {
            igniteToxic(centerColumn + offset, centerRow);
            igniteToxic(centerColumn - offset, centerRow);
            igniteToxic(centerColumn, centerRow + offset);
            igniteToxic(centerColumn, centerRow - offset);
        }
    }

    /**
     * Ignites fire on each eligible non-wall neighbour of a detonating barrel (explosive → fire
     * chain). Chance-gated per neighbour by BalanceConfig.HAZARD_EXPLOSION_IGNITE_CHANCE.
     */
    public void igniteFireFromExplosion(int tileColumn, int tileRow) {
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + STEP_COLUMNS[direction];
            int neighborRow    = tileRow    + STEP_ROWS[direction];
            if (hazardRandom.nextFloat() < BalanceConfig.HAZARD_EXPLOSION_IGNITE_CHANCE) {
                igniteFire(neighborColumn, neighborRow);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Per-turn simulation
    // -------------------------------------------------------------------------

    /** Advances every hazard tile one world turn: damage, spread/chain, then decay. */
    public void tick(int playerColumn, int playerRow, Player player) {
        rebuildSnapshot();
        damagePhase(playerColumn, playerRow, player);
        spreadPhase();
        decayPhase();
    }

    private void rebuildSnapshot() {
        snapshotCount = 0;
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                byte type = hazardType[tileRow][tileColumn];
                if (type == NONE) continue;
                snapshotColumns[snapshotCount] = tileColumn;
                snapshotRows[snapshotCount]    = tileRow;
                snapshotTypes[snapshotCount]   = type;
                snapshotCount++;
            }
        }
    }

    private void damagePhase(int playerColumn, int playerRow, Player player) {
        List<Enemy> enemies = enemyManager.getEnemies();
        for (int index = 0; index < snapshotCount; index++) {
            int  tileColumn = snapshotColumns[index];
            int  tileRow    = snapshotRows[index];
            byte type       = snapshotTypes[index];

            if (player != null && playerColumn == tileColumn && playerRow == tileRow) {
                applyHazardStatus(player, type);
            }
            for (int enemyIndex = 0; enemyIndex < enemies.size(); enemyIndex++) {
                Enemy enemy = enemies.get(enemyIndex);
                if (enemy.isAlive() && enemy.tileColumn == tileColumn && enemy.tileRow == tileRow) {
                    applyHazardStatus(enemy, type);
                }
            }
        }
    }

    private void applyHazardStatus(StatusHost host, byte type) {
        if (statusEffectController == null) return;
        if (type == FIRE) {
            statusEffectController.apply(host, StatusType.BURNING,
                    BalanceConfig.HAZARD_FIRE_BURN_TURNS, EffectConstants.BURN_DAMAGE_PER_TURN, null);
        } else if (type == TOXIC) {
            statusEffectController.apply(host, StatusType.POISONED,
                    BalanceConfig.HAZARD_TOXIC_POISON_TURNS, EffectConstants.POISON_DAMAGE_PER_STACK, null);
        }
    }

    private void spreadPhase() {
        for (int index = 0; index < snapshotCount; index++) {
            int  tileColumn = snapshotColumns[index];
            int  tileRow    = snapshotRows[index];
            byte type       = snapshotTypes[index];

            float spreadChance = (type == FIRE)
                    ? BalanceConfig.HAZARD_FIRE_SPREAD_CHANCE
                    : BalanceConfig.HAZARD_TOXIC_SPREAD_CHANCE;
            if (hazardRandom.nextFloat() >= spreadChance) continue;

            // Spread to ONE eligible neighbour, trying the four cardinal directions in a random
            // order (random start avoids eastward bias) and stopping at the first that takes.
            int startDirection = hazardRandom.nextInt(4);
            for (int attempt = 0; attempt < 4; attempt++) {
                int direction      = (startDirection + attempt) % 4;
                int neighborColumn = tileColumn + STEP_COLUMNS[direction];
                int neighborRow    = tileRow    + STEP_ROWS[direction];
                if (!inBounds(neighborColumn, neighborRow)) continue;

                char neighborCell = level.getCell(neighborColumn, neighborRow);
                if (type == FIRE && explosiveBarrelManager != null
                        && explosiveBarrelManager.isExplosiveBarrel(neighborColumn, neighborRow)) {
                    // Fire → explosive chain. Detonation may ignite fire back onto its neighbours.
                    explosiveBarrelManager.onExplosiveBarrelHit(neighborColumn, neighborRow);
                    break;
                }
                // Fire never spreads back onto a tile that has already burned out — that mutual
                // re-ignition between neighbours is what made a dense fire immortal. Toxic is unaffected.
                if (type == FIRE && fireBurnedOut[neighborRow][neighborColumn]) continue;
                if (hazardType[neighborRow][neighborColumn] == NONE
                        && Level.isHazardSpreadable(neighborCell)) {
                    ignite(neighborColumn, neighborRow, type,
                            type == FIRE ? BalanceConfig.HAZARD_FIRE_LIFETIME_TURNS
                                         : BalanceConfig.HAZARD_TOXIC_LIFETIME_TURNS);
                    break;
                }
            }
        }
    }

    private void decayPhase() {
        for (int index = 0; index < snapshotCount; index++) {
            int tileColumn = snapshotColumns[index];
            int tileRow    = snapshotRows[index];
            if (hazardType[tileRow][tileColumn] == NONE) continue; // already cleared this tick
            short turnsLeft = (short) (remainingTurns[tileRow][tileColumn] - 1);
            remainingTurns[tileRow][tileColumn] = turnsLeft;
            if (turnsLeft <= 0) {
                clear(tileColumn, tileRow);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Tile state helpers
    // -------------------------------------------------------------------------

    private void ignite(int tileColumn, int tileRow, byte type, int lifetimeTurns) {
        if (!inBounds(tileColumn, tileRow)) return;
        char cell = level.getCell(tileColumn, tileRow);
        byte existing = hazardType[tileRow][tileColumn];
        if (existing == type) {
            // Refresh the lifetime of an existing same-type tile (standing fire keeps burning).
            remainingTurns[tileRow][tileColumn] = (short) lifetimeTurns;
            return;
        }
        if (existing != NONE) return;                 // never overwrite a different hazard type
        if (!Level.isHazardSpreadable(cell)) return;  // only overlay plain floor/stain tiles

        hazardType[tileRow][tileColumn]     = type;
        remainingTurns[tileRow][tileColumn] = (short) lifetimeTurns;
        savedUnderChar[tileRow][tileColumn] = cell;
        // Fresh fire clears the burned-out mark so this tile burns normally again. Spread is gated
        // upstream (spreadPhase), so reaching here for fire means a deliberate ignition was allowed.
        if (type == FIRE) fireBurnedOut[tileRow][tileColumn] = false;
        char hazardChar = (type == FIRE) ? FIRE_CHAR : TOXIC_CHAR;
        level.setCell(tileColumn, tileRow, hazardChar);
        if (visualListener != null) {
            visualListener.onHazardTilePlaced(tileColumn, tileRow, hazardChar);
        }
    }

    private void clear(int tileColumn, int tileRow) {
        byte type = hazardType[tileRow][tileColumn];
        if (type == NONE) return;
        hazardType[tileRow][tileColumn]     = NONE;
        remainingTurns[tileRow][tileColumn] = 0;
        // A fire that burns out can't be relit by spread (only by a deliberate ignition). This is the
        // hard stop that makes fire die within a few turns instead of self-sustaining across the level.
        if (type == FIRE) fireBurnedOut[tileRow][tileColumn] = true;
        char hazardChar = (type == FIRE) ? FIRE_CHAR : TOXIC_CHAR;
        // Only restore the saved floor tile if the cell still shows our decal; if something else
        // (a corpse/ammo drop) overwrote it, leave that in place.
        if (level.getCell(tileColumn, tileRow) == hazardChar) {
            level.setCell(tileColumn, tileRow, savedUnderChar[tileRow][tileColumn]);
        }
    }

    /** Registers any hand-placed 'i'/'q' tiles already in the level grid as live hazards. */
    private void seedFromLevel() {
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                char cell = level.getCell(tileColumn, tileRow);
                if (Level.isHazardFire(cell)) {
                    hazardType[tileRow][tileColumn]     = FIRE;
                    remainingTurns[tileRow][tileColumn] = (short) BalanceConfig.HAZARD_FIRE_LIFETIME_TURNS;
                    savedUnderChar[tileRow][tileColumn] = ' ';
                } else if (Level.isHazardToxic(cell)) {
                    hazardType[tileRow][tileColumn]     = TOXIC;
                    remainingTurns[tileRow][tileColumn] = (short) BalanceConfig.HAZARD_TOXIC_LIFETIME_TURNS;
                    savedUnderChar[tileRow][tileColumn] = ' ';
                }
            }
        }
    }

    private boolean inBounds(int tileColumn, int tileRow) {
        return tileColumn >= 0 && tileColumn < gridWidth && tileRow >= 0 && tileRow < gridHeight;
    }
}
