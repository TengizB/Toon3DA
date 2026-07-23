package ge.tbegvadze.toon3d.util;

/** Weapon system constants — timing, per-weapon stats, HUD rendering, fire effects. */
public final class WeaponConstants {

    private WeaponConstants() {}

    // Weapon system — timing
    // PLAYER_FIRE_DURATION: how long the fire action locks input (slightly heavier than a step)
    public static final float PLAYER_FIRE_DURATION              = 0.16f;
    // FIRE_FLASH_DURATION: real-time muzzle-flash pose duration; cosmetic only
    public static final float FIRE_FLASH_DURATION               = 0.22f;
    // BURST_VISUAL_FLASH_DURATION: per-round muzzle-flash duration when BURST_FIRE replays the
    // flash once per burst round. Shorter than FIRE_FLASH_DURATION so a full burst reads as a
    // rapid staccato rather than one long flash. Purely cosmetic — game logic resolves the whole
    // burst in a single turn inside Weapon.fire(); these flashes are staggered over real time.
    public static final float BURST_VISUAL_FLASH_DURATION       = 0.11f;
    // NORMAL_TO_RELOAD_DELAY: how long the normal pose is held after the fire flash
    // before the reload pose begins; lets the player see the weapon lower to idle first
    public static final float NORMAL_TO_RELOAD_DELAY_SECONDS    = 0.10f;
    // DAMAGE_MIN_MULTIPLIER: damage floor at extreme range; prevents a dead zone.
    public static final float DAMAGE_MIN_MULTIPLIER             = BalanceConfig.DAMAGE_MIN_MULTIPLIER;

    // Per-weapon fire shake magnitudes — triggered via ImpactEffectSystem.triggerShake on fire.
    // Small for rapid weapons to avoid nausea on sustained fire; large for slow heavy ones.
    public static final float SHOTGUN_FIRE_SHAKE_MAGNITUDE      = 6f;
    public static final float DBL_SHOTGUN_FIRE_SHAKE_MAGNITUDE  = 9f;
    public static final float PLASMA_FIRE_SHAKE_MAGNITUDE       = 3f;
    public static final float CHAINGUN_FIRE_SHAKE_MAGNITUDE     = 2f;
    public static final float GRENADE_FIRE_SHAKE_MAGNITUDE      = 7f;
    public static final float ASSAULT_RIFLE_FIRE_SHAKE_MAGNITUDE = 4f;
    public static final float ARC_CANNON_FIRE_SHAKE_MAGNITUDE   = 3f;

    // Shotgun stats.
    public static final int     SHOTGUN_DAMAGE             = BalanceConfig.SHOTGUN_DAMAGE;
    public static final int     SHOTGUN_CLIP_SIZE          = BalanceConfig.SHOTGUN_CLIP_SIZE;
    public static final int     SHOTGUN_RELOAD_TIME_TICKS  = BalanceConfig.SHOTGUN_RELOAD_TIME_TICKS;
    public static final float   SHOTGUN_DAMAGE_DROP_COEFF  = BalanceConfig.SHOTGUN_DAMAGE_DROP_COEFF;
    public static final int     SHOTGUN_RANGE_TILES        = BalanceConfig.SHOTGUN_RANGE_TILES;
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
    public static final int     DBL_SHOTGUN_DAMAGE             = BalanceConfig.DBL_SHOTGUN_DAMAGE;
    public static final int     DBL_SHOTGUN_CLIP_SIZE          = BalanceConfig.DBL_SHOTGUN_CLIP_SIZE;
    public static final int     DBL_SHOTGUN_RELOAD_TIME_TICKS  = BalanceConfig.DBL_SHOTGUN_RELOAD_TIME_TICKS;
    public static final float   DBL_SHOTGUN_DAMAGE_DROP_COEFF  = BalanceConfig.DBL_SHOTGUN_DAMAGE_DROP_COEFF;
    public static final int     DBL_SHOTGUN_RANGE_TILES        = BalanceConfig.DBL_SHOTGUN_RANGE_TILES;
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
    public static final int   PLASMA_RIFLE_DAMAGE             = BalanceConfig.PLASMA_RIFLE_DAMAGE;
    public static final int   PLASMA_RIFLE_CLIP_SIZE          = BalanceConfig.PLASMA_RIFLE_CLIP_SIZE;
    public static final int   PLASMA_RIFLE_RELOAD_TIME_TICKS  = BalanceConfig.PLASMA_RIFLE_RELOAD_TIME_TICKS;
    public static final float PLASMA_RIFLE_DAMAGE_DROP_COEFF  = BalanceConfig.PLASMA_RIFLE_DAMAGE_DROP_COEFF;
    public static final int   PLASMA_RIFLE_RANGE_TILES        = BalanceConfig.PLASMA_RIFLE_RANGE_TILES;
    // PLASMA_RIFLE_PENETRATION: true = shot pierces all enemies in a line
    public static final boolean PLASMA_RIFLE_PENETRATION      = true;

