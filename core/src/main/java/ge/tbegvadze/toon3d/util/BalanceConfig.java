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
    /**
     * Fraction of each incoming hit absorbed by armour (depleting armour instead of HP). Range: 0.25–0.75.
     * balance-overhaul order 4 (H8): 0.50 -> 0.67, so armour is worth wearing.
     */
    public static final float ARMOUR_ABSORB_FRACTION  = 0.67f;

    // Heal magnitudes scaled ~1.8x in the economy rescale (idea-A, iteration 2): enemy damage
    // rose, so a floor's INCOMING damage rose, and the heals had to rise with it to keep the
    // per-floor net HP drain in the 5-15% band (SECTION 10). Player eHP itself is UNCHANGED.
    // MEDKIT_STIM_HEAL / MEDKIT_FULL_HEAL / ARMOUR_SHARD_VALUE / ARMOUR_VEST_VALUE moved to SECTION 20 as fractions of max (R10).

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

    // POWER-LADDER REBASE (balance-overhaul order 1, R8): every archetype's depth-1 HP and damage were
    // re-set so the fight lengths land in the R8 hit targets against the on-curve reference — the Assault
    // Rifle (COMMON, L1) at 3 tiles = 15.2 per hit, and the 205-eHP start player:
    //     CHAFF      dies in 1-2 hits, lands >= 10 hits before the player dies
    //     SOLDIER    3-4 hits  | 7-9 hits
    //     BRUISER    5-7 hits  | 4-6 hits
    //     MINI_ELITE 8-12 hits | 3-5 hits
    // i.e. HP roughly -35..-60% against the economy-rescale numbers, damage raised only where the band
    // demands it (SOLDIER +65..+110%, BRUISER +75..+100%, the mini-elite +75%; CHAFF damage is unchanged —
    // the old values already land >= 10 hits). Damage sits at the LOW end of each band on purpose: the
    // heal economy (R-HEALDRAIN-DEPTH) has to absorb every point of it. The
    // per-archetype comments below were written for (their "was X" notes describe that older history).
    // No more sponges: fights are short and hits land hard; floors get more bodies in balance-overhaul
    // order 2. Verified by R-ENEMY (BalanceSchema.enemyHitResults) and BalanceReport's ENEMIES table.

    // GORE_BITER (spawn '3') — fast light melee; spawns in packs. (was 18 HP / 7 dmg)
    public static final int GORE_BITER_MAX_HEALTH          = 28;
    public static final int GORE_BITER_ATTACK_DAMAGE       = 12;
    public static final int GORE_BITER_MOVE_EVERY_N_TURNS  = 1;

    // EYE_TYRANT (spawn '2') — fast ranged kiter. (was 18 HP / 7 dmg)
    public static final int EYE_TYRANT_MAX_HEALTH          = 28;
    public static final int EYE_TYRANT_ATTACK_DAMAGE       = 11;
    public static final int EYE_TYRANT_RANGE_TILES         = 5;

    // ACID_DRONE (spawn '$') — ranged mechanical. (was 22 HP / 8 dmg)
    public static final int ACID_DRONE_MAX_HEALTH          = 45;
    public static final int ACID_DRONE_ATTACK_DAMAGE       = 24;
    public static final int ACID_DRONE_RANGE_TILES         = 4;
    public static final int ACID_DRONE_MOVE_EVERY_N_TURNS  = 1;

    // VOID_SHROUD (spawn '^') — fast stealth melee FLANKER (Pillar 2). (was 25 HP / 9 dmg)
    public static final int VOID_SHROUD_MAX_HEALTH         = 50;
    public static final int VOID_SHROUD_ATTACK_DAMAGE      = 25;
    public static final int VOID_SHROUD_MOVE_EVERY_N_TURNS = 1;
    /**
     * Flank strike bonus (Pillar 2): the Void Shroud prefers the tile behind the player's facing
     * and hits HARDER from that blind side, so "rotate to face it" is the counterplay. The base
     * 13 dmg becomes ~21 from behind — still well under the 25%-eHP telegraph cap (~51, idea 4, Pillar 5).
     */
    public static final float VOID_SHROUD_FLANK_DAMAGE_MULTIPLIER = 1.6f;

    // MIRE_WRAITH (spawn '5') — slow ground-based ranged acid; tanky. (was 38 HP / 7 dmg)
    public static final int MIRE_WRAITH_MAX_HEALTH         = 52;
    public static final int MIRE_WRAITH_ATTACK_DAMAGE      = 23;
    public static final int MIRE_WRAITH_RANGE_TILES        = 3;
    public static final int MIRE_WRAITH_MOVE_EVERY_N_TURNS = 2;

    // SHELL_BRUTE (spawn '4') — heavy CHARGER melee (Pillar 2). (was 38 HP / 13 dmg)
    public static final int SHELL_BRUTE_MAX_HEALTH         = 95;
    public static final int SHELL_BRUTE_ATTACK_DAMAGE      = 35;
    public static final int SHELL_BRUTE_MOVE_EVERY_N_TURNS = 1;
    // Charge damage is a HEAVY fraction of the on-curve player's max HP (SECTION 23, HeavyAttack.CHARGE);
    // the old x2.4 multiplier and the 75% stop-short chance are gone (balance-overhaul order 4 override).
    /** Nearest cardinal-lane gap (tiles) that opens a charge; closer than this it just melees. Range: 2–3. */
    public static final int   SHELL_BRUTE_CHARGE_TRIGGER_MIN_TILES = 2;
    /** Farthest cardinal-lane gap (tiles) the brute will start a charge from (<= LOS). Range: 3–6. */
    public static final int   SHELL_BRUTE_CHARGE_TRIGGER_MAX_TILES = 5;

    // PLAGUE_HULK (spawn '1') — slow tank melee. (was 50 HP / 10 dmg)
    public static final int PLAGUE_HULK_MAX_HEALTH         = 60;
    public static final int PLAGUE_HULK_ATTACK_DAMAGE      = 23;
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
    // Blast damage is a HEAVY fraction of the on-curve player's max HP (SECTION 23, HeavyAttack.SELF_DESTRUCT);
    // the old x3.0 multiplier and the 68 cap are gone (balance-overhaul order 4 override).
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
    public static final int IRON_STALKER_MAX_HEALTH        = 170;
    public static final int IRON_STALKER_MELEE_DAMAGE      = 42;
    public static final int IRON_STALKER_RANGED_DAMAGE     = 30;
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
    public static final int GHOUL_MAX_HEALTH          = 30;
    public static final int GHOUL_ATTACK_DAMAGE       = 13;
    public static final int GHOUL_MOVE_EVERY_N_TURNS  = 2;

    // CRAWLER (spawn 'z') — fast, fragile low-to-the-ground melee CHAFF; rushes in.
    // Was 22 HP / 8 dmg -> TP 8.1, far UNDER the chaff band. Leaned into the fragile-glass-cannon
    // niche: 24 HP / 15 dmg (fast melee) -> TP 16.6, in band; a 1-hit kill that punishes if ignored.
    public static final int CRAWLER_MAX_HEALTH         = 15;
    public static final int CRAWLER_ATTACK_DAMAGE      = 15;
    public static final int CRAWLER_MOVE_EVERY_N_TURNS = 1;

    // REVENANT (spawn 'K') — fast, hard-hitting undead melee; punishes a slow kill.
    // Was classified SOLDIER but its stats (TP 79, golden ratio 2.4) are honestly BRUISER-tier — a
    // "soldier" that duels like a bruiser is an unfair surprise. Reclassified to BRUISER in
    // EnemyType.role() (TP 79 in the 70-120 bruiser band, gr 2.4 in the [2,4] bruiser band); it is
    // the fast, non-charging bruiser counterpart to the Shell Brute charger. Stats unchanged.
    public static final int REVENANT_MAX_HEALTH         = 85;
    public static final int REVENANT_ATTACK_DAMAGE      = 35;
    public static final int REVENANT_MOVE_EVERY_N_TURNS = 1;

    // VORTEX_EYE (spawn 'V') — short-range ranged CHAFF caster; weaker, closer kiter than Eye Tyrant.
    // TP 16.4 — already lands (just) inside the chaff band, so left unchanged by the contract pass.
    public static final int VORTEX_EYE_MAX_HEALTH         = 24;
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
    public static final int BLIGHT_CORRUPTOR_MAX_HEALTH         = 48;
    public static final int BLIGHT_CORRUPTOR_ATTACK_DAMAGE      = 26;
    public static final int BLIGHT_CORRUPTOR_MOVE_EVERY_N_TURNS = 2;

    // -------------------------------------------------------------------------
    // ELEMENTAL GOLEM family — animate mineral constructs (EnemyFamily.GOLEM).
    // -------------------------------------------------------------------------

    // AURIC_SENTINEL (spawn '(') — ranged SOLDIER whose three orbiting gold shards are BOTH its
    // armour and its ammunition (.claude/agents/ideas/elemental-golem-auric-sentinel.txt). Each shard
    // nullifies exactly ONE incoming damage instance whatever its size, and each shot SPENDS one, so
    // the player's aggression disarms it. The body under the ring is deliberately glassy (70 HP): once
    // the ring is gone one good hit is most of the fight. It is the roster's LOADOUT test — a rapid,
    // multi-hit weapon strips the ring in a burst while a single huge slug wastes its whole damage on
    // one shard — and the deliberate mirror of the burst-hungry Rimeshell Lancer.
    public static final int AURIC_SENTINEL_MAX_HEALTH           = 18;
    public static final int AURIC_SENTINEL_ATTACK_DAMAGE        = 26;
    public static final int AURIC_SENTINEL_RANGE_TILES          = 5;
    public static final int AURIC_SENTINEL_MOVE_EVERY_N_TURNS   = 1;
    /** Turns between shard launches — it can only fire as fast as it re-grows shards. Range: 1–3. */
    public static final int AURIC_SENTINEL_ATTACK_CADENCE_TURNS = 2;
    /**
     * Shallowest floor the encounter planner may spend budget on a Sentinel. By depth 3 the player
     * reliably holds a second weapon, which is precisely the decision this archetype tests; on floor 1,
     * against a single starting weapon, it would be a wall rather than a puzzle. Range: 2–4.
     */
    public static final int AURIC_SENTINEL_MIN_SPAWN_DEPTH      = 3;
    /** Shards in a full ring. THE tuning lever if the loadout swing proves too wide (3 -> 2). Range: 2–4. */
    public static final int AURIC_MAX_SHARDS                    = 3;
    /** Enemy turns between shard re-growths. The counter advances on EVERY turn, fired or not. Range: 2–5. */
    public static final int AURIC_SHARD_REGEN_TURNS             = 3;
    /**
     * The PRICED value of shard absorption, fed to GameMath.enemyEffectiveHitPoints as an armour pool.
     * Absorbing a hit outright IS "extra effective HP", so the shards are priced exactly there rather
     * than as a bespoke TP term. Derivation:
     *     shards absorbed over a fight ~= 3 (the ring) + ~0.5 (one regrow) = 3.5
     *     value per absorbed hit       ~= the R8 reference hit (15.2) * 0.8 ~= 12
     *     armorPool                     = 3.5 * 12                          = 42
     *   (balance-overhaul order 1 rebase: was 3.5 * 20 = 70 against the old 25-DPT yardstick.)
     * The 0.8 factor is the honest discount for the fact that a player who READS the enemy strips
     * shards with cheap hits, not reference-sized ones. Yields eHP 60 (4 reference hits, R8 SOLDIER).
     * RISK, STATED PLAINLY: this is the archetype whose effective eHP swings hardest with the player's
     * loadout, and one averaged pool cannot express that — the simulator's per-policy S-* bands are the
     * right instrument, and the lever if the spread is unacceptable is AURIC_MAX_SHARDS, not the HP.
     */
    public static final float AURIC_SHARD_ARMOR_POOL            = 42f;

    // CINDERFORGE_COLOSSUS (spawn '{') — the golem family's melee BRUISER ANCHOR
    // (.claude/agents/ideas/elemental-golem-cinderforge-colossus.txt). A towering furnace of magma
    // crystal that PUNISHES HESITATION with two passive mechanics: it hardens every turn it takes no
    // damage (COOLING CRUST), and every tile it leaves behind catches fire (MOLTEN TRAIL). It is the
    // roster's TEMPO test — the Shell Brute is the POSITIONING bruiser (sidestep the lane, punish the
    // recovery); the Colossus is the one you must never stop shooting.
    public static final int CINDERFORGE_COLOSSUS_MAX_HEALTH          = 75;
    public static final int CINDERFORGE_COLOSSUS_ATTACK_DAMAGE       = 36;
    /** Ponderous by design: it is KITEABLE, and it should be — the trail is the price of kiting it. Range: 1–3. */
    public static final int CINDERFORGE_COLOSSUS_MOVE_EVERY_N_TURNS  = 2;
    /**
     * Shallowest floor the encounter planner may seat a Colossus as the floor's anchor. Floor 1 is the
     * player's first weapon and first reload, and "never stop shooting" is not yet a choice they can
     * make; from floor 2 it is. Hand-authored levels are NOT gated. Range: 1–3.
     */
    public static final int CINDERFORGE_COLOSSUS_MIN_SPAWN_DEPTH     = 2;
    /** Crust stacks a fully cooled shell holds. THE lever if hesitation is punished too hard. Range: 2–4. */
    public static final int CINDERFORGE_CRUST_MAX_STACKS             = 3;
    /** Flat damage reduction each cooled crust stack contributes while it stands. Range: 3–6. */
    public static final int CINDERFORGE_CRUST_ARMOR_PER_STACK        = 4;
    /**
     * The PRICED value of the crust, fed to GameMath.enemyEffectiveHitPoints as flat reduction.
     *
     * <p>TIME-AVERAGED, and the number looks wrong without this note — a naive reader will "fix" it to
     * 12 and blow the bruiser band. LIVE crust reaches {@code MAX_STACKS * ARMOR_PER_STACK} = 12 flat,
     * but ONLY against a player who has stopped attacking. The modelled player attacks every turn (that
     * is exactly what REFERENCE_PLAYER_DPT means), and any damage shatters the whole shell, so against
     * the modelled player the crust sits near zero on most turns. Priced at the honest average of about
     * one stack of uptime: 1 * 4 ~= 3.
     *     eHP = enemyEffectiveHitPoints(110, 0, 0, 3, 25) = 110 * 25/(25-3) = 125.0
     *     TP  = (18/1) * (125.0/25) * POSITIONAL_MULT_MELEE = 18 * 5.0 * 1.00 = 90.0  (BRUISER 70-120)
     * The MOLTEN TRAIL is deliberately NOT folded in here: terrain danger has its own priced channel
     * (GameMath.hazardTileThreatPoints, printed by BalanceReport's HAZARDS section), and counting it in
     * the enemy's TP as well would double-count it.
     */
    public static final float CINDERFORGE_CRUST_AVERAGED_FLAT_REDUCTION = 3f;
    /**
     * Damage multiplier on the single hit that breaks a FULLY cooled (max-stack) shell. Breaking a fully
     * hardened Colossus is loud and rewarding — it is the PAYOFF for coming back out, not a punishment
     * for having waited. Deliberately only on a full shell, so it never fires on incidental chip.
     * Range: 1.0–1.5.
     */
    public static final float CINDERFORGE_CRUST_SHATTER_MULTIPLIER   = 1.25f;
    /** Turns a molten-trail fire tile burns. 3 keeps corridors survivable; test 4 on floors 8+. Range: 2–5. */
    public static final int CINDERFORGE_TRAIL_FIRE_TURNS             = 3;
    /** Live trail tiles ONE Colossus may hold at once, so a long chase cannot carpet a floor. Range: 4–8. */
    public static final int CINDERFORGE_TRAIL_MAX_LIVE_TILES         = 6;

    // RIMESHELL_LANCER (spawn '[') — the golem family's first ranged SOLDIER
    // (.claude/agents/ideas/elemental-golem-rimeshell-lancer.txt). A squat crystal mass with a furnace
    // core: it is the game's most damage-resistant non-elite WHILE SEALED, but its ice shell must OPEN
    // for the molten lance to fire — armoured OR dangerous, never both at once. Every other ranged enemy
    // punishes you for standing in its line; the Lancer punishes you for shooting on the wrong turn.
    public static final int RIMESHELL_LANCER_MAX_HEALTH             = 48;
    public static final int RIMESHELL_LANCER_ATTACK_DAMAGE          = 24;
    public static final int RIMESHELL_LANCER_RANGE_TILES            = 6;
    /** Heavy: it repositions slowly, and should — the shell is the reason it can afford to. Range: 1–3. */
    public static final int RIMESHELL_LANCER_MOVE_EVERY_N_TURNS     = 2;
    /**
     * LIVE per-instance flat mitigation of the sealed ice shell — the roster's toughest non-elite while
     * it stands. DROPS TO 0 the whole player turn the Lancer is charging (the punish window) and reseals
     * after the beam. This is the SIMULATED shell; it is deliberately NOT the number priced into Threat
     * Points (see {@link #RIMESHELL_SHELL_AVERAGED_FLAT_REDUCTION}) — the two must never be wired to each
     * other, because TP is a per-turn AVERAGE and the shell is up on most turns but down on one. Range: 5–9.
     */
    public static final int RIMESHELL_SHELL_ARMOR                  = 7;
    /**
     * The PRICED shell, fed to GameMath.enemyEffectiveHitPoints as flat reduction — the TIME-AVERAGE of
     * the live {@link #RIMESHELL_SHELL_ARMOR}, NOT its peak. The shell is DOWN for one turn of every ~3
     * (charge out of charge + fire + cooldown), so the honest per-turn average is round(7 * (1 - 1/3)) ≈ 5.
     *     eHP = enemyEffectiveHitPoints(90, 0, 0, 5, 25) = 90 * 25/(25-5) = 112.5
     *     TP  = (14/2) * (112.5/25) * POSITIONAL_MULT_RANGED = 7 * 4.5 * 1.30 = 40.95  (SOLDIER 36-66)
     * The friendly fire and the lane-lock are PLAYER-FAVOURING, so pricing them at zero is conservative
     * in the right direction. A naive reader will "fix" this to 7 and drift the number out of the honest
     * average — which is exactly why it is a named constant with this note, not a literal.
     */
    public static final float RIMESHELL_SHELL_AVERAGED_FLAT_REDUCTION = 5f;
    /** Enemy turns after a lance fires before it may charge again — the sealed, armoured half. Range: 1–4. */
    public static final int RIMESHELL_LANCE_COOLDOWN_TURNS         = 2;
    /**
     * Fraction of the lance's damage every OTHER enemy standing in the beam takes — golems do not care
     * about each other. NOT a bug to balance away: it is the second half of the puzzle. Pull a pack into
     * the lane, bait the shot, and the room thins itself out. Priced at zero (player-favouring). Range: 0.5–1.0.
     */
    public static final float RIMESHELL_LANCE_FRIENDLY_FIRE_FRACTION = 0.5f;

    // VERDANT_SPIRESOWER (spawn '}') — the golem family's terrain-editing melee SOLDIER
    // (.claude/agents/ideas/elemental-golem-verdant-spiresower.txt). It plants solid, sight-blocking
    // crystal SPIRES that drain life into it while they stand: shoot the golem and it heals, shoot the
    // spires and it stops — the room is already a maze. The first enemy that makes the board itself the
    // problem. Melee-only up close; it prefers to reposition behind its spires and sow at range.
    public static final int VERDANT_SPIRESOWER_MAX_HEALTH          = 45;
    public static final int VERDANT_SPIRESOWER_ATTACK_DAMAGE       = 24;
    /** Heavy and patient — it would rather reposition behind its spires than close. Range: 1–3. */
    public static final int VERDANT_SPIRESOWER_MOVE_EVERY_N_TURNS  = 2;
    /**
     * The PRICED value of the Spiresower's REGENERATION, fed to GameMath.enemyEffectiveHitPoints as an
     * ARMOUR POOL — regen is simply extra effective HP, and the model already owns that primitive. Expected
     * regen over a fight:
     *     SPIRESOWER_REGEN_PER_SPIRE (2) * expected living spires (~2, averaged — it starts at 0 and is
     *     being shot) * expected fight length (~3 turns after the R8 rebase; was ~5) = 12 HP.
     *     eHP = enemyEffectiveHitPoints(100, 20, 0, 0, 25) = 120.0 ; survivalTurns = 120/25 = 4.8
     *     TP  = (13/1) * 4.8 * POSITIONAL_MULT_MELEE = 13 * 4.8 * 1.00 = 62.4  (SOLDIER 36-66, upper-mid)
     * The spires' TERRAIN value (blocked tiles + blocked LOS) is deliberately UNPRICED: it hurts its own
     * family (ranged allies lose their lanes) as much as the player, and the model has no positional-denial
     * channel for enemies. Flag for review if the sim says it over-performs its 62 TP. Range: 12–28.
     */
    public static final float SPIRESOWER_REGEN_ARMOR_POOL         = 12f;
    /** Enemy turns between sow attempts — a readable rhythm the player learns to pre-empt. Range: 3–6. */
    public static final int SPIRESOWER_SOW_CADENCE_TURNS          = 4;
    /** Spires planted per sow (each into a distinct legal tile). Range: 1–3. */
    public static final int SPIRESOWER_SPIRES_PER_SOW             = 2;
    /** Hard cap on live spires per golem; at the cap it does not sow. Range: 3–6. */
    public static final int SPIRESOWER_MAX_LIVE_SPIRES            = 4;
    /** Chebyshev radius within which a sow may plant a spire. Range: 2–4. */
    public static final int SPIRESOWER_SOW_RADIUS_TILES           = 3;
    /** Chebyshev range within which a living spire heals its sower each turn. Range: 4–6. */
    public static final int SPIRESOWER_LEY_RANGE_TILES            = 5;
    /** HP the sower regains per LIVING spire in ley range at the end of each of its turns. Range: 1–3. */
    public static final int SPIRESOWER_REGEN_PER_SPIRE           = 2;
    /** A spire's HP — dies to roughly one shell, but that is a shell not spent on the golem. Range: 20–45. */
    public static final int SPIRESOWER_SPIRE_HIT_POINTS          = 30;
    /** World turns a spire stands before it decays on its own, so a fled fight never leaves a mazed room. Range: 6–12. */
    public static final int SPIRESOWER_SPIRE_LIFETIME_TURNS      = 8;

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
    // (baseBlock ≈ one reference hit), so it never inflates a role's TTK out of its R8 hit band.
    // Verified by BalanceReport's BLOCK section. POWER-LADDER REBASE (balance-overhaul order 1): the
    // three base gains were re-set to ~one R8 reference hit (15.2) scaled by role (was 22/30/40 against
    // the old 25-DPT yardstick), and BOTH the gain and the BLOCK_MAX cap now ride the enemy HP growth
    // (GameMath.enemyHealthAtDepth), so a deep enemy's brace stays proportional to its HP.
    // -------------------------------------------------------------------------

    /** Depth-1 hard cap on any actor's Block (scaled by the enemy HP growth at depth). Stops defend-spam from stacking into immortality. Range: 40–90. */
    public static final int   BLOCK_MAX                       = 60;
    /**
     * World turns a gained Block survives before the StatusEffectController tick zeroes it.
     * 1 = Block protects through exactly the single player turn during which the player reacts
     * to the active shield (the enemy gains it on its defend turn; the next status tick — which
     * fires AFTER that player turn's damage resolves — clears it). Range: 1–2.
     */
    public static final int   BLOCK_DECAY_TURNS               = 1;

    /** Depth-1 base Block a SOLDIER-role enemy gains on DEFEND (≈ one R8 reference hit). Range: 10–22. */
    public static final int   DEFEND_BLOCK_GAIN_SOLDIER       = 15;
    /** Depth-1 base Block a BRUISER-role enemy gains on DEFEND (braces harder). Range: 15–30. */
    public static final int   DEFEND_BLOCK_GAIN_BRUISER       = 20;
    /** Depth-1 base Block a MINI_ELITE-role enemy gains on DEFEND (the sturdiest bracer). Range: 20–40. */
    public static final int   DEFEND_BLOCK_GAIN_MINI_ELITE    = 27;

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

    // GUARD_FRONT_MULTIPLIER (0.35) was DELETED by balance-overhaul order 4 (H7): the front arc now has two
    // multipliers, GUARD_FRONT_ORDINARY / GUARD_FRONT_HEAVY, in SECTION 23.
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
    /** Sits between Void Shroud (12) and Mire Wraith (15) — consistent with its mid-soldier TP. */
    public static final int CREDIT_REWARD_AURIC_SENTINEL    = 14;
    /** Above the Shell Brute (12), far below the Iron Stalker (40) — matches its mid-BRUISER TP of ~90. */
    public static final int CREDIT_REWARD_CINDERFORGE_COLOSSUS = 16;
    /** Between Acid Drone (8) and Void Shroud (12) — consistent with its low-mid SOLDIER TP of ~41. */
    public static final int CREDIT_REWARD_RIMESHELL_LANCER  = 10;
    /** Sits between Void Shroud (12) and Mire Wraith (15) — consistent with its upper-mid SOLDIER TP of ~62. */
    public static final int CREDIT_REWARD_VERDANT_SPIRESOWER = 13;

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

    // ENEMY HP / DAMAGE DEPTH GROWTH moved to SECTION 20 (balance-overhaul order 1): the old
    // ENEMY_HEALTH_SCALE_PER_DEPTH 1.042 ("held to protect order-3 scarcity") and
    // ENEMY_DAMAGE_SCALE_PER_DEPTH 1.073 (fitted to the retired R-DEPTH coupling) are REPLACED by the
    // ladder-fitted ENEMY_HEALTH_GROWTH / ENEMY_DAMAGE_GROWTH, which R-LADDER L1 defends.
    /** Per-floor linear credit bonus: base * (1 + (depth-1) * scale). Range: 0.05–0.25. */
    public static final float CREDIT_DEPTH_SCALE           = 0.12f;

    /**
     * Levels the average player is expected to gain per floor descended (~1 level/floor). This is the
     * player-side input to the depth-coupling invariant (SECTION 9): GameMath.playerPowerAtDepth lifts
     * the player's power multiplier by EXPECTED_PERK_POWER_PP_PER_PICK power points per level gained. The boss
     * ruleset (SECTION 14) mirrors this as BOSS_EXPECTED_LEVELS_PER_DEPTH. Range: 0.7–1.3.
     */
    public static final float EXPECTED_LEVELS_PER_DEPTH    = 1.0f;

    /** Extra enemies a deepest-depth room may add over the base count. Range: 0–4. */
    public static final int   LEVEL_GEN_DEPTH_ENEMY_BONUS_MAX      = 2;
    /** At full depth, chance a light spawn is upgraded to a heavy archetype. Range: 0.0–1.0. */
    public static final float LEVEL_GEN_DEPTH_ENEMY_UPGRADE_CHANCE = 0.60f;

    // =====================================================================================
    // SECTION 4 — WEAPON OUTPUT (damage / clip / range / falloff / reload)
    // The player's side of the TTK equation. dropCoeff is the per-tile damage falloff;
    // reloadTicks is how many turns a reload eats.
    // =====================================================================================

    // Shotgun — high single-shot burst, 1-shell clip. Point-blank role (balance-overhaul order 3, W1):
    // the per-tile falloff is the SECTION 22 table SHOTGUN_FALLOFF_BY_TILE (the drop coefficient is
    // gone), and a close hit knocks back / staggers (SECTION 22). FITTED under R-ROLE-4 (CP3b): 44 -> 66 —
    // the smallest base whose SUSTAINED per-turn damage at 1-2 tiles (accuracy-weighted, the 1-tick reload
    // counted) is >= 2.0x the Assault Rifle's at 3 tiles (25.9 vs 12.8 per turn).
    public static final int   SHOTGUN_DAMAGE             = 66;
    public static final int   SHOTGUN_CLIP_SIZE          = 1;
    public static final int   SHOTGUN_RANGE_TILES        = 5;
    public static final int   SHOTGUN_RELOAD_TIME_TICKS  = 1;

    // Double-Barrel Shotgun — both barrels in ONE action (the built-in BURST_FIRE), 2-shell clip,
    // one tile shorter than the Shotgun (W2: DOUBLE_BARREL_FALLOFF_BY_TILE). FITTED with the Shotgun (CP3b):
    // 32 -> 59 per barrel = 0.9x the re-fitted Shotgun's 66, so the two-barrel action lands ~1.8x (W2).
    public static final int   DBL_SHOTGUN_DAMAGE             = 59;
    public static final int   DBL_SHOTGUN_CLIP_SIZE          = 2;
    public static final int   DBL_SHOTGUN_RANGE_TILES        = 4;
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
    // W6 (balance-overhaul order 3): the old "scarcity-gated over-band" waiver is DELETED (AS8) and the
    // full charge is FITTED under R-ROLE: 90 -> 75 — still a one-slug answer to the PLATED tank (S4,
    // Cinderforge Colossus, 1.40x) and the FLESH soldier at range (S1), but no longer the fastest answer to
    // the SHIELDED / INFERNAL soldiers too (R-ROLE-3: at most two scenarios best).
    public static final int[] RAILGUN_DAMAGE_BY_CHARGE      = {0, 40, 75};
    public static final int   RAILGUN_RANGE_TILES           = 16;
    public static final float RAILGUN_DROP_COEFF            = 0.02f;
    public static final float RAILGUN_DAMAGE_MIN_MULTIPLIER = 0.70f;
    public static final int   RAILGUN_CLIP_SIZE             = 1;
    public static final int   RAILGUN_RELOAD_TIME_TICKS     = 2;

    // Incinerator — short-range cone flamethrower. Impact + STACKING burn DoT (W3; burn numbers in
    // SECTION 22). Impact per target 0.6x the Assault Rifle's base hit (20): was 8 / 5 at the cone edge;
    // 12 / 8 hold under R-ROLE (CP3b): the S5 BURNABLE-group answer in under one turn, and three
    // on-curve chaff (S2) cleared in two sprays at depths 5 and 15 (A6).
    public static final int   FLAME_IMPACT_DAMAGE     = 12;
    public static final int   FLAME_FALLOFF           = 8;
    public static final float FLAME_DAMAGE_DROP_COEFF = 0.0f;
    public static final int   FLAME_RANGE_TILES       = 3;
    public static final int   FLAME_CLIP_SIZE         = 30;
    public static final int   FLAME_RELOAD_TICKS      = 1;

    // Grenade Launcher — bouncing AoE splash. Centre / orthogonal-neighbour / self damage.
    // W7 FITTED under R-ROLE (balance-overhaul order 3, CP3b): centre 42 -> 36, neighbours 22 -> 32 — the
    // blast is a GROUP tool (the S2 FLESH-pack answer, ~1 turn) rather than the single-target hammer that
    // won five of eight scenarios at 42 (R-ROLE-3). Self-damage unchanged.
    public static final int   GRENADE_SPLASH_DAMAGE     = 36;
    public static final int   GRENADE_FALLOFF_DAMAGE    = 32;
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
    // W5 FITTED under R-ROLE (balance-overhaul order 3, CP3b): 28 -> 40 — the ENERGY answer to the
    // SHIELDED (S3, 1.5x) and INFERNAL (S7, 1.35x) soldiers; at 28 ENERGY was never the best answer
    // anywhere (R-ROLE-1). The chain never drops below neutral (W5, EnemyManager).
    public static final int   ARC_CANNON_DAMAGE            = 40;
    public static final int   ARC_CANNON_CLIP_SIZE         = 7;
    public static final int   ARC_CANNON_RANGE_TILES       = 7;
    public static final float ARC_CANNON_DAMAGE_DROP_COEFF = 0.08f;
    public static final int   ARC_CANNON_RELOAD_TIME_TICKS = 2;
    // Lateral chain (uncredited bonus): up to CHAIN_JUMPS leaps beyond the primary target, each
    // dealing CHAIN_DAMAGE_MULTIPLIER^jump of the primary hit (40 -> 24 -> 14 -> 9).
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

    // POWER-LADDER RE-FIT (balance-overhaul order 1): the R8 rebase cut the model floor's DEMAND
    // 720 -> 502 (enemy HP -30%), so LEVER 2 box sizes and LEVER 3 caps were cut ~0.7x with it (bullets
    // 15/54 -> 10/38, shells 5/24 -> 4/17, cells 10/38 -> 7/27, slugs 2/8 -> 1/6, fuel 25/50 -> 18/35,
    // grenades 3/10 -> 2/7; rocket boxes stay at 2, caps 26 -> 18). Box SIZE — not drop frequency — is the
    // lever because a guaranteed box (cache / event / vault) must stay worth the same share of a floor.
    // LEVER 2 — DROP SIZE: ammo box grants (rounds per pickup). RE-SCALED ~2.5x in the economy
    // rescale (idea-A, iteration 2): the model-floor DEMAND rose from 288 to 720 dmg (enemy eHP
    // ~3x), so SUPPLY had to rise proportionally to hold the floor-wide scarcity ratio S in
    // [0.75, 0.95]. With these sizes S = 0.83 floor-wide and < 0.6 per weapon (verified via the
    // harness). The bigger boxes are no longer the awkward 2-4 rounds the old low-eHP economy
    // forced — they fit clip sizes again (the clip-vs-eHP mismatch the old scale created is gone).
    public static final int AMMO_BOX_BULLETS    = 10;
    // Shells 4 -> 3 (balance-overhaul order 3, CP3b): the Shotgun's base rose 44 -> 66, so a 3-shell box is
    // worth what a 4-shell box was (the box-SIZE lever's own rule above); CP4 re-fits generosity.
    public static final int AMMO_BOX_SHELLS     = 3;
    public static final int AMMO_BOX_CELLS      = 7;
    public static final int AMMO_BOX_ROCKETS    = 2;
    public static final int RAILGUN_PICKUP_SLUGS = 1;
    public static final int FLAME_PICKUP_FUEL   = 18;
    public static final int GRENADE_PICKUP_AMMO = 2;

    // LEVER 3 — RESERVE CAP: the hoarding ceiling. RE-FITTED by balance-overhaul order 3 (A-1, CP4) to a
    // PER-TYPE banking target (SECTION 22 AMMO_BANKING_FLOORS_*; R-SUPPLY "reserve banks" defends it):
    //     cap = round(target x model-floor DEMAND (502) / damagePerUnit)    (GameMath.reserveBankingFloors)
    // The generalist's BULLETS bank ~1.0 floor (38 -> 25: the scarce ammo is the reliable gun's), the SPREAD
    // shells and the cells the Incinerator / Plasma / Arc share bank ~1.5 (11 and 27 hold), rockets
    // re-fit to 1.5 after the order-3 Grenade fit (18 -> 21), and slugs stay the tightest bank, ~1.0
    // (6 -> 7, after the Railgun's 90 -> 75). A full Assault Rifle clip (30) now refills from a full
    // reserve in one reload only partially (25) — deliberate: the generalist's ammo is the scarce one.
    public static final int AMMO_RESERVE_CAP_BULLETS = 25;
    public static final int AMMO_RESERVE_CAP_SHELLS  = 11;
    public static final int AMMO_RESERVE_CAP_CELLS   = 27;
    public static final int AMMO_RESERVE_CAP_ROCKETS = 21;
    public static final int RAILGUN_MAX_SLUGS        = 7;
    public static final int FLAME_MAX_FUEL           = 35;
    public static final int GRENADE_MAX_AMMO         = 7;

    // LEVER 1 — DROP FREQUENCY was REPLACED by balance-overhaul order 2: ammo is no longer rolled per
    // room or per kill (ENEMY_AMMO_DROP_CHANCE / MELEE_KILL_AMMO_DROP_CHANCE / LEVEL_GEN_AMMO_CHANCE_PER_ROOM
    // are gone). How MUCH ammo a floor holds is planned from its roster (SECTION 21, R-SUPPLY); the box
    // sizes above still set the grain.

    // LEVER 5 (per-region AMMO_SUPPLY_REGION_MULTIPLIER) — DELETED by balance-overhaul order 2 (CP6):
    // supply is planned from each floor's own roster demand, so it tracks demand at every depth by
    // construction and no region trim is left for it to correct.

    // =====================================================================================
    // SECTION 6 — LOOT / PICKUP SPAWN CHANCES (the drop economy)
    // Global density of props and enemies, plus the room budgets that govern where
    // medkits, armour, ammo and weapons appear.
    // =====================================================================================

    /** Chance any interior floor tile in a non-entrance room receives a prop. Range: 0.05–0.25. */
    public static final float LEVEL_GEN_PROP_CHANCE          = 0.13f;
    /** Hard cap on enemies spawned per room. Range: 1–5. */
    public static final int   LEVEL_GEN_MAX_ENEMIES_PER_ROOM = 3;

    // Credit-chip tier weights (proportional, need not sum to 100): their weighted mean is the value of
    // one planned chip (SupplyPlanner.averageCreditChipValue); the chip COUNT is SECTION 21's.
    public static final int CREDIT_SPAWN_WEIGHT_SMALL   = 70;
    public static final int CREDIT_SPAWN_WEIGHT_MEDIUM  = 24;
    public static final int CREDIT_SPAWN_WEIGHT_LARGE   = 6;

    // LOOT ROOM BUDGETS (the per-room-type pickup and weapon chances) and the per-floor 3-7 credit-chip
    // roll were DELETED by balance-overhaul order 2: every pickup, weapon drop and credit chip on a
    // generated floor is planned by the SupplyPlanner (SECTION 21) — themed rooms are now pure
    // decoration and a cave floor carries the same supply as a rooms floor at its depth.

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
    public static final float XP_CURVE_GROWTH_PER_LEVEL = 1.2495f;

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

    // The LEVEL-UP CARD SYSTEM (idea 5: LEVEL_UP_BUDGET_PP, LEVEL_UP_BUDGET_TOLERANCE, the card pricing
    // references and the ten card magnitudes) was RETIRED by balance-overhaul order 5 (CP2): level-ups
    // offer tagged perks and keystones (SECTION 24). The draw bias survives there as PERK_DRAW_BIAS_PER_PICK.

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

    // --- THE DOT DEFINITIONS (R-DOT, restated by balance-overhaul order 4). Exactly ONE definition per
    // status-side DoT and ONE for the weapon-side burn; BalanceSchema's R-DOT rule fails the build if a
    // second definition appears. STATUS magnitudes (hazard fire, enemy-applied poison, acid) are FRACTIONS
    // of the host's max HP defined in SECTION 23 (BURN_TICK_FRACTION, POISON_TICK_FRACTION) and resolved at
    // application time through GameMath.statusTickDamage, so they never go stale with depth. The
    // WEAPON-applied burn (Incendiary, Incinerator) is R-ROLE-fitted damage and keeps its own base
    // (WEAPON_BURN_BASE_PER_TURN, below). The old flat BURN_DAMAGE_PER_TURN (4) and POISON_DAMAGE_PER_STACK
    // (2) were DELETED by order 4 (override clause).
    /** Minimum BURNING duration (turns) a burn application may roll. */
    public static final int   BURN_DURATION_MIN       = 3;
    /** Maximum BURNING duration (turns) a burn application may roll. */
    public static final int   BURN_DURATION_MAX       = 5;
    /** Hard cap on concurrent POISONED stacks on one host (order 4: 5 -> 6). Stacks decay by 1 per turn. */
    public static final int   POISON_MAX_STACKS       = 6;

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
    public static final int   WEAK_DAMAGE_PERCENT       = 35;
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

    // --- Explosive barrel hazard (moved from EffectConstants; gameplay). The flat EXPLOSION_DAMAGE (12) is
    // GONE (balance-overhaul order 4, H13): blast damage is a fraction of the reference soldier's HP at the
    // floor's depth / of the player's max HP (SECTION 23, HAZARD_BARREL_*).
    /** Hard ceiling on chained barrel detonations from one trigger. */
    public static final int   EXPLOSION_CHAIN_MAX = 32;

    // Rend (BLEED DoT — on hit). STANDARD, BLADE. MOVED 2/.5/6 -> 4/.15/5.35 by balance-overhaul order 5
    // (B6, CP4): 4.8 -> 10.4..13.9 PP, so it is felt at level 1 and a guaranteed count stays in its tier band.
    public static final float REND_DAMAGE_PER_TURN_BASE      = 4f;
    public static final float REND_DAMAGE_PER_TURN_PER_LEVEL = 0.15f;
    public static final float REND_DAMAGE_PER_TURN_CAP       = 5.35f;
    public static final int   REND_DURATION_TURNS            = 4;

    // Incendiary (BURN DoT — on hit). The level-1 base IS the ONE weapon-side burn base (R-DOT): order 4
    // split it from the status burn (now a fraction of max HP) so the R-ROLE-fitted weapon burns keep their
    // damage; the value 4 is byte-identical to the pre-order-4 unified base.
    /** THE weapon-side burn base (damage/turn): Incendiary level 1; every weapon burn derives from it. */
    public static final float WEAPON_BURN_BASE_PER_TURN          = 4f;
    public static final float INCENDIARY_BURN_PER_TURN_BASE      = WEAPON_BURN_BASE_PER_TURN;
    // STANDARD, PYRO. MOVED per-level .5 -> .15 and cap 7 -> 5.35 by balance-overhaul order 5 (B5/B6, CP4):
    // 10.4 PP at level 1 -> 13.9 PP at MAX_WEAPON_LEVEL, inside the single-ability UNCOMMON band at every level.
    public static final float INCENDIARY_BURN_PER_TURN_PER_LEVEL = 0.15f;
    public static final float INCENDIARY_BURN_PER_TURN_CAP       = 5.35f;
    public static final int   INCENDIARY_BURN_DURATION           = 3;
    public static final int   INCENDIARY_INCINERATOR_EXTRA_TURNS = 1;

    // Incinerator weapon burn (the flamethrower's own burn DoT). Per-stack damage and the stack cap
    // live in SECTION 22 (FLAME_BURN_FRACTION, FLAME_BURN_MAX_STACKS — balance-overhaul order 3, W3).
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
    // REFERENCE_AMMO_EFFICIENCY and the WEAPON POWER BANDS (sidearm 8-14 / workhorse 12-18 / burst 18-26 /
    // heavy 24-32) were RETIRED with R-WEAPON by balance-overhaul order 3: weapons are now governed by
    // R-ROLE's reference scenarios (SECTION 22, util/WeaponRoleModel).

    // --- ENEMY THREAT-POINT BANDS — RE-DERIVED from the R8 hit targets (balance-overhaul order 1). The
    // old hand-set bands (CHAFF 16-34 | SOLDIER 36-66 | BRUISER 70-120 | MINI_ELITE 160-310) priced the
    // economy-rescale roster; the rebase moved every archetype, so the band is now COMPUTED per role in
    // BalanceSchema from the centre of the role's R8 box: centre eHP = mean hits-to-kill x the reference
    // hit, centre hit = REFERENCE_PLAYER_EHP / mean hits-to-die (an open-ended TTD band uses
    // LADDER_TTD_OPEN_BAND_CENTRE_FACTOR x its floor), TP at melee positional and cadence 1, and the band
    // spans [LOW, HIGH] x that centre — wide enough for the ranged/status positional multipliers
    // (up to 1.55) and the slow (cadence-2) archetypes. Printed by BalanceReport's ENEMIES table.
    /** Lower edge of a role's TP band as a multiple of its R8-centre TP (covers cadence-2 archetypes). */
    public static final float LADDER_TP_BAND_LOW_FACTOR  = 0.5f;
    /** Upper edge of a role's TP band as a multiple of its R8-centre TP (covers ranged + status positional). */
    public static final float LADDER_TP_BAND_HIGH_FACTOR = 1.6f;
    /** Centre of an OPEN-ended hits-to-die band (CHAFF: ">= 10 hits") as a multiple of its floor. */
    public static final float LADDER_TTD_OPEN_BAND_CENTRE_FACTOR = 1.5f;

    // --- POSITIONAL MULTIPLIERS for the Threat-Point formula (designer classification).
    public static final float POSITIONAL_MULT_MELEE       = 1.00f;
    public static final float POSITIONAL_MULT_FAST_MELEE  = 1.15f;
    public static final float POSITIONAL_MULT_RANGED      = 1.30f;
    /** Added on top of the base positional multiplier when the enemy applies a DOT/stun/slow. */
    public static final float POSITIONAL_MULT_STATUS_BONUS = 0.25f;

    // --- RETIRED by balance-overhaul order 1 (override record in docs/game-balance-authority.txt):
    //   GOLDEN_RATIO_* bands        -> the R8 hit bands (LADDER_TTK_HITS_* / LADDER_TTD_HITS_*, SECTION 20)
    //   DEPTH_COUPLING_RATIO_MIN/MAX -> R-LADDER L1 (LADDER_ON_CURVE_TOLERANCE)
    //   GEAR_CURVE_PER_REGION (1.35) -> the per-floor weapon ladder (LADDER_GROWTH + the level gap)

    /**
     * Depths per region — the SAME 5-floor band the route map uses ({@link RouteMapConstants#REGION_BAND_SIZE}),
     * referenced here so every per-region table (drop tiers, supply/heal multipliers, the region danger
     * dial) and the descent's region boundaries can never drift apart. A region ends on a boss floor.
     * (Named for the retired order-2 gear curve; kept because every per-region table indexes by it.)
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

    // --- PER-REGION HEAL SUPPLY MULTIPLIER — DELETED by balance-overhaul order 2 (CP6): heals are
    // planned per floor from its own incoming damage (SECTION 21, S3/S4); nothing reads a region nudge.

    // --- NEVER-SOFTLOCK (order 3, part D): the emergency ammo lifeline. When the player's TOTAL
    // remaining potential damage (all reserves * efficiency + melee) falls below the remaining floor
    // demand times this fraction, the next eligible enemy drop is forced to be ammo for an equipped
    // weapon (capped once per floor — GameMath.emergencySupplyTriggers). Sits FAR below the scarcity
    // band, so a hoarder never trips it; it only converts "standing empty-handed" deaths into
    // fighting-retreat deaths. Melee (fist, no ammo) is the true backstop. Range: 0.15–0.35.
    public static final float EMERGENCY_SUPPLY_FRACTION = 0.25f;

    // --- CREDIT ECONOMY BAND: R-CREDITS v2 (balance-overhaul order 6, F12) lives in SECTION 25 — it REPLACED the
    // order-3 band [0.9, 1.4] on "one weapon-class buy + 1.5 supplies per region" (CREDIT_INCOME_RATIO_* and
    // SHOP_EXPECTED_*_BUYS_PER_REGION deleted).
    /** Target credit banking horizon (floors) — a full purse should bank about one region. Range: 4–7. */
    public static final float CREDIT_BANK_TARGET_FLOORS = 5f;
    /** PP value of one "significant" (weapon-class) purchase — the route model's upgrade-opportunity unit. */
    public static final float SHOP_SIGNIFICANT_BUY_POWER_POINTS = 12f;
    /** PP value of a representative small (supply) purchase — ammo box / stim; the NEEDED BUNDLE's consumable (F12). */
    public static final float SHOP_SMALL_BUY_POWER_POINTS        = 3f;

    // --- HEAL PRICING BANDS (Balance Authority R-HEAL) — RE-STATED by balance-overhaul order 1 (the
    // OVERRIDE CLAUSE: "flat heal values and R-HEAL's flat survival-turns pricing -> fractional heals").
    // Every heal/armour pickup restores a FRACTION of the max it refills (SECTION 20, R10), so it is
    // priced by that fraction: a small pickup ('+' stim, 'a' shard) tops up a slice, a large one ('H'
    // medkit, 'A' vest) buys most of a fight but never a full reset. Survival turns bought on the model
    // floor stay printed by BalanceReport as information (they fell with the R8 rebase's harder hits).
    public static final float HEAL_SMALL_MAX_FRACTION_MIN = 0.10f;
    public static final float HEAL_SMALL_MAX_FRACTION_MAX = 0.25f;
    public static final float HEAL_LARGE_MAX_FRACTION_MIN = 0.40f;
    public static final float HEAL_LARGE_MAX_FRACTION_MAX = 0.70f;

    // --- THE MODEL FLOOR (depth 1) — the worked reference encounter from idea 3.
    // Enemy composition (DEMAND = sum of these enemies' eHP). At depth 1 eHP == raw HP. After the
    // economy rescale (idea-A, iteration 2) the enemy eHP is ~3x higher, so:
    // DEMAND = 6*40 + 3*40 + 2*120 + 1*120 = 720 damage; total TP ~= 432 (a ~500-TP budget).
    // The SECTION 5 ammo levers and the heal inputs below are tuned against THIS rescaled floor.
    public static final int MODEL_FLOOR_GORE_BITER_COUNT  = 6;
    public static final int MODEL_FLOOR_EYE_TYRANT_COUNT  = 3;
    public static final int MODEL_FLOOR_SHELL_BRUTE_COUNT = 2;
    public static final int MODEL_FLOOR_PLAGUE_HULK_COUNT = 1;

    // Heal-economy model inputs for the model floor.
    /**
     * Expected medkits found on the model floor (priced as a stim '+' / full 'H' mix). Balance-overhaul
     * order 1 re-synced it to the generator's default per-room chance (MODEL_FLOOR_ROOM_COUNT 8 x
     * LevelGenConfig.medkitChancePerRoom 0.35 = 2.8; was a conservative 1.5) — the R8 rebase made every
     * fight costlier and the old undercount priced a heal economy harsher than the game actually places.
     */
    public static final float MODEL_FLOOR_EXPECTED_MEDKITS        = 2.8f;
    /** Expected armour pickups on the model floor (shard 'a' / vest 'A' mix): 8 rooms x armourChancePerRoom 0.20 = 1.6 (was 1.0). */
    public static final float MODEL_FLOOR_EXPECTED_ARMOUR_PICKUPS = 1.6f;
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

    // ENCOUNTER COMPOSITION — REPLACED by balance-overhaul order 2 (override clause). The anchor reserve
    // band + ceiling (ENCOUNTER_ANCHOR_BUDGET_*), the elite-gauntlet roll (ENCOUNTER_ELITE_ANCHOR_*), the
    // per-room cap (ENCOUNTER_PER_ROOM_TP_FRACTION_CAP 0.25) and its room-geometry multipliers
    // (ROOM_OPEN / ROOM_CHOKEPOINT), consecutive-run chaff packs (CHAFF_PACK_MIN / MAX) and the remainder
    // pass are all GONE: a floor now fills a growing BODY TARGET with registered GROUP TEMPLATES (SECTION
    // 21: BODY_TARGET_*, GROUP_*), the anchor is a template (ESCORT / WARBAND), the per-group cap is
    // GROUP_TP_FRACTION_CAP 0.35, and chaff never spawns alone because every chaff slot fields at least
    // GROUP_CHAFF_SLOT_MIN of one archetype. Only the fill target survives: the planner aims each pick at
    // the threat-per-body still to spend so the roster ends near this fraction of the cap.
    /**
     * The roster aims to spend this fraction of the floor's Threat-Point CAP by the time it reaches its
     * body target (balance-overhaul order 2) — leaves a little headroom so a roster never overshoots. XP
     * pacing (R-XP-PACE) reads the same fraction. Range: 0.85–1.0.
     */
    public static final float ENCOUNTER_BUDGET_FILL_TARGET_FRACTION = 0.95f;

    // 3. THE REGION DANGER DIAL (fixes knowledge-doc problem 12) — route regions stop being frequency-only.
    //    Each region declares a Threat-Point budget multiplier applied ON TOP of the depth curve
    //    (GameMath.regionScaledFloorThreatPointBudget), indexed 0-based by region
    //    (floor((depth-1)/GEAR_CURVE_REGION_BAND_SIZE)); one entry per region of the finite run. Node WEIGHT multipliers (RouteMapConstants
    //    REGION_*_MULTIPLIER_*) stay for FLAVOUR — what kind of rooms; this dial owns HOW HARD. A lethal
    //    region is now EXPLICITLY lethal and budgeted, and the depth-coupling audit reads it per lane so it
    //    stays provably fair (region danger is extra bodies, not an unfair per-duel spike).
    //    BALANCE-OVERHAUL ORDER 6 (F7, OVERRIDES {0.90, 1.00, 1.15, 1.25} + "monotonic non-decreasing"): the five
    //    regions of the finite run, with the PEAK the owner asked for at floors 10-15 — A OUTER FACILITY 0.85 |
    //    B RESEARCH WING 1.00 | C REACTOR DEPTHS 1.25 | D THE BREACH 1.20 (dips as the build comes online) |
    //    E THE CORE 1.30 (the finale). Range per entry: 0.8–1.5; shape C >= B >= A and E >= D (R-REGION).
    public static final float[] REGION_TP_BUDGET_MULTIPLIER = {0.85f, 1.00f, 1.25f, 1.20f, 1.30f};
    /** R-REGION lower bound: no region's TP dial may fall below this (a region never trivialises a floor). */
    public static final float   REGION_TP_BUDGET_MULTIPLIER_MIN = 0.8f;
    /** R-REGION upper bound: no region's TP dial may exceed this (region danger stays an attrition tax, not a wall). */
    public static final float   REGION_TP_BUDGET_MULTIPLIER_MAX = 1.5f;
    /** R-REGION: minimum margin by which the LETHAL regions (C/D/E) must out-dial region A (measurably harder). */
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
     * the player can escape — the counterplay. Damage/turn = BURN_TICK_FRACTION of the host's max HP (SECTION 23).
     */
    public static final int   HAZARD_FIRE_BURN_TURNS       = 2;

    /**
     * Multiplier applied to the BURN_TICK_FRACTION damage when the host standing in a fire
     * tile is an enemy rather than the player. Fire still hurts the player at the base rate (the
     * misposition risk stays real), but enemies caught in the flamethrower's spreading fire burn
     * faster — rewarding the player for herding a swarm into the flame instead of just tagging
     * them directly. Range: 1.0 (symmetric) – 3.0. (CP6, H13: 1.5 -> 2.5 so a fire is a real weapon against a pack.)
     */
    public static final float HAZARD_FIRE_ENEMY_DAMAGE_MULTIPLIER = 2.5f;

    // Toxic pools apply POISON_STACKS_PER_TOXIC_TURN stacks (SECTION 23) per turn stood; stacks decay by 1 a
    // turn, so a pool is area denial (standing in it escalates to POISON_MAX_STACKS) with no separate duration.

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
    //   BOSS_EXPECTED_OFFENCE_BUDGET_FRACTION * EXPECTED_PERK_POWER_PP_PER_PICK * (BOSS_EXPECTED_LEVELS_PER_DEPTH * depth).
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
     * player) is the tightest gate — the shallowest case is the real limiter.
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

    // Per-boss TARGET FIGHT TURNS and boss DEPTHS moved to the BOSS TABLE, SECTION 25 (balance-overhaul order 6).

    // VERB DAMAGE TABLES — each boss verb's single-hit damage as a FRACTION of that boss's derived DPT
    // (order 6, TECHNICAL NOTES). A telegraphed signature hit is a multiple of the sustained per-turn
    // average; an un-telegraphed poke is a small fraction (it must stay under 25% eHP, RULE 3). All are
    // audited against the 35%/25% single-hit caps by R-BOSS-VERB-CAP at the derived values.

    // The Overseer (depths 5 / 20, BOSS TABLE) — security core robot; laser lanes + melee charge.
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

    // The Corruptor (depths 10 / 25, BOSS TABLE) — mutated scientist; summoner + acid burst.
    /** ACID (telegraphed burst) as a fraction of boss DPT. Range: 1.0–1.8. */
    public static final float CORRUPTOR_ACID_DPT_FRACTION   = 1.40f;
    public static final int   CORRUPTOR_SUMMON_COOLDOWN     = 3;
    public static final int   CORRUPTOR_MINION_CAP          = 5;
    public static final int   CORRUPTOR_ACID_POOL_DURATION  = 3;

    // Hell Baron (depth 15, BOSS TABLE) — armored greater demon; firewall + enrage.
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
    // every rollable weapon ability. GameBalance keeps re-export shims.
    //
    // RE-TUNED by balance-overhaul order 5 (B5/B6, CP4). Fourteen sub-threshold abilities were CUT or
    // MERGED (ABILITY_MIN_FELT_PP, SECTION 24) and the fifteen survivors re-tuned so every one is FELT at
    // its expected weapon level and the B5 counts land inside the B5 tier bands at EVERY level:
    //   STANDARD abilities  price ~10.5 PP at level 1 -> ~14 PP at MAX_WEAPON_LEVEL (cap = base + 9 x per-level,
    //                       reached at the top of the respanned 1..10 legacy curve);
    //   MAJOR abilities     price ~12 PP -> ~17.4 PP (rolled on EPIC and LEGENDARY only).
    // Any 1 standard fits UNCOMMON 12 +/-20%; any 2 fit RARE 25; any 2 + a MAJOR fit EPIC 40; any 2 + a MAJOR +
    // the signature (18 PP) fit LEGENDARY 60 — so a guaranteed count can never be forced out of its band. Each
    // MOVED value carries the PP it prices to (GameMath.abilityPowerPoints). REND and INCENDIARY live with the
    // DoT rules in SECTION 8 (re-tuned there, same shape).
    // =====================================================================================

    // ── Critical Strike (STANDARD, MARKSMAN) — MOVED .05/.015/.30 -> .105/.0039/.14 (10.5 -> 14.0 PP) ──
    public static final float CRIT_CHANCE_BASE            = 0.105f;
    public static final float CRIT_CHANCE_PER_LEVEL       = 0.0039f;
    public static final float CRIT_CHANCE_CAP             = 0.14f;

    // ── Armor Pierce (STANDARD, DEMOLITION) — bypasses a fraction of the target's Block AND (merged from the
    // cut OVERPENETRATION) every shot pierces ARMOR_PIERCE_EXTRA_TARGETS more enemies in line.
    // MOVED .20/.05/.60 -> .285/.013/.40 (8.55 + 2 = 10.55 -> 12 + 2 = 14.0 PP) ──
    public static final float ARMOR_PIERCE_BASE           = 0.285f;
    public static final float ARMOR_PIERCE_PER_LEVEL      = 0.013f;
    public static final float ARMOR_PIERCE_CAP            = 0.40f;
    /** Extra enemies a shot pierces beyond the first (merged from OVERPENETRATION's level-1 count). Range: 1. */
    public static final int   ARMOR_PIERCE_EXTRA_TARGETS              = 1;
    /** On an already-piercing weapon (Plasma, Railgun): damage bonus per enemy struck past the first. */
    public static final float ARMOR_PIERCE_ALREADY_PIERCING_BONUS     = 0.25f;

    // ── Executioner (MAJOR, BLADE) — bonus vs low-HP targets. MOVED .30/.06/.80 -> .40/.02/.58 (12 -> 17.4 PP) ──
    public static final float EXECUTIONER_THRESHOLD       = 0.25f;
    public static final float EXECUTIONER_BONUS_BASE      = 0.40f;
    public static final float EXECUTIONER_BONUS_PER_LEVEL = 0.02f;
    public static final float EXECUTIONER_BONUS_CAP       = 0.58f;

    // ── Stagger Rounds (STANDARD, CLOSE_QUARTERS) — stun on hit. MOVED .08/.02/.35 -> .235/.0083/.31 (10.6 -> 13.95 PP) ──
    public static final float STAGGER_CHANCE_BASE         = 0.235f;
    public static final float STAGGER_CHANCE_PER_LEVEL    = 0.0083f;
    public static final float STAGGER_CHANCE_CAP          = 0.31f;

    // ── Lifesteal (STANDARD, VITALITY). MOVED .06/.015/.20 -> .21/.0078/.28 (10.5 -> 14.0 PP) ──
    public static final float LIFESTEAL_BASE              = 0.21f;
    public static final float LIFESTEAL_PER_LEVEL         = 0.0078f;
    public static final float LIFESTEAL_CAP               = 0.28f;

    // ── Kinetic Slam (STANDARD, BULWARK, melee). MOVED .20/.04/.60 -> .235/.0083/.31 (10.6 -> 13.95 PP) ──
    public static final float KINETIC_SLAM_CHANCE_BASE       = 0.235f;
    public static final float KINETIC_SLAM_CHANCE_PER_LEVEL  = 0.0083f;
    public static final float KINETIC_SLAM_CHANCE_CAP        = 0.31f;
    public static final int   KINETIC_SLAM_WALL_BONUS_DAMAGE = 3;

    // ── Cleave (MAJOR, BLADE, melee). MOVED .40/.05/.80 -> .40/.02/.58 (12 -> 17.4 PP) ──
    public static final float CLEAVE_FRACTION_BASE      = 0.40f;
    public static final float CLEAVE_FRACTION_PER_LEVEL = 0.02f;
    public static final float CLEAVE_FRACTION_CAP       = 0.58f;

    // ── Berserker's Oath (legendary, melee) ──
    public static final float BERSERKER_DAMAGE_PER_STACK  = 0.10f;
    public static final int   BERSERKER_HP_TICK_PER_STACK = 1;
    public static final int   BERSERKER_MAX_STACKS        = 5;

    // ── Scavenger Rounds (STANDARD, SCAVENGER, gun) — on kill, a chance to refund SCAVENGER_REFUND_ROUNDS
    // rounds to the reserve. MOVED chance .15/.03/.50 -> .30/.0112/.40 and refund 1 (2 at Lv.7+) -> 3 flat
    // (0.30 x 3 x 11.67 = 10.5 -> 0.40 x 3 x 11.67 = 14.0 PP). The old Lv.7 refund step is gone: the refund
    // is the ability's countValue now, so the price reads it. ──
    public static final float SCAVENGER_CHANCE_BASE          = 0.30f;
    public static final float SCAVENGER_CHANCE_PER_LEVEL     = 0.0112f;
    public static final float SCAVENGER_CHANCE_CAP           = 0.40f;
    /** Rounds refunded to the reserve on a Scavenger proc (the instance's countValue). Range: 2-4. */
    public static final int   SCAVENGER_REFUND_ROUNDS        = 3;

    // ── Point Blank (STANDARD, CLOSE_QUARTERS). MOVED .20/.05/.70 -> .35/.0125/.46 (10.5 -> 13.8 PP) ──
    public static final float POINT_BLANK_BONUS_BASE      = 0.35f;
    public static final float POINT_BLANK_BONUS_PER_LEVEL = 0.0125f;
    public static final float POINT_BLANK_BONUS_CAP       = 0.46f;
    public static final int   POINT_BLANK_MAX_DISTANCE    = 1;

    // ── Opening Salvo (MAJOR, MARKSMAN). MOVED .30/.07/.90 -> .40/.02/.58 (12 -> 17.4 PP) ──
    public static final float OPENING_SALVO_BONUS_BASE      = 0.40f;
    public static final float OPENING_SALVO_BONUS_PER_LEVEL = 0.02f;
    public static final float OPENING_SALVO_BONUS_CAP       = 0.58f;

    // ── Rhythm / Heat-Up (STANDARD, ENERGY). MOVED .06/.01/.15 -> .105/.0039/.14 (10.5 -> 14.0 PP) ──
    public static final float RHYTHM_RAMP_PER_HIT_BASE      = 0.105f;
    public static final float RHYTHM_RAMP_PER_HIT_PER_LEVEL = 0.0039f;
    public static final float RHYTHM_RAMP_PER_HIT_CAP       = 0.14f;
    public static final int   RHYTHM_MAX_STACKS             = 5;

    // ── Static Discharge (STANDARD, ENERGY). MOVED 4/1/14 -> 7.5/0.28/10 (10.5 -> 14.0 PP) ──
    public static final float STATIC_SPLASH_BASE      = 7.5f;
    public static final float STATIC_SPLASH_PER_LEVEL = 0.28f;
    public static final int   STATIC_SPLASH_CAP       = 10;

    // ── Burst Fire (MAJOR, CLOSE_QUARTERS, gun) — rounds fired per attack action. MOVED from a count that
    // stepped 2 -> 5 with level (4 -> 10 PP at 2 PP per count) to a FIXED two-round burst priced on its own
    // weight (ABILITY_PP_PER_EXTRA_BURST_ROUND = 16 PP for the one extra round). The Double-Barrel's
    // built-in burst reads the same count. ──
    /** Rounds fired per attack action by BURST_FIRE (the base shot plus the extras). Range: 2. */
    public static final int   BURST_FIRE_ROUND_COUNT = 2;

    // ── Legendary signatures ──
    public static final int   SOULFORGE_KILLS_PER_LEVEL_UP  = 5;
    public static final int   JUDGMENT_COOLDOWN_FIRES       = 5;
    public static final int   JUDGMENT_LANCE_RANGE          = 20;
    public static final float JUDGMENT_DAMAGE_MULTIPLIER    = 3.0f;
    public static final int   HELLFIRE_NOVA_RADIUS          = 2;
    public static final float HELLFIRE_NOVA_DAMAGE_FRACTION = 0.75f;

    // CUT by balance-overhaul order 5 (B6, priced below ABILITY_MIN_FELT_PP at level 1, PP in brackets):
    // HEMORRHAGE_HARVEST (0.5), VAMPIRIC_CRIT (0.7), BULWARK_ROUNDS (0.3), ADRENAL_SURGE (3.0),
    // SECOND_WIND (7.5; also collided with the VITALITY perk of the same name), SALVAGE_STRIKE / SCHOLARS_EDGE /
    // FIELD_MEDIC_ROUNDS / CREDIT_FANG / QUICK_HANDS (3.0 nominal each), EXTENDED_MAG (2.0), MARKSMANS_PATIENCE
    // (1.5), RESONANT_ROUNDS (1.2). MERGED: OVERPENETRATION (2.0) -> ARMOR_PIERCE (its pierce rides
    // ARMOR_PIERCE_EXTRA_TARGETS). Their magnitudes are deleted with them.

    // -------------------------------------------------------------------------------------
    // TIER = PRICED ABILITY BUDGET (new-game-balancr order 2). Rarity NEVER raises a weapon's
    // POWER band (that stays a role property, SECTION 9) — it buys ABILITIES, priced in power points
    // (PP = %-of-reference-DPT/eHP; see GameMath.abilityPowerPoints). The tier budgets themselves —
    // the order-2 UNCOMMON 6 / RARE 12 / EPIC 20 / LEGENDARY 30 ceilings — were REPLACED by balance-
    // overhaul order 5 (B5, OVERRIDE CLAUSE) with GUARANTEED COUNTS and a BAND (floor AND ceiling):
    // TIER_ABILITY_COUNT_* / TIER_ABILITY_PP_BUDGET_* / TIER_ABILITY_PP_TOLERANCE live in SECTION 24.
    // -------------------------------------------------------------------------------------

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
    /**
     * Damage-over-time (rend, incendiary) — refreshes not stacks, and overkills dying targets. MOVED .60 -> .65
     * (balance-overhaul order 5): the Incendiary level-1 base is pinned to WEAPON_BURN_BASE_PER_TURN (R-DOT), and
     * at .60 it priced to exactly the UNCOMMON band floor (9.6 PP); .65 prices it at 10.4, clear of the edge.
     */
    public static final float ABILITY_UTIL_DAMAGE_OVER_TIME = 0.65f;
    /** Close-range / long-range / first-shot / low-HP conditional damage bonuses. */
    public static final float ABILITY_UTIL_CONDITIONAL      = 0.30f;
    /** Lifesteal-style sustain expressed as a fraction of damage dealt. */
    public static final float ABILITY_UTIL_SUSTAIN_FRACTION = 0.50f;
    /** Flat on-kill AoE burst (static discharge) — small, situational. */
    public static final float ABILITY_UTIL_FLAT_EVENT       = 0.35f;
    /** PP per extra pierced enemy (armor pierce's merged over-penetration). */
    public static final float ABILITY_PP_PER_COUNT          = 2.0f;
    /**
     * PP for each round BURST_FIRE adds to an attack action (balance-overhaul order 5; was ABILITY_PP_PER_COUNT
     * = 2, which priced a doubled attack below a crit chance). An extra round doubles a turn's damage but
     * spends the reserve twice as fast, so a floor's damage SUPPLY is unchanged — the gain is TEMPO (fewer
     * enemy turns per kill), valued as a major conditional bonus. Range: 12-18.
     */
    public static final float ABILITY_PP_PER_EXTRA_BURST_ROUND = 16.0f;
    /**
     * PP per round of ammo SCAVENGER_ROUNDS is expected to refund per kill (chance x rounds), balance-
     * overhaul order 5; replaces the flat 3 PP economy nominal. One refunded round per kill is roughly a
     * quarter of a gun's per-kill ammo cost. Range: 9-14.
     */
    public static final float ABILITY_PP_PER_EXPECTED_REFUND_ROUND = 11.67f;
    /**
     * Flat marquee PP for a legendary SIGNATURE ability. MOVED 10 -> 18 (balance-overhaul order 5): a
     * signature is the run-defining rule of a LEGENDARY (a 3x lance every 5 fires, a crit nova, a weapon
     * that levels itself), worth more than any MAJOR; 18 is also what puts signature + 3 standards inside
     * the LEGENDARY 60 PP band at level 1. Range: 16-24.
     */
    public static final float ABILITY_PP_LEGENDARY_SIGNATURE = 18.0f;

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
    // THE PITY RULE (GUARANTEED_UPGRADE_PER_REGION) — DELETED by balance-overhaul order 2 (CP6): replaced by
    // the two-floor weapon CADENCE (S9, RunStats.weaponCadenceDue -> LevelGenConfig.weaponCadenceDue).

    // =====================================================================================
    // SECTION 16 — SHOP ECONOMY (UAC Fabricator)
    // CONSOLIDATED from GameBalance (Balance Authority, order 1). Stock size, category
    // weights, base prices, and the depth/rarity price scaling. The credit economy is still
    // flagged provisional (the sink is unproven in play — knowledge doc SECTION 12), but its
    // numbers now live in the single source of truth. Machine PLACEMENT geometry
    // (SHOP_TWO_MACHINE_MIN_SPACING) stays in GameBalance — layout, not economy.
    // =====================================================================================

    // Machine counts per floor moved to SECTION 25 (F13: exactly SHOP_MACHINES_PER_FLOOR, SHOP_NODE_MACHINE_COUNT on
    // a SHOP node) — they REPLACED SHOP_MIN_PER_FLOOR 1 / SHOP_MAX_PER_FLOOR 2 / SHOP_SECOND_MACHINE_CHANCE 0.40.

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
    // RE-FIT (balance-overhaul order 6, F12 — owner D4 "shops everywhere are too expensive"): 0.10 -> 0.0 and
    // 36 -> 15.5 so R-CREDITS v2 holds in every region. Modelled income per floor climbs ~5% a floor while the
    // NEEDED BUNDLE's value climbs by region tier, so a depth price ramp on top made the deep regions
    // unaffordable; the flattest ratio across A..E is no ramp at all (modelled A 1.37, B 1.33, C 1.26, D 1.15,
    // E 1.33 against [1.10, 1.40]). The depth factor stays in the formula — 0.0 is a tuning, not a deletion.
    public static final float SHOP_DEPTH_PRICE_SCALE       = 0.0f;
    /** Credits charged per power point of an offer's priced value — the single price-level knob. Range: 12–48. */
    public static final float SHOP_CREDITS_PER_POWER_POINT = 15.5f;
    /** Damage supplied per power point when pricing an ammo box (box damage / this = its PP value). Range: 60–120. */
    public static final float SHOP_AMMO_DAMAGE_PER_POWER_POINT = 90f;
    /** HP restored per power point when pricing a medkit (heal amount / this = its PP value). Range: 8–16. */
    public static final float SHOP_HEAL_HP_PER_POWER_POINT     = 11f;
    // A weapon TIER-UPGRADE prices on the ABILITY-PP budget its destination tier unlocks
    // (TIER_ABILITY_PP_BUDGET_*, SECTION 15) — the marquee value is the ability slot the tier buys.
    // A PLAYER-ABILITY offer sells ONE PERK RANK priced on SHOP_PERK_RANK_POWER_POINTS (SECTION 24;
    // balance-overhaul order 5 replaced the budget-equal boon card): a paid rank of the same perks the
    // level-up screen offers. Both are read directly from those sections in DefaultShopOfferSource —
    // no separate price constants here.
    /** Ammo "large box" multiplier over the standard box size. */
    public static final int   SHOP_AMMO_LARGE_BOX_MULTIPLIER = 2;

    // =====================================================================================
    // SECTION 18 — SIMULATION PROOF (new-game-balancr order 9) — the BEHAVIOURAL bands
    // Orders 1-8 make the numbers self-consistent; a green audit still cannot prove the game
    // PLAYS the way the model claims (knowledge-doc problem 1: "verified by harness only —
    // NEVER playtested"). Because the game is strictly turn-based, a whole run is SIMULABLE:
    // sim/BalanceSimulator plays real runs through the real systems with scripted policies,
    // and the bands below are what those played runs must satisfy. They are TUNABLE TARGETS
    // like every other value here — moving one is a design decision, recorded in the authority
    // doc's CHANGELOG. Enforced by BalanceSchema (RuleKind.SIM_*) via `./gradlew balanceSim`.
    // =====================================================================================

    // --- A. HARNESS PARAMETERS (how much play is simulated) ------------------------------
    /**
     * Seeds per policy in the full matrix. 200 is the headline sample: it makes "0% clear the
     * first boss" (S-GATE) a claim with teeth while keeping the matrix inside the CI budget.
     */
    public static final int   SIM_SEED_COUNT              = 200;
    /**
     * Depth the simulator stops at: the whole finite run (balance-overhaul order 6 — was 20 under the
     * endless descent). A run that clears the depth-RUN_FINAL_DEPTH boss ends as VICTORY, never deeper.
     */
    public static final int   SIM_DEPTH_CEILING           = BalanceConfig.RUN_FINAL_DEPTH;
    /** First floor of a simulated run (the staging room is skipped — see SimWorld). */
    public static final int   SIM_STARTING_DEPTH          = 1;
    /**
     * Turns a policy may spend on one floor before the run is declared STALLED. Generous: a big
     * cavern floor plus a boss fight is a few hundred turns; anything past this is a policy that
     * cannot find the exit, which the S-SOFTLOCK band wants to see rather than hide.
     */
    public static final int   SIM_TURNS_PER_FLOOR_CAP     = 900;
    /**
     * Fixed time step fed to the real controller/door/weapon update() calls. Far larger than any
     * animation duration, so exactly one action resolves per simulated turn (the game is
     * turn-based; wall-clock time carries no gameplay meaning).
     */
    public static final float SIM_TIME_STEP_SECONDS       = 1.0f;
    /** Safety net on the per-action drain loop — never a rule, just a guard against a stuck state. */
    public static final int   SIM_MAX_STEPS_PER_ACTION    = 8;

    // --- B. POLICY THRESHOLDS (how the scripted players behave) --------------------------
    /**
     * How far away an enemy still counts as "the fight I am in" rather than scenery. Wider than any
     * weapon's range so a policy commits to a fight it can already be shot in, not just one it can
     * shoot into.
     */
    public static final int   SIM_ENGAGE_RANGE_TILES      = 12;
    /** Vitality fraction at or below which a policy heals. The "heals at <30% HP" NAIVE rule. */
    public static final float SIM_NAIVE_HEAL_FRACTION     = 0.30f;
    /** The TACTICAL policy heals earlier — it does not wait to be one hit from death. */
    public static final float SIM_TACTICAL_HEAL_FRACTION  = 0.55f;
    /**
     * How close a sleeping enemy has to be before the TACTICAL policy bothers to pick the fight. It
     * still fights anything ALREADY awake — this only stops it crossing a floor to wake something.
     */
    public static final int   SIM_TACTICAL_ENGAGE_RANGE_TILES = 6;
    /** How far a policy will detour from its route to collect a pickup. Beyond this it walks on. */
    public static final int   SIM_SUPPLY_DETOUR_RANGE_TILES = 10;
    /** Consecutive refusals of the SAME step before the simulator writes that tile off as impassable. */
    public static final int   SIM_REFUSALS_BEFORE_TILE_WRITTEN_OFF = 3;
    /**
     * Turns a policy keeps walking toward a goal it is not getting closer to before writing that goal
     * off for the floor. Commitment is what stops a scripted player oscillating between two
     * destinations; patience is what stops it committing to something it can never reach.
     */
    public static final int   SIM_GOAL_PATIENCE_TURNS = 25;
    /**
     * LOOP BREAKER (balance-overhaul order 7, P8). A scripted player that has bounced among at most
     * SIM_LOOP_MAX_DISTINCT_TILES tiles for SIM_LOOP_WINDOW_TURNS turns without firing, reloading or healing is
     * caught between two of its own rules (a dodge undone by the walk plan, a heavy that re-commits every
     * time it steps back into the lane). It then PUSHES for SIM_LOOP_PUSH_TURNS turns: no sidestepping, the
     * walk plan routes straight through enemies and fights whatever stands in the way — the player who notices
     * they are going in circles and does something different. Navigation competence, never a game rule.
     */
    public static final int   SIM_LOOP_WINDOW_TURNS        = 16;
    public static final int   SIM_LOOP_MAX_DISTINCT_TILES  = 4;
    public static final int   SIM_LOOP_PUSH_TURNS          = 12;
    /**
     * P8 / A1 (balance-overhaul order 7): the largest share of PLAYED floors any standard policy may leave
     * stalled (the floor's turn cap ran out before the exit). The navigation waiver this replaces existed because
     * the share was ~85% of runs; the depth-derived bands only measure the game once it is below this.
     */
    public static final float SIM_MAX_STALLED_FLOOR_FRACTION = 0.10f;
    // --- ROUND SUMMARY (balance-overhaul order 7, P3): the target metrics a playtest round reads ----------
    // The sim half of each P3 metric, printed with an IN/OUT marker on the ROUND SUMMARY page every balanceSim
    // run. Where a P3 band already exists as an S-band constant it is reused (S-HEAVY, S-BUILD, F8, S-SHOP,
    // S-ECONOMY); the rest live here. A band moves only under P5 (>= 3 owner runs consistently contradicting it).
    /** P2: minimum owner runs per playtest round (a documentary number — the rounds are run by hand). */
    public static final int   ROUND_MIN_RUNS                     = 5;
    /** T2: SOLDIER hits-to-kill at depths 3-5, start kit / on-curve kit, at least this (the ladder punishes lag). */
    public static final float ROUND_T2_LAG_HITS_RATIO_MIN        = 1.5f;
    /** T3: "floors 1-3 are full" reads COMBAT + ELITE floors at depth <= this. */
    public static final int   ROUND_T3_EARLY_DEPTH_MAX           = 3;
    /** T3: mean enemies a floor 1-3 spawns, at least this. */
    public static final float ROUND_T3_MIN_ENEMIES               = 12f;
    /** T3: median turns to the first damage exchange on floors 1-3, at most this. */
    public static final float ROUND_T3_MAX_TURNS_TO_FIRST_EXCHANGE = 15f;
    /** T3: mean encounter groups a floor 1-3 fields, at least this. */
    public static final float ROUND_T3_MIN_GROUPS                = 2f;
    /** T4: TACTICAL median COMBAT-floor exit health fraction band. */
    public static final float ROUND_T4_EXIT_HEALTH_MIN           = 0.55f;
    public static final float ROUND_T4_EXIT_HEALTH_MAX           = 0.80f;
    /** T4: a "starved" death holds no heal and less than this fraction of its carried ammo caps. */
    public static final float ROUND_T4_STARVED_AMMO_FRACTION     = 0.20f;
    /** T4: at most this share of TACTICAL deaths may be starved deaths. */
    public static final float ROUND_T4_MAX_STARVED_DEATH_SHARE   = 0.30f;
    /** T6: no DamageClass may deal more than this share of TACTICAL's landed damage. */
    public static final float ROUND_T6_MAX_CLASS_DAMAGE_SHARE    = 0.50f;
    /** T6: hint-driven switches per hinted COMBAT floor, at least this. */
    public static final float ROUND_T6_MIN_SWITCHES_PER_HINTED_FLOOR = 1.0f;
    /** T7: no keystone may be more than this share of the keystones plain TACTICAL takes. */
    public static final float ROUND_T7_MAX_KEYSTONE_SHARE        = 0.50f;
    /** G: median reserve on leaving a COMBAT floor, per carried ammo type, as a fraction of its cap. */
    public static final float ROUND_G_RESERVE_MIN                = 0.10f;
    public static final float ROUND_G_RESERVE_MAX                = 0.60f;
    /** J2: no perk may be picked on more than this share of the screens that offered it... */
    public static final float ROUND_J2_MAX_PERK_PICK_RATE        = 0.60f;
    /** ...counted over perks offered at least this many times (a rate on three offers is noise). */
    public static final int   ROUND_J2_MIN_PERK_OFFERS           = 10;

    /** Vitality fraction at which TACTICAL breaks off a fight and heads for the stairs (no heals left). */
    public static final float SIM_TACTICAL_RETREAT_FRACTION = 0.35f;
    /** Vitality/ammo/heal fraction below which a turn counts as taken in a RESOURCE CRISIS. */
    public static final float SIM_RESOURCE_CRISIS_FRACTION = 0.25f;
    // (SIM_TACTICAL_GUARD_DAMAGE_FRACTION — "guard when a telegraphed hit would take a fifth of what is left" —
    // was DELETED by balance-overhaul order 4, CP7b: TACTICAL now answers a heavy committed at it, see S-HEAVY.)

    // --- C. S-GATE: the boss-cheese regression (the headline guarantee) ------------------
    /**
     * Fraction of HOARDER-START-WEAPON seeds allowed to clear the FIRST boss. Zero, by contract:
     * the starting loadout losing to the first boss is what R-BOSS-GATE proves on paper, and this
     * band proves it in play. The original bug (a starting shotgun that could grind a boss down)
     * can never return silently.
     */
    public static final float SIM_GATE_MAX_CLEAR_FRACTION = 0.0f;

    // --- D. S-FAIR: deaths read as fair. The run-outcome bands that REPLACED the TACTICAL median-death-depth
    // band [SIM_TARGET_DEPTH_MIN 8, MAX 14] live in SECTION 25 (balance-overhaul order 6, F8).
    /**
     * Fraction of TACTICAL deaths that must be READABLE: preceded either by a committed enemy
     * intent (the hit was shown before it landed) or by a resource state under the crisis
     * fraction (the player walked in weak). A death must feel like "I made a mistake".
     */
    public static final float SIM_FAIR_READABLE_DEATH_MIN = 0.90f;

    // --- E. S-SKILL: playing well must matter --------------------------------------------
    /**
     * Median depth the TACTICAL policy must reach BEYOND the NAIVE one. The game ships one
     * difficulty (order 8); this gap IS its difficulty range, so it may never collapse.
     * Balance-overhaul order 6 (F8, OVERRIDE): 3 -> 5, against the finite 25-floor run.
     */
    public static final int   SIM_SKILL_DEPTH_GAP_MIN     = 5;

    // --- F. S-ECONOMY: the played economy matches the modelled one -----------------------
    /**
     * S-ECONOMY (re-based by balance-overhaul order 2): the mean share of a floor's PLANNED ammo units the
     * player actually picks up, over the floors they EXIT. Below the minimum, the planner puts supply
     * where play does not reach it; above the maximum, unplanned income is leaking in.
     */
    public static final float SIM_ECONOMY_SUPPLY_SHARE_MIN = 0.75f;
    public static final float SIM_ECONOMY_SUPPLY_SHARE_MAX = 1.10f;
    /**
     * S-SUPPLY (balance-overhaul order 2, AS2): TACTICAL's mean health fraction leaving a COMBAT floor.
     * MAX re-based 0.80 -> 0.90 by balance-overhaul order 4 (override clause): the scripted reader now answers
     * ~0.99 of the heavies that used to be its drain, and a heal re-fit cannot pull exit health back (COMBAT
     * heal is already floor-bound at the S4 medkit) — see docs/game-balance-authority.txt S-SUPPLY.
     */
    public static final float SIM_SUPPLY_EXIT_HEALTH_MIN = 0.55f;
    public static final float SIM_SUPPLY_EXIT_HEALTH_MAX = 0.90f;
    /** Fraction of TACTICAL floors allowed to fire the never-softlock emergency ammo lifeline. */
    public static final float SIM_ECONOMY_EMERGENCY_MAX_FRACTION = 0.05f;

    // --- G. S-ROUTE: the priced map survives contact with play ---------------------------
    /**
     * Tolerance on the played per-floor net HP drain versus the order-7 modelled trajectory band.
     * Play is noisier than the ledger (a policy can dodge a fight the model charges for), so the
     * band is widened by this fraction rather than applied raw.
     */
    public static final float SIM_ROUTE_TRAJECTORY_TOLERANCE = 0.25f;

    // --- H. S-SOFTLOCK: a run may end, but never get stuck --------------------------------
    /** Fraction of seeds allowed to end in a "cannot damage anything" state. Zero, by contract. */
    public static final float SIM_SOFTLOCK_MAX_FRACTION   = 0.0f;

    // --- S-LAG + THE LADDER REPORT (balance-overhaul order 1) --------------------------------
    /**
     * S-LAG: the HOARDER-START-WEAPON policy (the weapon never climbs the ladder) must die by this median
     * depth — falling behind the power ladder is punished in PLAY, not only on paper (R-LADDER L2).
     */
    public static final float SIM_LAG_MAX_MEDIAN_DEPTH       = 6f;
    /** Seeds per start depth in the LADDER REPORT probe (one floor each, both kits). */
    public static final int   SIM_LADDER_PROBE_SEEDS_PER_DEPTH = 8;

    // --- S-BUILD + THE BUILD REPORT (balance-overhaul order 5, A6) ----------------------------
    // Four build-biased TACTICAL variants (sim/BuildBias: CLOSE, MARKSMAN, PYRO-DEMOLITION, BULWARK) play
    // as extra matrix rows. Two claims: no bias is a trap or a must-pick in PLAY (depth spread — depth-
    // derived; enforced since balance-overhaul order 7 fixed the sim's navigation), and plain TACTICAL
    // does not end every run as the same character (dominant-tag share — on the order-7 round-pending waiver,
    // a round-1 question (J2); its >= SIM_BUILD_MIN_SAMPLE sample line is enforced).
    /**
     * Seeds per build-biased variant. Equal to SIM_SEED_COUNT: the whole seven-row matrix plays in well under
     * a minute on a 4-core machine (CP6 measured), so the variants get the headline sample rather than a cut.
     */
    public static final int   SIM_BUILD_SEED_COUNT             = 200;
    /** S-BUILD depth part: the four variants' median death depths satisfy max / min <= 1 + this. */
    public static final float SIM_BUILD_DEPTH_SPREAD           = 0.25f;
    /** S-BUILD dominant-tag part: no single tag may be plain TACTICAL's end-of-run dominant tag on more than this share. */
    public static final float SIM_BUILD_MAX_DOMINANT_TAG_SHARE = 0.40f;
    /** S-BUILD: plain TACTICAL runs that held a perk rank at the end must number at least this (non-vacuous share). */
    public static final int   SIM_BUILD_MIN_SAMPLE             = 30;
    /**
     * Seeds of the dedicated plain-TACTICAL BUILD SAMPLE row (TACTICAL-BUILD-SAMPLE) the dominant-tag share is
     * read from. Order 5 sized it when only ~10% of TACTICAL runs levelled up (CP6 measured 20 of 200; order 7
     * CP1, navigating: 278 ranked runs of 600), so 600 seeds give ~60 ranked runs at worst — twice SIM_BUILD_MIN_SAMPLE. The main 200-seed rows every other
     * band reads are untouched; the sample's first 200 seeds replay plain TACTICAL's runs exactly.
     */
    public static final int   SIM_BUILD_SAMPLE_SEED_COUNT      = 600;

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
     * EVENT floor budget — ZERO, because EventRoomGenerator emits an empty spawn list by design
     * (a curated story beat in a ~135-tile room, not a dungeon to clear). Was 0.15f, which was a
     * DEAD number: the generator ignored it and the RouteEconomics EVENT row already priced the
     * node at budgetScale(0f). Three sources of truth, two of them right; this is the third
     * agreeing. Keep at 0 unless EventRoomGenerator is taught to spawn.
     */
    public static final float EVENT_BUDGET_SCALE       = 0f;
    /**
     * REGION_GATE ceremonial airlock budget — ZERO, for the same reason as EVENT_BUDGET_SCALE:
     * GateAirlockGenerator hardcodes an empty spawn list (a ~131-tile pacing breath between acts)
     * and the RouteEconomics REGION_GATE row prices it at budgetScale(0f). Was a dead 0.10f.
     */
    public static final float GATE_BUDGET_SCALE        = 0f;

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
    /**
     * OVERCLOCKED vault premium: extra owned-ammo boxes that pay for its raised threat. Balance-overhaul order 6
     * (CP5): 1 -> 2 — the re-fit shop makes a credit worth ~2.3x the power it was, so the host ELITE's credit
     * income out-grew this ammo-only premium (R-RISK-PREMIUM read 0.999 at d16).
     */
    public static final int   AFFIX_OVERCLOCKED_VAULT_AMMO_BOXES = 2;
    /** SWARM vault premium: extra owned-ammo boxes that pay for its raised threat. */
    public static final int   AFFIX_SWARM_VAULT_AMMO_BOXES       = 9;
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
    /**
     * MED-BAY auto-doc one-shot heal, as a fraction of the player's max HP. Raised from the
     * order-8 hand-set 0.35: a REST node earns NO floor XP and almost no loot, so at 0.35 it
     * priced far BELOW the calm band (a sucker node). Range: 0.5–1.0.
     */
    public static final float REST_HEAL_FRACTION      = 1.0f;
    /** Take-away field medkits ('H') the clinic stocks near its exit (order-7 re-pricing). */
    public static final int   REST_MEDKITS            = 1;
    /** Take-away stim-packs ('+') the clinic stocks alongside the medkit. */
    public static final int   REST_STIMS              = 2;
    /** Take-away armour pickups the clinic stocks (shard 'a' shallow, vest 'A' deep). */
    public static final int   REST_ARMOUR             = 1;
    /** Depth at/after which the clinic's armour stock upgrades from a shard 'a' to a vest 'A'. */
    public static final int   REST_ARMOUR_VEST_DEPTH  = 4;

    // --- D. ELITE PAYOFF (the vault the risk premium pays for, R-RISK-PREMIUM) ------------
    /** Guaranteed field medkits ('H') behind the vault. */
    public static final int   ELITE_MEDKITS           = 1;
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

    // --- F. EVENT-NODE PAYOFFS. Balance-overhaul order 6 (F15) re-expressed them in LADDER CURRENCIES — the
    // magnitudes live in SECTION 25 (EVENT_* / MYSTERY_REWARD_*); the order-10 XP and ammo-box payoffs
    // (EVENT_XP_SMALL 60, EVENT_XP_LARGE 200, EVENT_AMMO_BOXES 6) are deleted.
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
    /**
     * Balance-overhaul order 2: the upgrade opportunity ONE expected on-level weapon offer is worth.
     * COMBAT / ELITE / CACHE rows derive theirs from their NodeSupplySpec weapons
     * (GameMath.expectedOnLevelWeaponOffers x this), so a spec change re-prices the map. 0.70 keeps
     * COMBAT (0.5 offers) at the historic 0.35.
     */
    public static final float ROUTE_UPGRADE_OPPORTUNITY_PER_ON_LEVEL_WEAPON = 0.70f;
    public static final float ROUTE_UPGRADE_OPPORTUNITY_ELITE  = 0.60f;
    public static final float ROUTE_UPGRADE_OPPORTUNITY_SHOP   = 0.30f;
    public static final float ROUTE_UPGRADE_OPPORTUNITY_CACHE  = 0.05f;
    /** Threat scale a BOSS floor is priced at for the ledger (bosses are governed by R-BOSS-*, not the node bands). */
    public static final float ROUTE_BOSS_THREAT_SCALE = 2.0f;
    /** HP a TRAP GAUNTLET's hazard field is expected to bite out of the player (priced as negative heal). */
    public static final float ROUTE_TRAP_GAUNTLET_HAZARD_HIT_POINTS = 45f;
    /** HP a MALFUNCTION sector's failing hazards are expected to cost (the bad-but-survivable pull). */
    public static final float ROUTE_MALFUNCTION_HAZARD_HIT_POINTS   = 55f;
    // ROUTE_EVENT_EXPECTED_CHOICE_SHARE (0.40) deleted by balance-overhaul order 6: the EVENT ledger row is now
    // priced from the catalog itself (the mean of each event's best choice, RouteEconomicsModel).

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
    // OVERRIDE (balance-overhaul order 6, CP4): MAX 0.35 -> 0.40. The audit horizon is now the whole 25-floor run
    // and the F7 dial puts REACTOR DEPTHS at 1.25 and THE CORE at 1.30, so a COMBAT node's bodies (and their XP /
    // credits) grow past the 1..15 range this band was sketched on while a calm node's payoff stays capped:
    // measured cache 0.36 / shop 0.37 at d21. Calm still costs tempo and loot — it just costs more at the peak.
    public static final float ROUTE_CALM_EV_DISCOUNT_MIN = 0.10f;
    public static final float ROUTE_CALM_EV_DISCOUNT_MAX = 0.40f;
    /**
     * R-MYSTERY-EV v2 (balance-overhaul order 6, F15 — OVERRIDES "within +/-15% of a combat node"): the weighted
     * table's EV is AT LEAST a combat node's and at most this premium above it (a gamble pays for its variance,
     * but must never become the strictly-better pick). Range: 0.20–0.40.
     */
    public static final float ROUTE_MYSTERY_EV_PREMIUM_MAX = 0.30f;
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
    // BALANCE-OVERHAUL ORDER 1 RE-FIT (override record in docs/game-balance-authority.txt): the four
    // UPPER band ends below moved to the re-measured range + ~5% — SAFEST scarcity 1.60 -> 1.70 (measured
    // 1.60: every guaranteed box is now priced against the rebased, lower enemy HP), DEADLIEST drain
    // 0.45 -> 0.65 (measured 0.62: R8 fights land harder and a fight-everything route pays for it on the
    // boss floors), XP pace 2.45 -> 2.95 (measured 2.78: the XP curve steepened 1.118 -> 1.2495 per level
    // to follow the ladder, so the same one-level lead reads as a larger banked-XP ratio) and coupling
    // 1.55 -> 1.65 (measured 1.58, now read on the power-ladder model). Every LOWER end is unchanged.
    public static final float ROUTE_TRAJECTORY_SCARCITY_MIN = 0.55f;
    public static final float ROUTE_TRAJECTORY_SCARCITY_MAX = 1.70f;
    /**
     * Cumulative per-floor net HP drain fraction over a journey (the per-FLOOR band is [0.05, 0.15]).
     * NEGATIVE means the route BANKS health — which is exactly what a calm route buys with its skipped
     * XP and loot. Measured: -0.78 (SAFEST banks) .. +0.39 (DEADLIEST bleeds ~40% of eHP per floor,
     * survivable only by routing calm before the boss). Both ends must stay inside these bounds.
     */
    public static final float ROUTE_TRAJECTORY_DRAIN_MIN = -0.85f;
    public static final float ROUTE_TRAJECTORY_DRAIN_MAX =  0.65f;
    /**
     * Cumulative XP pace: banked XP / XP needed to stand at the depth's expected level (the per-floor
     * yield is ~1.11). The floor says even the safest route ends a region no worse than ~30% under the
     * curve (the order-4 catch-up band then pulls it back); the ceiling caps how far a fight-everything
     * route may out-level the content. Measured: 0.79 (SAFEST) .. 2.33 (DEADLIEST — it also collects
     * the ELITEs the upgrade guarantee plants), with ~5% headroom above.
     */
    public static final float ROUTE_TRAJECTORY_XP_PACE_MIN = 0.70f;
    // OVERRIDE (balance-overhaul order 6, CP4): MAX 2.95 -> 3.20. The F7 peak (REACTOR DEPTHS 1.25) is more bodies
    // on the fight-everything route, so DEADLIEST banks more XP (measured 3.07). The fairness end-stop is the
    // depth-coupling line below, which stays in band — out-levelling the content is what DEADLIEST pays HP for.
    public static final float ROUTE_TRAJECTORY_XP_PACE_MAX = 3.20f;
    /**
     * Depth coupling measured at the level the journey's XP ACTUALLY bought (the per-floor band is
     * [0.90, 1.20], which assumes an exactly on-curve player). This is the fairness end-stop of the
     * whole route: neither the safest nor the deadliest way to play may leave the player unfairly weak
     * against the floor, or trivially strong. Measured: 0.87 (SAFEST) .. 1.47 (DEADLIEST), with a small
     * margin either side — a route that fights everything SHOULD end a region ahead of the curve; it
     * just may not run away with the game.
     */
    public static final float ROUTE_TRAJECTORY_COUPLING_MIN = 0.80f;
    public static final float ROUTE_TRAJECTORY_COUPLING_MAX = 1.65f;

    // =====================================================================================
    // SECTION 20 — THE POWER LADDER (balance-overhaul order 1)
    // ONE steep, readable ladder shared by both sides. Every floor has a THREAT LEVEL equal to its
    // depth; weapon damage and enemy HP grow by compound steps FITTED so an on-curve player's fights
    // feel the same length at floor 1 and floor 25; a weapon BELOW the floor's threat level takes a
    // sharp multiplicative penalty per level; rarity multiplies damage; character level multiplies max
    // HP/armour while enemy damage grows to match. The contract is R-LADDER (six sub-checks) and
    // R-LADDER-AFFORD in BalanceSchema; the one expected-player model is GameMath.expectedPlayerAtDepth.
    // See docs/game-balance-authority.txt (R-LADDER) and .claude/agents/ideas/balance-overhaul-order-1.txt.
    // =====================================================================================

    /** The audit horizon of every depth-swept rule (the run itself ends here in balance-overhaul order 6). */
    public static final int   RUN_FINAL_DEPTH = 25;

    /**
     * Highest weapon level (R6): the run's final depth plus two, so a +1 roll on the last floor and one
     * level-up beyond it stay reachable. The legacy accuracy/reload/clip/range/ability curves are
     * re-spanned over 1..MAX_WEAPON_LEVEL (GameMath.respannedLegacyWeaponLevel). Range: RUN_FINAL_DEPTH..+5.
     */
    public static final int   MAX_WEAPON_LEVEL = RUN_FINAL_DEPTH + 2;

    /** Compound weapon damage step per weapon level: base * LADDER_GROWTH^(level-1). Range: 1.05–1.10. */
    public static final float LADDER_GROWTH = 1.08f;

    // --- LEVEL GAP (R3): weapon level L on a floor of threat level d, gap = L - d.
    /** Per-level multiplicative penalty below the floor's threat level: BASE^(-gap * STEEPNESS). Range: 0.75–0.90. */
    public static final float LEVEL_GAP_PENALTY_BASE     = 0.82f;
    /** The penalty never drops a hit below this fraction of its on-level value. Range: 0.15–0.40. */
    public static final float LEVEL_GAP_FLOOR            = 0.25f;
    /** Damage bonus per level ABOVE the floor's threat level (a small reward for an elite find). Range: 0.0–0.10. */
    public static final float LEVEL_GAP_BONUS_PER_LEVEL  = 0.05f;
    /** Levels of "ahead" bonus that count; beyond this the bonus is flat. Range: 1–3. */
    public static final int   LEVEL_GAP_BONUS_CAP_LEVELS = 2;
    /**
     * THE one knob that scales the whole lag penalty: 1.0 = the designed ladder, 0 disables the penalty
     * entirely (the order-7 feel pass tunes it). Range: 0.0–1.5.
     */
    public static final float LEVEL_GAP_STEEPNESS        = 1.0f;

    /**
     * Rarity damage multiplier, indexed by WeaponTier ordinal (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY),
     * on top of the tier's ability budget (which stays). Overrides the old "rarity never raises a band".
     */
    public static final float[] RARITY_DAMAGE_MULTIPLIER = {1.00f, 1.10f, 1.20f, 1.32f, 1.45f};

    // --- ENEMY + PLAYER GROWTH (R7, R9) — FITTED so R-LADDER L1 holds at every depth 1..RUN_FINAL_DEPTH.
    // Fit (balance-overhaul order 1): the expected player's per-hit damage grows by LADDER_GROWTH^(d-1)
    // times the region's expected rarity times the expected offence-card lift (6% of reference DPT per
    // character level); the least-max-deviation compound rate for enemy HP over 1..25 is 1.139, holding
    // on-curve turns-to-kill within +/-13% of depth 1 (the residual wobble is the per-region rarity step).
    // Enemy damage is fitted the same way against max HP/armour growth (PLAYER_VITALITY_GROWTH) plus the
    // expected flat defence-card eHP, holding turns-to-die within +/-7%. Enemy HP at depth 25 is ~23x
    // depth 1, enemy damage ~9.2x (health bars are fraction-based; AS10 accepts the bigger numbers).
    /** Per-floor compound enemy HP growth: baseHP * growth^(depth-1). Fitted for R-LADDER L1. Range: 1.08–1.16. */
    public static final float ENEMY_HEALTH_GROWTH    = 1.139f;
    /** Per-floor compound enemy damage growth: baseDmg * growth^(depth-1). Fitted for R-LADDER L1. Range: 1.06–1.12. */
    public static final float ENEMY_DAMAGE_GROWTH    = 1.097f;
    /**
     * Per-character-level compound growth of max HP AND max armour (R9):
     * PLAYER_MAX_HEALTH * growth^(level-1) + card/stat bonuses. 1.09 keeps R-LADDER L6 (three levels
     * behind survives <= 80% as long) with margin. Range: 1.06–1.12.
     */
    public static final float PLAYER_VITALITY_GROWTH = 1.09f;

    /** Expected fraction of each level's EXPECTED_PERK_POWER_PP_PER_PICK spent on OFFENCE by the on-curve player (the boss derivation's 0.5). Range: 0.3–0.6. */
    public static final float LADDER_EXPECTED_OFFENCE_BUDGET_FRACTION = 0.50f;
    /**
     * Expected fraction of each level's EXPECTED_PERK_POWER_PP_PER_PICK bought as FLAT max-HP/armour eHP (R9). A quarter:
     * the defensive half of the deck (DEFENSE / SUSTAIN / UTILITY pools) is split between flat pools and
     * unhealable mitigation (dodge, flat reduction, speed), and only the flat pools stack on the vitality
     * curve. It also keeps the fitted turns-to-die drift within +/-7% (a larger flat share bends the
     * survival curve away from any single compound enemy-damage rate). Range: 0.15–0.5.
     */
    public static final float LADDER_EXPECTED_DEFENCE_BUDGET_FRACTION = 0.25f;

    // --- HEALS AS FRACTIONS OF MAX (R10): resolved against max HP / max armour at the moment of use.
    /** Field medkit ('H'): fraction of max HP restored. */
    public static final float MEDKIT_FULL_HEAL_FRACTION = 0.45f;
    /** Stim pack ('+'): fraction of max HP restored. */
    public static final float MEDKIT_STIM_HEAL_FRACTION = 0.18f;
    /** Armour shard ('a'): fraction of max armour restored. */
    public static final float ARMOUR_SHARD_FRACTION     = 0.15f;
    /** Security vest ('A'): fraction of max armour restored. */
    public static final float ARMOUR_VEST_FRACTION      = 0.60f;

    // --- FOUND / DROPPED WEAPON LEVEL ROLL (R6): level = floor threat level + offset, weighted.
    /** Level offsets a found weapon may roll relative to the floor's threat level. */
    public static final int[] WEAPON_LEVEL_ROLL_OFFSETS = {-1, 0, 1};
    /** Relative weights of WEAPON_LEVEL_ROLL_OFFSETS (same order). */
    public static final int[] WEAPON_LEVEL_ROLL_WEIGHTS = {30, 50, 20};

    // --- R8 REBASED DEPTH-1 TARGETS (on-curve player; reference workhorse = Assault Rifle, COMMON L1,
    // measured per hit at LADDER_REFERENCE_RANGE_TILES including falloff). TTK = hits to kill the enemy;
    // TTD = ordinary enemy hits the player survives from full HP + armour (REFERENCE_PLAYER_EHP).
    /** Range at which the R8 reference hit is measured (falloff included). */
    public static final int   LADDER_REFERENCE_RANGE_TILES = 3;
    public static final int   LADDER_TTK_HITS_CHAFF_MIN      = 1;
    public static final int   LADDER_TTK_HITS_CHAFF_MAX      = 2;
    public static final int   LADDER_TTK_HITS_SOLDIER_MIN    = 3;
    public static final int   LADDER_TTK_HITS_SOLDIER_MAX    = 4;
    public static final int   LADDER_TTK_HITS_BRUISER_MIN    = 5;
    public static final int   LADDER_TTK_HITS_BRUISER_MAX    = 7;
    public static final int   LADDER_TTK_HITS_MINI_ELITE_MIN = 8;
    public static final int   LADDER_TTK_HITS_MINI_ELITE_MAX = 12;
    /** CHAFF has no TTD ceiling: it must merely be survivable for at least this many hits. */
    public static final int   LADDER_TTD_HITS_CHAFF_MIN      = 10;
    public static final int   LADDER_TTD_HITS_SOLDIER_MIN    = 7;
    public static final int   LADDER_TTD_HITS_SOLDIER_MAX    = 9;
    public static final int   LADDER_TTD_HITS_BRUISER_MIN    = 4;
    public static final int   LADDER_TTD_HITS_BRUISER_MAX    = 6;
    public static final int   LADDER_TTD_HITS_MINI_ELITE_MIN = 3;
    public static final int   LADDER_TTD_HITS_MINI_ELITE_MAX = 5;

    // --- R-LADDER BOUNDS (R12). Ratios are CONTINUOUS turns (eHP / per-hit damage, un-rounded), so the
    // one-hit quantisation of a chaff kill cannot hide a lag penalty.
    /** L1: on-curve TTK and TTD stay within +/- this fraction of their depth-1 values. */
    public static final float LADDER_ON_CURVE_TOLERANCE        = 0.15f;
    /** L2: two levels behind must take at least this many times as long to kill. */
    public static final float LADDER_LAG_TWO_MIN_TTK_RATIO     = 1.5f;
    /** L2: four levels behind must take at least this many times as long to kill. */
    public static final float LADDER_LAG_FOUR_MIN_TTK_RATIO    = 2.5f;
    /** L2: the start weapon (L1 COMMON) from LADDER_START_WEAPON_FROM_DEPTH on. */
    public static final float LADDER_START_WEAPON_MIN_TTK_RATIO = 3.0f;
    /** L2: first depth the start-weapon bound applies at. */
    public static final int   LADDER_START_WEAPON_FROM_DEPTH   = 5;
    /** L3: two levels ahead may shorten a kill to no less than this fraction of on-curve. */
    public static final float LADDER_AHEAD_TWO_MIN_TTK_RATIO   = 0.75f;
    /** L4: on floor d, weapon level d vs d-1 must gain at least this fraction of DPT. */
    public static final float LADDER_LEVEL_FELT_MIN_GAIN       = 0.25f;
    /** L5: every rarity tier step must gain at least this fraction of DPT at equal level. */
    public static final float LADDER_RARITY_STEP_MIN_GAIN      = 0.08f;
    /** L5: LEGENDARY vs COMMON at equal level must gain at least this fraction of DPT. */
    public static final float LADDER_LEGENDARY_MIN_GAIN        = 0.40f;
    /** L6: character levels behind the curve the vitality check measures. */
    public static final int   LADDER_VITALITY_LAG_LEVELS       = 3;
    /** L6: that many levels behind must survive no more than this fraction of on-curve TTD. */
    public static final float LADDER_VITALITY_LAG_MAX_TTD_RATIO = 0.80f;

    /**
     * R-LADDER-AFFORD (R13): the shop's LEVEL UP rung must cost at most this fraction of one COMBAT
     * floor's modelled credit income, at every depth 1..RUN_FINAL_DEPTH.
     */
    public static final float LADDER_AFFORD_FRACTION = 0.5f;
    /**
     * Power-point value the shop prices the LEVEL UP rung at (through GameMath.shopPrice, like every
     * offer). Deliberately LOW: the rung is a reliable ladder step, not a marquee purchase — 1.5 PP is
     * 54 credits at depth 1 (a third of one combat floor) and 184 at depth 25 (47%). Prices EVERY shop weapon level-up
     * (the old 10 PP constant is gone). Range: 1.0–2.0.
     */
    public static final float LADDER_LEVEL_UP_POWER_POINTS = 1.5f;

    // =====================================================================================
    // SECTION 21 — SUPPLY & DENSITY (balance-overhaul order 2: full floors, fair supply)
    // -------------------------------------------------------------------------------------
    // Every generated floor is built through ONE pipeline: layout -> slot list -> ENCOUNTER plan ->
    // SUPPLY plan -> placement (level/SupplyPlanner, level/EncounterBudgetPlanner). Supply is DERIVED
    // from the floor's actual roster (its demand) and the node type's NodeSupplySpec — never from the
    // player's current HP or ammo, and never from an independent per-room or per-kill dice roll.
    // Replaces the SECTION 6 per-room chances, the SECTION 10 model floor as the tuning reference,
    // the per-kill ammo drop chances, the credit-chip roll and the GUARANTEED_UPGRADE_PER_REGION pity
    // rule. See docs/game-balance-authority.txt (R-SUPPLY, R-DENSITY, S-SUPPLY).
    // =====================================================================================

    // --- S2 AMMO split -------------------------------------------------------------------
    /** S2: share of the ammo plan spread across the ammo types of the weapons the player CARRIES. */
    public static final float SUPPLY_CARRIED_SHARE  = 0.70f;
    /** S2: share of the ammo plan spread across every OTHER ammo type (so a found weapon is usable). */
    public static final float SUPPLY_OFF_TYPE_SHARE = 0.30f;

    // --- S3 HEALS (the incoming-damage model, re-based on the order-1 expected player) ---
    /** S3: turns each planned enemy is modelled as engaging the player (the heal-economy model). */
    public static final float SUPPLY_TURNS_ENGAGED_PER_ENEMY = 2.0f;
    /**
     * S3: fraction of the roster's possible damage a competent player avoids. Range 0.4-0.7.
     * RE-FIT 0.55 -> 0.70 (balance-overhaul order 3, CP7 — the planned-heal re-fit decided at CP3): the
     * order-3 start kit (the R-ROLE Shotgun with its stagger + knockback) and the C4 hint switching let a
     * competent player avoid more of a floor's damage, so a floor needs less planned heal to leave him
     * equally hurt. Brings S-SUPPLY's TACTICAL COMBAT exit health from the 0.80 cap to 0.78. The S4 heal
     * floor (one 'H') is unaffected.
     */
    public static final float SUPPLY_AVOIDANCE_FACTOR        = 0.70f;

    // --- S4 HEAL FLOOR ---------------------------------------------------------------------
    /** S4: minimum heal value on every non-BOSS/REST/REGION_GATE floor, in fractions of max HP (one 'H'). */
    public static final float SUPPLY_HEAL_FLOOR_FRACTION = 0.45f;
    /** S4: share of the heal floor that must sit in the first half of the floor by walk distance. */
    public static final float SUPPLY_HEAL_EARLY_SHARE    = 0.50f;

    // --- S5 / S6 / S7 tracking, carriers, spread -----------------------------------------
    /** S5: placed supply of every category stays within +/- this fraction of the plan. */
    public static final float SUPPLY_TRACK_TOLERANCE = 0.10f;
    /** S6: share of the ammo + (non-floor) heal plan handed to specific enemies as their drop. */
    public static final float SUPPLY_CARRIER_SHARE   = 0.25f;
    /** S7: no single room / chamber holds more than this share of any category (count basis). */
    public static final float SUPPLY_MAX_ROOM_SHARE  = 0.35f;
    /** S6 edge case: a carrier dying on an occupied tile drops onto the nearest free tile within this radius. */
    public static final int   CARRIER_DROP_SEARCH_RADIUS = 2;
    /** S7: placement score bonus for a slot on the start->exit walk path. */
    public static final float SUPPLY_EXIT_PATH_BONUS  = 1.0f;
    /** S7: placement score bonus for a slot inside a room that holds an enemy group (reward the fight). */
    public static final float SUPPLY_GROUP_ROOM_BONUS = 1.5f;

    // --- S8 CREDITS --------------------------------------------------------------------------
    /** S8: credit chips a scale-1.0 floor carries (the old 3-7 roll's mean). */
    public static final int   SUPPLY_CREDIT_CHIPS_PER_FLOOR = 5;

    // --- E1 BODY TARGETS ---------------------------------------------------------------------
    /** E1: COMBAT body target at depth 1 (low end). */
    public static final int   BODY_TARGET_MIN_DEPTH_ONE        = 12;
    /** E1: COMBAT body target at depth 1 (high end). */
    public static final int   BODY_TARGET_MAX_DEPTH_ONE        = 16;
    /** E1: COMBAT body target at BODY_TARGET_REFERENCE_DEEP_DEPTH (low end). */
    public static final int   BODY_TARGET_MIN_DEEP             = 22;
    /** E1: COMBAT body target at BODY_TARGET_REFERENCE_DEEP_DEPTH (high end). */
    public static final int   BODY_TARGET_MAX_DEEP             = 28;
    /** E1: depth the deep body targets are reached at (linear from depth 1; held beyond). */
    public static final int   BODY_TARGET_REFERENCE_DEEP_DEPTH = 25;

    // --- E2 / E3 / E4 GROUPS -----------------------------------------------------------------
    /** E3: no non-anchor group may spend more than this fraction of the floor's threat cap. */
    public static final float GROUP_TP_FRACTION_CAP          = 0.35f;
    /** E2: HUNTER (a lone bruiser / flanker) groups are at most this fraction of a floor's groups. */
    public static final float GROUP_HUNTER_MAX_FRACTION      = 0.20f;
    /** E4: on COMBAT / ELITE floors at least this fraction of enemies stand in groups of >= 2. */
    public static final float SHAPE_GROUPED_MIN_FRACTION     = 0.75f;
    /** E4: at least this many groups of >= SHAPE_BIG_GROUP_SIZE members. */
    public static final int   SHAPE_MIN_BIG_GROUPS           = 2;
    /** E4: the size that counts as a "big" group. */
    public static final int   SHAPE_BIG_GROUP_SIZE           = 3;
    /** E4: at most this many enemies stand alone. */
    public static final int   SHAPE_MAX_LONE_ENEMIES         = 3;
    /** E2: every chaff slot fields at least this many of ONE archetype (chaff never spawns alone). */
    public static final int   GROUP_CHAFF_SLOT_MIN           = 2;
    /** E2: first depth an ELITE floor's anchor may be a WARBAND (a mini-elite + two soldiers). */
    public static final int   GROUP_WARBAND_MIN_DEPTH        = 3;
    /** E2: random noise added to a group pick's threat-per-body mismatch (variety between equal shapes). */
    public static final float GROUP_SELECTION_NOISE          = 0.30f;
    /** E2: random archetype draws tried per shape per pick before the shape is ruled out for that pick. */
    public static final int   GROUP_INSTANTIATION_ATTEMPTS   = 4;
    /** E2: the smallest remainder of bodies a shape can still field (a pick that strands fewer is penalised). */
    public static final int   GROUP_MINIMUM_FILLABLE_REMAINDER = 3;
    /** E5: a corridor-pocket first contact never stands closer than this to the start. */
    public static final int   FIRST_CONTACT_MIN_WALK_TILES   = 5;
    /** E2: no single archetype may spend more than this fraction of the cap (variety, when alternatives exist). */
    public static final float GROUP_MAX_SINGLE_TYPE_FRACTION = 0.40f;

    // --- MIXED ENCOUNTER GROUPS (mixed-encounter-groups, V1-V7) --------------------------------
    // Chaff come in PAIRS of different archetypes and every group mixes behaviours (ranged / rusher /
    // plodder), so a room asks "which one first". A monoculture survives only as the rare BROOD.
    /** V1: no archetype appears more than this many times in one group (a BROOD excepted). */
    public static final int   GROUP_MAX_SAME_TYPE_PER_GROUP       = 2;
    /** V4: a BROOD group is exactly this many of ONE chaff archetype (the only same-type cluster above V1). */
    public static final int   GROUP_BROOD_SIZE                    = 3;
    /** V4: chance, rolled once per floor, that BROOD is eligible on that floor. */
    public static final float GROUP_BROOD_FLOOR_CHANCE            = 0.25f;
    /** V4: at most this many BROOD groups on one floor. */
    public static final int   GROUP_BROOD_MAX_PER_FLOOR           = 1;
    /** V3: a non-BROOD group of at least this many members spans at least two encounter behaviours. */
    public static final int   GROUP_MIXED_BEHAVIOUR_MIN_SIZE      = 3;
    /** V6: no archetype exceeds this fraction of a floor's body target (floor GROUP_CHAFF_SLOT_MIN) while alternatives exist. */
    public static final float GROUP_MAX_SINGLE_TYPE_BODY_FRACTION = 0.30f;
    /** V7: no room holds more than this many of one archetype when the placer puts two groups in it. */
    public static final int   GROUP_ROOM_MAX_SAME_TYPE            = 3;

    // --- E5 FIRST CONTACT -------------------------------------------------------------------
    /** E5: the first group of >= 2 stands within this many walk tiles of the start. */
    public static final int   FIRST_CONTACT_MAX_WALK_TILES             = 18;
    /** E5: on the run's FIRST floor (depth 1) the first group stands within this many walk tiles. */
    public static final int   FIRST_CONTACT_FIRST_FLOOR_MAX_WALK_TILES = 12;

    // --- E6 FOOTPRINT (OWNER OVERRIDE 2026-10-01: a cut from each layout's ORIGINAL size) ---------
    // Each combat layout builds to its ORIGINAL walkable size (before balance-overhaul order 2) minus a
    // depth-ramped cut: half on floor 1, easing to a fifth from floor 5 on (deeper floors reuse the last
    // entry). Replaced the per-region 350-550 / 450-650 / 500-750 ranges, which cut caves by ~65-79%.
    /** E6: the cut from the original size per depth (index = depth - 1; deeper floors reuse the last). */
    public static final float[] FOOTPRINT_REDUCTION_BY_DEPTH = {0.50f, 0.40f, 0.30f, 0.25f, 0.20f};
    /** E6: ROOMS_MST's original walkable tiles on a full 80x45 grid (measured before the footprint cut). */
    public static final int   FOOTPRINT_ORIGINAL_WALKABLE_ROOMS  = 1130;
    /** E6: LINEAR_CORRIDOR's original walkable tiles (measured before the footprint cut). */
    public static final int   FOOTPRINT_ORIGINAL_WALKABLE_LINEAR = 700;
    /** E6: CAVERN's original walkable tiles (measured before the footprint cut). */
    public static final int   FOOTPRINT_ORIGINAL_WALKABLE_CAVERN = 2100;
    /** E6: a generator builds to its target within +/- this fraction. */
    public static final float FOOTPRINT_TOLERANCE     = 0.15f;

    // --- E7 DENSITY (enemies per 100 walkable tiles) ----------------------------------------
    public static final float DENSITY_COMBAT_MIN = 2.2f;
    public static final float DENSITY_COMBAT_MAX = 4.0f;
    public static final float DENSITY_ELITE_MIN  = 3.0f;
    public static final float DENSITY_ELITE_MAX  = 5.0f;
    public static final float DENSITY_CALM_MIN   = 0.6f;
    public static final float DENSITY_CALM_MAX   = 1.5f;

    // --- S10 NODE SUPPLY SPECS (route/NodeSupplySpec rows read these; one block per node type) -
    // threat = multiplier on the floor's threat CAP (the region dial and the node affix ride on top);
    // bodies = multiplier on the E1 body target; ammoRatio = planned ammo damage / roster demand;
    // drainTarget = share of modelled incoming damage the heals deliberately do NOT cover (negative =
    // a net heal gain); armourShare = share of the heal value delivered as armour; credits = chip scale.
    public static final float NODE_SUPPLY_COMBAT_THREAT       = 1.00f;
    public static final float NODE_SUPPLY_COMBAT_BODIES       = 1.00f;
    public static final float NODE_SUPPLY_COMBAT_AMMO_RATIO   = 0.85f;
    public static final float NODE_SUPPLY_COMBAT_DRAIN_TARGET = 0.20f;
    public static final float NODE_SUPPLY_COMBAT_ARMOUR_SHARE = 0.20f;
    public static final float NODE_SUPPLY_COMBAT_CREDITS      = 1.00f;
    public static final int   NODE_SUPPLY_COMBAT_WEAPONS      = 1;
    public static final int   NODE_SUPPLY_COMBAT_WEAPON_OFFSET_MIN = -1;
    public static final int   NODE_SUPPLY_COMBAT_WEAPON_OFFSET_MAX = 0;

    /**
     * ELITE threat cap = 1.6x COMBAT (A6). Bodies barely rise (1.05x): the extra threat buys HEAVIER groups
     * (a WARBAND anchor), not a crowd — 1.2x bodies on the lower half of a region's footprint cannot hold
     * the ELITE density band (34 bodies / 625 tiles = 5.4 per 100 at depth 25).
     */
    public static final float NODE_SUPPLY_ELITE_THREAT        = 1.60f;
    public static final float NODE_SUPPLY_ELITE_BODIES        = 1.05f;
    public static final float NODE_SUPPLY_ELITE_AMMO_RATIO    = 0.85f;
    /** ELITE vault: extra ammo (as a share of roster demand) placed behind the anchor group. */
    public static final float NODE_SUPPLY_ELITE_VAULT_AMMO_RATIO = 0.25f;
    public static final float NODE_SUPPLY_ELITE_DRAIN_TARGET  = 0.30f;
    public static final float NODE_SUPPLY_ELITE_ARMOUR_SHARE  = 0.30f;
    public static final float NODE_SUPPLY_ELITE_CREDITS       = 1.60f;
    public static final int   NODE_SUPPLY_ELITE_WEAPONS       = 1;
    public static final int   NODE_SUPPLY_ELITE_WEAPON_OFFSET_MIN = 1;
    public static final int   NODE_SUPPLY_ELITE_WEAPON_OFFSET_MAX = 2;
    /** ELITE reward tier floor: the region's minimum drop tier plus this many steps. */
    public static final int   NODE_SUPPLY_ELITE_WEAPON_TIER_BONUS = 1;

    // CACHE threat 0.30, not the idea's 0.35 starting value: R-HONEST-SAFE caps a safe-looking node's
    /** A6: an ELITE floor's spent Threat Points over a COMBAT floor's at the same depth (~1.6x). */
    public static final float ELITE_THREAT_RATIO_MIN = 1.40f;
    public static final float ELITE_THREAT_RATIO_MAX = 1.80f;

    // threat ratio at ROUTE_HONEST_SAFE_MAX_THREAT_RATIO (0.30); bodies stay 0.35 (balance-overhaul o2 CP6).
    public static final float NODE_SUPPLY_CACHE_THREAT        = 0.30f;
    public static final float NODE_SUPPLY_CACHE_BODIES        = 0.35f;
    public static final float NODE_SUPPLY_CACHE_AMMO_RATIO    = 2.00f;
    /** CACHE: negative drain = the heals deliberately exceed the modelled incoming damage (a breather). */
    public static final float NODE_SUPPLY_CACHE_DRAIN_TARGET  = -1.25f;
    public static final float NODE_SUPPLY_CACHE_ARMOUR_SHARE  = 0.40f;
    // Balance-overhaul order 6 (CP5): 1.20 -> 1.50 — after the shop re-fit a credit buys ~2.3x the power, so
    // COMBAT's bounties out-grew the depot (R-CALM-COST cache discount read 0.40 at d25); more chips keep it a
    // breather that still costs loot.
    public static final float NODE_SUPPLY_CACHE_CREDITS       = 1.50f;
    public static final int   NODE_SUPPLY_CACHE_WEAPONS       = 1;
    public static final float NODE_SUPPLY_CACHE_WEAPON_CHANCE = 0.50f;

    public static final float NODE_SUPPLY_SHOP_THREAT         = 0.35f;
    public static final float NODE_SUPPLY_SHOP_BODIES         = 0.35f;
    public static final float NODE_SUPPLY_SHOP_AMMO_RATIO     = 2.00f;
    public static final float NODE_SUPPLY_SHOP_DRAIN_TARGET   = -0.75f;
    public static final float NODE_SUPPLY_SHOP_ARMOUR_SHARE   = 0.20f;
    public static final float NODE_SUPPLY_SHOP_CREDITS        = 2.00f;

    // REST / EVENT / MYSTERY / BOSS / REGION_GATE carry TODAY'S contents as a spec (order 6 retunes
    // REST / EVENT / MYSTERY). Their bespoke stock still rides the profiles' guarantees.
    /** MYSTERY: threat 1.0 — the hidden outcome's own EnemyBudgetOverride scales it (order 6 retunes). */
    public static final float NODE_SUPPLY_MYSTERY_THREAT      = 1.00f;
    public static final float NODE_SUPPLY_MYSTERY_BODIES      = 1.00f;
    /** EVENT: the event room's own budget scale is the threat (RouteMapConstants.EVENT_BUDGET_SCALE). */
    public static final float NODE_SUPPLY_EVENT_THREAT        = 1.00f;
    /** REGION_GATE: threat 0 — the airlock is a story beat, not a fight. */
    public static final float NODE_SUPPLY_GATE_THREAT         = 0.00f;

    // --- AUDIT SWEEP (R-SUPPLY / R-DENSITY) ---------------------------------------------------

    /** A2/A3: seeds per (generator x node type x depth) cell of the R-SUPPLY / R-DENSITY sweep. */
    public static final int   SUPPLY_AUDIT_SEED_COUNT = 30;
    /** A2/A3: the sparse depths the sweep visits. */
    public static final int[] SUPPLY_AUDIT_DEPTHS     = {1, 5, 10, 15, 20, 25};

    // =====================================================================================
    // SECTION 22 — MATCHUPS & ROLES (balance-overhaul order 3: weapon roles and matchups)
    // -------------------------------------------------------------------------------------
    // Every weapon declares a DamageClass (entity/DamageClass) and every enemy family a trait
    // (enemy/EnemyTrait). The MATCHUP TABLE joins them: one multiplier per class x trait, applied to
    // the player's hit after the power ladder and before enemy Block/armour (M3). The rows below are
    // the table's single source of truth — entity/MatchupCatalog.bootstrap() READS them and registers
    // one row per cell; nothing else may hardcode a matchup number.
    // Later checkpoints of order 3 land here too: the shotgun falloff table, knockback/stagger rules,
    // incinerator burn stacks, per-AmmoType supply generosity + re-fitted reserve caps, the R-ROLE
    // reference scenarios and margins. See docs/game-balance-authority.txt (R-ROLE).
    // =====================================================================================

    /**
     * M3: every matchup multiplier is raised to this power before use (GameMath.matchupMultiplier).
     * 1.0 = the table as written; 0 flattens every matchup to 1.0. Range: 0.0-1.5 (order 7 tunes it).
     */
    public static final float MATCHUP_STRENGTH            = 1.0f;
    /** M4: a (strength-adjusted) multiplier at or above this is EFFECTIVE. */
    public static final float MATCHUP_EFFECTIVE_THRESHOLD = 1.3f;
    /** M4: a (strength-adjusted) multiplier at or below this is RESISTED. */
    public static final float MATCHUP_RESISTED_THRESHOLD  = 0.8f;

    /**
     * TRAIT-AWARE TP (CP2): how much of the reference GENERALIST's (BALLISTIC) matchup against an
     * archetype's trait is priced into its Threat Points. referenceMultiplier = 1 + weight x (m - 1);
     * eHP_priced = eHP / referenceMultiplier (GameMath.matchupReferenceMultiplier /
     * traitAdjustedEnemyEffectiveHitPoints). 0 = the old trait-blind pricing; 1 = priced as if the
     * player only ever carried a rifle. Between, because the on-curve player usually carries a second
     * gun. Moves TP, XP, the encounter budget and survival turns together; the R8 hit bands and
     * R-LADDER read the NEUTRAL eHP and do not move. Range: 0.0-1.0.
     *
     * DECISION (balance-overhaul order 3, CP7): ships at 0 — the machinery stays live, weighted to zero.
     * Re-fit with TACTICAL switching by matchup and the planned-heal re-fit (SUPPLY_AVOIDANCE_FACTOR 0.70):
     * w = 0.25 -> S-SUPPLY 0.83, w = 0.5 -> 0.81, both over the 0.80 cap (w = 0: 0.78). Pricing the
     * generalist's armour penalty makes the encounter planner swap armoured bodies for unresisted ones,
     * which the sim's two-gun kit (Chaingun + Shotgun) beats more easily. R-ENEMY would allow w <= ~0.83.
     * Revisit in the order-7 feel pass, together with S-SUPPLY, once played kits carry more classes.
     */
    public static final float MATCHUP_TP_REFERENCE_WEIGHT = 0.0f;

    // --- W1/W2 SPREAD ROLE (Shotgun / Double-Barrel) — starting values, CP3b fits them ------------
    /**
     * W1: Shotgun damage fraction by tile distance; index = distance - 1, beyond the table = 0
     * (GameMath.shotgunFalloffAtTile). Replaces the 0.18 drop coefficient.
     */
    public static final float[] SHOTGUN_FALLOFF_BY_TILE       = {1.00f, 0.85f, 0.55f, 0.30f, 0.15f};
    /** W2: the Double-Barrel's falloff — the Shotgun's shape, one tile shorter (range 4). */
    public static final float[] DOUBLE_BARREL_FALLOFF_BY_TILE = {1.00f, 0.85f, 0.55f, 0.30f};
    /** W1: a SPREAD hit at or inside this many tiles knocks a non-BOSS, non-MINI_ELITE target back one tile. */
    public static final int     SHOTGUN_KNOCKBACK_MAX_TILES   = 1;
    /** W1: a SPREAD hit at or inside this many tiles STAGGERS the target (its next committed action is lost). */
    public static final int     SHOTGUN_STAGGER_MAX_TILES     = 2;
    /**
     * W1: world turns that must separate two staggers on the same enemy — 2 means a stagger on turn N
     * blocks turn N+1 ("cannot chain two turns running") and allows turn N+2.
     */
    public static final int     SHOTGUN_STAGGER_MIN_TURNS_BETWEEN = 2;

    // --- W3 FIRE ROLE (Incinerator burn) — starting values, CP3b fits them ------------------------
    /**
     * W3: each Incinerator burn stack ticks this fraction of the weapon's ladder-scaled impact hit per
     * turn (GameMath.incineratorBurnPerStack). 0.5 x 12 = 6/turn per stack at depth 1 (the old flat 6).
     */
    public static final float   FLAME_BURN_FRACTION   = 0.6f;
    /** W3: Incinerator burn stacks on one target, at most (one stack per spray). */
    public static final int     FLAME_BURN_MAX_STACKS = 3;

    // --- A-1 AMMO GENEROSITY (CP4) — per-AmmoType weights on the order-2 carried / off-type split -------
    // SupplyPlanner multiplies each type's 70/30 share by its weight and RE-NORMALISES the shares to sum
    // 1 (GameMath.normalisedAmmoShare), so a floor's TOTAL planned ammo damage is unchanged — only the
    // mix moves: the generalist's bullets get scarcer, shells richer. Read through AmmoType (data, no
    // switch). The spec's FUEL has no AmmoType of its own — the Incinerator burns CELLS (shared with the
    // Plasma Rifle and the Arc Cannon), which keep 1.0; the spec's GRENADES are AmmoType.ROCKETS.
    public static final float AMMO_SUPPLY_GENEROSITY_BULLETS = 0.8f;
    public static final float AMMO_SUPPLY_GENEROSITY_SHELLS  = 1.3f;
    public static final float AMMO_SUPPLY_GENEROSITY_CELLS   = 1.0f;
    public static final float AMMO_SUPPLY_GENEROSITY_ROCKETS = 1.0f;
    public static final float AMMO_SUPPLY_GENEROSITY_SLUGS   = 0.9f;
    /** A-1: floors of model-floor demand a FULL reserve of each type banks (the cap re-fit target). */
    public static final float AMMO_BANKING_FLOORS_BULLETS = 1.0f;
    public static final float AMMO_BANKING_FLOORS_SHELLS  = 1.5f;
    public static final float AMMO_BANKING_FLOORS_CELLS   = 1.5f;
    public static final float AMMO_BANKING_FLOORS_ROCKETS = 1.5f;
    public static final float AMMO_BANKING_FLOORS_SLUGS   = 1.0f;
    /** A-1: a reserve cap's banked floors may sit this far either side of its target (whole-unit rounding). */
    public static final float AMMO_BANKING_FLOORS_TOLERANCE = 0.15f;

    // --- S-SWITCH (CP7, A8 — re-stated by DECISION D3) ----------------------------------------------
    // A8 as first written ("TACTICAL >= 1.0 matchup switches per COMBAT floor") measured how often the sim's
    // Chaingun + Shotgun start kit happens to meet a CHITIN target, not whether the C4 hint works: measured
    // 0.13 per COMBAT floor with a take rate of 1.00 (21 of 167 COMBAT floors ever showed a hint). Re-stated
    // (override clause) as three lines: the hint is TAKEN when it appears, it produces a switch on every
    // floor that shows it, and enough floors show it that the first two are not read off an empty sample.
    /** S-SWITCH: TACTICAL's hint take rate (hint-driven switches / hint episodes) must be at least this. */
    public static final float SIM_SWITCH_MIN_TAKE_RATE = 0.8f;
    /** S-SWITCH: hint-driven switches per COMBAT floor ON WHICH A HINT APPEARED must be at least this. */
    public static final float SIM_SWITCH_MIN_PER_HINTED_COMBAT_FLOOR = 1.0f;
    /** S-SWITCH: at least this many TACTICAL COMBAT floors across the matrix must show a hint (non-vacuous). */
    public static final int   SIM_SWITCH_MIN_HINTED_COMBAT_FLOORS = 10;

    // --- S-HEAVY (balance-overhaul order 4, CP7b, A8) -----------------------------------------------------
    // Replaces the old "guard at a fifth of what is left" TacticalPolicy trigger. Success = the player answered the
    // heavy: it was avoided, guarded (front arc), cancelled by a stagger/riposte/stun, or its source was killed
    // first. Only LANDED (unguarded, or hit from the side/back) is a failure.
    /** S-HEAVY: TACTICAL's success share of the heavies committed AT it must be at least this. */
    public static final float SIM_HEAVY_TACTICAL_MIN_SUCCESS = 0.80f;
    /** S-HEAVY: NAIVE ignores telegraphs, so its success share (by chance alone) must be at most this. */
    public static final float SIM_HEAVY_NAIVE_MAX_SUCCESS    = 0.30f;
    /** S-HEAVY: TACTICAL must have had at least this many heavies committed at it (so the share is not vacuous). */
    public static final int   SIM_HEAVY_MIN_SAMPLE           = 30;
    /** HEAVY REPORT depth bands: each entry is a band's LAST depth (1-5, 6-10, 11-15); a final band runs to the ceiling. */
    public static final int[] SIM_HEAVY_REPORT_BAND_LAST_DEPTHS = {5, 10, 15};

    // --- R-ROLE — the reference scenarios and margins (replace the R-WEAPON power bands) -----------
    // Each scenario = a target trait x group size x engagement band; its archetype (the representative
    // of that trait and role) is chosen in BalanceSchema's scenario registry. Evaluated for the ON-CURVE
    // player (GameMath.expectedPlayerAtDepth) at every ROLE_SCENARIO_DEPTHS entry; the turns-to-clear
    // model is GameMath.roleScenarioClear, S8 is GameMath.roleScenarioBruiserHitsTaken.
    /** R-ROLE: the depths every scenario is evaluated at. */
    public static final int[] ROLE_SCENARIO_DEPTHS          = {5, 15};
    /** R-ROLE: turns after which a scenario counts as not cleared. */
    public static final int   ROLE_SCENARIO_TURN_CAP        = 60;
    /** R-ROLE: of a clustered group, how many stand in the firing lane (what a PIERCE shot reaches). */
    public static final int   ROLE_GROUP_TARGETS_IN_LANE    = 2;
    /** S1 FLESH single, 3-5 tiles. */
    public static final int   ROLE_S1_GROUP = 1, ROLE_S1_MIN_TILES = 3, ROLE_S1_MAX_TILES = 5;
    /** S2 FLESH group of 3 (on-curve chaff), 1-3 tiles. */
    public static final int   ROLE_S2_GROUP = 3, ROLE_S2_MIN_TILES = 1, ROLE_S2_MAX_TILES = 3;
    /** S3 SHIELDED single, 3-5 tiles. */
    public static final int   ROLE_S3_GROUP = 1, ROLE_S3_MIN_TILES = 3, ROLE_S3_MAX_TILES = 5;
    /** S4 PLATED single, 3-6 tiles. */
    public static final int   ROLE_S4_GROUP = 1, ROLE_S4_MIN_TILES = 3, ROLE_S4_MAX_TILES = 6;
    /** S5 BURNABLE group of 3, 1-3 tiles. */
    public static final int   ROLE_S5_GROUP = 3, ROLE_S5_MIN_TILES = 1, ROLE_S5_MAX_TILES = 3;
    /** S6 CHITIN single, 1-2 tiles. */
    public static final int   ROLE_S6_GROUP = 1, ROLE_S6_MIN_TILES = 1, ROLE_S6_MAX_TILES = 2;
    /** S7 INFERNAL single, 3-5 tiles. */
    public static final int   ROLE_S7_GROUP = 1, ROLE_S7_MIN_TILES = 3, ROLE_S7_MAX_TILES = 5;
    /** S8 a BRUISER charging from this many tiles — scored in hits it lands before it dies. */
    public static final int   ROLE_S8_START_TILES = 3;
    /** R-ROLE-1 NICHE: a non-BALLISTIC class's best must beat the best BALLISTIC by this factor. */
    public static final float ROLE_NICHE_MARGIN               = 1.20f;
    /** R-ROLE-2 GENERALIST: BALLISTIC within this factor of the best in S1. */
    public static final float ROLE_GENERALIST_BOUND           = 1.30f;
    /** R-ROLE-3 NO DOMINANCE: no class is best in more than this many scenarios (per depth). */
    public static final int   ROLE_MAX_SCENARIOS_BEST         = 2;
    /**
     * R-ROLE-5: each ranged DamageClass's DECLARED niche scenario, indexed by DamageClass ordinal (the W1-W8
     * role text: BALLISTIC the S1 generalist, SPREAD S6 point-blank chitin, ENERGY S3 shields, RAIL S4 plate
     * (AS8 — the Railgun's ammo check), FIRE S5 burnable groups, EXPLOSIVE S2 groups; melee null, exempt).
     * R-ROLE-5 checks ammo feasibility HERE — not wherever the class happens to sit closest to the best —
     * so the niche the role exists for can never quietly price itself out of a floor's supply.
     */
    public static final String[] ROLE_DECLARED_NICHE_BY_CLASS = { "S1", "S6", "S3", "S4", "S5", "S2", null, null };
    /** R-ROLE-4 RISK PAYS: SPREAD sustained per-turn damage at 1-2 tiles over the Assault Rifle's at 3. */
    public static final float ROLE_RISK_PAYS_RATIO            = 2.0f;
    /** R-ROLE-4: the SPREAD engagement band and the generalist's reference range. */
    public static final int   ROLE_RISK_SPREAD_MIN_TILES = 1, ROLE_RISK_SPREAD_MAX_TILES = 2;
    public static final int   ROLE_RISK_GENERALIST_TILES = 3;
    /** A6: the Incinerator clears S2 (three on-curve chaff) in at most this many sprays at every R-ROLE depth. */
    public static final int   ROLE_INCINERATOR_S2_MAX_SPRAYS  = 2;
    /** R-ROLE-5 AMMO FEASIBLE: seeds averaged for "one average COMBAT floor's planned supply". */
    public static final int   ROLE_SUPPLY_SEED_COUNT          = 20;

    // --- M3 MATCHUP TABLE — one row per EnemyTrait, columns in DamageClass ordinal order ----
    //                                                 BALLISTIC SPREAD ENERGY RAIL  FIRE  EXPLOSIVE BLADE BLUNT
    /** M3 row FLESH (Aberrations) — the neutral baseline. */
    public static final float[] MATCHUP_ROW_FLESH    = {1.00f,  1.00f, 1.00f, 1.00f, 1.00f, 1.00f,  1.00f, 1.00f};
    /** M3 row SHIELDED (Machines) — energy cracks the shield; bullets glance. */
    public static final float[] MATCHUP_ROW_SHIELDED = {0.65f,  0.80f, 1.50f, 1.00f, 0.80f, 1.00f,  0.80f, 1.20f};
    /** M3 row PLATED (Golems) — rail, explosives and blunt force crack plate; bullets and fire resisted. */
    public static final float[] MATCHUP_ROW_PLATED   = {0.55f,  0.80f, 0.90f, 1.40f, 0.60f, 1.30f,  0.60f, 1.40f};
    /** M3 row BURNABLE (Undead) — fire is the answer. */
    public static final float[] MATCHUP_ROW_BURNABLE = {1.00f,  1.00f, 1.00f, 1.00f, 1.80f, 1.10f,  1.10f, 1.00f};
    /** M3 row CHITIN (Insects) — spread and fire at close range; bullets resisted. */
    public static final float[] MATCHUP_ROW_CHITIN   = {0.75f,  1.35f, 1.00f, 1.10f, 1.40f, 1.00f,  1.20f, 1.00f};
    /** M3 row INFERNAL (Demons) — energy bites; fire is almost useless. */
    public static final float[] MATCHUP_ROW_INFERNAL = {1.00f,  1.10f, 1.35f, 1.00f, 0.30f, 0.90f,  1.00f, 1.00f};

    // =========================================================================
    // SECTION 23 — HEAVIES, GUARD, ARMOUR, STATUS, HAZARDS (balance-overhaul order 4)
    // CP1 adds the HEAVY numbers and the (data-only) guard split; later checkpoints extend it.
    // A HEAVY is a committed-then-resolved attack (HeavyAttack): damage = fraction of the ON-CURVE player's
    // max HP at the enemy's depth (ExpectedPlayer), never a flat number. Overrides: the Shell Brute charge
    // x2.4 + 75% stop-short, the self-destruct x3.0 capped at 68, AREA_STRIKE_DAMAGE_MULTIPLIER 1.35.
    // =========================================================================

    /** H2: CHARGE / LANCE damage as a fraction of the on-curve player's max HP. Band [MIN, MAX]. */
    public static final float HEAVY_DAMAGE_FRACTION                = 0.50f;
    /** H2: AREA_STRIKE slam fraction (the strike cross is wider, so slightly lighter). */
    public static final float HEAVY_AREA_STRIKE_DAMAGE_FRACTION    = 0.45f;
    /** H2: SELF_DESTRUCT blast fraction (the finisher is the heaviest, and defusable by killing it). */
    public static final float HEAVY_SELF_DESTRUCT_DAMAGE_FRACTION  = 0.55f;
    /** R-HEAVY band: every heavy's on-curve unguarded hit lies in [MIN, MAX] of expected max HP. */
    public static final float HEAVY_DAMAGE_FRACTION_MIN            = 0.40f;
    public static final float HEAVY_DAMAGE_FRACTION_MAX            = 0.60f;
    /** Ordinary feel knob (order 7): scales every heavy hit; 1.0 = exactly the fractions above. */
    public static final float HEAVY_DAMAGE_SCALE                   = 1.0f;
    /** TP pricing: share of a heavy a competent player is expected to dodge or blunt (priced hit = hit x (1 - this)). */
    public static final float HEAVY_EXPECTED_AVOIDANCE             = 0.6f;
    /** H7: incoming-damage multiplier inside the guard's front arc for an ORDINARY hit (replaced GUARD_FRONT_MULTIPLIER 0.35). */
    public static final float GUARD_FRONT_ORDINARY                 = 0.50f;
    /** H7: ... for a HEAVY hit, which also RIPOSTES. R-HEAVY reads it for the cornered-guard cap. */
    public static final float GUARD_FRONT_HEAVY                    = 0.20f;
    /** H8: a HEAVY hit costs the armour pool this multiple of the share armour absorbed (HP damage unaffected). */
    public static final float ARMOUR_HEAVY_SHRED_MULTIPLIER        = 1.5f;
    /** H9: each enemy status application blocked by armour costs this fraction of MAX armour (min 1). */
    public static final float ARMOUR_STATUS_COST_FRACTION          = 0.10f;
    /** R-HEAVY (H4): a guarded heavy costs at most this fraction of the on-curve player's max HP. */
    public static final float HEAVY_GUARDED_MAX_FRACTION           = 0.12f;
    /** R-BOSS-VERB-CAP (override): a TELEGRAPHED boss heavy verb costs at most this fraction of eHP. */
    public static final float BOSS_TELEGRAPHED_HEAVY_MAX_FRACTION  = 0.50f;

    // --- STATUS magnitudes as fractions of the host's max HP (H10). Resolved at APPLICATION time via
    // GameMath.statusTickDamage and stored per stack in the StatusEffect, so they never go stale with depth.
    // These REPLACE SECTION 8's flat BURN_DAMAGE_PER_TURN (4), POISON_DAMAGE_PER_STACK (2) and, with
    // POISON_MAX_STACKS 5 -> 6 and WEAK_DAMAGE_PERCENT 25 -> 35 above, are the override of the status numbers.
    /** H10: POISON damage per stack per turn, as a fraction of the host's max HP. */
    public static final float POISON_TICK_FRACTION                 = 0.015f;
    /** H10: stacks an acid ranged hit (Mire Wraith / Acid Drone) applies to the player. */
    public static final int   POISON_STACKS_PER_ACID_HIT           = 2;
    /** H10: stacks a toxic hazard tile applies to the PLAYER each turn stood in it. */
    public static final int   POISON_STACKS_PER_TOXIC_TURN         = 2;
    /** H10: BURN damage per turn from status/hazard fire, as a fraction of the host's max HP. */
    public static final float BURN_TICK_FRACTION                   = 0.03f;
    /** H10: turns of SLOWED a connecting Rimeshell lance beam applies to the player. */
    public static final int   LANCE_SLOW_DURATION_TURNS            = 2;

    // --- REVENANT LUNGE (H1, CP5): the Revenant's WIND_UP lunge reuses the Shell Brute charge code path with
    // its own, shorter lane and a cooldown, so it stays a melee bruiser that occasionally lunges.
    /** Nearest cardinal-lane gap (tiles) that opens a Revenant lunge. A lunge is short. */
    public static final int   REVENANT_LUNGE_TRIGGER_MIN_TILES    = 2;
    /** Farthest cardinal-lane gap (tiles) a Revenant will lunge from; its danger lane is this + 1. */
    public static final int   REVENANT_LUNGE_TRIGGER_MAX_TILES    = 3;
    /** Own turns that must pass after a lunge commit before the Revenant may commit another. */
    public static final int   REVENANT_LUNGE_COOLDOWN_TURNS       = 3;

    // --- HAZARDS as fractions (H13/H14, CP6). Replace the flat EXPLOSION_DAMAGE 12, the 1.5x enemy-fire
    // multiplier (now HAZARD_FIRE_ENEMY_DAMAGE_MULTIPLIER 2.5, SECTION 12) and HAZARD_THREAT_TURNS_STOOD (the
    // hazard TP window is now the burn window HAZARD_FIRE_BURN_TURNS).
    /**
     * Base HP of the REFERENCE SOLDIER barrels are scaled to: the R-LADDER reference SOLDIER archetype
     * (BalanceSchema.ladderReferenceArchetype(SOLDIER) = VOID_SHROUD), grown by GameMath.enemyHealthAtDepth.
     * A barrel's blast is a fraction of THAT, so it never goes stale as enemy HP climbs the ladder.
     */
    public static final int   HAZARD_BARREL_REFERENCE_SOLDIER_BASE_HEALTH = VOID_SHROUD_MAX_HEALTH;
    /** H13: the barrel's own tile, as a fraction of the reference soldier's HP at the floor's depth. */
    public static final float HAZARD_BARREL_CENTRE_FRACTION        = 1.2f;
    /** H13: each orthogonal neighbour tile, as a fraction of the reference soldier's HP at the floor's depth. */
    public static final float HAZARD_BARREL_RING_FRACTION          = 0.6f;
    /** H13: blast damage to the PLAYER on the centre tile, as a fraction of the player's max HP. */
    public static final float HAZARD_BARREL_PLAYER_CENTRE_FRACTION = 0.30f;
    /** H13: blast damage to the PLAYER on an orthogonal ring tile, as a fraction of the player's max HP. */
    public static final float HAZARD_BARREL_PLAYER_RING_FRACTION   = 0.15f;
    /** H13: stacks a toxic tile / Plague Hulk cloud applies to an ENEMY each turn it stands in it. */
    public static final int   HAZARD_TOXIC_ENEMY_STACKS_PER_TURN   = 2;
    /** H14: share of the eligible group rooms (a group of HAZARD_GROUP_MIN_SIZE+) that receive barrels (rounded up). */
    public static final float HAZARD_GROUP_ROOM_SHARE              = 0.40f;
    /** H14: smallest group that makes its room eligible for barrels. */
    public static final int   HAZARD_GROUP_MIN_SIZE                = 3;
    /** H14: fewest barrels an eligible room receives. */
    public static final int   HAZARD_GROUP_BARRELS_MIN             = 1;
    /** H14: most barrels an eligible room receives. */
    public static final int   HAZARD_GROUP_BARRELS_MAX             = 2;
    /** H14: barrels sit within this Chebyshev distance (tiles) of the group cluster's centroid. */
    public static final int   HAZARD_GROUP_BARREL_RADIUS_TILES     = 2;

    // =========================================================================
    // SECTION 24 — BUILDS (balance-overhaul order 5): perks, keystones, the build valuation
    // REPLACES R-CARD (every card priced at LEVEL_UP_BUDGET_PP +/- tolerance), R-CARD-BREAKPOINT and the
    // TOTAL-PP INVARIANT ("total power at level L = L x 12 PP for every build"): a perk is no longer priced
    // to be EQUAL, it is priced to be FELT inside its style (R-PERK PK1), to stay FOCUSED on that style
    // (PK2), a keystone trades a real niche for a real cost (R-KEYSTONE), and no style is a trap or
    // dominant at depth 15 (R-CARRY — a bounded spread replaces the invariant). The R-CARD constants
    // themselves (LEVEL_UP_BUDGET_PP, LEVEL_UP_BUDGET_TOLERANCE, the ten card magnitudes) were deleted in
    // CP2, when the level-up runtime switched to the perk deck. Valued by util/BuildValuationModel on the
    // order-3 reference scenarios; see docs/game-balance-authority.txt R-PERK / R-KEYSTONE / R-CARRY.
    // Every per-rank array holds the TOTAL magnitude at rank 1, 2, 3 (index 0 = rank 1).
    // =========================================================================

    // --- OFFER SHAPE (B3 / B4) ----------------------------------------------------------------
    /** B3: perks offered per level-up. Range: 3 (fixed by the offer rules: synergy + pivot + one more). */
    public static final int     PERKS_OFFERED              = 3;
    /** B2: the most ranks any perk has (one-rank perks declare 1). Range: 3. */
    public static final int     PERK_MAX_RANK              = 3;
    /** B4: character levels whose level-up offers keystones instead of perks. Range: four distinct levels. */
    public static final int[]   KEYSTONE_CHARACTER_LEVELS  = {5, 10, 15, 20};
    /** B4: keystones offered on a keystone level. Range: 3. */
    public static final int     KEYSTONES_OFFERED          = 3;
    /**
     * B3: per-prior-pick draw-weight bonus toward a perk's TAG — the old LEVEL_UP_DRAW_BIAS_PER_PICK (0.6, idea 5),
     * kept as the weight behind the synergy + pivot slots so a run's identity deepens as it goes. A perk's
     * weight is 1 + ranks already held in its tag x this. Range: 0.0-1.0.
     */
    public static final float   PERK_DRAW_BIAS_PER_PICK    = 0.6f;
    /**
     * THE EXPECTED PLAYER'S PERK TERM (balance-overhaul order 5, CP2; replaces LEVEL_UP_BUDGET_PP in
     * GameMath.expectedPlayer, the boss derivation, the route ledger's XP pricing and the LADDER probe kit):
     * the power points one level-up is expected to buy, so the on-curve player's card-power term becomes
     * "expected perk power at the expected rank count" = this x (characterLevel - 1). A keystone level counts
     * as a pick at the same average (keystones trade a niche for a cost; their net is priced by R-KEYSTONE,
     * not added here). 12 reproduces the retired budget-equal deck exactly, which keeps R-LADDER and every
     * boss / route rule that reads the expected player on its fitted values. The catalog's MEASURED average
     * gain per rank (the PERKS table in BalanceReport) sits on its tag's niche and is far larger than 12 PP
     * there by design (PK1 wants >= 10% per rank on the NICHE, PK2 keeps it off the other scenarios), so a
     * perk is not 12 PP of general power: 12 is the expected player's whole-roster average, and order 6
     * re-derives it with the economy. Range: 8-16.
     */
    public static final float   EXPECTED_PERK_POWER_PP_PER_PICK = 12f;
    /**
     * The power points ONE perk rank sold by a UAC Fabricator's PLAYER-ABILITY offer is priced on (replaces
     * LEVEL_UP_BUDGET_PP for that offer; balance-overhaul order 5 CP2). Equal to the expected perk power of a
     * pick, so a bought rank costs what a level-up is worth to the expected player. Order 6 re-prices it.
     * Range: 8-16.
     */
    public static final float   SHOP_PERK_RANK_POWER_POINTS = EXPECTED_PERK_POWER_PP_PER_PICK;

    // --- PERK MAGNITUDES (B2) — CLOSE QUARTERS --------------------------------------------------
    /** Buckshot Discipline: SPREAD damage, total by rank. Spec default +15%/rank; MOVED (escalating) for PK1: the Shotgun's S6 kill is shot-reload-shot, so a flat +15% shortens it only 8% a rank. Range: 0.15-0.35 per rank. */
    public static final float[] PERK_BUCKSHOT_SPREAD_DAMAGE_BY_RANK      = {0.20f, 0.46f, 0.80f};
    /** Point Man (decided r1): SPREAD damage at point-blank (<= BUILD_POINT_BLANK_MAX_TILES), total by rank — the same ladder as Buckshot for the same reason (S6 reload). Range: 0.15-0.35 per rank. */
    public static final float[] PERK_POINT_MAN_POINT_BLANK_DAMAGE_BY_RANK = {0.20f, 0.46f, 0.80f};
    /** Point Man r2: extra knockback reach (tiles). Range: 1. */
    public static final int     PERK_POINT_MAN_KNOCKBACK_BONUS_TILES     = 1;
    /** Point Man r3: stagger reaches this many tiles (spec: "stagger also at 3 tiles"). Range: 3. */
    public static final int     PERK_POINT_MAN_STAGGER_REACH_TILES       = 3;
    /** Breacher: chance a SPREAD kill refunds one shell, total by rank. Range: 0.25-0.40 per rank (<= 1.0). */
    public static final float[] PERK_BREACHER_REFUND_CHANCE_BY_RANK      = {0.33f, 0.66f, 1.00f};

    // --- MARKSMAN --------------------------------------------------------------------------------
    /** Steady Aim: fraction of the BALLISTIC / RAIL falloff coefficient removed, total by rank. Spec default -30%/rank; MOVED to 33/66/100% (CP1b, D3) — r3 removes falloff entirely to lift MARKSMAN's R-CARRY. Range: 0.30-0.34 per rank (total <= 1.0). */
    public static final float[] PERK_STEADY_AIM_FALLOFF_REDUCTION_BY_RANK = {0.33f, 0.66f, 1.00f};
    /** First Blood: BALLISTIC / RAIL damage on a hit against a full-HP enemy, total by rank. Spec default +25%/rank; MOVED to +90%/rank: only one hit of the five an S1 kill takes is a first hit (PK1), and MARKSMAN's R-CARRY needed the rest (CP1b). Range: 0.8-1.0 per rank. */
    public static final float[] PERK_FIRST_BLOOD_DAMAGE_BY_RANK           = {0.90f, 1.80f, 2.70f};
    /** Lane Discipline: extra BALLISTIC / RAIL reach (tiles), total by rank. Range: 1 per rank. */
    public static final float[] PERK_LANE_DISCIPLINE_RANGE_BY_RANK        = {1f, 2f, 3f};
    /** Lane Discipline: BALLISTIC / RAIL damage at BUILD_LONG_RANGE_MIN_TILES or farther, total by rank. Spec default +10%/rank; MOVED to 24/50/80% — two of S1's three tiles are long range (PK1) and MARKSMAN's R-CARRY (CP1b). Range: 0.20-0.30 per rank. */
    public static final float[] PERK_LANE_DISCIPLINE_DAMAGE_BY_RANK       = {0.24f, 0.50f, 0.80f};

    // --- PYRO ------------------------------------------------------------------------------------
    /** Accelerant: extra max burn stacks (FIRE), total by rank. Range: 1 per rank. */
    public static final float[] PERK_ACCELERANT_BURN_STACKS_BY_RANK       = {1f, 2f, 3f};
    /** Accelerant (decided): FIRE damage (impact and the burn it seeds), total by rank — the burn stack alone is never reached in a reference fight. Range: 0.15-0.25 per rank. */
    public static final float[] PERK_ACCELERANT_FIRE_DAMAGE_BY_RANK       = {0.18f, 0.38f, 0.60f};

    /** Wildfire (owner ruling D5): extra turns every burn the player applies lasts, total by rank. Range: 1 per rank. */
    public static final float[] PERK_WILDFIRE_BURN_TURNS_BY_RANK          = {1f, 2f, 3f};

    // --- DEMOLITION ------------------------------------------------------------------------------
    /** Shaped Charges: EXPLOSIVE damage, total by rank. Spec default +20%/rank; MOVED to 32/68/112% for R-CARRY (DEMOLITION's lift, net of DEMOLITIONIST's HP cost). Range: 0.25-0.40 per rank. */
    public static final float[] PERK_SHAPED_CHARGES_DAMAGE_BY_RANK        = {0.32f, 0.68f, 1.12f};
    /** Blast Shield: own explosion damage removed, total by rank (never above 1.0). Spec default -50%; r3 reaches full immunity so the last rank is felt (PK1). Range: ladder ending at <= 1.0. */
    public static final float[] PERK_BLAST_SHIELD_REDUCTION_BY_RANK       = {0.50f, 0.75f, 1.00f};
    /** Sapper: barrel blast bonus, total by rank. Range: 0.30-0.60 per rank. */
    public static final float[] PERK_SAPPER_BARREL_DAMAGE_BY_RANK         = {0.50f, 1.00f, 1.50f};

    // --- ENERGY ----------------------------------------------------------------------------------
    /** Capacitor: ENERGY damage, total by rank. Spec default +15%/rank; MOVED up for R-CARRY (ENERGY's lift). Range: 0.15-0.35 per rank. */
    public static final float[] PERK_CAPACITOR_ENERGY_DAMAGE_BY_RANK      = {0.27f, 0.56f, 0.92f};
    /** Arc Jump: extra Arc Cannon chain jumps, total by rank. Range: 1 per rank. */
    public static final float[] PERK_ARC_JUMP_CHAIN_JUMPS_BY_RANK         = {1f, 2f, 3f};
    /** Arc Jump (decided): raise of the per-jump chain multiplier, total by rank — a fourth jump at 0.6^4 is not felt, so the jumps also lose less. Range: 0.08-0.15 per rank (multiplier <= 1). */
    public static final float[] PERK_ARC_JUMP_CHAIN_MULTIPLIER_BY_RANK    = {0.12f, 0.25f, 0.38f};
    /** Overcharge: Plasma pierce bonus per enemy already pierced, total by rank. Spec default +15%/rank; MOVED: in a two-wide lane only the second body gains, and the clear waits on the first. Range: 0.35-0.50 per rank. */
    public static final float[] PERK_OVERCHARGE_PIERCE_BONUS_BY_RANK      = {0.40f, 0.80f, 1.35f};

    // --- BULWARK ---------------------------------------------------------------------------------
    /** Shield Wall r2: the guarded HEAVY multiplier (was GUARD_FRONT_HEAVY 0.20). Spec default x0.10; MOVED to x0.0 — at x0.10 the rank removes ~7% of a floor's incoming (PK1 needs 10%). Range: 0.0-0.10. */
    public static final float   PERK_SHIELD_WALL_HEAVY_GUARD_MULTIPLIER   = 0.0f;
    /** Riposte Drill: extra VULNERABLE turns a riposte applies, total by rank. Range: 1 per rank. */
    public static final float[] PERK_RIPOSTE_DRILL_TURNS_BY_RANK          = {1f, 2f, 3f};
    /** Riposte Drill: extra damage the riposted enemy takes, total by rank. Range: 0.20-0.40 per rank. */
    public static final float[] PERK_RIPOSTE_DRILL_DAMAGE_BY_RANK         = {0.25f, 0.50f, 0.75f};
    /** Plate Carrier: max armour, total by rank. Spec default +20%/rank; MOVED: armour is ~30% of a floor's sustain, so +20% reads ~6%. Range: 0.35-0.55 per rank. */
    public static final float[] PERK_PLATE_CARRIER_ARMOR_BY_RANK          = {0.42f, 0.90f, 1.48f};

    // --- BLADE -----------------------------------------------------------------------------------
    /** Butcher: melee damage (BLADE and BLUNT), total by rank. Range: 0.15-0.30 per rank. */
    public static final float[] PERK_BUTCHER_MELEE_DAMAGE_BY_RANK         = {0.25f, 0.50f, 0.75f};
    /** Bloodletting: max HP a melee kill heals, total by rank. Spec default 3%/rank; MOVED up for PK1 at depth 5 (fewer bodies a floor). Range: 0.03-0.05 per rank. */
    public static final float[] PERK_BLOODLETTING_HEAL_BY_RANK            = {0.04f, 0.085f, 0.135f};

    // --- SCAVENGER -------------------------------------------------------------------------------
    /** Pack Rat: ammo pickups, total by rank. Range: 0.10-0.25 per rank. */
    public static final float[] PERK_PACK_RAT_AMMO_BY_RANK                = {0.15f, 0.30f, 0.45f};
    /** Haggler: shop discount, total by rank. Range: 0.08-0.12 per rank. */
    public static final float[] PERK_HAGGLER_DISCOUNT_BY_RANK             = {0.10f, 0.20f, 0.30f};
    /** Salvager: chance a carrier drops one extra item, total by rank. Range: 0.25-0.40 per rank (<= 1.0). */
    public static final float[] PERK_SALVAGER_EXTRA_DROP_BY_RANK          = {0.25f, 0.50f, 0.75f};

    // --- VITALITY --------------------------------------------------------------------------------
    /** Hardy: max HP, total by rank. Spec default +10%/rank; MOVED into the PK1/PK2 window: each rank must lift the floor (pool + heals) >= 10% while lifting one fight's pool <= 10%. CP1b (D3) sits each increment (0.147 / 0.162 / 0.179) at the LOW edge of the window at depths 5 and 15 to bring VITALITY's R-CARRY down. Range: the window, recomputed when the heal plan moves. */
    public static final float[] PERK_HARDY_MAX_HEALTH_BY_RANK             = {0.147f, 0.309f, 0.488f};
    /** Field Medic: heals, total by rank (valued on a floor's heal supply — nominal, so each total is >= 1.1 x the previous). Spec default +15%/rank; MOVED to the PK1 floor 10.5/22/34.5% (CP1b, D3) to bring VITALITY's R-CARRY down. Range: the PK1 floor up to 0.15 per rank. */
    public static final float[] PERK_FIELD_MEDIC_HEAL_BY_RANK             = {0.105f, 0.22f, 0.345f};

    // --- KEYSTONE MAGNITUDES (B4) — gains, then costs (a cost is the MULTIPLIER, 0.85 = -15%) ----
    /** SCATTERGUN DOCTRINE cost: non-SPREAD damage multiplier. Range: 0.75-0.90. */
    public static final float   KEYSTONE_SCATTERGUN_NON_SPREAD_MULTIPLIER = 0.85f;
    /** DEADEYE cost: clip-size multiplier. Spec default 0.70; MOVED to 0.75 (CP1b, D3) — the Chaingun keeps one more burst a clip (MARKSMAN's R-CARRY); the cost still reads 13% (the Incinerator in S6). Range: 0.70-0.78. */
    public static final float   KEYSTONE_DEADEYE_CLIP_MULTIPLIER          = 0.75f;
    /** PYROMANIAC gain: extra damage burning enemies take from every source. Range: 0.30-0.60. */
    public static final float   KEYSTONE_PYROMANIAC_BURNING_DAMAGE        = 0.50f;
    /** PYROMANIAC cost: max-armour multiplier. Spec default 0.75; MOVED to 0.70 so the cost reads >= 10% somewhere (R-KEYSTONE). Range: 0.60-0.72. */
    public static final float   KEYSTONE_PYROMANIAC_ARMOR_MULTIPLIER      = 0.70f;
    /** DEMOLITIONIST gain: multiplier on barrels the player detonates. Range: 1.5-2.5. */
    public static final float   KEYSTONE_DEMOLITIONIST_BARREL_MULTIPLIER  = 2.0f;
    /** DEMOLITIONIST cost: max-HP multiplier. Spec default 0.80; 0.84 still costs > 10% of a floor's survival. Range: 0.75-0.86. */
    public static final float   KEYSTONE_DEMOLITIONIST_HEALTH_MULTIPLIER  = 0.84f;
    /** STORM CONDUIT gain: fraction of an ENERGY hit that arcs to one adjacent enemy. Spec default 0.50; MOVED up for R-CARRY. Range: 0.45-0.70. */
    public static final float   KEYSTONE_STORM_CONDUIT_ARC_FRACTION       = 0.60f;
    /** STORM CONDUIT cost: BALLISTIC damage multiplier. Range: 0.65-0.85. */
    public static final float   KEYSTONE_STORM_CONDUIT_BALLISTIC_MULTIPLIER = 0.75f;
    /** IRON BULWARK gain: fraction of the blocked damage a guarded hit reflects. Spec default 0.50; MOVED to 1.4 — heavies are a minority of a melee duel's turns, so 0.5 buys ~13% (R-KEYSTONE needs 30% at depth 15). Range: 1.2-1.6. */
    public static final float   KEYSTONE_IRON_BULWARK_REFLECT_FRACTION    = 1.4f;
    /** IRON BULWARK cost: all weapon damage multiplier. Spec default 0.85; 0.90 still costs 11% everywhere. Range: 0.85-0.90. */
    public static final float   KEYSTONE_IRON_BULWARK_DAMAGE_MULTIPLIER   = 0.90f;
    /** BERSERKER gain: max HP a melee kill restores. Spec default 0.08; MOVED to 0.14 — net of its own heal cost, 0.08 lifts the floor ~15% (R-KEYSTONE needs 30%). Range: 0.12-0.16. */
    public static final float   KEYSTONE_BERSERKER_KILL_HEAL              = 0.14f;
    /** BERSERKER gain: melee damage while below BUILD_BERSERK_LOW_HEALTH_FRACTION of max HP. Range: 0.30-0.60. */
    public static final float   KEYSTONE_BERSERKER_LOW_HEALTH_MELEE       = 0.50f;
    /** BERSERKER cost: heal multiplier. Range: 0.60-0.80. */
    public static final float   KEYSTONE_BERSERKER_HEAL_MULTIPLIER        = 0.70f;
    /** QUARTERMASTER gain: extra weapon loadout slots. Range: 1. */
    public static final int     KEYSTONE_QUARTERMASTER_EXTRA_SLOTS        = 1;
    /** QUARTERMASTER gain: shop price multiplier. Range: 0.70-0.80. */
    public static final float   KEYSTONE_QUARTERMASTER_PRICE_MULTIPLIER   = 0.75f;
    /** QUARTERMASTER cost: max-HP multiplier. Spec default 0.90; MOVED to 0.85 — -10% HP costs only ~7% of a floor's survival. Range: 0.80-0.86. */
    public static final float   KEYSTONE_QUARTERMASTER_HEALTH_MULTIPLIER  = 0.85f;
    /** IRONHIDE gain: max-HP multiplier. Spec default 1.40; MOVED to 1.475 — +40% HP is +25% to the pool's hits to die and R-KEYSTONE needs 30%; 1.475 is the lowest value that clears it (CP1b, D3: VITALITY's R-CARRY down). Range: 1.475-1.60. */
    public static final float   KEYSTONE_IRONHIDE_HEALTH_MULTIPLIER       = 1.475f;
    /** IRONHIDE cost: heal multiplier. Range: 0.70-0.85. */
    public static final float   KEYSTONE_IRONHIDE_HEAL_MULTIPLIER         = 0.80f;

    // --- RUNTIME GEOMETRY the perks read (CP2/CP3 seams honour these exactly) ------------------
    /** Point Man: "point-blank" means at most this many tiles. Range: 1-2. */
    public static final int     BUILD_POINT_BLANK_MAX_TILES  = 2;
    /** Lane Discipline: the long-range bonus applies at this many tiles or farther. Range: 4-5. */
    public static final int     BUILD_LONG_RANGE_MIN_TILES   = 4;
    /** BERSERKER: the low-health melee bonus applies below this fraction of max HP. Range: 0.25-0.35. */
    public static final float   BUILD_BERSERK_LOW_HEALTH_FRACTION = 0.30f;

    // --- R-PERK / R-KEYSTONE / R-CARRY THRESHOLDS (B7) -----------------------------------------
    /** PK1 FELT: every rank improves its niche metric by at least this fraction. Range: 0.10. */
    public static final float   PERK_FELT_MIN_GAIN              = 0.10f;
    /** PK2 FOCUSED: no non-VITALITY perk at max rank improves EVERY reference scenario by more than this. Range: 0.10. */
    public static final float   PERK_FOCUSED_MAX_GAIN           = 0.10f;
    /** PK2: a VITALITY perk rank improves no reference scenario by more than this. Range: 0.10. */
    public static final float   PERK_VITALITY_MAX_GAIN_PER_RANK = 0.10f;
    /** R-PERK: the depths every niche is measured at (PK1 holds at each; the breakpoint at any one). Range: R-ROLE depths. */
    public static final int[]   BUILD_PERK_REFERENCE_DEPTHS     = {5, 15};
    /** R-KEYSTONE: a keystone's niche gain is at least this. Range: 0.30. */
    public static final float   KEYSTONE_NICHE_MIN_GAIN         = 0.30f;
    /** R-KEYSTONE: at least one other scenario or metric worsens by at least this. Range: 0.10. */
    public static final float   KEYSTONE_COST_MIN_LOSS          = 0.10f;
    /** R-CARRY: the depth the tag builds are compared at. Range: 15. */
    public static final int     BUILD_CARRY_DEPTH               = 15;
    /** R-CARRY: best / worst tag carry score is at most 1 + this. Range: 0.20. */
    public static final float   BUILD_CARRY_TOLERANCE           = 0.20f;
    /** R-CARRY: guns a build's loadout carries (the Loadout's usable gun slots; QUARTERMASTER adds to it). Range: 2. */
    public static final int     BUILD_KIT_GUN_SLOTS             = 2;
    /** R-CARRY: a reference fight costs at least this many turns (sub-turn clears are floored here). Range: 1. */
    public static final float   BUILD_CARRY_MIN_TURNS           = 1f;
    /**
     * R-CARRY: share of the expected player's perk picks spent in its build's own tag (the rest pivot), so
     * the expected rank count at a depth is round(this x perk picks), capped at the tag's rank capacity.
     * Range: 0.5-0.7.
     */
    public static final float   BUILD_EXPECTED_FOCUS_SHARE      = 0.60f;

    // --- VALUATION MODEL INPUTS (util/BuildValuationModel; each one a stated assumption) -------
    /** Turns between two heavies from one heavy-capable enemy in an attrition fight (commit + recover + melee). Range: 3-5. */
    public static final int     BUILD_HEAVY_PERIOD_TURNS        = 4;
    /** Cardinal lanes a heavy can arrive from (the guard-arc model). Range: 4. */
    public static final int     BUILD_GUARD_LANES               = 4;
    /** Unfaced lanes the Shield Wall +/-90 arc adds to the guard (both sides). Range: 2. */
    public static final int     BUILD_WIDE_GUARD_EXTRA_LANES    = 2;
    /** Seed salt of the valuation's average COMBAT floor (distinct from the R-ROLE supply salt). Range: any long. */
    public static final long    BUILD_SUPPLY_SEED_SALT          = 0xB01D5L;
    /** Share of a floor's incoming damage that arrives as HEAVY hits (the guardable part). Range: 0.25-0.45. */
    public static final float   BUILD_FLOOR_HEAVY_DAMAGE_SHARE  = 0.35f;
    /** Turns per floor a PYRO build stands in fire (its own cone ignites the lanes it fights in — Incinerator.marchShot -> HazardManager.igniteFire). Unverified model input; the sim can measure it. Range: 3-6. */
    // Balance-overhaul order 6 (CP4): 5 -> 5.5. The F7 dial (REACTOR DEPTHS 1.15 -> 1.25) added bodies at d15,
    // diluting fire's share of a PYRO floor's incoming until FIREPROOF r1 read +9.8% (PK1 felt floor 10%).
    public static final float   BUILD_PYRO_FIRE_TURNS_PER_FLOOR = 5.5f;
    /** Share of a floor's bodies a BLADE build kills in melee. Range: 0.3-0.6. */
    public static final float   BUILD_BLADE_MELEE_KILL_SHARE    = 0.45f;
    /** Share of a BLADE build's melee turns fought below BUILD_BERSERK_LOW_HEALTH_FRACTION. Range: 0.15-0.35. */
    public static final float   BUILD_BLADE_LOW_HEALTH_TURN_SHARE = 0.25f;
    /** Seeds the valuation averages one COMBAT floor's planned supply over. Range: 10-30. */
    public static final int     BUILD_SUPPLY_SEED_COUNT         = 20;

    // --- REAL RARITY (B5) + ABILITY PRUNE (B6) ------------------------------------------------
    // OVERRIDES (OVERRIDE CLAUSE): the order-2 R-ABILITY tier CEILINGS (UNCOMMON 6 / RARE 12 / EPIC 20 /
    // LEGENDARY 30, +20% overspend, abilities added greedily while they fit) and WeaponTier's slot counts
    // (0/1/2/3/5, "4 standard + 1 signature") are REPLACED by: a GUARANTEED ability count per tier, rolled
    // in full every time, whose priced total must land inside a BAND around the tier budget (a floor AND a
    // ceiling — a RARE that rolls 3 PP is now a violation). EPIC draws exactly one MAJOR; LEGENDARY draws one
    // MAJOR too and rolls its signature on top of its count. WeaponTier reads these; R-ABILITY asserts them (count, MAJOR,
    // signature, band) over a seeded roll sweep, and asserts every surviving ability is FELT
    // (>= ABILITY_MIN_FELT_PP) at its expected weapon level.
    /** B5: abilities a COMMON weapon rolls (vanilla). Range: 0. */
    public static final int     TIER_ABILITY_COUNT_COMMON        = 0;
    /** B5: abilities an UNCOMMON weapon rolls. Range: 1. */
    public static final int     TIER_ABILITY_COUNT_UNCOMMON      = 1;
    /** B5: abilities a RARE weapon rolls. Range: 2. */
    public static final int     TIER_ABILITY_COUNT_RARE          = 2;
    /** B5: abilities an EPIC weapon rolls (TIER_MAJOR_COUNT_EPIC of them MAJOR). Range: 3. */
    public static final int     TIER_ABILITY_COUNT_EPIC          = 3;
    /** B5: abilities a LEGENDARY weapon rolls IN ADDITION to its signature. Range: 3. */
    public static final int     TIER_ABILITY_COUNT_LEGENDARY     = 3;
    /** B5: MAJOR abilities a COMMON roll draws (none). */
    public static final int     TIER_MAJOR_COUNT_COMMON          = 0;
    /** B5: MAJOR abilities an UNCOMMON roll draws (MAJORs start at EPIC). */
    public static final int     TIER_MAJOR_COUNT_UNCOMMON        = 0;
    /** B5: MAJOR abilities a RARE roll draws (MAJORs start at EPIC). */
    public static final int     TIER_MAJOR_COUNT_RARE            = 0;
    /**
     * B5: MAJOR abilities an EPIC roll draws — exactly one ("EPIC 3, one of them from the MAJOR list"). Exactly,
     * not "at least": two MAJORs + one standard at MAX_WEAPON_LEVEL prices 48.8 PP, past the EPIC ceiling. Range: 1.
     */
    public static final int     TIER_MAJOR_COUNT_EPIC            = 1;
    /**
     * B5: MAJOR abilities a LEGENDARY roll draws besides its signature. DECIDED (CP4; B5 leaves it open): one, so a
     * LEGENDARY is an EPIC's shape PLUS a signature — rarity never loses a MAJOR on the way up. Range: 0-1.
     */
    public static final int     TIER_MAJOR_COUNT_LEGENDARY       = 1;
    /** B5: ability-PP budget of a COMMON weapon (none). */
    public static final float   TIER_ABILITY_PP_BUDGET_COMMON    = 0f;
    /** B5: ability-PP budget of an UNCOMMON weapon (one standard ability). Was 6 (order 2). */
    public static final float   TIER_ABILITY_PP_BUDGET_UNCOMMON  = 12f;
    /** B5: ability-PP budget of a RARE weapon (two standard abilities). Was 12 (order 2). */
    public static final float   TIER_ABILITY_PP_BUDGET_RARE      = 25f;
    /** B5: ability-PP budget of an EPIC weapon (two standard + one MAJOR). Was 20 (order 2). */
    public static final float   TIER_ABILITY_PP_BUDGET_EPIC      = 40f;
    /** B5: ability-PP budget of a LEGENDARY weapon (two standard + one MAJOR + the signature). Was 30 (order 2). */
    public static final float   TIER_ABILITY_PP_BUDGET_LEGENDARY = 60f;
    /** B5: a roll's total PP must lie within budget x (1 -/+ this) — a floor AND a ceiling. Range: 0.20. */
    public static final float   TIER_ABILITY_PP_TOLERANCE        = 0.20f;
    /**
     * B6: the smallest priced PP (GameMath.abilityPowerPoints) an ability may carry at its EXPECTED weapon
     * level and still exist — below it the ability is not felt, and is cut or merged. Range: 8.
     */
    public static final float   ABILITY_MIN_FELT_PP              = 8f;
    /** A4: the R-ABILITY roll sweep's depths, one per early / mid / deep region. Range: one depth per region band. */
    public static final int[]   ABILITY_ROLL_SWEEP_DEPTHS        = {3, 12, 22};
    /** A4: seeds the R-ABILITY roll sweep rolls at every (tier, depth, melee-ness). Range: 30. */
    public static final int     ABILITY_ROLL_SWEEP_SEEDS         = 30;

    // =====================================================================================
    // SECTION 25 — RUN SHAPE & ECONOMY (balance-overhaul order 6: the 25-floor run).
    // The descent is FINITE (RUN_FINAL_DEPTH floors in five regions of five, route/RegionPlan), a boss closes
    // every region, and the depth-RUN_FINAL_DEPTH boss's death is a VICTORY. Everything the finite run re-derives
    // lives here: the BOSS TABLE (F3, F9), the region difficulty dial (F7), the shop economy (F12, F13), the
    // event / mystery currencies (F15) and the run-outcome / S-BOSS / S-SHOP sim bands (F8, F10, F14).
    // =====================================================================================

    // --- BOSS TABLE (F3). One row per boss floor: route/BossTable registers these (index-aligned) and
    // util/BossBalance derives HP / DPT / reward from the row against ExpectedPlayer(depth) — there is no
    // modulo rotation and no per-boss *_DEPTH / *_TARGET_FIGHT_TURNS constant anymore (OVERRIDES the order-6
    // new-game-balancr constants OVERSEER_DEPTH 5 / CORRUPTOR_DEPTH 10 / HELL_BARON_DEPTH 15 and their target
    // turns 22 / 28 / 34). The rotation is the old cycle made explicit, so the Core-ending boss is unchanged.
    /** F3: the boss floors, in descent order. Each must be a boss floor (GameMath.isBossFloor). */
    public static final int[]   BOSS_TABLE_DEPTHS              = {5, 10, 15, 20, 25};
    // F3: the ARCHETYPE on each row is data in route/BossTable.bootstrap (OVERSEER, CORRUPTOR, HELL_BARON,
    // OVERSEER, CORRUPTOR), index-aligned with these arrays.
    /** F3: each row's RULE-1 target fight length (turns), inside the act band [18, 40]. Range: 18–40. */
    public static final float[] BOSS_TABLE_TARGET_FIGHT_TURNS  = {22f, 26f, 30f, 32f, 36f};
    /** F3: each row's RULE-3 survival-ratio target, inside [BOSS_SURVIVAL_CHECK_RATIO_MIN, _MAX]. Range: 0.40–0.70. */
    public static final float[] BOSS_TABLE_SURVIVAL_RATIO      = {0.50f, 0.50f, 0.50f, 0.50f, 0.50f};
    /**
     * F9 (R-BOSS-FAIR v2): the on-curve player's fight must last its row's target +/- this fraction
     * (OVERRIDES the order-6 band [target, 1.5 * target]; the 1.5x upper cap survives as the soft-enrage
     * trigger, BOSS_UPPER_FIGHT_TURNS_MULTIPLIER). Range: 0.25.
     */
    public static final float   BOSS_FIGHT_TURNS_TOLERANCE     = 0.25f;

    // --- RUN-OUTCOME BANDS (F8; sim/BehavioralBands S-FAIR + S-SKILL). They REPLACE S-FAIR's TACTICAL median
    // death depth [8, 14]. Depth-derived. The navigation waiver they rode is DELETED (balance-overhaul order 7: S-NAVIGATION enforced);
    // lines still out of band ride BalanceSchema.waiveRoundPendingBands until a playtest round meets them.
    /** F8: TACTICAL win rate (clears the depth-RUN_FINAL_DEPTH boss) band. Range: 0.20–0.40. */
    public static final float SIM_TACTICAL_WIN_RATE_MIN          = 0.20f;
    public static final float SIM_TACTICAL_WIN_RATE_MAX          = 0.40f;
    /** F8: the depth TACTICAL must reach on at least SIM_TACTICAL_REACH_FRACTION_MIN of seeds. */
    public static final int   SIM_TACTICAL_REACH_DEPTH           = 10;
    public static final float SIM_TACTICAL_REACH_FRACTION_MIN    = 0.60f;
    /** F8: TACTICAL's median death depth over its LOSSES (non-VICTORY runs). Range: 11–18. */
    public static final int   SIM_TACTICAL_LOSS_DEPTH_MIN        = 11;
    public static final int   SIM_TACTICAL_LOSS_DEPTH_MAX        = 18;
    /** F8: NAIVE win rate ceiling. */
    public static final float SIM_NAIVE_WIN_RATE_MAX             = 0.03f;
    /** F8: NAIVE median death depth band. Range: 4–8. */
    public static final int   SIM_NAIVE_DEPTH_MIN                = 4;
    public static final int   SIM_NAIVE_DEPTH_MAX                = 8;
    /** F8 FIRST BOSS: TACTICAL clears it on at least this share of the seeds that REACH it. */
    public static final float SIM_FIRST_BOSS_TACTICAL_CLEAR_MIN  = 0.75f;
    /** F8 FIRST BOSS: NAIVE reaches it on at least this share of seeds ... */
    public static final float SIM_FIRST_BOSS_NAIVE_REACH_MIN     = 0.40f;
    /** ... and clears it on at least this share of seeds (of all seeds). */
    public static final float SIM_FIRST_BOSS_NAIVE_CLEAR_MIN     = 0.15f;

    // --- SHOP ECONOMY (F12, F13). R-CREDITS v2: for every region, the modelled on-curve credit income per floor
    // divided by the price of the NEEDED BUNDLE per floor lands in [CREDITS_V2_RATIO_MIN, MAX] — a surplus, so
    // the shop is a place to spend (owner D4). NEEDED BUNDLE per floor = one LEVEL UP rung (order 1)
    // + CREDITS_NEEDED_CONSUMABLES_PER_FLOOR consumables at SHOP_SMALL_BUY_POWER_POINTS
    // + one tier upgrade per region (to the region's top drop tier) amortised over its floors.
    /** F12: R-CREDITS v2 band on income / needed bundle, per region. Range: 1.10–1.40. */
    public static final float CREDITS_V2_RATIO_MIN               = 1.10f;
    public static final float CREDITS_V2_RATIO_MAX               = 1.40f;
    /** F12: consumables the on-curve player typically needs per floor (the NEEDED BUNDLE's supply part). */
    public static final float CREDITS_NEEDED_CONSUMABLES_PER_FLOOR = 1.5f;
    /** F13: UAC Fabricators on every floor that is not a SHOP node (the guaranteed LEVEL UP rung rides it). */
    public static final int   SHOP_MACHINES_PER_FLOOR            = 1;
    /** F13: Fabricators on a SHOP node's floor (the two-machine upgrade / supply bias applies). */
    public static final int   SHOP_NODE_MACHINE_COUNT            = 2;
    /** F13: price multiplier on every offer a SHOP node's machines roll (stacks with the build's Haggler). Range: 0.80. */
    public static final float SHOP_NODE_PRICE_MULTIPLIER         = 0.80f;
    // --- S-BOSS (F10): boss CONSISTENCY in play, per BossTable row, over the TACTICAL seeds that reach it.
    /** F10: a boss's TACTICAL clear rate band (the first boss's floor is SIM_FIRST_BOSS_TACTICAL_CLEAR_MIN). */
    public static final float SIM_BOSS_CLEAR_RATE_MIN            = 0.50f;
    public static final float SIM_BOSS_CLEAR_RATE_MAX            = 0.90f;
    /** F10: the fight-length coefficient of variation (std / mean of turns, first exchange to kill) ceiling. */
    public static final float SIM_BOSS_MAX_FIGHT_TURNS_COV       = 0.30f;
    /** F10: the median player health fraction at the kill band (the R-BOSS survival ratio, felt). */
    public static final float SIM_BOSS_KILL_HEALTH_MIN           = 0.30f;
    public static final float SIM_BOSS_KILL_HEALTH_MAX           = 0.60f;
    /** F10: kills a row needs before its CoV / median-health lines read as a measurement. */
    public static final int   SIM_BOSS_MIN_SAMPLE                = 10;
    /** F14 S-SHOP: TACTICAL buys at least this many offers per Fabricator visit (per floor visited). */
    public static final float SIM_SHOP_MIN_OFFERS_PER_VISIT      = 1.5f;
    /** F14 S-SHOP: TACTICAL's median unspent credits when a lost run ends, in floors of modelled income. */
    public static final float SIM_SHOP_MAX_UNSPENT_FLOORS        = 1.5f;

    // --- EVENTS AND MYSTERIES IN LADDER CURRENCIES (F15). Every reward / cost is a weapon LEVEL (<= d+1), a PERK
    // RANK of a carried tag, a HEAL / ARMOUR fraction, or CREDITS in floors of a COMBAT floor's income at the depth
    // (route/EventEffect); route/FacilityEvents and route/MysteryOutcome carry the data, RouteEconomicsModel
    // prices it in power points, and R-MYSTERY-EV v2 + R-EVENT-VALUE band it.
    /** F15: credits a small event payout grants (riding a level / perk / heal), in floors of income. */
    public static final float EVENT_CREDIT_FLOORS_SMALL          = 0.75f;
    /**
     * F15: credits the all-credits event payout grants (the beacon "strip it for parts" choice), in floors of
     * income. An EVENT floor replaces a COMBAT floor, so its best choice must be worth that floor's WHOLE supply
     * (ammo + heal + credits, R-EVENT-VALUE) — about three floors of credits at depth 1.
     */
    public static final float EVENT_CREDIT_FLOORS_LARGE          = 3.00f;
    /** F15: credits riding the stash gamble's weapon level (paid only when the gamble pays). */
    public static final float EVENT_STASH_CREDIT_FLOORS          = 2.00f;
    /** F15: heal fraction riding the stash gamble's weapon level. */
    public static final float EVENT_GAMBLE_HEAL_FRACTION         = 0.20f;
    /** F15: HP fraction a lost stash gamble bites. */
    public static final float EVENT_GAMBLE_FAIL_HEALTH_FRACTION  = 0.20f;
    /** F15: the surviving tech's armour patch (fraction of max armour). */
    public static final float EVENT_TECH_ARMOUR_FRACTION         = 1.00f;
    /** F15: the shrine's surge (fraction of max HP). */
    public static final float EVENT_SHRINE_HEAL_FRACTION         = 1.00f;
    /** F15 MYSTERY: VAULT JACKPOT pays a weapon level and this many floors of credits on the way out. */
    public static final float MYSTERY_REWARD_VAULT_CREDIT_FLOORS = 0.50f;
    /** F15 MYSTERY: TRAP GAUNTLET pays this many floors of credits for surviving the hazards. */
    public static final float MYSTERY_REWARD_TRAP_CREDIT_FLOORS  = 1.50f;
    /** F15 MYSTERY: AMBUSH (an ELITE floor) pays this many floors of credits on top. */
    public static final float MYSTERY_REWARD_AMBUSH_CREDIT_FLOORS = 0.50f;
    /** F15 MYSTERY: LORE SIGNAL (a med-bay) pays this many floors of credits (salvaged intel). */
    public static final float MYSTERY_REWARD_LORE_CREDIT_FLOORS  = 0.25f;
    /** F15 MYSTERY: MALFUNCTION costs this many floors of credits (a failing sector eats a toll). */
    public static final float MYSTERY_REWARD_MALFUNCTION_CREDIT_COST_FLOORS = 0.25f;
    /**
     * R-MYSTERY-EV v2 (F15): the table's outcome EVs must SPREAD — their weighted standard deviation is at least
     * this fraction of a standard COMBAT node's EV (a mystery is a gamble, never a safe COMBAT in disguise).
     */
    public static final float ROUTE_MYSTERY_MIN_SPREAD_FRACTION  = 0.25f;
    /**
     * F15: power points ONE weapon level is worth in the route model (permanent progress). A level is a full
     * ladder rung (R-LADDER L4: >= 25% DPT on its floor), so it is priced like one perk pick — never at the
     * shop's deliberately cheap rung price. Range: 8–16.
     */
    public static final float ROUTE_WEAPON_LEVEL_POWER_POINTS    = EXPECTED_PERK_POWER_PP_PER_PICK;
    /** R-EVENT-VALUE (F15): every EVENT's best choice is worth at least this x a COMBAT floor's SUPPLY (PP). */
    public static final float EVENT_BEST_CHOICE_MIN_SUPPLY_RATIO = 1.00f;

    // =====================================================================================
    // SECTION 26 — RELICS (relics-run-changing-artifacts): rare run-changing artifacts, priced in the build contract
    // A relic is a run-scoped passive that folds into the SAME BuildStats aggregate perks and keystones use
    // (PerkModifier + PerkEffect -> PlayerBuild). Holding, offers, trigger runtime, rarity bands, valuation
    // assumptions and sim probe numbers live here. The per-relic RELIC_<ID>_<KIND> magnitudes follow the offers block. Starting values are tunable inside the bands (D7); the bands and weights are the design.
    // =====================================================================================

    // --- HOLDING & OFFERS --------------------------------------------------------------------
    /** R2: a run holds at most this many relics (a safety cap; about five are expected). */
    public static final int     RELIC_MAX_HELD                = 8;
    /** The most cards any offer shows (sizes the pre-allocated RelicOffer). */
    public static final int     RELIC_OFFER_MAX_CARDS         = 3;
    /** R7: offers that may wait their turn, first in first out. */
    public static final int     RELIC_PENDING_OFFER_CAPACITY  = 2;
    /** R6: salt xor-ed into the run seed before GameMath.floorSeed, so relic rolls never mirror other seeded rolls. */
    public static final long    RELIC_OFFER_SEED_SALT         = 0x52454C4943L;
    /** R4 RUN_START: cards in the opening COMMON offer. */
    public static final int     RELIC_START_OFFER_CARDS       = 3;
    /** R4 BOSS: cards in a boss offer. */
    public static final int     RELIC_BOSS_OFFER_CARDS        = 3;
    /** R4 MYSTERY_CACHE / QUARANTINE_LOCKER: cards in a node offer. */
    public static final int     RELIC_NODE_OFFER_CARDS        = 2;
    /** R4 BOSS: per-card rarity weights {COMMON, RARE, LEGENDARY}, one row per offering boss (bosses 1-4). Each row sums to 1. */
    public static final float[][] RELIC_BOSS_OFFER_RARITY_WEIGHTS = {
        {0.50f, 0.40f, 0.10f}, {0.35f, 0.45f, 0.20f}, {0.20f, 0.50f, 0.30f}, {0.10f, 0.50f, 0.40f}
    };
    /** R4 MYSTERY_CACHE: per-card rarity weights {COMMON, RARE}. Sums to 1. */
    public static final float[]   RELIC_MYSTERY_OFFER_RARITY_WEIGHTS = {0.60f, 0.40f};
    /**
     * Weight of the RELIC_CACHE mystery outcome (mirrored into RouteMapConstants). The spec's tuning floor was 4,
     * but R-MYSTERY-EV (weighted table EV / combat EV, worst at depth 1) is 1.3022 at 4 against a 1.30 ceiling: the
     * pre-relic table already sits at 1.2917 and RELIC_CACHE (supply-cache floor + one relic offer, about one perk
     * rank) is priced ABOVE the 1.30 line, so any weight above ~3.1 breaks it. 3 is the largest integer that passes.
     */
    public static final int     MYSTERY_WEIGHT_RELIC_CACHE    = 3;

    // --- RUNTIME -----------------------------------------------------------------------------
    /** R9: the share of the centre blast fraction the four neighbours of a door blast take. */
    public static final float   RELIC_BLAST_RING_SHARE                = 0.60f;
    /** R12: damage-taken multiplier floor (the product of relic multipliers never reads below this). */
    public static final float   RELIC_DAMAGE_TAKEN_MULTIPLIER_MIN     = 0.50f;
    /** R12/R13: enemy-health multiplier floor. */
    public static final float   RELIC_ENEMY_HEALTH_MULTIPLIER_MIN     = 0.50f;

    // --- CATALOG MAGNITUDES: RELIC_<RELIC_ID>_<PERK_MODIFIER_KIND> (CP3). Gain kinds ADD; cost kinds and
    //     DAMAGE_TAKEN / ENEMY_HEALTH are MULTIPLIERS (0.88 = take 12% less). Boolean kinds carry no constant.
    //     CP5 tuned them into their bands (D7) — see the RELICS table in BalanceReport.
    // COMMON
    public static final float RELIC_FIELD_DRESSING_FLOOR_ARRIVAL_HEAL        = 0.13f;
    public static final float RELIC_SPARE_PLATES_FLOOR_ARRIVAL_ARMOR         = 0.22f;
    public static final float RELIC_BANDOLIER_FLOOR_ARRIVAL_AMMO             = 0.38f;
    public static final float RELIC_BOUNTY_CHIP_KILL_CREDIT_BONUS            = 0.21f;
    public static final float RELIC_THRESHOLD_STIM_DOOR_OPEN_HEAL            = 0.04f;
    public static final float RELIC_SALVAGE_HOOK_CARRIER_EXTRA_DROP          = 1.00f;
    public static final float RELIC_MEDICS_GLOVE_HEAL_BONUS                  = 0.13f;
    public static final float RELIC_RECOIL_SPRING_KILL_AMMO_REFUND           = 0.50f;
    public static final float RELIC_RECOIL_SPRING_AMMO_PICKUP_BONUS          = 0.35f;
    public static final float RELIC_DOG_TAGS_XP_MULTIPLIER                   = 0.42f;
    public static final float RELIC_SPOTTERS_LENS_FULL_HEALTH_TARGET_DAMAGE  = 0.01f;
    public static final float RELIC_SPOTTERS_LENS_DAMAGE_TAKEN_MULTIPLIER    = 0.95f;
    // RARE
    public static final float RELIC_BREACHING_CHARGE_DOOR_BLAST              = 0.42f;
    public static final float RELIC_VAMPIRIC_CORE_KILL_HEAL                  = 0.02f;
    public static final float RELIC_SALVAGE_PLATING_KILL_ARMOR               = 0.03f;
    public static final float RELIC_GRIEF_ENGINE_CORPSE_BLAST                = 0.15f;
    public static final float RELIC_GRIEF_ENGINE_KILL_ARMOR                  = 0.03f;
    public static final float RELIC_KEVLAR_WEAVE_DAMAGE_TAKEN_MULTIPLIER     = 0.92f;
    public static final float RELIC_CULLING_SIGNAL_ENEMY_HEALTH_MULTIPLIER   = 0.95f;
    public static final float RELIC_CHOKE_BORE_SPREAD_DAMAGE                 = 0.06f;
    public static final float RELIC_CHOKE_BORE_KILL_ARMOR                    = 0.03f;
    public static final int   RELIC_CHOKE_BORE_KNOCKBACK_BONUS               = 1;
    public static final float RELIC_CAPACITOR_BANK_ARC_CHAIN_MULTIPLIER      = 0.20f;
    public static final int   RELIC_CAPACITOR_BANK_ARC_CHAIN_JUMP            = 1;
    public static final float RELIC_SHAPED_CASINGS_EXPLOSIVE_DAMAGE          = 0.04f;
    public static final float RELIC_SHAPED_CASINGS_OWN_EXPLOSION_REDUCTION   = 0.90f;
    public static final float RELIC_BONE_SAW_MELEE_DAMAGE                    = 0.25f;
    public static final float RELIC_BONE_SAW_MELEE_KILL_HEAL                 = 0.03f;
    // LEGENDARY
    public static final float RELIC_HUNGER_LOOP_KILL_HEAL                    = 0.02f;
    public static final float RELIC_HUNGER_LOOP_KILL_ARMOR                   = 0.02f;
    public static final float RELIC_CHAIN_DETONATOR_CORPSE_BLAST             = 0.45f;
    public static final float RELIC_CHAIN_DETONATOR_DOOR_BLAST               = 0.70f;
    public static final int   RELIC_FOURTH_HOLSTER_EXTRA_LOADOUT_SLOT        = 1;
    public static final float RELIC_FOURTH_HOLSTER_FLOOR_ARRIVAL_AMMO        = 0.12f;
    public static final float RELIC_DEADMAN_TRIGGER_LOW_HEALTH_DAMAGE        = 0.90f;
    public static final float RELIC_ATROPHY_FIELD_ENEMY_HEALTH_MULTIPLIER    = 0.93f;
    public static final float RELIC_OVERCLOCKED_SERVOS_ALL_WEAPON_DAMAGE     = 0.25f;
    public static final int   RELIC_OVERCLOCKED_SERVOS_RANGE_BONUS           = 1;
    public static final float RELIC_COUNTERWEIGHT_GUARD_REFLECT              = 0.25f;
    public static final int   RELIC_ASBESTOS_LINING_BURN_TURNS               = 1;
    public static final float RELIC_ASBESTOS_LINING_FIRE_DAMAGE              = 0.07f;
    // CURSED (gain kinds add; cost kinds are MULTIPLIERS, e.g. 0.60 = -40%; DAMAGE_TAKEN / ENEMY_HEALTH above 1 are the cost)
    public static final float RELIC_BLOOD_PACT_ALL_WEAPON_DAMAGE             = 0.55f;
    public static final float RELIC_BLOOD_PACT_DAMAGE_TAKEN_MULTIPLIER       = 1.15f;
    public static final float RELIC_FEVER_CORE_KILL_HEAL                     = 0.065f;
    public static final float RELIC_FEVER_CORE_HEAL_COST                     = 0.50f;
    public static final float RELIC_VOLATILE_RIG_DOOR_BLAST                  = 1.00f;
    public static final float RELIC_VOLATILE_RIG_CORPSE_BLAST                = 0.50f;
    public static final float RELIC_VOLATILE_RIG_MAX_ARMOR_COST              = 0.70f;
    public static final float RELIC_TITHE_ENGINE_KILL_CREDIT_BONUS           = 0.40f;
    public static final float RELIC_TITHE_ENGINE_XP_MULTIPLIER               = 0.40f;
    public static final float RELIC_TITHE_ENGINE_ENEMY_HEALTH_MULTIPLIER     = 1.15f;
    public static final float RELIC_IRON_LUNG_DAMAGE_TAKEN_MULTIPLIER        = 0.84f;
    public static final float RELIC_IRON_LUNG_ALL_WEAPON_DAMAGE_COST         = 0.92f;
    public static final float RELIC_DEATHS_DOOR_LOW_HEALTH_DAMAGE            = 1.70f;
    public static final float RELIC_DEATHS_DOOR_MAX_HEALTH_COST              = 0.85f;

    // --- RARITY BANDS (R-RELIC / R-RELIC-CURSE): a relic's carry lift (or supply ratio) must land in its band ---
    /** Depths at which every relic is priced (early, mid, late). */
    public static final int[]   RELIC_REFERENCE_DEPTHS                = {5, 15, 25};
    public static final float[] RELIC_CARRY_BAND_COMMON               = {1.03f, 1.08f};
    public static final float[] RELIC_CARRY_BAND_RARE                 = {1.06f, 1.13f};
    /** Top edge 1.22, widened from 1.20 by the CP5 hub licence (one band, <= 0.02): SECOND HEART's Second Wind flag alone prices 1.205 at depth 5. */
    public static final float[] RELIC_CARRY_BAND_LEGENDARY            = {1.10f, 1.22f};
    public static final float[] RELIC_CARRY_BAND_CURSED               = {1.05f, 1.18f};
    public static final float[] RELIC_SUPPLY_BAND_COMMON              = {1.06f, 1.20f};
    public static final float[] RELIC_SUPPLY_BAND_RARE                = {1.15f, 1.35f};
    public static final float[] RELIC_SUPPLY_BAND_CURSED              = {1.20f, 1.60f};
    /** R-RELIC-CURSE: a cursed relic's worst niche loss is at least this at some reference depth. */
    public static final float   RELIC_CURSED_MIN_LOSS                 = 0.10f;
    /** Carry lift one perk rank is worth at depth 15 (MARKSMAN 1.751 over 7 ranks); anchors relic power points. */
    public static final float   RELIC_PERK_RANK_EQUIVALENT_LIFT       = 1.08f;
    /** R-RELIC-SOURCE: tolerance on the measured per-rank lift vs RELIC_PERK_RANK_EQUIVALENT_LIFT. */
    public static final float   RELIC_PERK_RANK_EQUIVALENT_TOLERANCE  = 0.03f;

    // --- VALUATION ASSUMPTIONS (stated once, printed by BalanceReport; AS16) -----------------
    /** Share of a floor's bodies a kill-triggered relic is assumed to fire on. */
    public static final float   RELIC_VALUATION_KILL_SHARE            = 0.60f;
    /** Doors the player is assumed to open per floor. */
    public static final int     RELIC_VALUATION_DOORS_PER_FLOOR       = 3;
    /** Share of fights assumed to open through a door the player just opened. */
    public static final float   RELIC_VALUATION_DOOR_FIGHT_SHARE      = 0.30f;
    /** Share of a corpse burst's fraction assumed to land on remaining group members. */
    public static final float   RELIC_VALUATION_CORPSE_ADJACENCY      = 0.50f;
    /** Share of QUARANTINE_LOCKER events assumed to be broken open (vs sold). */
    public static final float   RELIC_VALUATION_EVENT_TAKE_SHARE      = 0.50f;
    /** Share of RUN_START offers assumed taken (every run holds the start relic). */
    public static final float   RELIC_VALUATION_START_TAKE_SHARE      = 1.0f;
    /**
     * The quarantine_locker event's assumed weight in the EVENT pool (CP4): "one more equal-weight event" — every
     * shipped FacilityEvent carries the builder default weight 1, and CP6 registers the locker the same way.
     * Read by RelicValuationModel.nodeOfferRatePerFloor until the locker is registered (then its own weight is read).
     */
    public static final float   RELIC_VALUATION_LOCKER_EVENT_WEIGHT   = 1f;

    // --- SIMULATOR (S-RELIC) -----------------------------------------------------------------
    /** Seeds per relic probe row. */
    public static final int     SIM_RELIC_PROBE_SEED_COUNT            = 120;
    /** SR1: a rarity row's mean depth / no-relic baseline may not fall below 1 - this. */
    public static final float   SIM_RELIC_ROW_TRAP_TOLERANCE          = 0.10f;
    /** SR1/SR2: a probe row's mean depth ratio may not exceed 1 + this ("nothing is mandatory"). */
    public static final float   SIM_RELIC_ROW_MAX_LIFT                = 0.40f;
    /** SR2: the CURSED row may fall this far below the baseline. */
    public static final float   SIM_RELIC_CURSED_ROW_TOLERANCE        = 0.15f;
    /** SR4: a single relic's mean depth ratio may not fall below 1 - this. */
    public static final float   SIM_RELIC_PER_RELIC_TRAP_TOLERANCE    = 0.25f;
    /** SR4: a single relic's mean depth ratio may not exceed 1 + this. */
    public static final float   SIM_RELIC_PER_RELIC_MAX_LIFT          = 0.60f;
    /** SR4/SR5: probe runs a non-affine relic needs before its ratio is judged. */
    public static final int     SIM_RELIC_MIN_RUNS_PER_RELIC          = 10;

    // =====================================================================================
    // SECTION 27 — FLOOR TWISTS (floor-twists): rule-changing modifiers on COMBAT nodes
    // A twist is a FLOOR_TWIST-scoped row of the route NodeAffixRegistry, rolled onto an eligible COMBAT node
    // at map-gen and shown on the card before the player commits. SALVAGE and HUNTED change the floor's threat
    // and loot; BLACKOUT changes only the dormant-WAKE perception (its dark/no-map half is cosmetic and lives in
    // RenderConstants/HudConstants). Priced by R-TWIST (T1-T3) against plain COMBAT, banded by S-TWIST.
    // =====================================================================================

    // --- ROLL ----------------------------------------------------------------------------------
    /** R2: share of eligible COMBAT nodes (depth >= a twist's minimum) that carry one twist. */
    public static final float   TWIST_ROLL_CHANCE_COMBAT              = 0.30f;
    /** R2: relative pick weight of BLACKOUT among the eligible twists. */
    public static final float   TWIST_WEIGHT_BLACKOUT                 = 1.0f;
    /** R2: relative pick weight of SALVAGE among the eligible twists. */
    public static final float   TWIST_WEIGHT_SALVAGE                  = 1.0f;
    /**
     * R2: relative pick weight of HUNTED among the eligible twists. Tuned 1.0 -> 0.5 at CP3: HUNTED is priced as a
     * 1.78x-threat floor, so the R-TRAJECTORY DEADLIEST walker takes every one it sees and its XP buys a level too
     * many (depth coupling 1.72 > 1.65 at weight 0.75+; 1.58 at 0.5). The band does not move — the twist does.
     */
    public static final float   TWIST_WEIGHT_HUNTED                   = 0.5f;
    /** R3: the shallowest depth BLACKOUT may roll on (floor one is the teaching floor). */
    public static final int     TWIST_MIN_DEPTH_BLACKOUT              = 2;
    /** R3: the shallowest depth SALVAGE may roll on. */
    public static final int     TWIST_MIN_DEPTH_SALVAGE               = 2;
    /** R3: the shallowest depth HUNTED may roll on (an Iron Stalker on floor two is too sharp a spike). */
    public static final int     TWIST_MIN_DEPTH_HUNTED                = 4;

    // --- BLACKOUT (R7d) — the dormant-WAKE check only; attack ranges and LOS for attacks unchanged ---
    /** R7d: proximity radius (tiles) that wakes a dormant enemy on a BLACKOUT floor (default ALERT_RADIUS_TILES). */
    public static final int     TWIST_BLACKOUT_WAKE_RADIUS_TILES      = 3;   // CP8: 2 flipped S-RELIC SR3 (sim noise at 1.01); 3/6 restores it
    /** R7d: wake-by-sight range (tiles) on a BLACKOUT floor (default LOS_MAX_RANGE_TILES, wake check only). */
    public static final int     TWIST_BLACKOUT_WAKE_SIGHT_TILES       = 6;

    // --- SALVAGE (R8) ---------------------------------------------------------------------------
    /** R8: threat multiplier on the COMBAT floor's budget (through the same EnemyBudgetOverride SWARM uses). */
    public static final float   TWIST_SALVAGE_BUDGET_MULT             = 1.30f;
    /** R8: extra owned-ammo boxes scattered over the floor. */
    public static final int     TWIST_SALVAGE_AMMO_BOXES              = 4;
    /** R8: extra '+' stim packs scattered over the floor. */
    public static final int     TWIST_SALVAGE_STIMS                   = 1;
    /** R8: extra 'a' armour shards scattered over the floor. */
    public static final int     TWIST_SALVAGE_ARMOUR_SHARDS           = 1;

    // --- HUNTED (R9-R12) ------------------------------------------------------------------------
    /** R9a: completed player turns on the floor before the trail hunter is printed. */
    public static final int     TWIST_HUNTED_ARRIVAL_TURNS            = 40;
    /** R9a: how many turns before the arrival the warning fires. */
    public static final int     TWIST_HUNTED_WARNING_TURNS            = 5;
    /** R9a: the arrival tile must be at least this far (Manhattan tiles) from the player for the hunter to spawn. */
    public static final int     TWIST_HUNTED_MIN_SPAWN_DISTANCE_TILES = 5;
    /**
     * R12: owned-ammo boxes the trail hunter drops on death, on top of its ordinary drops. Tuned 3 -> 10 at CP3:
     * the hunter is a MINI_ELITE worth ~0.77 of a COMBAT floor's TP, and R-TWIST T1 prices it as always arriving
     * (AS16), so the bounty must pay a 1.78x threat into the [1.0, 1.2] premium band (10 boxes -> 1.02-1.03).
     */
    public static final int     TWIST_HUNTED_BOUNTY_AMMO_BOXES        = 10;
    /**
     * T3: the declared extra threat of the hunter as a share of a COMBAT floor's Threat-Point budget, priced at
     * FULL (as if it always arrives — AS16). R-TWIST T3 fails if the hunter's real per-archetype TP at any audited
     * depth, over the COMBAT floor's TP budget, exceeds this: the ledger may not under-price the hunter.
     */
    public static final float   TWIST_HUNTED_THREAT_SHARE             = 0.78f;   // CP3: real IRON_STALKER share peaks 0.765 at depth 4

    // --- AUDIT (R-TWIST) + SIMULATOR (S-TWIST) -------------------------------------------------
    /** T2: a threat-neutral twist's total EV must sit within +-this share of plain COMBAT's at every audited depth. */
    public static final float   TWIST_NEUTRAL_EV_TOLERANCE            = 0.10f;
    /** ST1: a twist's floor-death rate may not exceed this x the plain-COMBAT floor-death rate in the same depths. */
    public static final float   TWIST_SIM_MAX_DEATH_RATE_RATIO        = 1.50f;
    /** ST1: floors a twist needs in the TACTICAL row before ST1 judges it (fewer = reported n/a). */
    public static final int     TWIST_SIM_MIN_FLOORS                  = 60;
}
