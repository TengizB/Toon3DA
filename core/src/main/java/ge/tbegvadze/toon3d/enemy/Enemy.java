package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.status.StatusEffect;
import ge.tbegvadze.toon3d.status.StatusHost;
import ge.tbegvadze.toon3d.status.StatusResistance;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.IntentConstants;

import java.util.EnumMap;

/** Runtime state for a single enemy instance. Pure data + lightweight behavior helpers. */
public class Enemy implements StatusHost {

    // Stable integer identity for ability tracking (e.g. Rhythm target ID).
    // Incrementing at construction time guarantees uniqueness within a JVM session.
    private static int nextId = 0;
    private final  int id     = nextId++;

    /** Returns the stable identity integer for this enemy instance. Used by Rhythm ability. */
    public int getId() { return id; }

    /** Resets the ID counter; call when starting a completely new game run. */
    public static void resetIdCounter() { nextId = 0; }

    public final EnemyType type;

    /** Authoritative tile position (integer grid coordinates). */
    public int tileColumn;
    public int tileRow;

    /**
     * Implicit cardinal FACING (strategy-combat-order-6): the unit direction this enemy last moved or
     * attacked along, used for the player's backstab/flank bonus. (0,0) until the enemy first moves or
     * attacks — {@link GameMath#isAttackerBehindFacing} treats that as "no backstab" (safe default).
     * Maintained by EnemyManager (set on every commitMove and on each attack toward the player).
     */
    public int facingColumn = 0;
    public int facingRow    = 0;

    public int        health;
    /** Effective max HP for this instance — may exceed type.maxHealth() on deeper floors. */
    public int        maxHealth;
    /**
     * Flat damage reduction from physical armour (0 = unarmoured), subtracted after Block in
     * {@link #applyDamage}. Distinct from the ARMOR_PIERCE ability, which pierces Block (the
     * enemy's active damage-absorbing shield), not this flat layer.
     * Default 0; depth-scaled enemies may receive a non-zero value at spawn time.
     *
     * <p>The Cinderforge Colossus's COOLING CRUST rides on this same field rather than on a parallel
     * one: each stack adds {@link EnemyConstants#CINDERFORGE_CRUST_ARMOR_PER_STACK} here while it
     * stands and removes it again on {@link #shatterCrust}, so the crust automatically obeys the
     * existing mitigation order and every armour-aware system sees it for free.
     */
    public int        armor = 0;
    /**
     * Temporary damage-absorbing buffer (strategy-combat-order-3). Incoming damage is subtracted
     * from Block before it reaches HP (and before flat armor) — see {@link #applyDamage}. Gained
     * when the enemy EXECUTEs a committed DEFEND ({@link #gainBlock}); zeroed after
     * {@link #decayBlock} counts down (driven by the StatusEffectController tick). Default 0.
     * Direct HP loss from status DoT ({@link #applyDoTDamage}) deliberately BYPASSES Block.
     */
    public int        block = 0;
    /**
     * World turns of life remaining on the current Block before it expires. Set by {@link #gainBlock};
     * decremented once per world turn by {@link #decayBlock}. Meaningless while {@code block == 0}.
     */
    public int        blockDecayTurns = 0;
    /**
     * Depth-scaling multiplier applied to type.attackDamage() on each turn.
     * Set to {@code GameBalance.enemyDamageScaleForDepth(depth)} at spawn time.
     * Floor 1 = 1.0 (no scaling).
     */
    public float      attackDamageMultiplier = 1f;
    public EnemyState state              = EnemyState.DORMANT;
    public int        turnCounter        = 0;
    public int        stuckTurns         = 0;
    /** Wall-clock seconds remaining in the white hit-flash. Purely cosmetic — does not affect simulation. */
    public float      hitFlashTimerSeconds = 0f;
    /** Wall-clock seconds remaining in the attack animation. Cosmetic only — never affects simulation. */
    public float      attackAnimTimerSeconds = 0f;
    /** Wall-clock seconds remaining in the pre-hit telegraph (same-turn flinch). Cosmetic only. */
    public float      telegraphTimerSeconds = 0f;

    // -------------------------------------------------------------------------
    // Visible locomotion slide (boss-fight-mobile-overseer ORDER 2) — COSMETIC ONLY.
    // The authoritative tileColumn/tileRow above jump to the destination instantly
    // when a move resolves (the turn model stays clean — simulation is never
    // mid-tile). These fields let the render position LAG behind and catch up over
    // BOSS_DASH_ANIM_DURATION, sweeping smoothly through the exact tiles the boss
    // logically crossed, so a multi-tile relocation reads as a sprint rather than a
    // teleport (fairness contract F3). Same discipline as attackAnimTimerSeconds:
    // never read by collision / LOS / attack math. Normal enemies leave these at
    // rest; today only the boss calls beginSlide(). Buffers are pre-sized in the
    // constructor so advanceSlide() — which runs every frame — never allocates.
    private float visualColumn;   // interpolated render column (tiles)
    private float visualRow;      // interpolated render row (tiles)
    private float moveAnimSeconds;  // seconds remaining in the current slide
    private float moveAnimDuration; // total seconds for the current slide
    // Sized to the longest single-turn slide (a full-range CHARGE lunge, ORDER 3), not just the
    // DASH reach: BOSS_MAX_SLIDE_TILES steps + the origin tile. Reused every slide; never a new[].
    private final int[] slidePathColumns = new int[Constants.BOSS_MAX_SLIDE_TILES + 1];
    private final int[] slidePathRows    = new int[Constants.BOSS_MAX_SLIDE_TILES + 1];
    private int   slidePathLength;

    /** Dungeon floor on which this enemy spawned (1-based). Used for the name-tag display "Type LVL N". */
    public int        dungeonLevel = 1;

    /** Pre-built display string shown above the health bar, e.g. "Corruptor LVL 2". Set at spawn time. */
    public String     nameTag = "";

    /** Set by StatusEffectController when STUNNED ticks; consumed in EnemyManager's EXECUTE phase (R6). */
    public boolean skipNextAction = false;

    /**
     * No-repeat memory for scripted SPECIAL abilities (strategy-combat-order-5). One reused instance;
     * the COMMIT step records each chosen ability so a multi-ability caster rotates its script rather
     * than spamming one move. Never allocated inside the turn loop.
     */
    public final MoveHistory moveHistory = new MoveHistory();

