package ge.tbegvadze.toon3d.render.enemyvisual;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;
import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.enemy.EnemyVisualEvent;
import ge.tbegvadze.toon3d.enemy.EnemyVisualListener;
import ge.tbegvadze.toon3d.util.EffectConstants;

import java.util.List;

/**
 * THE ANIMATED ENEMY LAYER of one floor (generated-enemy-art order 2): the {@link EnemyVisualListener}
 * the simulation reports moments to, plus everything those moments drive — the per-floor
 * {@link EnemyAtlasCache}, a pool of {@link EnemyAnimator}s keyed by enemy id, and the
 * {@link DyingEnemyPool}. {@code World} builds one per floor beside the {@code EnemyRenderer}, advances it
 * from the update path (so its clock freezes whenever the world does) and disposes it with the floor.
 * {@code EnemyRenderer} only asks it which frame to draw.
 *
 * <p>Rule R2: the layer reads enemies and receives moments; it never writes to an enemy, the manager or
 * the world. Rule R6: after construction nothing here allocates — the animator pool and the id map are
 * pre-sized, and every per-frame loop is an index loop.
 */
public final class EnemyVisualLayer implements EnemyVisualListener, Disposable {

    private static final String LOG_TAG = "EnemyVisualLayer";

    private final EnemyManager             enemyManager;
    private final EnemyAtlasCache          atlasCache = new EnemyAtlasCache();
    private final EnemyAnimator[]          animators  = new EnemyAnimator[EffectConstants.ENEMY_VISUAL_ANIMATOR_POOL_CAPACITY];
    private final IntMap<EnemyAnimator>    animatorsById = new IntMap<>(EffectConstants.ENEMY_VISUAL_ANIMATOR_POOL_CAPACITY * 2);
    private final DyingEnemyPool           dyingPool  = new DyingEnemyPool();
    private int     updateStamp = 0;
    private boolean poolExhaustedLogged = false;

    /** Builds the layer and loads the creatures every type on the floor (and what they summon) needs (R5). */
    public EnemyVisualLayer(EnemyManager enemyManager) {
        this.enemyManager = enemyManager;
        for (int slotIndex = 0; slotIndex < animators.length; slotIndex++) animators[slotIndex] = new EnemyAnimator();
        List<Enemy> enemies = enemyManager.getEnemies();
        for (int enemyIndex = 0; enemyIndex < enemies.size(); enemyIndex++) {
            Enemy enemy = enemies.get(enemyIndex);
            atlasCache.ensureLoaded(enemy.type);
            atlasCache.ensureLoaded(enemy.type.summonType());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The seam
    // ---------------------------------------------------------------------------------------------

    @Override
    public void onEnemyVisualMoment(EnemyVisualEvent event) {
        EnemyAnimator animator = animatorFor(event.enemy, true);
        if (animator == null) return;   // a legacy type, or one whose creature failed to load (R5)
        EnemyAnimationRules.Row row = EnemyAnimationRules.rowFor(event.moment);
        row.kind.apply(this, animator, event, row);
    }

    /** DIED: the dying pool takes over at the body's last visual position and the animator is freed. */
    void beginDying(EnemyAnimator animator, Enemy enemy) {
        dyingPool.add(enemy.type, animator.visual, animator.renderCenterX(enemy), animator.renderCenterY(enemy),
                enemy.tileColumn, enemy.tileRow);
        release(animator);
    }

    // ---------------------------------------------------------------------------------------------
    // The clock
    // ---------------------------------------------------------------------------------------------

    /**
     * Advances every animator and dying instance. Call once per frame from the update path that also
     * advances the enemies' hit-flash timers, so it stops whenever the world is frozen.
     */
    public void update(float deltaTime) {
        updateStamp++;
        List<Enemy> enemies = enemyManager.getEnemies();
        for (int enemyIndex = 0; enemyIndex < enemies.size(); enemyIndex++) {
            Enemy enemy = enemies.get(enemyIndex);
            if (!enemy.isAlive()) continue;
            EnemyAnimator animator = animatorFor(enemy, true);
            if (animator == null) continue;
            animator.seenStamp = updateStamp;
            animator.advance(deltaTime, enemy);
        }
        // Sweep: an enemy that left the list without a DIED moment frees its animator.
        for (EnemyAnimator animator : animators) {
            if (animator.enemyId >= 0 && animator.seenStamp != updateStamp) release(animator);
        }
        dyingPool.advance(deltaTime);
    }

    // ---------------------------------------------------------------------------------------------
    // What the renderer reads
    // ---------------------------------------------------------------------------------------------

    /** The animator drawing {@code enemy}, or null when the enemy keeps the legacy sprite. */
    public EnemyAnimator animatorFor(Enemy enemy) {
        return animatorsById.get(enemy.getId());
    }

    public DyingEnemyPool getDyingPool() {
        return dyingPool;
    }

    public EnemyAtlasCache getAtlasCache() {
        return atlasCache;
    }

    // ---------------------------------------------------------------------------------------------

    private EnemyAnimator animatorFor(Enemy enemy, boolean acquireIfMissing) {
        EnemyAnimator animator = animatorsById.get(enemy.getId());
        if (animator != null || !acquireIfMissing) return animator;
        atlasCache.ensureLoaded(enemy.type);   // R5 safety net: a mid-floor arrival of an unforeseen type
        EnemyAtlasCache.CreatureVisual visual = atlasCache.visualFor(enemy.type);
        if (visual == null) return null;
        for (EnemyAnimator candidate : animators) {
            if (candidate.enemyId >= 0) continue;
            candidate.reset(enemy.getId(), visual);
            candidate.seenStamp = updateStamp;
            animatorsById.put(enemy.getId(), candidate);
            return candidate;
        }
        if (!poolExhaustedLogged) {
            poolExhaustedLogged = true;
            Gdx.app.error(LOG_TAG, "animator pool exhausted; extra enemies keep the legacy sprite");
        }
        return null;
    }

    private void release(EnemyAnimator animator) {
        animatorsById.remove(animator.enemyId);
        animator.release();
    }

    @Override
    public void dispose() {
        dyingPool.clear();
        animatorsById.clear();
        for (EnemyAnimator animator : animators) animator.release();
        atlasCache.dispose();
    }
}
