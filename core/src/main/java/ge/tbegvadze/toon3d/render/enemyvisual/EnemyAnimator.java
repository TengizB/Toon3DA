package ge.tbegvadze.toon3d.render.enemyvisual;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyState;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * One live enemy's visual state (generated-enemy-art order 2): the one-shot clip playing on top, the clip
 * a held state keeps on its last frame, the cosmetic tile slide, and the loop clock with the enemy's own
 * idle phase. Order 3 adds the REACTION state: the recoil kick, the white-flash suppression of a resisted or
 * blocked hit, the block shimmer, the WOUNDED resting loop and the dazed hold, plus the countdowns of every
 * repeating effect (leak, wind-up crackle, status particles) and a pending projectile emit. Pooled by
 * {@link EnemyVisualLayer}, reset on reuse; nothing here allocates.
 *
 * <p>Rule R2 (order 2) / R1 (order 3): it READS the enemy (state, tile, health, statuses) and RECEIVES
 * moments; it never writes to the enemy.
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

    // ---- order 3: reactions ----
    private float recoilDirectionX, recoilDirectionY;   // unit, world floor plane, AWAY from the hit
    private float recoilPeakTiles, recoilPeakDropPixels;
    private float recoilSeconds = Float.MAX_VALUE;
    private float flashSuppressSeconds;
    private float shimmerSeconds = Float.MAX_VALUE;
    /** Screen-right at the last advance (world floor plane), for the dazed sway. */
    private float rightX, rightY;
    private boolean dazed;

    // ---- order 3: effect countdowns (seconds until the repeating burst fires again) ----
    float   leakTimerSeconds;
    float   chargeTimerSeconds;
    final float[] statusTimerSeconds = new float[StatusType.values().length];
    /** Seconds until the committed shot leaves the emitter; negative = no shot pending. */
    float   pendingEmitSeconds = -1f;
    float   pendingTargetX, pendingTargetY;

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
        recoilPeakTiles      = 0f;
        recoilPeakDropPixels = 0f;
        recoilSeconds        = Float.MAX_VALUE;
        flashSuppressSeconds = 0f;
        shimmerSeconds       = Float.MAX_VALUE;
        dazed                = false;
        leakTimerSeconds     = 0f;
        chargeTimerSeconds   = 0f;
        java.util.Arrays.fill(statusTimerSeconds, 0f);
        pendingEmitSeconds   = -1f;
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

    /**
     * RECOIL (order 3): pushes the body {@code tiles} along the unit floor-plane direction ({@code awayX},
     * {@code awayY}) and drops it {@code dropPixels} canvas px, easing back over ENEMY_VISUAL_RECOIL_SECONDS.
     * A kick during a kick adds to what is still showing, never past the caps.
     */
    void kick(float awayX, float awayY, float tiles, float dropPixels) {
        float remaining = recoilEnvelope();
        recoilPeakTiles      = Math.min(EffectConstants.ENEMY_VISUAL_RECOIL_MAX_TILES, recoilPeakTiles * remaining + tiles);
        recoilPeakDropPixels = Math.min(EffectConstants.ENEMY_VISUAL_RECOIL_MAX_DROP_PIXELS,
                recoilPeakDropPixels * remaining + dropPixels);
        recoilDirectionX = awayX;
        recoilDirectionY = awayY;
        recoilSeconds    = 0f;
    }

    /** The next {@code seconds} of hit flash are not drawn (a resisted or blocked hit). */
    void suppressHitFlash(float seconds) {
        flashSuppressSeconds = Math.max(flashSuppressSeconds, seconds);
    }

    void beginShimmer() {
        shimmerSeconds = 0f;
    }

    /** Advances every clock by {@code deltaTime}; ends a finished one-shot and a finished slide. */
    void advance(float deltaTime, Enemy enemy, float screenRightX, float screenRightY) {
        loopSeconds += deltaTime;
        rightX = screenRightX;
        rightY = screenRightY;
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
        if (recoilSeconds < Float.MAX_VALUE) recoilSeconds += deltaTime;
        if (flashSuppressSeconds > 0f) flashSuppressSeconds -= deltaTime;
        if (shimmerSeconds < Float.MAX_VALUE) shimmerSeconds += deltaTime;
        // Dazed (order 3): a STUNNED status, or a stagger / riposte that cancelled its next action.
        dazed = enemy.isAlive() && enemy.state != EnemyState.DORMANT
                && (enemy.skipNextAction || EnemyVisualLayer.hasStatus(enemy, StatusType.STUNNED));
    }

    /** True below the WOUNDED threshold (AS3), awake and alive: the WOUNDED idle and the leak. */
    boolean isWounded(Enemy enemy) {
        return enemy.isAlive() && enemy.state != EnemyState.DORMANT && enemy.maxHealth > 0
                && enemy.health < enemy.maxHealth * EffectConstants.ENEMY_VISUAL_WOUNDED_HEALTH_FRACTION;
    }

    /**
     * The frame to draw now: the highest-priority candidate among the one-shot, the state's held clip, the
     * dazed hold, the slide's MOVE loop and the resting loop (DORMANT asleep, WOUNDED when hurt, else IDLE).
     */
    public EnemyClipManifest.Frame currentFrame(Enemy enemy) {
        EnemyClipManifest manifest = visual.manifest;
        EnemyClipId bestClip = EnemyClipId.IDLE;
        if (enemy.state == EnemyState.DORMANT && manifest.clip(EnemyClipId.DORMANT) != null) {
            bestClip = EnemyClipId.DORMANT;
        } else if (isWounded(enemy) && manifest.clip(EnemyClipId.WOUNDED) != null) {
            bestClip = EnemyClipId.WOUNDED;
        }
        int         bestPriority = EnemyAnimationRules.PRIORITY_IDLE;
        float       bestSeconds  = loopSeconds;
        if (sliding && manifest.clip(EnemyClipId.MOVE) != null) {
            bestClip = EnemyClipId.MOVE; bestPriority = EnemyAnimationRules.PRIORITY_MOVE; bestSeconds = slideSeconds;
        }
        if (dazed && manifest.clip(EnemyClipId.HURT_HEAVY) != null && EnemyAnimationRules.PRIORITY_DAZED > bestPriority) {
            // HURT_HEAVY's last pose, held (the sway is a position offset, see renderCenterX).
            bestClip = EnemyClipId.HURT_HEAVY; bestPriority = EnemyAnimationRules.PRIORITY_DAZED;
            bestSeconds = Float.MAX_VALUE;
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

    /** World X of the body: the eased slide (or the enemy's own centre), plus the recoil and the dazed sway. */
    public float renderCenterX(Enemy enemy) {
        float base = sliding ? GameMath.lerp(slideFromX, slideToX, slideProgress()) : enemy.renderCenterX();
        return base + (recoilDirectionX * recoilTiles() * Constants.CELL_SIZE) + rightX * swayWorldUnits(enemy);
    }

    public float renderCenterY(Enemy enemy) {
        float base = sliding ? GameMath.lerp(slideFromY, slideToY, slideProgress()) : enemy.renderCenterY();
        return base + (recoilDirectionY * recoilTiles() * Constants.CELL_SIZE) + rightY * swayWorldUnits(enemy);
    }

    /** How far the recoil drops the body right now, in canvas pixels (the renderer scales it to the screen). */
    public float recoilDropPixels() {
        return recoilPeakDropPixels * recoilEnvelope();
    }

    /** True while a resisted / blocked hit's white flash must not be drawn. */
    public boolean suppressesHitFlash() {
        return flashSuppressSeconds > 0f;
    }

    /**
     * The block shimmer: -1 when none, else how far its band has swept down the body, 0 (top) to 1 (gone
     * below the feet).
     */
    public float shimmerProgress() {
        if (shimmerSeconds >= EffectConstants.ENEMY_VISUAL_SHIMMER_SECONDS) return -1f;
        return shimmerSeconds / EffectConstants.ENEMY_VISUAL_SHIMMER_SECONDS;
    }

    public boolean isDazed() {
        return dazed;
    }

    private float recoilTiles() {
        return recoilPeakTiles * recoilEnvelope();
    }

    /** 1 at the kick, easing out to 0 over the recoil time: (1 - t)^2. */
    private float recoilEnvelope() {
        if (recoilSeconds >= EffectConstants.ENEMY_VISUAL_RECOIL_SECONDS) return 0f;
        float remaining = 1f - recoilSeconds / EffectConstants.ENEMY_VISUAL_RECOIL_SECONDS;
        return remaining * remaining;
    }

    /** The dazed sway across the screen, in WORLD units (canvas px scaled by the creature's own pixel size). */
    private float swayWorldUnits(Enemy enemy) {
        if (!dazed) return 0f;
        float tilesPerPixel = enemy.type.heightMultiplier() / visual.manifest.canvasHeight;
        return (float) Math.sin(loopSeconds * EffectConstants.ENEMY_VISUAL_DAZED_SWAY_HZ * Math.PI * 2.0)
                * EffectConstants.ENEMY_VISUAL_DAZED_SWAY_PIXELS * tilesPerPixel * Constants.CELL_SIZE;
    }

    private float slideProgress() {
        return GameMath.smoothstep01(Math.min(1f, slideSeconds / slideDurationSeconds));
    }
}
