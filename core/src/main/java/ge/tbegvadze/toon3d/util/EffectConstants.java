package ge.tbegvadze.toon3d.util;

/** Visual effect constants — screen shake, kill flash, hit particles, event text, vignettes, ambient lighting. */
public final class EffectConstants {

    private EffectConstants() {}

    // Impact effects — screen shake, hit particles, kill flash, floating damage numbers
    // Hit shake: small jolt confirming contact; duration matches the weapon fire lock
    public static final float HIT_SHAKE_MAGNITUDE           = 4f;
    public static final float HIT_SHAKE_DURATION_SECONDS    = 0.12f;
    // Kill shake: twice as strong and lasting — rewards the kill decisively
    public static final float KILL_SHAKE_MAGNITUDE          = 10f;
    public static final float KILL_SHAKE_DURATION_SECONDS   = 0.25f;
    // Kill flash: bright warm-white rects along all four screen edges; sin-curve fade
    public static final float KILL_FLASH_DURATION_SECONDS   = 0.35f;
    public static final float KILL_FLASH_MAX_ALPHA          = 0.70f;
    public static final float KILL_FLASH_EDGE_THICKNESS     = 110f;  // rect depth from each edge
    // Hit particles: square fragments ejecting from the enemy's screen-space position
    public static final int   HIT_PARTICLE_COUNT            = 8;
    public static final float HIT_PARTICLE_DURATION_SECONDS = 0.40f;
    public static final float HIT_PARTICLE_SPEED_MIN        = 60f;   // world units/s
    public static final float HIT_PARTICLE_SPEED_MAX        = 140f;
    public static final float HIT_PARTICLE_SIZE             = 4f;    // square side in world units
    public static final float HIT_PARTICLE_GRAVITY          = 80f;   // world units/s² downward arc
    public static final int   IMPACT_MAX_SIMULTANEOUS_HITS  = 12;    // pre-allocated pool ceiling
    // Death burst: expanding ring of dots + white-hot core at the kill position
    public static final float DEATH_BURST_LIFE_SECONDS      = 0.45f;
    public static final float DEATH_BURST_BASE_RADIUS       = 30f;   // minimum ring radius
    public static final float DEATH_BURST_SCALE_PER_HEIGHT  = 50f;   // radius += heightMultiplier×this
    // Floating damage numbers: "-N" text rising above the enemy on contact
    public static final float DAMAGE_NUMBER_DURATION_SECONDS = 0.80f;
    public static final float DAMAGE_NUMBER_RISE_SPEED       = 55f;  // world units/s upward
    public static final float DAMAGE_NUMBER_FONT_SCALE       = 1.4f;
    // Damage numbers grow above this threshold: scale = base + (amount - threshold + 1) * perPoint
    public static final int   DAMAGE_NUMBER_SCALE_THRESHOLD  = 8;
    public static final float DAMAGE_NUMBER_SCALE_PER_POINT  = 0.04f;
    public static final float DAMAGE_NUMBER_MAX_FONT_SCALE   = 3.0f;

    // Explosive barrel hazard — detonation damage and chain limit
    public static final int   EXPLOSION_DAMAGE    = 12;
    public static final int   EXPLOSION_CHAIN_MAX = 32;

    // Event Text — screen-space rising text for game events
    public static final int   EVENT_TEXT_MAX             = 8;
    public static final float EVENT_TEXT_LIFE_SECONDS    = 1.5f;
    public static final float EVENT_TEXT_RISE_PIXELS     = 70f;
    public static final float EVENT_TEXT_ANCHOR_Y        = 470f;
    public static final float EVENT_TEXT_LINE_STEP       = 34f;

    // Hit Vignette — red screen-edge flash when player takes damage
    public static final float HIT_VIGNETTE_FADE_SECONDS  = 0.6f;
    public static final float HIT_VIGNETTE_MAX_ALPHA     = 0.55f;

    // Level-up Vignette — cyan screen-edge burst played after choosing a level-up card
    public static final float LEVEL_UP_VIGNETTE_FADE_SECONDS = 0.8f;
    public static final float LEVEL_UP_VIGNETTE_MAX_ALPHA    = 0.55f;

    // Status effects — gameplay constants (balance numbers live in GameBalance)
    public static final int   BURN_DAMAGE_PER_TURN           = 4;
    public static final int   BURN_DURATION_MIN              = 3;
    public static final int   BURN_DURATION_MAX              = 5;
    public static final int   POISON_DAMAGE_PER_STACK        = 2;
    public static final int   POISON_MAX_STACKS              = 5;
    public static final int   POISON_DURATION               = 4;
    public static final int   STUN_DURATION_DEFAULT         = 1;
    public static final int   STUN_DURATION_HEAVY           = 2;
    public static final int   BLIND_FOV_DEGREES             = 30;
    public static final int   BLIND_DURATION               = 2;
    public static final float SLOW_FACTOR                   = 2.0f;
    public static final int   SLOW_DURATION                = 3;
    public static final int   EMPOWERED_DAMAGE_PERCENT      = 50;
    public static final int   EMPOWERED_DURATION            = 5;

    // Status effect visual — player screen-edge vignettes and enemy billboard tints
    public static final float STATUS_VIGNETTE_MAX_ALPHA     = 0.22f;
    public static final float STATUS_BLIND_VIGNETTE_ALPHA   = 0.65f;
    public static final float STATUS_VIGNETTE_FADE_IN_SECONDS  = 0.25f;
    public static final float STATUS_VIGNETTE_FADE_OUT_SECONDS = 0.60f;
    public static final float ENEMY_STATUS_TINT_STRENGTH    = 0.35f;

    // Enemy attack status-effect application chances (0..1)
    public static final float MIRE_WRAITH_POISON_CHANCE     = 0.60f;
    public static final float ACID_DRONE_POISON_CHANCE      = 0.75f;
}
