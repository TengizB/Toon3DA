package ge.tbegvadze.toon3d.util;

/** Enemy system constants — textures, stats, AI parameters, hit flash, health bar. */
public final class EnemyConstants {

    private EnemyConstants() {}

    // Enemy sprite sheet textures — two 2x2 grid sheets, each holding 4 enemy types.
    // Blight sheet Q1=PLAGUE_HULK, Q2=EYE_TYRANT, Q3=IRON_STALKER, Q4=MIRE_WRAITH.
    // Infernal sheet Q1=GORE_BITER, Q2=SHELL_BRUTE, Q3=ACID_DRONE, Q4=VOID_SHROUD.
    public static final String  ENEMY_SHEET_BLIGHT_PATH          = "textures/enemies/enemy_sheet_blight.png";
    public static final String  ENEMY_SHEET_INFERNAL_PATH        = "textures/enemies/enemy_sheet_infernal.png";
    // Elemental golem sheet — Q1=RIMESHELL_LANCER, Q2=CINDERFORGE_COLOSSUS, Q3=VERDANT_SPIRESOWER,
    // Q4=AURIC_SENTINEL. Q2 and Q4 have shipped archetypes today; Q1 and Q3 are art waiting on their
    // design docs (.claude/agents/ideas/elemental-golem-*.txt).
    public static final String  ENEMY_SHEET_ELEMENTAL_GOLEMS_PATH = "textures/enemies/enemy_sheet_elemental_golems.png";

    // Necrotic faction — individual single-sprite PNGs (one texture per archetype, drawn
    // full-frame rather than sliced from a 2x2 sheet). EnemyRenderer loads each as its own
    // Texture and maps the whole image as the enemy's TextureRegion.
    public static final String  ENEMY_BLIGHT_CORRUPTOR_PATH      = "textures/enemies/enemy_corruptor.png";
    public static final String  ENEMY_VORTEX_EYE_PATH            = "textures/enemies/enemy_vortex_eye.png";
    public static final String  ENEMY_GHOUL_PATH                 = "textures/enemies/enemy_ghoul.png";
    public static final String  ENEMY_CRAWLER_PATH               = "textures/enemies/enemy_crawler.png";
    public static final String  ENEMY_REVENANT_PATH              = "textures/enemies/enemy_revenant.png";

    // Boss texture — not used in regular combat; reserved for the boss encounter.
    public static final String  ENEMY_BOSS_OVERSEER_PATH         = "textures/enemies/enemy_boss_overseer.png";

    // Enemy system — AI and combat
    // Only cosmetic fields (height/hover) stay here.
    // Probability (0–1) that a killed enemy drops an ammo pickup on its tile.
    public static final float   ENEMY_AMMO_DROP_CHANCE           = BalanceConfig.ENEMY_AMMO_DROP_CHANCE;
    public static final int     ALERT_RADIUS_TILES               = BalanceConfig.ALERT_RADIUS_TILES;
    public static final int     CHAIN_ALERT_RADIUS_TILES         = BalanceConfig.CHAIN_ALERT_RADIUS_TILES;
    public static final int     LOS_MAX_RANGE_TILES              = BalanceConfig.LOS_MAX_RANGE_TILES;

    // PLAGUE_HULK — slow tank melee (spawn '1')
    public static final int     PLAGUE_HULK_MAX_HEALTH           = BalanceConfig.PLAGUE_HULK_MAX_HEALTH;
    public static final int     PLAGUE_HULK_ATTACK_DAMAGE        = BalanceConfig.PLAGUE_HULK_ATTACK_DAMAGE;
    public static final int     PLAGUE_HULK_MOVE_EVERY_N_TURNS   = BalanceConfig.PLAGUE_HULK_MOVE_EVERY_N_TURNS;
    public static final float   PLAGUE_HULK_HEIGHT_MULTIPLIER    = 0.95f;
    // PLAGUE_HULK self-destruct finisher (.claude/agents/ideas/plague-hulk-self-destruct.txt)
    public static final float   PLAGUE_HULK_SELF_DESTRUCT_HP_PERCENT
            = BalanceConfig.PLAGUE_HULK_SELF_DESTRUCT_HP_PERCENT;
    public static final int     PLAGUE_HULK_SELF_DESTRUCT_BRACE_TURNS
            = BalanceConfig.PLAGUE_HULK_SELF_DESTRUCT_BRACE_TURNS;
    public static final int     PLAGUE_HULK_SELF_DESTRUCT_BLAST_RADIUS_TILES
            = BalanceConfig.PLAGUE_HULK_SELF_DESTRUCT_BLAST_RADIUS_TILES;
    public static final float   PLAGUE_HULK_SELF_DESTRUCT_BLAST_DAMAGE_MULTIPLIER
            = BalanceConfig.PLAGUE_HULK_SELF_DESTRUCT_BLAST_DAMAGE_MULTIPLIER;
    public static final int     PLAGUE_HULK_SELF_DESTRUCT_BLAST_DAMAGE_MAX
            = BalanceConfig.PLAGUE_HULK_SELF_DESTRUCT_BLAST_DAMAGE_MAX;
    public static final int     PLAGUE_HULK_SELF_DESTRUCT_TOXIC_RADIUS_TILES
            = BalanceConfig.PLAGUE_HULK_SELF_DESTRUCT_TOXIC_RADIUS_TILES;
    public static final int     PLAGUE_HULK_MINIMAL_TOXIC_RADIUS_TILES
            = BalanceConfig.PLAGUE_HULK_MINIMAL_TOXIC_RADIUS_TILES;

    // EYE_TYRANT — fast ranged kiter (spawn '2'); hover offset keeps it floating
    public static final int     EYE_TYRANT_MAX_HEALTH            = BalanceConfig.EYE_TYRANT_MAX_HEALTH;
    public static final int     EYE_TYRANT_ATTACK_DAMAGE         = BalanceConfig.EYE_TYRANT_ATTACK_DAMAGE;
    public static final int     EYE_TYRANT_RANGE_TILES           = BalanceConfig.EYE_TYRANT_RANGE_TILES;
    public static final float   EYE_TYRANT_HEIGHT_MULTIPLIER     = 0.55f;
    public static final float   EYE_TYRANT_HOVER_OFFSET_FRACTION = 0.25f;

    // GORE_BITER — fast light melee (spawn '3')
    public static final int     GORE_BITER_MAX_HEALTH            = BalanceConfig.GORE_BITER_MAX_HEALTH;
    public static final int     GORE_BITER_ATTACK_DAMAGE         = BalanceConfig.GORE_BITER_ATTACK_DAMAGE;
    public static final int     GORE_BITER_MOVE_EVERY_N_TURNS    = BalanceConfig.GORE_BITER_MOVE_EVERY_N_TURNS;
    public static final float   GORE_BITER_HEIGHT_MULTIPLIER     = 0.85f;

