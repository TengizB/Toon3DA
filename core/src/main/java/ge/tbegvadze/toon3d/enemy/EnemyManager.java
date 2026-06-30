package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.entity.EnemyHitTarget;
import ge.tbegvadze.toon3d.entity.ImpactEventListener;
import ge.tbegvadze.toon3d.entity.Loadout;
import ge.tbegvadze.toon3d.entity.boss.Boss;
import ge.tbegvadze.toon3d.entity.MeleeWeapon;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.level.EnemySpawnPoint;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.progression.KillCreditListener;
import ge.tbegvadze.toon3d.progression.KillEventListener;
import ge.tbegvadze.toon3d.progression.KillXpListener;
import ge.tbegvadze.toon3d.status.StatusEffectController;
import ge.tbegvadze.toon3d.status.StatusResistance;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Owns all enemy instances and drives the turn-based enemy simulation.
 *
 * Each call to takeTurn() runs three phases:
 *   A  — Perception: dormant enemies check proximity and LOS; newly alerted are queued.
 *   A2 — Chain alert: BFS propagation from newly-alerted seeds.
 *   B  — Action: every alerted enemy moves or attacks.
 *
 * Zero allocations inside takeTurn() or render() — all scratch arrays pre-allocated in constructor.
 * Implements EnemyHitTarget so Weapon.fire() can query and damage enemies.
 */
public final class EnemyManager implements EnemyHitTarget {

    /** Notified whenever a drop tile is stamped onto the level grid after an enemy dies. */
    public interface DropPlacedListener {
        void onDropPlaced(int tileColumn, int tileRow, char dropChar);
    }

    /**
     * Notified when an enemy dies so area-denial archetypes can leave a hazard behind
     * (balance idea 4, Pillar 2/3 — the Plague Hulk's toxic death cloud).
     */
    public interface EnemyDeathHazardListener {
        void onEnemyDied(EnemyType type, int tileColumn, int tileRow);
    }

    private final List<Enemy> enemies;
    private final Level       level;
    private final DoorManager doorManager;
    private ImpactEventListener impactEventListener;
    private KillXpListener      killXpListener;
    private KillEventListener   killEventListener;
    private KillCreditListener  killCreditListener;
    private DropPlacedListener  dropPlacedListener;
    private EnemyDeathHazardListener enemyDeathHazardListener;
    private EnemyAttackListener enemyAttackListener;

    /** Flat damage bonus from player level-up damage cards (Hollow Points / Glass Cannon); added to every hit. */
    private int playerFlatDamageBonus = 0;

    /**
     * Player ranged/melee damage multipliers derived from MARKSMANSHIP/STRENGTH (attribute system
     * + level-up cards). Applied centrally here — the single point that knows whether the hit was
     * melee ({@code pendingMeleeKill}) or ranged — so no weapon double-counts them. Default 1.0.
     */
    private float playerRangedDamageMultiplier = 1.0f;
    private float playerMeleeDamageMultiplier  = 1.0f;

    // Pre-allocated scratch state — never re-allocated after construction
    private final boolean[][]  occupancy;           // [column][row] — true if an enemy is there this turn
    private final int[]        chainAlertQueue;     // BFS queue of enemy indices for chain-alert
    private final int[]        wiggleLegalColumns;  // reused in wiggleStep — avoids allocation in takeTurn
    private final int[]        wiggleLegalRows;
    private final Random       wiggleRandom;
    private final Random       dropRandom;
    private final Random       effectRandom;

    private boolean anyAlertedEver = false;

    // Stored so killCreditListener can scale credit rewards by dungeon depth at kill time.
    private final int currentDepth;

    // Set to true by notifyMeleeAttack() before applyDamageTo(); consumed and reset inside killEnemy().
    private boolean pendingMeleeKill = false;
    // Injected by World so melee kills can drop ammo matching the player's equipped ranged weapons.
    private Loadout loadout = null;

    private StatusEffectController statusEffectController = null;

    /**
     * @param dungeonDepth current floor number (1-based); drives enemy health and damage scaling.
     *                     Pass 1 for the first floor.
     */
    public EnemyManager(Level level, DoorManager doorManager, int dungeonDepth) {
        this.level        = level;
        this.doorManager  = doorManager;
        this.currentDepth = dungeonDepth;
        this.enemies      = buildInitialEnemies(level.getEnemySpawnPoints(), dungeonDepth, new Random());
        int levelWidth   = level.getWidth();
        int levelHeight  = level.getHeight();
        int enemyCount   = enemies.size();
        this.occupancy          = new boolean[levelWidth][levelHeight];
        this.chainAlertQueue    = new int[Math.max(1, enemyCount)];
        this.wiggleLegalColumns = new int[4];
        this.wiggleLegalRows    = new int[4];
        this.wiggleRandom       = new Random(12345L);
        this.dropRandom         = new Random();
        this.effectRandom       = new Random();
    }

    /** Injects the status effect controller so ranged enemies can inflict DoT on the player. */
    public void setStatusEffectController(StatusEffectController controller) {
        this.statusEffectController = controller;
    }

    /**
     * Injects the player's loadout so melee kills can drop ammo that matches
     * one of the player's equipped ranged weapons instead of a fixed enemy-type drop.
     */
    public void setLoadout(Loadout playerLoadout) {
        this.loadout = playerLoadout;
    }

    @Override
    public void notifyMeleeAttack() {
        pendingMeleeKill = true;
    }

    @Override
    public boolean isAtFullHp(Object enemyObject) {
        Enemy enemy = (Enemy) enemyObject;
        return enemy.health >= enemy.maxHealth;
    }

