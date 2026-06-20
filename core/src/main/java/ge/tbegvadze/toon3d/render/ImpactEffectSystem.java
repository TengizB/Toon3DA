package ge.tbegvadze.toon3d.render;

import ge.tbegvadze.toon3d.entity.ImpactEventListener;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.Random;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;

/**
 * Simulation-only: owns all live impact-effect state and the pre-allocated
 * object pools for particles, death bursts, floating damage numbers, colored
 * ring pulses, and flash accumulators.
 * Carries no GPU resources — no dispose() needed.
 *
 * World creates this, wires it to EnemyManager as an ImpactEventListener,
 * pushes setPlayerState() + update(deltaTime) each frame before rendering,
 * and then hands the reference to ImpactEffectRenderer for drawing.
 *
 * Pre-computed ring segment table is static so it is built once per JVM load
 * and reused by every ImpactEffectRenderer.
 */
public final class ImpactEffectSystem implements ImpactEventListener {

    // Pre-baked ring-dot positions — built once, zero runtime trig in render
    static final int     RING_SEGMENTS = 16;
    static final float[] RING_COS      = new float[RING_SEGMENTS];
    static final float[] RING_SIN      = new float[RING_SEGMENTS];

    static {
        for (int segmentIndex = 0; segmentIndex < RING_SEGMENTS; segmentIndex++) {
            double angleRadians = 2.0 * Math.PI * segmentIndex / RING_SEGMENTS;
            RING_COS[segmentIndex] = (float) Math.cos(angleRadians);
            RING_SIN[segmentIndex] = (float) Math.sin(angleRadians);
        }
    }

    // Pool sizes derived from constants
    static final int MAX_PARTICLES     = EffectConstants.IMPACT_MAX_SIMULTANEOUS_HITS
                                          * EffectConstants.HIT_PARTICLE_COUNT * 2; // ×2 for kill debris
    static final int MAX_DEATH_BURSTS  = EffectConstants.IMPACT_MAX_SIMULTANEOUS_HITS;
    static final int MAX_DAMAGE_NUMBERS = EffectConstants.IMPACT_MAX_SIMULTANEOUS_HITS;
    static final int MAX_RING_PULSES   = EffectConstants.RING_PULSE_POOL_SIZE;

    // -------------------------------------------------------------------------
    // Screen shake — single accumulator
    // -------------------------------------------------------------------------
    private float shakeTimeRemaining = 0f;
    private float shakeDuration      = 0f;
    private float shakeMagnitude     = 0f;
    private float shakeOffsetX       = 0f;
    private float shakeOffsetY       = 0f;

    // Kill flash — single accumulator
    private float killFlashTimeRemaining = 0f;

    // Crit flash — shorter, dimmer than kill flash
    private float critFlashTimeRemaining = 0f;

    // -------------------------------------------------------------------------
    // Particle pool — hit sparks and death debris share this pool
    // Package-private so ImpactEffectRenderer can iterate without copy.
    // -------------------------------------------------------------------------
    final float[]   particleScreenX   = new float[MAX_PARTICLES];
    final float[]   particleScreenY   = new float[MAX_PARTICLES];
    final float[]   particleVelocityX = new float[MAX_PARTICLES];
    final float[]   particleVelocityY = new float[MAX_PARTICLES];
    final float[]   particleAge       = new float[MAX_PARTICLES];
    final float[]   particleLife      = new float[MAX_PARTICLES];
    final float[]   particleRed       = new float[MAX_PARTICLES];
    final float[]   particleGreen     = new float[MAX_PARTICLES];
    final float[]   particleBlue      = new float[MAX_PARTICLES];
    final float[]   particleSize      = new float[MAX_PARTICLES];
    final boolean[] particleActive    = new boolean[MAX_PARTICLES];