    // SHELL_BRUTE — heavy charger melee (spawn '4')
    public static final int     SHELL_BRUTE_MAX_HEALTH           = BalanceConfig.SHELL_BRUTE_MAX_HEALTH;
    public static final int     SHELL_BRUTE_ATTACK_DAMAGE        = BalanceConfig.SHELL_BRUTE_ATTACK_DAMAGE;
    public static final int     SHELL_BRUTE_MOVE_EVERY_N_TURNS   = BalanceConfig.SHELL_BRUTE_MOVE_EVERY_N_TURNS;
    public static final float   SHELL_BRUTE_HEIGHT_MULTIPLIER    = 1.05f;
    // Charger AI (Pillar 2) — telegraphed rush after a one-turn wind-up.
    public static final float   SHELL_BRUTE_CHARGE_DAMAGE_MULTIPLIER = BalanceConfig.SHELL_BRUTE_CHARGE_DAMAGE_MULTIPLIER;
    public static final int     SHELL_BRUTE_CHARGE_TRIGGER_MIN_TILES = BalanceConfig.SHELL_BRUTE_CHARGE_TRIGGER_MIN_TILES;
    public static final int     SHELL_BRUTE_CHARGE_TRIGGER_MAX_TILES = BalanceConfig.SHELL_BRUTE_CHARGE_TRIGGER_MAX_TILES;
    public static final float   SHELL_BRUTE_CHARGE_STOP_SHORT_CHANCE = BalanceConfig.SHELL_BRUTE_CHARGE_STOP_SHORT_CHANCE;

    // MIRE_WRAITH — slow ground-based ranged acid (spawn '5')
    public static final int     MIRE_WRAITH_MAX_HEALTH           = BalanceConfig.MIRE_WRAITH_MAX_HEALTH;
    public static final int     MIRE_WRAITH_ATTACK_DAMAGE        = BalanceConfig.MIRE_WRAITH_ATTACK_DAMAGE;
    public static final int     MIRE_WRAITH_RANGE_TILES          = BalanceConfig.MIRE_WRAITH_RANGE_TILES;
    public static final int     MIRE_WRAITH_MOVE_EVERY_N_TURNS   = BalanceConfig.MIRE_WRAITH_MOVE_EVERY_N_TURNS;
    public static final float   MIRE_WRAITH_HEIGHT_MULTIPLIER    = 0.80f;

    // IRON_STALKER — armored elite, melee + ranged (spawn '!')
    public static final int     IRON_STALKER_MAX_HEALTH          = BalanceConfig.IRON_STALKER_MAX_HEALTH;
    public static final int     IRON_STALKER_MELEE_DAMAGE        = BalanceConfig.IRON_STALKER_MELEE_DAMAGE;
    public static final int     IRON_STALKER_RANGED_DAMAGE       = BalanceConfig.IRON_STALKER_RANGED_DAMAGE;
    public static final int     IRON_STALKER_RANGE_TILES         = BalanceConfig.IRON_STALKER_RANGE_TILES;
    public static final int     IRON_STALKER_MOVE_EVERY_N_TURNS  = BalanceConfig.IRON_STALKER_MOVE_EVERY_N_TURNS;
    public static final float   IRON_STALKER_HEIGHT_MULTIPLIER   = 1.10f;

    // ACID_DRONE — ranged mechanical (spawn '$')
    public static final int     ACID_DRONE_MAX_HEALTH            = BalanceConfig.ACID_DRONE_MAX_HEALTH;
    public static final int     ACID_DRONE_ATTACK_DAMAGE         = BalanceConfig.ACID_DRONE_ATTACK_DAMAGE;
    public static final int     ACID_DRONE_RANGE_TILES           = BalanceConfig.ACID_DRONE_RANGE_TILES;
    public static final int     ACID_DRONE_MOVE_EVERY_N_TURNS    = BalanceConfig.ACID_DRONE_MOVE_EVERY_N_TURNS;
    public static final float   ACID_DRONE_HEIGHT_MULTIPLIER     = 0.70f;

    // VOID_SHROUD — fast stealth melee FLANKER (spawn '^')
    public static final int     VOID_SHROUD_MAX_HEALTH           = BalanceConfig.VOID_SHROUD_MAX_HEALTH;
    public static final int     VOID_SHROUD_ATTACK_DAMAGE        = BalanceConfig.VOID_SHROUD_ATTACK_DAMAGE;
    public static final int     VOID_SHROUD_MOVE_EVERY_N_TURNS   = BalanceConfig.VOID_SHROUD_MOVE_EVERY_N_TURNS;
    public static final float   VOID_SHROUD_HEIGHT_MULTIPLIER    = 0.80f;
    // Flanker AI (Pillar 2) — prefers the tile behind the player's facing and hits harder there.
    public static final float   VOID_SHROUD_FLANK_DAMAGE_MULTIPLIER = BalanceConfig.VOID_SHROUD_FLANK_DAMAGE_MULTIPLIER;

    // GHOUL — slow shambling melee chaff (spawn '~')
    public static final int     GHOUL_MAX_HEALTH                 = BalanceConfig.GHOUL_MAX_HEALTH;
    public static final int     GHOUL_ATTACK_DAMAGE              = BalanceConfig.GHOUL_ATTACK_DAMAGE;
    public static final int     GHOUL_MOVE_EVERY_N_TURNS         = BalanceConfig.GHOUL_MOVE_EVERY_N_TURNS;
    public static final float   GHOUL_HEIGHT_MULTIPLIER          = 0.90f;

    // CRAWLER — fast, fragile low melee chaff (spawn 'z')
    public static final int     CRAWLER_MAX_HEALTH               = BalanceConfig.CRAWLER_MAX_HEALTH;
    public static final int     CRAWLER_ATTACK_DAMAGE            = BalanceConfig.CRAWLER_ATTACK_DAMAGE;
    public static final int     CRAWLER_MOVE_EVERY_N_TURNS       = BalanceConfig.CRAWLER_MOVE_EVERY_N_TURNS;
    public static final float   CRAWLER_HEIGHT_MULTIPLIER        = 0.55f;

    // REVENANT — fast, hard-hitting undead soldier melee (spawn 'K')
    public static final int     REVENANT_MAX_HEALTH              = BalanceConfig.REVENANT_MAX_HEALTH;
    public static final int     REVENANT_ATTACK_DAMAGE           = BalanceConfig.REVENANT_ATTACK_DAMAGE;
    public static final int     REVENANT_MOVE_EVERY_N_TURNS      = BalanceConfig.REVENANT_MOVE_EVERY_N_TURNS;
    public static final float   REVENANT_HEIGHT_MULTIPLIER       = 1.05f;

