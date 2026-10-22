package ge.tbegvadze.toon3d.render.enemyvisual;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;
import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.enemy.EnemyState;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.enemy.EnemyVisualEvent;
import ge.tbegvadze.toon3d.enemy.EnemyVisualListener;
import ge.tbegvadze.toon3d.status.StatusEffect;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.Constants;
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
 * <p>ORDER 3 — REACTIONS AND EFFECTS. The layer also owns the floor's {@link EnemyEffectSystem} (pooled
 * particles) and {@link RemainsRegistry}, answers every hit by its {@link EnemyAnimationRules.HitReaction}
 * row (clip, family burst, recoil away from the hit's source, flash suppression, block shimmer), runs the
 * repeating effects of a living creature every frame (wounded leak, wind-up crackle, status particles from
 * {@link EnemyStatusVisuals}), launches a ranged creature's generated projectile on its ATTACK clip's emit
 * frame, and at a death fires the kill and death bursts and leaves the remains.
 *
 * <p>Rule R2 (order 2) / R1 (order 3): the layer reads enemies and receives moments; it never writes to an
 * enemy, the manager or the world. Rule R6 / R4: after construction nothing here allocates — the animator,
 * particle, dying and remains pools are pre-sized, and every per-frame loop is an index loop.
 */
public final class EnemyVisualLayer implements EnemyVisualListener, Disposable {

    private static final String LOG_TAG = "EnemyVisualLayer";

    private final EnemyManager             enemyManager;
    private final EnemyAtlasCache          atlasCache = new EnemyAtlasCache();
    private final EnemyAnimator[]          animators  = new EnemyAnimator[EffectConstants.ENEMY_VISUAL_ANIMATOR_POOL_CAPACITY];
    private final IntMap<EnemyAnimator>    animatorsById = new IntMap<>(EffectConstants.ENEMY_VISUAL_ANIMATOR_POOL_CAPACITY * 2);
    private final DyingEnemyPool           dyingPool  = new DyingEnemyPool();
    private final EnemyEffectSystem        effects    = new EnemyEffectSystem();
    private final RemainsRegistry          remains    = new RemainsRegistry();
    private int     updateStamp = 0;
    private boolean poolExhaustedLogged = false;
    // The viewer (World pushes it every frame): projectile target and the direction "away from the player".
    private float   viewerWorldX, viewerWorldY;
    private float   viewerDirectionX = 1f, viewerDirectionY = 0f;
    // The tile of the last hit / death this layer answered with its own effects since the last update: the
    // legacy screen-space hit spray (ImpactEffectSystem) stands down there, so a migrated body never bleeds red.
    private int     answeredTileColumn = -1, answeredTileRow = -1;

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

    /**
     * Loads the creature for a type the floor will add later that the constructor could not see (the HUNTED
     * trail hunter), so its first arrival never loads an atlas mid-turn (R5). Render thread only.
     */
    public void preload(EnemyType type) {
        atlasCache.ensureLoaded(type);
    }

    /** The player, once per frame before {@link #update}: where shots fly to and which way is "away". */
    public void setViewer(float worldX, float worldY, float directionX, float directionY) {
        viewerWorldX     = worldX;
        viewerWorldY     = worldY;
        viewerDirectionX = directionX;
        viewerDirectionY = directionY;
        effects.setViewer(directionX, directionY);
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

    /**
     * A hit's answer (order 3): the row's flinch, its family burst at the hit centre, the recoil AWAY from the
     * hit's source tile (the blast tile for splash, else the player), and the flash / shimmer reads. A hit
     * that killed is answered by DIED instead (DEATH + the KILL burst), so it does nothing here.
     */
    void react(EnemyAnimator animator, EnemyVisualEvent event, EnemyAnimationRules.HitReaction reaction) {
        Enemy enemy = event.enemy;
        answeredTileColumn = enemy.tileColumn;
        answeredTileRow    = enemy.tileRow;
        if (!enemy.isAlive()) return;
        if (reaction.clip != null) animator.playOneShot(reaction.clip, EnemyAnimationRules.PRIORITY_HURT);
        if (reaction.suppressFlash) animator.suppressHitFlash(EffectConstants.ENEMY_VISUAL_FLASH_SUPPRESS_SECONDS);
        if (reaction.shimmer) animator.beginShimmer();
        if (reaction.recoilTiles > 0f) {
            // Away from the source: enemy tile minus source tile; on the source tile itself (or unknown), away
            // from the viewer, which is straight back into the screen.
            float awayX = viewerDirectionX;
            float awayY = viewerDirectionY;
            if (event.sourceColumn >= 0) {
                float differenceX = enemy.tileColumn - event.sourceColumn;
                float differenceY = enemy.tileRow    - event.sourceRow;
                float length = (float) Math.sqrt(differenceX * differenceX + differenceY * differenceY);
                if (length > 0f) {
                    awayX = differenceX / length;
                    awayY = differenceY / length;
                }
            }
            animator.kick(awayX, awayY, reaction.recoilTiles, reaction.dropPixels);
        }
        if (reaction.burst != null) {
            emitAt(animator, enemy, animator.currentFrame(enemy), null, animator.visual.burst(reaction.burst));
        }
    }

    /** STATUS_APPLIED: the status's onset burst (a fire catching), at its placement. */
    void statusApplied(EnemyAnimator animator, Enemy enemy, StatusType status) {
        if (status == null || !enemy.isAlive()) return;
        EnemyStatusVisuals.Row row = EnemyStatusVisuals.rowFor(status);
        if (row.onsetBurst == null) return;
        emitAt(animator, enemy, animator.currentFrame(enemy), animator.visual.placement(row.placement),
                animator.visual.burst(row.onsetBurst));
    }

    /**
     * A ranged shot of any kind (the ordinary attack AND the debuff shot — every caller of
     * {@code EnemyAttackListener.onRangedAttack}), aimed at the COMMITTED lane target tile, so a player who
     * stepped off the lane sees the glob fly down the old lane and miss — never curve into their new tile.
     * It leaves the current frame's EMITTER anchor on the emit frame (guide 7.7) of the one-shot playing - ATTACK,
     * or (order 5) an ability clip such as the Mire Wraith's DEBUFF_PLAYER volley - else at once. Returns false when
     * this layer cannot draw it (a legacy type, no PROJECTILE in the family set, the animator pool exhausted): the
     * caller then draws the procedural glob as before.
     */
    public boolean scheduleShot(Enemy enemy, int targetColumn, int targetRow) {
        EnemyAnimator animator = animatorFor(enemy, true);
        if (animator == null || animator.visual.burst(EnemyEffectSet.BurstId.PROJECTILE) == null) return false;
        float delaySeconds = 0f;
        EnemyClipId playing = animator.oneShotClip();
        EnemyClipManifest.Clip playingClip = playing != null ? animator.visual.manifest.clip(playing) : null;
        if (playingClip != null) {
            float emitSeconds = 0f;
            for (EnemyClipManifest.Frame frame : playingClip.frames) {
                if (frame.event == EnemyClipManifest.FrameEvent.EMIT) break;
                emitSeconds += frame.durationSeconds;
            }
            if (emitSeconds < playingClip.durationSeconds) {   // no emit frame: leave at once
                delaySeconds = Math.max(0f, emitSeconds - animator.oneShotElapsedSeconds());
            }
        }
        animator.pendingEmitSeconds = delaySeconds;
        animator.pendingTargetX     = targetColumn * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
        animator.pendingTargetY     = targetRow    * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
        return true;
    }

    /**
     * DIED: the KILL burst (a weapon or blast kill: the killing blow, stronger than a hit), the DEATH burst
     * of the creature's own materials on the killing frame, the dying pool plays DEATH at the body's last
     * visual position, and the remains are registered there to take over when it ends (A4).
     */
    void beginDying(EnemyAnimator animator, Enemy enemy, EnemyVisualEvent.DeathCause cause) {
        // A shot committed but not yet out of the emitter still leaves now: scheduleShot already told the
        // procedural glob to stand down, so dying before the emit frame must not swallow it.
        if (animator.pendingEmitSeconds >= 0f) {
            launchShot(animator, enemy);
            animator.pendingEmitSeconds = -1f;
        }
        EnemyClipManifest.Frame frame = animator.currentFrame(enemy);
        answeredTileColumn = enemy.tileColumn;
        answeredTileRow    = enemy.tileRow;
        if (cause == EnemyVisualEvent.DeathCause.WEAPON || cause == EnemyVisualEvent.DeathCause.HAZARD) {
            emitAt(animator, enemy, frame, null, animator.visual.burst(EnemyEffectSet.BurstId.KILL));
        }
        // Order 5: the death row for the cause (a self-destruct bursts: DEATH_BURST + DETONATE), else DEATH.
        EnemyEffectSet.Burst deathBurst = animator.visual.burst(EnemyAnimationRules.deathBurstFor(cause));
        if (deathBurst == null) deathBurst = animator.visual.burst(EnemyEffectSet.BurstId.DEATH);
        emitAt(animator, enemy, frame, animator.visual.placement(EnemyEffectSet.PlacementId.BODY), deathBurst);
        float worldX = animator.renderCenterX(enemy);
        float worldY = animator.renderCenterY(enemy);
        EnemyClipId deathClip = EnemyAnimationRules.deathClipFor(cause, animator.visual.manifest);
        dyingPool.add(enemy.type, animator.visual, deathClip, worldX, worldY, enemy.tileColumn, enemy.tileRow);
        EnemyClipManifest.Clip death = animator.visual.manifest.clip(deathClip);
        remains.add(enemy.type, animator.visual, deathClip, worldX, worldY, enemy.tileColumn, enemy.tileRow,
                death != null ? death.durationSeconds : 0f);
        release(animator);
    }

    // ---------------------------------------------------------------------------------------------
    // The clock
    // ---------------------------------------------------------------------------------------------

    /**
     * Advances every animator, its repeating effects, the particles, the dying instances and the remains.
     * Call once per frame from the update path that also advances the enemies' hit-flash timers, so it
     * stops whenever the world is frozen.
     */
    public void update(float deltaTime) {
        updateStamp++;
        answeredTileColumn = -1;
        answeredTileRow    = -1;
        List<Enemy> enemies = enemyManager.getEnemies();
        for (int enemyIndex = 0; enemyIndex < enemies.size(); enemyIndex++) {
            Enemy enemy = enemies.get(enemyIndex);
            if (!enemy.isAlive()) continue;
            EnemyAnimator animator = animatorFor(enemy, true);
            if (animator == null) continue;
            animator.seenStamp = updateStamp;
            animator.advance(deltaTime, enemy, effects.getRightX(), effects.getRightY());
            runRepeatingEffects(animator, enemy, deltaTime);
        }
        // Sweep: an enemy that left the list without a DIED moment frees its animator.
        for (EnemyAnimator animator : animators) {
            if (animator.enemyId >= 0 && animator.seenStamp != updateStamp) release(animator);
        }
        dyingPool.advance(deltaTime);
        remains.advance(deltaTime);
        effects.update(deltaTime);
    }

    /** The effects a living creature keeps making: its pending shot, the wounded leak, the wind-up, its statuses. */
    private void runRepeatingEffects(EnemyAnimator animator, Enemy enemy, float deltaTime) {
        EnemyAtlasCache.CreatureVisual visual = animator.visual;
        if (animator.pendingEmitSeconds >= 0f) {
            animator.pendingEmitSeconds -= deltaTime;
            if (animator.pendingEmitSeconds < 0f) launchShot(animator, enemy);
        }
        if (enemy.state == EnemyState.DORMANT) return;
        if (animator.isWounded(enemy)) {
            animator.leakTimerSeconds = tick(animator, enemy, animator.leakTimerSeconds, deltaTime,
                    visual.burst(EnemyEffectSet.BurstId.LEAK), EnemyEffectSet.PlacementId.LEAK);
        }
        if (EnemyAnimationRules.holdsWindUp(enemy) && !animator.isDazed()) {
            animator.chargeTimerSeconds = tick(animator, enemy, animator.chargeTimerSeconds, deltaTime,
                    visual.burst(EnemyEffectSet.BurstId.CHARGE), EnemyEffectSet.PlacementId.CHARGE);
        } else {
            animator.chargeTimerSeconds = 0f;   // the next wind-up crackles at once
        }
        for (int rowIndex = 0; rowIndex < EnemyStatusVisuals.count(); rowIndex++) {
            EnemyStatusVisuals.Row row = EnemyStatusVisuals.get(rowIndex);
            if (row.loopBurst == null) continue;
            if (!hasStatus(enemy, row.status)) {
                animator.statusTimerSeconds[rowIndex] = 0f;
                continue;
            }
            animator.statusTimerSeconds[rowIndex] = tick(animator, enemy, animator.statusTimerSeconds[rowIndex],
                    deltaTime, visual.burst(row.loopBurst), row.placement);
        }
    }

    /** One repeating burst's countdown: fires it when due and returns the new countdown. */
    private float tick(EnemyAnimator animator, Enemy enemy, float timerSeconds, float deltaTime,
                       EnemyEffectSet.Burst burst, EnemyEffectSet.PlacementId placementId) {
        if (burst == null || !burst.repeats()) return timerSeconds;
        timerSeconds -= deltaTime;
        if (timerSeconds > 0f) return timerSeconds;
        emitAt(animator, enemy, animator.currentFrame(enemy), animator.visual.placement(placementId), burst);
        return effects.nextInterval(burst);
    }

    /** The emit frame arrived: a muzzle flash on the emitter anchor and the glob flying at the player. */
    private void launchShot(EnemyAnimator animator, Enemy enemy) {
        EnemyAtlasCache.CreatureVisual visual = animator.visual;
        EnemyClipManifest.Frame frame = animator.currentFrame(enemy);
        resolvePoint(animator, enemy, frame, EnemyClipManifest.Anchor.EMITTER, 0f, 0f);
        emitResolved(visual.burst(EnemyEffectSet.BurstId.MUZZLE), 0f);
        float fromTileX = resolvedTileX, fromTileY = resolvedTileY, fromHeight = resolvedHeight;
        effects.launch(visual.burst(EnemyEffectSet.BurstId.PROJECTILE), visual.burst(EnemyEffectSet.BurstId.TRAIL),
                visual.burst(EnemyEffectSet.BurstId.IMPACT), fromTileX, fromTileY, fromHeight,
                animator.pendingTargetX / Constants.CELL_SIZE, animator.pendingTargetY / Constants.CELL_SIZE,
                EffectConstants.ENEMY_EFFECT_PROJECTILE_TARGET_HEIGHT,
                EffectConstants.ENEMY_PROJECTILE_TRAVEL_SECONDS, resolvedTilesPerPixel);
    }

    // ---------------------------------------------------------------------------------------------
    // Emission: a canvas anchor of the current frame -> a world point
    // ---------------------------------------------------------------------------------------------

    // Scratch of the last resolvePoint (render/update thread only; no allocation).
    private float resolvedTileX, resolvedTileY, resolvedHeight, resolvedTilesPerPixel, resolvedRadiusPixels;

    private void emitAt(EnemyAnimator animator, Enemy enemy, EnemyClipManifest.Frame frame,
                        EnemyEffectSet.Placement placement, EnemyEffectSet.Burst burst) {
        if (burst == null) return;
        if (placement != null) {
            resolvePoint(animator, enemy, frame, placement.anchor, placement.offsetX, placement.offsetY);
            resolvedRadiusPixels = placement.radius;
        } else {
            resolvePoint(animator, enemy, frame, EnemyClipManifest.Anchor.HIT_CENTRE, 0f, 0f);
        }
        emitResolved(burst, 0f);
    }

    private void emitResolved(EnemyEffectSet.Burst burst, float angleBiasDegrees) {
        effects.emit(burst, resolvedTileX, resolvedTileY, resolvedHeight, resolvedTilesPerPixel,
                resolvedRadiusPixels, angleBiasDegrees);
    }

    /**
     * Converts a canvas point (an anchor of {@code frame} + an offset, canvas pixels, y down) into the world:
     * the body's render centre, across the screen by the point's distance from the canvas midline, up by its
     * height above the canvas floor, a hair toward the viewer so the effect sits in front of the body.
     */
    private void resolvePoint(EnemyAnimator animator, Enemy enemy, EnemyClipManifest.Frame frame,
                              EnemyClipManifest.Anchor anchor, float offsetX, float offsetY) {
        EnemyClipManifest manifest = animator.visual.manifest;
        float anchorX = frame.anchors[anchor.ordinal() * 2];
        float anchorY = frame.anchors[anchor.ordinal() * 2 + 1];
        if (Float.isNaN(anchorX)) {   // a creature without this anchor: its hit centre, else the canvas middle
            anchorX = frame.anchors[EnemyClipManifest.Anchor.HIT_CENTRE.ordinal() * 2];
            anchorY = frame.anchors[EnemyClipManifest.Anchor.HIT_CENTRE.ordinal() * 2 + 1];
            if (Float.isNaN(anchorX)) {
                anchorX = manifest.canvasWidth * 0.5f;
                anchorY = manifest.canvasHeight * 0.5f;
            }
        }
        float tilesPerPixel = enemy.type.heightMultiplier() / manifest.canvasHeight;
        float acrossTiles = (anchorX + offsetX - manifest.canvasWidth * 0.5f) * tilesPerPixel;
        float centreTileX = animator.renderCenterX(enemy) / Constants.CELL_SIZE;
        float centreTileY = animator.renderCenterY(enemy) / Constants.CELL_SIZE;
        resolvedTileX = centreTileX + effects.getRightX() * acrossTiles
                - viewerDirectionX * EffectConstants.ENEMY_EFFECT_FRONT_NUDGE_TILES;
        resolvedTileY = centreTileY + effects.getRightY() * acrossTiles
                - viewerDirectionY * EffectConstants.ENEMY_EFFECT_FRONT_NUDGE_TILES;
        resolvedHeight = Math.max(0f, (manifest.canvasHeight - (anchorY + offsetY)) * tilesPerPixel);
        resolvedTilesPerPixel = tilesPerPixel;
        resolvedRadiusPixels  = 0f;
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

    public EnemyEffectSystem getEffects() {
        return effects;
    }

    public RemainsRegistry getRemains() {
        return remains;
    }

    /**
     * True when the hit or death just reported on this tile was a migrated creature's, answered with its own
     * generated effects — the generic screen-space hit spray must not draw over them.
     */
    public boolean answeredHitAt(int tileColumn, int tileRow) {
        return tileColumn == answeredTileColumn && tileRow == answeredTileRow;
    }

    /** Null-safe active test (an enemy's status map holds a row per type; this never trusts it blindly). */
    public static boolean hasStatus(Enemy enemy, StatusType status) {
        StatusEffect effect = enemy.getActiveEffects().get(status);
        return effect != null && effect.isActive();
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
        remains.clear();
        effects.clear();
        animatorsById.clear();
        for (EnemyAnimator animator : animators) animator.release();
        atlasCache.dispose();
    }
}