    // Plasma Rifle HUD textures — always procedural (no asset files)
    public static final String PLASMA_RIFLE_NORMAL_TEXTURE_PATH = "textures/guns/plasma/plasma_normal.png";
    public static final String PLASMA_RIFLE_FIRE_TEXTURE_PATH   = "textures/guns/plasma/plasma_fire.png";
    public static final String PLASMA_RIFLE_RELOAD_TEXTURE_PATH = "textures/guns/plasma/plasma_reload.png";

    // Plasma rifle procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer.
    // Rendered at 1.5× the base 192×134 for finer panel/coil/emitter detail while preserving the
    // exact 384:268 display aspect ratio (288/201 = 1.4328) so the sprite is never stretched.
    public static final int PLASMA_RIFLE_CANVAS_WIDTH  = 288;
    public static final int PLASMA_RIFLE_CANVAS_HEIGHT = 201;

    // Plasma rifle idle "charged and breathing" pulse — a live ShapeRenderer glow overlay drawn on
    // top of the static sprite each frame while the rifle is equipped and NOT firing/reloading. This
    // is unique to the plasma rifle; no other weapon animates in its resting NORMAL state. The pulse
    // is driven by GameMath.pulseMultiplier(clock, hertz, min, max) — a slow sine breath. Emitter
    // glow modulates alpha + radius; the coil bands shimmer with a per-band phase offset so the
    // energy reads as flowing up the barrel toward the emitter. All values kept low/subtle so the
    // effect is a soft breath, not a distraction, and never fights the firing muzzle burst.
    public static final float PLASMA_RIFLE_IDLE_PULSE_HERTZ        = 0.55f; // breaths per second (slow)
    public static final float PLASMA_RIFLE_IDLE_EMITTER_MIN_ALPHA = 0.16f;
    public static final float PLASMA_RIFLE_IDLE_EMITTER_MAX_ALPHA = 0.46f;
    public static final float PLASMA_RIFLE_IDLE_EMITTER_MIN_RADIUS = 22f;   // world units, emitter halo
    public static final float PLASMA_RIFLE_IDLE_EMITTER_MAX_RADIUS = 40f;
    // Coil shimmer: thin cyan bands across the body/barrel, one per Y fraction of the HUD sprite.
    public static final float   PLASMA_RIFLE_IDLE_COIL_MIN_ALPHA     = 0.08f;
    public static final float   PLASMA_RIFLE_IDLE_COIL_MAX_ALPHA     = 0.30f;
    public static final float   PLASMA_RIFLE_IDLE_COIL_PHASE_OFFSET  = 0.18f; // seconds of clock skew per band
    public static final float   PLASMA_RIFLE_IDLE_COIL_HALF_WIDTH    = 104f;  // world units, half body width
    public static final float   PLASMA_RIFLE_IDLE_COIL_THICKNESS     = 4f;    // world units
    // Coil band centres as fractions of WEAPON_HUD_HEIGHT (body region of the sprite, front-to-back).
    public static final float[] PLASMA_RIFLE_IDLE_COIL_Y_FRACTIONS   = {0.16f, 0.22f, 0.29f, 0.36f, 0.43f};