    // -------------------------------------------------------------------------
    // Death burst pool — expanding ring of dots + white-hot core
    // -------------------------------------------------------------------------
    final float[]   burstScreenX   = new float[MAX_DEATH_BURSTS];
    final float[]   burstScreenY   = new float[MAX_DEATH_BURSTS];
    final float[]   burstAge       = new float[MAX_DEATH_BURSTS];
    final float[]   burstLife      = new float[MAX_DEATH_BURSTS];
    final float[]   burstMaxRadius = new float[MAX_DEATH_BURSTS];
    final boolean[] burstActive    = new boolean[MAX_DEATH_BURSTS];

    // -------------------------------------------------------------------------
    // Floating damage number pool — world position re-projected each frame
    // -------------------------------------------------------------------------
    final float[]   numberWorldX  = new float[MAX_DAMAGE_NUMBERS];
    final float[]   numberWorldY  = new float[MAX_DAMAGE_NUMBERS];
    final float[]   numberAge     = new float[MAX_DAMAGE_NUMBERS];
    final float[]   numberLife    = new float[MAX_DAMAGE_NUMBERS];
    final int[]     numberAmount  = new int[MAX_DAMAGE_NUMBERS];
    final boolean[] numberIsKill  = new boolean[MAX_DAMAGE_NUMBERS];
    final boolean[] numberActive  = new boolean[MAX_DAMAGE_NUMBERS];

    // -------------------------------------------------------------------------
    // Colored ring pulse pool — world-anchored expanding ring on proc hits
    // Screen position computed at spawn time from tile coords; not re-projected.
    // -------------------------------------------------------------------------
    final float[]   ringPulseScreenX   = new float[MAX_RING_PULSES];
    final float[]   ringPulseScreenY   = new float[MAX_RING_PULSES];
    final float[]   ringPulseAge       = new float[MAX_RING_PULSES];
    final float[]   ringPulseLife      = new float[MAX_RING_PULSES];
    final float[]   ringPulseMaxRadius = new float[MAX_RING_PULSES];
    final float[]   ringPulseRed       = new float[MAX_RING_PULSES];
    final float[]   ringPulseGreen     = new float[MAX_RING_PULSES];
    final float[]   ringPulseBlue      = new float[MAX_RING_PULSES];
    final boolean[] ringPulseActive    = new boolean[MAX_RING_PULSES];

    // -------------------------------------------------------------------------
    // Player state for world → screen projection (pushed by World each frame
    // before rendering; used both at spawn time and by the renderer per frame)
    // -------------------------------------------------------------------------
    private float playerWorldX = 0f;
    private float playerWorldY = 0f;
    private float directionX   = 1f;
    private float directionY   = 0f;
    private float planeX       = 0f;
    private float planeY       = 1f;

    private final Random random;

    public ImpactEffectSystem() {
        this.random = new Random();
    }

    // -------------------------------------------------------------------------
    // Player state push — called each frame before rendering
    // -------------------------------------------------------------------------

    public void setPlayerState(float worldX, float worldY,
                                float playerDirectionX, float playerDirectionY,
                                float fieldOfViewRadians) {
        this.playerWorldX = worldX;
        this.playerWorldY = worldY;
        this.directionX   = playerDirectionX;
        this.directionY   = playerDirectionY;
        float planeScale  = (float) Math.tan(fieldOfViewRadians / 2.0);
        this.planeX       = GameMath.cameraPlaneX(playerDirectionY, planeScale);
        this.planeY       = GameMath.cameraPlaneY(playerDirectionX, planeScale);
    }

    // -------------------------------------------------------------------------
    // ImpactEventListener — called from EnemyManager
    // -------------------------------------------------------------------------

    @Override
    public void onEnemyHit(float worldX, float worldY, float heightMultiplier, int damageDealt) {
        triggerShake(EffectConstants.HIT_SHAKE_MAGNITUDE, EffectConstants.HIT_SHAKE_DURATION_SECONDS);

        float screenX = projectToScreenX(worldX, worldY);
        float screenY = projectToScreenY(worldX, worldY, heightMultiplier);

        spawnHitParticles(screenX, screenY, EffectConstants.HIT_PARTICLE_COUNT, false);
        spawnDamageNumber(worldX, worldY, damageDealt, false);
    }

