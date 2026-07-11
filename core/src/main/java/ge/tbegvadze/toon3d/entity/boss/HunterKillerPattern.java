package ge.tbegvadze.toon3d.entity.boss;

import com.badlogic.gdx.Gdx;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Overseer's Hunter-Killer brain (boss-fight-mobile-overseer ORDER 4) — a tactic-selecting
 * state machine that sequences the ORDER 3 verb kit (stalk/flee dashes, charge, hazards, summon,
 * melee) into readable, varied multi-turn TACTICS. It replaces the stationary OverseerPhase1/2Pattern
 * as the Overseer's active pattern.
 *
 * <h2>How it "thinks"</h2>
 * A {@link Tactic} is a short fixed array of atomic {@link Verb}s (data, not code). While a tactic is
 * in progress the brain simply emits the next verb's {@link BossMove} — deterministic and readable once
 * the player recognises the opener. When a tactic finishes it SELECTS a new one by scoring every
 * eligible tactic against the current situation (range to player, HP, live adds, player camping/cornered)
 * plus a small seeded random tiebreak, honouring a per-verb cooldown and a no-immediate-repeat rule.
 * The scoring weights and cooldown gaps are all {@link GameBalance} constants so a designer can retune
 * aggression without touching this class.
 *
 * <h2>Fairness</h2>
 * Every damaging verb is telegraphed a turn ahead (charge/hazard windup steps); the tactics are shaped so
 * a punish window (a planted HOLD / recovery / telegraph) arrives frequently, and the brain mirrors the
 * controller's F1 consecutive-move backstop ({@link Constants#BOSS_MAX_CONSECUTIVE_MOVE_TURNS}) so it
 * never trips it. The controller ({@code BossFloorController}) remains the authoritative executor and
 * enforces the F1/F2 backstops regardless of what this brain emits.
 *
 * <h2>Allocation</h2>
 * All buffers are pre-allocated; {@link #nextMove} runs once per player turn (never in render()) and does
 * not allocate. The seeded RNG makes the random tiebreak reproducible for replays/debugging (ORDER 4 open
 * question 3).
 *
 * <p>HEAL (ORDER 5) is not yet a verb in the kit; when it lands it plugs in as a preempting tactic here
 * (see {@link #selectTactic}) exactly like the LAST STAND preempt already does.
 */
public final class HunterKillerPattern implements BossAttackPattern {

    /** Which phase pool this brain instance drives — selects the tactic set and cooldown gaps. */
    public enum Pool { PHASE1, PHASE2 }

    /**
     * Atomic, single-turn steps a tactic is built from. Each maps to exactly one {@link BossMove}
     * (and may arm the boss's DangerTileSet). Damaging verbs come in a windup→resolve pair so the hit
     * is always telegraphed one turn ahead (fairness F2).
     */
    enum Verb {
        /** DASH toward the player's tile (closes distance; controller stops adjacent). */
        STALK,
        /** DASH away from the player to a flee tile (the "hit then run" escape). */
        FLEE,
        /** Adjacent slam — resolves only if the controller finds the player adjacent. */
        MELEE,
        /** Summon a small pack of adds near the boss (capped by OVERSEER_ADDS_CAP). */
        SUMMON,
        /** A planted, empty turn — the guaranteed punish window. */
        HOLD,
        /** Arm the charge corridor toward the player and telegraph it (charge wind-up). */
        CHARGE_WINDUP,
        /** Lunge along the telegraphed corridor, resolving the hit (charge strike). */
        CHARGE_STRIKE,
        /** Arm a fire lane footprint and telegraph it (fire wind-up). */
        FIRE_WINDUP,
        /** Seed fire on the armed footprint (fire cast). */
        FIRE_CAST,
        /** Arm a 3x3 toxic cloud footprint on the player and telegraph it (toxic wind-up). */
        TOXIC_WINDUP,
        /** Seed toxic on the armed footprint (toxic cast). */
        TOXIC_CAST
    }

    /**
     * The tactic catalogue (ORDER 4). Each tactic is a named, fixed verb sequence the player learns to
     * recognise by its opener. {@code usesCharge/usesSummon/usesHazard} gate the tactic behind the
     * matching per-verb cooldown so heavy verbs never spam.
     */
    enum Tactic {
        /** Neutral filler: close in, poke, plant. Uses no heavy verb, so it is ALWAYS eligible — the
         *  guaranteed fallback that keeps selection non-empty and gives the no-repeat rule an out. */
        PROBE(new Verb[]{ Verb.STALK, Verb.MELEE, Verb.HOLD },
              false, false, false),

        /** Phase 1 signature "hit then flee": lunge in, crack, sprint off, burn the ground behind. */
        AMBUSH(new Verb[]{ Verb.STALK, Verb.MELEE, Verb.FLEE, Verb.FIRE_WINDUP, Verb.FIRE_CAST, Verb.HOLD },
               false, false, true),

        /** Phase 1 ranged pressure: wall off space with toxic, back away, drop adds, reset. */
        ZONER(new Verb[]{ Verb.TOXIC_WINDUP, Verb.TOXIC_CAST, Verb.FLEE, Verb.SUMMON, Verb.HOLD },
              false, true, true),

        /** Phase 1 big telegraphed line hit + escape; punish the recovery. */
        CHARGER(new Verb[]{ Verb.CHARGE_WINDUP, Verb.CHARGE_STRIKE, Verb.HOLD, Verb.FLEE },
                true, false, false),

        /** Phase 2 layered zoning — the enraged escalation of ZONER; carpets the arena. */
        FIRESTORM(new Verb[]{ Verb.FIRE_WINDUP, Verb.FIRE_CAST, Verb.FLEE,
                              Verb.TOXIC_WINDUP, Verb.TOXIC_CAST, Verb.SUMMON, Verb.HOLD },
                  false, true, true),

        /** Phase 2 relentless close pressure — charge chained into a melee; two telegraphs, then a window. */
        BLITZ(new Verb[]{ Verb.STALK, Verb.CHARGE_WINDUP, Verb.CHARGE_STRIKE,
                          Verb.MELEE, Verb.FLEE, Verb.HOLD },
              true, false, false);

        final Verb[]  steps;
        final boolean usesCharge;
        final boolean usesSummon;
        final boolean usesHazard;

        Tactic(Verb[] steps, boolean usesCharge, boolean usesSummon, boolean usesHazard) {
            this.steps      = steps;
            this.usesCharge = usesCharge;
            this.usesSummon = usesSummon;
            this.usesHazard = usesHazard;
        }
    }

    private static final Tactic[] PHASE1_POOL = { Tactic.AMBUSH, Tactic.ZONER, Tactic.CHARGER, Tactic.PROBE };
    private static final Tactic[] PHASE2_POOL = { Tactic.FIRESTORM, Tactic.BLITZ, Tactic.CHARGER, Tactic.PROBE };

    // Cardinal neighbour offsets used for the "cornered" exit count.
    private static final int[] STEP_COLUMNS = {  0,  0,  1, -1 };
    private static final int[] STEP_ROWS    = {  1, -1,  0,  0 };

    private final Pool     pool;
    private final Tactic[] tactics;
    private final int      chargeGap;
    private final int      summonGap;
    private final int      hazardGap;
    private final Random   tiebreakRandom;

    private Tactic currentTactic   = null;
    private Tactic lastTactic      = null;
    private int    tacticStepIndex = 0;

    // Per-verb cooldowns (turns) — a heavy verb's tactic is ineligible while its cooldown is above zero.
    private int chargeCooldownLeft = 0;
    private int summonCooldownLeft = 0;
    private int hazardCooldownLeft = 0;

    // Charge axis captured at CHARGE_WINDUP, replayed at CHARGE_STRIKE.
    private int     chargeDirectionColumn = 0;
    private int     chargeDirectionRow    = 0;
    /** Set when a wind-up could not arm (boss boxed in / empty footprint) so the paired resolve HOLDs. */
    private boolean windupAborted         = false;

    // F1 mirror: consecutive kite (DASH) turns the brain has emitted. Mirrors the controller's guard so
    // the brain plants BEFORE the controller has to override it.
    private int consecutiveMoveTurns = 0;

    // Camping detection — how many turns the player has held the same tile.
    private int previousPlayerColumn = Integer.MIN_VALUE;
    private int previousPlayerRow    = Integer.MIN_VALUE;
    private int playerStationaryTurns = 0;

    // Pre-allocated footprint scratch (reused every wind-up; never a per-tick new[]).
    private final List<DangerTileSet.MarkedTile> footprintScratch = new ArrayList<>();

    /**
     * @param pool     which phase's tactic set + cooldown gaps this instance drives
     * @param bossSeed the run-derived seed for the random tiebreak, so replays/debugging are reproducible
     *                 (ORDER 4 open question 3). The two instances (phase 1 / phase 2) salt it differently
     *                 so their random streams are independent.
     */
    public HunterKillerPattern(Pool pool, long bossSeed) {
        this.pool           = pool;
        this.tactics        = pool == Pool.PHASE1 ? PHASE1_POOL : PHASE2_POOL;
        this.chargeGap      = pool == Pool.PHASE1
                ? GameBalance.BOSS_TACTIC_CHARGE_GAP_PHASE1 : GameBalance.BOSS_TACTIC_CHARGE_GAP_PHASE2;
        this.summonGap      = pool == Pool.PHASE1
                ? GameBalance.BOSS_TACTIC_SUMMON_GAP_PHASE1 : GameBalance.BOSS_TACTIC_SUMMON_GAP_PHASE2;
        this.hazardGap      = pool == Pool.PHASE1
                ? GameBalance.BOSS_TACTIC_HAZARD_GAP_PHASE1 : GameBalance.BOSS_TACTIC_HAZARD_GAP_PHASE2;
        this.tiebreakRandom = new Random(bossSeed ^ (pool == Pool.PHASE1 ? 0x4B1L : 0x4B2L));
    }

    @Override
    public void onPhaseStart() {
        currentTactic         = null;
        lastTactic            = null;
        tacticStepIndex       = 0;
        chargeCooldownLeft    = 0;
        summonCooldownLeft    = 0;
        hazardCooldownLeft    = 0;
        windupAborted         = false;
        consecutiveMoveTurns  = 0;
        previousPlayerColumn  = Integer.MIN_VALUE;
        previousPlayerRow     = Integer.MIN_VALUE;
        playerStationaryTurns = 0;
    }

    @Override
    public BossMove nextMove(Boss boss, Player player, Level level, long tickIndex) {
        int playerColumn = GameMath.worldToTile(player.positionX);
        int playerRow    = GameMath.worldToTile(player.positionY);

        tickCooldowns();
        updateCampingTracker(playerColumn, playerRow);

        // If no tactic is in progress, choose the next one from the situation.
        if (currentTactic == null || tacticStepIndex >= currentTactic.steps.length) {
            selectTactic(boss, level, playerColumn, playerRow);
        }

        Verb verb = currentTactic.steps[tacticStepIndex];

        // F1 mirror backstop: if this step is a kite (DASH) and the brain is already at the cap, plant
        // instead and retry the kite next turn. The controller guards this too, but planting here keeps
        // tactics flowing naturally instead of getting silently swallowed mid-sequence.
        if (isKiteVerb(verb) && consecutiveMoveTurns >= Constants.BOSS_MAX_CONSECUTIVE_MOVE_TURNS) {
            consecutiveMoveTurns = 0;
            return BossMove.none();
        }

        BossMove move = emitVerb(verb, boss, level, playerColumn, playerRow);
        tacticStepIndex++;

        if (isKiteVerb(verb)) {
            consecutiveMoveTurns++;
        } else {
            consecutiveMoveTurns = 0;
        }
        return move;
    }

    // ---------------------------------------------------------------------------------------------
    // Tactic selection
    // ---------------------------------------------------------------------------------------------

    /**
     * Chooses the next tactic from the current situation and resets the step cursor. Preempts the normal
     * scored selection with LAST STAND (phase 2, low HP → commit to BLITZ). Otherwise scores every
     * eligible tactic, applies the no-immediate-repeat rule, and sets the chosen tactic's verb cooldowns.
     */
    private void selectTactic(Boss boss, Level level, int playerColumn, int playerRow) {
        // LAST STAND preempt (ORDER 4 backlog): below the threshold in phase 2 the boss goes all-in on
        // BLITZ every selection — the readable, climactic finish. Bypasses cooldown/no-repeat by design.
        if (pool == Pool.PHASE2 && healthFraction(boss) <= GameBalance.BOSS_TACTIC_LAST_STAND_HP_FRACTION) {
            commitTactic(Tactic.BLITZ);
            return;
        }

        int rangeToPlayer = GameMath.chebyshevDistanceTiles(
                boss.tileColumn, boss.tileRow, playerColumn, playerRow);
        boolean playerCornered = countPassableExits(level, playerColumn, playerRow)
                <= GameBalance.BOSS_TACTIC_CORNERED_EXITS;
        boolean playerCamping  = playerStationaryTurns >= GameBalance.BOSS_TACTIC_CAMP_TURNS;
        int     addsAlive      = Math.max(0, boss.liveMinionCount);

        // The no-repeat rule only applies if there is at least one OTHER eligible tactic to switch to.
        boolean anotherEligibleExists = false;
        for (int index = 0; index < tactics.length; index++) {
            Tactic candidate = tactics[index];
            if (candidate != lastTactic && isEligible(candidate)) {
                anotherEligibleExists = true;
                break;
            }
        }

        Tactic bestTactic = Tactic.PROBE; // PROBE is always eligible → never a null selection.
        int    bestScore  = Integer.MIN_VALUE;
        for (int index = 0; index < tactics.length; index++) {
            Tactic candidate = tactics[index];
            if (!isEligible(candidate)) continue;
            if (candidate == lastTactic && anotherEligibleExists) continue; // no immediate repeat
            int score = scoreTactic(candidate, rangeToPlayer, addsAlive, playerCornered, playerCamping)
                    + tiebreakRandom.nextInt(GameBalance.BOSS_TACTIC_RANDOM_TIEBREAK);
            if (score > bestScore) {
                bestScore  = score;
                bestTactic = candidate;
            }
        }

        commitTactic(bestTactic);
    }

    /** Locks in the chosen tactic: resets the step cursor, records it for no-repeat, and sets cooldowns. */
    private void commitTactic(Tactic tactic) {
        currentTactic   = tactic;
        lastTactic      = tactic;
        tacticStepIndex = 0;

        // Cooldown = the tactic's own length + the configured gap, so spacing is measured from when the
        // tactic finishes (not when it starts) regardless of how many steps it has.
        int spacing = tactic.steps.length;
        if (tactic.usesCharge) chargeCooldownLeft = spacing + chargeGap;
        if (tactic.usesSummon) summonCooldownLeft = spacing + summonGap;
        if (tactic.usesHazard) hazardCooldownLeft = spacing + hazardGap;

        if (GameBalance.BOSS_TACTIC_DEBUG_LOG) {
            Gdx.app.log("HunterKiller", pool + " -> tactic " + tactic
                    + " (charge cd=" + chargeCooldownLeft + " summon cd=" + summonCooldownLeft
                    + " hazard cd=" + hazardCooldownLeft + ")");
        }
    }

    private boolean isEligible(Tactic tactic) {
        if (tactic.usesCharge && chargeCooldownLeft > 0) return false;
        if (tactic.usesSummon && summonCooldownLeft > 0) return false;
        if (tactic.usesHazard && hazardCooldownLeft > 0) return false;
        return true;
    }

    /**
     * Scores a tactic from the situation (ORDER 4 selection scoring). All weights are GameBalance
     * constants; this is the boss's "personality" surface — bias it here, not in the verb code.
     */
    private int scoreTactic(Tactic tactic, int rangeToPlayer, int addsAlive,
                            boolean playerCornered, boolean playerCamping) {
        int score       = GameBalance.BOSS_TACTIC_SCORE_BASE;
        boolean close   = rangeToPlayer <= GameBalance.BOSS_TACTIC_CLOSE_RANGE_TILES;
        boolean addsRoom = addsAlive < EnemyConstants.OVERSEER_ADDS_CAP;

        switch (tactic) {
            case AMBUSH:
                if (close)         score += GameBalance.BOSS_TACTIC_SCORE_CLOSE_RANGE;
                if (playerCamping) score += GameBalance.BOSS_TACTIC_SCORE_CAMPING; // fire on the camp tile
                break;
            case BLITZ:
                if (close)          score += GameBalance.BOSS_TACTIC_SCORE_CLOSE_RANGE;
                if (playerCornered) score += GameBalance.BOSS_TACTIC_SCORE_CORNERED;
                break;
            case CHARGER:
                if (!close)         score += GameBalance.BOSS_TACTIC_SCORE_FAR_RANGE; // close the gap
                if (playerCornered) score += GameBalance.BOSS_TACTIC_SCORE_CORNERED;
                break;
            case ZONER:
                if (!close)        score += GameBalance.BOSS_TACTIC_SCORE_FAR_RANGE;
                if (addsRoom)      score += GameBalance.BOSS_TACTIC_SCORE_ADDS_ROOM;
                if (playerCamping) score += GameBalance.BOSS_TACTIC_SCORE_CAMPING;
                break;
            case FIRESTORM:
                if (!close)        score += GameBalance.BOSS_TACTIC_SCORE_FAR_RANGE;
                if (addsRoom)      score += GameBalance.BOSS_TACTIC_SCORE_ADDS_ROOM;
                if (playerCamping) score += GameBalance.BOSS_TACTIC_SCORE_CAMPING;
                break;
            case PROBE:
            default:
                break; // neutral filler — base score only
        }
        return score;
    }

    // ---------------------------------------------------------------------------------------------
    // Verb → BossMove
    // ---------------------------------------------------------------------------------------------

    private BossMove emitVerb(Verb verb, Boss boss, Level level, int playerColumn, int playerRow) {
        switch (verb) {
            case STALK:
                return BossMove.dash(playerColumn, playerRow);

            case FLEE:
                return buildFleeDash(boss, playerColumn, playerRow);

            case MELEE:
                return BossMove.melee(EnemyConstants.OVERSEER_MELEE_DAMAGE);

            case SUMMON:
                return BossMove.summon(pickSummonType(), EnemyConstants.OVERSEER_SUMMON_COUNT,
                        EnemyConstants.OVERSEER_ADDS_CAP);

            case CHARGE_WINDUP:
                return buildChargeWindup(boss, level, playerColumn, playerRow);

            case CHARGE_STRIKE:
                if (windupAborted) {
                    windupAborted = false;
                    return BossMove.none();
                }
                return BossMove.charge(chargeDirectionColumn, chargeDirectionRow,
                        EnemyConstants.OVERSEER_CHARGE_RANGE_TILES);

            case FIRE_WINDUP:
                return buildHazardWindup(boss, level, playerColumn, playerRow, true);

            case TOXIC_WINDUP:
                return buildHazardWindup(boss, level, playerColumn, playerRow, false);

            case FIRE_CAST:
                if (windupAborted) {
                    windupAborted = false;
                    return BossMove.none();
                }
                return BossMove.castHazard(BossMove.HazardKind.FIRE);

            case TOXIC_CAST:
                if (windupAborted) {
                    windupAborted = false;
                    return BossMove.none();
                }
                return BossMove.castHazard(BossMove.HazardKind.TOXIC);

            case HOLD:
            default:
                return BossMove.none();
        }
    }

    /**
     * Builds a FLEE dash: an absolute target BOSS_MAX_DASH_TILES away from the player along the dominant
     * escape axis. The controller path-steps toward it and stops at the first wall, so the flee is a
     * visible slide that never overruns cover.
     */
    private BossMove buildFleeDash(Boss boss, int playerColumn, int playerRow) {
        int differenceColumn = boss.tileColumn - playerColumn;
        int differenceRow    = boss.tileRow    - playerRow;

        int stepColumn = 0;
        int stepRow    = 0;
        if (Math.abs(differenceColumn) >= Math.abs(differenceRow)) {
            stepColumn = differenceColumn == 0 ? 1 : Integer.signum(differenceColumn);
        } else {
            stepRow = Integer.signum(differenceRow);
        }

        int targetColumn = boss.tileColumn + stepColumn * Constants.BOSS_MAX_DASH_TILES;
        int targetRow    = boss.tileRow    + stepRow    * Constants.BOSS_MAX_DASH_TILES;
        return BossMove.dash(targetColumn, targetRow);
    }

    /**
     * Arms the charge corridor: the straight cardinal lane from the boss toward the player, up to
     * OVERSEER_CHARGE_RANGE_TILES tiles or the first wall/cover column. Records the axis for the strike.
     * If no clear lane exists (boss on the player's tile / boxed at a wall) it sets {@link #windupAborted}
     * and emits a HOLD so the paired CHARGE_STRIKE also HOLDs instead of firing an empty charge.
     */
    private BossMove buildChargeWindup(Boss boss, Level level, int playerColumn, int playerRow) {
        footprintScratch.clear();
        int differenceColumn = playerColumn - boss.tileColumn;
        int differenceRow    = playerRow    - boss.tileRow;
        if (differenceColumn == 0 && differenceRow == 0) {
            windupAborted = true;
            return BossMove.none();
        }

        int stepColumn = Integer.signum(differenceColumn);
        int stepRow    = Integer.signum(differenceRow);
        if (Math.abs(differenceColumn) >= Math.abs(differenceRow)) {
            stepRow = 0;
        } else {
            stepColumn = 0;
        }
        chargeDirectionColumn = stepColumn;
        chargeDirectionRow    = stepRow;

        for (int reach = 1; reach <= EnemyConstants.OVERSEER_CHARGE_RANGE_TILES; reach++) {
            int targetColumn = boss.tileColumn + stepColumn * reach;
            int targetRow    = boss.tileRow    + stepRow    * reach;
            if (targetColumn < 0 || targetColumn >= level.getWidth())  break;
            if (targetRow    < 0 || targetRow    >= level.getHeight()) break;
            char cell = level.getCell(targetColumn, targetRow);
            if (Level.isWall(cell) || Level.isColumn(cell)) break;
            footprintScratch.add(new DangerTileSet.MarkedTile(targetColumn, targetRow));
        }

        if (footprintScratch.isEmpty()) {
            windupAborted = true;
            return BossMove.none();
        }
        windupAborted = false;
        boss.dangerTileSet.arm(footprintScratch, EnemyConstants.OVERSEER_CHARGE_DAMAGE);
        return BossMove.telegraph(EnemyConstants.OVERSEER_CHARGE_DAMAGE);
    }

    /**
     * Arms a hazard footprint and telegraphs it. FIRE lays a short cardinal lane from the boss toward the
     * player (burning the ground between them so pursuit is denied); TOXIC lays a small cloud centred on
     * the player. If the footprint is entirely walls it sets {@link #windupAborted} so the cast HOLDs.
     */
    private BossMove buildHazardWindup(Boss boss, Level level, int playerColumn, int playerRow,
                                       boolean fire) {
        footprintScratch.clear();
        if (fire) {
            buildFireLane(boss, level, playerColumn, playerRow);
        } else {
            buildToxicCloud(level, playerColumn, playerRow);
        }

        if (footprintScratch.isEmpty()) {
            windupAborted = true;
            return BossMove.none();
        }
        windupAborted = false;
        // Damage is nominal — the hazard system owns the DOT once cast; this value is only the telegraph
        // marker's carried damage and is never resolved (the paired cast ignites and clears the marks).
        boss.dangerTileSet.arm(footprintScratch, EnemyConstants.OVERSEER_MELEE_DAMAGE);
        return BossMove.telegraph(EnemyConstants.OVERSEER_MELEE_DAMAGE);
    }

    /** Marks a cardinal fire lane from the boss toward the player, length OVERSEER_FIRE_LANE_LENGTH. */
    private void buildFireLane(Boss boss, Level level, int playerColumn, int playerRow) {
        int differenceColumn = playerColumn - boss.tileColumn;
        int differenceRow    = playerRow    - boss.tileRow;
        int stepColumn = Integer.signum(differenceColumn);
        int stepRow    = Integer.signum(differenceRow);
        if (Math.abs(differenceColumn) >= Math.abs(differenceRow)) {
            stepRow = 0;
            if (stepColumn == 0) stepColumn = 1; // boss already on the player's column/tile
        } else {
            stepColumn = 0;
        }

        for (int reach = 1; reach <= EnemyConstants.OVERSEER_FIRE_LANE_LENGTH; reach++) {
            int targetColumn = boss.tileColumn + stepColumn * reach;
            int targetRow    = boss.tileRow    + stepRow    * reach;
            if (targetColumn < 0 || targetColumn >= level.getWidth())  break;
            if (targetRow    < 0 || targetRow    >= level.getHeight()) break;
            char cell = level.getCell(targetColumn, targetRow);
            if (Level.isWall(cell) || Level.isColumn(cell)) break;
            footprintScratch.add(new DangerTileSet.MarkedTile(targetColumn, targetRow));
        }
    }

    /** Marks a (2·radius+1)² toxic cloud centred on the player, skipping walls/cover columns. */
    private void buildToxicCloud(Level level, int playerColumn, int playerRow) {
        int radius = EnemyConstants.OVERSEER_TOXIC_RADIUS;
        for (int columnOffset = -radius; columnOffset <= radius; columnOffset++) {
            for (int rowOffset = -radius; rowOffset <= radius; rowOffset++) {
                int targetColumn = playerColumn + columnOffset;
                int targetRow    = playerRow    + rowOffset;
                if (targetColumn < 0 || targetColumn >= level.getWidth())  continue;
                if (targetRow    < 0 || targetRow    >= level.getHeight()) continue;
                char cell = level.getCell(targetColumn, targetRow);
                if (Level.isWall(cell) || Level.isColumn(cell)) continue;
                footprintScratch.add(new DangerTileSet.MarkedTile(targetColumn, targetRow));
            }
        }
    }

    /** Alternates the summoned add archetype via the seeded RNG (variety without unfairness). */
    private EnemyType pickSummonType() {
        return tiebreakRandom.nextBoolean() ? EnemyType.CRAWLER : EnemyType.GHOUL;
    }

    // ---------------------------------------------------------------------------------------------
    // Situation helpers
    // ---------------------------------------------------------------------------------------------

    private void tickCooldowns() {
        if (chargeCooldownLeft > 0) chargeCooldownLeft--;
        if (summonCooldownLeft > 0) summonCooldownLeft--;
        if (hazardCooldownLeft > 0) hazardCooldownLeft--;
    }

    private void updateCampingTracker(int playerColumn, int playerRow) {
        if (playerColumn == previousPlayerColumn && playerRow == previousPlayerRow) {
            playerStationaryTurns++;
        } else {
            playerStationaryTurns = 0;
            previousPlayerColumn  = playerColumn;
            previousPlayerRow     = playerRow;
        }
    }

    private int countPassableExits(Level level, int tileColumn, int tileRow) {
        int exits = 0;
        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            int targetColumn = tileColumn + STEP_COLUMNS[directionIndex];
            int targetRow    = tileRow    + STEP_ROWS[directionIndex];
            if (targetColumn < 0 || targetColumn >= level.getWidth())  continue;
            if (targetRow    < 0 || targetRow    >= level.getHeight()) continue;
            char cell = level.getCell(targetColumn, targetRow);
            if (!Level.isWall(cell) && !Level.isColumn(cell)) exits++;
        }
        return exits;
    }

    private static float healthFraction(Boss boss) {
        return boss.maxHealth > 0 ? (float) boss.health / (float) boss.maxHealth : 0f;
    }

    private static boolean isKiteVerb(Verb verb) {
        return verb == Verb.STALK || verb == Verb.FLEE;
    }
}