    // Chaingun — triple-barrel rotary weapon, sustained fire, medium range
    public static final String CHAINGUN_DISPLAY_NAME        = "CHAINGUN";
    // Chaingun stats — rapid-fire rotary weapon, 8-shot clip, 3-tick reload, medium range
    // Damage table (coefficient 0.10, floor 0.15):
    //   distance 1: 10 × 0.90 = 9    distance 2: 10 × 0.80 = 8
    //   distance 4: 10 × 0.60 = 6    distance 6: 10 × 0.40 = 4
    //   distance 8: 10 × 0.20 = 2    (minimum floor applies at extreme range)
    public static final int     CHAINGUN_DAMAGE             = BalanceConfig.CHAINGUN_DAMAGE;
    // 8 bursts × 3 shots = 24 total rounds per clip
    public static final int     CHAINGUN_CLIP_SIZE          = BalanceConfig.CHAINGUN_CLIP_SIZE;
    // Each press of fire launches CHAINGUN_BURST_SIZE simultaneous bullets in one volley
    public static final int     CHAINGUN_BURST_SIZE         = 3;
    public static final int     CHAINGUN_RELOAD_TIME_TICKS  = BalanceConfig.CHAINGUN_RELOAD_TIME_TICKS;
    public static final float   CHAINGUN_DAMAGE_DROP_COEFF  = BalanceConfig.CHAINGUN_DAMAGE_DROP_COEFF;
    public static final int     CHAINGUN_RANGE_TILES        = BalanceConfig.CHAINGUN_RANGE_TILES;
    // CHAINGUN_PENETRATION: false = stops at first enemy (bullets are stopped by armour)
    public static final boolean CHAINGUN_PENETRATION        = false;

    // Chaingun HUD textures — always procedural (no asset files)
    public static final String CHAINGUN_NORMAL_TEXTURE_PATH = "textures/guns/chaingun/chaingun.png";
    public static final String CHAINGUN_FIRE_TEXTURE_PATH   = "textures/guns/chaingun/chaingun_fire.png";
    public static final String CHAINGUN_RELOAD_TEXTURE_PATH = "textures/guns/chaingun/chaingun_reload.png";
    // Chaingun procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer.
    // Rendered at 1.5× the base 192×134 for finer barrel/drum detail while preserving the exact
    // 384:268 display aspect ratio (288/201 = 1.4328) so the sprite is never stretched.
    public static final int CHAINGUN_CANVAS_WIDTH  = 288;
    public static final int CHAINGUN_CANVAS_HEIGHT = 201;

    // Chaingun barrel-spin animation.
    // The six barrels are baked at several rotation phases into one horizontal sprite-sheet
    // texture (CHAINGUN_ROTATION_FRAME_COUNT frames, each CANVAS_WIDTH wide). At run time the
    // renderer only swaps which frame sub-region it samples — no per-frame FrameBuffer work and
    // no allocation. Because six identical barrels sit 60° apart, one 60° step reproduces an
    // identical image, so the baked frames span exactly one CHAINGUN_ROTOR_PERIOD_DEGREES and
    // loop seamlessly. The rotor speed ramps up when firing starts and winds down afterward.
    public static final int   CHAINGUN_ROTOR_BARREL_COUNT               = 6;
    public static final float CHAINGUN_ROTOR_PERIOD_DEGREES             = 60f;   // 360 / barrel count
    public static final int   CHAINGUN_ROTATION_FRAME_COUNT             = 6;     // baked phases per period
    public static final float CHAINGUN_ROTOR_MAX_SPEED_DEGREES_PER_SECOND = 900f;// spun-up rotor speed while firing
    public static final float CHAINGUN_ROTOR_RAMP_RATE                  = 7f;    // spin-up / wind-down lerp rate
    public static final float CHAINGUN_ROTOR_START_ANGLE_DEGREES        = 90f;   // phase-0 (symmetric) arrangement

