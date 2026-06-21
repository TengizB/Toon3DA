package ge.tbegvadze.toon3d.util;

/** Visual effect constants — screen shake, kill flash, hit particles, event text, vignettes, ambient lighting. */
public final class EffectConstants {

    private EffectConstants() {}

    // Impact effects — screen shake, hit particles, kill flash, floating damage numbers
    // Hit shake: small jolt confirming contact; duration matches the weapon fire lock
    public static final float HIT_SHAKE_MAGNITUDE           = 4f;
    public static final float HIT_SHAKE_DURATION_SECONDS    = 0.12f;
    // Reference damage for shake scaling: a hit equal to this value delivers HIT_SHAKE_MAGNITUDE.
    // Smaller hits produce less shake (min 0.4×); critical hits can exceed 1.0× up to 1.5×.
    public static final float HIT_SHAKE_REFERENCE_DAMAGE    = 20f;
    // Kill shake: twice as strong and lasting — rewards the kill decisively
    public static final float KILL_SHAKE_MAGNITUDE          = 10f;
    public static final float KILL_SHAKE_DURATION_SECONDS   = 0.25f;
    // Kill flash: bright warm-white rects along all four screen edges; sin-curve fade
    public static final float KILL_FLASH_DURATION_SECONDS   = 0.35f;
    public static final float KILL_FLASH_MAX_ALPHA          = 0.55f;
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
    public static final float DAMAGE_NUMBER_DURATION_SECONDS = 0.65f;
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
    // Persistent low-HP vignette floor: below 25% HP this baseline intensity is always visible
    public static final float LOW_HP_VIGNETTE_FLOOR      = 0.18f;

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

    // ─── Ability Event Feedback — banner tiers, animations, and world accents ───
    // Banner font scales per tier (Tier 1 = no banner; tag suffix only in Phase 2)
    // PROC must exceed LEGACY_FONT_SCALE (1.8f) so ability banners read larger than info text.
    public static final float ABILITY_BANNER_PROC_SCALE          = 2.0f;
    public static final float ABILITY_BANNER_SLAM_SCALE          = 2.6f;
    public static final float ABILITY_BANNER_LEGENDARY_SCALE     = 3.2f;
    // Screen-space Y anchors for each tier band (world units, Y-up)
    public static final float ABILITY_BANNER_PROC_ANCHOR_Y       = 470f;
    public static final float ABILITY_BANNER_SLAM_ANCHOR_Y       = 540f;
    public static final float ABILITY_BANNER_LEGENDARY_ANCHOR_Y  = 400f;
    // Pop-in / punch-in animation durations
    public static final float ABILITY_BANNER_POP_DURATION_SEC    = 0.08f;
    public static final float ABILITY_BANNER_PUNCH_DURATION_SEC  = 0.12f;
    // Overshoot: Tier 3/4 banners punch in from (1 + overshoot)× scale down to 1×
    public static final float ABILITY_BANNER_PUNCH_OVERSHOOT     = 0.6f;
    // Legendary hold: extra opaque dwell time before Tier 4 banners begin to fade
    public static final float ABILITY_BANNER_LEGENDARY_HOLD_SEC  = 0.35f;
    // Minimum screen Y for banners — keeps them above the thumb cluster
    public static final float ABILITY_BANNER_MIN_Y               = 300f;
    // Crit flash: white full-screen edge flash, shorter and dimmer than kill flash
    public static final float CRIT_FLASH_DURATION_SECONDS        = 0.18f;
    public static final float CRIT_FLASH_MAX_ALPHA               = 0.35f;
    // Kill-proc cascade: delay between the kill burst and the proc banner/accent
    public static final float KILL_PROC_CASCADE_DELAY_SECONDS    = 0.15f;
    // Colored ring pulse pool — world-anchored expanding ring on proc hits
    public static final int   RING_PULSE_POOL_SIZE               = 8;
    public static final float RING_PULSE_LIFE_SECONDS            = 0.30f;
    public static final float RING_PULSE_MAX_RADIUS              = 60f;
    // Minimum projected screen X considered visible; values below this mean behind-player sentinel
    public static final float RING_PULSE_VISIBLE_SCREEN_X_MIN   = -500f;
    // Affliction markers (Phase 3 — constants reserved for future implementation)
    public static final int   AFFLICTION_TICK_PARTICLE_COUNT     = 4;
    public static final float AFFLICTION_MARKER_PULSE_HZ         = 2.0f;

