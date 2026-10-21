package ge.tbegvadze.toon3d.render.enemyvisual;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyState;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * One live enemy's visual state (generated-enemy-art order 2): the one-shot clip playing on top, the clip
 * a held state keeps on its last frame, the cosmetic tile slide, and the loop clock with the enemy's own
 * idle phase. Pooled by {@link EnemyVisualLayer}, reset on reuse; nothing here allocates.
 *
 * <p>Rule R2: it READS the enemy (state, tile) and RECEIVES moments; it never writes to the enemy.
 */
public final class EnemyAnimator {

    int   enemyId = -1;
    EnemyAtlasCache.CreatureVisual visual;
    /** Frame stamp of the last update that found this enemy alive in the manager (release sweep). */
    int   seenStamp;

    private float       loopSeconds;
    private EnemyClipId oneShotClip;
    private int         oneShotPriority;
    private float       oneShotSeconds;
    private EnemyClipId holdClip;
    private float       holdSeconds;

    private boolean sliding;
    private float   slideFromX, slideFromY, slideToX, slideToY;
    private float   slideSeconds, slideDurationSeconds;

    /** The creature this animator draws (its manifest and atlases). */
    public EnemyAtlasCache.CreatureVisual getVisual() {
        return visual;
    }

    void reset(int newEnemyId, EnemyAtlasCache.CreatureVisual newVisual) {
        enemyId  = newEnemyId;
        visual   = newVisual;
        float idleDuration = newVisual.manifest.clip(EnemyClipId.IDLE).durationSeconds;
        float phaseFraction = (newEnemyId * EffectConstants.ENEMY_VISUAL_IDLE_PHASE_SPREAD) % 1f;
        loopSeconds     = phaseFraction * idleDuration;
        oneShotClip     = null;
        oneShotPriority = EnemyAnimationRules.PRIORITY_IDLE;
        oneShotSeconds  = 0f;
        holdClip        = null;
        holdSeconds     = 0f;
        sliding         = false;
    }

    void release() {
        enemyId = -1;
        visual  = null;
    }

    /** Starts {@code clip} once unless a higher-priority one-shot is still playing (equal priority restarts). */
    void playOneShot(EnemyClipId clip, int priority) {
        if (visual.manifest.clip(clip) == null) return;
        if (oneShotClip != null && priority < oneShotPriority) return;
        oneShotClip     = clip;
        oneShotPriority = priority;
        oneShotSeconds  = 0f;
    }

    /**
     * A held clip starts from frame 0 — unless it is already the clip being held (a DEFENDING enemy that
     * braces again keeps its brace instead of replaying it). A lower one-shot in flight (ALERT) is cut.
     */
    void beginHold(EnemyClipId clip, int priority) {
        if (holdClip != clip) {
            holdClip    = clip;
            holdSeconds = 0f;
        }
        if (oneShotClip != null && oneShotPriority < priority) oneShotClip = null;
    }

    void beginSlide(int fromColumn, int fromRow, int toColumn, int toRow, float durationSeconds) {
        if (durationSeconds <= 0f) return;
        // Tile centres, exactly as Enemy.worldCenterX/Y place a settled enemy — except that a slide begun
        // while the last one is still running starts from where the body IS, so fast play never snaps it back.
        if (sliding) {
            float progress = slideProgress();
            float currentX = GameMath.lerp(slideFromX, slideToX, progress);
            float currentY = GameMath.lerp(slideFromY, slideToY, progress);
            slideFromX = currentX;
            slideFromY = currentY;
        } else {
            slideFromX = fromColumn * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
            slideFromY = fromRow    * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
        }
        slideToX   = toColumn   * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
        slideToY   = toRow      * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
        slideSeconds         = 0f;
        slideDurationSeconds = durationSeconds;
        sliding              = true;
    }

    /** Advances every clock by {@code deltaTime}; ends a finished one-shot and a finished slide. */
    void advance(float deltaTime, Enemy enemy) {
        loopSeconds += deltaTime;
        if (oneShotClip != null) {
            oneShotSeconds += deltaTime;
            if (visual.manifest.clip(oneShotClip).isFinishedAt(oneShotSeconds)) oneShotClip = null;
        }
        EnemyAnimationRules.Hold hold = EnemyAnimationRules.holdFor(enemy.state);
        if (hold == null) {
            holdClip = null;
        } else {
            if (holdClip != hold.clip) {
                // The state holds a clip whose moment never reached us (e.g. a re-commit): show the held pose.
                holdClip    = hold.clip;
                holdSeconds = visual.manifest.clip(hold.clip) != null
                        ? visual.manifest.clip(hold.clip).durationSeconds : 0f;
            }
            holdSeconds += deltaTime;
        }
        if (sliding) {
            slideSeconds += deltaTime;
            if (slideSeconds >= slideDurationSeconds) sliding = false;
        }
    }

    /**
     * The frame to draw now: the highest-priority candidate among the one-shot, the state's held clip,
     * the slide's MOVE loop and the resting loop (DORMANT while asleep, else IDLE).
     */
    public EnemyClipManifest.Frame currentFrame(Enemy enemy) {
        EnemyClipManifest manifest = visual.manifest;
        EnemyClipId bestClip     = enemy.state == EnemyState.DORMANT && manifest.clip(EnemyClipId.DORMANT) != null
                ? EnemyClipId.DORMANT : EnemyClipId.IDLE;
        int         bestPriority = EnemyAnimationRules.PRIORITY_IDLE;
        float       bestSeconds  = loopSeconds;
        if (sliding && manifest.clip(EnemyClipId.MOVE) != null) {
            bestClip = EnemyClipId.MOVE; bestPriority = EnemyAnimationRules.PRIORITY_MOVE; bestSeconds = slideSeconds;
        }
        EnemyAnimationRules.Hold hold = EnemyAnimationRules.holdFor(enemy.state);
        if (hold != null && holdClip == hold.clip && manifest.clip(holdClip) != null && hold.priority > bestPriority) {
            bestClip = holdClip; bestPriority = hold.priority; bestSeconds = holdSeconds;
        }
        if (oneShotClip != null && oneShotPriority > bestPriority) {
            bestClip = oneShotClip; bestSeconds = oneShotSeconds;
        }
        EnemyClipManifest.Clip clip = manifest.clip(bestClip);
        return clip.frames[clip.frameIndexAt(bestSeconds)];
    }

    /** The clip currently chosen. TOOLING ONLY (capture driver, logs): it allocates — never call it from render(). */
    public EnemyClipId currentClipId(Enemy enemy) {
        EnemyClipManifest.Frame frame = currentFrame(enemy);
        for (EnemyClipId id : EnemyClipId.values()) {
            EnemyClipManifest.Clip clip = visual.manifest.clip(id);
            if (clip == null) continue;
            for (EnemyClipManifest.Frame candidate : clip.frames) {
                if (candidate == frame) return id;
            }
        }
        return EnemyClipId.IDLE;
    }

    /** World X of the body: the eased slide position while sliding, else the enemy's own render centre. */
    public float renderCenterX(Enemy enemy) {
        if (!sliding) return enemy.renderCenterX();
        return GameMath.lerp(slideFromX, slideToX, slideProgress());
    }

    public float renderCenterY(Enemy enemy) {
        if (!sliding) return enemy.renderCenterY();
        return GameMath.lerp(slideFromY, slideToY, slideProgress());
    }

    private float slideProgress() {
        return GameMath.smoothstep01(Math.min(1f, slideSeconds / slideDurationSeconds));
    }
}