    // Chaingun sprite layout (canvas pixels, Y-up, offsets relative to centerX). The receiver sits
    // at the bottom nearest the player; the rotor drum above it; the barrels extend away to the top.
    public static final float CHAINGUN_BODY_TOP_Y                = 74f;
    public static final float CHAINGUN_BODY_HALF_WIDTH           = 78f;
    public static final float CHAINGUN_DRUM_CENTER_Y             = 84f;
    public static final float CHAINGUN_DRUM_RADIUS               = 50f;
    public static final float CHAINGUN_CLAMP_BOTTOM_Y            = 126f;
    public static final float CHAINGUN_CLAMP_TOP_Y               = 138f;
    public static final float CHAINGUN_BARREL_BASE_Y            = 108f;  // geometric base (hidden behind the drum)
    public static final float CHAINGUN_BARREL_MUZZLE_Y          = 194f;
    public static final float CHAINGUN_BARREL_CLUSTER_RADIUS_X   = 42f;  // horizontal ring radius at barrel base
    public static final float CHAINGUN_BARREL_CLUSTER_RADIUS_Y   = 9f;   // vertical (depth) foreshortening of the ring
    public static final float CHAINGUN_BARREL_HALF_WIDTH         = 9f;   // half-width of one barrel tube at its base
    public static final float CHAINGUN_BARREL_CONVERGENCE        = 0.66f;// muzzle offset = base offset × this factor
    public static final float CHAINGUN_HUB_BOLT_RADIUS           = 30f;  // orbit radius of the rotor-face bolt studs

    // Assault Rifle — precision automatic bullet rifle, shares BULLETS ammo with the Chaingun.
    // Single accurate hitscan round per fire press; longer effective range, no penetration.
    // Damage table (coefficient 0.08, floor 0.15):
    //   distance 1:  14 × 0.92 = 12   distance 4: 14 × 0.68 = 9
    //   distance 6:  14 × 0.52 =  7   distance 8: 14 × 0.36 = 5
    //   distance 10: 14 × 0.20 =  2   (minimum floor applies at extreme range)
    public static final String  ASSAULT_RIFLE_DISPLAY_NAME      = "ASSAULT RIFLE";
    public static final int     ASSAULT_RIFLE_DAMAGE            = BalanceConfig.ASSAULT_RIFLE_DAMAGE;
    public static final int     ASSAULT_RIFLE_CLIP_SIZE         = BalanceConfig.ASSAULT_RIFLE_CLIP_SIZE;
    public static final int     ASSAULT_RIFLE_RELOAD_TIME_TICKS = BalanceConfig.ASSAULT_RIFLE_RELOAD_TIME_TICKS;
    public static final float   ASSAULT_RIFLE_DAMAGE_DROP_COEFF = BalanceConfig.ASSAULT_RIFLE_DAMAGE_DROP_COEFF;
    public static final int     ASSAULT_RIFLE_RANGE_TILES       = BalanceConfig.ASSAULT_RIFLE_RANGE_TILES;
    // ASSAULT_RIFLE_PENETRATION: false = stops at the first enemy (single bullet, no pierce)
    public static final boolean ASSAULT_RIFLE_PENETRATION       = false;

