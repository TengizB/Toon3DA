package ge.tbegvadze.toon3d.util;

/**
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
 * <h2>Consolidation is COMPLETE (Balance Authority, new-game-balancr order 1)</h2>
 * The formerly-scattered gameplay magnitudes now all live here: shared status-effect
 * magnitudes (SECTION 8, previously {@link EffectConstants}), boss stats and boss AI
 * tactic weights (SECTION 14, previously {@link EnemyConstants} / {@link GameBalance}),
 * the whole weapon ABILITY catalogue (SECTION 15, previously {@link GameBalance}), and
 * the shop economy (SECTION 16, previously {@link GameBalance}). The origin files keep
 * re-export shims so call sites did not churn.
 *
 * <p>Every value in this file is constrained by the declarative rule schema in
 * {@link BalanceSchema}; {@code BalanceAuditTest} runs that schema under
 * {@code ./gradlew test} and FAILS THE BUILD when a value leaves its band without an
 * explicit waiver. Before adding a weapon / enemy / item, read
 * {@code docs/game-balance-authority.txt}, add the tuning number HERE first, declare its
 * role/band in {@link BalanceSchema}, then reference it from the matching
 * {@code *Constants} file.
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
    // Android plugin). See docs/game-balance-authority.txt and balance-ideas-review.txt.

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
    /**
     * Probability that a charge which reaches the player STOPS next to them and does NOT land the hit,
     * ending its rush adjacent and unwinded so the player gets a guaranteed swing before it attacks
     * again (design feedback: a charge should usually leave the brute punishable, not just delete HP).
     * The remaining {@code 1 - this} of reaching charges land the full multiplier hit. Range: 0.5–0.9.
     */
    public static final float SHELL_BRUTE_CHARGE_STOP_SHORT_CHANCE = 0.75f;

    // PLAGUE_HULK (spawn '1') — slow tank melee. (was 50 HP / 10 dmg)
    public static final int PLAGUE_HULK_MAX_HEALTH         = 120;
    public static final int PLAGUE_HULK_ATTACK_DAMAGE      = 16;
    public static final int PLAGUE_HULK_MOVE_EVERY_N_TURNS = 2;
    /**
     * Low-HP finisher (.claude/agents/ideas/plague-hulk-self-destruct.txt): once HP drops to or below
     * this fraction of max the Hulk primes a self-destruct instead of attacking/moving/defending. Kept
     * BELOW DEFEND_HP_THRESHOLD_FRACTION (0.50) so the two brace states never collide — a Hulk turtles
     * first, then only becomes a bomb once critically low.
     */
    public static final float PLAGUE_HULK_SELF_DESTRUCT_HP_PERCENT              = 0.30f;
    /** Telegraphed turns the Hulk braces before detonating; the player sees a live countdown. */
    public static final int   PLAGUE_HULK_SELF_DESTRUCT_BRACE_TURNS             = 2;
    /** Cardinal-arm reach (tiles) of the direct blast; an arm stops early at the first wall. */
    public static final int   PLAGUE_HULK_SELF_DESTRUCT_BLAST_RADIUS_TILES      = 2;
    /**
     * Direct blast damage multiplier on scaledAttackDamage() if the countdown reaches zero. 16 * 3.0 =
     * 48 (~23% of the 205 reference eHP) — telegraphed for two full turns, so it is allowed to exceed
     * the 25%-eHP cap for un-telegraphed hits (docs/game-balance-authority.txt TELEGRAPH & COUNTERPLAY).
     */
    public static final float PLAGUE_HULK_SELF_DESTRUCT_BLAST_DAMAGE_MULTIPLIER = 3.0f;
    /** Hard cap on the blast hit regardless of depth/EMPOWERED scaling (~33% eHP, under the 35% boss cap). */
    public static final int   PLAGUE_HULK_SELF_DESTRUCT_BLAST_DAMAGE_MAX        = 68;
    /** Massive toxic cross radius left behind if the Hulk survives to detonate. */
    public static final int   PLAGUE_HULK_SELF_DESTRUCT_TOXIC_RADIUS_TILES      = 3;
    /**
     * Minimal toxic cross radius left behind when the player kills the Hulk before it detonates —
     * mirrors the ordinary death cloud (defined later in this file); kept as its own named constant
     * so the self-destruct feature reads independently of that unrelated section.
     */
    public static final int   PLAGUE_HULK_MINIMAL_TOXIC_RADIUS_TILES           = 1;

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

    // NECROTIC FACTION — CONTRACT PASS (game-balance-tuning): the five archetypes below were added
    // AFTER the iteration-2 economy rescale and were never entered into the balance contract, so their
    // Threat Points sat OUT of band while they were live in the EncounterBudgetPlanner fill pool
    // (corrupting every procedural floor's budget). Re-tuned so each lands in its role's TP band and
    // golden ratio, verified by BalanceReport's ENEMIES table. See docs/game-balance-authority.txt.

    // GHOUL (spawn '~') — slow shambling melee CHAFF; relentless but easily outpaced.
    // Was 30 HP / 9 dmg -> TP 10.8, UNDER the chaff band (16-34): under-costed filler. Raised to
    // 42 HP / 13 dmg -> TP 21.8, mid-chaff, dies in 2 reference hits like the other chaff.
    public static final int GHOUL_MAX_HEALTH          = 42;
    public static final int GHOUL_ATTACK_DAMAGE       = 13;
    public static final int GHOUL_MOVE_EVERY_N_TURNS  = 2;

    // CRAWLER (spawn 'z') — fast, fragile low-to-the-ground melee CHAFF; rushes in.
    // Was 22 HP / 8 dmg -> TP 8.1, far UNDER the chaff band. Leaned into the fragile-glass-cannon
    // niche: 24 HP / 15 dmg (fast melee) -> TP 16.6, in band; a 1-hit kill that punishes if ignored.
    public static final int CRAWLER_MAX_HEALTH         = 24;
    public static final int CRAWLER_ATTACK_DAMAGE      = 15;
    public static final int CRAWLER_MOVE_EVERY_N_TURNS = 1;

    // REVENANT (spawn 'K') — fast, hard-hitting undead melee; punishes a slow kill.
    // Was classified SOLDIER but its stats (TP 79, golden ratio 2.4) are honestly BRUISER-tier — a
    // "soldier" that duels like a bruiser is an unfair surprise. Reclassified to BRUISER in
    // EnemyType.role() (TP 79 in the 70-120 bruiser band, gr 2.4 in the [2,4] bruiser band); it is
    // the fast, non-charging bruiser counterpart to the Shell Brute charger. Stats unchanged.
    public static final int REVENANT_MAX_HEALTH         = 110;
    public static final int REVENANT_ATTACK_DAMAGE      = 18;
    public static final int REVENANT_MOVE_EVERY_N_TURNS = 1;

    // VORTEX_EYE (spawn 'V') — short-range ranged CHAFF caster; weaker, closer kiter than Eye Tyrant.
    // TP 16.4 — already lands (just) inside the chaff band, so left unchanged by the contract pass.
    public static final int VORTEX_EYE_MAX_HEALTH         = 35;
    public static final int VORTEX_EYE_ATTACK_DAMAGE      = 9;
    public static final int VORTEX_EYE_RANGE_TILES        = 4;
    public static final int VORTEX_EYE_MOVE_EVERY_N_TURNS = 1;

    // BLIGHT_CORRUPTOR (spawn '*') — durable slow infected summoner SOLDIER melee; grind it from range.
    // Was 130 HP / 14 dmg -> TP 72.8, OVER the soldier band (36-66) with a bruiser-tier golden ratio.
    // HP trimmed 130 -> 115 -> TP 64.4 (top of the soldier band) and golden ratio 3.0 (in the [3,8]
    // soldier band); lower HP also means its SUMMON move-set floods the room a little less.
    //
    // ORDER-5 CYCLE-AVERAGED RE-FIT (the predicted "rebalance fallout"): once its SUMMON verb is PRICED
    // (GameMath.specialEquivalentSummon amortises ~1.5 summoned bodies worth of TP into its cycle-averaged
    // DPT), the old 115-HP body read ~92 TP — OVER the soldier band, exactly as the order-5 idea predicted.
    // Trimmed 115 -> 90: the summon adds a flat ~26 TP regardless of body HP, so the BODY must stay light
    // to keep the whole archetype a SOLDIER. New cycle-averaged TP ~64 (top of the 36-66 band), golden
    // ratio ceil(90/25)=4 -> 15/4 = 3.75 (in [3,8]); the lighter body also floods the room a touch less.
    public static final int BLIGHT_CORRUPTOR_MAX_HEALTH         = 90;
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

    // -------------------------------------------------------------------------
    // BLOCK & DEFEND (strategy-combat-order-3) — the shared damage-absorption buffer.
    // When an enemy commits DEFEND it gains Block on its next turn; incoming damage is
    // subtracted from Block before HP (see the 5-step mitigation pipeline in
    // docs/game-balance-authority.txt). Block is transient eHP priced at ~1 turn of survival
    // (baseBlock ≈ REFERENCE_PLAYER_DPT), so it never inflates a role's TTK out of its
    // golden-ratio band. Verified by BalanceReport's BLOCK section.
    // -------------------------------------------------------------------------

    /** Hard cap on any actor's Block. Stops defend-spam from stacking into immortality. Range: 40–90. */
    public static final int   BLOCK_MAX                       = 60;
    /**
     * World turns a gained Block survives before the StatusEffectController tick zeroes it.
     * 1 = Block protects through exactly the single player turn during which the player reacts
     * to the active shield (the enemy gains it on its defend turn; the next status tick — which
     * fires AFTER that player turn's damage resolves — clears it). Range: 1–2.
     */
    public static final int   BLOCK_DECAY_TURNS               = 1;

    /** Depth-1 base Block a SOLDIER-role enemy gains on DEFEND (≈ reference player DPT). Range: 16–30. */
    public static final int   DEFEND_BLOCK_GAIN_SOLDIER       = 22;
    /** Depth-1 base Block a BRUISER-role enemy gains on DEFEND (braces harder). Range: 24–40. */
    public static final int   DEFEND_BLOCK_GAIN_BRUISER       = 30;
    /** Depth-1 base Block a MINI_ELITE-role enemy gains on DEFEND (the sturdiest bracer). Range: 32–50. */
    public static final int   DEFEND_BLOCK_GAIN_MINI_ELITE    = 40;

    /** An enemy turtles (may DEFEND) only when its HP fraction is at or below this. Range: 0.35–0.6. */
    public static final float DEFEND_HP_THRESHOLD_FRACTION    = 0.5f;
    /**
     * BRUISER guardians brace on this fixed turn cadence (every Nth of their turns) to create the
     * read-and-adapt rhythm StS elites have, independent of their HP. Range: 2–4.
     */
    public static final int   DEFEND_BRUISER_CADENCE_TURNS    = 3;
    /**
     * Hard ceiling on how many turns in a row an enemy may commit DEFEND before it is forced to do
     * something else (attack / reposition). Without this a low-HP, cornered enemy that can neither hit
     * nor safely move would brace EVERY turn and just stand there shielding — reading to the player as
     * a broken enemy "doing nothing". After this many consecutive braces the DEFEND branch is skipped
     * for at least one turn so the enemy re-engages. Range: 1–3.
     */
    public static final int   DEFEND_MAX_CONSECUTIVE_TURNS    = 2;

    // -------------------------------------------------------------------------
    // PLAYER GUARD — directional defense (strategy-combat-order-4).
    // Tapping GUARD ends the turn and braces the marine: incoming damage from the
    // FRONT facing arc is heavily reduced, while SIDE and BACK hits land at FULL
    // damage. This turns "which way am I facing" into a defensive decision — read the
    // enemy intent icons, rotate to face the biggest hit, then guard.
    //
    // Guard is transient, CONDITIONAL eHP: it only helps vs the faced arc, only for the
    // single enemy turn it buys, and it costs a whole turn of offense (a ~1 reference-DPT
    // opportunity cost). Because it does NOTHING vs flanks, its average value is
    // self-limiting in multi-enemy rooms, so it needs no hard cap the way Block does.
    // Priced in docs/game-balance-authority.txt (GUARD note).
    // -------------------------------------------------------------------------

    /** Incoming-damage multiplier for a hit inside the front (facing) arc while guarding. Range: 0.25–0.5. */
    public static final float GUARD_FRONT_MULTIPLIER      = 0.35f;
    /** Multiplier for a hit from the side arcs while guarding — FULL damage by design. Range: 1.0 (do not weaken). */
    public static final float GUARD_SIDE_MULTIPLIER       = 1.0f;
    /** Multiplier for a hit from directly behind while guarding — FULL damage by design (>1.0 = optional backstab). Range: 1.0–1.5. */
    public static final float GUARD_BACK_MULTIPLIER       = 1.0f;
    /** Half-angle of the protected front arc, in degrees. ±60° cleanly catches only the faced cardinal. Range: 45–75. */
    public static final float GUARD_FRONT_HALF_ANGLE_DEGREES = 60f;
    /** Half-angle of the rear arc (measured from the reverse-facing vector), in degrees. Range: 45–75. */
    public static final float GUARD_BACK_HALF_ANGLE_DEGREES  = 60f;

    // Per-kill XP rewards are now DERIVED, not hand-set (new-game-balancr order 4). The thirteen
    // per-archetype XP constants that lived here were DELETED: every enemy's XP is computed from its
    // Threat Points via GameMath.xpRewardAtDepth (xpReward = XP_PER_THREAT_POINT * enemyThreatAtDepth),
    // so dangerous enemies automatically pay more and a new archetype can never ship with a forgotten
    // XP value. The single knob is XP_PER_THREAT_POINT (SECTION 7). Boss XP is DERIVED too (order 6):
    // BossBalance prices it from the boss's depth-scaled Threat Points — no flat placeholder anywhere.

    // Per-kill credit rewards (the currency payout for each archetype). Credits stay hand-set (order 4
    // only re-derives XP); the credit economy is order 3's concern.
    public static final int CREDIT_REWARD_GORE_BITER   = 5;
    public static final int CREDIT_REWARD_EYE_TYRANT   = 6;
    public static final int CREDIT_REWARD_ACID_DRONE   = 8;
    public static final int CREDIT_REWARD_VOID_SHROUD  = 12;
    public static final int CREDIT_REWARD_MIRE_WRAITH  = 15;
    public static final int CREDIT_REWARD_SHELL_BRUTE  = 12;
    public static final int CREDIT_REWARD_PLAGUE_HULK  = 8;
    public static final int CREDIT_REWARD_IRON_STALKER = 40;
    public static final int CREDIT_REWARD_GHOUL            = 5;
    public static final int CREDIT_REWARD_CRAWLER          = 5;
    public static final int CREDIT_REWARD_REVENANT         = 11;
    public static final int CREDIT_REWARD_VORTEX_EYE       = 5;
    public static final int CREDIT_REWARD_BLIGHT_CORRUPTOR = 13;

    // -------------------------------------------------------------------------------------
    // SPECIAL-VERB TP EQUIVALENCE (new-game-balancr order 5) — cycle-averaged threat pricing.
    // An archetype's Threat Points are now blended over its special-ability cadence cycle
    // (GameMath.cycleAveragedDamagePerTurn): a caster/buffer/summoner's EFFECTIVE danger, not
    // just its stat block. Each verb converts to an equivalent-damage number through a pure
    // GameMath.specialEquivalent* formula; these two knobs are the only tunable inputs those
    // formulas need beyond the already-existing status magnitudes (SECTION 8) and the special
    // constants in EnemyConstants. See docs/game-balance-knowledge.txt (cycle-averaged TP).
    // -------------------------------------------------------------------------------------
    /**
     * How much of the PLAYER's lost output a defensive debuff (WEAK / SLOW / BLIND) is worth as enemy
     * OFFENCE when pricing it into Threat Points. Below 1.0 because a turn of your lost output is worth
     * LESS than a turn of the enemy's dealt damage — you can rotate, retreat or heal around a debuff, so
     * it never converts one-for-one into threat. Start 0.6 (band-checked against every debuffing caster).
     * Used by GameMath.specialEquivalentDebuffWeak / specialEquivalentDebuffControl. Range: 0.4–0.8.
     */
    public static final float DEBUFF_TP_EQUIVALENCE       = 0.6f;
    /**
     * Bodies a SUMMON verb is expected to add over one fight, for TP pricing (GameMath.specialEquivalent-
     * Summon). Priced CONSERVATIVELY below the max batch (SUMMON_COUNT_MIN..MAX in EnemyConstants): a
     * summoner casts once per lifetime, and blocked tiles + the per-room live cap (SUMMON_ROOM_LIVE_CAP)
     * eat part of the batch — "bounded by the existing hard caps." Each expected body adds its own priced
     * TP to the summoner's total. Range: 1.0–2.5.
     */
    public static final float SUMMON_EXPECTED_PER_FIGHT   = 1.5f;

    // =====================================================================================
    // SECTION 3 — DEPTH SCALING (how threat and reward grow per floor)
    // Compound HP/damage growth and linear credit growth applied as you descend.
    // =====================================================================================

    // DEPTH-COUPLING TUNED (see SECTION 9 invariant + GameMath.depthCouplingRatio).
    // These were 1.08 / 1.06. Enemy threat scales by the PRODUCT of both as a COMPOUND curve
    // (depthThreatScale), while the player's level-up power grows LINEARLY (1 + levels*budget/100,
    // GameMath.playerPowerAtDepth). At 1.08/1.06 the compound enemy curve outran the linear player
    // curve from depth 5 on (coupling ratio fell to 0.86 at d5 and ~0.40 by d15 — the game became
    // unwinnable at depth).
    //
    // ENDLESS-MODE PASS (game-balance-tuning): the descent is ENDLESS (region bands of 5 depths,
    // bosses every 5, "THE BREACH" cycling forever — see RouteMapConstants), so runs regularly pass
    // depth 15-20. At 1.045/1.035 the coupling ratio fell out the bottom of the [0.9, 1.2] band at
    // depth 15 (0.89) and collapsed to 0.74 by depth 20 — deep endless floors were unfair-hard.
    // A compound enemy curve must eventually outrun a linear player curve, so this cannot be held
    // forever; the goal is to hold the fair band as DEEP as possible while barely touching the
    // well-tuned early/mid game. Trimmed 1.045/1.035 -> 1.042/1.032: the ratio held the band through
    // depth 15 (0.97) and landed depth 18/20 at 0.88/0.83 — a soft, graceful hard-edge.
    //
    // HONEST-POWER RE-FIT (new-game-balancr order 4): the OLD coupling defended a FICTION — the player
    // curve counted ONLY level-up cards (playerPowerAtDepth), ignoring found weapons, priced abilities
    // and crits. Order 4 replaces it with the HONEST total-power model GameMath.playerPowerAtDepthV2
    // (cardPower * gearRamp * abilityPower). That honest curve is far STEEPER (v2(15) ~= 5.5 vs the old
    // fiction's 2.68), so the enemy compound rates had to RISE to stay coupled (as the order-4 design
    // predicted). The HEALTH scale is deliberately HELD at 1.042 so order-3's scarcity depth-sweep
    // (DEMAND rides the HEALTH curve) and its region multipliers are UNTOUCHED; the whole re-fit is put
    // into the DAMAGE scale, which cancels in the heal-drain fraction (order 3) and interacts only with
    // the gear gate (order 2). DAMAGE scale re-fit 1.032 -> 1.073: the depth-coupling ratio against v2
    // now holds [0.9, 1.2] for depths 1..15 (ranging ~0.95..1.19) with NO under-band fudge, degrading
    // gracefully to the EASY side (an on-curve player pulls slightly ahead) only from depth ~17 — the
    // boundary is printed in BalanceReport's DEPTH COUPLING table. The gear gate stays in band at the
    // re-fit rate (region-2 on-curve soldier golden ratio = 3.0, exactly the fair edge). Regenerate the
    // DEPTH COUPLING table after changing either. Range: 1.03–1.08 (HP) / 1.02–1.08 (dmg).
    /** Per-floor compound HP multiplier: baseHP * scale^(depth-1). Held at 1.042 (protects order-3 scarcity). Range: 1.03–1.08. */
    public static final float ENEMY_HEALTH_SCALE_PER_DEPTH = 1.042f;
    /** Per-floor compound damage multiplier: baseDmg * scale^(depth-1). Re-fit 1.032->1.073 vs the honest v2 curve (order 4). Range: 1.02–1.08. */
    public static final float ENEMY_DAMAGE_SCALE_PER_DEPTH = 1.073f;
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
    // so it no longer invalidates the other guns. See docs/game-balance-authority.txt.
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
    // Per docs/game-balance-authority.txt, nerfing the raw 90 would make it worthless rather than
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

    // Arc Cannon — chain-lightning energy weapon (CELLS ammo). Anti-swarm specialist: the
    // primary bolt hits the first enemy in the facing line, then the arc LEAPS laterally through
    // adjacent enemies in a cluster, dealing decaying damage to each additional target. No other
    // weapon chains between separated targets — this is the crowd-clear niche.
    //
    // BALANCE (credited on the SINGLE-TARGET primary line, exactly as the Grenade Launcher is
    // credited on its centre splash — the lateral chain is bonus AoE, uncredited, and its per-shot
    // ceiling (~61 across 4 targets) stays well under the heavy-band Grenade Launcher's 130):
    //   sustainedDPT = 7 * 28 / (7 + 2) = 21.78 ; ammoEff = 28 (1 cell/shot)
    //   powerScore   = 21.78 * sqrt(28/40) = 18.2  -> BURST/SPECIALIST band 18-26, low end. OK.
    // Distinct from the three existing burst weapons (Shotgun/Dbl/Plasma) by MECHANIC, not band.
    public static final int   ARC_CANNON_DAMAGE            = 28;
    public static final int   ARC_CANNON_CLIP_SIZE         = 7;
    public static final int   ARC_CANNON_RANGE_TILES       = 7;
    public static final float ARC_CANNON_DAMAGE_DROP_COEFF = 0.08f;
    public static final int   ARC_CANNON_RELOAD_TIME_TICKS = 2;
    // Lateral chain (uncredited bonus): up to CHAIN_JUMPS leaps beyond the primary target, each
    // dealing CHAIN_DAMAGE_MULTIPLIER^jump of the primary hit (28 -> 17 -> 10 -> 6).
    public static final int   ARC_CANNON_CHAIN_JUMPS             = 3;
    public static final float ARC_CANNON_CHAIN_DAMAGE_MULTIPLIER = 0.6f;

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
    // docs/game-balance-authority.txt and balance_order_3_resource_scarcity_economy.txt.
    //
    // NOTE ON MAGNITUDE: the cuts are large because the weapon-damage / enemy-eHP economy
    // is high-damage / low-eHP (a single 44-dmg shell two-shots most chaff), so a whole
    // floor's DEMAND (~288 dmg at depth 1) is only ~6-12 ammo units. Scarce ammo therefore
    // means small boxes and low drop rates. Fully reconciling clip sizes with this economy
    // is the deferred eHP/damage rescale flagged in docs/game-balance-authority.txt.

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

    // LEVER 5 — PER-REGION SUPPLY MULTIPLIER (new-game-balancr order 3): a per-region trim/boost on
    // ammo SUPPLY so a region can be tuned without touching the global box sizes. Indexed by 0-based
    // region (floor((depth-1)/GEAR_CURVE_REGION_BAND_SIZE)); depths past the last entry clamp to it.
    // WHY it is not all-1.0: SUPPLY rides the gear curve (steps ~+35%/region) while DEMAND rides enemy
    // eHP (compounds ~+23%/region), so region 1's within-region dip needs a small boost and the deeper
    // regions (gear outrunning eHP) need a small trim to hold the scarcity ratio S in [0.75, 0.95] at
    // EVERY depth (R-SCARCITY-DEPTH). Verified by the SCARCITY depth-sweep in BalanceReport. Range per
    // entry: 0.85–1.15. See docs/game-balance-authority.txt and new-game-balancr-order-3.txt.
    public static final float[] AMMO_SUPPLY_REGION_MULTIPLIER = {1.08f, 1.00f, 0.94f};

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

    // XP CURVE — GEOMETRIC (new-game-balancr order 4). The old curve was POLYNOMIAL (base * level^exp),
    // which grows at a different rate than the enemy threat a floor's roster is worth (that grows
    // GEOMETRICALLY, depthThreatScale). Those shapes cannot stay coupled, so per-floor XP pacing drifted
    // (order 4, problem 2). The curve is now GEOMETRIC (base * growth^(level-1)) with the growth tied to
    // the enemy threat compound, so — with per-enemy XP DERIVED from depth-scaled TP (XP_PER_THREAT_POINT)
    // — a floor's roster XP and the level requirement grow at the SAME rate and the yield is depth-stable
    // (R-XP-PACE holds at every depth). See GameMath.xpRequiredForLevelGeometric.
    /** Base XP needed to advance from level 1 to 2: xpRequired = base * growth^(level-1). Raised 50->150 with the geometric curve. Range: 100–200. */
    public static final int   XP_BASE_REQUIREMENT = 150;
    /**
     * Per-level GEOMETRIC growth of the XP requirement. Chosen to TRACK the enemy threat compound
     * (ENEMY_HEALTH_SCALE_PER_DEPTH * ENEMY_DAMAGE_SCALE_PER_DEPTH ~= 1.118/floor) so leveling keeps pace
     * with the descent — the player gains ~1 level/floor at EVERY depth. R-XP-PACE is the guardrail that
     * fails the build if this drifts out of coupling with the enemy rates. Range: 1.08–1.16.
     */
    public static final float XP_CURVE_GROWTH_PER_LEVEL = 1.118f;

    // XP PACING (new-game-balancr order 4) — pacing is now a RULE (R-XP-PACE), not a hope.
    /**
     * Per-kill XP per Threat Point — the single knob that replaces the thirteen deleted per-enemy XP
     * constants. xpReward = round(XP_PER_THREAT_POINT * enemyThreatAtDepth). Chosen so a floor's roster
     * XP lands ~1.1x the level requirement (mid-band of the yield below) and per-enemy magnitudes stay
     * familiar (a depth-1 soldier ~13 XP, a mini-elite ~89). Range: 0.25–0.5.
     */
    public static final float XP_PER_THREAT_POINT      = 0.35f;
    /** R-XP-PACE lower bound: a floor must award at least this many level-ups worth of XP. Range: 0.9–1.1. */
    public static final float XP_FLOOR_YIELD_MIN       = 1.0f;
    /** R-XP-PACE upper bound: a floor must not award more than this many level-ups (progression stays paced). Range: 1.2–1.5. */
    public static final float XP_FLOOR_YIELD_MAX       = 1.3f;
    /**
     * CATCH-UP rubber band (forward only): while the player is more than one level BELOW the expected
     * level for their depth, incoming XP is multiplied by this. Never slows an at-or-ahead player — being
     * ahead is earned, being behind is recoverable. See GameMath.catchUpScaledXp. Range: 1.25–2.0.
     */
    public static final float XP_CATCHUP_MULTIPLIER    = 1.5f;

    // ---------------------------------------------------------------------------------
    // LEVEL-UP CARD SYSTEM — power budget & re-priced boons (idea 5: build diversity)
    //
    // Every level-up offers LEVEL_UP_CARDS_OFFERED cards drawn from four pools. Each card
    // costs the SAME power budget (LEVEL_UP_BUDGET_PP, in "power points" = %-gain to the
    // reference DPT or eHP from SECTION 9), so no card is a strict upgrade over another —
    // they differ in KIND, not amount. Because each pick is budget-equal, the player's
    // TOTAL power at level L is L * LEVEL_UP_BUDGET_PP regardless of which cards were taken;
    // only its SHAPE differs. That is how build diversity stays inside the depth-coupling
    // band (SECTION 9) for every build. See docs/game-balance-authority.txt (section [D]).
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

    // ---------------------------------------------------------------------------------
    // THE CANONICAL STARTING ATTRIBUTE BLOCK — ONE DIFFICULTY (new-game-balancr order 8)
    //
    // DESIGN INVARIANT (owner decision, final): the game has EXACTLY ONE difficulty.
    // There are no easy/normal/hard modes and there must never be. Difficulty variance
    // comes from INSIDE the run — route choice (SECTION 19), region danger (SECTION 3),
    // depth (the coupling curve), and loot luck — never from a menu.
    //
    // Order 8 deleted the four per-mode starting-attribute tables that used to live in
    // GameBalance. These four values ARE the surviving block (the old "normal" tier), and
    // they are what every band, anchor and living table in orders 1-7 was derived against:
    // with PLAYER_MAX_HEALTH 130 + PLAYER_MAX_ARMOR 75, no dodge and no flat reduction,
    // they produce REFERENCE_PLAYER_EHP = 205 (SECTION 9). The whole contract is therefore
    // EXACT for the one real game rather than exact for one mode out of four.
    //
    // ENFORCEMENT: BalanceSchema's R-SINGLE-DIFFICULTY rule requires exactly one starting
    // block (these four PLAYER_START_* fields, one per Attribute) and fails the build on
    // any constant, enum or nested type reintroducing mode-family naming.
    // ---------------------------------------------------------------------------------

    /** Canonical starting STRENGTH. Feeds the melee multiplier (STAT_REFERENCE 0 → 1.10x at 2). Range: 0–4. */
    public static final int PLAYER_START_STRENGTH     = 2;
    /** Canonical starting AGILITY. Feeds dodge chance and action duration. Range: 0–4. */
    public static final int PLAYER_START_AGILITY      = 2;
    /** Canonical starting TOUGHNESS. Feeds max-HP bonus and flat damage reduction; part of the 205 eHP anchor. Range: 0–4. */
    public static final int PLAYER_START_TOUGHNESS    = 2;
    /** Canonical starting MARKSMANSHIP. Feeds ranged damage and accuracy. Range: 0–4. */
    public static final int PLAYER_START_MARKSMANSHIP = 2;

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
    // Per-turn tick damage and durations for every status effect, shared and weapon-applied.
    // CONSOLIDATED (Balance Authority, order 1): the shared status magnitudes below moved in
    // from EffectConstants (which keeps re-export shims and now holds only cosmetic values).
    // =====================================================================================

    // --- THE UNIFIED DOT BASES. Exactly ONE definition per status — BalanceSchema's R-DOT
    // rule fails the build if a second base definition appears. Before order 1 two divergent
    // BURN definitions existed (EffectConstants said 4/turn — the value the game actually
    // applied via StatusEffectController — while the Incendiary ability base said 3/turn).
    // The EffectConstants value won; the Incendiary base now REFERENCES the unified base.
    /** BURNING damage per turn — THE single burn base (hazard fire, enemy burns, Incendiary level 1). */
    public static final int   BURN_DAMAGE_PER_TURN    = 4;
    /** Minimum BURNING duration (turns) a burn application may roll. */
    public static final int   BURN_DURATION_MIN       = 3;
    /** Maximum BURNING duration (turns) a burn application may roll. */
    public static final int   BURN_DURATION_MAX       = 5;
    /** POISONED damage per stack per turn — THE single poison base (toxic pools, enemy acid). */
    public static final int   POISON_DAMAGE_PER_STACK = 2;
    /** Hard cap on concurrent POISONED stacks on one host. */
    public static final int   POISON_MAX_STACKS       = 5;
    /** POISONED duration (turns) per application (re-application refreshes and stacks). */
    public static final int   POISON_DURATION         = 4;

    // --- Shared status-effect magnitudes (moved from EffectConstants; gameplay, not cosmetic).
    /** STUNNED duration (turns) for the standard stun application. */
    public static final int   STUN_DURATION_DEFAULT   = 1;
    /** STUNNED duration (turns) for heavy stun sources. */
    public static final int   STUN_DURATION_HEAVY     = 2;
    /** BLINDED duration (turns). */
    public static final int   BLIND_DURATION          = 2;
    /** SLOWED action-duration multiplier applied to the debuffed host. */
    public static final float SLOW_FACTOR             = 2.0f;
    /** SLOWED duration (turns). */
    public static final int   SLOW_DURATION           = 3;
    /** EMPOWERED outgoing-damage bonus percent. */
    public static final int   EMPOWERED_DAMAGE_PERCENT = 50;
    /** EMPOWERED duration (turns). */
    public static final int   EMPOWERED_DURATION       = 5;
    /** VULNERABLE incoming-damage bonus percent per stack. */
    public static final int   VULNERABLE_DAMAGE_PERCENT = 50;
    /** VULNERABLE duration (turns); re-application refreshes and adds a stack. */
    public static final int   VULNERABLE_DURATION       = 2;
    /** Hard cap on VULNERABLE stacks so a build can't multiply a boss into a trivial kill. */
    public static final int   VULNERABLE_MAX_STACKS     = 2;
    /** WEAK outgoing-damage reduction percent on the debuffed host. */
    public static final int   WEAK_DAMAGE_PERCENT       = 25;
    /** WEAK duration (turns). */
    public static final int   WEAK_DURATION             = 2;
    /** EXPOSED duration (turns): the next hit into the host ignores its Block, then clears. */
    public static final int   EXPOSED_DURATION          = 2;
    /** Bonus damage percent for a player hit landed from BEHIND an enemy's facing. */
    public static final int   BACKSTAB_DAMAGE_PERCENT   = 30;

    // --- Enemy status-application chances (moved from EffectConstants; gameplay).
    /** Chance a Mire Wraith ranged hit applies POISONED. */
    public static final float MIRE_WRAITH_POISON_CHANCE = 0.30f;
    /** Chance an Acid Drone ranged hit applies POISONED. */
    public static final float ACID_DRONE_POISON_CHANCE  = 0.75f;

    // --- Explosive barrel hazard (moved from EffectConstants; gameplay).
    /** Damage a detonating explosive barrel deals to hosts in its blast. */
    public static final int   EXPLOSION_DAMAGE    = 12;
    /** Hard ceiling on chained barrel detonations from one trigger. */
    public static final int   EXPLOSION_CHAIN_MAX = 32;

    // Rend (BLEED DoT — on hit).
    public static final float REND_DAMAGE_PER_TURN_BASE      = 2f;
    public static final float REND_DAMAGE_PER_TURN_PER_LEVEL = 0.5f;
    public static final float REND_DAMAGE_PER_TURN_CAP       = 6f;
    public static final int   REND_DURATION_TURNS            = 4;

    // Incendiary (BURN DoT — on hit). The level-1 base IS the unified burn base above (was a
    // divergent literal 3f before order 1; the game's applied value 4 won the unification).
    public static final float INCENDIARY_BURN_PER_TURN_BASE      = BURN_DAMAGE_PER_TURN;
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
    // See docs/game-balance-authority.txt for the full contract and the living table
    // (regenerate the table with BalanceReport whenever any number above changes).
    // =====================================================================================

    /**
     * Reference player Damage-Per-Turn — the FIXED yardstick every enemy's survival and
     * Threat-Point value is measured against. Originally set to the start shotgun's
     * sustained DPT ((1*50)/(1+1) = 25). The shotgun was since trimmed to 44/shot
     * (sustained DPT now 22) to pull its power score into the burst band, but this anchor
     * is deliberately HELD at 25: it is a stable reference for the whole enemy TP table,
     * not a live mirror of the current shotgun. Moving it would rescale every enemy's TP
     * at once (all thirteen non-boss archetypes currently sit in-band) for no balance gain.
     * Keep it at 25 unless you intend to re-tune the entire enemy roster.
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
     * Reference player effective HP — THE start survivability of the one canonical player
     * (130 HP + 75 armour, no dodge, no flat reduction; the SECTION 7 PLAYER_START_* block).
     * Used as the denominator for the player's Turns-To-Die in golden-ratio checks.
     *
     * <p>Order 8 removed the difficulty modes, so this stopped being "the anchor of one mode
     * out of four" and became THE player anchor: the telegraph fairness caps (25% un-telegraphed,
     * 35% boss single hit) now provably protect the actual player pool in every run.
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

    // --- THE EXPECTED ARSENAL CURVE (new-game-balancr order 2) — the GEAR GATE anchor.
    // REFERENCE_PLAYER_DPT (above) is the FIXED depth-1 yardstick. Order 2 layers a per-depth
    // EXPECTED PLAYER on top of it: expectedPlayerDamagePerTurn(depth) = REFERENCE_PLAYER_DPT *
    // gearCurve(depth) (a later order multiplies in a card curve too). gearCurve models the arsenal
    // the game EXPECTS you to be holding at a depth — found/bought weapons + weapon levels — as a
    // step-per-region multiplier:
    //     gearCurve(d) = GEAR_CURVE_PER_REGION ^ floor((d-1) / GEAR_CURVE_REGION_BAND_SIZE)
    // Because a region is GEAR_CURVE_REGION_BAND_SIZE (5) depths, gearCurve is 1.0 across the whole
    // first region (depths 1..5), so expectedPlayerDamagePerTurn(1..5) == REFERENCE_PLAYER_DPT exactly
    // (the run BEGINS on the curve, at the bottom of it). Each subsequent region expects ~+35% weapon
    // power. THE GATE: a player who never upgrades keeps the depth-1 REFERENCE_PLAYER_DPT while the
    // curve rises, so by region 2 they fight at ~1/1.35 = 74% of the expected DPT and by region 3 at
    // ~55% — their golden ratio falls out of the fair band (BalanceSchema R-GEARGATE proves this with
    // the region-2 entry soldier), which is what makes "find better gear or die" a real loop.
    // See docs/game-balance-authority.txt (THE GEAR CURVE) and GameMath.gearCurveAtDepth.
    /** Weapon-power multiplier the game expects the player to GAIN per 5-floor region (each region ~+35%). Range: 1.2–1.5. */
    public static final float GEAR_CURVE_PER_REGION       = 1.35f;
    /**
     * Depths per region for the gear curve — the SAME 5-floor band the route map uses
     * ({@link RouteMapConstants#REGION_BAND_SIZE}), referenced here so the curve and the descent's
     * region boundaries can never drift apart. A region ends on a boss floor, and the gear step lands
     * exactly on the region boundary.
     */
    public static final int   GEAR_CURVE_REGION_BAND_SIZE = RouteMapConstants.REGION_BAND_SIZE;

    // =====================================================================================
    // SECTION 10 — RESOURCE SCARCITY MODEL & BANDS (idea 3)
    // The bands the scarcity contract checks against, plus the canonical MODEL FLOOR — a
    // fixed depth-1 reference encounter whose SUPPLY/DEMAND, scarcity ratio S, and net HP
    // drain are computed by GameMath and printed by BalanceReport. The SECTION 5 levers are
    // tuned against this model floor. See docs/game-balance-authority.txt and
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

    // --- PER-REGION HEAL SUPPLY MULTIPLIER (new-game-balancr order 3, part B): the heal-economy
    // twin of AMMO_SUPPLY_REGION_MULTIPLIER — a per-region nudge on heal/armour SUPPLY, indexed the
    // same way (clamped past the last entry). Defaults to 1.0: incoming damage, heal supply and the
    // player's current-difficulty eHP all ride the enemy-damage curve, so the net-drain FRACTION is
    // depth-stable (GameMath.netHpDrainFractionAtDepth) and R-HEALDRAIN-DEPTH holds at every depth
    // 1..15 with no per-region tuning. The lever exists to shift a region's drain without touching
    // the heal magnitudes in SECTION 1. Range per entry: 0.85–1.15.
    public static final float[] HEAL_SUPPLY_REGION_MULTIPLIER = {1.00f, 1.00f, 1.00f};

    // --- NEVER-SOFTLOCK (order 3, part D): the emergency ammo lifeline. When the player's TOTAL
    // remaining potential damage (all reserves * efficiency + melee) falls below the remaining floor
    // demand times this fraction, the next eligible enemy drop is forced to be ammo for an equipped
    // weapon (capped once per floor — GameMath.emergencySupplyTriggers). Sits FAR below the scarcity
    // band, so a hoarder never trips it; it only converts "standing empty-handed" deaths into
    // fighting-retreat deaths. Melee (fist, no ammo) is the true backstop. Range: 0.15–0.35.
    public static final float EMERGENCY_SUPPLY_FRACTION = 0.25f;

    // --- CREDIT ECONOMY BAND (order 3, part C): the credit sink is only a real resource if income is
    // COUPLED to it. R-CREDITS checks expected income per region / price of the expected purchase
    // bundle (one weapon-class buy + a couple of supplies) lands in [MIN, MAX] — a region affords
    // roughly one significant purchase plus resupply, banking about one region's worth.
    public static final float CREDIT_INCOME_RATIO_MIN = 0.9f;
    public static final float CREDIT_INCOME_RATIO_MAX = 1.4f;
    /** Target credit banking horizon (floors) — a full purse should bank about one region. Range: 4–7. */
    public static final float CREDIT_BANK_TARGET_FLOORS = 5f;
    /** PP value of the ONE "significant" (weapon-class) purchase a region's income is expected to afford. */
    public static final float SHOP_SIGNIFICANT_BUY_POWER_POINTS = 12f;
    /** PP value of a representative small (supply) purchase — ammo box / stim, used in the credit bundle. */
    public static final float SHOP_SMALL_BUY_POWER_POINTS        = 3f;
    /** Significant (weapon-class) purchases a region's income should afford. */
    public static final int   SHOP_EXPECTED_SIGNIFICANT_BUYS_PER_REGION = 1;
    /** Small (supply) purchases a region's income should afford on top of the significant buy (1–2). */
    public static final float SHOP_EXPECTED_SMALL_BUYS_PER_REGION       = 1.5f;

    // --- HEAL PRICING BANDS (Balance Authority R-HEAL): every heal/armour pickup is priced in
    // SURVIVAL TURNS BOUGHT = pickupValue / averageIncomingDamagePerTurn on the model floor
    // (GameMath.survivalTurnsBought). Small pickups ('+' stim, 'a' shard) buy a few turns; large
    // pickups ('H' medkit, 'A' vest) buy most of a fight but never a full reset. A pickup outside
    // its band is either worthless filler (under) or removes the resource decision (over).
    public static final float HEAL_SMALL_SURVIVAL_TURNS_MIN = 2f;
    public static final float HEAL_SMALL_SURVIVAL_TURNS_MAX = 6f;
    public static final float HEAL_LARGE_SURVIVAL_TURNS_MIN = 8f;
    public static final float HEAL_LARGE_SURVIVAL_TURNS_MAX = 16f;

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

    // --- ENCOUNTER BUDGET V2 (new-game-balancr order 5) — three additions on top of the composition
    // rules above. See docs/game-balance-knowledge.txt (ENCOUNTER BUDGET V2) and new-game-balancr-order-5.

    // 1. TACTICAL ROOM CAPS — a room's spent TP is capped against its GEOMETRY, computed from the
    //    generator's room metadata (open vs chokepoint), never a per-room hand tag. An OPEN room has
    //    nowhere to break a ranged enemy's cardinal line, so it is capped LOWER; a corridor-adjacent
    //    chokepoint is more defensible, so it may hold a little MORE.
    /** Per-room TP cap multiplier for an OPEN room (no cover, wide sightlines — ranged lines can't be broken). Range: 0.7–0.9. */
    public static final float ROOM_OPEN_TP_MULTIPLIER       = 0.8f;
    /** Per-room TP cap multiplier for a corridor-adjacent CHOKEPOINT room (defensible — the player can funnel). Range: 1.0–1.2. */
    public static final float ROOM_CHOKEPOINT_TP_MULTIPLIER = 1.1f;

    // 2. PACK COHERENCE — chaff spawns in packs, because the golden-band CHAFF exemption ASSUMES packs
    //    (a lone chaff reads a harmless ~9 golden ratio; it is balanced by pack TP). The spawner now
    //    guarantees the assumption instead of hoping for it.
    /** Minimum chaff pack size the encounter spawner guarantees (the golden-band exemption assumes packs). Range: 2–3. */
    public static final int   CHAFF_PACK_MIN               = 2;
    /** Maximum chaff pack size a single pack may reach before a new pack is started. Range: 3–5. */
    public static final int   CHAFF_PACK_MAX               = 4;

    // 3. THE REGION DANGER DIAL (fixes knowledge-doc problem 12) — route regions stop being frequency-only.
    //    Each region declares a Threat-Point budget multiplier applied ON TOP of the depth curve
    //    (GameMath.regionScaledFloorThreatPointBudget), indexed 0-based by region
    //    (floor((depth-1)/GEAR_CURVE_REGION_BAND_SIZE)); depths past the last entry clamp to it (endless
    //    "The Breach" keeps its multiplier forever). Node WEIGHT multipliers (RouteMapConstants
    //    REGION_*_MULTIPLIER_*) stay for FLAVOUR — what kind of rooms; this dial owns HOW HARD. A lethal
    //    region is now EXPLICITLY lethal and budgeted, and the depth-coupling audit reads it per lane so it
    //    stays provably fair (region danger is extra bodies, not an unfair per-duel spike). Aligned to the
    //    four route regions: A OUTER FACILITY 0.9 | B RESEARCH WING 1.0 | C REACTOR DEPTHS 1.15 | D THE
    //    BREACH 1.25 (RouteMapConstants.REGION_A..D_NAME). Range per entry: 0.8–1.3, monotonic non-decreasing.
    public static final float[] REGION_TP_BUDGET_MULTIPLIER = {0.90f, 1.00f, 1.15f, 1.25f};
    /** R-REGION lower bound: no region's TP dial may fall below this (a region never trivialises a floor). */
    public static final float   REGION_TP_BUDGET_MULTIPLIER_MIN = 0.8f;
    /** R-REGION upper bound: no region's TP dial may exceed this (region danger stays an attrition tax, not a wall). */
    public static final float   REGION_TP_BUDGET_MULTIPLIER_MAX = 1.3f;
    /** R-REGION: minimum margin by which the two LETHAL regions (C/D) must out-dial region A (measurably harder). */
    public static final float   REGION_TP_LETHAL_MARGIN         = 0.1f;

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
     * Multiplier applied to EffectConstants.BURN_DAMAGE_PER_TURN when the host standing in a fire
     * tile is an enemy rather than the player. Fire still hurts the player at the base rate (the
     * misposition risk stays real), but enemies caught in the flamethrower's spreading fire burn
     * faster — rewarding the player for herding a swarm into the flame instead of just tagging
     * them directly. Range: 1.0 (symmetric) – 2.0.
     */
    public static final float HAZARD_FIRE_ENEMY_DAMAGE_MULTIPLIER = 1.5f;

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
    // instead — see GameMath's BOSS BALANCE RULESET block and docs/game-balance-authority.txt
    // (Boss appendix). Order 6 makes this LIVE: BossBalance re-derives every boss's HP, per-verb damage,
    // and reward at spawn from the targets/bands below through the GameMath formulas — there are NO flat
    // boss HP/damage/reward constants anymore. Changing a boss's difficulty = tuning its TARGET_FIGHT_TURNS
    // or the SURVIVAL_RATIO here (semantic dials), never editing a literal HP number.
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
    /**
     * Fraction of the fight's ammo consumption the BOSS ARENA guarantees via placed pickups (RULE 5 / the
     * AMMO CHECK): a build check must test your BUILD, not whether you happened to enter with full pockets,
     * so the arena tops the reserve cap up to at least this share of the modelled ammo demand. The boss-floor
     * generator places the pickups; {@code BossBalance.arenaAmmoBudgetDamage} is the modelled quantity the
     * audit charges against. Range: 0.3–0.7.
     */
    public static final float BOSS_ARENA_AMMO_BUDGET_FRACTION      = 0.50f;

    // --- RULE 3 (soft enrage, not a timer): once a fight drags past the upper cap (1.5 * target) the boss
    // gains a per-turn DPT ramp so turtling past the survival-check math stops working. Telegraphed, never a
    // fail-state — R2's ceiling is enforced by ESCALATION, not a clock.
    /** Extra boss damage per turn spent beyond the upper fight-turns cap, as a fraction of the base hit. Range: 0.05–0.12. */
    public static final float BOSS_ENRAGE_DPT_RAMP_PER_TURN        = 0.08f;

    // --- RULE 5 (the build check, as an assertion): with the DEPTH-1 STARTING loadout a perfect-play hoarder
    // must DIE at less than 1/MARGIN of the damage needed, WITH the maximum heal supply the fight can offer.
    /** turnsToKill(start) must be >= this * survivableTurns(bossDPT, eHP + max heals) for every boss (R-BOSS-GATE). Range: 1.8–2.5. */
    public static final float BOSS_GATE_MIN_DAMAGE_MARGIN          = 2.0f;
    /**
     * The maximum heal supply the GATE credits to the fight, as a fraction of reference eHP (arena drops +
     * a bounded carry). Kept modest because the FIRST boss (Overseer, only ~+30% expected DPT over the start
     * player) is the tightest gate — like the Railgun scarcity waiver, the shallowest case is the real limiter.
     * Raising it narrows the gate margin at the Overseer first. Range: 0.15–0.35.
     */
    public static final float BOSS_GATE_MODELED_HEAL_SUPPLY_EHP_FRACTION = 0.25f;

    // =====================================================================================
    // BOSS STATS — DERIVED, NEVER SET (order 6). The flat HP / verb-damage / reward PLACEHOLDERS that
    // used to live here are DELETED. Boss HP and DPT are now COMPUTED at spawn by BossBalance from the
    // inputs above (target fight turns + survival ratio) through the GameMath boss ruleset; each verb's
    // damage is a FRACTION of the derived boss DPT (below), so tuning the survival ratio retunes every
    // verb coherently. Changing a boss's difficulty = tuning its TARGET_FIGHT_TURNS or the SURVIVAL_RATIO
    // — semantic dials, not raw HP. Cosmetic boss values (accent colours, sprites) stay in EnemyConstants.
    // Non-damage gameplay constants (ranges, cooldowns, counts, radii, heal cadence) stay flat below —
    // they shape the CHOREOGRAPHY, which this order leaves untouched; only HP/damage/reward are derived.
    // =====================================================================================

    /** Boss phases: one HP bar split into this many equal escalation phases (RULE 4). 2 = phase-2 at 50%. */
    public static final int   BOSS_PHASE_COUNT                     = 2;

    // Per-boss TARGET FIGHT TURNS — the RULE-1 dial. Each sits inside the act band [18, 40] and escalates
    // by act (a deeper boss is a longer fight). bossHP = expectedPlayerSustainedDpt(depth) * target.
    /** The Overseer's target fight length (turns), inside the act band. Range: 18–40. */
    public static final float OVERSEER_TARGET_FIGHT_TURNS          = 22f;
    /** The Corruptor's target fight length (turns), inside the act band. Range: 18–40. */
    public static final float CORRUPTOR_TARGET_FIGHT_TURNS         = 28f;
    /** Hell Baron's target fight length (turns), inside the act band. Range: 18–40. */
    public static final float HELL_BARON_TARGET_FIGHT_TURNS        = 34f;

    // VERB DAMAGE TABLES — each boss verb's single-hit damage as a FRACTION of that boss's derived DPT
    // (order 6, TECHNICAL NOTES). A telegraphed signature hit is a multiple of the sustained per-turn
    // average; an un-telegraphed poke is a small fraction (it must stay under 25% eHP, RULE 3). All are
    // audited against the 35%/25% single-hit caps by R-BOSS-VERB-CAP at the derived values.

    // The Overseer (depth 5) — security core robot; laser lanes + melee charge.
    public static final int   OVERSEER_DEPTH                = 5;
    /** MELEE (UN-telegraphed poke) as a fraction of boss DPT — must resolve under 25% eHP. Range: 0.5–1.0. */
    public static final float OVERSEER_MELEE_DPT_FRACTION   = 0.75f;
    /** CHARGE (telegraphed line strike) as a fraction of boss DPT — the signature hit. Range: 1.2–2.2. */
    public static final float OVERSEER_CHARGE_DPT_FRACTION  = 1.80f;
    /** LASER (telegraphed lane) as a fraction of boss DPT — the ranged tell (EnemyType headline). Range: 0.8–1.4. */
    public static final float OVERSEER_LASER_DPT_FRACTION   = 1.10f;
    /** One-time REPAIR total, as a fraction of the boss's DERIVED max HP, spread over OVERSEER_HEAL_TURNS. Range: 0.18–0.30. */
    public static final float OVERSEER_HEAL_TOTAL_HP_FRACTION = 0.24f;
    public static final int   OVERSEER_RAM_COOLDOWN         = 3;
    public static final int   OVERSEER_CHARGE_RANGE_TILES   = 5;
    public static final int   OVERSEER_CHARGE_RECOVERY_TURNS = 1;
    public static final int   OVERSEER_ADDS_CAP             = 4;
    public static final int   OVERSEER_SUMMON_COUNT         = 2;
    public static final int   OVERSEER_FIRE_LANE_LENGTH     = 3;
    public static final int   OVERSEER_TOXIC_RADIUS         = 1;
    public static final float OVERSEER_HEAL_HP_THRESHOLD    = 0.35f;
    public static final int   OVERSEER_HEAL_TURNS           = 5;

    // The Corruptor (depth 10) — mutated scientist; summoner + acid burst.
    public static final int   CORRUPTOR_DEPTH               = 10;
    /** ACID (telegraphed burst) as a fraction of boss DPT. Range: 1.0–1.8. */
    public static final float CORRUPTOR_ACID_DPT_FRACTION   = 1.40f;
    public static final int   CORRUPTOR_SUMMON_COOLDOWN     = 3;
    public static final int   CORRUPTOR_MINION_CAP          = 5;
    public static final int   CORRUPTOR_ACID_POOL_DURATION  = 3;

    // Hell Baron (depth 15) — armored greater demon; firewall + enrage.
    public static final int   HELL_BARON_DEPTH              = 15;
    /** FIRE (telegraphed hazard tick) as a fraction of boss DPT — the DOT seed. Range: 0.3–0.7. */
    public static final float HELL_BARON_FIRE_DPT_FRACTION      = 0.50f;
    /** CLEAVE phase-1 (telegraphed) as a fraction of boss DPT. Range: 1.3–2.0. */
    public static final float HELL_BARON_CLEAVE_P1_DPT_FRACTION = 1.70f;
    /** CLEAVE phase-2 (telegraphed, the enraged signature) as a fraction of boss DPT. Range: 2.2–3.4. */
    public static final float HELL_BARON_CLEAVE_P2_DPT_FRACTION = 2.90f;
    public static final int   HELL_BARON_FIREWALL_COOLDOWN_P1 = 4;
    public static final int   HELL_BARON_FIREWALL_COOLDOWN_P2 = 2;
    public static final int   HELL_BARON_FIREWALL_DURATION  = 4;

    // Overseer Hunter-Killer brain tactic weights (moved from GameBalance; boss AI aggression
    // is a difficulty dial). BOSS_TACTIC_DEBUG_LOG stays in GameBalance (dev instrumentation).
    public static final int   BOSS_TACTIC_SCORE_BASE        = 10;
    public static final int   BOSS_TACTIC_SCORE_CLOSE_RANGE = 6;
    public static final int   BOSS_TACTIC_SCORE_FAR_RANGE   = 6;
    public static final int   BOSS_TACTIC_SCORE_ADDS_ROOM   = 4;
    public static final int   BOSS_TACTIC_SCORE_CORNERED    = 5;
    public static final int   BOSS_TACTIC_SCORE_CAMPING     = 6;
    public static final int   BOSS_TACTIC_CLOSE_RANGE_TILES = 3;
    public static final int   BOSS_TACTIC_CAMP_TURNS        = 3;
    public static final int   BOSS_TACTIC_CORNERED_EXITS    = 1;
    public static final int   BOSS_TACTIC_RANDOM_TIEBREAK   = 3;
    public static final int   BOSS_TACTIC_CHARGE_GAP_PHASE1 = 3;
    public static final int   BOSS_TACTIC_SUMMON_GAP_PHASE1 = 4;
    public static final int   BOSS_TACTIC_HAZARD_GAP_PHASE1 = 2;
    public static final int   BOSS_TACTIC_CHARGE_GAP_PHASE2 = 1;
    public static final int   BOSS_TACTIC_SUMMON_GAP_PHASE2 = 2;
    public static final int   BOSS_TACTIC_HAZARD_GAP_PHASE2 = 1;
    public static final float BOSS_TACTIC_LAST_STAND_HP_FRACTION = 0.15f;

    // =====================================================================================
    // SECTION 15 — WEAPON ABILITY MAGNITUDES (the ability catalogue)
    // CONSOLIDATED from GameBalance (Balance Authority, order 1). BASE / PER_LEVEL / CAP for
    // every rollable weapon ability. All values remain PLAYTESTING PLACEHOLDERS — order 1
    // moves them into the single source of truth; pricing them against the contract is a
    // later order in this series. GameBalance keeps re-export shims.
    // =====================================================================================

    // ── Critical Strike ──
    public static final float CRIT_CHANCE_BASE            = 0.05f;
    public static final float CRIT_CHANCE_PER_LEVEL       = 0.015f;
    public static final float CRIT_CHANCE_CAP             = 0.30f;

    // ── Armor Pierce (bypasses a fraction of the target's Block) ──
    public static final float ARMOR_PIERCE_BASE           = 0.20f;
    public static final float ARMOR_PIERCE_PER_LEVEL      = 0.05f;
    public static final float ARMOR_PIERCE_CAP            = 0.60f;

    // ── Executioner (bonus vs low-HP targets) ──
    public static final float EXECUTIONER_THRESHOLD       = 0.25f;
    public static final float EXECUTIONER_BONUS_BASE      = 0.30f;
    public static final float EXECUTIONER_BONUS_PER_LEVEL = 0.06f;
    public static final float EXECUTIONER_BONUS_CAP       = 0.80f;

    // ── Stagger Rounds (stun on hit) ──
    public static final float STAGGER_CHANCE_BASE         = 0.08f;
    public static final float STAGGER_CHANCE_PER_LEVEL    = 0.02f;
    public static final float STAGGER_CHANCE_CAP          = 0.35f;

    // ── Overpenetration ──
    public static final int   OVERPENETRATION_BASE_COUNT              = 1;
    public static final int   OVERPENETRATION_LEVELS_PER_STEP         = 3;
    public static final int   OVERPENETRATION_MAX_COUNT               = 3;
    public static final float OVERPENETRATION_ALREADY_PIERCING_BONUS  = 0.25f;

    // ── Lifesteal ──
    public static final float LIFESTEAL_BASE              = 0.06f;
    public static final float LIFESTEAL_PER_LEVEL         = 0.015f;
    public static final float LIFESTEAL_CAP               = 0.20f;

    // ── Hemorrhage Harvest (on-kill HP) ──
    public static final float HEMORRHAGE_HP_BASE          = 3f;
    public static final float HEMORRHAGE_HP_PER_LEVEL     = 0.7f;
    public static final int   HEMORRHAGE_HP_CAP           = 12;

    // ── Vampiric Crit ──
    public static final float VAMPIRIC_CRIT_HP_BASE       = 4f;
    public static final float VAMPIRIC_CRIT_HP_PER_LEVEL  = 1.0f;
    public static final int   VAMPIRIC_CRIT_HP_CAP        = 14;

    // ── Adrenal Surge ──
    public static final float ADRENAL_SURGE_CHANCE_BASE      = 0.10f;
    public static final float ADRENAL_SURGE_CHANCE_PER_LEVEL = 0.03f;
    public static final float ADRENAL_SURGE_CHANCE_CAP       = 0.40f;
    public static final float ADRENAL_SURGE_DAMAGE_BONUS     = 0.30f;

    // ── Bulwark Rounds (temp armour on reload) ──
    public static final float BULWARK_ARMOR_BASE          = 2f;
    public static final float BULWARK_ARMOR_PER_LEVEL     = 0.5f;
    public static final int   BULWARK_ARMOR_CAP           = 8;
    public static final int   BULWARK_ARMOR_DURATION      = 3;

    // ── Second Wind (low-HP damage bonus) ──
    public static final float SECOND_WIND_HP_THRESHOLD    = 0.30f;
    public static final float SECOND_WIND_BONUS_BASE      = 0.25f;
    public static final float SECOND_WIND_BONUS_PER_LEVEL = 0.05f;
    public static final float SECOND_WIND_BONUS_CAP       = 0.75f;

    // ── Kinetic Slam (melee) ──
    public static final float KINETIC_SLAM_CHANCE_BASE       = 0.20f;
    public static final float KINETIC_SLAM_CHANCE_PER_LEVEL  = 0.04f;
    public static final float KINETIC_SLAM_CHANCE_CAP        = 0.60f;
    public static final int   KINETIC_SLAM_WALL_BONUS_DAMAGE = 3;

    // ── Cleave (melee) ──
    public static final float CLEAVE_FRACTION_BASE      = 0.40f;
    public static final float CLEAVE_FRACTION_PER_LEVEL = 0.05f;
    public static final float CLEAVE_FRACTION_CAP       = 0.80f;

    // ── Salvage Strike (melee, on-kill ammo) ──
    public static final float SALVAGE_CHANCE_BASE      = 0.50f;
    public static final float SALVAGE_CHANCE_PER_LEVEL = 0.06f;
    public static final float SALVAGE_CHANCE_CAP       = 1.00f;

    // ── Scholar's Edge (melee, on-kill XP) ──
    public static final float SCHOLARS_XP_BONUS_BASE      = 0.15f;
    public static final float SCHOLARS_XP_BONUS_PER_LEVEL = 0.05f;
    public static final float SCHOLARS_XP_BONUS_CAP       = 0.75f;

    // ── Berserker's Oath (legendary, melee) ──
    public static final float BERSERKER_DAMAGE_PER_STACK  = 0.10f;
    public static final int   BERSERKER_HP_TICK_PER_STACK = 1;
    public static final int   BERSERKER_MAX_STACKS        = 5;

    // ── Scavenger Rounds (gun, on-kill ammo refund) ──
    public static final float SCAVENGER_CHANCE_BASE          = 0.15f;
    public static final float SCAVENGER_CHANCE_PER_LEVEL     = 0.03f;
    public static final float SCAVENGER_CHANCE_CAP           = 0.50f;
    public static final int   SCAVENGER_REFUND_BASE          = 1;
    public static final int   SCAVENGER_REFUND_HIGH_LEVEL    = 2;
    public static final int   SCAVENGER_HIGH_LEVEL_THRESHOLD = 7;

    // ── Field Medic Rounds (on-kill medkit drop) ──
    public static final float FIELD_MEDIC_CHANCE_BASE        = 0.05f;
    public static final float FIELD_MEDIC_CHANCE_PER_LEVEL   = 0.02f;
    public static final float FIELD_MEDIC_CHANCE_CAP         = 0.25f;

    // ── Credit Fang (on-kill credits) ──
    public static final float CREDIT_FANG_BASE               = 2f;
    public static final float CREDIT_FANG_PER_LEVEL          = 1f;
    public static final int   CREDIT_FANG_CAP                = 12;

    // ── Point Blank ──
    public static final float POINT_BLANK_BONUS_BASE      = 0.20f;
    public static final float POINT_BLANK_BONUS_PER_LEVEL = 0.05f;
    public static final float POINT_BLANK_BONUS_CAP       = 0.70f;
    public static final int   POINT_BLANK_MAX_DISTANCE    = 1;

    // ── Marksman's Patience ──
    public static final float MARKSMAN_PER_TILE_BASE      = 0.05f;
    public static final float MARKSMAN_PER_TILE_PER_LEVEL = 0.01f;
    public static final float MARKSMAN_PER_TILE_CAP       = 0.12f;
    public static final int   MARKSMAN_MIN_DISTANCE       = 2;
    public static final float MARKSMAN_TOTAL_BONUS_CAP    = 0.60f;

    // ── Opening Salvo ──
    public static final float OPENING_SALVO_BONUS_BASE      = 0.30f;
    public static final float OPENING_SALVO_BONUS_PER_LEVEL = 0.07f;
    public static final float OPENING_SALVO_BONUS_CAP       = 0.90f;

    // ── Rhythm / Heat-Up ──
    public static final float RHYTHM_RAMP_PER_HIT_BASE      = 0.06f;
    public static final float RHYTHM_RAMP_PER_HIT_PER_LEVEL = 0.01f;
    public static final float RHYTHM_RAMP_PER_HIT_CAP       = 0.15f;
    public static final int   RHYTHM_MAX_STACKS             = 5;

    // ── Static Discharge ──
    public static final float STATIC_SPLASH_BASE      = 4f;
    public static final float STATIC_SPLASH_PER_LEVEL = 1f;
    public static final int   STATIC_SPLASH_CAP       = 14;

    // ── Resonant Rounds (% of target max HP) ──
    public static final float RESONANT_PCT_BASE      = 0.04f;
    public static final float RESONANT_PCT_PER_LEVEL = 0.008f;
    public static final float RESONANT_PCT_CAP       = 0.10f;

    // ── Legendary signatures ──
    public static final int   SOULFORGE_KILLS_PER_LEVEL_UP  = 5;
    public static final int   JUDGMENT_COOLDOWN_FIRES       = 5;
    public static final int   JUDGMENT_LANCE_RANGE          = 20;
    public static final float JUDGMENT_DAMAGE_MULTIPLIER    = 3.0f;
    public static final int   HELLFIRE_NOVA_RADIUS          = 2;
    public static final float HELLFIRE_NOVA_DAMAGE_FRACTION = 0.75f;

    // ── Extended Mag ──
    public static final int   EXTENDED_MAG_BASE_COUNT      = 1;
    public static final int   EXTENDED_MAG_LEVELS_PER_STEP = 3;
    public static final int   EXTENDED_MAG_MAX_COUNT       = 4;

    // -------------------------------------------------------------------------------------
    // TIER = PRICED ABILITY BUDGET (new-game-balancr order 2). Rarity NEVER raises a weapon's
    // POWER band (that stays a role property, SECTION 9) — it buys ABILITIES, and now abilities
    // are PRICED in power points (PP = %-of-reference-DPT/eHP, the SAME currency the level-up
    // cards use; see GameMath.abilityPowerPoints). Each tier gets an ability-PP BUDGET; the
    // WeaponRoller rolls abilities until the budget is spent — never past. This converts the whole
    // "PLACEHOLDER — flag for playtesting" ability catalogue above into priced content: magnitudes
    // may be tuned freely, the price recomputes from the magnitude. BalanceSchema R-ABILITY asserts
    // every rollable tier/level roll fits its tier budget. See docs/game-balance-authority.txt.
    // -------------------------------------------------------------------------------------
    /** Ability-PP budget a COMMON weapon may spend on abilities (none — commons are vanilla). */
    public static final float TIER_ABILITY_PP_BUDGET_COMMON    = 0f;
    /** Ability-PP budget an UNCOMMON weapon may spend (~one modest ability). */
    public static final float TIER_ABILITY_PP_BUDGET_UNCOMMON  = 6f;
    /** Ability-PP budget a RARE weapon may spend (~two abilities). */
    public static final float TIER_ABILITY_PP_BUDGET_RARE      = 12f;
    /** Ability-PP budget an EPIC weapon may spend (~three abilities). */
    public static final float TIER_ABILITY_PP_BUDGET_EPIC      = 20f;
    /** Ability-PP budget a LEGENDARY weapon may spend (~four abilities + a signature). */
    public static final float TIER_ABILITY_PP_BUDGET_LEGENDARY = 30f;
    /** Fractional slack the roller may overspend a tier budget by before an ability is rejected (±20%). */
    public static final float TIER_ABILITY_PP_TOLERANCE        = 0.20f;

    // ── ABILITY PRICING UTILISATION WEIGHTS ──────────────────────────────────────────────
    // An ability's PP is its magnitude converted to a %-of-reference value, DISCOUNTED by how often
    // that value actually applies (a bonus that only fires below 30% HP is worth a fraction of an
    // always-on bonus of the same magnitude). These weights are the pricing MODEL — designer data,
    // tunable; the price always recomputes from (weight * live magnitude). GameMath.abilityPowerPoints
    // classifies each WeaponAbility into one of these and applies the matching weight.
    /** Always-on / on-every-hit damage multipliers (crit expectation, rhythm). Full weight. */
    public static final float ABILITY_UTIL_ALWAYS_ON        = 1.00f;
    /** Chance-gated crowd control (stagger, kinetic slam) — defensive value, not raw damage. */
    public static final float ABILITY_UTIL_CROWD_CONTROL    = 0.45f;
    /** Bypass/penetration value (armor pierce) — only pays off vs shielding enemies. */
    public static final float ABILITY_UTIL_PENETRATION      = 0.30f;
    /** Damage-over-time (rend, incendiary) — refreshes not stacks, and overkills dying targets. */
    public static final float ABILITY_UTIL_DAMAGE_OVER_TIME = 0.60f;
    /** Close-range / long-range / first-shot / low-HP conditional damage bonuses. */
    public static final float ABILITY_UTIL_CONDITIONAL      = 0.30f;
    /** Lifesteal-style sustain expressed as a fraction of damage dealt. */
    public static final float ABILITY_UTIL_SUSTAIN_FRACTION = 0.50f;
    /** Flat per-event HP/armour sustain and flat AoE burst — small, situational. */
    public static final float ABILITY_UTIL_FLAT_EVENT       = 0.35f;
    /** Percent-of-target-max-HP amplifiers (resonant) — strong but conditional on a bleed. */
    public static final float ABILITY_UTIL_PERCENT_MAX_HP   = 0.30f;
    /** PP per extra count for count utilities (extra pierce, +clip step, extra burst round). */
    public static final float ABILITY_PP_PER_COUNT          = 2.0f;
    /** Flat nominal PP for pure-utility on-kill economy abilities (scavenger, salvage, credits, medic, XP). */
    public static final float ABILITY_PP_UTILITY_NOMINAL    = 3.0f;
    /** Flat nominal PP for a legendary SIGNATURE ability (priced as a fixed marquee effect). */
    public static final float ABILITY_PP_LEGENDARY_SIGNATURE = 10.0f;

    // -------------------------------------------------------------------------------------
    // DEPTH-GATED LOOT TIERS + THE PITY RULE (new-game-balancr order 2). The game must actually
    // SUPPLY the arsenal the gear curve expects — a gate without supply is just a difficulty spike.
    // Dropped weapons roll their tier from a per-region BAND (indices into WeaponTier's ordinal:
    // 0 COMMON, 1 UNCOMMON, 2 RARE, 3 EPIC, 4/5 LEGENDARY). Region index = floor((depth-1)/band).
    // Regions beyond the last band entry clamp to the last (deepest) band. The run BEGINS on the
    // curve: region 1 offers COMMON..UNCOMMON (matching the start-room offer band, order 1).
    // -------------------------------------------------------------------------------------
    /** Per-region MINIMUM dropped-weapon tier ordinal, indexed by region (clamped to last entry). */
    public static final int[] WEAPON_DROP_TIER_MIN_BY_REGION = {0, 1, 2, 3};
    /** Per-region MAXIMUM dropped-weapon tier ordinal, indexed by region (clamped to last entry). */
    public static final int[] WEAPON_DROP_TIER_MAX_BY_REGION = {1, 2, 3, 5};
    /**
     * THE PITY RULE: every region must PLACE at least this many weapons at its tier band so no run is
     * starved of its curve by RNG. RunStats tracks upgrades seen this region; the floor generator
     * force-spawns one at a region's last floor if the region is about to end at zero. Range: 1–2.
     */
    public static final int   GUARANTEED_UPGRADE_PER_REGION = 1;

    // =====================================================================================
    // SECTION 16 — SHOP ECONOMY (UAC Fabricator)
    // CONSOLIDATED from GameBalance (Balance Authority, order 1). Stock size, category
    // weights, base prices, and the depth/rarity price scaling. The credit economy is still
    // flagged provisional (the sink is unproven in play — knowledge doc SECTION 12), but its
    // numbers now live in the single source of truth. Machine PLACEMENT geometry
    // (SHOP_TWO_MACHINE_MIN_SPACING) stays in GameBalance — layout, not economy.
    // =====================================================================================

    /** Minimum UAC Fabricator machines per non-boss floor (guaranteed credit sink). */
    public static final int   SHOP_MIN_PER_FLOOR            = 1;
    /** Maximum machines per floor. */
    public static final int   SHOP_MAX_PER_FLOOR            = 2;
    /** Probability a floor rolls the second machine. */
    public static final float SHOP_SECOND_MACHINE_CHANCE    = 0.40f;

    // Stock roll — offers per machine and weighted category pool for the remainder slots.
    public static final int   SHOP_ENTRY_MIN                 = 9;
    public static final int   SHOP_ENTRY_MAX                 = 9;
    public static final int   SHOP_CAT_WEIGHT_WEAPON_LEVELUP = 26;
    public static final int   SHOP_CAT_WEIGHT_AMMO           = 24;
    public static final int   SHOP_CAT_WEIGHT_MEDKIT         = 18;
    public static final int   SHOP_CAT_WEIGHT_ABILITY        = 16;
    public static final int   SHOP_CAT_WEIGHT_TIER_UPGRADE   = 16;
    /** Weight multiplier applied to the favoured category group when a machine is biased. */
    public static final float SHOP_BIAS_WEIGHT_MULTIPLIER    = 2.0f;

    // PRICING FROM POWER, NOT VIBES (new-game-balancr order 3, part C). Every offer's price derives
    // from its VALUE IN POWER POINTS through GameMath.shopPrice — there are ZERO hand-set per-offer
    // base prices (acceptance criterion). A weapon-class upgrade prices on its ability/level PP; a
    // consumable prices on its supply value converted to PP. One knob (SHOP_CREDITS_PER_POWER_POINT)
    // sets the whole economy's price level, so the credit BAND (R-CREDITS) is tuned by moving a single
    // number. depthFactor = 1 + SHOP_DEPTH_PRICE_SCALE * (depth - 1) keeps prices meaningful as kill
    // bounties grow with depth.
    public static final float SHOP_DEPTH_PRICE_SCALE       = 0.10f;
    /** Credits charged per power point of an offer's priced value — the single price-level knob. Range: 24–48. */
    public static final float SHOP_CREDITS_PER_POWER_POINT = 36f;
    /** Damage supplied per power point when pricing an ammo box (box damage / this = its PP value). Range: 60–120. */
    public static final float SHOP_AMMO_DAMAGE_PER_POWER_POINT = 90f;
    /** HP restored per power point when pricing a medkit (heal amount / this = its PP value). Range: 8–16. */
    public static final float SHOP_HEAL_HP_PER_POWER_POINT     = 11f;
    /** PP value of a single weapon LEVEL-UP offer (a level is ~+10% weapon damage). Range: 8–14. */
    public static final float SHOP_WEAPON_LEVEL_UP_POWER_POINTS = 10f;
    // A weapon TIER-UPGRADE prices on the ABILITY-PP budget its destination tier unlocks
    // (TIER_ABILITY_PP_BUDGET_*, SECTION 15) — the marquee value is the ability slot the tier buys.
    // A PLAYER-ABILITY boon prices on the level-up card's own PP (LEVEL_UP_BUDGET_PP), since a shop
    // boon is a paid acquisition of the same budget-equal card. Both are read directly from those
    // sections in DefaultShopOfferSource — no separate price constants here.
    /** Ammo "large box" multiplier over the standard box size. */
    public static final int   SHOP_AMMO_LARGE_BOX_MULTIPLIER = 2;

    // =====================================================================================
    // SECTION 19 — ROUTE ECONOMICS (new-game-balancr order 7) — the MAP joins the contract
    // Orders 1-6 balance FLOORS; the player plays a JOURNEY through the route map's branching
    // DAG (COMBAT / ELITE / CACHE / REST / SHOP / MYSTERY / EVENT). Every balance-bearing
    // route number now lives HERE — node budget scales, affix multipliers, calm/elite/mystery
    // payoffs, the mystery weight table, the audited map-shape constants, the pricing model's
    // knobs and every route band. util/RouteMapConstants keeps re-export shims under the old
    // names (order-1 discipline, structurally enforced by R-DOT), so no route call site churned;
    // COSMETIC overlay layout stays in RouteMapConstants and is NOT mirrored here.
    //
    // THE LEDGER: every node type, affix and mystery outcome is priced in
    // route/RouteEconomics (a NodeEconomics row) and folded into one comparable EV by
    // util/RouteEconomicsModel through GameMath.nodeExpectedValue. Rules R-ROUTE-PRICED,
    // R-RISK-PREMIUM, R-CALM-COST, R-MYSTERY-EV, R-PIPS-DERIVED, R-HONEST-SAFE,
    // R-TRAJECTORY and R-ROUTE-GUARANTEES enforce the numbers below.
    // See docs/game-balance-authority.txt and docs/route-map-system.txt.
    // =====================================================================================

    // --- A. NODE BUDGET SCALES (the THREAT side of every node) ---------------------------
    // Multipliers on the raw, depth-scaled Threat-Point budget (never a bypass of the depth
    // ramp — route-map DEPTH RAMP INVARIANT). 1.0 == a standard COMBAT floor.
    /** CALM floors (CACHE / REST): light stragglers only. Bounded by R-HONEST-SAFE. Range: 0.15–0.30. */
    public static final float ROUTE_CALM_BUDGET_SCALE  = 0.28f;
    /** Calm-but-not-empty floors (SHOP, MYSTERY vault): real but light resistance. Range: 0.4–0.6. */
    public static final float ROUTE_LIGHT_BUDGET_SCALE = 0.50f;
    /**
     * ELITE floor budget: a mini-setpiece. Its REWARD must price up with it (R-RISK-PREMIUM),
     * so raising this without raising the vault fails the audit. Range: 1.3–1.8.
     */
    public static final float ELITE_BUDGET_SCALE       = 1.5f;
    /** EVENT floor budget — a beat of story, not a fight. Range: 0.10–0.25. */
    public static final float EVENT_BUDGET_SCALE       = 0.15f;
    /** REGION_GATE ceremonial airlock budget — a pacing breath. Range: 0.05–0.20. */
    public static final float GATE_BUDGET_SCALE        = 0.10f;

    // --- B. ELITE AFFIXES: threat multiplier AND the vault that must pay for it -----------
    // An affix that raises threatCost MUST raise the vault (R-RISK-PREMIUM prices the pair).
    /** Probability an ELITE node rolls an affix. Range: 0.4–0.8. */
    public static final float AFFIX_ROLL_CHANCE_ELITE   = 0.60f;
    /** Probability a MYSTERY node rolls an affix (MYSTERY carries no affix catalog in v1). */
    public static final float AFFIX_ROLL_CHANCE_MYSTERY = 0.25f;
    /** OVERCLOCKED: budget multiplier (enemies "hit harder" = more Threat spent). Range: 1.1–1.3. */
    public static final float AFFIX_OVERCLOCKED_BUDGET_MULT = 1.2f;
    /** SWARM: budget multiplier (the crowd-control test — the one fewer-bodies exception). Range: 1.3–1.6. */
    public static final float AFFIX_SWARM_BUDGET_MULT       = 1.5f;
    /** OVERCLOCKED vault premium: extra owned-ammo boxes that pay for its raised threat. */
    public static final int   AFFIX_OVERCLOCKED_VAULT_AMMO_BOXES = 1;
    /** SWARM vault premium: extra owned-ammo boxes that pay for its raised threat. */
    public static final int   AFFIX_SWARM_VAULT_AMMO_BOXES       = 4;
    /** IRRADIATED: extra radioactive-barrel weight on top of the ELITE base. */
    public static final float AFFIX_IRRADIATED_BARREL_WEIGHT_BONUS = 0.12f;
    /** IRRADIATED: extra 'g' radioactive barrels stamped as hazard pools. */
    public static final int   AFFIX_IRRADIATED_EXTRA_BARRELS = 3;
    /** FORTIFIED: extra cover columns 'P' (a slugfest of angles). */
    public static final int   AFFIX_FORTIFIED_EXTRA_COLUMNS = 3;
    /** FORTIFIED: extra armour pickups (an eHP bump the player can also claim). */
    public static final int   AFFIX_FORTIFIED_EXTRA_ARMOUR = 1;
    /** VOLATILE: extra explosive barrels 'E' — the arena itself is a weapon for both sides. */
    public static final int   AFFIX_VOLATILE_EXTRA_BARRELS = 4;

    // --- C. CALM-NODE PAYOFFS (re-priced as order-3 economy inputs, R-CALM-COST) ----------
    // A calm node buys safety with TEMPO and LOOT: its total EV must sit 10-30% BELOW a
    // standard combat node's. The order-8 hand-set payoffs sat far ABOVE it (a chained
    // cache/rest route sailed over the order-3 supply band), so they are re-priced here.
    /** Ammo boxes stamped in a cache's cargo bay, spread across OWNED ammo types. Range: 1–3. */
    public static final int   CACHE_AMMO_BOXES        = 2;
    /** Guaranteed field medkits ('H') in a cache. */
    public static final int   CACHE_MEDKITS           = 1;
    /** Guaranteed stim-packs ('+') in a cache. */
    public static final int   CACHE_STIMS             = 0;
    /** Guaranteed armour pickups in a cache (shard 'a' shallow, vest 'A' deep). */
    public static final int   CACHE_ARMOUR            = 0;
    /** Depth at/after which the cache armour drop upgrades from a shard 'a' to a vest 'A'. */
    public static final int   CACHE_ARMOUR_VEST_DEPTH = 6;
    /**
     * MED-BAY auto-doc one-shot heal, as a fraction of the player's max HP. Raised from the
     * order-8 hand-set 0.35: a REST node earns NO floor XP and almost no loot, so at 0.35 it
     * priced far BELOW the calm band (a sucker node). Range: 0.5–1.0.
     */
    public static final float REST_HEAL_FRACTION      = 1.0f;
    /** Take-away field medkits ('H') the clinic stocks near its exit (order-7 re-pricing). */
    public static final int   REST_MEDKITS            = 1;
    /** Take-away stim-packs ('+') the clinic stocks alongside the medkit. */
    public static final int   REST_STIMS              = 1;
    /** Take-away armour pickups the clinic stocks (shard 'a' shallow, vest 'A' deep). */
    public static final int   REST_ARMOUR             = 1;
    /** Depth at/after which the clinic's armour stock upgrades from a shard 'a' to a vest 'A'. */
    public static final int   REST_ARMOUR_VEST_DEPTH  = 4;

    // --- D. ELITE PAYOFF (the vault the risk premium pays for, R-RISK-PREMIUM) ------------
    /** Ammo boxes stamped in the gated vault. Range: 2–5. */
    public static final int   ELITE_AMMO_BOXES        = 3;
    /** Guaranteed field medkits ('H') behind the vault. */
    public static final int   ELITE_MEDKITS           = 1;
    /** Guaranteed stim-packs ('+') behind the vault. */
    public static final int   ELITE_STIMS             = 1;
    /** Guaranteed armour pickups behind the vault (shard 'a' shallow, vest 'A' deep). */
    public static final int   ELITE_ARMOUR            = 1;
    /** Depth at/after which the ELITE armour drop upgrades from a shard 'a' to a vest 'A'. */
    public static final int   ELITE_ARMOUR_VEST_DEPTH = 4;

    // --- E. MYSTERY OUTCOME TABLE (weights sum to 100; R-MYSTERY-EV audits the mean) ------
    public static final int MYSTERY_WEIGHT_VAULT          = 16;
    public static final int MYSTERY_WEIGHT_SECRET_WARREN  = 18;
    public static final int MYSTERY_WEIGHT_TRAP_GAUNTLET  = 18;
    public static final int MYSTERY_WEIGHT_AMBUSH         = 18;
    public static final int MYSTERY_WEIGHT_LORE_SIGNAL    = 14;
    public static final int MYSTERY_WEIGHT_MALFUNCTION    = 16;
    /** Loot boxes in a MYSTERY vault jackpot (the dream pull — richer than an ELITE vault). */
    public static final int MYSTERY_VAULT_AMMO_BOXES  = 4;
    /** Loot boxes waiting at the end of a TRAP GAUNTLET (modest reward for patience). */
    public static final int MYSTERY_TRAP_AMMO_BOXES   = 2;
    /** Medkits waiting at the exit of a TRAP GAUNTLET. */
    public static final int MYSTERY_TRAP_MEDKITS      = 1;
    /** Explosive/radioactive barrels the TRAP GAUNTLET degrade stamps (traps -> barrels fallback). */
    public static final int MYSTERY_TRAP_BARRELS      = 6;
    /** Extra keycard-gated loot the SECRET WARREN promises (rewards those who search). */
    public static final int MYSTERY_WARREN_AMMO_BOXES = 2;
    public static final int MYSTERY_WARREN_MEDKITS    = 1;

    // --- F. EVENT-NODE PAYOFFS (the average an event choice hands out) -------------------
    /** XP awarded by an event choice at its small tier. */
    public static final int   EVENT_XP_SMALL              = 60;
    /** XP awarded by an event choice at its large tier. */
    public static final int   EVENT_XP_LARGE              = 150;
    /** Ammo boxes an event choice hands out (spread across owned ammo types, cache-style). */
    public static final int   EVENT_AMMO_BOXES            = 5;
    /** Next-floor encounter-budget nudge an event choice may apply (e.g. escort +10%). */
    public static final float EVENT_NEXT_FLOOR_BUDGET_BONUS = 0.10f;
    /** Fraction of a mimic-ambush roll's [0,1) space that resolves as loot (else an ambush sting). */
    public static final float EVENT_STASH_LOOT_CHANCE     = 0.60f;

    // --- G. AUDITED MAP SHAPE (branch width / spread / windows / graph guarantees) --------
    // These stop being layout trivia the moment the JOURNEY is the audited unit: they decide
    // how many real choices a layer offers and how often relief is reachable.
    public static final int   BRANCH_WIDTH_MINIMUM   = 2;
    public static final int   BRANCH_WIDTH_MAXIMUM   = 4;
    /** Probability a source node grows an extra edge to a projected-lane neighbour. Range: 0.4–0.7. */
    public static final float BRANCH_SPREAD_CHANCE   = 0.55f;
    /** Layers over which a CACHE/REST must be OFFERED at least once (ammo-economy protection). */
    public static final int   RESOURCE_RELIEF_WINDOW = 4;
    /** Sliding window (in layers) the shop pacing cap is measured over. */
    public static final int   SHOP_WINDOW_LAYERS     = 4;
    /** Max SHOP nodes permitted inside any SHOP_WINDOW_LAYERS-wide window (economy pacing). */
    public static final int   SHOP_MAX_PER_WINDOW    = 2;
    /** R-ROUTE-GUARANTEES: minimum DISTINCT node types every selectable layer must offer (no fake choices). */
    public static final int   ROUTE_MIN_NODE_TYPES_PER_LAYER = 2;
    /**
     * Layer index INSIDE a region band whose nodes carry the region's guaranteed UPGRADE-bearing
     * option (ELITE / SHOP). Every path through the band crosses this layer, so the order-2 pity
     * rule becomes a graph property instead of a spawn hope. Range: 0..bandSize-2.
     */
    public static final int   ROUTE_UPGRADE_GUARANTEE_LAYER = 1;
    /** Layer index inside a region band whose nodes carry the region's guaranteed CALM option. */
    public static final int   ROUTE_CALM_GUARANTEE_LAYER    = 2;

    // --- H. THE PRICING MODEL (how a node's ledger row becomes one comparable EV) ---------
    // Common currency = POWER POINTS (PP), the same unit orders 2/3 use for cards, abilities
    // and shop prices: ammo damage / SHOP_AMMO_DAMAGE_PER_POWER_POINT, HP /
    // SHOP_HEAL_HP_PER_POWER_POINT, credits / SHOP_CREDITS_PER_POWER_POINT.
    /**
     * DURABILITY WEIGHT on PERMANENT power (XP levels + weapon-class upgrades) against one-shot
     * consumables. A level-up or a gun applies to EVERY remaining floor; an ammo box is spent
     * once. Without this weight the model says fighting never pays (a calm floor's un-spent ammo
     * and un-taken damage always beat a floor's XP), which is exactly how the un-priced map let a
     * calm-chaining route sail above the supply band. Range: 2.0–4.0.
     */
    public static final float ROUTE_PERMANENT_POWER_WEIGHT = 2.6f;
    /**
     * Power points charged for the DEATH risk of ONE standard combat floor, on top of the ammo/HP
     * that floor consumes. Charged on the node's threat RATIO (a 1.5x ELITE pays 1.5x this), so the
     * danger price is depth-stable — Threat Points compound with depth while a floor's rewards do
     * not, and a raw per-TP charge would make every deep floor read as a catastrophic loss. Range:
     * 1.0–6.0 power points.
     */
    public static final float ROUTE_RISK_POWER_POINTS_PER_STANDARD_FLOOR = 3f;
    /** PP value of one reliable weapon-class upgrade opportunity (priced as the shop's significant buy). */
    public static final float ROUTE_UPGRADE_OPPORTUNITY_POWER_POINTS = SHOP_SIGNIFICANT_BUY_POWER_POINTS;
    // How much of ONE weapon-class upgrade a node reliably offers (0..1) — the honest read of what
    // each node type actually delivers today. ELITE is NOT 1.0: it has no guaranteed weapon SPAWN
    // (weapons come from WeaponSpawnPoints, not stampable tiles), only a richer drop band + the rack.
    // SHOP is not 1.0 either: its purchase is paid for at a fair price (GameMath.shopPrice), so only
    // the CHOOSE-EXACTLY-WHAT-YOU-NEED surplus over a random drop is credited.
    public static final float ROUTE_UPGRADE_OPPORTUNITY_COMBAT = 0.35f;
    public static final float ROUTE_UPGRADE_OPPORTUNITY_ELITE  = 0.60f;
    public static final float ROUTE_UPGRADE_OPPORTUNITY_SHOP   = 0.30f;
    public static final float ROUTE_UPGRADE_OPPORTUNITY_CACHE  = 0.05f;
    /** Threat scale a BOSS floor is priced at for the ledger (bosses are governed by R-BOSS-*, not the node bands). */
    public static final float ROUTE_BOSS_THREAT_SCALE = 2.0f;
    /** HP a TRAP GAUNTLET's hazard field is expected to bite out of the player (priced as negative heal). */
    public static final float ROUTE_TRAP_GAUNTLET_HAZARD_HIT_POINTS = 45f;
    /** HP a MALFUNCTION sector's failing hazards are expected to cost (the bad-but-survivable pull). */
    public static final float ROUTE_MALFUNCTION_HAZARD_HIT_POINTS   = 55f;
    /** Ordinary-loot share a MALFUNCTION sector rolls (its config switches medkits/armour off). */
    public static final float ROUTE_MALFUNCTION_LOOT_SCALE          = 0.5f;
    /** Share of an EVENT's payoff any single choice delivers, averaged over the v1 choice catalogue. */
    public static final float ROUTE_EVENT_EXPECTED_CHOICE_SHARE     = 0.40f;
    /**
     * Share of a standard floor's ORDINARY room loot a bespoke, curated small floor rolls
     * (MED_BAY clinic, EVENT room, GATE airlock — a handful of rooms, not a full dungeon).
     * Range: 0.2–0.6.
     */
    public static final float ROUTE_BESPOKE_FLOOR_LOOT_SCALE = 0.35f;

    // --- I. THE ROUTE BANDS (what the audit enforces) -------------------------------------
    /** R-RISK-PREMIUM: (reward premium)/(threat premium) — danger pays, slightly better than fair. */
    public static final float ROUTE_RISK_PREMIUM_MIN = 1.00f;
    public static final float ROUTE_RISK_PREMIUM_MAX = 1.20f;
    /**
     * R-CALM-COST: a calm node's EV must sit this far BELOW a standard combat node's — safety is
     * bought with tempo and loot. The upper edge is 0.35 rather than the 0.30 the order-7 idea
     * sketched, because the discount necessarily WIDENS with depth and one band must cover all of
     * 1..15: a combat floor's own value grows down the run (its ammo supply rides the gear curve, its
     * progress rides the ability curve) while a sanctuary node's payoff is capped by the player's own
     * maximum HP. Measured range across the audited depths: cache 0.12-0.23, shop 0.18-0.23, rest
     * 0.13-0.31, event 0.24-0.32.
     */
    public static final float ROUTE_CALM_EV_DISCOUNT_MIN = 0.10f;
    public static final float ROUTE_CALM_EV_DISCOUNT_MAX = 0.35f;
    /** R-MYSTERY-EV: the weighted mystery table EV may differ from a combat node's by at most this. */
    public static final float ROUTE_MYSTERY_EV_TOLERANCE = 0.15f;
    /**
     * R-MYSTERY-EV (worst case): the worst outcome's expected NET resource loss, as a multiple of
     * ONE floor's maximum modelled drain (the order-3 net-drain band max plus the un-covered share
     * of a floor's ammo demand). "Bad but survivable" as arithmetic. Range: 1.0–2.0.
     */
    public static final float ROUTE_MYSTERY_WORST_LOSS_FLOOR_MULTIPLIER = 1.5f;
    /** R-HONEST-SAFE: a node whose icon promises safety (CACHE/REST) may face at most this share of a combat floor's TP. */
    public static final float ROUTE_HONEST_SAFE_MAX_THREAT_RATIO = 0.30f;
    /** R-PIPS-DERIVED: threatCost ratio (vs the standard combat node) at/below which a node reads CALM. */
    public static final float ROUTE_PIP_CALM_MAX_THREAT_RATIO     = 0.60f;
    /** R-PIPS-DERIVED: ratio at/below which a node reads STANDARD; above it reads DANGER. */
    public static final float ROUTE_PIP_STANDARD_MAX_THREAT_RATIO = 1.25f;

    // --- J. THE TRAJECTORY AUDIT (the JOURNEY replaces the floor as the audited unit) -----
    // Three deterministic path policies (SAFEST / DEADLIEST / BALANCED) are walked through real
    // generated maps; the cumulative order-3/4 quantities are checked at every region boundary
    // over the ACTUAL nodes visited. The bands are WIDER than the per-floor bands by design:
    // their ENDS are the route's real difficulty range, and both ends must stay fair.
    /** Seeds the trajectory + reachability audits generate real route maps for. Range: 50–200. */
    public static final int   ROUTE_TRAJECTORY_SEED_COUNT = 100;
    /**
     * Cumulative scarcity S over a whole journey (the per-FLOOR band is [0.75, 0.95]). The floor is
     * the starved end an all-ELITE run rides — below it even the emergency lifeline could not keep a
     * fighting retreat armed; the ceiling is the generous end a calm-chaining run rides — above it
     * ammo would stop being a resource at all. Measured: 0.59 (DEADLIEST) .. 1.50 (SAFEST).
     */
    public static final float ROUTE_TRAJECTORY_SCARCITY_MIN = 0.55f;
    public static final float ROUTE_TRAJECTORY_SCARCITY_MAX = 1.60f;
    /**
     * Cumulative per-floor net HP drain fraction over a journey (the per-FLOOR band is [0.05, 0.15]).
     * NEGATIVE means the route BANKS health — which is exactly what a calm route buys with its skipped
     * XP and loot. Measured: -0.78 (SAFEST banks) .. +0.39 (DEADLIEST bleeds ~40% of eHP per floor,
     * survivable only by routing calm before the boss). Both ends must stay inside these bounds.
     */
    public static final float ROUTE_TRAJECTORY_DRAIN_MIN = -0.85f;
    public static final float ROUTE_TRAJECTORY_DRAIN_MAX =  0.45f;
    /**
     * Cumulative XP pace: banked XP / XP needed to stand at the depth's expected level (the per-floor
     * yield is ~1.11). The floor says even the safest route ends a region no worse than ~30% under the
     * curve (the order-4 catch-up band then pulls it back); the ceiling caps how far a fight-everything
     * route may out-level the content. Measured: 0.79 (SAFEST) .. 2.33 (DEADLIEST — it also collects
     * the ELITEs the upgrade guarantee plants), with ~5% headroom above.
     */
    public static final float ROUTE_TRAJECTORY_XP_PACE_MIN = 0.70f;
    public static final float ROUTE_TRAJECTORY_XP_PACE_MAX = 2.45f;
    /**
     * Depth coupling measured at the level the journey's XP ACTUALLY bought (the per-floor band is
     * [0.90, 1.20], which assumes an exactly on-curve player). This is the fairness end-stop of the
     * whole route: neither the safest nor the deadliest way to play may leave the player unfairly weak
     * against the floor, or trivially strong. Measured: 0.87 (SAFEST) .. 1.47 (DEADLIEST), with a small
     * margin either side — a route that fights everything SHOULD end a region ahead of the curve; it
     * just may not run away with the game.
     */
    public static final float ROUTE_TRAJECTORY_COUPLING_MIN = 0.80f;
    public static final float ROUTE_TRAJECTORY_COUPLING_MAX = 1.55f;
}