    // VORTEX_EYE — short-range ranged chaff caster (spawn 'V'); hovers like the Eye Tyrant
    public static final int     VORTEX_EYE_MAX_HEALTH            = BalanceConfig.VORTEX_EYE_MAX_HEALTH;
    public static final int     VORTEX_EYE_ATTACK_DAMAGE         = BalanceConfig.VORTEX_EYE_ATTACK_DAMAGE;
    public static final int     VORTEX_EYE_RANGE_TILES           = BalanceConfig.VORTEX_EYE_RANGE_TILES;
    public static final int     VORTEX_EYE_MOVE_EVERY_N_TURNS    = BalanceConfig.VORTEX_EYE_MOVE_EVERY_N_TURNS;
    public static final float   VORTEX_EYE_HEIGHT_MULTIPLIER     = 0.55f;

    // BLIGHT_CORRUPTOR — durable slow infected brute soldier melee (spawn '*')
    public static final int     BLIGHT_CORRUPTOR_MAX_HEALTH         = BalanceConfig.BLIGHT_CORRUPTOR_MAX_HEALTH;
    public static final int     BLIGHT_CORRUPTOR_ATTACK_DAMAGE      = BalanceConfig.BLIGHT_CORRUPTOR_ATTACK_DAMAGE;
    public static final int     BLIGHT_CORRUPTOR_MOVE_EVERY_N_TURNS = BalanceConfig.BLIGHT_CORRUPTOR_MOVE_EVERY_N_TURNS;
    public static final float   BLIGHT_CORRUPTOR_HEIGHT_MULTIPLIER  = 1.00f;

    // AURIC_SENTINEL — elemental GOLEM ranged soldier whose orbiting shards are armour AND ammo
    // (spawn '('). Stats mirror BalanceConfig; everything below AURIC_SHARD_ARMOR_POOL is cosmetic.
    public static final int     AURIC_SENTINEL_MAX_HEALTH           = BalanceConfig.AURIC_SENTINEL_MAX_HEALTH;
    public static final int     AURIC_SENTINEL_ATTACK_DAMAGE        = BalanceConfig.AURIC_SENTINEL_ATTACK_DAMAGE;
    public static final int     AURIC_SENTINEL_RANGE_TILES          = BalanceConfig.AURIC_SENTINEL_RANGE_TILES;
    public static final int     AURIC_SENTINEL_MOVE_EVERY_N_TURNS   = BalanceConfig.AURIC_SENTINEL_MOVE_EVERY_N_TURNS;
    public static final int     AURIC_SENTINEL_ATTACK_CADENCE_TURNS = BalanceConfig.AURIC_SENTINEL_ATTACK_CADENCE_TURNS;
    public static final int     AURIC_SENTINEL_MIN_SPAWN_DEPTH      = BalanceConfig.AURIC_SENTINEL_MIN_SPAWN_DEPTH;
    public static final int     AURIC_MAX_SHARDS                    = BalanceConfig.AURIC_MAX_SHARDS;
    public static final int     AURIC_SHARD_REGEN_TURNS             = BalanceConfig.AURIC_SHARD_REGEN_TURNS;
    public static final float   AURIC_SHARD_ARMOR_POOL              = BalanceConfig.AURIC_SHARD_ARMOR_POOL;
    public static final float   AURIC_SENTINEL_HEIGHT_MULTIPLIER    = 1.00f;

    // --- Orbit ring cosmetics (EnemyRenderer). The ring IS the readability contract: the live shard
    // count is legible from across the room with no HUD, no bar and no text, so these values are tuned
    // for "countable at a glance" rather than for spectacle.
    /** Angular speed of the shard ring, radians per wall-clock second. */
    public static final float   AURIC_SHARD_ORBIT_SPEED             = 1.60f;
    /** Ring radius as a fraction of the billboard's on-screen width. */
    public static final float   AURIC_SHARD_ORBIT_RADIUS_FRACTION   = 0.46f;
    /** Shard quad size as a fraction of the billboard's on-screen height. */
    public static final float   AURIC_SHARD_SIZE_FRACTION           = 0.13f;
    /** Height of the ring's centre above the sprite's feet, as a fraction of sprite height. */
    public static final float   AURIC_SHARD_ORBIT_HEIGHT_FRACTION   = 0.76f;
    /** Per-shard vertical bob amplitude, as a fraction of sprite height. */
    public static final float   AURIC_SHARD_BOB_AMPLITUDE           = 0.030f;
    /** Per-shard vertical bob speed, radians per wall-clock second. */
    public static final float   AURIC_SHARD_BOB_SPEED               = 2.40f;
    /** How much smaller a shard draws on the FAR side of the orbit, so the ring reads as 3D (0–1). */
    public static final float   AURIC_SHARD_FAR_SIDE_SIZE_FALLOFF   = 0.35f;
    /** Seconds the surviving shards take to re-space to even angles after one is lost. */
    public static final float   AURIC_SHARD_RESPACE_SECONDS         = 0.25f;
    // Shard body colour (hot gold), drawn additively.
    public static final float   AURIC_SHARD_R                       = 1.00f;
    public static final float   AURIC_SHARD_G                       = 0.82f;
    public static final float   AURIC_SHARD_B                       = 0.28f;
    // ABSORB flash: a bright GOLD ring-flash on the billboard INSTEAD of the usual white/red hit
    // flash. This distinction is the single most important effect on this archetype — without it the
    // Sentinel reads as a bullet sponge instead of a puzzle.
    public static final float   AURIC_ABSORB_FLASH_R                = 1.00f;
    public static final float   AURIC_ABSORB_FLASH_G                = 0.90f;
    public static final float   AURIC_ABSORB_FLASH_B                = 0.45f;
    /** Seconds the gold absorb flash lasts; also the window the ring re-spaces over. */
    public static final float   AURIC_ABSORB_FLASH_DURATION_SECONDS = 0.28f;
    // EXPOSED (shardless) tint: the amber glow drops to a dull violet matte so "it is disarmed RIGHT
    // NOW, hit it" is legible from across the room.
    public static final float   AURIC_EXPOSED_TINT_R                = 0.38f;
    public static final float   AURIC_EXPOSED_TINT_G                = 0.30f;
    public static final float   AURIC_EXPOSED_TINT_B                = 0.46f;
    /** Blend strength of the shardless matte tint (0 = unchanged, 1 = fully violet). */
    public static final float   AURIC_EXPOSED_TINT_STRENGTH         = 0.45f;