    @Override
    public void onEnemyKilled(float worldX, float worldY, float heightMultiplier, int killingBlowDamage) {
        triggerShake(EffectConstants.KILL_SHAKE_MAGNITUDE, EffectConstants.KILL_SHAKE_DURATION_SECONDS);
        killFlashTimeRemaining = EffectConstants.KILL_FLASH_DURATION_SECONDS;

        float screenX = projectToScreenX(worldX, worldY);
        float screenY = projectToScreenY(worldX, worldY, heightMultiplier);

        // Kill debris: twice the particle count, hot white-orange colour
        spawnHitParticles(screenX, screenY, EffectConstants.HIT_PARTICLE_COUNT * 2, true);
        spawnDeathBurst(screenX, screenY, heightMultiplier);
        spawnDamageNumber(worldX, worldY, killingBlowDamage, true);
    }

    // -------------------------------------------------------------------------
    // Flash triggers — called from AbilityFeedback
    // -------------------------------------------------------------------------

    /** Triggers a crit edge flash (shorter and dimmer than kill flash). */
    public void triggerCritFlash() {
        critFlashTimeRemaining = EffectConstants.CRIT_FLASH_DURATION_SECONDS;
    }

    // -------------------------------------------------------------------------
    // Ring pulse spawn — called from AbilityFeedback
    // -------------------------------------------------------------------------

    /**
     * Spawns a colored ring-pulse accent at the given enemy tile.
     * Screen position is projected from tile coords at spawn time using the
     * current player state; callers must ensure setPlayerState() was called
     * this frame before invoking this.
     * If tileColumn or tileRow is negative, or the tile is behind the player,
     * the spawn is silently skipped.
     */
    public void spawnColoredRingPulse(int tileColumn, int tileRow, float heightMultiplier,
                                       float red, float green, float blue) {
        if (tileColumn < 0 || tileRow < 0) return;

        float worldX  = tileColumn * Constants.CELL_SIZE + Constants.CELL_SIZE * 0.5f;
        float worldY  = tileRow    * Constants.CELL_SIZE + Constants.CELL_SIZE * 0.5f;
        float screenX = projectToScreenX(worldX, worldY);
        if (screenX <= -500f) return;
        float screenY = projectToScreenY(worldX, worldY, heightMultiplier);

        int slot = findFreeRingPulseSlot();
        if (slot < 0) return;

        ringPulseScreenX  [slot] = screenX;
        ringPulseScreenY  [slot] = screenY;
        ringPulseAge      [slot] = 0f;
        ringPulseLife     [slot] = EffectConstants.RING_PULSE_LIFE_SECONDS;
        ringPulseMaxRadius[slot] = EffectConstants.RING_PULSE_MAX_RADIUS;
        ringPulseRed      [slot] = red;
        ringPulseGreen    [slot] = green;
        ringPulseBlue     [slot] = blue;
        ringPulseActive   [slot] = true;
    }

    // -------------------------------------------------------------------------
    // Per-frame update — called by World each frame
    // -------------------------------------------------------------------------

    public void update(float deltaTime) {
        updateShake(deltaTime);
        updateKillFlash(deltaTime);
        updateCritFlash(deltaTime);
        updateParticles(deltaTime);
        updateBursts(deltaTime);
        updateNumbers(deltaTime);
        updateRingPulses(deltaTime);
    }

    private void updateShake(float deltaTime) {
        if (shakeTimeRemaining <= 0f) {
            shakeOffsetX = 0f;
            shakeOffsetY = 0f;
            return;
        }
        shakeTimeRemaining -= deltaTime;
        if (shakeTimeRemaining < 0f) shakeTimeRemaining = 0f;

        float decayFraction = shakeDuration > 0f ? shakeTimeRemaining / shakeDuration : 0f;
        float magnitude     = shakeMagnitude * decayFraction;
        // Random direction each frame produces the characteristic stutter feel
        float angleRadians  = random.nextFloat() * MathUtils_PI2;
        shakeOffsetX = (float) Math.cos(angleRadians) * magnitude;
        shakeOffsetY = (float) Math.sin(angleRadians) * magnitude;
    }