    private static List<Enemy> buildInitialEnemies(List<EnemySpawnPoint> spawnPoints,
                                                    int dungeonDepth, Random spawnVariance) {
        List<Enemy> list = new ArrayList<>(spawnPoints.size());
        for (EnemySpawnPoint spawnPoint : spawnPoints) {
            EnemyType type;
            switch (spawnPoint.spawnChar) {
                case '1': type = EnemyType.PLAGUE_HULK;   break;
                case '2': type = EnemyType.EYE_TYRANT;    break;
                case '3': type = EnemyType.GORE_BITER;    break;
                case '4': type = EnemyType.SHELL_BRUTE;   break;
                case '5': type = EnemyType.MIRE_WRAITH;   break;
                case '!': type = EnemyType.IRON_STALKER;  break;
                case '$': type = EnemyType.ACID_DRONE;    break;
                case '^': type = EnemyType.VOID_SHROUD;   break;
                case '~': type = EnemyType.GHOUL;            break;
                case 'z': type = EnemyType.CRAWLER;          break;
                case 'K': type = EnemyType.REVENANT;         break;
                case 'V': type = EnemyType.VORTEX_EYE;       break;
                case '*': type = EnemyType.BLIGHT_CORRUPTOR; break;
                case 'n': continue; // Boss spawn — BossFloorController seeds the correct boss by depth
                default:  type = EnemyType.PLAGUE_HULK;   break;
            }
            // Small chance to spawn a lower-level enemy for variety at higher depths.
            int effectiveDepth = dungeonDepth;
            if (dungeonDepth > 2) {
                float roll = spawnVariance.nextFloat();
                if (roll < 0.05f) {
                    effectiveDepth = Math.max(1, dungeonDepth - 2);
                } else if (roll < 0.15f) {
                    effectiveDepth = Math.max(1, dungeonDepth - 1);
                }
            }
            list.add(initScaledEnemy(type, spawnPoint.tileColumn, spawnPoint.tileRow, effectiveDepth));
        }
        return list;
    }

    /** Creates and fully initialises a depth-scaled Enemy instance. */
    private static Enemy initScaledEnemy(EnemyType type, int tileColumn, int tileRow, int effectiveDepth) {
        float healthScale = GameBalance.enemyHealthScaleForDepth(effectiveDepth);
        float damageScale = GameBalance.enemyDamageScaleForDepth(effectiveDepth);
        Enemy enemy = new Enemy(type, tileColumn, tileRow);
        int scaledHealth = Math.max(1, Math.round(type.maxHealth() * healthScale));
        enemy.maxHealth              = scaledHealth;
        enemy.health                 = scaledHealth;
        enemy.attackDamageMultiplier = damageScale;
        enemy.dungeonLevel           = effectiveDepth;
        enemy.nameTag                = type.displayName() + " LVL " + effectiveDepth;
        enemy.setStatusResistance(buildArchetypeResistance(type));
        return enemy;
    }

    /*
     * Builds the per-archetype StatusResistance table.
     * Values sourced from roguelike_order_8 and roguelike_order_13 design docs:
     *   VOID_SHROUD  — fire-immune (shadow entity; fire slides off)
     *   MIRE_WRAITH  — poison-immune (saturated in acid; own toxin does nothing)
     *   ACID_DRONE   — poison-immune (mechanical; impervious to biological agents)
     *   SHELL_BRUTE  — stun duration halved (heavy armoured frame resists concussive effects)
     *   EYE_TYRANT   — half fire damage (demon-origin; partially acclimated to heat)
     *   OVERSEER / CORRUPTOR / HELL_BARON — bosses are stun-immune; too powerful to chain-lock
     */
    private static StatusResistance buildArchetypeResistance(EnemyType type) {
        switch (type) {
            case VOID_SHROUD:
                return StatusResistance.builder()
                        .immune(StatusType.BURNING)
                        .build();
            case MIRE_WRAITH:
                return StatusResistance.builder()
                        .immune(StatusType.POISONED)
                        .build();
            case ACID_DRONE:
                return StatusResistance.builder()
                        .immune(StatusType.POISONED)
                        .damageMultiplier(StatusType.BURNING, 0.5f)
                        .build();
            case SHELL_BRUTE:
                return StatusResistance.builder()
                        .durationMultiplier(StatusType.STUNNED, 0.5f)
                        .build();
            case EYE_TYRANT:
                return StatusResistance.builder()
                        .damageMultiplier(StatusType.BURNING, 0.5f)
                        .build();
            case OVERSEER:
            case CORRUPTOR:
            case HELL_BARON:
                return StatusResistance.builder()
                        .immune(StatusType.STUNNED)
                        .build();
            default:
                return StatusResistance.defaultResistance();
        }
    }

    /** Wires the visual-effect system so every hit/kill fires cosmetic events. */
    public void setImpactEventListener(ImpactEventListener listener) {
        this.impactEventListener = listener;
    }

    /** Wires the XP system so every kill awards experience to the player. */
    public void setKillXpListener(KillXpListener listener) {
        this.killXpListener = listener;
    }

    /** Wires the kill-message system so every kill fires a display notification with name + XP. */
    public void setKillEventListener(KillEventListener listener) {
        this.killEventListener = listener;
    }

    /** Wires the credit system so every kill awards scaled credits to the player. */
    public void setKillCreditListener(KillCreditListener listener) {
        this.killCreditListener = listener;
    }

    /** Notified when a drop tile is stamped on the grid so renderers can display it immediately. */
    public void setDropPlacedListener(DropPlacedListener listener) {
        this.dropPlacedListener = listener;
    }

    /** Wires the area-denial death hook so e.g. a Plague Hulk leaves a toxic cloud where it dies. */
    public void setEnemyDeathHazardListener(EnemyDeathHazardListener listener) {
        this.enemyDeathHazardListener = listener;
    }

