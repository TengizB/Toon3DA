package ge.tbegvadze.toon3d.util;

/**
 * SINGLE SOURCE OF TRUTH FOR BALANCE.
 *
 * Every number in this file changes how hard the game is, or how generous it is with
 * resources. If you change a value here, the game gets easier, harder, more, or less
 * generous — nothing else. Cosmetic values (colours, sprite offsets, HUD geometry,
 * shake magnitudes, bob speeds, texture paths) live in their own {@code *Constants}
 * files and are NOT mirrored here.
 *
 * <h2>Why this file exists</h2>
 * Before this file, the balance numbers were scattered across at least five classes:
 * {@link GameBalance}, {@link EnemyConstants}, {@link WeaponConstants},
 * {@link ItemConstants} and {@link LevelGenConstants}. Enemy HP lived in one file, the
 * weapon that kills it in another, the ammo that feeds the weapon in a third, and the
 * drop rate that supplies the ammo in a fourth. You cannot balance what you cannot see
 * in one place. This class consolidates the raw numbers so a designer can read the whole
 * difficulty curve and resource economy from one screen.
 *
 * <h2>Strategy A — re-export, not behaviour change</h2>
 * The other {@code *Constants} files keep their existing field names but now derive their
 * balance values FROM this class (e.g.
 * {@code EnemyConstants.PLAGUE_HULK_MAX_HEALTH = BalanceConfig.PLAGUE_HULK_MAX_HEALTH;}).
 * Game code keeps compiling and behaviour is byte-for-byte identical — only the literal
 * numbers moved. Folding the {@code *Constants} files away entirely (Strategy B) is
 * deferred to the balance rule-system idea (idea 2).
 *
 * <h2>Out of scope (deliberately NOT moved here)</h2>
 * Boss stats (Overseer / Corruptor / Hell Baron) remain in {@link EnemyConstants}; the
 * boss-balancing ruleset is idea 6. The per-weapon ability catalogue (crit chance, pierce,
 * lifesteal, etc.) remains in {@link GameBalance} pending idea 2's formula contract.
 *
 * <p>Before adding a weapon / enemy / item, consult the balance rule system (idea 2)
 * once it exists, and add the new tuning number HERE first, then reference it from the
 * matching {@code *Constants} file.
 */
public final class BalanceConfig {

    private BalanceConfig() {}

    // =====================================================================================
    // SECTION 1 — PLAYER SURVIVABILITY (the denominator of all difficulty)
    // How much punishment the player can absorb before dying. Raise these to make the
    // game more forgiving; lower them to make every hit matter more.
    // =====================================================================================

    /** Player maximum HP pool. Range: 80–200. The single biggest forgiveness dial. */
    public static final int   PLAYER_MAX_HEALTH       = 130;
    /** Player maximum armour pool. Range: 0–120. Armour soaks part of every hit. */
    public static final int   PLAYER_MAX_ARMOR        = 75;
    /** Fraction of each incoming hit absorbed by armour (depleting armour instead of HP). Range: 0.25–0.75. */
    public static final float ARMOUR_ABSORB_FRACTION  = 0.50f;

    /** HP restored by a stim-pack ('+'). Range: 10–30. */
    public static final int   MEDKIT_STIM_HEAL        = 18;
    /** HP restored by a full medkit ('H'). Range: 30–80. */
    public static final int   MEDKIT_FULL_HEAL        = 50;
    /** Armour restored by an armour shard ('a'). Range: 4–15. */
    public static final int   ARMOUR_SHARD_VALUE      = 8;
    /** Armour restored by a security vest ('A'). Range: 20–50. */
    public static final int   ARMOUR_VEST_VALUE       = 35;

    /** Seconds per one-tile step. Lower = snappier, and you eat fewer enemy turns while repositioning. Range: 0.08–0.20. */
    public static final float PLAYER_MOVE_DURATION    = 0.12f;
    /** Seconds per 90° rotation. Same turn-economy effect as movement. Range: 0.06–0.18. */
    public static final float PLAYER_ROTATE_DURATION  = 0.09f;

    // =====================================================================================
    // SECTION 2 — ENEMY THREAT (HP / damage / range / cadence) — the numerator
    // The raw power of each enemy archetype at depth 1, plus the per-kill payouts.
    // moveEveryN = 1 means the enemy acts every player turn; 2 means every other turn.
    // =====================================================================================

    // GORE_BITER (spawn '3') — fast light melee; spawns in packs.
    public static final int GORE_BITER_MAX_HEALTH          = 18;
    public static final int GORE_BITER_ATTACK_DAMAGE       = 7;
    public static final int GORE_BITER_MOVE_EVERY_N_TURNS  = 1;

