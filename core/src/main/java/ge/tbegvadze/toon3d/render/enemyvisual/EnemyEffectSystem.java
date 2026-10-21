package ge.tbegvadze.toon3d.render.enemyvisual;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;

/**
 * The pooled WORLD-SPACE particles of the animated enemy layer (generated-enemy-art order 3): hit sprays,
 * resisted sparks, death bursts, wounded leaks, status particles, wind-up crackle, projectiles and their
 * trails. Every particle is a point in the world (tile units on the floor plane, plus a height above the
 * floor in wall heights) carrying a generated sprite frame; {@code EnemyRenderer} projects and draws it
 * after the enemy bodies, z-tested against the wall buffer.
 *
 * <p>Rule R4: a fixed pool, pre-allocated; when it is full the OLDEST particle is recycled — never an
 * allocation. The random source is the layer's own (cosmetic only, never the simulation's). Rule R1:
 * nothing here reads or writes simulation state.
 *
 * <p>EMISSION. A burst is fired at a world point with the throwing creature's pixel scale
 * ({@code tilesPerPixel} = heightMultiplier / canvas height) and the viewer's screen-right and toward-viewer
 * directions, so the emitter's canvas-pixel numbers become world velocities once, at spawn.
 */
public final class EnemyEffectSystem {

    /** One particle. Public fields are read by the renderer; only this system writes them. */
    public static final class Particle {
        public boolean active;
        public EnemyEffectSet.Sprite sprite;
        public int     orientation;
        /** Position: floor plane in TILES, height above the floor in wall heights (= tiles). */
        public float   tileX, tileY, height;
        float          velocityX, velocityY, velocityHeight;
        float          gravity;
        float          drag;
        public float   ageSeconds;
        public float   lifeSeconds;
        /** Tiles per canvas pixel of the creature that threw it (the sprite's pixel scale). */
        public float   tilesPerPixel;
        long           sequence;
        // Orbit (the dazed stars): the particle circles (orbitCentre*) instead of flying.
        float          orbitRadiusTiles, orbitAngularSpeed, orbitPhase;
        float          orbitCentreX, orbitCentreY, orbitCentreHeight;
        float          orbitRightX, orbitRightY;
        // A projectile sheds its trail burst on the trail's interval, and fades out as it reaches the viewer.
        public boolean projectile;
        EnemyEffectSet.Burst trailBurst;
        float          trailTimerSeconds;

        /** The sprite frame to draw now. */
        public EnemyEffectSet.SpriteFrame currentFrame() {
            EnemyEffectSet.SpriteFrame[] frames = sprite.frames[orientation];
            int frameIndex;
            if (sprite.looping) {
                frameIndex = (int) (ageSeconds * sprite.framesPerSecond) % frames.length;
            } else {
                frameIndex = Math.min(frames.length - 1, (int) (ageSeconds / lifeSeconds * frames.length));
            }
            return frames[Math.max(0, frameIndex)];
        }
    }

    private final Particle[]  particles = new Particle[EffectConstants.ENEMY_EFFECT_PARTICLE_CAPACITY];
    private final RandomXS128 random    = new RandomXS128(EffectConstants.ENEMY_EFFECT_RANDOM_SEED);
    private long  nextSequence = 0L;
    private float clockSeconds = 0f;

    // The viewer at the last setViewer call: the screen-right and toward-viewer directions spawns use.
    private float rightX = 0f, rightY = -1f;
    private float towardX = -1f, towardY = 0f;

    public EnemyEffectSystem() {
        for (int slotIndex = 0; slotIndex < particles.length; slotIndex++) particles[slotIndex] = new Particle();
    }

    /** The player's facing (unit vector); spawns this frame use it for "right on screen" and "toward the viewer". */
    public void setViewer(float directionX, float directionY) {
        // The camera plane (GameMath.cameraPlaneX/Y) points screen-LEFT, so screen-right is (dirY, -dirX).
        rightX  =  directionY;
        rightY  = -directionX;
        towardX = -directionX;
        towardY = -directionY;
    }

    public float getRightX() {
        return rightX;
    }

    public float getRightY() {
        return rightY;
    }

    /**
     * Fires every emitter of {@code burst} at the world point ({@code tileX}, {@code tileY}, {@code height}),
     * spread over {@code placementRadiusPixels} canvas px of the creature. {@code angleBiasDegrees} rotates
     * every emitter's direction (a recoil-side spray); 0 keeps the recipe's own.
     */
    public void emit(EnemyEffectSet.Burst burst, float tileX, float tileY, float height, float tilesPerPixel,
                     float placementRadiusPixels, float angleBiasDegrees) {
        if (burst == null) return;
        for (EnemyEffectSet.Emitter emitter : burst.emitters) {
            int count = emitter.countMin + (emitter.countMax > emitter.countMin
                    ? random.nextInt(emitter.countMax - emitter.countMin + 1) : 0);
            for (int particleIndex = 0; particleIndex < count; particleIndex++) {
                spawn(emitter, particleIndex, count, tileX, tileY, height, tilesPerPixel,
                        placementRadiusPixels, angleBiasDegrees);
            }
        }
    }

