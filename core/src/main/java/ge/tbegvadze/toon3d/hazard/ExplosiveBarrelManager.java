package ge.tbegvadze.toon3d.hazard;

import com.badlogic.gdx.math.MathUtils;
import ge.tbegvadze.toon3d.entity.BarrelHitTarget;
import ge.tbegvadze.toon3d.entity.DamageClass;
import ge.tbegvadze.toon3d.entity.EnemyHitTarget;
import ge.tbegvadze.toon3d.entity.ImpactEventListener;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * Handles explosive barrel ('E') detonation.
 *
 * When a weapon hits an 'E' tile, onExplosiveBarrelHit() is called. The barrel
 * is removed from the level grid, then blast damage is applied in a cross
 * pattern (centre + 4 cardinal neighbours) to the player and any enemies on
 * those tiles. Damage is a FRACTION (balance-overhaul order 4, H13): enemies take
 * HAZARD_BARREL_CENTRE_FRACTION / _RING_FRACTION of the reference soldier's HP at
 * this floor's depth, the player takes HAZARD_BARREL_PLAYER_CENTRE_FRACTION /
 * _RING_FRACTION of their own max HP. Adjacent 'E' barrels are queued for chain detonation and
 * processed in BFS order — one detonation can cascade through a whole row.
 *
 * Zero allocations per detonation: the chain queue is a pre-allocated int[].
 */
public final class ExplosiveBarrelManager implements BarrelHitTarget {

    // Cardinal cross pattern: centre, east, west, north, south
    private static final int[] BLAST_STEP_COLUMNS = { 0,  1, -1,  0,  0 };
    private static final int[] BLAST_STEP_ROWS    = { 0,  0,  0,  1, -1 };

    /** Notified when a barrel detonates so the hazard system can chain fire onto its neighbours. */
    public interface BarrelDetonationListener {
        void onBarrelDetonated(int tileColumn, int tileRow);
    }

    private final Level               level;
    private final EnemyHitTarget      enemyHitTarget;

    /** Nominal blast damage this floor's barrels dealt to enemies (observation only — the simulator's HEAVY REPORT). */
    private int enemyBlastDamageDealt = 0;
    private final Player              player;
    /** Enemy-side blast damage on the barrel's own tile / on each orthogonal neighbour, fixed for the floor's depth. */
    private final int                 enemyCentreDamage;
    private final int                 enemyRingDamage;
    private       ImpactEventListener impactEventListener = null;
    private       BarrelDetonationListener detonationListener = null;

    // Pre-allocated BFS chain queue — never reallocated after construction.
    private final int[] chainColumns;
    private final int[] chainRows;
    private int         chainCount;

    /**
     * @param depth the floor's depth (the same one the floor's EnemyManager scales its enemies with): the
     *              reference soldier's HP, and so the enemy blast damage, follows the enemy HP ladder.
     */
    public ExplosiveBarrelManager(Level level, EnemyHitTarget enemyHitTarget, Player player, int depth) {
        this.level          = level;
        // Unrounded on purpose: the ring (0.6 x soldier) must land exactly on a chaff's own rounded HP, so the
        // reference stays a float and only the final damage is rounded (BalanceSchema R-HAZARD audits it).
        float referenceSoldierMaxHealth = GameMath.enemyHealthAtDepth(
                BalanceConfig.HAZARD_BARREL_REFERENCE_SOLDIER_BASE_HEALTH, depth);
        this.enemyCentreDamage = GameMath.barrelBlastDamage(
                BalanceConfig.HAZARD_BARREL_CENTRE_FRACTION, referenceSoldierMaxHealth);
        this.enemyRingDamage   = GameMath.barrelBlastDamage(
                BalanceConfig.HAZARD_BARREL_RING_FRACTION, referenceSoldierMaxHealth);
        this.enemyHitTarget = enemyHitTarget;
        this.player         = player;
        this.chainColumns   = new int[EffectConstants.EXPLOSION_CHAIN_MAX];
        this.chainRows      = new int[EffectConstants.EXPLOSION_CHAIN_MAX];
    }

    public void setImpactEventListener(ImpactEventListener listener) {
        this.impactEventListener = listener;
    }

    /** Wires the hazard system so a detonation can chain fire onto the barrel's neighbours. */
    public void setDetonationListener(BarrelDetonationListener listener) {
        this.detonationListener = listener;
    }