    /**
     * Running total of chaff this summoner has spawned across its life (strategy-combat-order-5). No
     * longer a hard cap on summoning — the per-cast batch size, the SPECIAL cadence (the reuse cooldown)
     * and the per-room live ceiling bound the flood instead. Kept for stats/telemetry; non-summoners 0.
     */
    public int summonsSpawned = 0;

    /**
     * How many turns in a row this enemy has committed DEFEND. Incremented each time a DEFEND is
     * committed, reset to 0 the moment it commits anything else. EnemyManager.shouldDefend refuses to
     * brace again once this reaches {@code BalanceConfig.DEFEND_MAX_CONSECUTIVE_TURNS}, so a cornered
     * low-HP enemy can't turtle forever and just stand there (design feedback: enemies "doing nothing").
     */
    public int consecutiveDefendTurns = 0;

    /**
     * Pre-selected spawn tiles for a committed SUMMON (strategy-combat-order-5). Chosen at COMMIT time
     * (not execution) so the intent is honest: a floor telegraph marks these exact empty cells during
     * the player's turn (EnemyRenderer), then executeSummon spawns into them one turn later. Sized to
     * the four cardinal neighbours; {@code summonTargetCount} says how many slots are live this cast.
     * Meaningful only while {@code plannedAction.verb == SPECIAL} and its ability is SUMMON.
     */
    public final int[] summonTargetColumns = new int[EnemyConstants.SUMMON_TARGET_CAPACITY];
    public final int[] summonTargetRows    = new int[EnemyConstants.SUMMON_TARGET_CAPACITY];
    public int         summonTargetCount   = 0;

    /**
     * The action this enemy has COMMITTED to perform on its next turn (strategy-combat-order-1).
     * A single reused instance — never null after construction, but {@code committed} stays false
     * until the enemy first commits (a freshly-woken enemy shows no intent and only commits on its
     * wake turn). The intent-icon renderer (order-2) reads this during the player's turn.
     */
    public final PlannedAction plannedAction = new PlannedAction();

    /**
     * Wall-clock seconds remaining in the intent-icon "pop" (strategy-combat-order-2): a brief
     * scale-in played when the enemy re-commits to a DIFFERENT verb than last turn, so the player's
     * eye is drawn to the change. Cosmetic only — never read by the simulation.
     */
    public float intentPopTimerSeconds = 0f;

    /**
     * The verb committed on the PREVIOUS commit, used solely to detect a verb change and fire the
     * intent-icon pop. Null until the enemy's first commit. Cosmetic bookkeeping for order-2.
     */
    private IntentVerb previouslyCommittedVerb = null;

    /**
     * Committed cardinal rush direction captured when the charger commits a WIND_UP intent (Pillar 2).
     * Read by EnemyManager.performCharge on the following turn when the WIND_UP plan executes.
     */
    public int chargeDirectionColumn = 0;
    public int chargeDirectionRow    = 0;

    // -------------------------------------------------------------------------
    // Rime lance channel (elemental-golem-rimeshell-lancer) — the Lancer's molten beam. The lane is
    // LOCKED when it commits the WIND_UP charge and fired straight down it a turn later regardless of
    // where the player now stands (a sidestep off the lane wastes the whole beam). Inert for every
    // archetype whose type.baseArmor() is 0 (i.e. everything but the Lancer).
    // -------------------------------------------------------------------------

    /**
     * Committed cardinal lane direction captured when the Lancer commits its WIND_UP charge, mirroring
     * {@link #chargeDirectionColumn}/{@link #chargeDirectionRow}. Read by EnemyManager.executeLanceBeam on
     * the following turn: the beam fires down THIS locked lane, never re-homing on the player's live tile.
     */
    public int lanceDirectionColumn = 0;
    public int lanceDirectionRow    = 0;

    /**
     * Enemy turns remaining before this Lancer may charge another lance. Set to
     * {@link EnemyConstants#RIMESHELL_LANCE_COOLDOWN_TURNS} the turn a beam fires and counted down once per
     * enemy turn ({@link #advanceLanceCooldown}); while it is above zero the shell stays sealed and the
     * Lancer behaves as an ordinary slow ranged unit.
     */
    public int lanceCooldownTurns = 0;

    /**
     * Plague Hulk low-HP finisher (see .claude/agents/ideas/plague-hulk-self-destruct.txt). Once
     * {@code selfDestructPrimed} is set it is STICKY — the Hulk never reverts to attacking, moving,
     * or Block-defending; every later commit just re-telegraphs {@code selfDestructTurnsRemaining}.
     * {@code selfDestructed} is set true only at the instant of detonation, and read by the death
     * hazard hook to choose the massive toxic cloud over the ordinary minimal one.
     */
    public boolean selfDestructPrimed        = false;
    public int     selfDestructTurnsRemaining = 0;
    public boolean selfDestructed            = false;

    // -------------------------------------------------------------------------
    // Orbiting shard economy (elemental-golem-auric-sentinel) — the Auric Sentinel's shards are BOTH
    // its armour and its ammunition. Inert for every archetype whose type.maxShards() is 0.
    // -------------------------------------------------------------------------

    /**
     * Live shards in the ring, 0..{@code type.maxShards()}. Each one nullifies exactly ONE incoming
     * damage INSTANCE ({@link #applyDamage}) and each shot SPENDS one ({@link #spendShardForShot}), so
     * the counter to this enemy's offence and the counter to its defence are the same action. At 0 it
     * cannot attack at all. The count is the whole readability contract — EnemyRenderer draws exactly
     * this many orbiting quads, so the player reads the enemy's remaining armour AND its remaining
     * ammunition off the same ring, with no HUD, bar or text.
     *
     * <p>Assigned in the constructor from {@code type.maxShards()} (0 for every archetype but the
     * Sentinel) rather than by a field initialiser, so an enemy is never briefly ringless at spawn.
     */
    public int shardCount;

    /**
     * Enemy turns accumulated toward the next shard re-growth. Advanced once per enemy turn — including
     * turns it fired or was stunned — so a passive player faces a permanently full ring.
     */
    public int shardRegenCounter = 0;

    /**
     * Shards the ring held immediately before the most recent CHANGE (a loss or a re-growth). Read ONLY
     * by the renderer, to interpolate the surviving shards from their old spacing to their new even
     * spacing while {@link #shardRespaceTimerSeconds} runs. Never read by the simulation.
     */
    public int shardCountBeforeAbsorb = 0;