    // Assault Rifle HUD textures — always procedural (no asset files)
    public static final String ASSAULT_RIFLE_NORMAL_TEXTURE_PATH = "textures/guns/assault_rifle/assault_rifle.png";
    public static final String ASSAULT_RIFLE_FIRE_TEXTURE_PATH   = "textures/guns/assault_rifle/assault_rifle_fire.png";
    public static final String ASSAULT_RIFLE_RELOAD_TEXTURE_PATH = "textures/guns/assault_rifle/assault_rifle_reload.png";
    // Assault Rifle procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer
    public static final int ASSAULT_RIFLE_CANVAS_WIDTH  = 192;
    public static final int ASSAULT_RIFLE_CANVAS_HEIGHT = 134;

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
    // Assault Rifle — crisp four-point muzzle star + ejecting brass casings + thin rising smoke
    public static final float ASSAULT_RIFLE_EFFECT_STAR_RADIUS      = 46f;  // outer reach of the muzzle star points
    public static final float ASSAULT_RIFLE_EFFECT_STAR_CORE_RADIUS = 18f;  // bright central flash disc radius
    public static final int   ASSAULT_RIFLE_EFFECT_CASING_COUNT     = 3;    // brass shells flung out to the side
    public static final float ASSAULT_RIFLE_EFFECT_CASING_SPREAD_X  = 80f;  // horizontal reach of ejected casings (to the right)
    public static final float ASSAULT_RIFLE_EFFECT_CASING_SPREAD_Y  = 55f;  // vertical scatter of ejected casings
    public static final float ASSAULT_RIFLE_EFFECT_CASING_SIZE      = 7f;   // world units per casing rectangle long side
    public static final float ASSAULT_RIFLE_EFFECT_SMOKE_HEIGHT     = 70f;  // height of the rising smoke wisp
    public static final float ASSAULT_RIFLE_EFFECT_SMOKE_WIDTH      = 26f;  // base width of the rising smoke wisp
    // Railgun — electromagnetic discharge: crisp central lance, branching lightning, recoil ring
    public static final float RAILGUN_EFFECT_LANCE_HEIGHT     = 250f;  // taller, sharper central bolt
    public static final float RAILGUN_EFFECT_LANCE_BASE_WIDTH = 14f;   // narrower → crisper lance
    public static final float RAILGUN_EFFECT_ARC_SPREAD       = 70f;   // horizontal reach of branching arcs
    public static final float RAILGUN_EFFECT_ARC_HEIGHT       = 150f;  // vertical height of branching arcs
    // Recoil ring — a thin expanding halo ring punched out at the muzzle on discharge
    public static final float RAILGUN_EFFECT_RING_RADIUS      = 64f;   // outer radius of the recoil ring
    public static final float RAILGUN_EFFECT_RING_THICKNESS   = 9f;    // ring band thickness
    public static final float RAILGUN_EFFECT_RING_FLATTEN     = 0.40f; // vertical squash (top-down ellipse)
    // Lightning branch geometry — number of segments per branching arc and lateral jitter
    public static final int   RAILGUN_EFFECT_BRANCH_SEGMENTS   = 4;    // zig-zag segments per branch
    public static final float RAILGUN_EFFECT_BRANCH_JITTER     = 16f;  // lateral kink magnitude per segment
    public static final float RAILGUN_EFFECT_BOLT_HALF_WIDTH   = 2.2f; // half-thickness of each lightning bolt stroke
    public static final int   RAILGUN_EFFECT_RING_SEGMENTS     = 24;   // perimeter segments of the recoil ring
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
    // Melee hit lunge: weapon rises MELEE_LUNGE_Y above base on swing, returns over FIRE_FLASH_DURATION
    public static final float WEAPON_MELEE_LUNGE_Y           = 30f;
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
    // Balance values (damage, range, falloff, clip, reload, ammo) live in BalanceConfig.
    public static final int[]   RAILGUN_DAMAGE_BY_CHARGE          = BalanceConfig.RAILGUN_DAMAGE_BY_CHARGE;
    public static final int     RAILGUN_MAX_CHARGE                = 2;
    public static final float   RAILGUN_DROP_COEFF                = BalanceConfig.RAILGUN_DROP_COEFF;
    public static final float   RAILGUN_DAMAGE_MIN_MULTIPLIER     = BalanceConfig.RAILGUN_DAMAGE_MIN_MULTIPLIER;
    public static final int     RAILGUN_RANGE_TILES               = BalanceConfig.RAILGUN_RANGE_TILES;
    public static final int     RAILGUN_CLIP_SIZE                 = BalanceConfig.RAILGUN_CLIP_SIZE;
    public static final int     RAILGUN_RELOAD_TIME_TICKS         = BalanceConfig.RAILGUN_RELOAD_TIME_TICKS;
    public static final int     RAILGUN_PICKUP_SLUGS              = BalanceConfig.RAILGUN_PICKUP_SLUGS;
    public static final int     RAILGUN_MAX_SLUGS                 = BalanceConfig.RAILGUN_MAX_SLUGS;
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
    // Burn DoT constants (FLAME_BURN_*) drive the BURNING status applied to every enemy
    // the cone hits — Incinerator.marchShot() calls EnemyHitTarget.applyBurningStatus(),
    // which routes into StatusEffectController (StatusType.BURNING).
    // Balance values (impact/falloff/burn/range/clip/ammo) live in BalanceConfig.
    public static final int     FLAME_IMPACT_DAMAGE        = BalanceConfig.FLAME_IMPACT_DAMAGE;
    public static final int     FLAME_FALLOFF              = BalanceConfig.FLAME_FALLOFF;
    public static final int     FLAME_BURN_DAMAGE_PER_TURN = BalanceConfig.FLAME_BURN_DAMAGE_PER_TURN;
    public static final int     FLAME_BURN_TURNS           = BalanceConfig.FLAME_BURN_TURNS;
    public static final float   FLAME_DAMAGE_DROP_COEFF    = BalanceConfig.FLAME_DAMAGE_DROP_COEFF;
    public static final int     FLAME_RANGE_TILES          = BalanceConfig.FLAME_RANGE_TILES;
    public static final int     FLAME_CLIP_SIZE            = BalanceConfig.FLAME_CLIP_SIZE;
    public static final int     FUEL_PER_SHOT              = 3;
    public static final int     FLAME_RELOAD_TICKS         = BalanceConfig.FLAME_RELOAD_TICKS;
    public static final int     FLAME_PICKUP_FUEL          = BalanceConfig.FLAME_PICKUP_FUEL;
    public static final int     FLAME_MAX_FUEL             = BalanceConfig.FLAME_MAX_FUEL;
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
    //   impact tile:           GRENADE_SPLASH_DAMAGE  = 42 (full blast)
    //   4 orthogonal neighbours: GRENADE_FALLOFF_DAMAGE = 22 (edge of plus)
    //   player self-damage:    GRENADE_SELF_DAMAGE    = 24 (if caught in blast)
    // Per-shot ceiling: 5 enemies in plus = 42 + 4×22 = 130 distributed damage.
    // Balance values (splash/falloff/self damage, range, clip, ammo) live in BalanceConfig.
    public static final int     GRENADE_SPLASH_DAMAGE      = BalanceConfig.GRENADE_SPLASH_DAMAGE;
    public static final int     GRENADE_FALLOFF_DAMAGE     = BalanceConfig.GRENADE_FALLOFF_DAMAGE;
    public static final int     GRENADE_SELF_DAMAGE        = BalanceConfig.GRENADE_SELF_DAMAGE;
    public static final float   GRENADE_DAMAGE_DROP_COEFF  = BalanceConfig.GRENADE_DAMAGE_DROP_COEFF;
    public static final int     GRENADE_RANGE_TILES        = BalanceConfig.GRENADE_RANGE_TILES;
    // GRENADE_ARM_TILES: grenade must travel this many tiles before it can detonate.
    // Prevents point-blank abuse; unarmed grenade passes through enemies harmlessly.
    public static final int     GRENADE_ARM_TILES          = 2;
    public static final int     GRENADE_CLIP_SIZE          = BalanceConfig.GRENADE_CLIP_SIZE;
    public static final int     GRENADE_RELOAD_TIME_TICKS  = BalanceConfig.GRENADE_RELOAD_TIME_TICKS;
    public static final int     GRENADE_PICKUP_AMMO        = BalanceConfig.GRENADE_PICKUP_AMMO;
    public static final int     GRENADE_MAX_AMMO           = BalanceConfig.GRENADE_MAX_AMMO;
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