    // CINDERFORGE_COLOSSUS — elemental GOLEM melee BRUISER anchor that HARDENS while unharmed and sets
    // the ground behind it on fire (spawn '{'). Stats mirror BalanceConfig; everything below
    // CINDERFORGE_TRAIL_MAX_LIVE_TILES is cosmetic.
    public static final int     CINDERFORGE_COLOSSUS_MAX_HEALTH         = BalanceConfig.CINDERFORGE_COLOSSUS_MAX_HEALTH;
    public static final int     CINDERFORGE_COLOSSUS_ATTACK_DAMAGE      = BalanceConfig.CINDERFORGE_COLOSSUS_ATTACK_DAMAGE;
    public static final int     CINDERFORGE_COLOSSUS_MOVE_EVERY_N_TURNS = BalanceConfig.CINDERFORGE_COLOSSUS_MOVE_EVERY_N_TURNS;
    public static final int     CINDERFORGE_COLOSSUS_MIN_SPAWN_DEPTH    = BalanceConfig.CINDERFORGE_COLOSSUS_MIN_SPAWN_DEPTH;
    public static final int     CINDERFORGE_CRUST_MAX_STACKS            = BalanceConfig.CINDERFORGE_CRUST_MAX_STACKS;
    public static final int     CINDERFORGE_CRUST_ARMOR_PER_STACK       = BalanceConfig.CINDERFORGE_CRUST_ARMOR_PER_STACK;
    public static final float   CINDERFORGE_CRUST_AVERAGED_FLAT_REDUCTION
            = BalanceConfig.CINDERFORGE_CRUST_AVERAGED_FLAT_REDUCTION;
    public static final float   CINDERFORGE_CRUST_SHATTER_MULTIPLIER    = BalanceConfig.CINDERFORGE_CRUST_SHATTER_MULTIPLIER;
    public static final int     CINDERFORGE_TRAIL_FIRE_TURNS            = BalanceConfig.CINDERFORGE_TRAIL_FIRE_TURNS;
    public static final int     CINDERFORGE_TRAIL_MAX_LIVE_TILES        = BalanceConfig.CINDERFORGE_TRAIL_MAX_LIVE_TILES;
    public static final float   CINDERFORGE_COLOSSUS_HEIGHT_MULTIPLIER  = 1.15f;

    // --- SEAM GLOW = SOFTNESS (EnemyRenderer). The sprite's tint is driven by the live crust count, so
    // "how hard is it right now" is answerable by LOOKING at it: full-bright orange at zero stacks,
    // darkened charcoal at a full shell. The Q2 art is already a lattice of dark crust over glowing
    // seams, so the tint just turns up or down what the sprite already says.
    /** Sprite tint at ZERO crust stacks — seams blazing, the "hit me now" state. */
    public static final float   CINDERFORGE_TINT_HOT_R              = 1.00f;
    public static final float   CINDERFORGE_TINT_HOT_G              = 0.62f;
    public static final float   CINDERFORGE_TINT_HOT_B              = 0.26f;
    /** Sprite tint at a FULL crust shell — darkened, desaturated charcoal. */
    public static final float   CINDERFORGE_TINT_COOLED_R           = 0.46f;
    public static final float   CINDERFORGE_TINT_COOLED_G           = 0.32f;
    public static final float   CINDERFORGE_TINT_COOLED_B           = 0.26f;
    /** Blend strength of the hot/cooled tint over the base shade (0 = untinted, 1 = fully tinted). */
    public static final float   CINDERFORGE_TINT_STRENGTH           = 0.55f;
    /**
     * Seconds the tint takes to ease between two crust levels, so a shatter is a visible FLASH back to
     * hot rather than a snap. Cosmetic only — the simulation changes the stack count instantly.
     */
    public static final float   CINDERFORGE_TINT_LERP_SECONDS       = 0.40f;
    /**
     * CRITICAL READABILITY FLOOR. The cooled tint must never be confusable with the
     * DORMANT_SHADE_DAMPEN (0.7) darkening used for a SLEEPING enemy — "hardened" must never read as
     * "asleep". A cooled Colossus therefore keeps a hot orange rim: its red channel is floored at this
     * fraction of full brightness no matter how cool it is, and the additive seam pass below still
     * burns. A dormant enemy has neither.
     */
    public static final float   CINDERFORGE_RIM_MIN_RED             = 0.72f;

    // --- CRUST PLATES: one dark plate per live stack, drawn over the sprite at fixed offsets
    // (shoulders / chest / hips). A literal COUNT of the armour, readable at a glance and at distance —
    // the same discipline as the Auric ring: the stack count is the whole readability contract, and it
    // lives on the body, not in the HUD.
    /** Per-stack plate centre, as a fraction of sprite WIDTH from the billboard centre (index = stack-1). */
    public static final float[] CINDERFORGE_CRUST_PLATE_OFFSET_X    = { -0.20f,  0.20f,  0.00f };
    /** Per-stack plate centre, as a fraction of sprite HEIGHT above the sprite's feet (index = stack-1). */
    public static final float[] CINDERFORGE_CRUST_PLATE_OFFSET_Y    = {  0.74f,  0.74f,  0.46f };
    /** Plate width as a fraction of the billboard's on-screen width. */
    public static final float   CINDERFORGE_CRUST_PLATE_WIDTH_FRACTION  = 0.30f;
    /** Plate height as a fraction of the billboard's on-screen height. */
    public static final float   CINDERFORGE_CRUST_PLATE_HEIGHT_FRACTION = 0.11f;
    /** Plate body colour — cooled black crust. Drawn in ordinary alpha blend so it actually DARKENS. */
    public static final float   CINDERFORGE_CRUST_PLATE_R           = 0.10f;
    public static final float   CINDERFORGE_CRUST_PLATE_G           = 0.07f;
    public static final float   CINDERFORGE_CRUST_PLATE_B           = 0.07f;
    /** Opacity of a crust plate. Below 1 so the sprite's own lattice still shows through. */
    public static final float   CINDERFORGE_CRUST_PLATE_ALPHA       = 0.80f;
    /** Height of the glowing molten seam drawn under each plate, as a fraction of the plate height. */
    public static final float   CINDERFORGE_CRUST_SEAM_HEIGHT_FRACTION = 0.26f;

    // --- HEAT HAZE: a shimmer band at the base of the billboard, sold as a few additive orange slivers
    // whose horizontal offset oscillates. Cheap, allocation-free, and it sells the mass and the
    // temperature without resampling the sprite (this renderer draws one texture column at a time, so a
    // true refraction re-sample has no place to live).
    /** Horizontal shimmer amplitude, as a fraction of the billboard's on-screen width. */
    public static final float   CINDERFORGE_HEAT_HAZE_AMPLITUDE     = 0.055f;
    /** Shimmer oscillation speed, radians per wall-clock second. */
    public static final float   CINDERFORGE_HEAT_HAZE_SPEED         = 3.10f;
    /** Height of the shimmer band above the sprite's feet, as a fraction of sprite height. */
    public static final float   CINDERFORGE_HEAT_HAZE_ZONE_FRACTION = 0.22f;
    /** Number of shimmer slivers drawn in the band. */
    public static final int     CINDERFORGE_HEAT_HAZE_SLIVERS       = 4;
    /** Alpha of each additive shimmer sliver. */
    public static final float   CINDERFORGE_HEAT_HAZE_ALPHA         = 0.30f;