    /**
     * Wall-clock seconds remaining in the ring's re-space animation, armed by ANY change to the shard
     * count — absorbing a hit, launching a shot, or re-growing — so the ring always closes up (or opens)
     * smoothly instead of snapping. Cosmetic only.
     */
    public float shardRespaceTimerSeconds = 0f;

    /**
     * Wall-clock seconds remaining in the GOLD absorb flash — the "that hit did nothing to my health"
     * tell that replaces the ordinary white hit flash. Armed ONLY by an absorb, never by a shot: the
     * flash means "your damage was nullified", so firing must not counterfeit it. Cosmetic only.
     */
    public float shardAbsorbFlashTimerSeconds = 0f;

    // -------------------------------------------------------------------------
    // Cooling crust + molten trail (elemental-golem-cinderforge-colossus) — the CINDERFORGE_COLOSSUS
    // gets HARDER the longer the player stops shooting it, and sets fire to every tile it leaves.
    // Inert for every archetype whose type.crustMaxStacks() / type.leavesTrailFireTurns() is 0.
    // -------------------------------------------------------------------------

    /**
     * Live crust stacks, 0..{@code type.crustMaxStacks()}. Each stack adds
     * {@link EnemyConstants#CINDERFORGE_CRUST_ARMOR_PER_STACK} to {@link #armor} while it stands; ANY
     * damage shatters the WHOLE shell at once ({@link #shatterCrust}). Gained at the end of the enemy's
     * own turn only when it took no damage since its previous turn ({@link #advanceCrustCooling}), so
     * the stack count is a direct readout of how long the player has hesitated.
     *
     * <p>Per-instance and never persisted across a level — a fresh Colossus always enters the fight bare
     * and soft, so the opening burst is always worth taking.
     */
    public int crustStacks = 0;

    /**
     * True once this enemy has taken damage of ANY kind since its previous turn — a weapon hit, an
     * ability, a barrel, or a status DoT tick. Read (and cleared) once per turn by
     * {@link #advanceCrustCooling}: hesitation is measured in "turns during which nothing hurt it", so
     * the flag is the whole gain condition. DoT counts deliberately, or a Burning/Bleed build could
     * never express the counterplay at all.
     */
    public boolean damagedSinceLastTurn = false;

    /**
     * Crust level the RENDERER shows, easing toward the live {@code crustStacks / max} fraction over
     * {@link EnemyConstants#CINDERFORGE_TINT_LERP_SECONDS}. Cosmetic only — the simulation always reads
     * {@link #crustStacks}, which changes instantly. The ease is what makes a shatter a visible FLASH
     * back to hot instead of a snap.
     */
    private float crustTintLevel = 0f;

    /**
     * Remaining turns of each live MOLTEN TRAIL tile this enemy has lit, as a fixed-size ring of
     * counters. The hazard system owns the actual fire; this is only the per-Colossus LIVE-TILE CAP
     * bookkeeping ({@link EnemyConstants#CINDERFORGE_TRAIL_MAX_LIVE_TILES}), so one long chase cannot
     * carpet a floor. Pre-sized at construction and compacted in place — {@link #advanceTrailTiles} runs
     * every enemy turn and never allocates.
     */
    private final int[] trailTileTurnsRemaining =
            new int[EnemyConstants.CINDERFORGE_TRAIL_MAX_LIVE_TILES];
    private int         trailTileCount = 0;

    /**
     * True only for an add spawned by the boss's SUMMON tactic (boss-fight-mobile-overseer ORDER 6).
     * Set at spawn time by EnemyManager.spawnBossMinion; level-placed enemies and regular-summoner
     * adds leave it false. Read by the death drop path to grant the guaranteed ammo lifeline: in the
     * sealed boss arena, a player who has run out of ammo ALWAYS gets matching ammo from a summoned
     * add so the fight can never become unwinnable (Fairness F5). Scoped to summoned adds so the normal
     * loot economy is untouched everywhere else.
     */
    public boolean spawnedByBossSummon       = false;

    // Status effect storage — pre-allocated at construction, never replaced
    private final EnumMap<StatusType, StatusEffect> activeEffects;
    private StatusResistance statusResistance = StatusResistance.defaultResistance();

    public Enemy(EnemyType type, int tileColumn, int tileRow) {
        this.type          = type;
        this.tileColumn    = tileColumn;
        this.tileRow       = tileRow;
        this.maxHealth     = type.maxHealth();
        this.health        = this.maxHealth;
        // A shard-bearing archetype (today only AURIC_SENTINEL) spawns with a FULL ring; every other
        // archetype reports 0 shards, which makes every shard code path inert for it.
        this.shardCount    = type.maxShards();
        // A shell-bearing archetype (today only RIMESHELL_LANCER) spawns SEALED, with its live flat shell
        // already up; every other archetype reports 0, i.e. unarmoured. Set here rather than by the depth
        // scaler so a directly-constructed instance (tests) is never briefly shell-less.
        this.armor         = type.baseArmor();
        this.activeEffects = buildEffectsMap();
    }

    // -------------------------------------------------------------------------
    // Shard economy behaviour (elemental-golem-auric-sentinel). All no-ops when shardCount is 0.
    // -------------------------------------------------------------------------

    /** True while this enemy still holds at least one shard — i.e. it is both armoured and armed. */
    public boolean hasShard() {
        return shardCount > 0;
    }

    /**
     * Spends one shard to launch it as a projectile, returning false when the ring is EMPTY and the
     * enemy is therefore disarmed. Called from the EXECUTE phase on a committed shot: the shard is
     * spent even if the shot then whiffs under the standard fairness re-validation, so committing to a
     * shot the player steps out of is a real cost — and a real bait.
     */
    public boolean spendShardForShot() {
        if (shardCount <= 0) return false;
        // The ring closes up behind a launched shard exactly as it does after an absorb — but NOT the
        // gold absorb flash, which means "your damage did nothing" and must never fire on the enemy's
        // own shot.
        beginShardRespace();
        shardCount--;
        return true;
    }

    /** Arms the ring's re-space animation and records the pre-change count the renderer lerps from. */
    private void beginShardRespace() {
        shardCountBeforeAbsorb   = shardCount;
        shardRespaceTimerSeconds = EnemyConstants.AURIC_SHARD_RESPACE_SECONDS;
    }

