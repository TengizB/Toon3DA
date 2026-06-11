package ge.tbegvadze.toon3d.hazard;

import com.badlogic.gdx.math.MathUtils;
import ge.tbegvadze.toon3d.entity.BarrelHitTarget;
import ge.tbegvadze.toon3d.entity.EnemyHitTarget;
import ge.tbegvadze.toon3d.entity.ImpactEventListener;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;

/**
 * Handles explosive barrel ('E') detonation.
 *
 * When a weapon hits an 'E' tile, onExplosiveBarrelHit() is called. The barrel
 * is removed from the level grid, then EXPLOSION_DAMAGE is applied in a cross
 * pattern (center + 4 cardinal neighbours) to the player and any enemies on
 * those tiles. Adjacent 'E' barrels are queued for chain detonation and
 * processed in BFS order — one detonation can cascade through a whole row.
 *
 * Zero allocations per detonation: the chain queue is a pre-allocated int[].
 */
public final class ExplosiveBarrelManager implements BarrelHitTarget {

    // Cardinal cross pattern: centre, east, west, north, south
    private static final int[] BLAST_STEP_COLUMNS = { 0,  1, -1,  0,  0 };
    private static final int[] BLAST_STEP_ROWS    = { 0,  0,  0,  1, -1 };

    private final Level               level;
    private final EnemyHitTarget      enemyHitTarget;
    private final Player              player;
    private       ImpactEventListener impactEventListener = null;

    // Pre-allocated BFS chain queue — never reallocated after construction.
    private final int[] chainColumns;
    private final int[] chainRows;
    private int         chainCount;

    public ExplosiveBarrelManager(Level level, EnemyHitTarget enemyHitTarget, Player player) {
        this.level          = level;
        this.enemyHitTarget = enemyHitTarget;
        this.player         = player;
        this.chainColumns   = new int[EffectConstants.EXPLOSION_CHAIN_MAX];
        this.chainRows      = new int[EffectConstants.EXPLOSION_CHAIN_MAX];
    }

    public void setImpactEventListener(ImpactEventListener listener) {
        this.impactEventListener = listener;
    }

    @Override
    public boolean isExplosiveBarrel(int tileColumn, int tileRow) {
        char cell = level.getCell(tileColumn, tileRow);
        return cell == 'E' || cell == 'g';
    }

    @Override
    public void onExplosiveBarrelHit(int tileColumn, int tileRow) {
        chainCount = 0;
        detonateBarrel(tileColumn, tileRow);
        // BFS: chainCount grows as detonateBarrel() enqueues adjacent barrels.
        for (int queueHead = 0; queueHead < chainCount; queueHead++) {
            detonateBarrel(chainColumns[queueHead], chainRows[queueHead]);
        }
    }

    private void detonateBarrel(int tileColumn, int tileRow) {
        char barrelCell = level.getCell(tileColumn, tileRow);
        if (barrelCell != 'E' && barrelCell != 'g') return;
        // Remove the barrel first — prevents re-queuing the same tile as a chain target.
        level.setCell(tileColumn, tileRow, ' ');

        // Trigger explosion visual effect at the barrel's world-space centre.
        if (impactEventListener != null) {
            float worldX = tileColumn * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
            float worldY = tileRow    * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
            impactEventListener.onEnemyKilled(worldX, worldY, 0.65f, EffectConstants.EXPLOSION_DAMAGE);
        }

        int playerTileColumn = MathUtils.floor(player.positionX / Constants.CELL_SIZE);
        int playerTileRow    = MathUtils.floor(player.positionY / Constants.CELL_SIZE);

        for (int blastIndex = 0; blastIndex < BLAST_STEP_COLUMNS.length; blastIndex++) {
            int  blastColumn = tileColumn + BLAST_STEP_COLUMNS[blastIndex];
            int  blastRow    = tileRow    + BLAST_STEP_ROWS[blastIndex];
            char blastCell   = level.getCell(blastColumn, blastRow);

            if (Level.isWall(blastCell)) continue;

            if (blastColumn == playerTileColumn && blastRow == playerTileRow) {
                player.applyDamage(EffectConstants.EXPLOSION_DAMAGE);
            }

            if (enemyHitTarget != null) {
                Object enemy = enemyHitTarget.enemyAt(blastColumn, blastRow);
                if (enemy != null) {
                    enemyHitTarget.applyDamageTo(enemy, EffectConstants.EXPLOSION_DAMAGE);
                }
            }

            // Queue adjacent explosive/toxic barrels for chain detonation.
            // blastCell was read before setCell above, so barrel tiles still read correctly here.
            if ((blastCell == 'E' || blastCell == 'g') && chainCount < EffectConstants.EXPLOSION_CHAIN_MAX) {
                boolean alreadyQueued = false;
                for (int checkIndex = 0; checkIndex < chainCount; checkIndex++) {
                    if (chainColumns[checkIndex] == blastColumn && chainRows[checkIndex] == blastRow) {
                        alreadyQueued = true;
                        break;
                    }
                }
                if (!alreadyQueued) {
                    chainColumns[chainCount] = blastColumn;
                    chainRows[chainCount]    = blastRow;
                    chainCount++;
                }
            }
        }
    }
}