    // Heal proc particle feedback — green '+' symbols floating upward when HP is restored
    public static final int   HEAL_PARTICLE_POOL_SIZE            = 20;
    public static final int   HEAL_PARTICLE_COUNT                = 5;
    public static final float HEAL_PARTICLE_LIFE_SECONDS         = 0.85f;
    public static final float HEAL_PARTICLE_RISE_SPEED           = 65f;
    public static final float HEAL_PARTICLE_SPREAD_X             = 32f;
    public static final float HEAL_PARTICLE_FONT_SCALE           = 1.6f;
    public static final float HEAL_PARTICLE_SPAWN_CENTER_X       = 640f;
    public static final float HEAL_PARTICLE_SPAWN_BASE_Y         = 170f;
    public static final float HEAL_PARTICLE_SPAWN_Y_VARIANCE     = 20f;
    public static final float HEAL_PARTICLE_DRIFT_SPEED          = 22f;
    // Green edge vignette flash on heal proc (mirrors red HIT_VIGNETTE for damage)
    public static final float HEAL_VIGNETTE_FADE_SECONDS         = 0.45f;
    public static final float HEAL_VIGNETTE_MAX_ALPHA            = 0.38f;

    // ── Per-tier ability visual feedback ─────────────────────────────────────────
    // TIER_TAG: soft bottom-edge color tick on every passive proc
    public static final float TAG_EDGE_TICK_DURATION_SECONDS    = 0.14f;
    public static final float TAG_EDGE_TICK_MAX_ALPHA           = 0.22f;
    public static final float TAG_EDGE_TICK_THICKNESS           = 70f;
    // TIER_PROC: small colored spark puff at the enemy target
    public static final int   PROC_SPARK_COUNT                  = 6;
    public static final float PROC_SPARK_SPEED_MIN              = 40f;
    public static final float PROC_SPARK_SPEED_MAX              = 95f;
    public static final float PROC_SPARK_LIFE_SECONDS           = 0.30f;
    // TIER_SLAM: micro shake + colored four-edge flash + thicker ring
    // Keep SLAM_SHAKE_MAGNITUDE below HIT_SHAKE_MAGNITUDE (4f) so proc never drowns combat
    public static final float SLAM_SHAKE_MAGNITUDE              = 3.5f;
    public static final float SLAM_SHAKE_DURATION_SECONDS       = 0.10f;
    public static final float SLAM_FLASH_DURATION_SECONDS       = 0.16f;
    public static final float SLAM_FLASH_MAX_ALPHA              = 0.28f;
    public static final float SLAM_RING_PULSE_MAX_RADIUS        = 75f;
    // TIER_LEGENDARY: double rings + spark nova + hard shake + full flash + vignette
    // Keep LEGENDARY_SHAKE_MAGNITUDE just below KILL_SHAKE (10f); LEGENDARY_VIGNETTE_MAX_ALPHA
    // below damage (0.55f) so a gold breath never reads as "I'm hurt"
    public static final float LEGENDARY_RING_INNER_RADIUS       = 70f;
    public static final float LEGENDARY_RING_OUTER_RADIUS       = 130f;
    public static final int   LEGENDARY_NOVA_SPARK_COUNT        = 18;
    public static final float LEGENDARY_NOVA_SPEED_MIN          = 90f;
    public static final float LEGENDARY_NOVA_SPEED_MAX          = 220f;
    public static final float LEGENDARY_NOVA_LIFE_SECONDS       = 0.55f;
    public static final float LEGENDARY_FLASH_DURATION_SECONDS  = 0.22f;
    public static final float LEGENDARY_FLASH_MAX_ALPHA         = 0.45f;
    public static final float LEGENDARY_SHAKE_MAGNITUDE         = 9f;
    public static final float LEGENDARY_SHAKE_DURATION_SECONDS  = 0.22f;
    public static final float LEGENDARY_VIGNETTE_FADE_SECONDS   = 0.9f;
    public static final float LEGENDARY_VIGNETTE_MAX_ALPHA      = 0.40f;