    private void updateKillFlash(float deltaTime) {
        if (killFlashTimeRemaining > 0f) {
            killFlashTimeRemaining -= deltaTime;
            if (killFlashTimeRemaining < 0f) killFlashTimeRemaining = 0f;
        }
    }

    private void updateCritFlash(float deltaTime) {
        if (critFlashTimeRemaining > 0f) {
            critFlashTimeRemaining -= deltaTime;
            if (critFlashTimeRemaining < 0f) critFlashTimeRemaining = 0f;
        }
    }

    private void updateParticles(float deltaTime) {
        for (int particleIndex = 0; particleIndex < MAX_PARTICLES; particleIndex++) {
            if (!particleActive[particleIndex]) continue;
            particleAge[particleIndex] += deltaTime;
            if (particleAge[particleIndex] >= particleLife[particleIndex]) {
                particleActive[particleIndex] = false;
                continue;
            }
            // Gravity arc: pull downward in screen space (Y-up → subtract to fall)
            particleVelocityY[particleIndex] -= EffectConstants.HIT_PARTICLE_GRAVITY * deltaTime;
            particleScreenX[particleIndex]   += particleVelocityX[particleIndex] * deltaTime;
            particleScreenY[particleIndex]   += particleVelocityY[particleIndex] * deltaTime;
        }
    }

    private void updateBursts(float deltaTime) {
        for (int burstIndex = 0; burstIndex < MAX_DEATH_BURSTS; burstIndex++) {
            if (!burstActive[burstIndex]) continue;
            burstAge[burstIndex] += deltaTime;
            if (burstAge[burstIndex] >= burstLife[burstIndex]) {
                burstActive[burstIndex] = false;
            }
        }
    }

    private void updateNumbers(float deltaTime) {
        for (int numberIndex = 0; numberIndex < MAX_DAMAGE_NUMBERS; numberIndex++) {
            if (!numberActive[numberIndex]) continue;
            numberAge[numberIndex] += deltaTime;
            if (numberAge[numberIndex] >= numberLife[numberIndex]) {
                numberActive[numberIndex] = false;
            }
        }
    }