    // Arc Cannon — chain-lightning energy weapon (CELLS ammo). Primary bolt stops at the first
    // enemy in the facing line; the arc then LEAPS laterally through a cluster (see ArcCannon).
    // Balance values (damage, clip, reload, range, drop, chain) live in BalanceConfig.
    public static final int     ARC_CANNON_DAMAGE                  = BalanceConfig.ARC_CANNON_DAMAGE;
    public static final int     ARC_CANNON_CLIP_SIZE               = BalanceConfig.ARC_CANNON_CLIP_SIZE;
    public static final int     ARC_CANNON_RANGE_TILES             = BalanceConfig.ARC_CANNON_RANGE_TILES;
    public static final float   ARC_CANNON_DAMAGE_DROP_COEFF       = BalanceConfig.ARC_CANNON_DAMAGE_DROP_COEFF;
    public static final int     ARC_CANNON_RELOAD_TIME_TICKS       = BalanceConfig.ARC_CANNON_RELOAD_TIME_TICKS;
    // Primary bolt stops at the first enemy in the line; lateral chaining handles multi-target.
    public static final boolean ARC_CANNON_PENETRATION             = false;
    public static final int     ARC_CANNON_CHAIN_JUMPS             = BalanceConfig.ARC_CANNON_CHAIN_JUMPS;
    public static final float   ARC_CANNON_CHAIN_DAMAGE_MULTIPLIER = BalanceConfig.ARC_CANNON_CHAIN_DAMAGE_MULTIPLIER;
    // Arc Cannon procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer
    public static final int     ARC_CANNON_CANVAS_WIDTH            = 192;
    public static final int     ARC_CANNON_CANVAS_HEIGHT           = 134;
    // Arc Cannon HUD textures — always procedural (no asset files)
    public static final String  ARC_CANNON_NORMAL_TEXTURE_PATH     = "textures/guns/arc_cannon/arc_cannon.png";
    public static final String  ARC_CANNON_FIRE_TEXTURE_PATH       = "textures/guns/arc_cannon/arc_cannon_fire.png";
    public static final String  ARC_CANNON_RELOAD_TEXTURE_PATH     = "textures/guns/arc_cannon/arc_cannon_reload.png";
    // Arc Cannon muzzle discharge effect (world units) — central cyan flash + branching bolts.
    public static final float   ARC_CANNON_EFFECT_CORE_RADIUS      = 60f;  // bright cyan emitter flash radius
    public static final float   ARC_CANNON_EFFECT_BOLT_SPREAD_X    = 120f; // horizontal reach of the branching arcs
    public static final float   ARC_CANNON_EFFECT_BOLT_HEIGHT      = 150f; // vertical reach of the branching arcs
    public static final int     ARC_CANNON_EFFECT_BRANCH_COUNT     = 4;    // branching arcs flung out per discharge
    public static final int     ARC_CANNON_EFFECT_BRANCH_SEGMENTS  = 4;    // zig-zag segments per branch
    public static final float   ARC_CANNON_EFFECT_BRANCH_JITTER    = 22f;  // lateral kink magnitude per segment
    public static final float   ARC_CANNON_EFFECT_BOLT_HALF_WIDTH  = 3.0f; // half-thickness of each lightning stroke

