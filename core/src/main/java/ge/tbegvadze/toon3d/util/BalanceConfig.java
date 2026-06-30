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

    // Heal magnitudes scaled ~1.8x in the economy rescale (idea-A, iteration 2): enemy damage
    // rose, so a floor's INCOMING damage rose, and the heals had to rise with it to keep the
    // per-floor net HP drain in the 5-15% band (SECTION 10). Player eHP itself is UNCHANGED.
    /** HP restored by a stim-pack ('+'). Was 18; scaled with the damage economy. Range: 20–50. */
    public static final int   MEDKIT_STIM_HEAL        = 32;
    /** HP restored by a full medkit ('H'). Was 50; scaled with the damage economy. Range: 50–110. */
    public static final int   MEDKIT_FULL_HEAL        = 90;
    /** Armour restored by an armour shard ('a'). Was 8; scaled with the damage economy. Range: 8–25. */
    public static final int   ARMOUR_SHARD_VALUE      = 14;
    /** Armour restored by a security vest ('A'). Was 35; scaled with the damage economy. Range: 40–90. */
    public static final int   ARMOUR_VEST_VALUE       = 62;

    /** Seconds per one-tile step. Lower = snappier, and you eat fewer enemy turns while repositioning. Range: 0.08–0.20. */
    public static final float PLAYER_MOVE_DURATION    = 0.12f;
    /** Seconds per 90° rotation. Same turn-economy effect as movement. Range: 0.06–0.18. */
    public static final float PLAYER_ROTATE_DURATION  = 0.09f;

    // =====================================================================================
    // SECTION 2 — ENEMY THREAT (HP / damage / range / cadence) — the numerator
    // The raw power of each enemy archetype at depth 1, plus the per-kill payouts.
    // moveEveryN = 1 means the enemy acts every player turn; 2 means every other turn.
    //
    // ECONOMY-RESCALE (idea-A, iteration 2): enemy eHP was raised ~3x and damage ~1.5x in a
    // COORDINATED pass so a standard soldier survives ~3-4 turns of the reference player DPT
    // (25) instead of being one-shot. This is the root-cause fix for the golden ratio (TTD/TTK)
    // reading structurally OVER on every enemy: at the old scale the player one/two-shot
    // everything (enemy eHP 18-50 vs player burst 44), pinning TTK at 1 while TTD was 20-30.
    // With the bigger eHP, soldiers/bruisers now land their golden ratio in band ([3,8]/[2,4])
    // under the sustained-DPT TTK metric (SECTION 9). It cascades by design — the TP role bands
    // (SECTION 9), encounter budget (SECTION 11), the scarcity DEMAND / ammo box sizes / reserve
    // caps (SECTION 5), and the heal magnitudes (SECTION 1) were ALL re-derived together so every
    // band still holds (verified via the standalone harness; not Gradle-built — proxy blocks the
    // Android plugin). See docs/balance-rule-system.txt and balance-ideas-review.txt.

    // GORE_BITER (spawn '3') — fast light melee; spawns in packs. (was 18 HP / 7 dmg)
    public static final int GORE_BITER_MAX_HEALTH          = 40;
    public static final int GORE_BITER_ATTACK_DAMAGE       = 12;
    public static final int GORE_BITER_MOVE_EVERY_N_TURNS  = 1;

    // EYE_TYRANT (spawn '2') — fast ranged kiter. (was 18 HP / 7 dmg)
    public static final int EYE_TYRANT_MAX_HEALTH          = 40;
    public static final int EYE_TYRANT_ATTACK_DAMAGE       = 11;
    public static final int EYE_TYRANT_RANGE_TILES         = 5;

    // ACID_DRONE (spawn '$') — ranged mechanical. (was 22 HP / 8 dmg)
    public static final int ACID_DRONE_MAX_HEALTH          = 90;
    public static final int ACID_DRONE_ATTACK_DAMAGE       = 12;
    public static final int ACID_DRONE_RANGE_TILES         = 4;
    public static final int ACID_DRONE_MOVE_EVERY_N_TURNS  = 1;

    // VOID_SHROUD (spawn '^') — fast stealth melee FLANKER (Pillar 2). (was 25 HP / 9 dmg)
    public static final int VOID_SHROUD_MAX_HEALTH         = 96;
    public static final int VOID_SHROUD_ATTACK_DAMAGE      = 13;
    public static final int VOID_SHROUD_MOVE_EVERY_N_TURNS = 1;
    /**
     * Flank strike bonus (Pillar 2): the Void Shroud prefers the tile behind the player's facing
     * and hits HARDER from that blind side, so "rotate to face it" is the counterplay. The base
     * 13 dmg becomes ~21 from behind — still well under the 25%-eHP telegraph cap (~51, idea 4, Pillar 5).
     */
    public static final float VOID_SHROUD_FLANK_DAMAGE_MULTIPLIER = 1.6f;

    // MIRE_WRAITH (spawn '5') — slow ground-based ranged acid; tanky. (was 38 HP / 7 dmg)
    public static final int MIRE_WRAITH_MAX_HEALTH         = 100;
    public static final int MIRE_WRAITH_ATTACK_DAMAGE      = 11;
    public static final int MIRE_WRAITH_RANGE_TILES        = 3;
    public static final int MIRE_WRAITH_MOVE_EVERY_N_TURNS = 2;

    // SHELL_BRUTE (spawn '4') — heavy CHARGER melee (Pillar 2). (was 38 HP / 13 dmg)
    public static final int SHELL_BRUTE_MAX_HEALTH         = 120;
    public static final int SHELL_BRUTE_ATTACK_DAMAGE      = 20;
    public static final int SHELL_BRUTE_MOVE_EVERY_N_TURNS = 1;
    /**
     * Charge rush damage multiplier (Pillar 2). After a one-turn telegraphed wind-up the brute
     * rushes down a cardinal lane; if it connects it hits for base * this. 20 * 2.4 = 48 dmg —
     * a meaty, READABLE hit. It is telegraphed, so it is allowed to exceed the 25%-eHP cap that
     * un-telegraphed attacks must respect (idea 4, Pillar 5). Sidestep it to make the rush whiff.
     */
    public static final float SHELL_BRUTE_CHARGE_DAMAGE_MULTIPLIER = 2.4f;
    /** Nearest cardinal-lane gap (tiles) that opens a charge; closer than this it just melees. Range: 2–3. */
    public static final int   SHELL_BRUTE_CHARGE_TRIGGER_MIN_TILES = 2;
    /** Farthest cardinal-lane gap (tiles) the brute will start a charge from (<= LOS). Range: 3–6. */
    public static final int   SHELL_BRUTE_CHARGE_TRIGGER_MAX_TILES = 5;

    // PLAGUE_HULK (spawn '1') — slow tank melee. (was 50 HP / 10 dmg)
    public static final int PLAGUE_HULK_MAX_HEALTH         = 120;
    public static final int PLAGUE_HULK_ATTACK_DAMAGE      = 16;
    public static final int PLAGUE_HULK_MOVE_EVERY_N_TURNS = 2;

    // IRON_STALKER (spawn '!') — armoured elite, melee + ranged; the big threat. (was 95 HP /
    // 16 melee / 11 ranged). A mini-elite is a deliberate spike: tanky AND hard-hitting, so its
    // golden ratio reads UNDER the duel band by design — you spend heavy weapons or avoid it, you
    // do not trade blows. Its TP (now ~254) prices that on the encounter budget.
    public static final int IRON_STALKER_MAX_HEALTH        = 230;
    public static final int IRON_STALKER_MELEE_DAMAGE      = 24;
    public static final int IRON_STALKER_RANGED_DAMAGE     = 17;
    public static final int IRON_STALKER_RANGE_TILES       = 4;
    public static final int IRON_STALKER_MOVE_EVERY_N_TURNS = 1;

    // -------------------------------------------------------------------------
    // Necrotic faction — five archetypes reusing the legacy individual-PNG sprites
    // (corruptor / vortex_eye / ghoul / crawler / revenant). Distinct stat niches and
    // tactical verbs keep them from duplicating the blight/infernal roster above.
    // -------------------------------------------------------------------------

    // GHOUL (spawn '~') — slow shambling melee CHAFF; relentless but easily outpaced.
    public static final int GHOUL_MAX_HEALTH          = 30;
    public static final int GHOUL_ATTACK_DAMAGE       = 9;
    public static final int GHOUL_MOVE_EVERY_N_TURNS  = 2;

    // CRAWLER (spawn 'z') — fast, fragile low-to-the-ground melee CHAFF; rushes in.
    public static final int CRAWLER_MAX_HEALTH         = 22;
    public static final int CRAWLER_ATTACK_DAMAGE      = 8;
    public static final int CRAWLER_MOVE_EVERY_N_TURNS = 1;

    // REVENANT (spawn 'K') — fast, hard-hitting undead SOLDIER melee; punishes a slow kill.
    public static final int REVENANT_MAX_HEALTH         = 110;
    public static final int REVENANT_ATTACK_DAMAGE      = 18;
    public static final int REVENANT_MOVE_EVERY_N_TURNS = 1;

    // VORTEX_EYE (spawn 'V') — short-range ranged CHAFF caster; weaker, closer kiter than Eye Tyrant.
    public static final int VORTEX_EYE_MAX_HEALTH         = 35;
    public static final int VORTEX_EYE_ATTACK_DAMAGE      = 9;
    public static final int VORTEX_EYE_RANGE_TILES        = 4;
    public static final int VORTEX_EYE_MOVE_EVERY_N_TURNS = 1;

    // BLIGHT_CORRUPTOR (spawn '*') — durable slow infected brute SOLDIER melee; grind it from range.
    public static final int BLIGHT_CORRUPTOR_MAX_HEALTH         = 130;
    public static final int BLIGHT_CORRUPTOR_ATTACK_DAMAGE      = 14;
    public static final int BLIGHT_CORRUPTOR_MOVE_EVERY_N_TURNS = 2;

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
    public static final int XP_REWARD_GHOUL            = 8;
    public static final int XP_REWARD_CRAWLER          = 7;
    public static final int XP_REWARD_REVENANT         = 16;
    public static final int XP_REWARD_VORTEX_EYE       = 9;
    public static final int XP_REWARD_BLIGHT_CORRUPTOR = 18;

    // Per-kill credit rewards (the currency payout for each archetype).
    public static final int CREDIT_REWARD_GORE_BITER   = 5;
    public static final int CREDIT_REWARD_EYE_TYRANT   = 6;
    public static final int CREDIT_REWARD_ACID_DRONE   = 8;
    public static final int CREDIT_REWARD_VOID_SHROUD  = 12;
    public static final int CREDIT_REWARD_MIRE_WRAITH  = 15;
    public static final int CREDIT_REWARD_SHELL_BRUTE  = 12;
    public static final int CREDIT_REWARD_PLAGUE_HULK  = 8;
    public static final int CREDIT_REWARD_IRON_STALKER = 40;
    public static final int CREDIT_REWARD_GHOUL            = 4;
    public static final int CREDIT_REWARD_CRAWLER          = 4;
    public static final int CREDIT_REWARD_REVENANT         = 11;
    public static final int CREDIT_REWARD_VORTEX_EYE       = 5;
    public static final int CREDIT_REWARD_BLIGHT_CORRUPTOR = 13;

    // =====================================================================================
    // SECTION 3 — DEPTH SCALING (how threat and reward grow per floor)
    // Compound HP/damage growth and linear credit growth applied as you descend.
    // =====================================================================================

    // DEPTH-COUPLING TUNED (see SECTION 9 invariant + GameMath.depthCouplingRatio).
    // These were 1.08 / 1.06. Enemy threat scales by the PRODUCT of both as a COMPOUND curve
    // (depthThreatScale), while the player's level-up power grows LINEARLY (1 + levels*budget/100,
    // GameMath.playerPowerAtDepth). At 1.08/1.06 the compound enemy curve outran the linear player
    // curve from depth 5 on (coupling ratio fell to 0.86 at d5 and ~0.40 by d15 — the game became
    // unwinnable at depth). Trimmed to 1.045/1.035 so the coupled ratio stays in the [0.9, 1.2]
    // invariant band through ~depth 14 (the Hell Baron's depth-15 floor lands at the band edge).
    // Each floor's enemies still get meaningfully stronger (~8% threat/floor); they just no longer
    // outpace the player's expected upgrades. Regenerate BalanceReport's DEPTH COUPLING table after
    // changing either of these. Range: 1.03–1.08.
    /** Per-floor compound HP multiplier: baseHP * scale^(depth-1). Range: 1.03–1.08. */
    public static final float ENEMY_HEALTH_SCALE_PER_DEPTH = 1.045f;
    /** Per-floor compound damage multiplier: baseDmg * scale^(depth-1). Range: 1.02–1.06. */
    public static final float ENEMY_DAMAGE_SCALE_PER_DEPTH = 1.035f;
    /** Per-floor linear credit bonus: base * (1 + (depth-1) * scale). Range: 0.05–0.25. */
    public static final float CREDIT_DEPTH_SCALE           = 0.12f;

    /**
     * Levels the average player is expected to gain per floor descended (~1 level/floor). This is the
     * player-side input to the depth-coupling invariant (SECTION 9): GameMath.playerPowerAtDepth lifts
     * the player's power multiplier by LEVEL_UP_BUDGET_PP power points per level gained. The boss
     * ruleset (SECTION 14) mirrors this as BOSS_EXPECTED_LEVELS_PER_DEPTH. Range: 0.7–1.3.
     */
    public static final float EXPECTED_LEVELS_PER_DEPTH    = 1.0f;

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
    // Full-charge powerScore is 45.0, OVER the 24-32 heavy band. RE-EVALUATED in the economy
    // rescale (idea-A, iteration 2, secondary target): DECISION = KEEP as a documented
    // scarcity-gated exception, do NOT fold into the band. Two reasons reinforce this now:
    //   (1) its 90-per-slug efficiency is gated by slug SCARCITY (supply ~1.1 slugs/floor, plus the
    //       tightest reserve cap RAILGUN_MAX_SLUGS=8 — see SECTION 5), not by raw damage; and
    //   (2) the rescale made enemies far tankier (mini-elite 230 eHP), so a single big-burst slug
    //       is a genuine elite-buster niche — exactly what the heavy/charge role should own.
    // Per docs/balance-rule-system.txt, nerfing the raw 90 would make it worthless rather than
    // merely scarce, so the raw number is left intact deliberately.
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

    // LEVER 2 — DROP SIZE: ammo box grants (rounds per pickup). RE-SCALED ~2.5x in the economy
    // rescale (idea-A, iteration 2): the model-floor DEMAND rose from 288 to 720 dmg (enemy eHP
    // ~3x), so SUPPLY had to rise proportionally to hold the floor-wide scarcity ratio S in
    // [0.75, 0.95]. With these sizes S = 0.83 floor-wide and < 0.6 per weapon (verified via the
    // harness). The bigger boxes are no longer the awkward 2-4 rounds the old low-eHP economy
    // forced — they fit clip sizes again (the clip-vs-eHP mismatch the old scale created is gone).
    public static final int AMMO_BOX_BULLETS    = 15;
    public static final int AMMO_BOX_SHELLS     = 5;
    public static final int AMMO_BOX_CELLS      = 10;
    public static final int AMMO_BOX_ROCKETS    = 2;
    public static final int RAILGUN_PICKUP_SLUGS = 2;
    public static final int FLAME_PICKUP_FUEL   = 25;
    public static final int GRENADE_PICKUP_AMMO = 3;

    // LEVER 3 — RESERVE CAP: the hoarding ceiling, tuned to ~1.5 floors of that weapon's
    // run-demand (see GameMath.reserveBankingFloors) so banking is limited. RE-DERIVED against the
    // rescaled model-floor DEMAND (720): cap = ~1.5 * DEMAND / damagePerUnit. The bigger enemy eHP
    // also RESOLVES the old clip-vs-eHP mismatch — the bullet cap (now 54) banks exactly 1.5 floors
    // and still comfortably holds a full 30-round Assault Rifle / 24-round Chaingun clip, so the
    // "one cap over target" exception the old scale forced is gone; every cap now hits the target.
    public static final int AMMO_RESERVE_CAP_BULLETS = 54;
    public static final int AMMO_RESERVE_CAP_SHELLS  = 24;
    public static final int AMMO_RESERVE_CAP_CELLS   = 38;
    public static final int AMMO_RESERVE_CAP_ROCKETS = 26;
    // RAILGUN_MAX_SLUGS kept the TIGHTEST banking (~1.0 floor, not 1.5) to honor the railgun's
    // documented power-band exception (powerScore 45 > heavy band 24-32): slug SCARCITY, not raw
    // damage, is what holds it in check. The slug SUPPLY (~1.1 slugs/floor) is the true gate; the
    // tight cap reinforces it. The elite-busting niche of a 90-per-slug hit is MORE valuable now
    // that enemies are tankier, so the raw 90 is kept (see SECTION 4 + docs).
    public static final int RAILGUN_MAX_SLUGS        = 8;
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

    // ---------------------------------------------------------------------------------
    // LEVEL-UP CARD SYSTEM — power budget & re-priced boons (idea 5: build diversity)
    //
    // Every level-up offers LEVEL_UP_CARDS_OFFERED cards drawn from four pools. Each card
    // costs the SAME power budget (LEVEL_UP_BUDGET_PP, in "power points" = %-gain to the
    // reference DPT or eHP from SECTION 9), so no card is a strict upgrade over another —
    // they differ in KIND, not amount. Because each pick is budget-equal, the player's
    // TOTAL power at level L is L * LEVEL_UP_BUDGET_PP regardless of which cards were taken;
    // only its SHAPE differs. That is how build diversity stays inside the depth-coupling
    // band (SECTION 9) for every build. See docs/balance-rule-system.txt (section [D]).
    //
    // The three OLD flat boons (HP / armour / damage) survive as ONE card each, RE-PRICED
    // from their legacy magnitudes to ~LEVEL_UP_BUDGET_PP so they sit on the same curve as
    // every attribute card. Run BalanceReport to see each card's computed PP and band verdict.
    // ---------------------------------------------------------------------------------

    /** The fixed power budget every level-up card costs, in power points (% of reference DPT/eHP). Range: 8–16. */
    public static final float LEVEL_UP_BUDGET_PP        = 12f;
    /** Allowed fractional spread around the budget a single card may cost (±15%). A card outside this is rejected. */
    public static final float LEVEL_UP_BUDGET_TOLERANCE = 0.15f;
    /** How many cards are drawn and shown on each level-up. The overlay renders exactly this many. Range: 2–4. */
    public static final int   LEVEL_UP_CARDS_OFFERED    = 3;
    /** Per-prior-pick draw-weight bonus that biases new offers toward the player's emerging build. Range: 0.0–1.0. */
    public static final float LEVEL_UP_DRAW_BIAS_PER_PICK = 0.6f;

    // --- PP-PRICING REFERENCES (used only to COMPUTE each card's power-point value; see GameMath).
    /** Fraction of a flat per-shot damage bonus that lands as sustained DPT at the reference weapon (shotgun, clip 1 / reload 1 → 0.5). */
    public static final float CARD_FLAT_DAMAGE_DPT_FRACTION = 0.5f;
    /** Average fraction of attacks made with a MELEE weapon — discounts STRENGTH cards, whose damage only applies to melee. Range: 0.5–1.0. */
    public static final float CARD_MELEE_UTILIZATION        = 0.8f;
    /** Reference incoming hit (HP-bound) used when pricing TOUGHNESS flat-reduction into eHP. Matches the doc's tough-build example. */
    public static final int   CARD_PRICING_AVERAGE_HIT      = 12;

    // --- CARD MAGNITUDES (sized so each card's PP lands inside the budget band). Attribute steps:
    /** STRENGTH points granted by the Brutal Strength card (+15% melee dmg before the melee-utilization discount). */
    public static final int CARD_STRENGTH_STEP     = 3;
    /** MARKSMANSHIP points granted by the Marksman Training card (+12% ranged dmg, +9% accuracy). */
    public static final int CARD_MARKSMANSHIP_STEP = 3;
    /** AGILITY points granted by the Evasion Training card (+10% dodge, +15% faster actions). */
    public static final int CARD_AGILITY_STEP      = 5;
    /** TOUGHNESS points granted by the Toughened Hide card (+5 Max HP, +1 flat damage reduction). One point is potent. */
    public static final int CARD_TOUGHNESS_STEP    = 1;

    // --- TRADE-OFF CARD MAGNITUDES (net PP ≈ budget, but high variance: a big gain on one axis paid for on another).
    /** Glass Cannon: flat per-shot damage gained. Paired with a Max-HP cost. */
    public static final int CARD_GLASS_CANNON_DAMAGE      = 10;
    /** Glass Cannon: Max-HP sacrificed for the damage. */
    public static final int CARD_GLASS_CANNON_HP_COST     = 18;
    /** Iron Constitution: Max-HP gained. Paired with a Max-armour cost. */
    public static final int CARD_IRON_CONSTITUTION_HP     = 45;
    /** Iron Constitution: Max-armour sacrificed for the health. */
    public static final int CARD_IRON_CONSTITUTION_ARMOR  = 22;
    /** Reckless Charge: AGILITY gained (dodge + speed). Paired with a Max-armour cost. */
    public static final int CARD_RECKLESS_CHARGE_AGILITY  = 8;
    /** Reckless Charge: Max-armour sacrificed for the mobility. */
    public static final int CARD_RECKLESS_CHARGE_ARMOR    = 12;

    /** Flat max-HP gained when the Vitality (legacy HP_BOOST) card is chosen. Re-priced to ~budget PP. Range: 15–40. */
    public static final int LEVEL_UP_HP_BONUS     = 25;
    /** Flat max-armour gained when the Combat Armour (legacy ARMOR_BOOST) card is chosen. Re-priced 18→24 to ~budget PP. Range: 10–30. */
    public static final int LEVEL_UP_ARMOR_BONUS  = 24;
    /** Flat per-shot damage gained when the Hollow Points (legacy DAMAGE_BOOST) card is chosen. Re-priced 8→6 to ~budget PP. Range: 4–15. */
    public static final int LEVEL_UP_DAMAGE_BONUS = 6;

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
    // CONTRACT DECISION (idea-A, iteration 2): held at 25 and now ALSO the golden-ratio TTK metric.
    // The golden ratio (TTD/TTK) previously divided enemy eHP by the player's BEST BURST (shotgun
    // 44) for TTK, which pinned TTK at 1 for any enemy the shotgun one-shot. The doc listed using
    // the SUSTAINED reference DPT instead as a legitimate contract option (b); this iteration ADOPTS
    // it — BalanceReport now computes TTK as ceil(enemyEHP / REFERENCE_PLAYER_DPT). It is the
    // player's realistic sustained kill rate, not a one-shot spike, so it is the fair denominator,
    // and it keeps the metric semantically identical to the TP normaliser below (one yardstick).
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
    // RE-SCALED ~4x in the economy rescale (idea-A, iteration 2). REFERENCE_PLAYER_DPT is held at
    // 25 (semantically honest: survivalTurns = eHP/25 = the turns the enemy survives the player's
    // sustained fire), so the beefier enemies genuinely have ~4x the Threat Points — they survive
    // ~3x longer while hitting ~1.5x harder. The bands rise to match that honest TP; they are NOT
    // an artificial renormalisation. Enemy COUNT per floor stays constant because the encounter
    // budget (SECTION 11) was scaled by the same factor. Verified via the harness.
    public static final float ENEMY_TP_CHAFF_MIN      = 16f;
    public static final float ENEMY_TP_CHAFF_MAX      = 34f;
    public static final float ENEMY_TP_SOLDIER_MIN    = 36f;
    public static final float ENEMY_TP_SOLDIER_MAX    = 66f;
    public static final float ENEMY_TP_BRUISER_MIN    = 70f;
    public static final float ENEMY_TP_BRUISER_MAX    = 120f;
    public static final float ENEMY_TP_MINI_ELITE_MIN = 160f;
    public static final float ENEMY_TP_MINI_ELITE_MAX = 310f;

    // --- POSITIONAL MULTIPLIERS for the Threat-Point formula (designer classification).
    public static final float POSITIONAL_MULT_MELEE       = 1.00f;
    public static final float POSITIONAL_MULT_FAST_MELEE  = 1.15f;
    public static final float POSITIONAL_MULT_RANGED      = 1.30f;
    /** Added on top of the base positional multiplier when the enemy applies a DOT/stun/slow. */
    public static final float POSITIONAL_MULT_STATUS_BONUS = 0.25f;

    // --- GOLDEN-RATIO bands (turnsToDie / turnsToKill) per enemy role. Below = unfair/swingy;
    // above = harmless damage sponge. Bruisers are SUPPOSED to be scary 1v1, so their band is tighter.
    // These bands are UNCHANGED, but the economy rescale (idea-A, iteration 2) finally makes them
    // SATISFIABLE and SATISFIED: every soldier now reads 4.0-5.2 and the Shell Brute bruiser 2.2,
    // all in band. CHAFF is exempt (balanced by PACK TP, not the lone unit's ratio) and reads ~9.
    // MINI-ELITE is a deliberate spike (tanky AND hard-hitting) and reads UNDER the duel band by
    // design — the player spends heavy weapons or avoids it rather than trading blows.
    public static final float GOLDEN_RATIO_TRASH_MIN   = 3f;
    public static final float GOLDEN_RATIO_TRASH_MAX   = 8f;
    public static final float GOLDEN_RATIO_BRUISER_MIN = 2f;
    public static final float GOLDEN_RATIO_BRUISER_MAX = 4f;

    // --- DEPTH-COUPLING INVARIANT: playerPowerAtDepth / enemyThreatScale must stay in this band or
    // the curve drifts unfair-hard (below) or trivial-easy (above). Now COMPUTED and verified:
    // GameMath.playerPowerAtDepth (linear level-up power curve) over GameMath.depthThreatScale
    // (compound enemy curve) via GameMath.depthCouplingRatio; BalanceReport prints the DEPTH COUPLING
    // table across depths. This invariant is what the SECTION 3 enemy depth-scale tune defends.
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
    // Enemy composition (DEMAND = sum of these enemies' eHP). At depth 1 eHP == raw HP. After the
    // economy rescale (idea-A, iteration 2) the enemy eHP is ~3x higher, so:
    // DEMAND = 6*40 + 3*40 + 2*120 + 1*120 = 720 damage; total TP ~= 432 (a ~500-TP budget).
    // The SECTION 5 ammo levers and the heal inputs below are tuned against THIS rescaled floor.
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

    // =====================================================================================
    // SECTION 11 — ENCOUNTER BUDGET (idea 4, Pillar 1) — difficulty as a dial
    // The level generator SPENDS a Threat-Point budget per floor instead of rolling enemies
    // at random (EncounterBudgetPlanner). The base budget is the depth-1 reference; it scales
    // per floor by GameMath.floorThreatPointBudget using the SECTION 3 depth curve, and each
    // enemy's TP cost scales by the same curve (GameMath.enemyThreatAtDepth), so the enemy
    // COUNT stays roughly constant across depth while each enemy gets stronger. The model
    // floor (SECTION 10) totals ~432 TP after the economy rescale, so a 500-TP base budget
    // reproduces a comparable depth-1 roster. The composition fractions enforce idea 4's "spend the
    // budget tastefully" rules: one anchor, no mono-type rooms, no single oversized room.
    // =====================================================================================

    // RE-SCALED 120 -> 500 in the economy rescale (idea-A, iteration 2). Both the budget AND each
    // enemy's TP cost rose by the same ~4x (REFERENCE_PLAYER_DPT held at 25), so the enemy COUNT
    // per floor is scale-invariant — the depth-1 roster is still ~11-13 enemies, just each tankier.
    /** Depth-1 floor Threat-Point budget the generator spends on enemies. Range: 350–650. */
    public static final float FLOOR_BASE_THREAT_POINT_BUDGET = 500f;

    /** Reserve at least this fraction of the floor budget for the single anchor enemy. Range: 0.10–0.25. */
    public static final float ENCOUNTER_ANCHOR_BUDGET_FRACTION_MIN = 0.15f;
    /** Reserve at most this fraction of the floor budget for the single anchor enemy. Range: 0.25–0.40. */
    public static final float ENCOUNTER_ANCHOR_BUDGET_FRACTION_MAX = 0.30f;

    /** No single enemy TYPE may consume more than this fraction of the floor budget (variety rule). Range: 0.30–0.55. */
    public static final float ENCOUNTER_MAX_SINGLE_TYPE_FRACTION = 0.40f;

    /**
     * No single (non-anchor) room may hold more than this fraction of the floor budget. Lowered
     * 0.35 -> 0.25 alongside the load-balanced room distribution (LevelGenerator /
     * LinearCorridorGenerator placeEnemyLoadBalanced): with enemies now fanned out across the whole
     * floor this is a safety ceiling that stops a single room from becoming an un-winnable pile-up
     * for a low-level player, rather than the primary distribution driver. Range: 0.25–0.45.
     */
    public static final float ENCOUNTER_PER_ROOM_TP_FRACTION_CAP = 0.25f;

    /**
     * Stop adding fill enemies once spent TP reaches this fraction of the budget — leaves a
     * little headroom so a roster never overshoots the budget. Range: 0.85–1.0.
     */
    public static final float ENCOUNTER_BUDGET_FILL_TARGET_FRACTION = 0.95f;

    /**
     * Chance a floor is a deliberate "elite gauntlet" whose anchor is a mini-elite that exceeds
     * the normal anchor reserve (the gauntlet-climax exception in idea 4). Range: 0.0–0.30.
     */
    public static final float ENCOUNTER_ELITE_ANCHOR_FLOOR_CHANCE = 0.15f;
    /** Earliest depth an elite-gauntlet floor may appear, so floor 1 is never a mini-elite spike. Range: 2–5. */
    public static final int   ENCOUNTER_ELITE_ANCHOR_MIN_DEPTH    = 3;

    // =====================================================================================
    // SECTION 12 — TERRAIN HAZARDS (idea 4, Pillar 3) — the two-sided chain-reaction system
    // Fire and toxic floor tiles tick damage onto ANY host standing on them — player AND
    // enemies — by applying the existing BURNING / POISONED status effects (SECTION on status
    // effects in EffectConstants owns the per-turn magnitudes). Fire spreads along spreadable
    // floor/stain tiles and chain-detonates explosive barrels; toxic is a static area-denial
    // pool. Hazards MUST hurt the player too (idea 4 balance note) — that two-sidedness is the
    // whole tactic: a hazard can win the fight for you OR kill you if you misposition.
    // The HazardManager drives the simulation; HazardTickSubscriber ticks it once per turn.
    // =====================================================================================

    /** Turns a fire tile burns before dying out (each turn it tries to spread). Range: 2–6. */
    public static final int   HAZARD_FIRE_LIFETIME_TURNS   = 3;
    /** Turns a toxic pool lingers before dissipating. Range: 3–8. */
    public static final int   HAZARD_TOXIC_LIFETIME_TURNS  = 6;

    /**
     * BURNING duration (turns) a fire tile applies to a host on it each turn. Short: standing in
     * fire re-applies (REFRESH_DURATION) so it persists, but leaving stops the burn quickly so
     * the player can escape — the counterplay. Damage/turn = EffectConstants.BURN_DAMAGE_PER_TURN.
     */
    public static final int   HAZARD_FIRE_BURN_TURNS       = 2;
    /**
     * POISONED duration (turns) a toxic pool applies each turn. Toxic STACKS (STACK_MAGNITUDE), so
     * standing in it escalates — area denial. Damage/turn = stacks * EffectConstants.POISON_DAMAGE_PER_STACK.
     */
    public static final int   HAZARD_TOXIC_POISON_TURNS    = 3;

    /**
     * Per-turn chance a fire tile spreads to ONE eligible cardinal-neighbour floor/stain tile. Range: 0.1–0.6.
     * Kept sub-critical: lifetime (3) × spread (0.18) = 0.54 < 1, so the expected number of new tiles each
     * fire spawns over its life is below one. That guarantees the blaze shrinks instead of growing — combined
     * with the "burned-out tile can't be re-ignited by spread" rule in HazardManager, a fire sweeps a small
     * patch and then dies out within a few turns rather than engulfing the whole level.
     */
    public static final float HAZARD_FIRE_SPREAD_CHANCE    = 0.18f;
    /** Per-turn chance a toxic pool creeps to ONE eligible neighbour (low — pools are area denial). Range: 0.0–0.25. */
    public static final float HAZARD_TOXIC_SPREAD_CHANCE   = 0.10f;

    /** Chance a detonating explosive barrel ignites fire on each eligible non-wall neighbour (explosive→fire chain). Range: 0.0–1.0. */
    public static final float HAZARD_EXPLOSION_IGNITE_CHANCE = 0.50f;

    /** Cardinal radius of the toxic cloud a Plague Hulk leaves where it dies (0 = its tile only). Range: 0–2. */
    public static final int   HAZARD_PLAGUE_HULK_DEATH_CLOUD_RADIUS = 1;

    /**
     * Turns a careless player is assumed to stand in one hazard tile, used ONLY by
     * GameMath.hazardTileThreatPoints to fold hazard danger into the Threat-Point contract
     * (a hazard room raises its effective floor TP — idea 4, Pillar 3). A skilled player leaves
     * sooner; this is the "you mispositioned" reference, not the spread lifetime. Range: 1–3.
     */
    public static final int   HAZARD_THREAT_TURNS_STOOD    = 2;

    // =====================================================================================
    // SECTION 13 — TELEGRAPH & COUNTERPLAY (idea 4, Pillar 5) — fairness contract
    // A turn-based game is only tactical if big threats are READABLE before they land. The rule:
    // every attack that can deal more than this fraction of the reference player's eHP in ONE hit
    // MUST be telegraphed (a wind-up the player can react to) or otherwise avoidable. Burst damage
    // without warning is banned — a death must feel like "I made a mistake", not a dice roll.
    // BalanceReport's TELEGRAPH AUDIT checks every attack against this cap.
    // =====================================================================================

    /** Max fraction of reference eHP an UN-telegraphed single hit may deal (~51 HP of 205). Range: 0.20–0.30. */
    public static final float TELEGRAPH_MAX_UNTELEGRAPHED_HIT_FRACTION = 0.25f;

    // =====================================================================================
    // SECTION 14 — BOSS BALANCE RULESET (idea 6) — RULES & MATH, fights deferred
    // Bosses break the trash-mob threat math (a single entity meant to survive many turns
    // and threaten a PREPARED player), so the SECTION 9 golden-ratio / TP bands do NOT apply
    // to them. A boss is tuned to a fight-LENGTH target and a phase-structured threat curve
    // instead — see GameMath's BOSS BALANCE RULESET block and docs/balance-rule-system.txt
    // (Boss appendix). Boss FIGHTS are deferred (they need story/run structure), so these are
    // a CONTRACT the future boss work must satisfy: the targets/bands below feed the boss
    // formulas to RE-DERIVE boss HP/damage/reward, never to bless a literal HP constant. The
    // current placeholder boss stats (OVERSEER/CORRUPTOR/HELL_BARON in EnemyConstants, and
    // XP/CREDIT_REWARD_BOSS_BASE in GameBalance) are flagged "to be re-derived via this ruleset".
    // =====================================================================================

    // --- RULE 1: HP from fight length. Target fight-length BANDS (turns), never a flat HP.
    /** Act-boss target fight length (turns): lower bound of the band. Range: 14–22. */
    public static final float BOSS_TARGET_FIGHT_TURNS_ACT_MIN       = 18f;
    /** Act-boss target fight length (turns): upper bound of the band. Range: 32–48. */
    public static final float BOSS_TARGET_FIGHT_TURNS_ACT_MAX       = 40f;
    /** Run-final boss target fight length (turns): lower bound (FUTURE; no run-final boss yet). Range: 36–48. */
    public static final float BOSS_TARGET_FIGHT_TURNS_RUN_FINAL_MIN = 40f;
    /** Run-final boss target fight length (turns): upper bound (FUTURE). Range: 52–70. */
    public static final float BOSS_TARGET_FIGHT_TURNS_RUN_FINAL_MAX = 60f;

    /** Multi-phase factor ADDED to bossEffectiveHitPoints per phase the player effectively re-fights (RULE 1/4). */
    public static final float BOSS_MULTI_PHASE_FACTOR_PER_PHASE     = 1.0f;

    // --- RULE 2: cap the fight from above too (no sponges).
    /** Worst-case fight length for a player who plays well must stay <= this * target (RULE 2). Range: 1.3–1.7. */
    public static final float BOSS_UPPER_FIGHT_TURNS_MULTIPLIER     = 1.5f;

    // --- RULE 3: lethal-but-counterable. survivalCheckRatio = (playerEHP/bossDPT)/fightTurns band.
    /** A no-heal player should die no SOONER than this fraction of the fight (below = coin-flip). Range: 0.35–0.45. */
    public static final float BOSS_SURVIVAL_CHECK_RATIO_MIN         = 0.40f;
    /** ...and no LATER than this (above = the boss can't threaten a careless player). Range: 0.65–0.75. */
    public static final float BOSS_SURVIVAL_CHECK_RATIO_MAX         = 0.70f;
    /** The tuning target inside the band: a no-heal player dies at half the fight, skill/heals buy the rest. */
    public static final float BOSS_SURVIVAL_CHECK_RATIO_TARGET      = 0.50f;

    // --- RULE 3 (fairness caps): single-hit limits as a fraction of reference eHP.
    /** Hard cap: NO single boss attack may exceed this fraction of player eHP, telegraphed or not. Range: 0.30–0.40. */
    public static final float BOSS_HARD_SINGLE_HIT_FRACTION         = 0.35f;
    // The "must be telegraphed above this" cap reuses TELEGRAPH_MAX_UNTELEGRAPHED_HIT_FRACTION (0.25), SECTION 13.

    // --- RULE 4: phases structure the threat curve. Act bosses use 2–3 equal HP phases.
    /** Minimum phase count for an act boss (each phase escalates ONE mechanic at an HP threshold). Range: 2–2. */
    public static final int   BOSS_ACT_PHASE_COUNT_MIN              = 2;
    /** Maximum phase count for an act boss. Range: 3–4. */
    public static final int   BOSS_ACT_PHASE_COUNT_MAX              = 3;

    // --- RULE 1/5 (build check): expected-player-DPT-at-depth inputs. bossEffectiveHitPoints is
    // tied to expectedPlayerSustainedDamagePerTurn(depth) so an under-powered player cannot out-DPS
    // the fight window. Expected OFFENCE power points by a boss depth =
    //   BOSS_EXPECTED_OFFENCE_BUDGET_FRACTION * LEVEL_UP_BUDGET_PP * (BOSS_EXPECTED_LEVELS_PER_DEPTH * depth).
    /** Levels the average player is expected to gain per floor descended (~1 level/floor). Mirrors the
     *  depth-coupling input EXPECTED_LEVELS_PER_DEPTH (SECTION 3) — single source of truth. Range: 0.7–1.3. */
    public static final float BOSS_EXPECTED_LEVELS_PER_DEPTH        = EXPECTED_LEVELS_PER_DEPTH;
    /** Fraction of the level-up power budget the average player invests in OFFENCE (the rest is survival/utility). Range: 0.3–0.6. */
    public static final float BOSS_EXPECTED_OFFENCE_BUDGET_FRACTION = 0.50f;

    // --- RULE 6: reward priced by consumption * a risk premium (so a boss REFUNDS the fight + profit).
    /** Profit margin a boss pays over the ammo+heal resources its fight consumes (> 1, never a net loss). Range: 1.2–1.6. */
    public static final float BOSS_REWARD_RISK_PREMIUM             = 1.3f;
}