    // EYE_TYRANT (spawn '2') — fast ranged kiter.
    public static final int EYE_TYRANT_MAX_HEALTH          = 18;
    public static final int EYE_TYRANT_ATTACK_DAMAGE       = 7;
    public static final int EYE_TYRANT_RANGE_TILES         = 5;

    // ACID_DRONE (spawn '$') — ranged mechanical.
    public static final int ACID_DRONE_MAX_HEALTH          = 22;
    public static final int ACID_DRONE_ATTACK_DAMAGE       = 8;
    public static final int ACID_DRONE_RANGE_TILES         = 4;
    public static final int ACID_DRONE_MOVE_EVERY_N_TURNS  = 1;

    // VOID_SHROUD (spawn '^') — fast stealth melee.
    public static final int VOID_SHROUD_MAX_HEALTH         = 25;
    public static final int VOID_SHROUD_ATTACK_DAMAGE      = 9;
    public static final int VOID_SHROUD_MOVE_EVERY_N_TURNS = 1;

    // MIRE_WRAITH (spawn '5') — slow ground-based ranged acid; tanky.
    public static final int MIRE_WRAITH_MAX_HEALTH         = 38;
    public static final int MIRE_WRAITH_ATTACK_DAMAGE      = 7;
    public static final int MIRE_WRAITH_RANGE_TILES        = 3;
    public static final int MIRE_WRAITH_MOVE_EVERY_N_TURNS = 2;

    // SHELL_BRUTE (spawn '4') — heavy charger melee.
    public static final int SHELL_BRUTE_MAX_HEALTH         = 38;
    public static final int SHELL_BRUTE_ATTACK_DAMAGE      = 13;
    public static final int SHELL_BRUTE_MOVE_EVERY_N_TURNS = 1;

    // PLAGUE_HULK (spawn '1') — slow tank melee.
    public static final int PLAGUE_HULK_MAX_HEALTH         = 50;
    public static final int PLAGUE_HULK_ATTACK_DAMAGE      = 10;
    public static final int PLAGUE_HULK_MOVE_EVERY_N_TURNS = 2;

    // IRON_STALKER (spawn '!') — armoured elite, melee + ranged; the big threat.
    public static final int IRON_STALKER_MAX_HEALTH        = 95;
    public static final int IRON_STALKER_MELEE_DAMAGE      = 16;
    public static final int IRON_STALKER_RANGED_DAMAGE     = 11;
    public static final int IRON_STALKER_RANGE_TILES       = 4;
    public static final int IRON_STALKER_MOVE_EVERY_N_TURNS = 1;

    // Global enemy AI knobs — perception and kiting tuning shared across types.
    /** Tiles within which an enemy notices the player and wakes. Range: 3–6. */
    public static final int ALERT_RADIUS_TILES        = 4;
    /** Tiles within which a waking enemy alerts its neighbours. Range: 3–7. */
    public static final int CHAIN_ALERT_RADIUS_TILES  = 5;
    /** Maximum tiles a line-of-sight check reaches. Range: 6–12. */
    public static final int LOS_MAX_RANGE_TILES       = 8;
    /** Tiles a ranged enemy tries to keep between itself and the player when kiting. Range: 1–4. */
    public static final int RANGED_KITE_MIN_TILES     = 2;
    /** Turns an enemy stays blocked before it wiggles to a side tile. Range: 1–4. */
    public static final int STUCK_TURNS_BEFORE_WIGGLE = 2;

    // Per-kill XP rewards (the progression payout for each archetype).
    public static final int XP_REWARD_GORE_BITER   = 10;
    public static final int XP_REWARD_EYE_TYRANT   = 10;
    public static final int XP_REWARD_ACID_DRONE   = 14;
    public static final int XP_REWARD_VOID_SHROUD  = 18;
    public static final int XP_REWARD_MIRE_WRAITH  = 22;
    public static final int XP_REWARD_SHELL_BRUTE  = 18;
    public static final int XP_REWARD_PLAGUE_HULK  = 14;
    public static final int XP_REWARD_IRON_STALKER = 55;

    // Per-kill credit rewards (the currency payout for each archetype).
    public static final int CREDIT_REWARD_GORE_BITER   = 5;
    public static final int CREDIT_REWARD_EYE_TYRANT   = 6;
    public static final int CREDIT_REWARD_ACID_DRONE   = 8;
    public static final int CREDIT_REWARD_VOID_SHROUD  = 12;
    public static final int CREDIT_REWARD_MIRE_WRAITH  = 15;
    public static final int CREDIT_REWARD_SHELL_BRUTE  = 12;
    public static final int CREDIT_REWARD_PLAGUE_HULK  = 8;
    public static final int CREDIT_REWARD_IRON_STALKER = 40;

    // =====================================================================================
    // SECTION 3 — DEPTH SCALING (how threat and reward grow per floor)
    // Compound HP/damage growth and linear credit growth applied as you descend.
    // =====================================================================================