    // RIMESHELL_LANCER — elemental GOLEM ranged SOLDIER: armoured until it fires a piercing molten lance
    // down one cardinal lane (spawn '['). Stats mirror BalanceConfig; everything below the beam-seconds is
    // cosmetic. The sealed ice shell (RIMESHELL_SHELL_ARMOR) is a LIVE per-instance value on Enemy.armor;
    // the type-level flatReduction() below is the TIME-AVERAGED price only — the two are never wired
    // together (.claude/agents/ideas/elemental-golem-rimeshell-lancer.txt).
    public static final int     RIMESHELL_LANCER_MAX_HEALTH         = BalanceConfig.RIMESHELL_LANCER_MAX_HEALTH;
    public static final int     RIMESHELL_LANCER_ATTACK_DAMAGE      = BalanceConfig.RIMESHELL_LANCER_ATTACK_DAMAGE;
    public static final int     RIMESHELL_LANCER_RANGE_TILES        = BalanceConfig.RIMESHELL_LANCER_RANGE_TILES;
    public static final int     RIMESHELL_LANCER_MOVE_EVERY_N_TURNS = BalanceConfig.RIMESHELL_LANCER_MOVE_EVERY_N_TURNS;
    public static final int     RIMESHELL_SHELL_ARMOR               = BalanceConfig.RIMESHELL_SHELL_ARMOR;
    public static final float   RIMESHELL_SHELL_AVERAGED_FLAT_REDUCTION
            = BalanceConfig.RIMESHELL_SHELL_AVERAGED_FLAT_REDUCTION;
    public static final int     RIMESHELL_LANCE_COOLDOWN_TURNS      = BalanceConfig.RIMESHELL_LANCE_COOLDOWN_TURNS;
    public static final float   RIMESHELL_LANCE_FRIENDLY_FIRE_FRACTION
            = BalanceConfig.RIMESHELL_LANCE_FRIENDLY_FIRE_FRACTION;
    public static final float   RIMESHELL_LANCER_HEIGHT_MULTIPLIER  = 0.85f;

    // --- Rimeshell cosmetics (EnemyRenderer). "Cold body, hot core" IS the mechanic, so the sprite must
    // say it at a glance: a slow cyan frost pulse while sealed and patient, a hot orange heat wash the
    // whole turn the shell is open (the punish window), so "hit it NOW" is legible without the intent icon.
    /** Idle frost rim-pulse speed, radians per wall-clock second — slow and patient. */
    public static final float   RIMESHELL_FROST_PULSE_SPEED         = 2.10f;
    /** Peak strength of the idle cyan frost pulse blended over the base shade (small, cold, inert-looking). */
    public static final float   RIMESHELL_FROST_PULSE_AMPLITUDE     = 0.18f;
    /** Cyan frost rim colour, blended over the sealed sprite. */
    public static final float   RIMESHELL_FROST_TINT_R              = 0.55f;
    public static final float   RIMESHELL_FROST_TINT_G              = 0.85f;
    public static final float   RIMESHELL_FROST_TINT_B              = 1.00f;
    /** SHELL-OPEN heat wash — the body visibly "heats" toward orange the turn the lance is charging. */
    public static final float   RIMESHELL_SHELL_OPEN_TINT_R         = 1.00f;
    public static final float   RIMESHELL_SHELL_OPEN_TINT_G         = 0.52f;
    public static final float   RIMESHELL_SHELL_OPEN_TINT_B         = 0.18f;
    /** Blend strength of the shell-open heat wash (0 = untinted, 1 = fully orange). The punish-window tell. */
    public static final float   RIMESHELL_SHELL_OPEN_TINT_STRENGTH  = 0.55f;

    // Shared ranged AI — kiting constants reused across ranged types
    public static final int     RANGED_KITE_MIN_TILES            = BalanceConfig.RANGED_KITE_MIN_TILES;

    public static final float   DORMANT_SHADE_DAMPEN             = 0.7f;
    public static final int     STUCK_TURNS_BEFORE_WIGGLE        = BalanceConfig.STUCK_TURNS_BEFORE_WIGGLE;
    public static final boolean ENEMY_GREEDY_WIGGLE_ENABLED      = true;
    public static final float   MAX_ENEMY_DRAW_DISTANCE_TILES    = 14f;

    // Enemy hit flash — white blanch on damage contact (purely cosmetic, wall-clock timed)
    public static final float   ENEMY_HIT_FLASH_DURATION_SECONDS = 0.18f;

    // Enemy health bar — floating billboard above each alerted enemy sprite
    public static final float   ENEMY_HEALTH_BAR_WIDTH_FRACTION     = 0.9f;
    public static final float   ENEMY_HEALTH_BAR_HEIGHT_FRACTION    = 0.06f;
    public static final float   ENEMY_HEALTH_BAR_GAP_FRACTION       = 0.04f;
    public static final float   ENEMY_HEALTH_BAR_MIN_PIXELS         = 3f;
    // Upper clamps so a point-blank enemy (huge billboard) does not draw a screen-spanning bar.
    public static final float   ENEMY_HEALTH_BAR_MAX_PIXELS         = 24f;
    public static final float   ENEMY_HEALTH_BAR_MAX_WIDTH_PIXELS   = 240f;
    public static final float   ENEMY_HEALTH_BAR_BORDER_PIXELS      = 1f;
    public static final float   ENEMY_HEALTH_BAR_MAX_DISTANCE_TILES = 12f;

