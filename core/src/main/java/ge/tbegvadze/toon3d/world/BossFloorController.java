package ge.tbegvadze.toon3d.world;

import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.entity.boss.Boss;
import ge.tbegvadze.toon3d.entity.boss.BossMove;
import ge.tbegvadze.toon3d.entity.boss.BossVisualState;
import ge.tbegvadze.toon3d.entity.boss.DangerTileSet;
import ge.tbegvadze.toon3d.entity.boss.RegenState;
import ge.tbegvadze.toon3d.hazard.HazardManager;
import ge.tbegvadze.toon3d.level.BossArenaLayout;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.render.BossHudRenderer;
import ge.tbegvadze.toon3d.render.EventTextSystem;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.List;

/**
 * TickSubscriber that drives the boss encounter for one dungeon floor.
 *
 * Responsibilities per tick:
 *   1. Awaken check — once the player enters BOSS_AWAKEN_RADIUS_TILES, lock the arena door
 *      and start the intro sequence.
 *   2. Boss AI — delegate to Boss.activePattern().nextMove(); execute the resulting BossMove.
 *   3. Phase transition — when HP crosses the 50% threshold, trigger phase 2 banner and
 *      one-tick invulnerability.
 *   4. Death check — unlock arena door, re-enable the exit, fire kill events, hide HUD.
 *
 * The BossFloorController is subscribed to the TickEventBus before EnemyTurnSubscriber so
 * the boss acts first each turn.
 */
public final class BossFloorController implements TickSubscriber {

    // Cardinal step offsets for boss repositioning (N, S, E, W)
    private static final int[] STEP_COLUMNS = {  0,  0,  1, -1 };
    private static final int[] STEP_ROWS    = {  1, -1,  0,  0 };

    private final Boss           boss;
    private final Level          level;
    private final DoorManager    doorManager;
    private final EnemyManager   enemyManager;
    private final HazardManager  hazardManager;
    /**
     * Nullable — the boss banner/health HUD. Null in the headless balance simulator
     * (new-game-balancr order 9), which runs real boss fights with no render layer; every call site
     * below guards for it exactly like {@link #eventTextSystem} does.
     */
    private final BossHudRenderer bossHudRenderer;
    private final EventTextSystem eventTextSystem;

    // Arena door + exit positions — injected from the per-run BossArenaLayout (boss ORDER 7). The arena
    // is now procedural, so these vary per run and can no longer be compile-time constants.
    private final int arenaDoorColumn;
    private final int arenaDoorRow;

    // Exit tile position — disabled until boss dies
    private final int exitColumn;
    private final int exitRow;

    private boolean bossDefeated       = false;
    private boolean phase2Triggered    = false;
    private int     invulnerableTurnsLeft = 0;
    /** Soft enrage (order 6): true once the "FURIOUS" banner has fired, so the announcement plays exactly once. */
    private boolean enrageAnnounced    = false;

    /**
     * Fairness contract F1 (ORDER 2): how many turns in a row the boss has MOVED (DASH/REPOSITION)
     * without a planted turn. Reset to 0 by any planted action. When it would exceed
     * BOSS_MAX_CONSECUTIVE_MOVE_TURNS the controller overrides the move to a HOLD so the player always
     * gets a punish window — a hard backstop no pattern can break.
     */
    private int consecutiveMoveTurns = 0;

    /**
     * Fairness contract F1/F2 (ORDER 3): forced recovery turns remaining after a CHARGE lunge. While
     * this is above zero the controller overrides any locomotion move (DASH/REPOSITION/CHARGE) to a
     * HOLD so the boss cannot immediately flee or charge again — the player's guaranteed punish window.
     * Non-locomotion moves (telegraph / resolve / melee / summon / cast-hazard) still resolve.
     */
    private int chargeRecoveryTurnsLeft = 0;

    /**
     * Pre-allocated path buffers for the greedy DASH/REPOSITION stepper (ORDER 2) and the CHARGE lunge
     * (ORDER 3). Sized BOSS_MAX_SLIDE_TILES + 1 (origin tile + one entry per step) so a full-range charge
     * fits. Reused every move — never a per-tick new[]. Entry 0 is always the boss's origin tile; the
     * last live entry is the destination.
     */
    private final int[] movePathColumns = new int[Constants.BOSS_MAX_SLIDE_TILES + 1];
    private final int[] movePathRows    = new int[Constants.BOSS_MAX_SLIDE_TILES + 1];