    /** Per-floor compound HP multiplier: baseHP * scale^(depth-1). Range: 1.04–1.15. */
    public static final float ENEMY_HEALTH_SCALE_PER_DEPTH = 1.08f;
    /** Per-floor compound damage multiplier: baseDmg * scale^(depth-1). Range: 1.03–1.12. */
    public static final float ENEMY_DAMAGE_SCALE_PER_DEPTH = 1.06f;
    /** Per-floor linear credit bonus: base * (1 + (depth-1) * scale). Range: 0.05–0.25. */
    public static final float CREDIT_DEPTH_SCALE           = 0.12f;

    /** Extra enemies a deepest-depth room may add over the base count. Range: 0–4. */
    public static final int   LEVEL_GEN_DEPTH_ENEMY_BONUS_MAX      = 2;
    /** At full depth, chance a light spawn is upgraded to a heavy archetype. Range: 0.0–1.0. */
    public static final float LEVEL_GEN_DEPTH_ENEMY_UPGRADE_CHANCE = 0.60f;
    /** Additive medkit-chance bonus at full depth. Range: 0.0–0.4. */
    public static final float LEVEL_GEN_DEPTH_MEDKIT_BONUS         = 0.15f;
    /** Additive ammo-chance bonus at full depth. Range: 0.0–0.4. */
    public static final float LEVEL_GEN_DEPTH_AMMO_BONUS           = 0.20f;
    /** At full depth, chance a room receives a bonus second ammo box. Range: 0.0–1.0. */
    public static final float LEVEL_GEN_DEPTH_EXTRA_AMMO_CHANCE    = 0.50f;

    // =====================================================================================
    // SECTION 4 — WEAPON OUTPUT (damage / clip / range / falloff / reload)
    // The player's side of the TTK equation. dropCoeff is the per-tile damage falloff;
    // reloadTicks is how many turns a reload eats.
    // =====================================================================================

    // Shotgun — high single-shot burst, 1-shell clip.
    // Was 50 (powerScore 28.0, OVER the 18-26 burst band — best sustained DPT AND best
    // ammo efficiency of the non-charge guns). Trimmed to 44 (powerScore 23.1, in band)
    // so it no longer invalidates the other guns. See docs/balance-rule-system.txt.
    public static final int   SHOTGUN_DAMAGE             = 44;
    public static final int   SHOTGUN_CLIP_SIZE          = 1;
    public static final int   SHOTGUN_RANGE_TILES        = 5;
    public static final float SHOTGUN_DAMAGE_DROP_COEFF  = 0.18f;
    public static final int   SHOTGUN_RELOAD_TIME_TICKS  = 1;

    // Double-Barrel Shotgun — higher burst, shorter range, 2-shot clip.
    public static final int   DBL_SHOTGUN_DAMAGE             = 32;
    public static final int   DBL_SHOTGUN_CLIP_SIZE          = 2;
    public static final int   DBL_SHOTGUN_RANGE_TILES        = 4;
    public static final float DBL_SHOTGUN_DAMAGE_DROP_COEFF  = 0.22f;
    public static final int   DBL_SHOTGUN_RELOAD_TIME_TICKS  = 1;

    // Plasma Rifle — piercing, long range, lower per-shot damage.
    // Was 18 (powerScore 9.7, UNDER the 18-26 burst band). Raised to 28 (powerScore 18.7,
    // in band) — still the lowest per-shot of the burst class, but now worth its slot.
    public static final int   PLASMA_RIFLE_DAMAGE             = 28;
    public static final int   PLASMA_RIFLE_CLIP_SIZE          = 4;
    public static final int   PLASMA_RIFLE_RANGE_TILES        = 8;
    public static final float PLASMA_RIFLE_DAMAGE_DROP_COEFF  = 0.10f;
    public static final int   PLASMA_RIFLE_RELOAD_TIME_TICKS  = 1;

    // Chaingun — sustained fire, 24-round clip (8 bursts × 3).
    // Was 10 (powerScore 4.8, far UNDER the 12-18 workhorse band). Raised to 19
    // (powerScore 12.6, in band) so sustained fire is a real workhorse option.
    public static final int   CHAINGUN_DAMAGE             = 19;
    public static final int   CHAINGUN_CLIP_SIZE          = 24;
    public static final int   CHAINGUN_RANGE_TILES        = 8;
    public static final float CHAINGUN_DAMAGE_DROP_COEFF  = 0.10f;
    public static final int   CHAINGUN_RELOAD_TIME_TICKS  = 1;