    /**
     * Advances the re-growth clock by one enemy turn and grows a shard when due, returning true only on
     * the turn a shard actually appears (so the caller can fire the re-grow effect). The counter runs on
     * EVERY turn — including turns the enemy fired, braced or was stunned — so a passive player faces a
     * permanently full ring, and the clock keeps running at the cap rather than stalling.
     */
    public boolean advanceShardRegrowth() {
        int maxShards = type.maxShards();
        if (maxShards <= 0) return false;
        shardRegenCounter++;
        if (shardRegenCounter < type.shardRegenTurns()) return false;
        shardRegenCounter = 0;
        if (shardCount >= maxShards) return false;   // ring already full — the clock simply restarts
        beginShardRespace();
        shardCount++;
        return true;
    }

    /** Decrements both shard cosmetic timers by deltaTime, clamping at zero. Call once per frame. */
    public void advanceShardAbsorbFlash(float deltaTime) {
        if (shardAbsorbFlashTimerSeconds > 0f) {
            shardAbsorbFlashTimerSeconds -= deltaTime;
            if (shardAbsorbFlashTimerSeconds < 0f) shardAbsorbFlashTimerSeconds = 0f;
        }
        if (shardRespaceTimerSeconds > 0f) {
            shardRespaceTimerSeconds -= deltaTime;
            if (shardRespaceTimerSeconds < 0f) shardRespaceTimerSeconds = 0f;
        }
    }

    /** Gold absorb-flash strength in [0, 1]: 1 the instant a shard ate a hit, 0 once settled. */
    public float getShardAbsorbFlashStrength() {
        if (EnemyConstants.AURIC_ABSORB_FLASH_DURATION_SECONDS <= 0f) return 0f;
        return shardAbsorbFlashTimerSeconds / EnemyConstants.AURIC_ABSORB_FLASH_DURATION_SECONDS;
    }

    /**
     * Ring re-space progress in [0, 1]: 0 the instant the shard count changed (surviving shards still
     * at their OLD spacing), 1 once they have settled at their new even spacing.
     */
    public float getShardRespaceProgress() {
        if (EnemyConstants.AURIC_SHARD_RESPACE_SECONDS <= 0f) return 1f;
        return 1f - shardRespaceTimerSeconds / EnemyConstants.AURIC_SHARD_RESPACE_SECONDS;
    }

    // -------------------------------------------------------------------------
    // Cooling crust behaviour (elemental-golem-cinderforge-colossus). All no-ops when crustMaxStacks is 0.
    // -------------------------------------------------------------------------

    /**
     * Advances the cooling clock by one of this enemy's own turns, gaining a crust stack when it took NO
     * damage since its previous turn, and returns true only on the turn a stack actually forms (so the
     * caller can fire the cooling effect). Consumes {@link #damagedSinceLastTurn} either way, which is
     * what makes the mechanic "one uninterrupted turn = one stack" rather than a cumulative timer.
     *
     * <p>Called only for ALERTED enemies taking a turn, so a Colossus asleep since level load can never
     * wake up already fully hardened — the opening burst is always worth taking.
     */
    public boolean advanceCrustCooling() {
        int maxStacks = type.crustMaxStacks();
        if (maxStacks <= 0) return false;
        boolean tookDamage   = damagedSinceLastTurn;
        damagedSinceLastTurn = false;
        if (tookDamage) return false;
        if (crustStacks >= maxStacks) return false;   // already fully cooled — nothing more to gain
        crustStacks++;
        armor += EnemyConstants.CINDERFORGE_CRUST_ARMOR_PER_STACK;
        return true;
    }

    /**
     * Shatters the entire crust, removing its armour contribution, and returns how many stacks broke
     * (0 when there was no crust, which is the common case for every other archetype). Called from BOTH
     * damage entry points — {@link #applyDamage} and {@link #applyDoTDamage} — because "any damage
     * resets it" has to mean ANY, or a Burning/Bleed build is silently locked out of the counterplay.
     */
    public int shatterCrust() {
        damagedSinceLastTurn = true;
        if (crustStacks <= 0) return 0;
        int brokenStacks = crustStacks;
        crustStacks      = 0;
        armor            = Math.max(0,
                armor - brokenStacks * EnemyConstants.CINDERFORGE_CRUST_ARMOR_PER_STACK);
        return brokenStacks;
    }

    /** True while this enemy wears a FULLY cooled shell — the state that pays the shatter bonus. */
    public boolean hasFullCrust() {
        int maxStacks = type.crustMaxStacks();
        return maxStacks > 0 && crustStacks >= maxStacks;
    }

    /**
     * The damage {@code amount} becomes once the FULL-shell shatter bonus is applied, or {@code amount}
     * unchanged when there is no full shell to break. Pure query, no side effects.
     *
     * <p>Single source of truth for the multiplier: {@link #applyDamage} routes through it to compute the
     * real hit, and the caller routes through it to compute the number it FLOATS on screen. Without a
     * shared helper the two drift, and a shatter that hits for 31 while the floater says 25 turns the
     * archetype's payoff beat into a bug report.
     */
    public int crustShatterAmplified(int amount) {
        if (amount <= 0 || !hasFullCrust()) return amount;
        return Math.round(amount * EnemyConstants.CINDERFORGE_CRUST_SHATTER_MULTIPLIER);
    }

    /**
     * Eases the RENDERED crust level toward the live stack fraction. Call once per frame; a no-op for
     * every archetype without crust. Cosmetic only.
     */
    public void advanceCrustTint(float deltaTime) {
        int maxStacks = type.crustMaxStacks();
        if (maxStacks <= 0) return;
        float target = (float) crustStacks / maxStacks;
        crustTintLevel = GameMath.easeTowardLinear(crustTintLevel, target, deltaTime,
                EnemyConstants.CINDERFORGE_TINT_LERP_SECONDS);
    }

    /** Rendered crust level in [0, 1]: 0 = blazing hot and soft, 1 = fully cooled and hardened. */
    public float getCrustTintLevel() {
        return crustTintLevel;
    }

    // -------------------------------------------------------------------------
    // Molten trail bookkeeping (elemental-golem-cinderforge-colossus).
    // -------------------------------------------------------------------------

