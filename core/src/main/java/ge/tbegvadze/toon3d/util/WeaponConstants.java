package ge.tbegvadze.toon3d.util;

/** Weapon system constants — timing, per-weapon stats, HUD rendering, fire effects. */
public final class WeaponConstants {

    private WeaponConstants() {}

    // Weapon system — timing
    // PLAYER_FIRE_DURATION: how long the fire action locks input (slightly heavier than a step)
    public static final float PLAYER_FIRE_DURATION              = 0.14f;
    // FIRE_FLASH_DURATION: real-time muzzle-flash pose duration; cosmetic only
    public static final float FIRE_FLASH_DURATION               = 0.22f;
    // NORMAL_TO_RELOAD_DELAY: how long the normal pose is held after the fire flash
    // before the reload pose begins; lets the player see the weapon lower to idle first
    public static final float NORMAL_TO_RELOAD_DELAY_SECONDS    = 0.18f;
    // DAMAGE_MIN_MULTIPLIER: damage floor at extreme range; prevents a dead zone
    public static final float DAMAGE_MIN_MULTIPLIER             = 0.15f;

    // Shotgun stats
    public static final int     SHOTGUN_DAMAGE             = 50;
    public static final int     SHOTGUN_CLIP_SIZE          = 1;
    public static final int     SHOTGUN_RELOAD_TIME_TICKS  = 1;
    public static final float   SHOTGUN_DAMAGE_DROP_COEFF  = 0.18f;
    public static final int     SHOTGUN_RANGE_TILES        = 5;
    // SHOTGUN_PENETRATION: false = stops at first enemy (v1); true = pierces (future)
    public static final boolean SHOTGUN_PENETRATION        = false;

    // Shotgun HUD textures
    public static final String SHOTGUN_NORMAL_TEXTURE_PATH = "textures/guns/shotgun/shotgun.png";
    public static final String SHOTGUN_FIRE_TEXTURE_PATH   = "textures/guns/shotgun/shotgun_fire.png";
    public static final String SHOTGUN_RELOAD_TEXTURE_PATH = "textures/guns/shotgun/shotgun_reload.png";
    // Shotgun procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer
    public static final int SHOTGUN_CANVAS_WIDTH  = 192;
    public static final int SHOTGUN_CANVAS_HEIGHT = 134;

    // Double-Barrel Shotgun stats — higher burst damage, shorter range, 2-shot clip, slower reload
    // Damage table (coefficient 0.22, floor 0.15):
    //   distance 1: 32 × 0.78 = 25   distance 2: 32 × 0.56 = 18
    //   distance 3: 32 × 0.34 = 11   distance 4: 32 × 0.15 =  5   (clamped by floor)
    public static final int     DBL_SHOTGUN_DAMAGE             = 32;
    public static final int     DBL_SHOTGUN_CLIP_SIZE          = 2;
    public static final int     DBL_SHOTGUN_RELOAD_TIME_TICKS  = 1;
    public static final float   DBL_SHOTGUN_DAMAGE_DROP_COEFF  = 0.22f;
    public static final int     DBL_SHOTGUN_RANGE_TILES        = 4;
    // DBL_SHOTGUN_PENETRATION: false = stops at first enemy (spread dissipates on first target)
    public static final boolean DBL_SHOTGUN_PENETRATION        = false;

    // Double-Barrel Shotgun HUD textures — always procedural (no asset files)
    public static final String DBL_SHOTGUN_NORMAL_TEXTURE_PATH = "textures/guns/dbl_shotgun/dbl_shotgun.png";
    public static final String DBL_SHOTGUN_FIRE_TEXTURE_PATH   = "textures/guns/dbl_shotgun/dbl_shotgun_fire.png";
    public static final String DBL_SHOTGUN_RELOAD_TEXTURE_PATH = "textures/guns/dbl_shotgun/dbl_shotgun_reload.png";
    // Double-Barrel Shotgun procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer
    public static final int DBL_SHOTGUN_CANVAS_WIDTH  = 192;
    public static final int DBL_SHOTGUN_CANVAS_HEIGHT = 134;
    // Display name shown in the HUD ammo readout
    public static final String DBL_SHOTGUN_DISPLAY_NAME = "DBL SHOTGUN";