    // Assault Rifle — precision automatic, long range, no pierce.
    // Was 14 (powerScore 8.0, UNDER the 12-18 workhorse band). Raised to 20
    // (powerScore 13.7, in band) — the reliable mid-band workhorse it should be.
    public static final int   ASSAULT_RIFLE_DAMAGE            = 20;
    public static final int   ASSAULT_RIFLE_CLIP_SIZE         = 30;
    public static final int   ASSAULT_RIFLE_RANGE_TILES       = 10;
    public static final float ASSAULT_RIFLE_DAMAGE_DROP_COEFF = 0.08f;
    public static final int   ASSAULT_RIFLE_RELOAD_TIME_TICKS = 1;

    // Railgun — charge-up infinite-pierce sniper. Index by charge level: {0, half, full}.
    // Full-charge powerScore is 45.0, OVER the 24-32 heavy band, but its 90-per-slug
    // efficiency is held in check by slug SCARCITY (RAILGUN_MAX_SLUGS, cut to 5 by the
    // idea-3 scarcity pass — see SECTION 5) and the charge cost, not by raw damage. Per
    // docs/balance-rule-system.txt the raw number is left intact deliberately: now that the
    // idea-3 scarcity model HAS landed, the slug cap (5) is the lever holding it in check,
    // so nerfing the raw 90 would make the weapon worthless instead of merely scarce.
    public static final int[] RAILGUN_DAMAGE_BY_CHARGE      = {0, 40, 90};
    public static final int   RAILGUN_RANGE_TILES           = 16;
    public static final float RAILGUN_DROP_COEFF            = 0.02f;
    public static final float RAILGUN_DAMAGE_MIN_MULTIPLIER = 0.70f;
    public static final int   RAILGUN_CLIP_SIZE             = 1;
    public static final int   RAILGUN_RELOAD_TIME_TICKS     = 2;

    // Incinerator — short-range cone flamethrower. Impact + per-turn burn DoT (see section 8).
    public static final int   FLAME_IMPACT_DAMAGE     = 8;
    public static final int   FLAME_FALLOFF           = 5;
    public static final float FLAME_DAMAGE_DROP_COEFF = 0.0f;
    public static final int   FLAME_RANGE_TILES       = 3;
    public static final int   FLAME_CLIP_SIZE         = 30;
    public static final int   FLAME_RELOAD_TICKS      = 1;

    // Grenade Launcher — bouncing AoE splash. Centre / orthogonal-neighbour / self damage.
    // Centre splash was 30 (powerScore 15.6, UNDER the 24-32 heavy band). Raised to 42
    // (powerScore 25.8, in band); falloff/self raised proportionally to keep the blast
    // profile, with self kept modest so the player's own risk does not balloon.
    public static final int   GRENADE_SPLASH_DAMAGE     = 42;
    public static final int   GRENADE_FALLOFF_DAMAGE    = 22;
    public static final int   GRENADE_SELF_DAMAGE       = 24;
    public static final float GRENADE_DAMAGE_DROP_COEFF = 0.0f;
    public static final int   GRENADE_RANGE_TILES       = 6;
    public static final int   GRENADE_CLIP_SIZE         = 3;
    public static final int   GRENADE_RELOAD_TIME_TICKS = 2;

    // Melee weapons — base damage per swing (all swing once per turn).
    public static final int MELEE_FIST_DAMAGE     = 6;
    public static final int MELEE_KNIFE_DAMAGE    = 12;
    public static final int MELEE_CHAINSAW_DAMAGE = 18;
    public static final int MELEE_HAMMER_DAMAGE   = 20;

    // Global weapon knobs.
    /** Crit total-damage multiplier (crits deal this × base). Range: 1.5–3.0. */
    public static final float CRIT_DAMAGE_MULTIPLIER        = 2.0f;
    /** Damage floor as a fraction of base at maximum falloff range. Range: 0.10–0.30. */
    public static final float DAMAGE_MIN_MULTIPLIER         = 0.15f;
    /** Per-weapon-level outgoing damage bonus (+10%/level). Range: 0.05–0.20. */
    public static final float WEAPON_LEVEL_DAMAGE_PER_LEVEL = 0.10f;