    // Melee weapon procedural canvas — same dimensions as ranged weapons for consistent HUD framing
    public static final int MELEE_CANVAS_WIDTH  = 192;
    public static final int MELEE_CANVAS_HEIGHT = 134;

    // Weapon Loadout — slot management and HUD strip
    // Slots 0 and 1 are active ranged slots; slot 2 is permanently locked until Level 10.
    public static final int   WEAPON_SLOT_COUNT             = 3;
    // Slot index 2 is the locked (future) ranged slot; tryEquip() never assigns here.
    public static final int   WEAPON_GUN_SLOT_LOCKED_INDEX  = 2;
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
    // Fraction of corridor height (floor-to-ceiling) at which the weapon sprite centre hovers.
    // 0.40 = weapon centre is 40% of the way up from the visual floor → 40:60 split (closer to floor).
    public static final float WEAPON_PICKUP_CENTER_HEIGHT_FRACTION = 0.40f;

    // Tier-based glow aura for ground weapon pickups
    public static final int   WEAPON_PICKUP_GLOW_TEXTURE_SIZE      = 64;     // glow pixmap edge, px
    public static final float WEAPON_PICKUP_GLOW_SIZE_MULTIPLIER   = 2.2f;   // glow diameter as multiple of sprite height
    public static final float WEAPON_PICKUP_GLOW_ALPHA             = 0.55f;  // additive glow opacity