    /** Injects the attack effect listener so enemy attacks spawn projectile/lunge visuals. */
    public void setEnemyAttackListener(EnemyAttackListener listener) {
        this.enemyAttackListener = listener;
    }

    /**
     * Sets the flat damage bonus from player progression.  Added to every weapon shot
     * that hits an enemy.  Call after each level-up reward and after each floor rebuild.
     */
    public void setPlayerFlatDamageBonus(int bonus) {
        this.playerFlatDamageBonus = bonus;
    }

    /**
     * Sets the player's ranged damage multiplier (from MARKSMANSHIP + level-up cards). Applied to
     * non-melee hits in {@link #applyDamageTo}. Call after each level-up and after each floor rebuild.
     */
    public void setPlayerRangedDamageMultiplier(float multiplier) {
        this.playerRangedDamageMultiplier = multiplier;
    }

    /**
     * Sets the player's melee damage multiplier (from STRENGTH + level-up cards). Applied to melee
     * hits in {@link #applyDamageTo}. Call after each level-up and after each floor rebuild.
     */
    public void setPlayerMeleeDamageMultiplier(float multiplier) {
        this.playerMeleeDamageMultiplier = multiplier;
    }

    /** Read-only view of the live enemy list; used by EnemyRenderer each frame. */
    public List<Enemy> getEnemies() {
        return enemies;
    }

    /**
     * Spawns a new enemy of the given type at the specified tile.
     * Used by BossFloorController to summon minions during boss encounters.
     * Stats are scaled using the supplied dungeonDepth.
     * No-op if the tile is out of bounds, already occupied, or blocked.
     */
    public void spawnEnemy(EnemyType type, int tileColumn, int tileRow, int dungeonDepth) {
        if (tileColumn < 0 || tileColumn >= level.getWidth())  return;
        if (tileRow    < 0 || tileRow    >= level.getHeight()) return;
        if (level.isBlockedAt(tileColumn, tileRow, doorManager)) return;
        if (isTileOccupiedByEnemy(tileColumn, tileRow)) return;
        Enemy enemy = initScaledEnemy(type, tileColumn, tileRow, dungeonDepth);
        enemy.alert();
        enemies.add(enemy);
        occupancy[tileColumn][tileRow] = true;
    }

    /** True as soon as any enemy has been alerted; drives World.gameState.redAlert. */
    public boolean anyAlerted() {
        return anyAlertedEver;
    }

    // -------------------------------------------------------------------------
    // EnemyHitTarget implementation — called by Weapon.fire()
    // -------------------------------------------------------------------------

    @Override
    public Object enemyAt(int tileColumn, int tileRow) {
        for (int index = 0; index < enemies.size(); index++) {
            Enemy enemy = enemies.get(index);
            if (enemy.isAlive() && enemy.tileColumn == tileColumn && enemy.tileRow == tileRow) {
                return enemy;
            }
        }
        return null;
    }

    @Override
    public void applyDamageTo(Object enemyObject, int amount) {
        Enemy enemy = (Enemy) enemyObject;
        // Consume and reset the melee flag unconditionally — a non-lethal melee hit
        // must not carry the flag forward to the next (possibly ranged) killing blow.
        boolean thisKillWasMelee = pendingMeleeKill;
        pendingMeleeKill = false;

        float worldX           = enemy.worldCenterX();
        float worldY           = enemy.worldCenterY();
        float heightMultiplier = enemy.type.heightMultiplier();

        // Scale the weapon's base damage by the player's melee/ranged multiplier (STRENGTH /
        // MARKSMANSHIP + level-up cards), then add the flat per-shot bonus. This is the single
        // application point, so no weapon double-counts these multipliers.
        float damageMultiplier = thisKillWasMelee ? playerMeleeDamageMultiplier : playerRangedDamageMultiplier;
        int totalDamage = Math.round(amount * damageMultiplier) + playerFlatDamageBonus;
        enemy.applyDamage(totalDamage);
        enemy.triggerHitFlash();

        if (!enemy.isAlive()) {
            int xpAwarded = enemy.type.baseXpReward();
            if (killXpListener != null) {
                killXpListener.onEnemyKilledForXp(xpAwarded);
            }
            if (killEventListener != null) {
                killEventListener.onEnemyKilled(enemy.nameTag, xpAwarded);
            }
            if (killCreditListener != null) {
                killCreditListener.onEnemyKilledForCredits(enemy.type.baseCreditReward(), currentDepth);
            }
            killEnemy(enemy, thisKillWasMelee);
            if (impactEventListener != null) {
                impactEventListener.onEnemyKilled(worldX, worldY, heightMultiplier, totalDamage);
            }
        } else {
            if (impactEventListener != null) {
                impactEventListener.onEnemyHit(worldX, worldY, heightMultiplier, totalDamage);
            }
        }
    }

    /** Advances hit-flash and attack animation timers for all enemies. Call once per frame from World.update(). */
    public void advanceHitFlash(float deltaTime) {
        for (int index = 0; index < enemies.size(); index++) {
            Enemy enemy = enemies.get(index);
            enemy.advanceHitFlash(deltaTime);
            enemy.advanceAttackAnim(deltaTime);
        }
    }

    // -------------------------------------------------------------------------
    // Turn simulation
    // -------------------------------------------------------------------------

    public void takeTurn(int playerColumn, int playerRow, Player player) {
        rebuildOccupancy();
        phaseA(playerColumn, playerRow);
        phaseB(playerColumn, playerRow, player);
    }

    private void rebuildOccupancy() {
        int levelWidth  = level.getWidth();
        int levelHeight = level.getHeight();
        for (int column = 0; column < levelWidth; column++) {
            for (int row = 0; row < levelHeight; row++) {
                occupancy[column][row] = false;
            }
        }
        for (int index = 0; index < enemies.size(); index++) {
            Enemy enemy = enemies.get(index);
            if (enemy.isAlive()) {
                occupancy[enemy.tileColumn][enemy.tileRow] = true;
            }
        }
    }