    // =====================================================================================
    // SECTION 5 — RESOURCE SUPPLY (the ammo economy) — TUNED FOR SCARCITY (idea 3)
    // How much ammo a pickup grants, how much you can hoard, and how often kills/rooms
    // hand out ammo. Tighten these to create scarcity; loosen them for power-fantasy runs.
    //
    // This section holds THREE of the four scarcity levers (idea 3): LEVER 1 DROP FREQUENCY,
    // LEVER 2 DROP SIZE, and LEVER 3 RESERVE CAP. The fourth — LEVER 4 DEMAND (enemy
    // density / eHP per floor) — lives in SECTION 2 (enemy threat) and is owned by the floor
    // TP budget (idea 4); the model floor in SECTION 10 fixes a reference DEMAND so this
    // section can be tuned against it. They are tuned so the
    // model floor (SECTION 10) lands at scarcity ratio S ~= 0.88 floor-wide and < 0.6 per
    // weapon — ammo alone covers ~88% of the damage needed to clear a "fight everything"
    // floor, the rest coming from melee/avoidance. Pre-idea-3 these were ~6x too generous
    // (S ~= 5.8: a single weapon's ammo cleared the floor six times over). Regenerate the
    // scarcity living table with BalanceReport after any change here. See
    // docs/balance-rule-system.txt and balance_order_3_resource_scarcity_economy.txt.
    //
    // NOTE ON MAGNITUDE: the cuts are large because the weapon-damage / enemy-eHP economy
    // is high-damage / low-eHP (a single 44-dmg shell two-shots most chaff), so a whole
    // floor's DEMAND (~288 dmg at depth 1) is only ~6-12 ammo units. Scarce ammo therefore
    // means small boxes and low drop rates. Fully reconciling clip sizes with this economy
    // is the deferred eHP/damage rescale flagged in docs/balance-rule-system.txt.

    // LEVER 2 — DROP SIZE: ammo box grants (rounds per pickup). Cut hard from the
    // pre-scarcity values (30/12/25/4/60/6) so no single box clears a floor.
    public static final int AMMO_BOX_BULLETS    = 6;
    public static final int AMMO_BOX_SHELLS     = 2;
    public static final int AMMO_BOX_CELLS      = 4;
    public static final int AMMO_BOX_ROCKETS    = 1;
    public static final int RAILGUN_PICKUP_SLUGS = 1;
    public static final int FLAME_PICKUP_FUEL   = 25;
    public static final int GRENADE_PICKUP_AMMO = 3;

    // LEVER 3 — RESERVE CAP: the hoarding ceiling, tuned to ~1.5 floors of that weapon's
    // run-demand (see GameMath.reserveBankingFloors) so banking is limited. EXCEPTION:
    // AMMO_RESERVE_CAP_BULLETS is floored at the largest bullet clip (Assault Rifle 30,
    // Chaingun 24) so the weapon stays usable — it banks ~3 floors, the one cap above the
    // 1.5-floor target, a direct symptom of the clip-vs-eHP mismatch noted above.
    public static final int AMMO_RESERVE_CAP_BULLETS = 45;
    public static final int AMMO_RESERVE_CAP_SHELLS  = 12;
    public static final int AMMO_RESERVE_CAP_CELLS   = 16;
    public static final int AMMO_RESERVE_CAP_ROCKETS = 10;
    public static final int RAILGUN_MAX_SLUGS        = 5;
    public static final int FLAME_MAX_FUEL           = 50;
    public static final int GRENADE_MAX_AMMO         = 10;

    // LEVER 1 — DROP FREQUENCY: how often a pickup spawns at all. Frequency adds variance
    // (good for texture) and here carries more of the scarcity than ideal because box sizes
    // can only shrink so far before they stop being sensible "boxes" — see the magnitude note.
    /** Chance a ranged-weapon kill drops an ammo pickup. Range: 0.05–0.6. */
    public static final float ENEMY_AMMO_DROP_CHANCE         = 0.10f;
    /** Chance a melee kill drops an ammo pickup (kept above the ranged rate to reward the risky melee path). Range: 0.1–0.8. */
    public static final float MELEE_KILL_AMMO_DROP_CHANCE    = 0.20f;
    /** Base chance any non-entrance room contains at least one ammo box. Range: 0.1–0.6. */
    public static final float LEVEL_GEN_AMMO_CHANCE_PER_ROOM = 0.20f;

    // =====================================================================================
    // SECTION 6 — LOOT / PICKUP SPAWN CHANCES (the drop economy)
    // Global density of props and enemies, plus the room budgets that govern where
    // medkits, armour, ammo and weapons appear.
    // =====================================================================================

    /** Chance any interior floor tile in a non-entrance room receives a prop. Range: 0.05–0.25. */
    public static final float LEVEL_GEN_PROP_CHANCE          = 0.13f;
    /** Hard cap on enemies spawned per room. Range: 1–5. */
    public static final int   LEVEL_GEN_MAX_ENEMIES_PER_ROOM = 3;

    // Credit-chip floor spawns per level and their tier weights (proportional, need not sum to 100).
    public static final int CREDIT_CHIPS_PER_FLOOR_MIN  = 3;
    public static final int CREDIT_CHIPS_PER_FLOOR_MAX  = 7;
    public static final int CREDIT_SPAWN_WEIGHT_SMALL   = 70;
    public static final int CREDIT_SPAWN_WEIGHT_MEDIUM  = 24;
    public static final int CREDIT_SPAWN_WEIGHT_LARGE   = 6;