    // Plasma Rifle stats — high clip, long range, lower per-shot damage, moderate drop
    // Damage table (coefficient 0.10, floor 0.15):
    //   distance 1: 18 × 0.90 = 16   distance 4: 18 × 0.60 = 11
    //   distance 6: 18 × 0.40 =  7   distance 8: 18 × 0.20 =  4
    public static final int   PLASMA_RIFLE_DAMAGE             = 18;
    public static final int   PLASMA_RIFLE_CLIP_SIZE          = 4;
    public static final int   PLASMA_RIFLE_RELOAD_TIME_TICKS  = 1;
    public static final float PLASMA_RIFLE_DAMAGE_DROP_COEFF  = 0.10f;
    public static final int   PLASMA_RIFLE_RANGE_TILES        = 8;
    // PLASMA_RIFLE_PENETRATION: true = shot pierces all enemies in a line
    public static final boolean PLASMA_RIFLE_PENETRATION      = true;

    // Plasma Rifle HUD textures — always procedural (no asset files)
    public static final String PLASMA_RIFLE_NORMAL_TEXTURE_PATH = "textures/guns/plasma/plasma_normal.png";
    public static final String PLASMA_RIFLE_FIRE_TEXTURE_PATH   = "textures/guns/plasma/plasma_fire.png";
    public static final String PLASMA_RIFLE_RELOAD_TEXTURE_PATH = "textures/guns/plasma/plasma_reload.png";

    // Plasma rifle procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer
    public static final int PLASMA_RIFLE_CANVAS_WIDTH  = 192;
    public static final int PLASMA_RIFLE_CANVAS_HEIGHT = 134;

    // Chaingun — triple-barrel rotary weapon, sustained fire, medium range
    public static final String CHAINGUN_DISPLAY_NAME        = "CHAINGUN";
    // Chaingun stats — rapid-fire rotary weapon, 8-shot clip, 3-tick reload, medium range
    // Damage table (coefficient 0.10, floor 0.15):
    //   distance 1: 10 × 0.90 = 9    distance 2: 10 × 0.80 = 8
    //   distance 4: 10 × 0.60 = 6    distance 6: 10 × 0.40 = 4
    //   distance 8: 10 × 0.20 = 2    (minimum floor applies at extreme range)
    public static final int     CHAINGUN_DAMAGE             = 10;
    // 8 bursts × 3 shots = 24 total rounds per clip
    public static final int     CHAINGUN_CLIP_SIZE          = 24;
    // Each press of fire launches CHAINGUN_BURST_SIZE simultaneous bullets in one volley
    public static final int     CHAINGUN_BURST_SIZE         = 3;
    public static final int     CHAINGUN_RELOAD_TIME_TICKS  = 1;
    public static final float   CHAINGUN_DAMAGE_DROP_COEFF  = 0.10f;
    public static final int     CHAINGUN_RANGE_TILES        = 8;
    // CHAINGUN_PENETRATION: false = stops at first enemy (bullets are stopped by armour)
    public static final boolean CHAINGUN_PENETRATION        = false;

    // Chaingun HUD textures — always procedural (no asset files)
    public static final String CHAINGUN_NORMAL_TEXTURE_PATH = "textures/guns/chaingun/chaingun.png";
    public static final String CHAINGUN_FIRE_TEXTURE_PATH   = "textures/guns/chaingun/chaingun_fire.png";
    public static final String CHAINGUN_RELOAD_TEXTURE_PATH = "textures/guns/chaingun/chaingun_reload.png";
    // Chaingun procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer
    public static final int CHAINGUN_CANVAS_WIDTH  = 192;
    public static final int CHAINGUN_CANVAS_HEIGHT = 134;

    // Plasma muzzle blast — blue-cyan sphere burst; replaces the shotgun orange flame
    public static final float PLASMA_BLAST_RADIUS = 85f;