    /**
     * True when this enemy may light ANOTHER trail tile — i.e. it leaves a trail at all and is still
     * under its per-Colossus live-tile cap. The cap is what stops a long chase from carpeting a floor;
     * the oldest tiles expire first on their own, so the cap simply skips a tile rather than stealing
     * one back from the hazard system (which owns the fire once it is lit).
     */
    public boolean canLightTrailTile() {
        return type.leavesTrailFireTurns() > 0
                && trailTileCount < trailTileTurnsRemaining.length;
    }

    /** Records a freshly lit trail tile against the live-tile cap. Call only after a successful ignition. */
    public void recordTrailTile(int lifetimeTurns) {
        if (trailTileCount >= trailTileTurnsRemaining.length) return;
        trailTileTurnsRemaining[trailTileCount++] = lifetimeTurns;
    }

    /**
     * Ages every live trail tile by one turn and drops the expired ones, keeping the live count honest
     * against the hazard system's own lifetimes. Compacts the fixed-size ring in place — no allocation.
     * A no-op for every archetype that leaves no trail.
     */
    public void advanceTrailTiles() {
        if (trailTileCount <= 0) return;
        int survivorCount = 0;
        for (int tileIndex = 0; tileIndex < trailTileCount; tileIndex++) {
            int turnsLeft = trailTileTurnsRemaining[tileIndex] - 1;
            if (turnsLeft > 0) {
                trailTileTurnsRemaining[survivorCount++] = turnsLeft;
            }
        }
        trailTileCount = survivorCount;
    }

    /** Live trail tiles this enemy currently holds against its cap. Exposed for tests and telemetry. */
    public int liveTrailTileCount() {
        return trailTileCount;
    }

    // -------------------------------------------------------------------------
    // Rime lance shell behaviour (elemental-golem-rimeshell-lancer). All no-ops when baseArmor() is 0.
    // -------------------------------------------------------------------------

    /**
     * OPENS the ice shell — drops the live flat mitigation to 0 for the punish window. Called by
     * EnemyManager when the Lancer commits its lance charge; the shell stays down for the whole of the
     * following player turn, then {@link #resealShell} restores it after the beam fires. A no-op-ish
     * setter for any archetype with no shell (it simply zeroes an already-zero {@code armor}).
     */
    public void openShell() {
        armor = 0;
    }

    /** RESEALS the ice shell to its full live value after the lance fires. Restores {@link Enemy#armor}. */
    public void resealShell() {
        armor = type.baseArmor();
    }

    /** True while a shell-bearing Lancer is currently OPEN (charging) — the window a burst punishes. */
    public boolean isShellOpen() {
        return type.baseArmor() > 0 && armor <= 0;
    }

    /**
     * Counts the lance cooldown down by one of this enemy's own turns, clamped at zero. Called once per
     * enemy turn from EnemyManager; while it is above zero the Lancer may not begin another charge. A
     * no-op for every archetype that never lances (its cooldown never leaves zero).
     */
    public void advanceLanceCooldown() {
        if (lanceCooldownTurns > 0) lanceCooldownTurns--;
    }

    private static EnumMap<StatusType, StatusEffect> buildEffectsMap() {
        EnumMap<StatusType, StatusEffect> map = new EnumMap<>(StatusType.class);
        for (StatusType type : StatusType.values()) {
            map.put(type, new StatusEffect(type));
        }
        return map;
    }

    /** Assigns the archetype-specific resist/immunity table. Call once after construction at spawn time. */
    public void setStatusResistance(StatusResistance resistance) {
        this.statusResistance = resistance;
    }

    @Override
    public EnumMap<StatusType, StatusEffect> getActiveEffects() { return activeEffects; }

    @Override
    public StatusResistance getStatusResistance() { return statusResistance; }

    @Override
    public void applyDoTDamage(int amount) {
        // Enemies have no dodge or toughness reduction — DoT damage applies directly.
        // Status DoT (burning/poison/bleed) BYPASSES Block by design (strategy-combat-order-3):
        // status builds are the rock-paper-scissors answer to defensive, Block-stacking enemies.
        // For the same reason it bypasses SHARDS (elemental-golem-auric-sentinel): a burn ticks
        // straight into a Sentinel's HP without consuming a shard, so burning one down is a legitimate,
        // distinct answer to the archetype and no status build is ever hard-countered by it. It is also
        // why hazard tiles (fire/toxic, which apply status) damage a Sentinel through its ring.
        //
        // It does NOT, however, bypass the Cinderforge Colossus's COOLING CRUST reset
        // (elemental-golem-cinderforge-colossus): a DoT tick still COUNTS as damage taken, so a burn or
        // a bleed keeps the shell shattered and the enemy soft. Without that, an Incinerator or Bleed
        // build could not express the archetype's counterplay at all — it would watch the Colossus
        // harden through its own damage. The tick deliberately claims no shatter BONUS (that payoff
        // belongs to the player who came back out and pulled the trigger), which is why it calls
        // shatterCrust directly rather than going through applyDamage's shatter branch.
        shatterCrust();
        health = Math.max(0, health - amount);
    }

    /**
     * Grants Block (strategy-combat-order-3): braces the enemy for {@code amount}, added to any
     * residual Block and capped at {@code blockMax}. A fresh DEFEND also REFRESHES the decay window
     * to a full {@code decayTurns} — re-bracing keeps the shield alive for another window rather than
     * inheriting the old timer (the REFRESH_DURATION semantics the status layer uses for burns). With
     * the default 1-turn decay, Block always expires between two of an enemy's turns, so this refresh
     * only ever re-arms a freshly-emptied buffer in practice. Called from EnemyManager when a committed
     * DEFEND executes.
     */
    public void gainBlock(int amount, int blockMax, int decayTurns) {
        if (amount <= 0) return;
        block           = Math.min(blockMax, block + amount);
        blockDecayTurns = decayTurns;
    }

    /**
     * Counts the active Block down by one world turn and zeroes it on expiry (strategy-combat-order-3).
     * Called once per living enemy per turn from the StatusEffectController tick so Block expiry shares
     * the deterministic status-phase ordering. No-op while the enemy holds no Block.
     */
    public void decayBlock() {
        if (block <= 0) {
            blockDecayTurns = 0;
            return;
        }
        blockDecayTurns--;
        if (blockDecayTurns <= 0) {
            block           = 0;
            blockDecayTurns = 0;
        }
    }