    // -------------------------------------------------------------------------------------
    // LOOT ROOM BUDGETS — per-room-type pickup chances. These are the real spawn dials:
    // they decide how generous each themed room is with heals, armour, ammo and weapons.
    // -------------------------------------------------------------------------------------
    // Server room (data vault).
    public static final float LEVEL_GEN_SERVER_MEDKIT_CHANCE = 0.55f;
    public static final float LEVEL_GEN_SERVER_ARMOUR_CHANCE = 0.35f;
    // Large landmark room.
    public static final float LEVEL_GEN_LARGE_MEDKIT_CHANCE  = 0.50f;
    public static final float LEVEL_GEN_LARGE_ARMOUR_CHANCE  = 0.30f;
    public static final float LEVEL_GEN_LARGE_WEAPON_CHANCE  = 0.30f;
    // Standard room weapon spawn.
    public static final float LEVEL_GEN_RANDOM_ROOM_WEAPON_CHANCE = 0.35f;
    // Armory.
    public static final float LEVEL_GEN_ARMORY_MEDKIT_CHANCE = 0.40f;
    public static final float LEVEL_GEN_ARMORY_ARMOUR_CHANCE = 0.80f;
    // Command center.
    public static final float LEVEL_GEN_COMMAND_MEDKIT_CHANCE = 0.50f;
    public static final float LEVEL_GEN_COMMAND_ARMOUR_CHANCE = 0.50f;
    public static final float LEVEL_GEN_COMMAND_AMMO_CHANCE   = 0.60f;
    // Hazard rooms (power plant / cryo / containment) — reduced loot for the danger.
    public static final float LEVEL_GEN_HAZARD_ROOM_MEDKIT_CHANCE = 0.25f;
    public static final float LEVEL_GEN_HAZARD_ROOM_ARMOUR_CHANCE = 0.20f;
    public static final float LEVEL_GEN_HAZARD_ROOM_AMMO_CHANCE   = 0.30f;

    // =====================================================================================
    // SECTION 7 — PROGRESSION REWARDS (XP curve + level-up payouts + stat rates)
    // The player's power-growth curve. Per-enemy XP rewards live in section 2.
    // =====================================================================================

    /** Base XP needed to advance from level 1 to 2: xpRequired = base * level^exponent. Range: 30–80. */
    public static final int   XP_BASE_REQUIREMENT = 50;
    /** Exponent of the XP power curve; higher = steeper. Range: 1.1–1.6. */
    public static final float XP_CURVE_EXPONENT   = 1.3f;

    /** Flat max-HP gained when HP_BOOST level-up reward is chosen. Range: 15–40. */
    public static final int LEVEL_UP_HP_BONUS     = 25;
    /** Flat max-armour gained when ARMOR_BOOST reward is chosen. Range: 10–30. */
    public static final int LEVEL_UP_ARMOR_BONUS  = 18;
    /** Flat per-shot damage gained when DAMAGE_BOOST reward is chosen. Range: 4–15. */
    public static final int LEVEL_UP_DAMAGE_BONUS = 8;

    // Per-point stat rates (attribute system). Each point of a stat applies this effect.
    /** Melee damage fraction added per STRENGTH point. Range: 0.03–0.08. */
    public static final float STR_MELEE_PER_POINT     = 0.05f;
    /** Ranged damage fraction added per MARKSMANSHIP point. Range: 0.02–0.06. */
    public static final float MRK_DAMAGE_PER_POINT    = 0.04f;
    /** Action-duration reduction fraction per AGILITY point. Range: 0.02–0.05. */
    public static final float AGI_SPEED_PER_POINT     = 0.03f;
    /** Raw dodge chance added per AGILITY point (before the cap). Range: 0.01–0.03. */
    public static final float AGI_DODGE_PER_POINT     = 0.02f;
    /** Maximum dodge probability regardless of AGILITY. Range: 0.20–0.50. */
    public static final float DODGE_CAP               = 0.35f;
    /** Max-HP added per TOUGHNESS point. Range: 3–8. */
    public static final int   TGH_HP_PER_POINT        = 5;
    /** Flat damage shaved off every HP-bound hit per TOUGHNESS point. Range: 1–3. */
    public static final int   TGH_REDUCTION_PER_POINT = 1;

    // =====================================================================================
    // SECTION 8 — STATUS / DOT MAGNITUDES (damage-over-time is damage; it counts toward TTK)
    // Per-turn tick damage and durations for the damage-over-time effects.
    // =====================================================================================

    // Rend (BLEED DoT — on hit).
    public static final float REND_DAMAGE_PER_TURN_BASE      = 2f;
    public static final float REND_DAMAGE_PER_TURN_PER_LEVEL = 0.5f;
    public static final float REND_DAMAGE_PER_TURN_CAP       = 6f;
    public static final int   REND_DURATION_TURNS            = 4;