    // Per-weapon fire effects — world-unit dimensions used by WeaponHudRenderer render*Effect methods
    // Shotgun — wide short burst; horizontal disc is the primary element
    public static final float SHOTGUN_EFFECT_FLAME_HEIGHT     = 65f;
    public static final float SHOTGUN_EFFECT_FLAME_BASE_WIDTH = 190f;
    public static final float SHOTGUN_EFFECT_DISC_HALF_WIDTH  = 100f;
    public static final float SHOTGUN_EFFECT_DISC_HALF_HEIGHT = 28f;
    // Double-Barrel Shotgun — two separate side-by-side flame tongues
    public static final float DBL_SHOTGUN_EFFECT_FLAME_HEIGHT      = 72f;
    public static final float DBL_SHOTGUN_EFFECT_TONGUE_BASE_WIDTH = 68f;
    public static final float DBL_SHOTGUN_EFFECT_TONGUE_OFFSET_X   = 55f; // offset of each tongue centre from barrel centre
    // Chaingun — tight narrow cone plus scattered spark dots
    public static final float CHAINGUN_EFFECT_CONE_HEIGHT     = 90f;
    public static final float CHAINGUN_EFFECT_CONE_BASE_WIDTH = 60f;
    public static final int   CHAINGUN_EFFECT_SPARK_COUNT     = 10;
    public static final float CHAINGUN_EFFECT_SPARK_SPREAD_X  = 70f;  // half-width of spark scatter
    public static final float CHAINGUN_EFFECT_SPARK_SPREAD_Y  = 80f;  // height of spark scatter
    public static final float CHAINGUN_EFFECT_SPARK_SIZE      = 5f;   // world units per spark square side
    // Railgun — electric lance: no flame; narrow bright-white bolt with crackling side arcs
    public static final float RAILGUN_EFFECT_LANCE_HEIGHT    = 220f;
    public static final float RAILGUN_EFFECT_LANCE_BASE_WIDTH = 18f;
    public static final float RAILGUN_EFFECT_ARC_SPREAD      = 60f;   // horizontal reach of side arcs
    public static final float RAILGUN_EFFECT_ARC_HEIGHT      = 140f;  // vertical height of side arcs
    // Incinerator — large wide lingering flame; slower shrink so it fills more space
    public static final float INCINERATOR_EFFECT_FLAME_HEIGHT     = 130f;
    public static final float INCINERATOR_EFFECT_FLAME_BASE_WIDTH = 220f;
    public static final float INCINERATOR_EFFECT_SHRINK_RATE      = 0.25f; // slower than default 0.55
    // Grenade Launcher — grey-white smoke puff outer ring + orange core explosion + rising wisps
    public static final float GRENADE_EFFECT_PUFF_RADIUS     = 90f;
    public static final float GRENADE_EFFECT_CORE_RADIUS     = 55f;
    public static final float GRENADE_EFFECT_WISP_HEIGHT     = 100f;
    public static final float GRENADE_EFFECT_WISP_BASE_WIDTH = 18f;

    // Weapon HUD rendering — sprite anchored at screen bottom-centre
    // drawX = (WORLD_WIDTH - WEAPON_HUD_WIDTH) / 2f; drawY = WEAPON_HUD_BASE_Y
    // Canvas is 192×134; grip occupies Y=0..14 (transparent, cut off below screen edge).
    // Visible height = WEAPON_HUD_HEIGHT + WEAPON_HUD_BASE_Y = 268 − 28 = 240 world units
    //   = exactly 1/3 of WORLD_HEIGHT (720).  Width maintains the 192:134 canvas aspect ratio.
    public static final float WEAPON_HUD_WIDTH  = 384f;
    public static final float WEAPON_HUD_HEIGHT = 268f;
    // Transparent grip area at canvas bottom = (14/134) × 268 ≈ 28 world units.
    // Setting drawY to -28 hides the grip below the screen edge, putting the body flush.
    public static final float WEAPON_HUD_BASE_Y = -28f;
    // Recoil: weapon drops instantly by RECOIL_OFFSET_Y on fire, eases back over FIRE_FLASH_DURATION
    public static final float WEAPON_RECOIL_OFFSET_Y         = 55f;
    // Reload slide: weapon lerps this many world-units downward (mostly off screen) while reloading
    public static final float WEAPON_RELOAD_SLIDE_Y          = 200f;
    // Lerp speed for slide/return animations (higher = snappier)
    public static final float WEAPON_OFFSET_LERP_SPEED       = 14f;
    // Fraction of HUD height where the barrel tip sits in the sprite.
    // 0.92 places the plasma effect origin at the muzzle emitter center of the redesigned sprite.
    public static final float WEAPON_BARREL_TIP_Y_FRACTION = 0.92f;
    // Flame cone dimensions in world units
    public static final float WEAPON_FLAME_HEIGHT     = 80f;
    public static final float WEAPON_FLAME_BASE_WIDTH =  160f;