    private void updateRingPulses(float deltaTime) {
        for (int pulseIndex = 0; pulseIndex < MAX_RING_PULSES; pulseIndex++) {
            if (!ringPulseActive[pulseIndex]) continue;
            ringPulseAge[pulseIndex] += deltaTime;
            if (ringPulseAge[pulseIndex] >= ringPulseLife[pulseIndex]) {
                ringPulseActive[pulseIndex] = false;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Accessors for World (shake) and ImpactEffectRenderer (drawing)
    // -------------------------------------------------------------------------

    public float getShakeOffsetX() { return shakeOffsetX; }
    public float getShakeOffsetY() { return shakeOffsetY; }

    /*
     * Formula: killFlashAlpha
     * Derivation:
     *   fraction goes from 1 (just triggered) to 0 (expired).
     *   sin(fraction × π) maps that to 0 → 1 → 0, peaking at fraction = 0.5 (midpoint).
     *   Multiplied by KILL_FLASH_MAX_ALPHA to cap the intensity.
     * Edge cases:
     *   fraction = 0 (expired) → sin(0) = 0 → alpha = 0 (correct, no flash).
     */
    public float getKillFlashAlpha() {
        if (killFlashTimeRemaining <= 0f) return 0f;
        float fraction = killFlashTimeRemaining / EffectConstants.KILL_FLASH_DURATION_SECONDS;
        return EffectConstants.KILL_FLASH_MAX_ALPHA * (float) Math.sin(fraction * Math.PI);
    }

    /*
     * Formula: critFlashAlpha
     * Derivation:
     *   Same sin-curve envelope as kill flash; shorter duration and dimmer cap.
     *   fraction goes from 1 (just triggered) to 0 (expired).
     *   sin(fraction × π) peaks at fraction = 0.5.
     * Edge cases:
     *   fraction = 0 (expired) → sin(0) = 0 → alpha = 0 (no flash).
     */
    public float getCritFlashAlpha() {
        if (critFlashTimeRemaining <= 0f) return 0f;
        float fraction = critFlashTimeRemaining / EffectConstants.CRIT_FLASH_DURATION_SECONDS;
        return EffectConstants.CRIT_FLASH_MAX_ALPHA * (float) Math.sin(fraction * Math.PI);
    }

    // Player state accessors for the renderer's per-frame damage-number projection
    float getPlayerWorldX() { return playerWorldX; }
    float getPlayerWorldY() { return playerWorldY; }
    float getDirectionX()   { return directionX; }
    float getDirectionY()   { return directionY; }
    float getPlaneX()       { return planeX; }
    float getPlaneY()       { return planeY; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void triggerShake(float magnitude, float duration) {
        // Only escalate — a kill shake (magnitude 10) should not be overridden by a
        // simultaneous hit shake (magnitude 4) from a second enemy in the same turn
        if (magnitude > shakeMagnitude || shakeTimeRemaining <= 0f) {
            shakeMagnitude     = magnitude;
            shakeDuration      = duration;
            shakeTimeRemaining = duration;
        }
    }

    private void spawnHitParticles(float screenX, float screenY, int count, boolean isKill) {
        for (int particleCount = 0; particleCount < count; particleCount++) {
            int slot = findFreeParticleSlot();
            if (slot < 0) break;

            particleScreenX[slot] = screenX;
            particleScreenY[slot] = screenY;

            float angleRadians = random.nextFloat() * MathUtils_PI2;
            float speed        = EffectConstants.HIT_PARTICLE_SPEED_MIN
                    + random.nextFloat() * (EffectConstants.HIT_PARTICLE_SPEED_MAX - EffectConstants.HIT_PARTICLE_SPEED_MIN);
            particleVelocityX[slot] = (float) Math.cos(angleRadians) * speed;
            particleVelocityY[slot] = (float) Math.sin(angleRadians) * speed;
            particleAge[slot]       = 0f;
            particleLife[slot]      = EffectConstants.HIT_PARTICLE_DURATION_SECONDS;
            particleSize[slot]      = EffectConstants.HIT_PARTICLE_SIZE;
            particleActive[slot]    = true;

            if (isKill) {
                // Kill debris: hot white-orange → cools to orange
                particleRed[slot]   = 1f;
                particleGreen[slot] = 0.75f + random.nextFloat() * 0.25f;
                particleBlue[slot]  = 0.30f + random.nextFloat() * 0.35f;
            } else {
                // Hit sparks: blood red → orange-red
                particleRed[slot]   = 1f;
                particleGreen[slot] = random.nextFloat() * 0.35f;
                particleBlue[slot]  = 0f;
            }
        }
    }

    private void spawnDeathBurst(float screenX, float screenY, float heightMultiplier) {
        int slot = findFreeBurstSlot();
        if (slot < 0) return;

        burstScreenX[slot]   = screenX;
        burstScreenY[slot]   = screenY;
        burstAge[slot]       = 0f;
        burstLife[slot]      = EffectConstants.DEATH_BURST_LIFE_SECONDS;
        burstMaxRadius[slot] = EffectConstants.DEATH_BURST_BASE_RADIUS
                               + EffectConstants.DEATH_BURST_SCALE_PER_HEIGHT * heightMultiplier;
        burstActive[slot]    = true;
    }

    private void spawnDamageNumber(float worldX, float worldY, int amount, boolean isKill) {
        int slot = findFreeNumberSlot();
        if (slot < 0) return;

        numberWorldX[slot]  = worldX;
        numberWorldY[slot]  = worldY;
        numberAge[slot]     = 0f;
        numberLife[slot]    = EffectConstants.DAMAGE_NUMBER_DURATION_SECONDS;
        numberAmount[slot]  = amount;
        numberIsKill[slot]  = isKill;
        numberActive[slot]  = true;
    }

    private int findFreeParticleSlot() {
        for (int particleIndex = 0; particleIndex < MAX_PARTICLES; particleIndex++) {
            if (!particleActive[particleIndex]) return particleIndex;
        }
        return -1;
    }

    private int findFreeBurstSlot() {
        for (int burstIndex = 0; burstIndex < MAX_DEATH_BURSTS; burstIndex++) {
            if (!burstActive[burstIndex]) return burstIndex;
        }
        return -1;
    }

    private int findFreeNumberSlot() {
        for (int numberIndex = 0; numberIndex < MAX_DAMAGE_NUMBERS; numberIndex++) {
            if (!numberActive[numberIndex]) return numberIndex;
        }
        return -1;
    }

    private int findFreeRingPulseSlot() {
        for (int pulseIndex = 0; pulseIndex < MAX_RING_PULSES; pulseIndex++) {
            if (!ringPulseActive[pulseIndex]) return pulseIndex;
        }
        return -1;
    }

    /*
     * Formula: projectToScreenX
     * Derivation:
     *   Converts a world position to the horizontal screen column in [0, WORLD_WIDTH]
     *   using the same camera-plane sprite projection as EnemyRenderer.
     *   tileOffset = (worldPos - playerPos) / CELL_SIZE  (tile-space vector)
     *   depth = tileOffset · direction  (perpendicular camera-plane distance)
     *   If depth ≤ epsilon the point is behind the player; return -1000 (off screen).
     * Edge cases:
     *   Point exactly on the camera plane → depth ≈ 0 → skip (behind guard).
     */
    private float projectToScreenX(float worldX, float worldY) {
        float tileOffsetX = (worldX - playerWorldX) / Constants.CELL_SIZE;
        float tileOffsetY = (worldY - playerWorldY) / Constants.CELL_SIZE;
        float depth = GameMath.spriteDepth(tileOffsetX, tileOffsetY, directionX, directionY);
        if (depth <= RenderConstants.PROP_BEHIND_PLAYER_EPSILON_TILES) return -1000f;
        return GameMath.spriteScreenColumnCenter(tileOffsetX, tileOffsetY,
                directionX, directionY, planeX, planeY,
                RenderConstants.WALL_PROJECTION_SCREEN_WIDTH);
    }

    /*
     * Formula: projectToScreenY
     * Derivation:
     *   The sprite is centred on the horizon (screenHeight / 2).
     *   Sprite top = horizon + (screenHeight / (2 × depth)) × heightMultiplier.
     *   Particles and bursts spawn at the sprite vertical centre (horizon).
     *   The +offset pushes the spawn point to the upper third of the sprite so
     *   debris visually erupts from the torso, not the feet.
     * Edge cases:
     *   depth ≤ 0 guarded in projectToScreenX; callers check the X result first.
     */
    private float projectToScreenY(float worldX, float worldY, float heightMultiplier) {
        float tileOffsetX = (worldX - playerWorldX) / Constants.CELL_SIZE;
        float tileOffsetY = (worldY - playerWorldY) / Constants.CELL_SIZE;
        float depth = GameMath.spriteDepth(tileOffsetX, tileOffsetY, directionX, directionY);
        if (depth <= RenderConstants.PROP_BEHIND_PLAYER_EPSILON_TILES) {
            return RenderConstants.WALL_PROJECTION_SCREEN_HEIGHT / 2f;
        }
        float halfSpriteHeight = (RenderConstants.WALL_PROJECTION_SCREEN_HEIGHT / depth) * heightMultiplier / 2f;
        float horizon = RenderConstants.WALL_PROJECTION_SCREEN_HEIGHT / 2f;
        // Anchor at centre + 40% up the sprite so effects originate from the torso
        return horizon + halfSpriteHeight * 0.4f;
    }

    // PI×2 literal inlined to avoid importing MathUtils just for this constant
    private static final float MathUtils_PI2 = (float)(Math.PI * 2.0);
}