    // Phase A: proximity + LOS wake, then chain-alert BFS
    private void phaseA(int playerColumn, int playerRow) {
        int chainQueueSize = 0;

        for (int index = 0; index < enemies.size(); index++) {
            Enemy enemy = enemies.get(index);
            if (!enemy.isAlive() || enemy.isAlerted()) continue;

            int chebyshev = GameMath.chebyshevDistanceTiles(
                    enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);

            boolean shouldAlert = false;
            if (chebyshev <= EnemyConstants.ALERT_RADIUS_TILES) {
                shouldAlert = true;
            } else if (chebyshev <= EnemyConstants.LOS_MAX_RANGE_TILES) {
                shouldAlert = hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
            }

            if (shouldAlert) {
                enemy.alert();
                anyAlertedEver = true;
                chainAlertQueue[chainQueueSize++] = index;
            }
        }

        // BFS chain propagation
        int chainQueueHead = 0;
        while (chainQueueHead < chainQueueSize) {
            Enemy alerter = enemies.get(chainAlertQueue[chainQueueHead++]);
            for (int index = 0; index < enemies.size(); index++) {
                Enemy candidate = enemies.get(index);
                if (!candidate.isAlive() || candidate.isAlerted()) continue;
                int chainDist = GameMath.chebyshevDistanceTiles(
                        candidate.tileColumn, candidate.tileRow,
                        alerter.tileColumn,   alerter.tileRow);
                if (chainDist <= EnemyConstants.CHAIN_ALERT_RADIUS_TILES) {
                    candidate.alert();
                    anyAlertedEver = true;
                    chainAlertQueue[chainQueueSize++] = index;
                }
            }
        }
    }

    // Phase B: every alerted enemy acts once (stunned enemies skip their action this turn)
    private void phaseB(int playerColumn, int playerRow, Player player) {
        for (int index = 0; index < enemies.size(); index++) {
            Enemy enemy = enemies.get(index);
            if (!enemy.isAlive() || !enemy.isAlerted()) continue;
            // Boss AI is driven entirely by BossFloorController; skip it here.
            if (enemy instanceof Boss) continue;
            if (enemy.skipNextAction) {
                enemy.skipNextAction = false;
                enemy.turnCounter++;
                continue;
            }
            enemy.turnCounter++;
            actEnemy(enemy, playerColumn, playerRow, player);
        }
    }

    /** Returns the total count of live (non-dead) enemies, including the boss if present. */
    public int countLiveEnemies() {
        int count = 0;
        for (int index = 0; index < enemies.size(); index++) {
            if (enemies.get(index).isAlive()) count++;
        }
        return count;
    }

    /**
     * Registers an already-constructed Boss into the live enemy list.
     * Stats (maxHealth, attackDamageMultiplier, nameTag) must be set on the Boss before calling.
     * Called by World.buildLevelDependentResources() for boss floors; the Boss is then
     * managed by BossFloorController while EnemyManager handles the regular enemy roster.
     * No-op if the tile is out of bounds or already occupied.
     */
    public void addBoss(Boss boss) {
        if (boss.tileColumn < 0 || boss.tileColumn >= level.getWidth())  return;
        if (boss.tileRow    < 0 || boss.tileRow    >= level.getHeight()) return;
        if (level.isBlockedAt(boss.tileColumn, boss.tileRow, doorManager)) return;
        if (isTileOccupiedByEnemy(boss.tileColumn, boss.tileRow)) return;
        enemies.add(boss);
        occupancy[boss.tileColumn][boss.tileRow] = true;
    }

    private void actEnemy(Enemy enemy, int playerColumn, int playerRow, Player player) {
        int chebyshev = GameMath.chebyshevDistanceTiles(
                enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);

        if (!enemy.type.isRanged()) {
            // Melee archetypes branch to their tactical VERB (balance idea 4, Pillar 2):
            //   SHELL_BRUTE — CHARGER: telegraphed rush; VOID_SHROUD — FLANKER: attack the blind
            //   side; everything else uses the shared greedy melee behaviour.
            if (enemy.type == EnemyType.SHELL_BRUTE) {
                actChargerEnemy(enemy, playerColumn, playerRow, player);
            } else if (enemy.type == EnemyType.VOID_SHROUD) {
                actFlankerEnemy(enemy, playerColumn, playerRow, player);
            } else {
                actMeleeEnemy(enemy, playerColumn, playerRow, player);
            }
        } else {
            // Ranged kiting — used by EYE_TYRANT, MIRE_WRAITH, ACID_DRONE
            actRangedEnemy(enemy, playerColumn, playerRow, player, chebyshev);
        }
    }

    /** Shared greedy melee: hit when cardinally adjacent, otherwise close the gap. */
    private void actMeleeEnemy(Enemy enemy, int playerColumn, int playerRow, Player player) {
        boolean cardinalAdjacent = GameMath.manhattanDistanceTiles(
                enemy.tileColumn, enemy.tileRow, playerColumn, playerRow) == 1;
        if (cardinalAdjacent) {
            player.applyDamage(enemy.scaledAttackDamage());
            enemy.state = EnemyState.ATTACKING;
            enemy.triggerAttackAnim();
            if (enemyAttackListener != null) enemyAttackListener.onMeleeAttack(enemy);
        } else {
            if (enemy.shouldMoveThisTurn()) {
                stepToward(enemy, playerColumn, playerRow);
            }
            enemy.state = EnemyState.CHASING;
        }
    }