    // Railgun — charge-up infinite-pierce hitscan sniper (SLUGS ammo)
    // Damage table (coefficient 0.02, floor 0.70):
    //   charge 1 (half), distance 1: 40 × 1.00 = 40
    //   charge 1 (half), distance 16: 40 × max(0.70, 1 - 0.02×15) = 40 × 0.70 = 28
    //   charge 2 (full), distance 1: 90 × 1.00 = 90
    //   charge 2 (full), distance 16: 90 × 0.70 = 63
    public static final int[]   RAILGUN_DAMAGE_BY_CHARGE          = {0, 40, 90};
    public static final int     RAILGUN_MAX_CHARGE                = 2;
    public static final float   RAILGUN_DROP_COEFF                = 0.02f;
    public static final float   RAILGUN_DAMAGE_MIN_MULTIPLIER     = 0.70f;
    public static final int     RAILGUN_RANGE_TILES               = 16;
    public static final int     RAILGUN_CLIP_SIZE                 = 1;
    public static final int     RAILGUN_RELOAD_TIME_TICKS         = 2;
    public static final int     RAILGUN_PICKUP_SLUGS              = 4;
    public static final int     RAILGUN_MAX_SLUGS                 = 12;
    public static final boolean RAILGUN_PENETRATION               = true;
    public static final float   RAILGUN_BEAM_DURATION             = 0.14f;
    public static final float   RAILGUN_SHAKE_INTENSITY           = 10f;
    public static final float   RAILGUN_SCREEN_FLASH_ALPHA        = 0.45f;
    public static final int     RAILGUN_CANVAS_WIDTH              = 192;
    public static final int     RAILGUN_CANVAS_HEIGHT             = 134;
    public static final String  RAILGUN_NORMAL_TEXTURE_PATH       = "textures/guns/railgun/railgun.png";
    public static final String  RAILGUN_FIRE_TEXTURE_PATH         = "textures/guns/railgun/railgun_fire.png";
    public static final String  RAILGUN_RELOAD_TEXTURE_PATH       = "textures/guns/railgun/railgun_reload.png";

    // Incinerator — short-range cone flamethrower (FUEL ammo)
    // Impact damage applied to every enemy in the cone on each spray.
    // Depth-3 (far-edge) tiles use FLAME_FALLOFF instead of FLAME_IMPACT_DAMAGE.
    // FLAME_DAMAGE_DROP_COEFF = 0.0: depth falloff is handled explicitly, not by the drop curve.
    // Burn DoT constants (FLAME_BURN_*) are reserved for the enemy system when implemented.
    public static final int     FLAME_IMPACT_DAMAGE        = 8;
    public static final int     FLAME_FALLOFF              = 5;
    public static final int     FLAME_BURN_DAMAGE_PER_TURN = 6;
    public static final int     FLAME_BURN_TURNS           = 4;
    public static final float   FLAME_DAMAGE_DROP_COEFF    = 0.0f;
    public static final int     FLAME_RANGE_TILES          = 3;
    public static final int     FLAME_CLIP_SIZE            = 30;
    public static final int     FUEL_PER_SHOT              = 3;
    public static final int     FLAME_RELOAD_TICKS         = 1;
    public static final int     FLAME_PICKUP_FUEL          = 60;
    public static final int     FLAME_MAX_FUEL             = 120;
    public static final float   FLAME_SHAKE_INTENSITY      = 4f;
    public static final float   FLAME_SCREEN_GLOW_ALPHA    = 0.30f;
    // Incinerator procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer
    public static final int     FLAME_CANVAS_WIDTH         = 192;
    public static final int     FLAME_CANVAS_HEIGHT        = 134;
    // Incinerator HUD textures — always procedural (no asset files)
    public static final String  FLAME_NORMAL_TEXTURE_PATH  = "textures/guns/incinerator/incinerator.png";
    public static final String  FLAME_FIRE_TEXTURE_PATH    = "textures/guns/incinerator/incinerator_fire.png";
    public static final String  FLAME_RELOAD_TEXTURE_PATH  = "textures/guns/incinerator/incinerator_reload.png";