    // On-screen safe area for the floating enemy UI cluster (health bar + name tag + intent icon).
    // When an enemy stands right next to the player its billboard fills the screen and the anchor
    // would push the whole cluster off the top / off the sides; these keep it clamped inside view.
    // Left/right/top gap (screen pixels) kept clear so the cluster never touches the screen edge.
    public static final float   ENEMY_UI_SCREEN_EDGE_MARGIN         = 8f;
    // Border / backdrop tint (semi-transparent near-black frame)
    public static final float   ENEMY_HEALTH_BAR_BORDER_RED         = 0.05f;
    public static final float   ENEMY_HEALTH_BAR_BORDER_GREEN       = 0.05f;
    public static final float   ENEMY_HEALTH_BAR_BORDER_BLUE        = 0.05f;
    public static final float   ENEMY_HEALTH_BAR_BORDER_ALPHA       = 0.85f;
    // Empty-track tint (missing health, dark red-gray)
    public static final float   ENEMY_HEALTH_BAR_TRACK_RED          = 0.18f;
    public static final float   ENEMY_HEALTH_BAR_TRACK_GREEN        = 0.05f;
    public static final float   ENEMY_HEALTH_BAR_TRACK_BLUE         = 0.05f;
    // Health gradient colour stops: full=green, half=yellow, empty=red
    public static final float   ENEMY_HEALTH_FULL_RED               = 0.10f;
    public static final float   ENEMY_HEALTH_FULL_GREEN             = 0.85f;
    public static final float   ENEMY_HEALTH_FULL_BLUE              = 0.10f;
    public static final float   ENEMY_HEALTH_HALF_RED               = 1.00f;
    public static final float   ENEMY_HEALTH_HALF_GREEN             = 0.85f;
    public static final float   ENEMY_HEALTH_HALF_BLUE              = 0.10f;
    public static final float   ENEMY_HEALTH_EMPTY_RED              = 1.00f;
    public static final float   ENEMY_HEALTH_EMPTY_GREEN            = 0.10f;
    public static final float   ENEMY_HEALTH_EMPTY_BLUE             = 0.10f;

    // HP text drawn inside the health bar: "current/max" in white for legibility
    // Text is hidden beyond this distance to de-clutter; rely on color gradient at range.
    public static final float   ENEMY_HP_TEXT_MAX_DISTANCE_TILES = 5f;
    public static final float   ENEMY_HP_TEXT_FONT_SCALE         = 0.65f;
    public static final float   ENEMY_HP_TEXT_RED                = 1.00f;
    public static final float   ENEMY_HP_TEXT_GREEN              = 1.00f;
    public static final float   ENEMY_HP_TEXT_BLUE               = 1.00f;

    // Active-Block visuals (strategy-combat-order-3). While an enemy holds Block > 0:
    //   - a steely-blue plating tint is blended over the billboard (bracing shimmer), and
    //   - the current Block value is drawn just left of the health bar in shield-blue.
    // The intent-icon shield (order-2) telegraphs the UPCOMING Block; these show ACTIVE Block.
    /** Strength of the blue plating tint blended over a blocking enemy's sprite [0,1]. */
    public static final float   ENEMY_BLOCK_TINT_STRENGTH        = 0.42f;
    public static final float   ENEMY_BLOCK_TINT_RED             = 0.38f;
    public static final float   ENEMY_BLOCK_TINT_GREEN           = 0.60f;
    public static final float   ENEMY_BLOCK_TINT_BLUE            = 1.00f;
    /** Gentle shimmer added to the plating tint: strength varies by this ± around the base. */
    public static final float   ENEMY_BLOCK_TINT_SHIMMER_AMOUNT  = 0.12f;
    /** Shimmer oscillation frequency (Hz). */
    public static final float   ENEMY_BLOCK_TINT_SHIMMER_HZ      = 1.4f;
    /** Beyond this distance the active-Block number is hidden (matches HP text de-clutter). */
    public static final float   ENEMY_BLOCK_NUMBER_MAX_DISTANCE_TILES = 6f;
    /** Font scale for the active-Block number drawn beside the health bar. */
    public static final float   ENEMY_BLOCK_NUMBER_FONT_SCALE    = 0.62f;
    /** Horizontal gap between the active-Block number's right edge and the health bar's left edge. */
    public static final float   ENEMY_BLOCK_NUMBER_BAR_GAP       = 5f;
    public static final float   ENEMY_BLOCK_NUMBER_RED           = 0.55f;
    public static final float   ENEMY_BLOCK_NUMBER_GREEN         = 0.80f;
    public static final float   ENEMY_BLOCK_NUMBER_BLUE          = 1.00f;

    // Enemy name tag — shown above health bar only when close enough
    public static final float ENEMY_NAME_TAG_MAX_DISTANCE_TILES = 8f;
    // Name tag font scale applied to the default BitmapFont — sized up for at-a-glance legibility.
    public static final float ENEMY_NAME_TAG_FONT_SCALE         = 1.15f;
    // Vertical gap between name tag baseline and top of health bar (screen pixels)
    public static final float ENEMY_NAME_TAG_BAR_GAP            = 4f;
    // Level-tier colors for the name tag text (determined by dungeonLevel at spawn)
    // Tier 1 LVL 1-2: white
    public static final float ENEMY_NAME_TAG_TIER1_R = 1.00f;
    public static final float ENEMY_NAME_TAG_TIER1_G = 1.00f;
    public static final float ENEMY_NAME_TAG_TIER1_B = 1.00f;
    // Tier 2 LVL 3-4: green
    public static final float ENEMY_NAME_TAG_TIER2_R = 0.25f;
    public static final float ENEMY_NAME_TAG_TIER2_G = 1.00f;
    public static final float ENEMY_NAME_TAG_TIER2_B = 0.25f;
    // Tier 3 LVL 5: blue
    public static final float ENEMY_NAME_TAG_TIER3_R = 0.25f;
    public static final float ENEMY_NAME_TAG_TIER3_G = 0.60f;
    public static final float ENEMY_NAME_TAG_TIER3_B = 1.00f;
    // Tier 4 LVL 6-7: violet
    public static final float ENEMY_NAME_TAG_TIER4_R = 0.80f;
    public static final float ENEMY_NAME_TAG_TIER4_G = 0.25f;
    public static final float ENEMY_NAME_TAG_TIER4_B = 1.00f;
    // Tier 5 LVL 8+: red
    public static final float ENEMY_NAME_TAG_TIER5_R = 1.00f;
    public static final float ENEMY_NAME_TAG_TIER5_G = 0.18f;
    public static final float ENEMY_NAME_TAG_TIER5_B = 0.18f;

    // -------------------------------------------------------------------------
    // Boss encounter stats
    //
    // RE-EXPORT SHIMS (Balance Authority, order 1): the boss gameplay numbers moved
    // into BalanceConfig SECTION 14 (the single source of truth). Tune them THERE.
    // Only cosmetic boss values (accent colours) remain owned by this file.
    //
    // PLACEHOLDER NUMBERS — TO BE RE-DERIVED VIA THE BOSS BALANCE RULESET (idea 6).
    // Every HP / damage / phase number below is a flat placeholder, exactly the "big
    // trash mob with arbitrary HP" the boss ruleset replaces. When boss FIGHTS are
    // actually built/retuned (deferred — they need story/run structure), re-derive these
    // by formula, NOT by guesswork:
    //   * HP   = GameMath.bossEffectiveHitPoints(expectedPlayerSustainedDamagePerTurn(depth),
    //            targetFightTurns, multiPhaseFactor)   — never a literal (RULE 1).
    //   * DPT  = GameMath.bossDamagePerTurnForSurvivalCheck(playerEHP, fightTurns, ratio) (RULE 3),
    //            with NO single hit over BalanceConfig.BOSS_HARD_SINGLE_HIT_FRACTION of eHP,
    //            and any hit over 25% telegraphed one turn ahead (RULE 3 fairness caps).
    //   * phases at GameMath.bossPhaseHealthThreshold(i, n) (RULE 4).
    // Targets/bands live in BalanceConfig SECTION 14; see docs/game-balance-authority.txt
    // (Boss appendix). BalanceReport's BOSS RULESET section prints the derived HP each
    // current boss SHOULD have at its depth next to the placeholder literals below.
    // -------------------------------------------------------------------------