    // Enemy attack status-effect application chances (0..1)
    public static final float MIRE_WRAITH_POISON_CHANCE     = 0.30f;
    public static final float ACID_DRONE_POISON_CHANCE      = 0.75f;

    // Enemy attack animations — purely cosmetic, timer-driven (wall-clock, not turn-driven)
    public static final float ENEMY_ATTACK_ANIM_DURATION_SECONDS  = 0.30f;
    // Melee lunge: sprite grows and slides toward the camera bottom
    public static final float ENEMY_LUNGE_SCALE_BONUS             = 0.35f;   // +35% sprite size at peak
    public static final float ENEMY_LUNGE_DROP_FRACTION           = 0.18f;   // slide down 18% of wall height
    // Ranged recoil kick: sprite nudges away from the player on fire
    public static final float ENEMY_RECOIL_MAX_PIXELS             = 14f;
    // Muzzle flash quad: warm-white overlay at the firing sprite center
    public static final float ENEMY_MUZZLE_FLASH_FRACTION         = 0.45f;   // shown for first 45% of anim
    public static final float ENEMY_MUZZLE_FLASH_SIZE_FRACTION    = 0.30f;   // flash quad is 30% of sprite width
    // Pre-hit telegraph: brief scale+tint pulse on the turn the attack lands
    public static final float ENEMY_TELEGRAPH_DURATION_SECONDS    = 0.30f;
    public static final float ENEMY_TELEGRAPH_SCALE_BONUS         = 0.15f;
    public static final float ENEMY_TELEGRAPH_RIM_ALPHA           = 0.55f;
    // Ranged projectile pool
    public static final int   ENEMY_PROJECTILE_POOL_SIZE          = 16;
    public static final float ENEMY_PROJECTILE_TRAVEL_SECONDS     = 0.22f;
    public static final float ENEMY_PROJECTILE_BASE_SIZE          = 22f;     // world units at 1-tile depth
    public static final float ENEMY_PROJECTILE_BEAM_THICKNESS     = 6f;      // EYE_TYRANT beam
    // ACID_DRONE projectile color (bright acid green)
    public static final float ACID_DRONE_PROJECTILE_R             = 1.0f;
    public static final float ACID_DRONE_PROJECTILE_G             = 0.85f;
    public static final float ACID_DRONE_PROJECTILE_B             = 0.10f;
    // MIRE_WRAITH projectile color (sickly purple-green)
    public static final float MIRE_WRAITH_PROJECTILE_R            = 0.55f;
    public static final float MIRE_WRAITH_PROJECTILE_G            = 0.25f;
    public static final float MIRE_WRAITH_PROJECTILE_B            = 0.60f;
    // EYE_TYRANT beam color (hot red lance)
    public static final float EYE_TYRANT_BEAM_R                   = 1.0f;
    public static final float EYE_TYRANT_BEAM_G                   = 0.15f;
    public static final float EYE_TYRANT_BEAM_B                   = 0.10f;
    // Melee telegraph warning color (deep red)
    public static final float MELEE_TELEGRAPH_R                   = 0.90f;
    public static final float MELEE_TELEGRAPH_G                   = 0.20f;
    public static final float MELEE_TELEGRAPH_B                   = 0.15f;
}