    /**
     * Returns this enemy's attack damage scaled by the depth multiplier AND its own EMPOWERED buff
     * (strategy-combat-order-5: a caster that used BUFF_SELF hits harder), minimum 1. Reading the buff
     * here means the same value drives both the committed predicted-damage number and the executed hit.
     */
    public int scaledAttackDamage() {
        return Math.max(1, Math.round(type.attackDamage() * attackDamageMultiplier
                * empoweredDamageMultiplier() * weakOutgoingMultiplier()));
    }

    /**
     * Returns this enemy's WEAK outgoing-damage multiplier (strategy-combat-order-6): {@code 1 - P/100}
     * while a WEAK debuff is active, else 1.0. Because {@link #scaledAttackDamage} folds it in, a
     * WEAK-ened enemy's telegraphed predicted-damage number AND its executed hit both drop together —
     * the player SEES their debuff working. Softening the room is a defensive alternative to racing it.
     */
    public float weakOutgoingMultiplier() {
        StatusEffect weakEffect = activeEffects.get(StatusType.WEAK);
        return GameMath.weakDamageMultiplier(weakEffect != null && weakEffect.isActive(),
                EffectConstants.WEAK_DAMAGE_PERCENT);
    }

    /**
     * Returns the active VULNERABLE stack count (strategy-combat-order-6), or 0 when unmarked. The
     * caller (EnemyManager mitigation pipeline) turns this into the incoming-damage multiplier via
     * {@link GameMath#vulnerableDamageMultiplier}.
     */
    public int vulnerableStacks() {
        StatusEffect vulnerableEffect = activeEffects.get(StatusType.VULNERABLE);
        return (vulnerableEffect != null && vulnerableEffect.isActive()) ? vulnerableEffect.getStacks() : 0;
    }

    /** True while an EXPOSED flag is active (strategy-combat-order-6): the next hit ignores this enemy's Block. */
    public boolean isExposed() {
        StatusEffect exposedEffect = activeEffects.get(StatusType.EXPOSED);
        return exposedEffect != null && exposedEffect.isActive();
    }

    /** Clears the EXPOSED flag after a hit has consumed it (one hit, not permanent — the anti-turtle contract). */
    public void consumeExposed() {
        activeEffects.get(StatusType.EXPOSED).reset();
    }

    /**
     * Returns the outgoing-damage multiplier from an active EMPOWERED buff (1.0 when unbuffed), mirroring
     * {@code Player.getEmpoweredDamageMultiplier()}: {@code 1 + magnitude / 100}. Lets an enemy that cast
     * BUFF_SELF (order-5) actually swing harder while the buff lasts.
     */
    private float empoweredDamageMultiplier() {
        StatusEffect empoweredEffect = activeEffects.get(StatusType.EMPOWERED);
        if (empoweredEffect != null && empoweredEffect.isActive()) {
            return 1f + empoweredEffect.getMagnitude() / 100f;
        }
        return 1f;
    }

    public boolean isAlive() {
        return health > 0;
    }

    public boolean isAlerted() {
        return state != EnemyState.DORMANT;
    }

    public void alert() {
        if (state == EnemyState.DORMANT) {
            state = EnemyState.ALERTED;
        }
    }

    /**
     * Applies a weapon/ability hit through the Block → armor → HP portion of the shared mitigation
     * pipeline (strategy-combat-order-3; the directional/power modifiers that precede these steps are
     * resolved by the caller). Block absorbs first (a hit fully swallowed by fresh Block deals no HP
     * damage — overkill into Block is lost), then flat armor reduces the overflow, then HP takes the
     * remainder. See docs/game-balance-authority.txt for the full 5-step order.
     */
    public void applyDamage(int amount) {
        applyDamage(amount, false);
    }

    /**
     * Block → armor → HP mitigation with an optional EXPOSED override (strategy-combat-order-6).
     * When {@code ignoreBlock} is true the hit skips Block absorption entirely and bites straight
     * into armor/HP — the answer to a turtling, Block-stacking enemy without an armor-piercing weapon.
     * The caller (EnemyManager) sets this from {@link #isExposed()} and then {@link #consumeExposed()}
     * so the shred lasts exactly ONE hit, never permanently.
     */
    public void applyDamage(int amount, boolean ignoreBlock) {
        applyDamage(amount, ignoreBlock, 0f);
    }