    /**
     * A projectile: the burst's first emitter's sprite flying in a straight line from the start point to the
     * target point over {@code travelSeconds}, shedding {@code trail} on its interval.
     */
    public void launch(EnemyEffectSet.Burst projectile, EnemyEffectSet.Burst trail,
                       float fromTileX, float fromTileY, float fromHeight,
                       float toTileX, float toTileY, float toHeight, float travelSeconds, float tilesPerPixel) {
        if (projectile == null || projectile.emitters.length == 0 || travelSeconds <= 0f) return;
        Particle particle = acquire();
        reset(particle, projectile.emitters[0].sprite, fromTileX, fromTileY, fromHeight, tilesPerPixel, travelSeconds);
        particle.velocityX      = (toTileX - fromTileX) / travelSeconds;
        particle.velocityY      = (toTileY - fromTileY) / travelSeconds;
        particle.velocityHeight = (toHeight - fromHeight) / travelSeconds;
        particle.trailBurst     = trail;
        particle.trailTimerSeconds = 0f;
        particle.projectile     = true;
    }

    /** Advances every particle; ages out the finished ones. Call from the update path (frozen with the world). */
    public void update(float deltaTime) {
        clockSeconds += deltaTime;
        for (Particle particle : particles) {
            if (!particle.active) continue;
            particle.ageSeconds += deltaTime;
            if (particle.ageSeconds >= particle.lifeSeconds) {
                particle.active = false;
                particle.trailBurst = null;
                continue;
            }
            if (particle.orbitRadiusTiles > 0f) {
                float angleRadians = particle.orbitPhase + particle.orbitAngularSpeed * particle.ageSeconds;
                float across = MathUtils.cos(angleRadians) * particle.orbitRadiusTiles;
                particle.tileX  = particle.orbitCentreX + particle.orbitRightX * across;
                particle.tileY  = particle.orbitCentreY + particle.orbitRightY * across;
                particle.height = particle.orbitCentreHeight + MathUtils.sin(angleRadians) * particle.orbitRadiusTiles
                        * EffectConstants.ENEMY_EFFECT_ORBIT_FLATTEN;
                continue;
            }
            if (particle.drag > 0f) {
                float keep = (float) Math.exp(-particle.drag * deltaTime);
                particle.velocityX      *= keep;
                particle.velocityY      *= keep;
                particle.velocityHeight *= keep;
            }
            particle.velocityHeight -= particle.gravity * deltaTime;
            particle.tileX  += particle.velocityX * deltaTime;
            particle.tileY  += particle.velocityY * deltaTime;
            particle.height += particle.velocityHeight * deltaTime;
            if (particle.height < 0f) {
                // Landed: it lies on the floor for the rest of its life (debris settles, it never sinks).
                particle.height = 0f;
                particle.velocityX = particle.velocityY = particle.velocityHeight = 0f;
            }
            if (particle.trailBurst != null) {
                particle.trailTimerSeconds -= deltaTime;
                if (particle.trailTimerSeconds <= 0f) {
                    EnemyEffectSet.Burst trail = particle.trailBurst;
                    particle.trailTimerSeconds = nextInterval(trail);
                    // emit() may recycle this very particle when the pool is full; read its state first.
                    float tileX = particle.tileX, tileY = particle.tileY, height = particle.height;
                    emit(trail, tileX, tileY, height, particle.tilesPerPixel, 0f, 0f);
                }
            }
        }
    }

    /** Seconds until a repeating burst fires again. */
    public float nextInterval(EnemyEffectSet.Burst burst) {
        return MathUtils.lerp(burst.intervalMin, burst.intervalMax, random.nextFloat());
    }

    /** A uniform random in [0, 1) from the layer's cosmetic source. */
    float nextFloat() {
        return random.nextFloat();
    }

    public int capacity() {
        return particles.length;
    }

    public Particle get(int slotIndex) {
        return particles[slotIndex];
    }

    /** Live particle count (tooling and the probe; a linear scan). */
    public int activeCount() {
        int count = 0;
        for (Particle particle : particles) if (particle.active) count++;
        return count;
    }

    public void clear() {
        for (Particle particle : particles) {
            particle.active = false;
            particle.sprite = null;
            particle.trailBurst = null;
        }
    }

    // ---------------------------------------------------------------------------------------------