    /**
     * CHARGER verb (Shell Brute, Pillar 2): when a clear cardinal lane opens within charge range
     * the brute spends ONE telegraphed wind-up turn (no move, readable rim flash), then on the
     * next turn rushes straight down that lane. A connecting rush hits for a high multiple of its
     * base damage; if the player sidesteps off the lane the rush whiffs and the brute is left in
     * an exposed recovery (skips its next action) — "sidestep the charge, then punish."
     */
    private void actChargerEnemy(Enemy enemy, int playerColumn, int playerRow, Player player) {
        // Resolve a pending wind-up: this IS the rush turn.
        if (enemy.chargeWindUpTurns > 0) {
            enemy.chargeWindUpTurns = 0;
            boolean connected = performCharge(enemy, playerColumn, playerRow, player);
            if (!connected) {
                enemy.skipNextAction = true; // committed rush missed — punishable recovery
            }
            return;
        }

        boolean cardinalAdjacent = GameMath.manhattanDistanceTiles(
                enemy.tileColumn, enemy.tileRow, playerColumn, playerRow) == 1;
        if (cardinalAdjacent) {
            // No room to build up a charge — just hit at normal strength.
            player.applyDamage(enemy.scaledAttackDamage());
            enemy.state = EnemyState.ATTACKING;
            enemy.triggerAttackAnim();
            if (enemyAttackListener != null) enemyAttackListener.onMeleeAttack(enemy);
            return;
        }

        if (canBeginCharge(enemy, playerColumn, playerRow)) {
            enemy.chargeWindUpTurns      = 1;
            enemy.chargeDirectionColumn  = Integer.signum(playerColumn - enemy.tileColumn);
            enemy.chargeDirectionRow     = Integer.signum(playerRow - enemy.tileRow);
            enemy.state                  = EnemyState.WINDING_UP;
            enemy.triggerTelegraph();    // readable wind-up cue; deliberately does NOT move
            return;
        }

        if (enemy.shouldMoveThisTurn()) {
            stepToward(enemy, playerColumn, playerRow);
        }
        enemy.state = EnemyState.CHASING;
    }