    /** Nominal blast damage this floor's barrels dealt to enemies (observation only). */
    public int getEnemyBlastDamageDealt() {
        return enemyBlastDamageDealt;
    }

    @Override
    public boolean isExplosiveBarrel(int tileColumn, int tileRow) {
        char cell = level.getCell(tileColumn, tileRow);
        return cell == 'E' || cell == 'g';
    }

    /**
     * True while the detonation being resolved was set off by the PLAYER (balance-overhaul order 5): a weapon
     * activation is armed on the hit target (a shot, a grenade, or a chain from one). A barrel lit by spreading
     * fire or an enemy is not the player's. Read once per detonation chain.
     */
    private boolean detonatedByPlayer = false;

    @Override
    public void onExplosiveBarrelHit(int tileColumn, int tileRow) {
        chainCount = 0;
        detonatedByPlayer = enemyHitTarget != null && enemyHitTarget.getActivationDamageClass() != null
                && !enemyHitTarget.isActivationHazardBlast();
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

        // Chain fire onto the barrel's neighbours (explosive → fire) if a hazard system is wired.
        if (detonationListener != null) {
            detonationListener.onBarrelDetonated(tileColumn, tileRow);
        }

        // Trigger explosion visual effect at the barrel's world-space centre.
        if (impactEventListener != null) {
            float worldX = tileColumn * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
            float worldY = tileRow    * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
            impactEventListener.onEnemyKilled(worldX, worldY, 0.65f, enemyRingDamage);
        }

        int playerTileColumn = MathUtils.floor(player.positionX / Constants.CELL_SIZE);
        int playerTileRow    = MathUtils.floor(player.positionY / Constants.CELL_SIZE);

        for (int blastIndex = 0; blastIndex < BLAST_STEP_COLUMNS.length; blastIndex++) {
            int  blastColumn = tileColumn + BLAST_STEP_COLUMNS[blastIndex];
            int  blastRow    = tileRow    + BLAST_STEP_ROWS[blastIndex];
            char blastCell   = level.getCell(blastColumn, blastRow);

            if (Level.isWall(blastCell)) continue;

            boolean centreTile = blastIndex == 0;
            if (blastColumn == playerTileColumn && blastRow == playerTileRow) {
                int playerBlast = GameMath.barrelBlastDamage(centreTile
                                ? BalanceConfig.HAZARD_BARREL_PLAYER_CENTRE_FRACTION
                                : BalanceConfig.HAZARD_BARREL_PLAYER_RING_FRACTION,
                        player.getMaxHealth());
                // A barrel the player set off is the player's OWN explosion (Blast Shield / DEMOLITIONIST —
                // balance-overhaul order 5); any other barrel hurts at full.
                if (detonatedByPlayer) player.applyOwnExplosionDamage(playerBlast);
                else                   player.applyDamage(playerBlast);
            }

            if (enemyHitTarget != null) {
                Object enemy = enemyHitTarget.enemyAt(blastColumn, blastRow);
                if (enemy != null) {
                    // AS6 (balance-overhaul order 3): a barrel blast is EXPLOSIVE against traits. A barrel
                    // can detonate mid-activation (a shot into it), so restore whatever was armed.
                    DamageClass previousClass = enemyHitTarget.getActivationDamageClass();
                    boolean previousFloorNeutral = enemyHitTarget.isActivationMatchupFloorNeutral();
                    boolean previousHazardBlast  = enemyHitTarget.isActivationHazardBlast();
                    enemyHitTarget.setActivationDamageClass(DamageClass.EXPLOSIVE);
                    enemyHitTarget.setActivationMatchupFloorNeutral(false);
                    // A barrel is a HAZARD blast: no weapon build terms; its own Sapper / DEMOLITIONIST
                    // multiplier (balance-overhaul order 5) scales it instead.
                    enemyHitTarget.setActivationHazardBlast(true);
                    int blastDamage = Math.round((centreTile ? enemyCentreDamage : enemyRingDamage)
                            * player.getBuildStats().barrelDamageMultiplier(detonatedByPlayer));
                    enemyHitTarget.applyDamageTo(enemy, blastDamage);
                    enemyBlastDamageDealt += blastDamage;
                    enemyHitTarget.setActivationHazardBlast(previousHazardBlast);
                    enemyHitTarget.setActivationDamageClass(previousClass);
                    enemyHitTarget.setActivationMatchupFloorNeutral(previousFloorNeutral);
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