    // The Overseer (depth 5) — security core robot; laser lanes + melee charge.
    // HP and per-verb DAMAGE are DERIVED at spawn (order 6) — see BossBalance / the *_DPT_FRACTION shims
    // below; there is no flat OVERSEER_MAX_HP / *_DAMAGE anymore. Only depth, cadence, and choreography
    // numbers stay flat here.
    public static final int   OVERSEER_DEPTH               = BalanceConfig.OVERSEER_DEPTH;
    /** MELEE (un-telegraphed) damage as a fraction of the derived boss DPT (order 6). */
    public static final float OVERSEER_MELEE_DPT_FRACTION  = BalanceConfig.OVERSEER_MELEE_DPT_FRACTION;
    /** CHARGE (telegraphed line strike) damage as a fraction of the derived boss DPT. */
    public static final float OVERSEER_CHARGE_DPT_FRACTION = BalanceConfig.OVERSEER_CHARGE_DPT_FRACTION;
    /** LASER (telegraphed lane) damage as a fraction of the derived boss DPT — the EnemyType headline. */
    public static final float OVERSEER_LASER_DPT_FRACTION  = BalanceConfig.OVERSEER_LASER_DPT_FRACTION;
    public static final int   OVERSEER_RAM_COOLDOWN        = BalanceConfig.OVERSEER_RAM_COOLDOWN;
    // Ability kit (boss-fight-mobile-overseer ORDER 3) — the Overseer's action VERBS as data.
    // Each damaging verb is telegraphed one turn ahead (fairness F2). Damage numbers sit under the
    // boss fairness caps at full player HP (100): CHARGE 30 and MELEE 14 are both < 35% eHP, and the
    // untelegraphed MELEE is < 25% eHP (docs/game-balance-authority.txt BOSS APPENDIX, RULE 3).
    // CHARGE — mobile line strike: lunge up to this many tiles along the telegraphed corridor.
    public static final int OVERSEER_CHARGE_RANGE_TILES    = BalanceConfig.OVERSEER_CHARGE_RANGE_TILES;
    // Forced plant turns after a CHARGE lands — the player's guaranteed punish window (fairness F1).
    public static final int OVERSEER_CHARGE_RECOVERY_TURNS = BalanceConfig.OVERSEER_CHARGE_RECOVERY_TURNS;
    // SUMMON_ADDS — Corruptor-style carrier pack: hard live-adds ceiling (F5) and adds per summon.
    public static final int OVERSEER_ADDS_CAP              = BalanceConfig.OVERSEER_ADDS_CAP;
    public static final int OVERSEER_SUMMON_COUNT          = BalanceConfig.OVERSEER_SUMMON_COUNT;
    // SPAWN_FIRE footprint — length of the telegraphed fire lane the brain arms (hazard DOT/lifetime
    // come from the existing HazardManager/BalanceConfig hazard constants; not re-declared here).
    public static final int OVERSEER_FIRE_LANE_LENGTH      = BalanceConfig.OVERSEER_FIRE_LANE_LENGTH;
    // SPAWN_TOXIC footprint — 3x3 cloud radius (centre + one ring); kept small so it can never wall a
    // 1-wide corridor (fairness F5).
    public static final int OVERSEER_TOXIC_RADIUS          = BalanceConfig.OVERSEER_TOXIC_RADIUS;
    // HEAL — one-time regeneration (boss-fight-mobile-overseer ORDER 5). The Overseer may repair itself
    // exactly ONCE per fight: below the HP threshold it breaks off, dashes to a safe spot, then restores
    // HP for a fixed number of turns. Taking ANY damage during the chain cancels the remaining ticks —
    // and the ability is spent either way. Numbers kept well below "back to full" so a fully-missed
    // interrupt only extends the fight, never resets it (see the idea file's NUMBERS section).
    // Trigger threshold sits below the phase-2 line (BOSS_PHASE2_HP_THRESHOLD) so it heals only when
    // genuinely threatened, never as a routine reset.
    public static final float OVERSEER_HEAL_HP_THRESHOLD   = BalanceConfig.OVERSEER_HEAL_HP_THRESHOLD;
    // Repair ticks after the flee step ("small amount each turn for 5 turns" — the brief).
    public static final int   OVERSEER_HEAL_TURNS          = BalanceConfig.OVERSEER_HEAL_TURNS;
    // Total HP restored over the repair chain, as a fraction of the DERIVED max HP (order 6): meaningful
    // (~24%), never a full wall. Per-tick amount is BossStats.healPerTurn(fraction, OVERSEER_HEAL_TURNS).
    public static final float OVERSEER_HEAL_TOTAL_HP_FRACTION = BalanceConfig.OVERSEER_HEAL_TOTAL_HP_FRACTION;
    // Accent color (cyan-white)
    public static final float OVERSEER_ACCENT_R = 0.60f;
    public static final float OVERSEER_ACCENT_G = 0.90f;
    public static final float OVERSEER_ACCENT_B = 1.00f;

    // The Corruptor (depth 10) — mutated scientist; summoner + acid burst.
    // HP and ACID damage are DERIVED at spawn (order 6) — see BossBalance / CORRUPTOR_ACID_DPT_FRACTION.
    public static final int   CORRUPTOR_DEPTH             = BalanceConfig.CORRUPTOR_DEPTH;
    public static final int   CORRUPTOR_SUMMON_COOLDOWN   = BalanceConfig.CORRUPTOR_SUMMON_COOLDOWN;
    public static final int   CORRUPTOR_MINION_CAP        = BalanceConfig.CORRUPTOR_MINION_CAP;
    /** ACID (telegraphed burst) damage as a fraction of the derived boss DPT (order 6). */
    public static final float CORRUPTOR_ACID_DPT_FRACTION = BalanceConfig.CORRUPTOR_ACID_DPT_FRACTION;
    public static final int   CORRUPTOR_ACID_POOL_DURATION = BalanceConfig.CORRUPTOR_ACID_POOL_DURATION;
    // Accent color (toxic green)
    public static final float CORRUPTOR_ACCENT_R = 0.40f;
    public static final float CORRUPTOR_ACCENT_G = 1.00f;
    public static final float CORRUPTOR_ACCENT_B = 0.10f;

