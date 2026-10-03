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
     * the player's power multiplier by LEVEL_UP_BUDGET_PP power points per level gained. The boss
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
    // A weapon TIER-UPGRADE prices on the ABILITY-PP budget its destination tier unlocks
    // (TIER_ABILITY_PP_BUDGET_*, SECTION 15) — the marquee value is the ability slot the tier buys.
    // A PLAYER-ABILITY boon prices on the level-up card's own PP (LEVEL_UP_BUDGET_PP), since a shop
    // boon is a paid acquisition of the same budget-equal card. Both are read directly from those
    // sections in DefaultShopOfferSource — no separate price constants here.
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
     * Depth the simulator stops at. Above the S-FAIR target band, so a run that survives to the
     * ceiling is a genuine outlier rather than an artefact of stopping early.
     */
    public static final int   SIM_DEPTH_CEILING           = 20;
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
    /** Vitality fraction at which TACTICAL breaks off a fight and heads for the stairs (no heals left). */
    public static final float SIM_TACTICAL_RETREAT_FRACTION = 0.35f;
    /** Vitality/ammo/heal fraction below which a turn counts as taken in a RESOURCE CRISIS. */
    public static final float SIM_RESOURCE_CRISIS_FRACTION = 0.25f;
    /** Telegraphed incoming damage (as a fraction of remaining vitality) that makes TACTICAL guard. */
    public static final float SIM_TACTICAL_GUARD_DAMAGE_FRACTION = 0.20f;

    // --- C. S-GATE: the boss-cheese regression (the headline guarantee) ------------------
    /**
     * Fraction of HOARDER-START-WEAPON seeds allowed to clear the FIRST boss. Zero, by contract:
     * the starting loadout losing to the first boss is what R-BOSS-GATE proves on paper, and this
     * band proves it in play. The original bug (a starting shotgun that could grind a boss down)
     * can never return silently.
     */
    public static final float SIM_GATE_MAX_CLEAR_FRACTION = 0.0f;

    // --- D. S-FAIR: deaths land in the intended window, and read as fair -----------------
    /** Median death depth the TACTICAL policy should reach — the intended run length. */
    public static final int   SIM_TARGET_DEPTH_MIN        = 8;
    public static final int   SIM_TARGET_DEPTH_MAX        = 14;
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
     */
    public static final int   SIM_SKILL_DEPTH_GAP_MIN     = 3;

    // --- F. S-ECONOMY: the played economy matches the modelled one -----------------------
    /**
     * S-ECONOMY (re-based by balance-overhaul order 2): the mean share of a floor's PLANNED ammo units the
     * player actually picks up, over the floors they EXIT. Below the minimum, the planner puts supply
     * where play does not reach it; above the maximum, unplanned income is leaking in.
     */
    public static final float SIM_ECONOMY_SUPPLY_SHARE_MIN = 0.75f;
    public static final float SIM_ECONOMY_SUPPLY_SHARE_MAX = 1.10f;
    /** S-SUPPLY (balance-overhaul order 2, AS2): TACTICAL's mean health fraction leaving a COMBAT floor. */
    public static final float SIM_SUPPLY_EXIT_HEALTH_MIN = 0.55f;
    public static final float SIM_SUPPLY_EXIT_HEALTH_MAX = 0.80f;
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
    /** OVERCLOCKED vault premium: extra owned-ammo boxes that pay for its raised threat. */
    public static final int   AFFIX_OVERCLOCKED_VAULT_AMMO_BOXES = 1;
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

    // --- F. EVENT-NODE PAYOFFS (the average an event choice hands out) -------------------
    /** XP awarded by an event choice at its small tier. */
    public static final int   EVENT_XP_SMALL              = 60;
    /** XP awarded by an event choice at its large tier. */
    public static final int   EVENT_XP_LARGE              = 200;
    /** Ammo boxes an event choice hands out (spread across owned ammo types, cache-style). */
    public static final int   EVENT_AMMO_BOXES            = 6;
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
    /** Share of an EVENT's payoff any single choice delivers, averaged over the v1 choice catalogue. */
    public static final float ROUTE_EVENT_EXPECTED_CHOICE_SHARE     = 0.40f;

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
    public static final float ROUTE_TRAJECTORY_XP_PACE_MAX = 2.95f;
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

    /** Expected fraction of each level's LEVEL_UP_BUDGET_PP spent on OFFENCE by the on-curve player (the boss derivation's 0.5). Range: 0.3–0.6. */
    public static final float LADDER_EXPECTED_OFFENCE_BUDGET_FRACTION = 0.50f;
    /**
     * Expected fraction of each level's LEVEL_UP_BUDGET_PP bought as FLAT max-HP/armour eHP (R9). A quarter:
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
    public static final float NODE_SUPPLY_CACHE_CREDITS       = 1.20f;
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

    // --- S-SWITCH (CP7, A8) ------------------------------------------------------------------------
    /**
     * S-SWITCH: TACTICAL's matchup-driven (C4 SWITCH-hint) weapon switches per played COMBAT floor must be
     * at least this. A fight-level count, not depth-derived, so the navigation waivers do not cover it.
     */
    public static final float SIM_SWITCH_MIN_PER_COMBAT_FLOOR = 1.0f;

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
}