    /**
     * SHARD → Block → armor → HP mitigation with an EXPOSED override and an ARMOR_PIERCE Block-pierce
     * fraction (weapon-ability audit). {@code ignoreBlock} skips Block entirely (EXPOSED);
     * otherwise {@code blockPierceFraction} (clamped to [0, 1]) shrinks the Block that can
     * absorb this hit, so a fraction of the damage bleeds straight through to HP even against a
     * shielded enemy. pierce = 0 reproduces the plain Block → armor → HP behaviour exactly.
     * The un-pierced portion of Block is left intact — the hit only bypasses it, never spends it.
     *
     * <p><b>SHARDS RESOLVE FIRST</b> (elemental-golem-auric-sentinel), ahead of both Block and flat
     * armour: while the ring holds a shard, ANY single incoming damage instance is NULLIFIED — not
     * reduced, nullified, whatever its size — and consumes exactly one shard. A 90-damage railgun slug
     * and a 10-damage chaingun tick both cost the Sentinel one shard and cost the player one shot,
     * which is the whole puzzle: the RIGHT WEAPON matters more than the right position.
     *
     * <p>Because absorption is per INSTANCE of damage rather than per turn, a weapon that resolves as
     * several separate {@code applyDamage} calls (a Chaingun burst, a multi-tile march, an ability's
     * bonus hit) strips several shards on one trigger pull. That is the intended reward for a rapid or
     * wide weapon and it FALLS OUT of this implementation rather than being special-cased anywhere.
     * Status DoT deliberately bypasses this hook entirely — see {@link #applyDoTDamage}.
     *
     * <p><b>THE COOLING CRUST SHATTERS LAST</b> (elemental-golem-cinderforge-colossus), after the whole
     * mitigation chain has run, so the full order is SHARD -> [x1.25 on a full shell] -> Block -> armor
     * (crust included) -> HP -> crust shatters. Two consequences are deliberate: the shell protects the
     * very blow that breaks it (otherwise hardening would buy the enemy literally nothing), and the hit
     * that breaks a FULLY cooled shell is amplified first — the payoff for coming back out. Every damage
     * instance shatters the shell, so a player who keeps firing never faces crust at all, which is the
     * entire lesson the archetype teaches.
     */
    public void applyDamage(int amount, boolean ignoreBlock, float blockPierceFraction) {
        if (amount > 0 && shardCount > 0) {
            beginShardRespace();
            shardCount--;
            shardAbsorbFlashTimerSeconds = EnemyConstants.AURIC_ABSORB_FLASH_DURATION_SECONDS;
            // An absorbed hit still COUNTS as damage taken for any crust this enemy might wear, so no
            // archetype can ever be built that hardens while its shards eat the incoming fire.
            shatterCrust();
            return;   // fully absorbed — Block, armour and HP are all untouched by this hit
        }
        // COOLING CRUST (elemental-golem-cinderforge-colossus). The shell is still standing when this
        // blow lands — its flat armour is already inside `armor` and reduces THIS hit, which is the
        // whole point of having hardened. Breaking a FULLY cooled shell pays the shatter multiplier
        // first: it is the payoff for coming back out and firing, not a punishment for having waited.
        // Then the shell is gone, and every later hit meets the bare body.
        // Captured BEFORE the shatter multiplier rewrites `amount`, so "did the player connect?" stays a
        // question about the incoming hit rather than about the amplified one.
        boolean landedHit = amount > 0;
        amount = crustShatterAmplified(amount);
        int remaining = amount;
        if (!ignoreBlock) {
            float clampedPierce = Math.max(0f, Math.min(1f, blockPierceFraction));
            int   effectiveBlock = Math.round(block * (1f - clampedPierce));
            int   absorbed       = GameMath.blockAbsorbed(effectiveBlock, amount);
            block -= absorbed;
            remaining = amount - absorbed;
        }
        if (remaining > 0) {                    // remaining <= 0 => fully braced, HP untouched this turn
            int afterArmor = Math.max(0, remaining - armor);
            health = Math.max(0, health - afterArmor);
        }
        // The crust falls AFTER mitigation, never before — so the shell the player just broke actually
        // protected the blow that broke it. A hit swallowed whole by Block still shatters it: the player
        // pulled the trigger and connected, and an enemy that could brace AND keep cooling would make
        // "never stop shooting" unanswerable. Cheap for every other archetype (crustStacks is 0, so this
        // only sets the damage flag nothing else reads).
        if (landedHit) shatterCrust();
    }

    /** Resets the hit-flash timer to full duration. Calling again before it expires re-triggers cleanly. */
    public void triggerHitFlash() {
        hitFlashTimerSeconds = EnemyConstants.ENEMY_HIT_FLASH_DURATION_SECONDS;
    }

    /** Decrements the hit-flash timer by deltaTime, clamping at zero. Call once per frame from World.update(). */
    public void advanceHitFlash(float deltaTime) {
        if (hitFlashTimerSeconds > 0f) {
            hitFlashTimerSeconds -= deltaTime;
            if (hitFlashTimerSeconds < 0f) hitFlashTimerSeconds = 0f;
        }
    }

    /** Returns flash strength in [0, 1]: 1 = freshly hit (full white), 0 = expired (normal shade). */
    public float getHitFlashStrength() {
        if (EnemyConstants.ENEMY_HIT_FLASH_DURATION_SECONDS <= 0f) return 0f;
        return hitFlashTimerSeconds / EnemyConstants.ENEMY_HIT_FLASH_DURATION_SECONDS;
    }

    /** Resets the attack animation timer to full duration. Mirrors triggerHitFlash(). */
    public void triggerAttackAnim() {
        attackAnimTimerSeconds = EffectConstants.ENEMY_ATTACK_ANIM_DURATION_SECONDS;
        telegraphTimerSeconds  = EffectConstants.ENEMY_TELEGRAPH_DURATION_SECONDS;
    }

    /**
     * Triggers the pre-hit telegraph pulse WITHOUT the attack/lunge animation. Used for the
     * charger wind-up (Pillar 2): a readable rim flash on the turn before the rush lands.
     */
    public void triggerTelegraph() {
        telegraphTimerSeconds = EffectConstants.ENEMY_TELEGRAPH_DURATION_SECONDS;
    }

    /** Advances both attack animation and telegraph timers by deltaTime, clamping at zero. */
    public void advanceAttackAnim(float deltaTime) {
        if (attackAnimTimerSeconds > 0f) {
            attackAnimTimerSeconds -= deltaTime;
            if (attackAnimTimerSeconds < 0f) attackAnimTimerSeconds = 0f;
        }
        if (telegraphTimerSeconds > 0f) {
            telegraphTimerSeconds -= deltaTime;
            if (telegraphTimerSeconds < 0f) telegraphTimerSeconds = 0f;
        }
    }

    /** Returns attack animation strength in [0, 1]: 1 at trigger, 0 when expired. */
    public float getAttackAnimStrength() {
        if (EffectConstants.ENEMY_ATTACK_ANIM_DURATION_SECONDS <= 0f) return 0f;
        return attackAnimTimerSeconds / EffectConstants.ENEMY_ATTACK_ANIM_DURATION_SECONDS;
    }

    /** Returns telegraph strength in [0, 1]: 1 at trigger, 0 when expired. */
    public float getTelegraphStrength() {
        if (EffectConstants.ENEMY_TELEGRAPH_DURATION_SECONDS <= 0f) return 0f;
        return telegraphTimerSeconds / EffectConstants.ENEMY_TELEGRAPH_DURATION_SECONDS;
    }

    /**
     * Records the verb just committed and, if it differs from the previously committed verb, triggers
     * the intent-icon pop (order-2). Call once per commit from EnemyManager's COMMIT phase. The first
     * commit never pops (there is no earlier verb to differ from).
     */
    public void notifyCommitted(IntentVerb committedVerb) {
        if (previouslyCommittedVerb != null && committedVerb != previouslyCommittedVerb) {
            intentPopTimerSeconds = IntentConstants.INTENT_POP_DURATION_SECONDS;
        }
        previouslyCommittedVerb = committedVerb;
    }

    /** Decrements the intent-pop timer by deltaTime, clamping at zero. Call once per frame. */
    public void advanceIntentPop(float deltaTime) {
        if (intentPopTimerSeconds > 0f) {
            intentPopTimerSeconds -= deltaTime;
            if (intentPopTimerSeconds < 0f) intentPopTimerSeconds = 0f;
        }
    }