    public BossFloorController(Boss boss, Level level, BossArenaLayout arenaLayout, DoorManager doorManager,
                               EnemyManager enemyManager, HazardManager hazardManager,
                               BossHudRenderer bossHudRenderer, EventTextSystem eventTextSystem) {
        this.boss            = boss;
        this.level           = level;
        this.doorManager     = doorManager;
        this.enemyManager    = enemyManager;
        this.hazardManager   = hazardManager;
        this.bossHudRenderer = bossHudRenderer;
        this.eventTextSystem = eventTextSystem;

        this.arenaDoorColumn = arenaLayout.doorColumn;
        this.arenaDoorRow    = arenaLayout.doorRow;
        this.exitColumn      = arenaLayout.exitColumn;
        this.exitRow         = arenaLayout.exitRow;

        // Block the exit until the boss is defeated
        level.setCell(exitColumn, exitRow, 'x');
    }

    @Override
    public void onTick(TickContext context) {
        if (bossDefeated) return;

        Player player         = context.getPlayer();
        int    playerColumn   = context.getPlayerTileColumn();
        int    playerRow      = context.getPlayerTileRow();
        long   tickIndex      = context.getTickIndex();

        // Awaken check — triggers once when the player first enters range
        if (!boss.hasAwakened) {
            int distance = GameMath.chebyshevDistanceTiles(
                    boss.tileColumn, boss.tileRow, playerColumn, playerRow);
            if (distance <= Constants.BOSS_AWAKEN_RADIUS_TILES) {
                awakeBoss();
            } else {
                return; // player hasn't entered the arena yet
            }
        }

        // Phase-transition invulnerability countdown
        if (invulnerableTurnsLeft > 0) {
            invulnerableTurnsLeft--;
            if (invulnerableTurnsLeft == 0) {
                boss.invulnerable = false;
            }
            return;
        }

        // Check for phase 2 transition
        if (!phase2Triggered && boss.isAtPhase2Threshold()) {
            triggerPhase2();
            return;
        }

        // Refresh the live-adds count so the attack pattern (ORDER 4 HunterKillerPattern) can score
        // summon tactics. countLiveEnemies() includes the boss itself, so subtract it.
        boss.liveMinionCount = Math.max(0, enemyManager.countLiveEnemies() - 1);

        // Run boss AI pattern
        BossMove move = boss.activePattern().nextMove(boss, player, level, tickIndex);
        boss.ticksSinceAwaken++;

        // SOFT ENRAGE (order 6, RULE 3) — not a fail timer: once the fight drags past the upper turns cap
        // (1.5 * target) the boss's direct hits ramp up (BossStats.enrageDamageMultiplier), so turtling past
        // the survival-check math stops working. Announce the escalation ONCE so the ramp is telegraphed.
        announceEnrageIfDue();

        // ORDER 5 — turn the entity-layer heal signals into HUD banners + event text (the entity/render
        // split keeps LibGDX out of the boss/AI classes; the controller owns the presentation).
        announceHealSignals();

        executeBossMove(move, player, playerColumn, playerRow);

        // Death check (damage may have been applied by the player's weapon before this tick)
        if (!boss.isAlive()) {
            defeatBoss(player);
        }
    }