    // Incendiary (BURN DoT — on hit).
    public static final float INCENDIARY_BURN_PER_TURN_BASE      = 3f;
    public static final float INCENDIARY_BURN_PER_TURN_PER_LEVEL = 0.5f;
    public static final float INCENDIARY_BURN_PER_TURN_CAP       = 7f;
    public static final int   INCENDIARY_BURN_DURATION           = 3;
    public static final int   INCENDIARY_INCINERATOR_EXTRA_TURNS = 1;

    // Incinerator weapon burn (the flamethrower's own burn DoT).
    public static final int FLAME_BURN_DAMAGE_PER_TURN = 6;
    public static final int FLAME_BURN_TURNS           = 4;

    // Stagger Rounds (STUN — on hit).
    public static final int STAGGER_STUN_DURATION = 1;

    // =====================================================================================
    // SECTION 9 — BALANCE RULE SYSTEM ANCHORS & BANDS (the math contract, idea 2)
    // The reference yardsticks every contract formula in GameMath compares against, plus
    // the per-role POWER and THREAT-POINT bands a new weapon / enemy must land inside.
    // See docs/balance-rule-system.txt for the full contract and the living table
    // (regenerate the table with BalanceReport whenever any number above changes).
    // =====================================================================================

    /**
     * Reference player Damage-Per-Turn — the FIXED yardstick every enemy's survival and
     * Threat-Point value is measured against. Originally set to the start shotgun's
     * sustained DPT ((1*50)/(1+1) = 25). The shotgun was since trimmed to 44/shot
     * (sustained DPT now 22) to pull its power score into the burst band, but this anchor
     * is deliberately HELD at 25: it is a stable reference for the whole enemy TP table,
     * not a live mirror of the current shotgun. Moving it would rescale every enemy's TP
     * at once (all eight currently sit in-band) for no balance gain. Keep it at 25 unless
     * you intend to re-tune the entire enemy roster.
     */
    public static final float REFERENCE_PLAYER_DPT = 25f;
    /**
     * Reference player effective HP — the MARINE start survivability (130 HP + 75 armour,
     * no dodge, no flat reduction). Used as the denominator for the player's Turns-To-Die
     * in golden-ratio checks.
     */
    public static final float REFERENCE_PLAYER_EHP = 205f;
    /**
     * Reference ammo efficiency (damage per ammo unit) the weapon power score normalises
     * against, so a reference-class weapon contributes a sqrt-factor of 1.0. Chosen so a
     * sustained-DPT-25 shotgun-class weapon scores near the top of the burst band, which
     * surfaces the shotgun as slightly over-band (a known follow-up tuning target).
     */
    public static final float REFERENCE_AMMO_EFFICIENCY = 40f;

    // --- WEAPON POWER BANDS (weaponPowerScore must land in the band for the chosen role).
    // Higher rarity does NOT raise these bands — it buys abilities (idea 5), not raw damage.
    public static final float WEAPON_POWER_SIDEARM_MIN    = 8f;
    public static final float WEAPON_POWER_SIDEARM_MAX    = 14f;
    public static final float WEAPON_POWER_WORKHORSE_MIN  = 12f;
    public static final float WEAPON_POWER_WORKHORSE_MAX  = 18f;
    public static final float WEAPON_POWER_BURST_MIN      = 18f;
    public static final float WEAPON_POWER_BURST_MAX      = 26f;
    public static final float WEAPON_POWER_HEAVY_MIN      = 24f;
    public static final float WEAPON_POWER_HEAVY_MAX      = 32f;

    // --- ENEMY THREAT-POINT BANDS (threatPoints must land in the band for the chosen role).
    public static final float ENEMY_TP_CHAFF_MIN      = 4f;
    public static final float ENEMY_TP_CHAFF_MAX      = 8f;
    public static final float ENEMY_TP_SOLDIER_MIN    = 9f;
    public static final float ENEMY_TP_SOLDIER_MAX    = 16f;
    public static final float ENEMY_TP_BRUISER_MIN    = 17f;
    public static final float ENEMY_TP_BRUISER_MAX    = 28f;
    public static final float ENEMY_TP_MINI_ELITE_MIN = 40f;
    public static final float ENEMY_TP_MINI_ELITE_MAX = 75f;

    // --- POSITIONAL MULTIPLIERS for the Threat-Point formula (designer classification).
    public static final float POSITIONAL_MULT_MELEE       = 1.00f;
    public static final float POSITIONAL_MULT_FAST_MELEE  = 1.15f;
    public static final float POSITIONAL_MULT_RANGED      = 1.30f;
    /** Added on top of the base positional multiplier when the enemy applies a DOT/stun/slow. */
    public static final float POSITIONAL_MULT_STATUS_BONUS = 0.25f;