    private void spawn(EnemyEffectSet.Emitter emitter, int particleIndex, int count, float tileX, float tileY,
                       float height, float tilesPerPixel, float placementRadiusPixels, float angleBiasDegrees) {
        Particle particle = acquire();
        float lifeSeconds = MathUtils.lerp(emitter.lifeMin, emitter.lifeMax, random.nextFloat());
        // Spawn jitter: the placement's spread across the screen plane plus the emitter's own radius.
        float jitterPixels = placementRadiusPixels + emitter.radius;
        float jitterAcross = (random.nextFloat() * 2f - 1f) * jitterPixels * tilesPerPixel;
        float jitterUp     = (random.nextFloat() * 2f - 1f) * jitterPixels * tilesPerPixel
                * EffectConstants.ENEMY_EFFECT_VERTICAL_JITTER_SCALE;
        float spawnX = tileX + rightX * jitterAcross;
        float spawnY = tileY + rightY * jitterAcross;
        float spawnHeight = Math.max(0f, height + jitterUp);
        reset(particle, emitter.sprite, spawnX, spawnY, spawnHeight, tilesPerPixel, lifeSeconds);

        if (emitter.orbitRadius > 0f && emitter.orbitPeriodSeconds > 0f) {
            particle.orbitRadiusTiles  = emitter.orbitRadius * tilesPerPixel;
            particle.orbitAngularSpeed = MathUtils.PI2 / emitter.orbitPeriodSeconds;
            // Successive waves continue the same rotation, so a held orbit never jumps.
            particle.orbitPhase = particle.orbitAngularSpeed * clockSeconds + particleIndex * MathUtils.PI2 / count;
            particle.orbitCentreX = spawnX;
            particle.orbitCentreY = spawnY;
            particle.orbitCentreHeight = spawnHeight;
            particle.orbitRightX = rightX;
            particle.orbitRightY = rightY;
            return;
        }

        float angleRadians = (emitter.angleDegrees + angleBiasDegrees
                + (random.nextFloat() - 0.5f) * emitter.spreadDegrees) * MathUtils.degreesToRadians;
        float speedPixels  = MathUtils.lerp(emitter.speedMin, emitter.speedMax, random.nextFloat());
        float towardPixels = MathUtils.lerp(emitter.towardMin, emitter.towardMax, random.nextFloat());
        float across = MathUtils.cos(angleRadians) * speedPixels * tilesPerPixel;
        float up     = MathUtils.sin(angleRadians) * speedPixels * tilesPerPixel;
        float toward = towardPixels * tilesPerPixel;
        particle.velocityX      = rightX * across + towardX * toward;
        particle.velocityY      = rightY * across + towardY * toward;
        particle.velocityHeight = up;
        particle.gravity        = emitter.gravity * tilesPerPixel;
        particle.drag           = emitter.drag;
        int orientationCount = emitter.sprite.frames.length;
        if (orientationCount > 1 && speedPixels > 0f) {
            // Pixel art is never rotated: pick the pre-rendered angle nearest the on-screen direction.
            float screenDegrees = MathUtils.atan2(MathUtils.sin(angleRadians), MathUtils.cos(angleRadians))
                    * MathUtils.radiansToDegrees;
            if (screenDegrees < 0f) screenDegrees += 180f;
            particle.orientation = Math.round(screenDegrees / (180f / orientationCount)) % orientationCount;
        }
    }

    private void reset(Particle particle, EnemyEffectSet.Sprite sprite, float tileX, float tileY, float height,
                       float tilesPerPixel, float lifeSeconds) {
        particle.active          = true;
        particle.sprite          = sprite;
        particle.orientation     = 0;
        particle.tileX           = tileX;
        particle.tileY           = tileY;
        particle.height          = height;
        particle.velocityX       = 0f;
        particle.velocityY       = 0f;
        particle.velocityHeight  = 0f;
        particle.gravity         = 0f;
        particle.drag            = 0f;
        particle.ageSeconds      = 0f;
        particle.lifeSeconds     = Math.max(lifeSeconds, EffectConstants.ENEMY_EFFECT_MIN_LIFE_SECONDS);
        particle.tilesPerPixel   = tilesPerPixel;
        particle.orbitRadiusTiles = 0f;
        particle.trailBurst      = null;
        particle.projectile      = false;
        particle.sequence        = nextSequence++;
    }

    /** A free slot, else the OLDEST live particle (R4). */
    private Particle acquire() {
        Particle oldest = null;
        for (Particle particle : particles) {
            if (!particle.active) return particle;
            if (oldest == null || particle.sequence < oldest.sequence) oldest = particle;
        }
        return oldest;
    }

    /** World units -> tiles, for callers that hold world coordinates. */
    public static float toTiles(float worldUnits) {
        return worldUnits / Constants.CELL_SIZE;
    }
}