    /** Returns intent-pop strength in [0, 1]: 1 at the instant of a verb change, 0 when settled. */
    public float getIntentPopStrength() {
        if (IntentConstants.INTENT_POP_DURATION_SECONDS <= 0f) return 0f;
        return intentPopTimerSeconds / IntentConstants.INTENT_POP_DURATION_SECONDS;
    }

    /** True when this enemy's move cooldown allows it to step on the current turn. */
    public boolean shouldMoveThisTurn() {
        return turnCounter % type.moveEveryNTurns() == 0;
    }

    /** World-space X center of this enemy's tile (for rendering). */
    public float worldCenterX() {
        return tileColumn * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
    }

    /** World-space Y center of this enemy's tile (for rendering). */
    public float worldCenterY() {
        return tileRow * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
    }

    // -------------------------------------------------------------------------
    // Visible locomotion slide (ORDER 2) — cosmetic render-position interpolation.
    // -------------------------------------------------------------------------

    /**
     * Starts a cosmetic multi-tile slide (ORDER 2). The path is the exact sequence of tiles the boss
     * logically crossed this turn, starting with its ORIGIN tile and ending on its (already-updated)
     * destination tile. Copies the path into the pre-sized buffers (no allocation), snaps the visual
     * position to the first path tile, and arms the animation for {@code durationSeconds}. Purely
     * cosmetic — the caller has already committed the logical tile move. A {@code count} of 0 or 1
     * (no real movement) leaves the slide inert.
     */
    public void beginSlide(int[] pathColumns, int[] pathRows, int count, float durationSeconds) {
        slidePathLength = Math.min(count, slidePathColumns.length);
        for (int pathIndex = 0; pathIndex < slidePathLength; pathIndex++) {
            slidePathColumns[pathIndex] = pathColumns[pathIndex];
            slidePathRows[pathIndex]    = pathRows[pathIndex];
        }
        if (slidePathLength < 2 || durationSeconds <= 0f) {
            // Nothing to animate — leave the slide inert so renderColumn/Row fall back to the tile.
            moveAnimSeconds  = 0f;
            moveAnimDuration = 0f;
            return;
        }
        moveAnimDuration = durationSeconds;
        moveAnimSeconds  = durationSeconds;
        visualColumn     = slidePathColumns[0];
        visualRow        = slidePathRows[0];
    }

    /**
     * Advances the cosmetic slide by {@code deltaTime} (ORDER 2). Called every frame from
     * EnemyManager.advanceHitFlash — a no-op unless a slide is active. Maps overall progress across the
     * path and lerps the visual position between the two bounding path tiles (slight ease-out so the
     * boss "arrives and plants" with weight — OPEN QUESTION 2). Snaps the visual position to the final
     * path tile (== the logical tile) on completion to kill float drift. Never allocates.
     */
    public void advanceSlide(float deltaTime) {
        if (moveAnimSeconds <= 0f) return;
        moveAnimSeconds -= deltaTime;
        if (moveAnimSeconds <= 0f) {
            moveAnimSeconds = 0f;
            visualColumn    = slidePathColumns[slidePathLength - 1];
            visualRow       = slidePathRows[slidePathLength - 1];
            return;
        }
        float easedProgress = GameMath.smoothstep01(1f - moveAnimSeconds / moveAnimDuration);
        visualColumn = sampleSlideColumn(easedProgress);
        visualRow    = sampleSlideRow(easedProgress);
    }

    /**
     * Interpolates the slide path column at an overall eased progress in [0, 1] (0 = origin tile,
     * 1 = destination tile). Shared by {@link #advanceSlide} and the ORDER 8 afterimage trail, which
     * samples a few earlier progress points to place fading ghost billboards behind a dashing boss.
     * Returns the origin/destination tile for out-of-range or inert (< 2 tiles) paths.
     */
    public float sampleSlideColumn(float easedProgress) {
        if (slidePathLength < 2) return tileColumn;
        int   segmentCount    = slidePathLength - 1;
        float clamped         = easedProgress < 0f ? 0f : (easedProgress > 1f ? 1f : easedProgress);
        float pathPosition    = clamped * segmentCount;
        int   segmentIndex    = (int) pathPosition;
        if (segmentIndex >= segmentCount) segmentIndex = segmentCount - 1;
        float segmentFraction = pathPosition - segmentIndex;
        return GameMath.lerp(slidePathColumns[segmentIndex],
                slidePathColumns[segmentIndex + 1], segmentFraction);
    }

    /** Interpolates the slide path row at an overall eased progress in [0, 1]. See {@link #sampleSlideColumn}. */
    public float sampleSlideRow(float easedProgress) {
        if (slidePathLength < 2) return tileRow;
        int   segmentCount    = slidePathLength - 1;
        float clamped         = easedProgress < 0f ? 0f : (easedProgress > 1f ? 1f : easedProgress);
        float pathPosition    = clamped * segmentCount;
        int   segmentIndex    = (int) pathPosition;
        if (segmentIndex >= segmentCount) segmentIndex = segmentCount - 1;
        float segmentFraction = pathPosition - segmentIndex;
        return GameMath.lerp(slidePathRows[segmentIndex],
                slidePathRows[segmentIndex + 1], segmentFraction);
    }

    /**
     * Raw (un-eased) overall progress of the active slide in [0, 1] — 0 at the origin tile, 1 at the
     * destination. The afterimage trail (ORDER 8) offsets this backward to sample trailing ghost
     * positions. Returns 1 when no slide is active.
     */
    public float slideProgress() {
        if (moveAnimSeconds <= 0f || moveAnimDuration <= 0f) return 1f;
        return 1f - moveAnimSeconds / moveAnimDuration;
    }

    /** True while a cosmetic slide is playing out (ORDER 2). */
    public boolean isSliding() { return moveAnimSeconds > 0f; }

    /** Render column in tiles: the interpolated visual position while sliding, else the logical tile. */
    public float renderColumn() { return isSliding() ? visualColumn : tileColumn; }

    /** Render row in tiles: the interpolated visual position while sliding, else the logical tile. */
    public float renderRow() { return isSliding() ? visualRow : tileRow; }

    /** World-space X center for rendering — follows the cosmetic slide while one is active (ORDER 2). */
    public float renderCenterX() {
        return renderColumn() * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
    }

    /** World-space Y center for rendering — follows the cosmetic slide while one is active (ORDER 2). */
    public float renderCenterY() {
        return renderRow() * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
    }
}