    // --- GOLDEN-RATIO bands (turnsToDie / turnsToKill) per enemy role. Below = unfair/swingy;
    // above = harmless damage sponge. Bruisers are SUPPOSED to be scary 1v1, so their band is tighter.
    public static final float GOLDEN_RATIO_TRASH_MIN   = 3f;
    public static final float GOLDEN_RATIO_TRASH_MAX   = 8f;
    public static final float GOLDEN_RATIO_BRUISER_MIN = 2f;
    public static final float GOLDEN_RATIO_BRUISER_MAX = 4f;

    // --- DEPTH-COUPLING INVARIANT: playerPowerAtDepth / enemyThreatAtDepth must stay in
    // this band or the curve drifts unfair-hard (below) or trivial-easy (above).
    public static final float DEPTH_COUPLING_RATIO_MIN = 0.9f;
    public static final float DEPTH_COUPLING_RATIO_MAX = 1.2f;

    // =====================================================================================
    // SECTION 10 — RESOURCE SCARCITY MODEL & BANDS (idea 3)
    // The bands the scarcity contract checks against, plus the canonical MODEL FLOOR — a
    // fixed depth-1 reference encounter whose SUPPLY/DEMAND, scarcity ratio S, and net HP
    // drain are computed by GameMath and printed by BalanceReport. The SECTION 5 levers are
    // tuned against this model floor. See docs/balance-rule-system.txt and
    // .claude/agents/ideas/balance_order_3_resource_scarcity_economy.txt.
    // =====================================================================================

    // --- SCARCITY RATIO BANDS (S = ranged ammo SUPPLY / floor DEMAND, "fight everything").
    /** Floor-wide scarcity ratio must land in [MIN, MAX]: ammo covers most but not all damage. */
    public static final float SCARCITY_RATIO_FLOOR_MIN    = 0.75f;
    public static final float SCARCITY_RATIO_FLOOR_MAX    = 0.95f;
    /** The tuning target inside the band — just below 1 so every fight asks "shoot or save?". */
    public static final float SCARCITY_RATIO_FLOOR_TARGET = 0.85f;
    /** No SINGLE weapon's ammo economy may cover this fraction of a floor, forcing diversification. */
    public static final float SCARCITY_PER_WEAPON_MAX     = 0.60f;

    // --- ANTI-HOARD: a full reserve should bank only ~this many floors of that weapon's
    // run-demand (GameMath.reserveBankingFloors). Caps in SECTION 5 are tuned to this.
    public static final float RESERVE_BANKING_FLOORS_TARGET = 1.5f;

    // --- HEAL ECONOMY: each floor should be a small NET HP LOSS so HP stays precious but
    // the run stays survivable. Net drain as a fraction of reference eHP must land in band.
    public static final float HEAL_NET_DRAIN_FRACTION_MIN = 0.05f;
    public static final float HEAL_NET_DRAIN_FRACTION_MAX = 0.15f;

    // --- THE MODEL FLOOR (depth 1) — the worked reference encounter from idea 3.
    // Enemy composition (DEMAND = sum of these enemies' eHP). At depth 1 eHP == raw HP, so
    // DEMAND = 6*18 + 3*18 + 2*38 + 1*50 = 288 damage; total TP ~= 104 (a ~120-TP budget).
    public static final int MODEL_FLOOR_GORE_BITER_COUNT  = 6;
    public static final int MODEL_FLOOR_EYE_TYRANT_COUNT  = 3;
    public static final int MODEL_FLOOR_SHELL_BRUTE_COUNT = 2;
    public static final int MODEL_FLOOR_PLAGUE_HULK_COUNT = 1;
    /** Rooms on the model floor that can roll an ammo box (LEVER 1 source count). */
    public static final int MODEL_FLOOR_ROOM_COUNT        = 8;
    /** Floor-droppable ammo types the generator rolls uniformly (bullets/shells/cells/rockets/slugs). */
    public static final int MODEL_FLOOR_AMMO_TYPE_COUNT   = 5;

    // Heal-economy model inputs for the model floor.
    /** Expected medkits found on the model floor (mix of stim '+' and full 'H'). */
    public static final float MODEL_FLOOR_EXPECTED_MEDKITS        = 1.5f;
    /** Expected armour pickups found on the model floor (mix of shard 'a' and vest 'A'). */
    public static final float MODEL_FLOOR_EXPECTED_ARMOUR_PICKUPS = 1.0f;
    /** Average turns each enemy stays engaged and able to hit the player. */
    public static final int   MODEL_FLOOR_TURNS_ENGAGED_PER_ENEMY = 2;
    /** Fraction of incoming damage a skilled player cancels via positioning/avoidance. Range 0–1. */
    public static final float MODEL_FLOOR_AVOIDANCE_FACTOR        = 0.50f;
}
