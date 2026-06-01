package ge.tbegvadze.toon3d.render;

import ge.tbegvadze.toon3d.entity.ImpactEventListener;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.Random;

/**
 * Simulation-only: owns all live impact-effect state and the pre-allocated
 * object pools for particles, death bursts, and floating damage numbers.
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
    static final int MAX_PARTICLES     = Constants.IMPACT_MAX_SIMULTANEOUS_HITS
                                          * Constants.HIT_PARTICLE_COUNT * 2; // ×2 for kill debris
    static final int MAX_DEATH_BURSTS  = Constants.IMPACT_MAX_SIMULTANEOUS_HITS;
    static final int MAX_DAMAGE_NUMBERS = Constants.IMPACT_MAX_SIMULTANEOUS_HITS;

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
        triggerShake(Constants.HIT_SHAKE_MAGNITUDE, Constants.HIT_SHAKE_DURATION_SECONDS);

        float screenX = projectToScreenX(worldX, worldY);
        float screenY = projectToScreenY(worldX, worldY, heightMultiplier);

        spawnHitParticles(screenX, screenY, Constants.HIT_PARTICLE_COUNT, false);
        spawnDamageNumber(worldX, worldY, damageDealt, false);
    }

    @Override
    public void onEnemyKilled(float worldX, float worldY, float heightMultiplier, int killingBlowDamage) {
        triggerShake(Constants.KILL_SHAKE_MAGNITUDE, Constants.KILL_SHAKE_DURATION_SECONDS);
        killFlashTimeRemaining = Constants.KILL_FLASH_DURATION_SECONDS;

        float screenX = projectToScreenX(worldX, worldY);
        float screenY = projectToScreenY(worldX, worldY, heightMultiplier);

        // Kill debris: twice the particle count, hot white-orange colour
        spawnHitParticles(screenX, screenY, Constants.HIT_PARTICLE_COUNT * 2, true);
        spawnDeathBurst(screenX, screenY, heightMultiplier);
        spawnDamageNumber(worldX, worldY, killingBlowDamage, true);
    }

    // -------------------------------------------------------------------------
    // Per-frame update — called by World each frame
    // -------------------------------------------------------------------------

    public void update(float deltaTime) {
        updateShake(deltaTime);
        updateKillFlash(deltaTime);
        updateParticles(deltaTime);
        updateBursts(deltaTime);
        updateNumbers(deltaTime);
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

    private void updateParticles(float deltaTime) {
        for (int particleIndex = 0; particleIndex < MAX_PARTICLES; particleIndex++) {
            if (!particleActive[particleIndex]) continue;
            particleAge[particleIndex] += deltaTime;
            if (particleAge[particleIndex] >= particleLife[particleIndex]) {
                particleActive[particleIndex] = false;
                continue;
            }
            // Gravity arc: pull downward in screen space (Y-up → subtract to fall)
            particleVelocityY[particleIndex] -= Constants.HIT_PARTICLE_GRAVITY * deltaTime;
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
        float fraction = killFlashTimeRemaining / Constants.KILL_FLASH_DURATION_SECONDS;
        return Constants.KILL_FLASH_MAX_ALPHA * (float) Math.sin(fraction * Math.PI);
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
            float speed        = Constants.HIT_PARTICLE_SPEED_MIN
                    + random.nextFloat() * (Constants.HIT_PARTICLE_SPEED_MAX - Constants.HIT_PARTICLE_SPEED_MIN);
            particleVelocityX[slot] = (float) Math.cos(angleRadians) * speed;
            particleVelocityY[slot] = (float) Math.sin(angleRadians) * speed;
            particleAge[slot]       = 0f;
            particleLife[slot]      = Constants.HIT_PARTICLE_DURATION_SECONDS;
            particleSize[slot]      = Constants.HIT_PARTICLE_SIZE;
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
        burstLife[slot]      = Constants.DEATH_BURST_LIFE_SECONDS;
        burstMaxRadius[slot] = Constants.DEATH_BURST_BASE_RADIUS
                               + Constants.DEATH_BURST_SCALE_PER_HEIGHT * heightMultiplier;
        burstActive[slot]    = true;
    }

    private void spawnDamageNumber(float worldX, float worldY, int amount, boolean isKill) {
        int slot = findFreeNumberSlot();
        if (slot < 0) return;

        numberWorldX[slot]  = worldX;
        numberWorldY[slot]  = worldY;
        numberAge[slot]     = 0f;
        numberLife[slot]    = Constants.DAMAGE_NUMBER_DURATION_SECONDS;
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
        if (depth <= Constants.PROP_BEHIND_PLAYER_EPSILON_TILES) return -1000f;
        return GameMath.spriteScreenColumnCenter(tileOffsetX, tileOffsetY,
                directionX, directionY, planeX, planeY,
                Constants.WALL_PROJECTION_SCREEN_WIDTH);
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
        if (depth <= Constants.PROP_BEHIND_PLAYER_EPSILON_TILES) {
            return Constants.WALL_PROJECTION_SCREEN_HEIGHT / 2f;
        }
        float halfSpriteHeight = (Constants.WALL_PROJECTION_SCREEN_HEIGHT / depth) * heightMultiplier / 2f;
        float horizon = Constants.WALL_PROJECTION_SCREEN_HEIGHT / 2f;
        // Anchor at centre + 40% up the sprite so effects originate from the torso
        return horizon + halfSpriteHeight * 0.4f;
    }

    // PI×2 literal inlined to avoid importing MathUtils just for this constant
    private static final float MathUtils_PI2 = (float)(Math.PI * 2.0);
}