    private void awakeBoss() {
        boss.hasAwakened = true;
        boss.visualState = BossVisualState.STALKING;
        boss.alert();
        doorManager.lockArenaDoor(arenaDoorColumn, arenaDoorRow);
        if (bossHudRenderer != null) bossHudRenderer.showIntro();
        boss.phase1Pattern.onPhaseStart();
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor(boss.bossName.toUpperCase(), EventTextSystem.COLOR_GREEN);
        }
    }

    private void triggerPhase2() {
        phase2Triggered       = true;
        boss.phase            = 2;
        boss.invulnerable     = true;
        boss.visualState      = BossVisualState.ENRAGED;
        invulnerableTurnsLeft = Constants.BOSS_PHASE_TRANSITION_TURNS;
        boss.phase2Pattern.onPhaseStart();
        if (bossHudRenderer != null) bossHudRenderer.showBanner("PHASE 2");
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor("ENRAGED!", EventTextSystem.COLOR_GREEN);
        }
    }

    private void executeBossMove(BossMove move, Player player,
                                 int playerColumn, int playerRow) {
        // ORDER 8 — record the beat this move represents so the HUD state label reflects what the player
        // is seeing this turn. Planted-override branches below reset it to STALKING (a planted turn).
        boss.visualState = visualStateForMove(move);

        // Fairness contract F1 (ORDER 2): the boss must plant at least every
        // BOSS_MAX_CONSECUTIVE_MOVE_TURNS turns. If a pattern requests yet another move past that cap,
        // the controller overrides it to a HOLD (a planted "catch me" turn) so there is ALWAYS a punish
        // window. This is a hard backstop; the brain (ORDER 4) should respect it, but the controller
        // guarantees it regardless of pattern.
        // A "kite" move is a pure relocation the player can punish (DASH/REPOSITION). CHARGE is NOT a
        // kite: it is telegraph-gated and self-planting (it forces its own recovery), so it is excluded
        // from the kite cap — otherwise the cap could swallow a CHARGE and strand its armed corridor
        // telegraph unresolved. It IS still blocked during recovery below.
        boolean isKiteMove   = move.kind == BossMove.Kind.DASH || move.kind == BossMove.Kind.REPOSITION;
        boolean isRelocation = isKiteMove || move.kind == BossMove.Kind.CHARGE;
        if (isKiteMove && consecutiveMoveTurns >= Constants.BOSS_MAX_CONSECUTIVE_MOVE_TURNS) {
            consecutiveMoveTurns = 0;   // this HOLD counts as the plant
            boss.visualState = BossVisualState.STALKING;   // overridden to a planted HOLD
            return;                     // override to HOLD — no move this turn
        }

        // Fairness F1/F2 (ORDER 3): during CHARGE recovery the boss may not relocate. Any relocation
        // (including a follow-up charge) is overridden to a HOLD — the player's guaranteed punish
        // window; stationary verbs (telegraph / resolve / melee / summon / cast-hazard) still resolve.
        if (chargeRecoveryTurnsLeft > 0) {
            chargeRecoveryTurnsLeft--;
            if (isRelocation) {
                consecutiveMoveTurns = 0;   // the forced recovery HOLD counts as the plant
                boss.visualState = BossVisualState.STALKING;   // overridden to a planted recovery HOLD
                return;
            }
        }

        switch (move.kind) {
            case TELEGRAPH:
                // DangerTileSet already armed by the pattern; nothing more to do this tick
                consecutiveMoveTurns = 0;   // a plant — resets the fairness counter (F1)
                break;

            case RESOLVE:
                resolveDangerTiles(player, playerColumn, playerRow);
                consecutiveMoveTurns = 0;
                break;

            case REPOSITION:
                if (repositionBoss(playerColumn, playerRow)) {
                    consecutiveMoveTurns++;
                } else {
                    consecutiveMoveTurns = 0;   // blocked in → effectively a plant
                }
                break;

            case DASH:
                if (executeDash(move, playerColumn, playerRow)) {
                    consecutiveMoveTurns++;
                } else {
                    consecutiveMoveTurns = 0;   // fully boxed in → degrades to HOLD (a plant)
                }
                break;

            case CHARGE:
                executeCharge(move, player, playerColumn, playerRow);
                // The charge itself relocates, but its FORCED recovery IS the punish window, so it
                // resets the F1 counter — a charge is never the start of a kite chain (ORDER 3).
                consecutiveMoveTurns = 0;
                break;

            case CAST_HAZARD:
                castHazard(move);
                consecutiveMoveTurns = 0;
                break;

            case SUMMON:
                summonMinions(move, playerColumn, playerRow);
                consecutiveMoveTurns = 0;
                break;

            case MELEE:
                if (GameMath.manhattanDistanceTiles(
                        boss.tileColumn, boss.tileRow, playerColumn, playerRow) == 1) {
                    applyBossDamageToPlayer(player, move.tileDamage);
                }
                consecutiveMoveTurns = 0;
                break;

            case HEAL:
                // ORDER 5 — one repair tick: restore HP (clamped to maxHealth), no offense. The full-screen
                // HP bar reads boss.health directly, so it ticks up on its own. A repair tick is a plant.
                boss.health = Math.min(boss.maxHealth, boss.health + move.healAmount);
                consecutiveMoveTurns = 0;
                break;

            case TRANSITION:
            case NONE:
            default:
                consecutiveMoveTurns = 0;
                break;
        }
    }

    /**
     * ORDER 8 — maps the move the boss is executing this turn to its presentation beat for the HUD state
     * label. A bare {@code TELEGRAPH} is ambiguous (charge vs hazard vs summon), so it is disambiguated by
     * the kind of the DangerTileSet the pattern just armed. Presentation only — never read by the sim.
     */
    private BossVisualState visualStateForMove(BossMove move) {
        switch (move.kind) {
            case CHARGE:
                return BossVisualState.CHARGING;
            case CAST_HAZARD:
                return BossVisualState.CASTING;
            case SUMMON:
                return BossVisualState.SUMMONING;
            case HEAL:
                return BossVisualState.REPAIRING;
            case TELEGRAPH:
                switch (boss.dangerTileSet.getTelegraphKind()) {
                    case CHARGE_LINE:                  return BossVisualState.CHARGING;
                    case HAZARD_FIRE: case HAZARD_TOXIC: return BossVisualState.CASTING;
                    case SUMMON_RUNES:                 return BossVisualState.SUMMONING;
                    default:                           return BossVisualState.STALKING;
                }
            default:
                // DASH / REPOSITION / RESOLVE / MELEE / TRANSITION / NONE — all read as stalking pressure.
                return BossVisualState.STALKING;
        }
    }

    /**
     * ORDER 5 — consumes the one-shot heal signals raised by the entity/AI layer ({@link RegenState}) and
     * presents them: the "REPAIRING" banner when the repair chain starts, and "REPAIR INTERRUPTED" when a
     * hit cancels it (the interrupt is raised inside {@code Boss.applyDamage}). Each flag is cleared as it
     * is consumed, so a banner fires exactly once per event. Skipped when the boss is already dead, so a
     * killing blow that also interrupts a repair does not flash a stray banner over the death line.
     */
    private void announceHealSignals() {
        if (!boss.isAlive()) return;
        RegenState regenState = boss.regenState;
        if (regenState.healJustStarted) {
            regenState.healJustStarted = false;
            if (bossHudRenderer != null) bossHudRenderer.showBanner("REPAIRING");
            if (eventTextSystem != null) {
                eventTextSystem.spawnWithColor("OVERSEER REPAIRING", EventTextSystem.COLOR_GREEN);
            }
        }
        if (regenState.healJustInterrupted) {
            regenState.healJustInterrupted = false;
            if (bossHudRenderer != null) bossHudRenderer.showBanner("REPAIR INTERRUPTED");
            if (eventTextSystem != null) {
                eventTextSystem.spawnWithColor("REPAIR INTERRUPTED", EventTextSystem.COLOR_GREEN);
            }
        }
    }

    private void resolveDangerTiles(Player player, int playerColumn, int playerRow) {
        DangerTileSet dangerTileSet = boss.dangerTileSet;
        if (!dangerTileSet.isActive()) return;
        if (dangerTileSet.contains(playerColumn, playerRow)) {
            applyBossDamageToPlayer(player, dangerTileSet.getDamage());
        }
    }

    /**
     * Applies a DIRECT boss hit to the player, scaled by the soft-enrage multiplier (order 6). Every direct
     * boss hit (melee slam, telegraphed charge/danger-tile resolve) funnels through here so the enrage ramp
     * is applied uniformly; lingering hazard DOT is owned by HazardManager and intentionally NOT ramped.
     */
    private void applyBossDamageToPlayer(Player player, int baseDamage) {
        int scaled = baseDamage;
        if (boss.stats != null) {
            scaled = Math.round(baseDamage * boss.stats.enrageDamageMultiplier(boss.ticksSinceAwaken));
        }
        player.applyDamage(scaled);
    }

    /**
     * Fires the one-time "FURIOUS" escalation banner (order 6, soft enrage) the first turn the fight passes
     * the upper turns cap. Presentation only — the actual damage ramp lives in {@link #applyBossDamageToPlayer}.
     */
    private void announceEnrageIfDue() {
        if (enrageAnnounced || boss.stats == null) return;
        if (boss.ticksSinceAwaken <= boss.stats.upperFightTurnsCap) return;
        enrageAnnounced = true;
        if (bossHudRenderer != null) bossHudRenderer.showBanner("FURIOUS");
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor(boss.bossName.toUpperCase() + " IS FURIOUS", EventTextSystem.COLOR_GREEN);
        }
    }

    /**
     * Single-tile chase nudge toward the player (ORDER 2: generalised to animate a visible slide).
     * Picks the cardinal neighbour that most reduces Manhattan distance to the player, commits the
     * logical move, and plays a one-step cosmetic slide so even a single-tile reposition never pops.
     * Returns true when the boss actually moved.
     */
    private boolean repositionBoss(int playerColumn, int playerRow) {
        int bestColumn   = boss.tileColumn;
        int bestRow      = boss.tileRow;
        int bestDistance = GameMath.manhattanDistanceTiles(
                boss.tileColumn, boss.tileRow, playerColumn, playerRow);
        boolean moved    = false;

        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            int targetColumn = boss.tileColumn + STEP_COLUMNS[directionIndex];
            int targetRow    = boss.tileRow    + STEP_ROWS[directionIndex];
            if (!isBossStepPassable(targetColumn, targetRow, playerColumn, playerRow)) continue;
            int distance = GameMath.manhattanDistanceTiles(targetColumn, targetRow, playerColumn, playerRow);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestColumn   = targetColumn;
                bestRow      = targetRow;
                moved        = true;
            }
        }

        if (!moved) return false;

        // Record origin → destination for the cosmetic slide, then commit the logical move.
        movePathColumns[0] = boss.tileColumn;
        movePathRows[0]    = boss.tileRow;
        movePathColumns[1] = bestColumn;
        movePathRows[1]    = bestRow;
        enemyManager.tryPushEnemy(boss, bestColumn, bestRow);
        boss.beginSlide(movePathColumns, movePathRows, 2, Constants.BOSS_DASH_ANIM_DURATION);
        return true;
    }

    /**
     * Executes a multi-tile DASH toward the move's absolute target tile (ORDER 2). Greedily path-steps
     * one cardinal tile at a time, reducing Manhattan distance to the target, up to BOSS_MAX_DASH_TILES
     * steps, stopping at the first tile that fails isBossStepPassable (wall/door/occupied/player tile).
     * The larger remaining axis is tried first so the move looks purposeful; if that axis is blocked the
     * other axis is tried (a tiny greedy stepper — NOT full A*, so it stays allocation-free and readable
     * for the player). The whole path (origin first) is recorded, the LOGICAL tile jumps to the final
     * reached tile at once (turn model stays clean), and a cosmetic slide sweeps the sprite across the
     * crossed tiles (fairness contract F3 — never a teleport). Returns false when zero steps were
     * possible (fully boxed in) so the caller degrades the DASH to a HOLD.
     */
    private boolean executeDash(BossMove move, int playerColumn, int playerRow) {
        int currentColumn = boss.tileColumn;
        int currentRow    = boss.tileRow;
        movePathColumns[0] = currentColumn;
        movePathRows[0]    = currentRow;
        int pathLength = 1;

        for (int step = 0; step < Constants.BOSS_MAX_DASH_TILES; step++) {
            int differenceColumn = move.dashTargetColumn - currentColumn;
            int differenceRow    = move.dashTargetRow    - currentRow;
            if (differenceColumn == 0 && differenceRow == 0) break;   // reached the target

            int stepColumn = 0;
            int stepRow    = 0;
            // Prefer the axis with the larger remaining delta first (feels purposeful).
            if (Math.abs(differenceColumn) >= Math.abs(differenceRow)) {
                stepColumn = Integer.signum(differenceColumn);
            } else {
                stepRow = Integer.signum(differenceRow);
            }

            int nextColumn = currentColumn + stepColumn;
            int nextRow    = currentRow    + stepRow;
            if (!isBossStepPassable(nextColumn, nextRow, playerColumn, playerRow)) {
                // Preferred axis blocked — try the other axis for this step.
                if (stepColumn != 0) {
                    stepColumn = 0;
                    stepRow    = Integer.signum(differenceRow);
                } else {
                    stepRow    = 0;
                    stepColumn = Integer.signum(differenceColumn);
                }
                nextColumn = currentColumn + stepColumn;
                nextRow    = currentRow    + stepRow;
                if ((stepColumn == 0 && stepRow == 0)
                        || !isBossStepPassable(nextColumn, nextRow, playerColumn, playerRow)) {
                    break;   // both axes blocked — stop here
                }
            }

            currentColumn = nextColumn;
            currentRow    = nextRow;
            movePathColumns[pathLength] = currentColumn;
            movePathRows[pathLength]    = currentRow;
            pathLength++;
        }

        if (pathLength < 2) return false;   // no passable step — fully boxed in, degrade to HOLD

        // Commit the logical move to the final reached tile (single occupancy update) and animate.
        enemyManager.tryPushEnemy(boss, currentColumn, currentRow);
        boss.beginSlide(movePathColumns, movePathRows, pathLength, Constants.BOSS_DASH_ANIM_DURATION);
        return true;
    }

    /**
     * Executes a CHARGE lunge (ORDER 3) — the "hit then flee" signature move. The previous turn's
     * TELEGRAPH armed the boss's DangerTileSet with the corridor tiles + damage; this turn:
     *   1. RESOLVE the hit — any player standing on a marked corridor tile takes the telegraphed damage,
     *      then the marks are cleared (the corridor is now spent).
     *   2. LUNGE — slide the boss up to move.chargeRangeTiles along the cardinal charge axis, stopping
     *      at the first blocked tile, ending on the far side of the lane. Reuses the ORDER 2 slide so
     *      the lunge is a visible sprint, never a teleport (fairness F3).
     *   3. Face the charge axis (visual + backstab facing) and arm the forced recovery plant so the
     *      player gets a guaranteed punish window next turn (fairness F1/F2).
     * The damage comes entirely from the armed DangerTileSet, so it lands even when the boss is boxed in
     * and cannot slide a single tile.
     */
    private void executeCharge(BossMove move, Player player, int playerColumn, int playerRow) {
        // Step 1 — resolve the telegraphed corridor hit, then clear it.
        resolveDangerTiles(player, playerColumn, playerRow);
        boss.dangerTileSet.clear();

        // Face the lunge axis regardless of whether the slide is blocked (visual intent).
        if (move.chargeDirectionColumn != 0 || move.chargeDirectionRow != 0) {
            boss.facingColumn = move.chargeDirectionColumn;
            boss.facingRow    = move.chargeDirectionRow;
        }

        // Step 2 — slide along the axis to the last passable tile (capped at the charge range).
        int currentColumn = boss.tileColumn;
        int currentRow    = boss.tileRow;
        movePathColumns[0] = currentColumn;
        movePathRows[0]    = currentRow;
        int pathLength = 1;
        int rangeTiles = Math.min(move.chargeRangeTiles, Constants.BOSS_MAX_SLIDE_TILES);
        for (int step = 0; step < rangeTiles; step++) {
            int nextColumn = currentColumn + move.chargeDirectionColumn;
            int nextRow    = currentRow    + move.chargeDirectionRow;
            if (!isBossStepPassable(nextColumn, nextRow, playerColumn, playerRow)) break;
            currentColumn = nextColumn;
            currentRow    = nextRow;
            movePathColumns[pathLength] = currentColumn;
            movePathRows[pathLength]    = currentRow;
            pathLength++;
        }

        if (pathLength >= 2) {
            enemyManager.tryPushEnemy(boss, currentColumn, currentRow);
            boss.beginSlide(movePathColumns, movePathRows, pathLength, Constants.BOSS_DASH_ANIM_DURATION);
        }

        // Step 3 — force the recovery plant (the punish window). Applies even when boxed in: the hit
        // still landed, so the boss must recover regardless.
        chargeRecoveryTurnsLeft = EnemyConstants.OVERSEER_CHARGE_RECOVERY_TURNS;
    }

    /**
     * Seeds a terrain hazard on the tiles armed by the previous turn's TELEGRAPH (ORDER 3). Places the
     * move's hazard kind via HazardManager on every currently-marked DangerTileSet tile — so the placed
     * hazard exactly matches what the player saw telegraphed — then clears the marks. Not a direct hit:
     * HazardManager owns the lingering damage-over-time from here. HazardManager.ignite* silently
     * ignores tiles that are not eligible floor, so walls/props in the footprint simply take no hazard.
     */
    private void castHazard(BossMove move) {
        DangerTileSet dangerTileSet = boss.dangerTileSet;
        if (dangerTileSet.isActive()) {
            List<DangerTileSet.MarkedTile> tiles = dangerTileSet.getTiles();
            for (int index = 0; index < tiles.size(); index++) {
                DangerTileSet.MarkedTile tile = tiles.get(index);
                if (move.hazardKind == BossMove.HazardKind.FIRE) {
                    hazardManager.igniteFire(tile.tileColumn, tile.tileRow);
                } else {
                    hazardManager.igniteToxic(tile.tileColumn, tile.tileRow);
                }
            }
        }
        dangerTileSet.clear();
    }

    private boolean isBossStepPassable(int targetColumn, int targetRow,
                                       int playerColumn, int playerRow) {
        if (targetColumn < 0 || targetColumn >= level.getWidth())  return false;
        if (targetRow    < 0 || targetRow    >= level.getHeight()) return false;
        if (level.isBlockedAt(targetColumn, targetRow, doorManager)) return false;
        if (targetColumn == playerColumn && targetRow == playerRow) return false;
        if (enemyManager.isTileOccupiedByEnemy(targetColumn, targetRow)) return false;
        return true;
    }

    private void summonMinions(BossMove move, int playerColumn, int playerRow) {
        int minionsBefore = enemyManager.countLiveEnemies() - 1; // subtract the boss itself
        // Cap is carried per-move so each boss keeps its own ceiling (Overseer OVERSEER_ADDS_CAP,
        // Corruptor CORRUPTOR_MINION_CAP) instead of sharing one hardcoded limit (fairness F5).
        if (minionsBefore >= move.summonCap) return;

        int spawned = 0;
        for (int directionIndex = 0; directionIndex < 4 && spawned < move.summonCount; directionIndex++) {
            int targetColumn = boss.tileColumn + STEP_COLUMNS[directionIndex];
            int targetRow    = boss.tileRow    + STEP_ROWS[directionIndex];
            if (targetColumn == playerColumn && targetRow == playerRow) continue;
            if (level.isBlockedAt(targetColumn, targetRow, doorManager)) continue;
            if (enemyManager.isTileOccupiedByEnemy(targetColumn, targetRow)) continue;
            // spawnBossMinion (not spawnEnemy) tags the add as boss-summoned so the enemy-death drop
            // path can grant the guaranteed ammo lifeline in this sealed arena (ORDER 6, Fairness F5).
            enemyManager.spawnBossMinion(move.summonType, targetColumn, targetRow, boss.dungeonLevel);
            spawned++;
        }
    }

    private void defeatBoss(Player player) {
        bossDefeated = true;
        boss.dangerTileSet.clear();

        // Restore the exit stairs
        level.setCell(exitColumn, exitRow, '>');

        // Unlock the arena door
        doorManager.unlockArenaDoor(arenaDoorColumn, arenaDoorRow);

        // Hide the boss HUD overlay
        if (bossHudRenderer != null) bossHudRenderer.hide();

        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor(boss.killLine, EventTextSystem.COLOR_GREEN);
        }
    }

    /** True after the boss has been defeated; used by World to skip further updates. */
    public boolean isBossDefeated() { return bossDefeated; }
}