    /** True when a clear, unobstructed cardinal lane to the player sits within the charge band. */
    private boolean canBeginCharge(Enemy enemy, int playerColumn, int playerRow) {
        if (!isSameCardinalLine(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)) return false;
        // On a shared cardinal line the Manhattan distance is the straight-line gap.
        int gap = GameMath.manhattanDistanceTiles(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
        if (gap < EnemyConstants.SHELL_BRUTE_CHARGE_TRIGGER_MIN_TILES) return false;
        if (gap > EnemyConstants.SHELL_BRUTE_CHARGE_TRIGGER_MAX_TILES) return false;
        if (!hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)) return false;
        return !hasEnemyBlockingShot(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
    }

    /**
     * Rushes the brute along its committed charge direction up to the charge range, stopping at
     * the player, a wall, or another enemy. Returns true if it ends cardinally adjacent to the
     * player (a connecting rush, dealt at the charge multiplier).
     */
    private boolean performCharge(Enemy enemy, int playerColumn, int playerRow, Player player) {
        int stepColumn = enemy.chargeDirectionColumn;
        int stepRow    = enemy.chargeDirectionRow;
        enemy.state = EnemyState.ATTACKING;
        enemy.triggerAttackAnim();

        int maxRushTiles = EnemyConstants.SHELL_BRUTE_CHARGE_TRIGGER_MAX_TILES;
        for (int step = 0; step < maxRushTiles; step++) {
            if (GameMath.manhattanDistanceTiles(enemy.tileColumn, enemy.tileRow,
                    playerColumn, playerRow) == 1) {
                break; // already in melee range — stop and strike
            }
            int nextColumn = enemy.tileColumn + stepColumn;
            int nextRow    = enemy.tileRow    + stepRow;
            if (nextColumn == playerColumn && nextRow == playerRow) break; // can't share the player's tile
            if (!isPassableForEnemy(nextColumn, nextRow, playerColumn, playerRow)) break;
            commitMove(enemy, nextColumn, nextRow);
        }

        boolean connected = GameMath.manhattanDistanceTiles(
                enemy.tileColumn, enemy.tileRow, playerColumn, playerRow) == 1;
        if (connected) {
            int chargeDamage = Math.max(1, Math.round(
                    enemy.scaledAttackDamage() * EnemyConstants.SHELL_BRUTE_CHARGE_DAMAGE_MULTIPLIER));
            player.applyDamage(chargeDamage);
            if (enemyAttackListener != null) enemyAttackListener.onMeleeAttack(enemy);
        }
        return connected;
    }

    /**
     * FLANKER verb (Void Shroud, Pillar 2): prefers the tile directly behind the player's facing
     * and strikes HARDER from that blind side, so "rotate to face it / keep your back covered" is
     * the counterplay. When not adjacent it paths toward the flank tile rather than the player.
     */
    private void actFlankerEnemy(Enemy enemy, int playerColumn, int playerRow, Player player) {
        boolean cardinalAdjacent = GameMath.manhattanDistanceTiles(
                enemy.tileColumn, enemy.tileRow, playerColumn, playerRow) == 1;
        if (cardinalAdjacent) {
            int damage = enemy.scaledAttackDamage();
            if (isBehindPlayerFacing(enemy, playerColumn, playerRow, player)) {
                damage = Math.max(1, Math.round(
                        damage * EnemyConstants.VOID_SHROUD_FLANK_DAMAGE_MULTIPLIER));
            }
            player.applyDamage(damage);
            enemy.state = EnemyState.ATTACKING;
            enemy.triggerAttackAnim();
            if (enemyAttackListener != null) enemyAttackListener.onMeleeAttack(enemy);
            return;
        }

        if (enemy.shouldMoveThisTurn()) {
            int faceColumn  = Math.round(player.directionX);
            int faceRow     = Math.round(player.directionY);
            int flankColumn = playerColumn - faceColumn; // tile directly behind the player
            int flankRow    = playerRow    - faceRow;
            if (!stepTowardTile(enemy, flankColumn, flankRow, playerColumn, playerRow)) {
                stepToward(enemy, playerColumn, playerRow); // flank route blocked — close in directly
            }
        }
        enemy.state = EnemyState.CHASING;
    }

    /** True when the enemy sits on the side opposite the player's facing (the blind side). */
    private static boolean isBehindPlayerFacing(Enemy enemy, int playerColumn, int playerRow, Player player) {
        int faceColumn    = Math.round(player.directionX);
        int faceRow       = Math.round(player.directionY);
        int toEnemyColumn = enemy.tileColumn - playerColumn;
        int toEnemyRow    = enemy.tileRow    - playerRow;
        // Negative dot product = enemy is behind where the player looks.
        return toEnemyColumn * faceColumn + toEnemyRow * faceRow < 0;
    }

    private void actRangedEnemy(Enemy enemy, int playerColumn, int playerRow, Player player, int distanceToPlayer) {
        int rangeLimit = enemy.type.attackRangeTiles();
        boolean hasLOS = hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);

        // Melee-pin: once the player is cardinally adjacent (one tile away, in the player's own
        // melee reach) the kiter can no longer simply back-pedal out of every swing — otherwise a
        // melee build can NEVER connect with a ranged enemy in open space (the enemy flees on its
        // turn every time the player steps in). When pinned it holds its ground and fires point-blank
        // instead, so the player trades blows turn-for-turn: a fair, winnable melee duel.
        boolean meleePinned = GameMath.manhattanDistanceTiles(
                enemy.tileColumn, enemy.tileRow, playerColumn, playerRow) == 1;

        if (distanceToPlayer < EnemyConstants.RANGED_KITE_MIN_TILES && !meleePinned) {
            // Too close (but not melee-pinned) — flee first, then re-evaluate
            stepAway(enemy, playerColumn, playerRow);
            hasLOS = hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
            distanceToPlayer = GameMath.chebyshevDistanceTiles(
                    enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
        } else if (distanceToPlayer <= rangeLimit && hasLOS
                && isSameCardinalLine(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)) {
            // Perfect kiting range on a cardinal line — hold position and fire; no movement
        } else {
            // Too far, LOS blocked, or not on a cardinal line — advance toward player
            if (enemy.shouldMoveThisTurn()) {
                stepToward(enemy, playerColumn, playerRow);
            }
            hasLOS = hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
            distanceToPlayer = GameMath.chebyshevDistanceTiles(
                    enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
        }

        boolean canFire = distanceToPlayer <= rangeLimit
                && hasLOS
                && isSameCardinalLine(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)
                && !hasEnemyBlockingShot(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
        if (canFire) {
            player.applyDamage(enemy.scaledAttackDamage());
            enemy.state = EnemyState.ATTACKING;
            enemy.triggerAttackAnim();
            if (enemyAttackListener != null) enemyAttackListener.onRangedAttack(enemy, playerColumn, playerRow);
            applyRangedAttackStatusEffect(enemy, player);
        } else {
            enemy.state = EnemyState.CHASING;
        }
    }

    private void applyRangedAttackStatusEffect(Enemy enemy, Player player) {
        if (statusEffectController == null) return;
        float poisonChance;
        switch (enemy.type) {
            case MIRE_WRAITH: poisonChance = EffectConstants.MIRE_WRAITH_POISON_CHANCE; break;
            case ACID_DRONE:  poisonChance = EffectConstants.ACID_DRONE_POISON_CHANCE;  break;
            default: return;
        }
        if (effectRandom.nextFloat() < poisonChance) {
            statusEffectController.apply(player, StatusType.POISONED,
                    EffectConstants.POISON_DURATION,
                    EffectConstants.POISON_DAMAGE_PER_STACK,
                    enemy);
        }
    }

    private static boolean isSameCardinalLine(int column1, int row1, int column2, int row2) {
        return column1 == column2 || row1 == row2;
    }

    private boolean hasEnemyBlockingShot(int fromColumn, int fromRow, int toColumn, int toRow) {
        if (fromColumn == toColumn) {
            int minRow = Math.min(fromRow, toRow) + 1;
            int maxRow = Math.max(fromRow, toRow) - 1;
            for (int row = minRow; row <= maxRow; row++) {
                if (occupancy[fromColumn][row]) return true;
            }
        } else {
            int minColumn = Math.min(fromColumn, toColumn) + 1;
            int maxColumn = Math.max(fromColumn, toColumn) - 1;
            for (int column = minColumn; column <= maxColumn; column++) {
                if (occupancy[column][fromRow]) return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Movement helpers
    // -------------------------------------------------------------------------

    // Cardinal direction offsets: East, West, North, South
    private static final int[] STEP_COLUMNS = {  1, -1,  0,  0 };
    private static final int[] STEP_ROWS    = {  0,  0,  1, -1 };

    private void stepToward(Enemy enemy, int playerColumn, int playerRow) {
        int bestColumn   = enemy.tileColumn;
        int bestRow      = enemy.tileRow;
        int bestDistance = Integer.MAX_VALUE;
        boolean moved    = false;

        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            int targetColumn = enemy.tileColumn + STEP_COLUMNS[directionIndex];
            int targetRow    = enemy.tileRow    + STEP_ROWS[directionIndex];
            if (!isPassableForEnemy(targetColumn, targetRow, playerColumn, playerRow)) continue;
            int distance = GameMath.manhattanDistanceTiles(targetColumn, targetRow, playerColumn, playerRow);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestColumn   = targetColumn;
                bestRow      = targetRow;
                moved        = true;
            }
        }

        if (!moved && EnemyConstants.ENEMY_GREEDY_WIGGLE_ENABLED
                && enemy.stuckTurns >= EnemyConstants.STUCK_TURNS_BEFORE_WIGGLE) {
            wiggleStep(enemy, playerColumn, playerRow);
            enemy.stuckTurns = 0;
            return;
        }

        if (moved) {
            int previousManhattan = GameMath.manhattanDistanceTiles(
                    enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
            if (bestDistance >= previousManhattan) {
                enemy.stuckTurns++;
            } else {
                enemy.stuckTurns = 0;
            }
            commitMove(enemy, bestColumn, bestRow);
        } else {
            enemy.stuckTurns++;
        }
    }

    /**
     * Greedily steps the enemy one tile toward an arbitrary goal tile (not necessarily the
     * player) while never walking onto the player or through walls/other enemies. Returns true
     * if a move was made. Used by the flanker to path toward the tile behind the player.
     */
    private boolean stepTowardTile(Enemy enemy, int goalColumn, int goalRow,
                                   int playerColumn, int playerRow) {
        int bestColumn   = enemy.tileColumn;
        int bestRow      = enemy.tileRow;
        int bestDistance = Integer.MAX_VALUE;
        boolean moved    = false;

        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            int targetColumn = enemy.tileColumn + STEP_COLUMNS[directionIndex];
            int targetRow    = enemy.tileRow    + STEP_ROWS[directionIndex];
            if (!isPassableForEnemy(targetColumn, targetRow, playerColumn, playerRow)) continue;
            int distance = GameMath.manhattanDistanceTiles(targetColumn, targetRow, goalColumn, goalRow);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestColumn   = targetColumn;
                bestRow      = targetRow;
                moved        = true;
            }
        }

        if (moved) {
            commitMove(enemy, bestColumn, bestRow);
        }
        return moved;
    }

    private void stepAway(Enemy enemy, int playerColumn, int playerRow) {
        int bestColumn   = enemy.tileColumn;
        int bestRow      = enemy.tileRow;
        int bestDistance = -1;
        boolean moved    = false;

        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            int targetColumn = enemy.tileColumn + STEP_COLUMNS[directionIndex];
            int targetRow    = enemy.tileRow    + STEP_ROWS[directionIndex];
            if (!isPassableForEnemy(targetColumn, targetRow, playerColumn, playerRow)) continue;
            int distance = GameMath.manhattanDistanceTiles(targetColumn, targetRow, playerColumn, playerRow);
            if (distance > bestDistance) {
                bestDistance = distance;
                bestColumn   = targetColumn;
                bestRow      = targetRow;
                moved        = true;
            }
        }

        if (moved) {
            commitMove(enemy, bestColumn, bestRow);
        }
    }

    private void wiggleStep(Enemy enemy, int playerColumn, int playerRow) {
        int legalCount = 0;
        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            int targetColumn = enemy.tileColumn + STEP_COLUMNS[directionIndex];
            int targetRow    = enemy.tileRow    + STEP_ROWS[directionIndex];
            if (isPassableForEnemy(targetColumn, targetRow, playerColumn, playerRow)) {
                wiggleLegalColumns[legalCount] = targetColumn;
                wiggleLegalRows[legalCount]    = targetRow;
                legalCount++;
            }
        }
        if (legalCount > 0) {
            int chosenIndex = wiggleRandom.nextInt(legalCount);
            commitMove(enemy, wiggleLegalColumns[chosenIndex], wiggleLegalRows[chosenIndex]);
        }
    }

    private void commitMove(Enemy enemy, int targetColumn, int targetRow) {
        occupancy[enemy.tileColumn][enemy.tileRow] = false;
        enemy.tileColumn = targetColumn;
        enemy.tileRow    = targetRow;
        occupancy[targetColumn][targetRow] = true;
    }

    /**
     * Attempts to push the given enemy to (targetColumn, targetRow) — used by Hammer knockback.
     * Checks bounds, walls/props, door state, and other-enemy occupancy before moving.
     * Returns true if the push succeeded; false if anything blocked it.
     */
    @Override
    public boolean tryPushEnemy(Object enemyObject, int targetColumn, int targetRow) {
        Enemy enemy = (Enemy) enemyObject;
        if (!enemy.isAlive()) return false;
        if (targetColumn < 0 || targetColumn >= level.getWidth())  return false;
        if (targetRow    < 0 || targetRow    >= level.getHeight()) return false;
        if (level.isBlockedAt(targetColumn, targetRow, doorManager)) return false;
        if (isTileOccupiedByEnemy(targetColumn, targetRow)) return false;
        occupancy[enemy.tileColumn][enemy.tileRow] = false;
        enemy.tileColumn = targetColumn;
        enemy.tileRow    = targetRow;
        occupancy[targetColumn][targetRow] = true;
        return true;
    }

    /**
     * Overwrites the level cell at (tileColumn, tileRow) with dropChar and notifies the
     * DropPlacedListener. Used by SALVAGE_STRIKE to guarantee an ammo pickup after a kill,
     * replacing whatever drop (or empty floor) the enemy's death would have left there.
     * No-op for out-of-bounds coordinates.
     */
    public void overrideDropAt(int tileColumn, int tileRow, char dropChar) {
        if (tileColumn < 0 || tileColumn >= level.getWidth())  return;
        if (tileRow    < 0 || tileRow    >= level.getHeight()) return;
        level.setCell(tileColumn, tileRow, dropChar);
        if (dropPlacedListener != null) {
            dropPlacedListener.onDropPlaced(tileColumn, tileRow, dropChar);
        }
    }

    /** Returns true if any live enemy currently occupies the given tile. Safe to call at any time. */
    public boolean isTileOccupiedByEnemy(int tileColumn, int tileRow) {
        for (int enemyIndex = 0; enemyIndex < enemies.size(); enemyIndex++) {
            Enemy enemy = enemies.get(enemyIndex);
            if (enemy.tileColumn == tileColumn && enemy.tileRow == tileRow) return true;
        }
        return false;
    }

    private boolean isPassableForEnemy(int targetColumn, int targetRow, int playerColumn, int playerRow) {
        if (level.isBlockedAt(targetColumn, targetRow, doorManager)) return false;
        if (occupancy[targetColumn][targetRow]) return false;
        if (targetColumn == playerColumn && targetRow == playerRow) return false;
        return true;
    }

    // -------------------------------------------------------------------------
    // LOS
    // -------------------------------------------------------------------------

    private boolean hasLineOfSight(int fromColumn, int fromRow, int toColumn, int toRow) {
        int chebyshev = GameMath.chebyshevDistanceTiles(fromColumn, fromRow, toColumn, toRow);
        if (chebyshev > EnemyConstants.LOS_MAX_RANGE_TILES) return false;
        return GameMath.tileLineOfSightClear(fromColumn, fromRow, toColumn, toRow,
                (column, row) -> {
                    char cell = level.getCell(column, row);
                    return Level.isWall(cell)
                            || doorManager.blocksSight(column, row)
                            || Level.isPropSolid(cell)
                            || Level.isColumn(cell);
                });
    }

    // -------------------------------------------------------------------------
    // Enemy death
    // -------------------------------------------------------------------------

    /**
     * Handles an enemy death delivered by a status effect DoT tick (Burning / Poison).
     * Awards XP to the player and stamps the corpse decal / drop, same as a weapon kill.
     * Called by StatusEffectController after it finishes iterating enemies for the turn,
     * so the enemy list is safe to modify (no ConcurrentModificationException risk).
     */
    public void processDoTKill(Enemy enemy) {
        int xpAwarded = enemy.type.baseXpReward();
        if (killXpListener    != null) killXpListener.onEnemyKilledForXp(xpAwarded);
        if (killEventListener != null) killEventListener.onEnemyKilled(enemy.nameTag, xpAwarded);
        if (killCreditListener != null) killCreditListener.onEnemyKilledForCredits(enemy.type.baseCreditReward(), currentDepth);
        killEnemy(enemy, false);
    }

    private void killEnemy(Enemy enemy, boolean isMeleeKill) {
        occupancy[enemy.tileColumn][enemy.tileRow] = false;
        char currentCell = level.getCell(enemy.tileColumn, enemy.tileRow);
        if (!Level.isStairsDown(currentCell)
                && !Level.isMedicalPickup(currentCell)
                && !Level.isArmourPickup(currentCell)
                && !Level.isKeycardPickup(currentCell)
                && !Level.isAmmoPickup(currentCell)) {
            char drop = rollEnemyDrop(enemy.type, isMeleeKill);
            level.setCell(enemy.tileColumn, enemy.tileRow, drop);
            if (dropPlacedListener != null) {
                dropPlacedListener.onDropPlaced(enemy.tileColumn, enemy.tileRow, drop);
            }
        }
        // Area-denial death hook (Pillar 2/3): e.g. a Plague Hulk leaves a toxic cloud here.
        if (enemyDeathHazardListener != null) {
            enemyDeathHazardListener.onEnemyDied(enemy.type, enemy.tileColumn, enemy.tileRow);
        }
        enemies.remove(enemy);
    }

    private char rollEnemyDrop(EnemyType type, boolean isMeleeKill) {
        float dropChance = isMeleeKill
                ? GameBalance.MELEE_KILL_AMMO_DROP_CHANCE
                : EnemyConstants.ENEMY_AMMO_DROP_CHANCE;
        if (dropRandom.nextFloat() >= dropChance) {
            return 'm'; // corpse decal, no item
        }
        if (isMeleeKill && loadout != null) {
            char meleeAmmo = rollLoadoutAmmoDrop();
            if (meleeAmmo != 0) return meleeAmmo;
        }
        switch (type) {
            case PLAGUE_HULK:  return '6'; // bullets — basic melee tank
            case EYE_TYRANT:   return '8'; // cells   — energy-based ranged
            case GORE_BITER:   return '7'; // shells  — brawler
            case SHELL_BRUTE:  return '6'; // bullets — heavy charger
            case MIRE_WRAITH:  return '8'; // cells   — acid ranged
            case IRON_STALKER: return '6'; // bullets — armored elite
            case ACID_DRONE:   return '8'; // cells   — mechanical ranged
            case VOID_SHROUD:  return '6'; // bullets — stealth melee
            case GHOUL:        return '6'; // bullets — shambling chaff
            case CRAWLER:      return '6'; // bullets — fast chaff
            case REVENANT:     return '7'; // shells  — heavy undead brawler
            case VORTEX_EYE:   return '8'; // cells   — energy caster (ranged)
            case BLIGHT_CORRUPTOR: return '7'; // shells — infected brute
            default:           return '6';
        }
    }

    /**
     * Picks an ammo pickup character matching one of the player's equipped ranged weapons.
     * Returns 0 if no ranged weapon is currently equipped (melee-only loadout).
     */
    private char rollLoadoutAmmoDrop() {
        // Collect pickup chars for every equipped ranged weapon slot.
        int count = 0;
        char[] candidates = new char[loadout.getSlotCount()];
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            Weapon weapon = loadout.getSlot(slotIndex);
            if (weapon != null && !(weapon instanceof MeleeWeapon)) {
                AmmoType ammoType = weapon.getAmmoType();
                if (ammoType != null) {
                    candidates[count++] = ammoType.getPickupTileChar();
                }
            }
        }
        if (count == 0) return 0;
        return candidates[dropRandom.nextInt(count)];
    }
}