    // Grenade Launcher — bouncing indirect-fire AoE splash weapon (GRENADES ammo)
    // Damage table (coefficient 0.0 — no travel falloff; splash is splash):
    //   impact tile:           GRENADE_SPLASH_DAMAGE  = 30 (full blast)
    //   4 orthogonal neighbours: GRENADE_FALLOFF_DAMAGE = 16 (edge of plus)
    //   player self-damage:    GRENADE_SELF_DAMAGE    = 20 (if caught in blast)
    // Per-shot ceiling: 5 enemies in plus = 30 + 4×16 = 94 distributed damage.
    public static final int     GRENADE_SPLASH_DAMAGE      = 30;
    public static final int     GRENADE_FALLOFF_DAMAGE     = 16;
    public static final int     GRENADE_SELF_DAMAGE        = 20;
    public static final float   GRENADE_DAMAGE_DROP_COEFF  = 0.0f;
    public static final int     GRENADE_RANGE_TILES        = 6;
    // GRENADE_ARM_TILES: grenade must travel this many tiles before it can detonate.
    // Prevents point-blank abuse; unarmed grenade passes through enemies harmlessly.
    public static final int     GRENADE_ARM_TILES          = 2;
    public static final int     GRENADE_CLIP_SIZE          = 3;
    public static final int     GRENADE_RELOAD_TIME_TICKS  = 2;
    public static final int     GRENADE_PICKUP_AMMO        = 6;
    public static final int     GRENADE_MAX_AMMO           = 18;
    // GRENADE_PENETRATION is not used by the custom marchShot, kept for API completeness.
    public static final boolean GRENADE_PENETRATION        = false;
    // 5-tile plus splash offsets: {forwardDelta, lateralDelta} relative to impact tile.
    // Index 0 = center (full damage); indices 1-4 = orthogonal neighbours (falloff damage).
    public static final int[][] GRENADE_SPLASH_OFFSETS     = {{0,0},{1,0},{-1,0},{0,1},{0,-1}};
    // Visual effect timings (reserved for ImpactEffectSystem wiring)
    public static final float   GRENADE_BLAST_DURATION     = 0.30f;
    public static final float   GRENADE_SHAKE_INTENSITY    = 8f;
    public static final float   GRENADE_SCREEN_FLASH_ALPHA = 0.35f;
    // Procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer
    public static final int     GRENADE_CANVAS_WIDTH       = 192;
    public static final int     GRENADE_CANVAS_HEIGHT      = 134;
    // Grenade Launcher HUD textures — always procedural (no asset files)
    public static final String  GRENADE_NORMAL_TEXTURE_PATH  = "textures/guns/grenade/grenade.png";
    public static final String  GRENADE_FIRE_TEXTURE_PATH    = "textures/guns/grenade/grenade_fire.png";
    public static final String  GRENADE_RELOAD_TEXTURE_PATH  = "textures/guns/grenade/grenade_reload.png";

    // Weapon Loadout — slot management and HUD strip
    // Only 2 slots available initially; the system supports more via WEAPON_SLOT_COUNT.
    public static final int   WEAPON_SLOT_COUNT             = 2;
    public static final float WEAPON_PICKUP_HEIGHT_FRACTION = 0.30f; // weapon floor pickups sit a bit higher than ammo
    public static final float WEAPON_SLOT_ICON_SIZE         = 44f;   // slot height; width is computed from full HUD width
    public static final float WEAPON_SLOT_ICON_GAP          = 8f;
    // Slots span the full HUD width; SIDE_PADDING is the horizontal margin on each edge.
    public static final float WEAPON_SLOT_STRIP_SIDE_PADDING = 20f;
    public static final float WEAPON_SLOT_STRIP_ORIGIN_Y    = 8f;
    public static final float WEAPON_SWITCH_RAISE_SECONDS   = 0.10f;

    // Weapon ground pickup billboard — bob animation and sprite sheet
    public static final float WEAPON_PICKUP_BOB_SPEED              = 2.2f;   // radians/sec of the sin clock
    public static final float WEAPON_PICKUP_BOB_AMPLITUDE_FRACTION = 0.10f;  // fraction of sprite height
    public static final int   WEAPON_PICKUP_TEXTURE_SIZE           = 64;     // square pixmap edge, px
    public static final float WEAPON_PICKUP_PHASE_STEP             = 0.7f;   // per-weapon phase offset (rad)
}