    // ── Weapon level scaling ────────────────────────────────────────────────
    public static final int   MAX_WEAPON_LEVEL                      = 10;
    public static final float WEAPON_LEVEL_DAMAGE_PER_LEVEL         = BalanceConfig.WEAPON_LEVEL_DAMAGE_PER_LEVEL;  // +10% per level (balance: BalanceConfig)
    public static final float WEAPON_LEVEL_ACCURACY_PER_LEVEL       = 0.02f;  // +2% per level
    public static final float WEAPON_LEVEL_ACCURACY_MINIMUM         = 0.50f;  // accuracy floor
    public static final float WEAPON_LEVEL_RELOAD_STEP              = 0.15f;  // ticks reduced per level
    public static final int   WEAPON_RELOAD_MIN_TICKS               = 1;
    public static final float WEAPON_LEVEL_CLIP_PER_LEVEL           = 0.08f;  // fraction of base clip per level
    public static final int   WEAPON_LEVEL_RANGE_PER_2_LEVELS       = 1;      // +1 tile every 2 levels
    public static final int   WEAPON_LEVEL_RANGE_MAX_BONUS          = 3;      // cap at +3 tiles

    // Ability clip and reload caps
    public static final int   WEAPON_CLIP_HARD_CAP                  = 99;

    // ── Tier spawn weights (weapon-system-order-11) ────────────────────────
    // Base values at floor 1. Values are relative weights, not percents.
    public static final float TIER_WEIGHT_COMMON_BASE    = 60f;
    public static final float TIER_WEIGHT_UNCOMMON_BASE  = 25f;
    public static final float TIER_WEIGHT_RARE_BASE      = 10f;
    public static final float TIER_WEIGHT_EPIC_BASE      = 4f;
    public static final float TIER_WEIGHT_LEGENDARY_BASE = 1f;

    // Weight drift per floor: weight + drift * (floor - 1), floored at minimum
    public static final float TIER_WEIGHT_COMMON_DRIFT    = -4f;
    public static final float TIER_WEIGHT_UNCOMMON_DRIFT  =  0f;
    public static final float TIER_WEIGHT_RARE_DRIFT      =  2f;
    public static final float TIER_WEIGHT_EPIC_DRIFT      =  1.5f;
    public static final float TIER_WEIGHT_LEGENDARY_DRIFT =  0.5f;

    // Hard floors and caps on computed weights
    public static final float TIER_WEIGHT_COMMON_MINIMUM = 10f;  // never disappears entirely
    public static final float TIER_WEIGHT_LEGENDARY_CAP  = 6f;   // legendaries stay rare

    // ── Ability system constants ────────────────────────────────────────────
    /** Critical hit total damage multiplier: crits deal 2× base damage.
     *  Only the bonus portion (CRIT_DAMAGE_MULTIPLIER - 1) * base is applied
     *  as a second applyDamageTo() call inside AbilityResolver. */
    public static final float CRIT_DAMAGE_MULTIPLIER = BalanceConfig.CRIT_DAMAGE_MULTIPLIER;

    // Per-weapon base accuracy (level-1, COMMON reference values)
    public static final float SHOTGUN_BASE_ACCURACY               = 0.85f;
    public static final float DOUBLE_BARREL_SHOTGUN_BASE_ACCURACY = 0.80f;
    public static final float CHAINGUN_BASE_ACCURACY              = 0.75f;
    public static final float PLASMA_RIFLE_BASE_ACCURACY          = 0.90f;
    public static final float RAILGUN_BASE_ACCURACY               = 0.95f;
    // Short-range cone spray always connects — no aimed accuracy roll (see Incinerator.isPerPelletAccuracy()).
    public static final float INCINERATOR_BASE_ACCURACY           = 1.00f;
    public static final float GRENADE_LAUNCHER_BASE_ACCURACY      = 0.80f;
    public static final float ASSAULT_RIFLE_BASE_ACCURACY         = 0.88f;
    public static final float ARC_CANNON_BASE_ACCURACY            = 0.88f;
    public static final float MELEE_BASE_ACCURACY                 = 1.00f;
}