    // =====================================================================
    // Special-ability move-sets (strategy-combat-order-5)
    // A scripted archetype consults its move-set on a fixed cadence, producing a readable rhythm the
    // player learns and plans around. Each damaging special is telegraphed one turn ahead (order-1),
    // so no fairness rule is broken. All numbers here so nothing is hardcoded (split-constants rule).
    // =====================================================================

    /** Enemy turns between special-ability attempts for a caster (Acid Drone / Mire Wraith / Vortex Eye). */
    public static final int SPECIAL_CADENCE_CASTER = 3;
    /** Frequent, layered elite script (Iron Stalker) — a special roughly every other turn. */
    public static final int SPECIAL_CADENCE_ELITE  = 2;
    /** Slow, heavy specials (Plague Hulk area slam, Blight Corruptor summon, Eye Tyrant blind). */
    public static final int SPECIAL_CADENCE_SLOW   = 4;

    /** How many recent picks the per-enemy no-repeat history tracks (StS "cannot repeat" window). */
    public static final int MOVE_HISTORY_SIZE = 4;

    // BUFF_SELF — the caster gains EMPOWERED (outgoing-damage buff). Enemy-specific values so the buff
    // prices into the enemy's Threat Points independently of the player's stim buff.
    public static final int BUFF_SELF_EMPOWERED_PERCENT = 40;
    public static final int BUFF_SELF_DURATION_TURNS    = 3;

    // DEBUFF_PLAYER — control applied down a cardinal line. Duration in world turns.
    public static final int DEBUFF_SLOW_DURATION_TURNS  = 3;
    public static final int DEBUFF_BLIND_DURATION_TURNS = 2;
    /**
     * WEAK debuff duration (strategy-combat-order-6): the acid casters (Mire Wraith / Acid Drone)
     * corrode the marine's output so its own HUD damage numbers dim — the symmetric, player-facing
     * side of the Weak power. Kept short so it softens a couple of hits, not the whole fight.
     */
    public static final int DEBUFF_WEAK_DURATION_TURNS  = 2;

    // SUMMON — spawn chaff on empty adjacent tiles. A summoner spawns a BATCH of 2-3 per cast, then is
    // gated by its SPECIAL cadence (the natural cooldown) and the per-room live-enemy ceiling before it
    // may summon again — reusable but never a per-turn flood (design feedback: "spawn 2-3 once, then be
    // able to use the ability again", not indefinitely). The room cap bounds the encounter-budget
    // headroom (docs/game-balance-authority.txt).
    public static final int SUMMON_COUNT_MIN     = 2;
    public static final int SUMMON_COUNT_MAX     = 3;
    public static final int SUMMON_ROOM_LIVE_CAP = 14;
    /** Slots reserved for pre-selected summon target tiles (the four cardinal neighbours). */
    public static final int SUMMON_TARGET_CAPACITY = 4;

    // SUMMON telegraph — a pulsing "spore rune" drawn on each pre-selected empty spawn cell during the
    // player's turn (EnemyRenderer), so the incoming spawn is a readable, dodge-able intent on the floor.
    /** Marker footprint as a fraction of the target tile's full wall-stripe height at its depth. */
    public static final float SUMMON_MARKER_SIZE_FRACTION = 0.55f;
    /** Vertical lift of the rune off the floor line, as a fraction of its own size (0 = flat on floor). */
    public static final float SUMMON_MARKER_FLOOR_LIFT_FRACTION = 0.20f;
    /** Pulse speed (Hz) of the rune's breathing glow. */
    public static final float SUMMON_MARKER_PULSE_HZ = 1.6f;
    /** Number of orbiting glow motes forming the summoning circle. */
    public static final int   SUMMON_MARKER_MOTE_COUNT = 8;
    /** Peak additive alpha of the rune at the brightest point of its pulse. */
    public static final float SUMMON_MARKER_MAX_ALPHA = 0.85f;
    /** Max draw distance (tiles) for the telegraph rune; beyond this it is culled for clarity. */
    public static final float SUMMON_MARKER_MAX_DISTANCE_TILES = 16f;
    /** Toxic-green rune colour (matches the Blight Corruptor's spore theme). */
    public static final float SUMMON_MARKER_R = 0.40f;
    public static final float SUMMON_MARKER_G = 1.00f;
    public static final float SUMMON_MARKER_B = 0.15f;

    // AREA_STRIKE — telegraphed slam hitting every tile within this cross radius of the enemy.
    public static final int   AREA_STRIKE_RADIUS_TILES      = 1;
    public static final float AREA_STRIKE_DAMAGE_MULTIPLIER = 1.35f;

    // Hell Baron (depth 15) — armored greater demon; firewall + enrage.
    // HP and per-verb DAMAGE are DERIVED at spawn (order 6) — see BossBalance / the *_DPT_FRACTION shims.
    public static final int   HELL_BARON_DEPTH                = BalanceConfig.HELL_BARON_DEPTH;
    public static final int   HELL_BARON_FIREWALL_COOLDOWN_P1 = BalanceConfig.HELL_BARON_FIREWALL_COOLDOWN_P1;
    public static final int   HELL_BARON_FIREWALL_COOLDOWN_P2 = BalanceConfig.HELL_BARON_FIREWALL_COOLDOWN_P2;
    public static final int   HELL_BARON_FIREWALL_DURATION    = BalanceConfig.HELL_BARON_FIREWALL_DURATION;
    /** FIRE (telegraphed hazard tick) damage as a fraction of the derived boss DPT (order 6). */
    public static final float HELL_BARON_FIRE_DPT_FRACTION      = BalanceConfig.HELL_BARON_FIRE_DPT_FRACTION;
    /** CLEAVE phase-1 (telegraphed) damage as a fraction of the derived boss DPT. */
    public static final float HELL_BARON_CLEAVE_P1_DPT_FRACTION = BalanceConfig.HELL_BARON_CLEAVE_P1_DPT_FRACTION;
    /** CLEAVE phase-2 (telegraphed signature) damage as a fraction of the derived boss DPT. */
    public static final float HELL_BARON_CLEAVE_P2_DPT_FRACTION = BalanceConfig.HELL_BARON_CLEAVE_P2_DPT_FRACTION;
    // Accent color (ember orange-red)
    public static final float HELL_BARON_ACCENT_R = 1.00f;
    public static final float HELL_BARON_ACCENT_G = 0.30f;
    public static final float HELL_BARON_ACCENT_B = 0.05f;
}
