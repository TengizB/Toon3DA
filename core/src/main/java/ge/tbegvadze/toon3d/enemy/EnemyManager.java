package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.entity.EnemyHitTarget;
import ge.tbegvadze.toon3d.entity.HazardIgniteTarget;
import ge.tbegvadze.toon3d.entity.ImpactEventListener;
import ge.tbegvadze.toon3d.entity.Loadout;
import ge.tbegvadze.toon3d.entity.boss.Boss;
import ge.tbegvadze.toon3d.entity.MeleeWeapon;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.entity.SpireHitTarget;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.level.EnemySpawnPoint;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.progression.KillCreditListener;
import ge.tbegvadze.toon3d.progression.KillEventListener;
import ge.tbegvadze.toon3d.progression.KillXpListener;
import ge.tbegvadze.toon3d.render.EventTextSystem;
import ge.tbegvadze.toon3d.status.StatusEffectController;
import ge.tbegvadze.toon3d.status.StatusResistance;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.BossBalance;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.StatsStore;
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
     *
     * @param selfDestructMassive true only for a Plague Hulk that survived its self-destruct
     *                            countdown to detonate (.claude/agents/ideas/plague-hulk-self-
     *                            destruct.txt) — the death hook should leave the MASSIVE toxic
     *                            cloud instead of the ordinary minimal one. Always false for a
     *                            normal kill (including a Hulk killed before it could detonate).
     */
    public interface EnemyDeathHazardListener {
        void onEnemyDied(EnemyType type, int tileColumn, int tileRow, boolean selfDestructMassive);
    }

    /**
     * Notified the moment a committed SPECIAL ability actually RESOLVES in front of the player —
     * not when it is telegraphed (narrative-rework order-5 C). The bestiary voice hangs off this:
     * five abilities ship and the game never explained any of them, so the first time each one
     * happens ORA says the rule out loud. Fires on EVERY resolution; the narrative layer's one-shot
     * flag decides that only the first is ever spoken, which keeps this hook free of story state.
     */
    public interface SpecialResolvedListener {
        void onSpecialResolved(SpecialAbility ability);
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
    private SpecialResolvedListener specialResolvedListener;

    /** Flat damage bonus from player level-up damage cards (Hollow Points / Glass Cannon); added to every hit. */
    private int playerFlatDamageBonus = 0;

    /**
     * Player ranged/melee damage multipliers derived from MARKSMANSHIP/STRENGTH (attribute system
     * + level-up cards). Applied centrally here — the single point that knows whether the hit was
     * melee ({@code pendingMeleeKill}) or ranged — so no weapon double-counts them. Default 1.0.
     */
    private float playerRangedDamageMultiplier = 1.0f;
    private float playerMeleeDamageMultiplier  = 1.0f;

    /**
     * Player reference + last-known tile, cached each turn from {@link #takeTurn} (strategy-combat-
     * order-6). Read by {@link #applyDamageTo} — which has no player argument — so a weapon hit can
     * apply the player's WEAK outgoing debuff and judge the backstab bonus against enemy facing.
     * Null / -1 until the first world turn runs; the modifier helpers treat that as "no debuff, no
     * backstab" (safe defaults), and no enemy can have acted on the player before then anyway.
     */
    private Player cachedPlayer       = null;
    private int    cachedPlayerColumn = -1;
    private int    cachedPlayerRow    = -1;

    // Pre-allocated scratch state — never re-allocated after construction
    private final boolean[][]  occupancy;           // [column][row] — true if an enemy is there this turn
    private final int[]        chainAlertQueue;     // BFS queue of enemy indices for chain-alert
    private final int[]        wiggleLegalColumns;  // reused in wiggleStep — avoids allocation in takeTurn
    private final int[]        wiggleLegalRows;
    private final Random       wiggleRandom;
    private final Random       dropRandom;
    private final Random       effectRandom;

    /**
     * Enemies that killed THEMSELVES mid-phaseBExecute (currently only a detonating Plague Hulk —
     * see executeSelfDestruct). Removing an enemy from {@code enemies} while phaseBExecute is still
     * iterating it by index would shift the list and skip the next enemy's turn, so the kill is
     * queued here and applied in a second pass after the phaseB loop finishes — the same deferred
     * pattern StatusEffectController uses for a DoT kill mid-tick.
     */
    private static final int MAX_PENDING_EXECUTE_DEATHS = 8;
    private final Enemy[] pendingExecuteDeaths = new Enemy[MAX_PENDING_EXECUTE_DEATHS];
    private int            pendingExecuteDeathCount = 0;

    private boolean anyAlertedEver = false;

    // Stored so killCreditListener can scale credit rewards by dungeon depth at kill time.
    private final int currentDepth;

    // Set to true by notifyMeleeAttack() before applyDamageTo(); consumed and reset inside killEnemy().
    private boolean pendingMeleeKill = false;
    // Fraction of enemy Block bypassed by every hit in the current fire activation (ARMOR_PIERCE).
    // Armed to the ability magnitude by Weapon.fire() at activation start, cleared to 0 at its end,
    // so only weapon hits pierce Block — DoT ticks and barrel damage resolve with pierce == 0.
    private float activationBlockPierceFraction = 0f;
    // Injected by World so melee kills can drop ammo matching the player's equipped ranged weapons.
    private Loadout loadout = null;
    /**
     * Fire-ignition sink for the Cinderforge Colossus's MOLTEN TRAIL, wired per floor by World (the
     * HazardManager is rebuilt each floor). Null in headless tests and no-World construction, which
     * makes every trail ignition a safe no-op.
     */
    private HazardIgniteTarget hazardIgniteTarget = null;

    /**
     * Crystal-spire simulation seam for the Verdant Spiresower (SpireManager, wired per floor by World).
     * Null in headless tests / no-World construction, which makes every sow, regen and spire query a safe
     * no-op — so an enemy turn never needs a spire system. Reached through the entity-package
     * {@link SpireHitTarget} so the enemy package never depends on the hazard package.
     */
    private SpireHitTarget spireHitTarget = null;

    // Sow-target selection scratch — the candidate box for one sow (radius R -> (2R+1)^2 tiles), gathered
    // once per commit and never reallocated (project rule: no allocation inside takeTurn()).
    private static final int SOW_CANDIDATE_CAPACITY =
            (2 * EnemyConstants.SPIRESOWER_SOW_RADIUS_TILES + 1)
          * (2 * EnemyConstants.SPIRESOWER_SOW_RADIUS_TILES + 1);
    private final int[] sowCandidateColumns = new int[SOW_CANDIDATE_CAPACITY];
    private final int[] sowCandidateRows    = new int[SOW_CANDIDATE_CAPACITY];

    /** Notified when the never-softlock emergency ammo lifeline fires (order 3, part D) — for telemetry. */
    public interface EmergencySupplyListener {
        void onEmergencySupplyGranted();
    }
    private EmergencySupplyListener emergencySupplyListener;
    /** Once-per-floor cap on the emergency ammo lifeline. Fresh EnemyManager per floor resets it. */
    private boolean emergencySupplyGrantedThisFloor = false;

    private StatusEffectController statusEffectController = null;

    /**
     * Nullable — wired by World. When present, a shot swallowed by an enemy's Block spawns a
     * "BLOCKED N" floater (strategy-combat-order-3) so the player learns the shield ate the hit.
     */
    private EventTextSystem eventTextSystem = null;

    /**
     * Per-stream salts so the spawn roll, the wiggle tie-break, the drop roll and the effect roll are
     * INDEPENDENT streams derived from one floor seed — otherwise a change in how many drops rolled
     * would shift every later spawn. Arbitrary distinct odd constants; only their distinctness matters.
     */
    private static final long SPAWN_STREAM_SALT  = 0x5DEECE66DL;
    private static final long WIGGLE_STREAM_SALT = 0x1D2B7A3CL;
    private static final long DROP_STREAM_SALT   = 0x9E3779B9L;
    private static final long EFFECT_STREAM_SALT = 0x27D4EB2FL;

    /** Seed used by the legacy (unseeded-looking) constructor so tests and tools stay reproducible. */
    private static final long DEFAULT_RANDOM_SEED = 12345L;

    /**
     * @param dungeonDepth current floor number (1-based); drives enemy health and damage scaling.
     *                     Pass 1 for the first floor.
     */
    public EnemyManager(Level level, DoorManager doorManager, int dungeonDepth) {
        this(level, doorManager, dungeonDepth, DEFAULT_RANDOM_SEED);
    }

    /**
     * Seeded variant — the SAME floor seed in always produces the same spawn roll, the same drops and
     * the same effect rolls. World passes its per-floor seed and the order-9 balance simulator passes
     * its own, so a run replays identically from its run seed (R-SIM-DETERMINISM). Unseeded
     * randomness in a gameplay manager is a determinism bug, not a style choice.
     *
     * @param dungeonDepth current floor number (1-based); drives enemy health and damage scaling.
     * @param randomSeed   the floor's master seed; every RNG stream below is derived from it.
     */
    public EnemyManager(Level level, DoorManager doorManager, int dungeonDepth, long randomSeed) {
        this.level        = level;
        this.doorManager  = doorManager;
        this.currentDepth = dungeonDepth;
        this.enemies      = buildInitialEnemies(level.getEnemySpawnPoints(), dungeonDepth,
                                                new Random(randomSeed ^ SPAWN_STREAM_SALT));
        int levelWidth   = level.getWidth();
        int levelHeight  = level.getHeight();
        int enemyCount   = enemies.size();
        this.occupancy          = new boolean[levelWidth][levelHeight];
        this.chainAlertQueue    = new int[Math.max(1, enemyCount)];
        this.wiggleLegalColumns = new int[4];
        this.wiggleLegalRows    = new int[4];
        this.wiggleRandom       = new Random(randomSeed ^ WIGGLE_STREAM_SALT);
        this.dropRandom         = new Random(randomSeed ^ DROP_STREAM_SALT);
        this.effectRandom       = new Random(randomSeed ^ EFFECT_STREAM_SALT);
    }

    /** Injects the status effect controller so ranged enemies can inflict DoT on the player. */
    public void setStatusEffectController(StatusEffectController controller) {
        this.statusEffectController = controller;
    }

    /** Injects the event-text system so an enemy's Block absorbing a shot spawns a "BLOCKED N" floater. */
    public void setEventTextSystem(EventTextSystem system) {
        this.eventTextSystem = system;
    }

    /**
     * Injects the player's loadout so melee kills can drop ammo that matches
     * one of the player's equipped ranged weapons instead of a fixed enemy-type drop.
     */
    public void setLoadout(Loadout playerLoadout) {
        this.loadout = playerLoadout;
    }

    /** Injects the emergency-supply telemetry listener (order 3, part D). Nullable. */
    public void setEmergencySupplyListener(EmergencySupplyListener listener) {
        this.emergencySupplyListener = listener;
    }

    /**
     * Injects the fire-ignition sink used by the MOLTEN TRAIL
     * (.claude/agents/ideas/elemental-golem-cinderforge-colossus.txt). World re-wires this whenever the
     * per-floor {@code HazardManager} is rebuilt, exactly as it does for the Incinerator. Null (the
     * default, and the case in headless unit tests) makes every trail ignition a safe no-op, so no test
     * needs a hazard system just to take an enemy turn.
     */
    public void setHazardIgniteTarget(HazardIgniteTarget target) {
        this.hazardIgniteTarget = target;
    }

    /**
     * Injects the crystal-spire seam used by the Verdant Spiresower
     * (.claude/agents/ideas/elemental-golem-verdant-spiresower.txt). World re-wires this whenever the
     * per-floor {@code SpireManager} is rebuilt. Null (the default, and the case in headless unit tests)
     * makes every sow, regen and spire query a safe no-op, so no test needs a spire system to take a turn.
     */
    public void setSpireHitTarget(SpireHitTarget target) {
        this.spireHitTarget = target;
    }

    @Override
    public boolean isSpireAt(int tileColumn, int tileRow) {
        return spireHitTarget != null && spireHitTarget.isSpireAt(tileColumn, tileRow);
    }

    /**
     * Routes a weapon hit that stopped on a spire's cover tile into the SpireManager (the design's
     * "extend the existing hit-target seam" rule). Fires the ordinary hit spark + damage number so a spire
     * reads as something you damaged, and — when the hit DESTROYS the spire — FLINCHES its sower, so the
     * player sees that hurting the terrain hurt the enemy. The golem's death withering (shattering all its
     * spires) is handled in {@link #killEnemy}, not here.
     */
    @Override
    public void damageSpireAt(int tileColumn, int tileRow, int amount) {
        if (spireHitTarget == null) return;
        int ownerId = spireHitTarget.spireOwnerAt(tileColumn, tileRow);
        boolean hit = spireHitTarget.damageSpireAt(tileColumn, tileRow, amount);
        if (!hit) return;
        float worldX = tileColumn * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
        float worldY = tileRow * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
        if (impactEventListener != null) {
            impactEventListener.onEnemyHit(worldX, worldY,
                    EnemyConstants.SPIRESOWER_SPIRE_HEIGHT_MULTIPLIER, amount);
        }
        if (!spireHitTarget.isSpireAt(tileColumn, tileRow)) {
            Enemy owner = findAliveEnemyById(ownerId);
            if (owner != null) owner.triggerHitFlash();   // low-strength flinch: hurting the terrain hurt it
        }
    }

    /** Finds a living enemy by its stable id, or null. Used to flinch a Spiresower whose spire was broken. */
    private Enemy findAliveEnemyById(int enemyId) {
        if (enemyId < 0) return null;
        for (int index = 0; index < enemies.size(); index++) {
            Enemy enemy = enemies.get(index);
            if (enemy.getId() == enemyId && enemy.isAlive()) return enemy;
        }
        return null;
    }

    @Override
    public void notifyMeleeAttack() {
        pendingMeleeKill = true;
    }

    @Override
    public void setActivationBlockPierce(float fraction) {
        activationBlockPierceFraction = Math.max(0f, Math.min(1f, fraction));
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
                case '(': type = EnemyType.AURIC_SENTINEL;   break;
                case '{': type = EnemyType.CINDERFORGE_COLOSSUS; break;
                case '[': type = EnemyType.RIMESHELL_LANCER;  break;
                case '}': type = EnemyType.VERDANT_SPIRESOWER; break;
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

    /**
     * XP awarded for killing this archetype on THIS floor (new-game-balancr order 4). Non-boss XP is
     * DERIVED from the enemy's depth-scaled Threat Points (GameMath.xpRewardAtDepth), so a deeper,
     * stronger instance automatically pays more and the whole floor's roster XP tracks the level
     * requirement (R-XP-PACE). Bosses keep their flat placeholder reward (baseXpReward) pending idea 6.
     */
    private int depthScaledXpReward(EnemyType enemyType) {
        if (enemyType.role() == EnemyRole.BOSS) {
            // Order 6: boss XP is DERIVED from the boss's depth-scaled stats (its Threat Points), so an
            // endless-mode boss at depth 20 pays its depth-20 XP automatically — no flat placeholder.
            return BossBalance.statsForDepth(currentDepth).xpReward;
        }
        return GameMath.xpRewardAtDepth(BalanceConfig.XP_PER_THREAT_POINT, enemyType.baseThreatPoints(),
                BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH, BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH,
                currentDepth);
    }

    /**
     * Credits awarded for killing this archetype on THIS floor. Non-boss credits come from the archetype's
     * base bounty; a boss's credit reward is DERIVED (order 6, RULE 6) from the modelled fight consumption at
     * the boss's actual depth, so a deeper boss pays more and the reward always refunds the fight plus a
     * premium.
     */
    private int depthScaledCreditReward(EnemyType enemyType) {
        if (enemyType.role() == EnemyRole.BOSS) {
            return BossBalance.statsForDepth(currentDepth).creditReward;
        }
        return enemyType.baseCreditReward();
    }

    /** Wires the kill-message system so every kill fires a display notification with name + XP. */
    public void setKillEventListener(KillEventListener listener) {
        this.killEventListener = listener;
    }

    /** Wires the credit system so every kill awards scaled credits to the player. */
    public void setKillCreditListener(KillCreditListener listener) {
        this.killCreditListener = listener;
    }

    /**
     * Wires the bestiary voice (narrative-rework order-5): every SPECIAL ability resolution is
     * reported so ORA can explain each ability the first time the player watches one happen.
     * Optional — a null listener leaves every special path byte-identical (sim/SimWorld sets none).
     */
    public void setSpecialResolvedListener(SpecialResolvedListener listener) {
        this.specialResolvedListener = listener;
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
        spawnEnemyInternal(type, tileColumn, tileRow, dungeonDepth, false);
    }

    /**
     * Spawns a boss-summoned add — identical to {@link #spawnEnemy} but tags the add with
     * {@link Enemy#spawnedByBossSummon}. Called by BossFloorController's SUMMON path so the enemy-death
     * drop logic can grant the guaranteed ammo lifeline (boss-fight-mobile-overseer ORDER 6): in the
     * sealed arena, killing a summoned add ALWAYS drops matching ammo when the player is out of ammo.
     * No-op if the tile is out of bounds, already occupied, or blocked.
     */
    public void spawnBossMinion(EnemyType type, int tileColumn, int tileRow, int dungeonDepth) {
        spawnEnemyInternal(type, tileColumn, tileRow, dungeonDepth, true);
    }

    private void spawnEnemyInternal(EnemyType type, int tileColumn, int tileRow, int dungeonDepth,
                                    boolean spawnedByBossSummon) {
        if (tileColumn < 0 || tileColumn >= level.getWidth())  return;
        if (tileRow    < 0 || tileRow    >= level.getHeight()) return;
        if (level.isBlockedAt(tileColumn, tileRow, doorManager)) return;
        if (isTileOccupiedByEnemy(tileColumn, tileRow)) return;
        Enemy enemy = initScaledEnemy(type, tileColumn, tileRow, dungeonDepth);
        enemy.alert();
        enemy.spawnedByBossSummon = spawnedByBossSummon;
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

        // Order-6 modifier layer — Step 2 of the shared mitigation pipeline (docs/game-balance-authority.txt),
        // applied BEFORE Block/armor. All three are pure GameMath multipliers:
        //   • WEAK on the player dims its OWN output (a debuffed marine hits softer).
        //   • VULNERABLE on the target amplifies the hit (setup-then-payoff), stack-capped.
        //   • BACKSTAB rewards landing the shot from behind the enemy's facing.
        float playerWeakMultiplier = (cachedPlayer != null)
                ? cachedPlayer.getWeakDamageMultiplier() : 1f;
        float vulnerableMultiplier = GameMath.vulnerableDamageMultiplier(
                enemy.vulnerableStacks(), EffectConstants.VULNERABLE_DAMAGE_PERCENT);
        boolean backstab = cachedPlayerColumn >= 0 && GameMath.isAttackerBehindFacing(
                enemy.facingColumn, enemy.facingRow, enemy.tileColumn, enemy.tileRow,
                cachedPlayerColumn, cachedPlayerRow);
        float backstabMultiplier = GameMath.backstabDamageMultiplier(backstab, EffectConstants.BACKSTAB_DAMAGE_PERCENT);
        totalDamage = Math.round(totalDamage * playerWeakMultiplier * vulnerableMultiplier * backstabMultiplier);
        if (backstab) {
            showCombatTipOnce("backstab", "BACKSTAB! Hits from behind deal bonus damage");
        }

        // EXPOSED (order-6): the next hit into a marked enemy ignores its Block (anti-turtle). Read the
        // flag now and consume it after the hit so the shred lasts exactly one blow.
        boolean exposed = enemy.isExposed();

        // Block absorption (strategy-combat-order-3): capture the buffer before the hit so we can tell
        // how much the shield ate and whether it shattered, then fire the Block-specific feedback.
        // The shard ring (elemental-golem-auric-sentinel) resolves INSIDE applyDamage, ahead of Block,
        // so capture it here too: an absorbed hit must read as gold "nothing happened", never as a
        // red damage number, or the Sentinel reads as a bullet sponge instead of a puzzle.
        // COOLING CRUST (elemental-golem-cinderforge-colossus): a hit that breaks a FULLY cooled shell is
        // AMPLIFIED inside applyDamage, so the number floated on screen must be the amplified one or the
        // archetype's payoff beat reads as a bug. Both sides route through the same pure query, and it is
        // evaluated BEFORE the hit, while the shell is still standing.
        int crustBefore   = enemy.crustStacks;
        boolean fullCrust = enemy.hasFullCrust();
        totalDamage       = enemy.crustShatterAmplified(totalDamage);

        int blockBefore = enemy.block;
        int shardsBefore = enemy.shardCount;
        enemy.applyDamage(totalDamage, exposed, activationBlockPierceFraction);
        if (exposed) enemy.consumeExposed();
        if (enemy.shardCount < shardsBefore) {
            spawnShardAbsorbFeedback(enemy);
            return;   // the shard ate the whole hit: no hit flash, no damage number, no HP change
        }
        if (crustBefore > 0 && enemy.crustStacks == 0) {
            spawnCrustShatterFeedback(enemy, crustBefore, fullCrust);
        }
        int blockAbsorbed = blockBefore - enemy.block;
        if (blockAbsorbed > 0) {
            boolean shattered = enemy.block == 0 && totalDamage > blockBefore;
            spawnBlockFeedback(enemy, blockAbsorbed, shattered);
        }

        enemy.triggerHitFlash();

        if (!enemy.isAlive()) {
            int xpAwarded = depthScaledXpReward(enemy.type);
            if (killXpListener != null) {
                killXpListener.onEnemyKilledForXp(xpAwarded);
            }
            if (killEventListener != null) {
                killEventListener.onEnemyKilled(enemy.nameTag, xpAwarded);
            }
            if (killCreditListener != null) {
                killCreditListener.onEnemyKilledForCredits(depthScaledCreditReward(enemy.type), currentDepth);
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

    /**
     * Applies (or refreshes) a BURNING damage-over-time status on the given enemy.
     * Routed through the shared StatusEffectController so the burn ticks each world
     * turn, obeys per-enemy fire resistance/immunity, and attributes any DoT kill
     * back to the player (source = this manager). Used by the Incinerator cone.
     */
    @Override
    public void applyBurningStatus(Object enemyObject, int turns, int magnitudePerTurn) {
        if (statusEffectController == null) return;
        statusEffectController.apply((Enemy) enemyObject, StatusType.BURNING,
                turns, magnitudePerTurn, this);
    }

    /**
     * Applies a VULNERABLE mark on the enemy (strategy-combat-order-6). Routed through the shared
     * controller so it respects the STACK_MAGNITUDE cap and the same resist table as every other
     * status. Magnitude carries no per-turn value (the potency lives in the mitigation multiplier);
     * a stack is added on each application. Used by the Railgun's marking slug.
     */
    @Override
    public void applyVulnerableStatus(Object enemyObject, int turns) {
        if (statusEffectController == null) return;
        statusEffectController.apply((Enemy) enemyObject, StatusType.VULNERABLE, turns, 0, this);
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor("VULNERABLE", EventTextSystem.COLOR_GOLD);
        }
        showCombatTipOnce("vulnerable", "VULNERABLE — it takes extra damage now. Follow up!");
    }

    /**
     * Applies an EXPOSED flag on the enemy (strategy-combat-order-6): the next hit ignores its Block.
     * Routed through the shared controller (REFRESH_DURATION). Used by the Plasma Rifle's piercing bolt.
     */
    @Override
    public void applyExposedStatus(Object enemyObject, int turns) {
        if (statusEffectController == null) return;
        statusEffectController.apply((Enemy) enemyObject, StatusType.EXPOSED, turns, 0, this);
        showCombatTipOnce("exposed", "EXPOSED — your next hit ignores its shield");
    }

    /**
     * First-encounter teaching (strategy-combat-order-6 onboarding): the first time the player sees a
     * given new combat element, spawn a one-line EventText tip, then persist a flag so it never fires
     * again (across runs). Keeps a strategic system from being noise — a tip nobody sees is useless, a
     * tip on every hit is spam. No-op without a wired EventTextSystem.
     */
    private void showCombatTipOnce(String tipKey, String message) {
        if (eventTextSystem == null) return;
        if (StatsStore.isTipSeen(tipKey)) return;
        StatsStore.markTipSeen(tipKey);
        eventTextSystem.spawnWithColor(message, EventTextSystem.COLOR_WHITE);
    }

    /** Advances hit-flash and attack animation timers for all enemies. Call once per frame from World.update(). */
    public void advanceHitFlash(float deltaTime) {
        for (int index = 0; index < enemies.size(); index++) {
            Enemy enemy = enemies.get(index);
            enemy.advanceHitFlash(deltaTime);
            enemy.advanceAttackAnim(deltaTime);
            enemy.advanceIntentPop(deltaTime);
            enemy.advanceSlide(deltaTime);   // ORDER 2: cosmetic boss locomotion slide (no-op when idle)
            enemy.advanceShardAbsorbFlash(deltaTime); // gold absorb flash (no-op without shards)
            enemy.advanceCrustTint(deltaTime);        // hot<->cooled seam glow (no-op without crust)
        }
    }

    // -------------------------------------------------------------------------
    // Turn simulation
    // -------------------------------------------------------------------------

    /**
     * Runs one enemy turn using the pre-committed intent model (strategy-combat-order-1):
     *   A  — Perception: dormant enemies wake on proximity/LOS, then chain-alert BFS.
     *   B  — EXECUTE: every alerted enemy performs the action it COMMITTED last turn (with
     *        fairness re-validation R1-R6). A freshly-woken enemy has no plan and only commits.
     *   C  — COMMIT: every alerted enemy reads the now-resolved board and computes its NEXT
     *        action, storing it so the player can read the telegraphed intent all next turn.
     * All of B runs before any of C (spawn-index order), so no enemy re-plans on a same-turn
     * ally's move — "what you saw is what you get."
     */
    public void takeTurn(int playerColumn, int playerRow, Player player) {
        // Cache for applyDamageTo (order-6): a weapon hit resolves during the player's action, before
        // the next takeTurn, so the player's tile/state here is exactly the one a same-action shot sees.
        cachedPlayer       = player;
        cachedPlayerColumn = playerColumn;
        cachedPlayerRow    = playerRow;
        rebuildOccupancy();
        phaseA(playerColumn, playerRow);
        phaseBExecute(playerColumn, playerRow, player);
        phaseCCommit(playerColumn, playerRow, player);
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

    // Phase B (EXECUTE): every alerted enemy runs the action it committed last turn.
    private void phaseBExecute(int playerColumn, int playerRow, Player player) {
        pendingExecuteDeathCount = 0;
        for (int index = 0; index < enemies.size(); index++) {
            Enemy enemy = enemies.get(index);
            if (!enemy.isAlive() || !enemy.isAlerted()) continue;
            // Boss AI is driven entirely by BossFloorController; skip it here.
            if (enemy instanceof Boss) continue;
            enemy.turnCounter++;
            // R6: a stun landed during the player turn — cancel the committed action this turn.
            if (enemy.skipNextAction) {
                enemy.skipNextAction    = false;
                enemy.plannedAction.verb = IntentVerb.STUNNED;
                advanceShardRegrowth(enemy);
                advanceCrustCooling(enemy);
                advanceSowerRegen(enemy);
                enemy.advanceLanceCooldown();
                continue;
            }
            executePlan(enemy, playerColumn, playerRow, player);
            advanceShardRegrowth(enemy);
            advanceCrustCooling(enemy);
            advanceSowerRegen(enemy);
            // Count the lance cooldown down on this Lancer's own clock — including a turn a stun cancelled
            // (the shell reseals on its own schedule, not on the enemy's success). A no-op for everything
            // else, and for an enemy that died during its own action.
            if (enemy.isAlive()) enemy.advanceLanceCooldown();
        }
        // Apply any self-inflicted deaths (e.g. a detonating Plague Hulk) AFTER the loop above
        // finishes, so removing them never shifts enemies.get(index) mid-iteration (R-self-destruct).
        for (int deathIndex = 0; deathIndex < pendingExecuteDeathCount; deathIndex++) {
            killEnemy(pendingExecuteDeaths[deathIndex], false);
            pendingExecuteDeaths[deathIndex] = null;
        }
    }

    /**
     * Advances a shard-bearing enemy's re-growth clock at the END of its turn and plays the gold
     * crystallise flash on the turn a shard actually returns (elemental-golem-auric-sentinel). Called
     * for every acting enemy — including one whose committed action was cancelled by a stun — because
     * the ring regrows on its own clock, not on the enemy's success. A no-op for every archetype with
     * no shards, and for an enemy that died during its own action (a corpse grows nothing).
     */
    private void advanceShardRegrowth(Enemy enemy) {
        if (!enemy.isAlive()) return;
        if (!enemy.advanceShardRegrowth()) return;
        if (impactEventListener != null) {
            impactEventListener.onShardRegrown(enemy.tileColumn, enemy.tileRow,
                    enemy.type.heightMultiplier());
        }
    }

    /**
     * Advances a crust-bearing enemy's COOLING clock at the END of its turn, and ages its live molten
     * trail tiles by the same turn (.claude/agents/ideas/elemental-golem-cinderforge-colossus.txt).
     *
     * <p>Called for EVERY acting enemy — including one whose committed action a stun cancelled — because
     * the crust hardens on the world's clock, not on the enemy's success: a stun-locked Colossus that
     * nobody is shooting is still cooling, which is precisely the "stop shooting and it gets worse"
     * lesson. Only ALERTED enemies reach this, so a Colossus asleep since level load never wakes already
     * armoured. A no-op for every archetype with no crust and no trail, and for an enemy that died during
     * its own action (a corpse cools nothing).
     */
    private void advanceCrustCooling(Enemy enemy) {
        if (!enemy.isAlive()) return;
        enemy.advanceTrailTiles();
        if (!enemy.advanceCrustCooling()) return;
        if (impactEventListener != null) {
            impactEventListener.onCrustCooled(enemy.tileColumn, enemy.tileRow,
                    enemy.type.heightMultiplier(), enemy.crustStacks);
        }
    }

    // Phase C (COMMIT): every alerted enemy decides its NEXT action from the resolved board.
    private void phaseCCommit(int playerColumn, int playerRow, Player player) {
        for (int index = 0; index < enemies.size(); index++) {
            Enemy enemy = enemies.get(index);
            if (!enemy.isAlive() || !enemy.isAlerted()) continue;
            if (enemy instanceof Boss) continue;
            computeNextPlan(enemy, playerColumn, playerRow, player);
            // Track consecutive DEFENDs so shouldDefend can break an endless turtle loop (design feedback).
            if (enemy.plannedAction.verb == IntentVerb.DEFEND) {
                enemy.consecutiveDefendTurns++;
            } else {
                enemy.consecutiveDefendTurns = 0;
            }
            // order-2: fire the intent-icon pop when the freshly committed verb differs from last turn.
            enemy.notifyCommitted(enemy.plannedAction.verb);
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
     * Live enemies that are no longer DORMANT — that is, how many things are currently coming for
     * the player.  Read by the story UI's pacing budget (Story UI order-6 Part B): while this is
     * high the player is in a fight, and a non-critical story line is held back for the lull rather
     * than delivered over the top of it.
     */
    public int countAwakeEnemies() {
        int count = 0;
        for (int index = 0; index < enemies.size(); index++) {
            Enemy enemy = enemies.get(index);
            if (enemy.isAlive() && enemy.state != EnemyState.DORMANT) count++;
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

    // -------------------------------------------------------------------------
    // EXECUTE step — run the action committed on the previous turn, R1-R6 re-validation
    // -------------------------------------------------------------------------

    /** Dispatches the enemy's committed plan. A not-yet-committed plan (fresh wake) does nothing. */
    private void executePlan(Enemy enemy, int playerColumn, int playerRow, Player player) {
        PlannedAction plan = enemy.plannedAction;
        if (!plan.committed) return; // freshly woken — one full turn of warning before its first action
        switch (plan.verb) {
            case ATTACK_MELEE:  executeMeleeAttack(enemy, plan, playerColumn, playerRow, player); break;
            case ATTACK_RANGED: executeRangedAttack(enemy, plan, playerColumn, playerRow, player); break;
            case WIND_UP:       executeWindUp(enemy, playerColumn, playerRow, player);             break;
            case MOVE:          executeMove(enemy, plan, playerColumn, playerRow);                 break;
            case DEFEND:        executeDefend(enemy, plan);                                        break;
            case SPECIAL:       executeSpecial(enemy, plan, playerColumn, playerRow, player);      break;
            case WAIT:
            case STUNNED:
            default:            break; // holds position
        }
    }

    /**
     * Executes a committed melee strike (R1/R2). The swing animation always plays; damage only
     * lands if the player is STILL cardinally adjacent. If the player stepped out of reach the
     * attack whiffs — no damage, no free re-plan — so "bait the swing, step away, punish" is the
     * counter. Void Shroud recomputes its blind-side bonus live from the player's current facing.
     */
    private void executeMeleeAttack(Enemy enemy, PlannedAction plan, int playerColumn, int playerRow, Player player) {
        enemy.state = EnemyState.ATTACKING;
        enemy.triggerAttackAnim();
        faceEnemyToward(enemy, playerColumn, playerRow); // order-6: face the target so flanking it is meaningful
        if (isCardinallyAdjacent(enemy, playerColumn, playerRow)) {
            int damage = enemy.scaledAttackDamage();
            if (enemy.type == EnemyType.VOID_SHROUD
                    && isBehindPlayerFacing(enemy, playerColumn, playerRow, player)) {
                damage = Math.max(1, Math.round(damage * EnemyConstants.VOID_SHROUD_FLANK_DAMAGE_MULTIPLIER));
            }
            player.applyDirectionalDamage(damage, enemy.worldCenterX(), enemy.worldCenterY());
            if (enemyAttackListener != null) enemyAttackListener.onMeleeAttack(enemy);
        }
    }

    /**
     * Executes a committed ranged shot (R3). The tracer always fires down the committed line for
     * readability; damage and status only apply if the player is still on the same cardinal line,
     * in range, with clear LOS and no ally blocking the shot. Stepping off the lane makes the shot
     * miss into the empty lane — the same "you dodged, enjoy the free turn" payoff as the melee whiff.
     *
     * <p>A shard-bearing shooter (elemental-golem-auric-sentinel) must SPEND a shard to fire, and the
     * shard is spent up front — before the fairness re-validation below — so a committed shot the
     * player then steps out of still costs it a shard. Committing to a shot it cannot land is a real
     * cost and a real bait. If the player stripped the ring during their turn the committed shot cannot
     * happen at all: the enemy is disarmed, visibly does nothing, and the player who read the ring is
     * rewarded with a free turn.
     */
    private void executeRangedAttack(Enemy enemy, PlannedAction plan, int playerColumn, int playerRow, Player player) {
        if (enemy.type.maxShards() > 0) {
            if (!enemy.spendShardForShot()) {
                enemy.state = EnemyState.ALERTED;   // disarmed mid-commit — no shot, no animation
                return;
            }
            if (impactEventListener != null) {
                impactEventListener.onShardShattered(enemy.tileColumn, enemy.tileRow,
                        enemy.type.heightMultiplier(), 1);
            }
        }
        enemy.state = EnemyState.ATTACKING;
        enemy.triggerAttackAnim();
        faceEnemyToward(enemy, playerColumn, playerRow); // order-6: a kiter that fires without moving still faces its shot
        boolean stillValid = GameMath.chebyshevDistanceTiles(
                        enemy.tileColumn, enemy.tileRow, playerColumn, playerRow) <= enemy.type.attackRangeTiles()
                && isSameCardinalLine(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)
                && hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)
                && !hasEnemyBlockingShot(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
        if (enemyAttackListener != null) {
            enemyAttackListener.onRangedAttack(enemy, plan.targetColumn, plan.targetRow);
        }
        if (stillValid) {
            // Ranged shot travels down the enemy's cardinal lane — use the enemy tile as the lane
            // origin so a guarding player's facing arc is judged against where the shot comes from.
            player.applyDirectionalDamage(enemy.scaledAttackDamage(),
                    enemy.worldCenterX(), enemy.worldCenterY());
            applyRangedAttackStatusEffect(enemy, player);
        }
    }

    /**
     * Executes a committed WIND_UP charge (R5): the rush locks the direction captured at commit and
     * runs down the lane, stopping at the player, a wall, or an ally. A connecting rush hits at the
     * charge multiplier; a whiff leaves the brute in a punishable one-turn recovery (skipNextAction).
     */
    private void executeWindUp(Enemy enemy, int playerColumn, int playerRow, Player player) {
        // The WIND_UP -> ATTACK pair is shared by two archetypes with very different follow-throughs: the
        // Shell Brute RUSHES down its locked lane (melee), the Rimeshell Lancer FIRES a piercing beam down
        // its locked lane (ranged) and never moves. Both locked their lane a full player turn earlier, so
        // both honour the sidestep counter — they just resolve it differently.
        if (enemy.type == EnemyType.RIMESHELL_LANCER) {
            executeLanceBeam(enemy, playerColumn, playerRow, player);
            return;
        }
        if (enemy.type == EnemyType.VERDANT_SPIRESOWER) {
            // The sower's WIND_UP resolves into a bespoke SOW (terrain creation), not a rush or a beam.
            executeSow(enemy, playerColumn, playerRow);
            return;
        }
        boolean connected = performCharge(enemy, playerColumn, playerRow, player);
        if (!connected) {
            enemy.skipNextAction = true; // committed rush missed — punishable recovery
        }
    }

    /**
     * Executes a committed RIME LANCE (elemental-golem-rimeshell-lancer): a piercing molten beam swept
     * straight down the LOCKED cardinal lane captured at commit time. It resolves every tile from the
     * Lancer to the first WALL / closed door / solid prop — never stopping at a body — so it damages the
     * player (for the full lance damage) AND every OTHER enemy in the lane (for
     * {@link EnemyConstants#RIMESHELL_LANCE_FRIENDLY_FIRE_FRACTION} of it; golems do not care about each
     * other). The player is only hit if they are STILL on the committed lane — a sidestep during the punish
     * window wastes the whole beam (no damage, no re-target), which is the counter, and it works because
     * the lane was locked a turn ago.
     *
     * <p>The shell RESEALS and the cooldown arms at the end of this turn, either way. An ally killed by the
     * splash is queued for deferred removal ({@link #pendingExecuteDeaths}), exactly like a self-destruct,
     * because this runs inside phaseBExecute's by-index iteration over {@code enemies}.
     */
    private void executeLanceBeam(Enemy enemy, int playerColumn, int playerRow, Player player) {
        enemy.state = EnemyState.ATTACKING;
        enemy.triggerAttackAnim();
        int stepColumn = enemy.lanceDirectionColumn;
        int stepRow    = enemy.lanceDirectionRow;
        int lanceDamage = enemy.scaledAttackDamage();
        int allyDamage  = Math.max(1, Math.round(
                lanceDamage * EnemyConstants.RIMESHELL_LANCE_FRIENDLY_FIRE_FRACTION));

        int column = enemy.tileColumn;
        int row    = enemy.tileRow;
        int reachTiles = 0;
        // Walk the locked lane out to the weapon's range, stopping at the first blocker. The beam is
        // PIERCING, so a body in the lane is damaged but does NOT stop the beam.
        for (int step = 0; step < enemy.type.attackRangeTiles(); step++) {
            int nextColumn = column + stepColumn;
            int nextRow    = row    + stepRow;
            if (isLanceBlocked(nextColumn, nextRow)) break;   // wall / closed door / solid prop
            column = nextColumn;
            row    = nextRow;
            reachTiles++;
            if (column == playerColumn && row == playerRow) {
                // Player still on the committed lane — the shot connects. Use the Lancer's tile as the lane
                // origin so a guarding player's facing arc is judged against where the beam comes from.
                player.applyDirectionalDamage(lanceDamage, enemy.worldCenterX(), enemy.worldCenterY());
            }
            Object occupant = enemyAt(column, row);
            if (occupant != null && occupant != enemy) {
                damageAllyInLane((Enemy) occupant, allyDamage);
            }
        }

        enemy.resealShell();
        enemy.lanceCooldownTurns = EnemyConstants.RIMESHELL_LANCE_COOLDOWN_TURNS;
        if (impactEventListener != null) {
            impactEventListener.onLanceBeam(enemy.tileColumn, enemy.tileRow,
                    stepColumn, stepRow, reachTiles, enemy.type.heightMultiplier());
        }
    }

    /**
     * Applies a friendly-fire splash hit to an ally caught in the lance lane and queues it for deferred
     * removal if it dies (never removing it mid-iteration, per {@link #pendingExecuteDeaths}). No XP or
     * credits are awarded: the player did not land this kill, the beam did — the payoff is the room
     * thinning itself out, which is reward enough.
     */
    private void damageAllyInLane(Enemy ally, int allyDamage) {
        if (!ally.isAlive()) return;
        ally.applyDamage(allyDamage);
        if (impactEventListener != null) {
            impactEventListener.onEnemyHit(ally.worldCenterX(), ally.worldCenterY(),
                    ally.type.heightMultiplier(), allyDamage);
        }
        if (ally.isAlive()) return;
        // Dead from the splash — fire the death burst and queue removal (guard against a double-queue,
        // e.g. two Lancers beaming the same tile in one turn).
        if (impactEventListener != null) {
            impactEventListener.onEnemyKilled(ally.worldCenterX(), ally.worldCenterY(),
                    ally.type.heightMultiplier(), allyDamage);
        }
        for (int deathIndex = 0; deathIndex < pendingExecuteDeathCount; deathIndex++) {
            if (pendingExecuteDeaths[deathIndex] == ally) return;
        }
        if (pendingExecuteDeathCount < MAX_PENDING_EXECUTE_DEATHS) {
            pendingExecuteDeaths[pendingExecuteDeathCount++] = ally;
        }
    }

    /** True when a tile stops the rime lance: out of bounds, a wall, a closed door, or a solid prop. */
    private boolean isLanceBlocked(int tileColumn, int tileRow) {
        if (tileColumn < 0 || tileColumn >= level.getWidth())  return true;
        if (tileRow    < 0 || tileRow    >= level.getHeight()) return true;
        char cell = level.getCell(tileColumn, tileRow);
        return Level.isWall(cell)
                || doorManager.blocksSight(tileColumn, tileRow)
                || Level.isPropSolid(cell)
                || Level.isColumn(cell)
                || Level.isSpire(cell);   // a crystal spire stops the rime lance like any solid tile
    }

    /**
     * Executes a committed DEFEND (strategy-combat-order-3): the enemy braces and gains the Block it
     * telegraphed last turn. The buffer becomes active immediately, so it protects the enemy against
     * the player's NEXT turn — the turn during which the player reads the active blue Block number and
     * decides whether to chip it, wait it out, or spend a Block-piercing option.
     */
    private void executeDefend(Enemy enemy, PlannedAction plan) {
        enemy.state = EnemyState.DEFENDING;
        enemy.gainBlock(plan.blockGain, BalanceConfig.BLOCK_MAX, BalanceConfig.BLOCK_DECAY_TURNS);
    }

    /**
     * Executes a committed MOVE. A retreat steps away from the player; an approach steps toward the
     * committed goal tile, falling back to closing on the player (with stuck/wiggle handling) when
     * the goal is unreachable this step (R4 — a failed move re-paths quietly, unlike a whiffed attack).
     */
    private void executeMove(Enemy enemy, PlannedAction plan, int playerColumn, int playerRow) {
        int originColumn = enemy.tileColumn;
        int originRow    = enemy.tileRow;
        if (plan.fleeFromTarget) {
            stepAway(enemy, playerColumn, playerRow);
        } else if (plan.goalIsFixedTile) {
            // Flanker: head to the blind-side tile; if that route is blocked close on the player.
            if (!stepTowardTile(enemy, plan.targetColumn, plan.targetRow, playerColumn, playerRow)) {
                stepToward(enemy, playerColumn, playerRow);
            }
        } else if (enemy.type == EnemyType.VERDANT_SPIRESOWER) {
            // Sower: close on the player but PREFER a step that keeps a living spire between it and them —
            // it would rather reposition behind its own terrain than walk into the open.
            stepTowardPreferringCover(enemy, playerColumn, playerRow);
        } else {
            // Plain chase — track the player's LIVE position (stuck/wiggle aware), not the stale
            // commit-time tile in plan.targetColumn/Row (that tile is kept only for icon display).
            stepToward(enemy, playerColumn, playerRow);
        }
        enemy.state = EnemyState.CHASING;
        // The move has RESOLVED, so the vacated tile is known and the enemy is already standing
        // elsewhere. A blocked step leaves origin == current, and neither hook fires: nothing was
        // vacated and nothing landed.
        if (enemy.tileColumn != originColumn || enemy.tileRow != originRow) {
            igniteMoltenTrail(enemy, originColumn, originRow);
            notifyHeavyFootfall(enemy, playerColumn, playerRow);
        }
    }

    /**
     * MOLTEN TRAIL (.claude/agents/ideas/elemental-golem-cinderforge-colossus.txt): sets fire to the tile
     * a Cinderforge Colossus just VACATED, so the ground the player would have retreated across is
     * burning behind it. Three deliberate properties:
     *
     * <p>It uses the SHIPPED hazard system rather than a new hazard type, so the trail participates in
     * fire spread and explosive-barrel chain reactions for free — baiting a Colossus across a barrel is
     * the archetype's best emergent counter, and it costs no code. It burns this enemy's ALLIES exactly
     * as it burns the player, because the hazard system applies BURNING to any host on the tile and
     * special-casing that away for one archetype would be both more code and worse tactics. And it never
     * ignites the tile the Colossus is STANDING on, so the player is never denied the tile they need to
     * melee it from without warning.
     *
     * <p>Bounded by the per-Colossus live-tile cap, so a long chase cannot carpet a floor.
     */
    private void igniteMoltenTrail(Enemy enemy, int vacatedColumn, int vacatedRow) {
        int trailFireTurns = enemy.type.leavesTrailFireTurns();
        if (trailFireTurns <= 0)        return;
        if (hazardIgniteTarget == null) return;   // headless / no-World construction — safe no-op
        if (!enemy.canLightTrailTile()) return;   // at the live-tile cap; the oldest expires first
        hazardIgniteTarget.igniteFire(vacatedColumn, vacatedRow, trailFireTurns);
        enemy.recordTrailTile(trailFireTurns);
    }

    /** Fires the heavy-footfall beat (ember burst + distance-scaled shake) for a massive archetype. */
    private void notifyHeavyFootfall(Enemy enemy, int playerColumn, int playerRow) {
        if (enemy.type != EnemyType.CINDERFORGE_COLOSSUS) return;
        if (impactEventListener == null) return;
        int distanceTiles = GameMath.chebyshevDistanceTiles(
                enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
        impactEventListener.onHeavyFootfall(enemy.tileColumn, enemy.tileRow,
                enemy.type.heightMultiplier(), distanceTiles);
    }

    // -------------------------------------------------------------------------
    // SPECIAL ability execution (strategy-combat-order-5) — EXECUTE side
    // Each damaging/board-affecting special re-validates fairness at execution just like a normal
    // attack (R1-R3): the player who reads the telegraph and repositions during their turn is rewarded.
    // -------------------------------------------------------------------------

    /** Dispatches a committed SPECIAL to its ability handler. A null ability (defensive) holds position. */
    private void executeSpecial(Enemy enemy, PlannedAction plan,
                                int playerColumn, int playerRow, Player player) {
        if (plan.specialAbility == null) return;
        // THE BESTIARY VOICE (narrative-rework order-5 C): reported at RESOLUTION, never at the
        // telegraph — the rule is only teachable at the moment the player watches it happen. Fired
        // before dispatch so a SELF_DESTRUCT is explained while its countdown is still running,
        // which is the only turn the advice ("get two tiles clear or kill it now") is any use.
        if (specialResolvedListener != null) {
            specialResolvedListener.onSpecialResolved(plan.specialAbility);
        }
        switch (plan.specialAbility) {
            case BUFF_SELF:     executeBuffSelf(enemy);                                      break;
            case DEBUFF_PLAYER: executeDebuffPlayer(enemy, playerColumn, playerRow, player); break;
            case SUMMON:        executeSummon(enemy, playerColumn, playerRow);               break;
            case AREA_STRIKE:   executeAreaStrike(enemy, playerColumn, playerRow, player);   break;
            case SELF_DESTRUCT: executeSelfDestruct(enemy, playerColumn, playerRow, player); break;
            default:                                                                         break;
        }
    }

    /**
     * BUFF_SELF: the caster gains EMPOWERED, so every subsequent {@code scaledAttackDamage()} — the
     * predicted number AND the real hit — is raised while the buff lasts. Telegraphed a turn ahead, so
     * the counter is "kill the buffer before its empowered swing lands."
     */
    private void executeBuffSelf(Enemy enemy) {
        enemy.state = EnemyState.ATTACKING;
        enemy.triggerTelegraph();
        if (statusEffectController != null) {
            statusEffectController.apply(enemy, StatusType.EMPOWERED,
                    EnemyConstants.BUFF_SELF_DURATION_TURNS, EnemyConstants.BUFF_SELF_EMPOWERED_PERCENT, this);
        }
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor("EMPOWERED", EventTextSystem.COLOR_GOLD);
        }
    }

    /**
     * DEBUFF_PLAYER: fires a control shot down the committed cardinal lane (R3-style re-validation). The
     * player who stepped off the line during their turn dodges it entirely; otherwise they take a SLOWED
     * or BLINDED debuff (resolved per archetype). Deals no HP damage — the intent icon shows no number.
     */
    private void executeDebuffPlayer(Enemy enemy, int playerColumn, int playerRow, Player player) {
        enemy.state = EnemyState.ATTACKING;
        enemy.triggerAttackAnim();
        if (enemyAttackListener != null) {
            enemyAttackListener.onRangedAttack(enemy, playerColumn, playerRow);
        }
        boolean stillValid = GameMath.chebyshevDistanceTiles(
                        enemy.tileColumn, enemy.tileRow, playerColumn, playerRow) <= enemy.type.attackRangeTiles()
                && isSameCardinalLine(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)
                && hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
        if (!stillValid || statusEffectController == null) return;

        StatusType debuff = debuffStatusFor(enemy.type);
        int durationTurns = debuffDurationFor(debuff);
        statusEffectController.apply(player, debuff, durationTurns, 0, enemy);
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor(debuffLabelFor(debuff), EventTextSystem.COLOR_GREY);
        }
        if (debuff == StatusType.WEAK) {
            showCombatTipOnce("weak_player", "WEAKENED — your damage is cut. Retreat or wait it out");
        }
    }

    /**
     * Which control status a debuffing archetype inflicts: the eyes BLIND, the acid casters (Mire
     * Wraith / Acid Drone) apply WEAK — corroding the marine's output (strategy-combat-order-6) — and
     * everything else SLOWS.
     */
    private static StatusType debuffStatusFor(EnemyType type) {
        // Single source of truth: EnemyType owns the mapping so the live effect and the order-5
        // Threat-Point pricing of DEBUFF_PLAYER can never drift apart.
        return type.debuffStatusType();
    }

    /** Duration (world turns) for each control debuff DEBUFF_PLAYER can inflict. */
    private static int debuffDurationFor(StatusType debuff) {
        switch (debuff) {
            case BLINDED: return EnemyConstants.DEBUFF_BLIND_DURATION_TURNS;
            case WEAK:    return EnemyConstants.DEBUFF_WEAK_DURATION_TURNS;
            case SLOWED:
            default:      return EnemyConstants.DEBUFF_SLOW_DURATION_TURNS;
        }
    }

    /** Floater label for each DEBUFF_PLAYER control status. */
    private static String debuffLabelFor(StatusType debuff) {
        switch (debuff) {
            case BLINDED: return "BLINDED";
            case WEAK:    return "WEAKENED";
            case SLOWED:
            default:      return "SLOWED";
        }
    }

    /**
     * SUMMON: spawns the batch of 2-3 chaff pre-selected at commit ({@link #selectSummonTargets}) into
     * the floor-telegraphed empty cells, re-validating each and bounded by the per-room live-enemy
     * ceiling so the encounter's Threat-Point budget stays honest (docs/game-balance-authority.txt).
     * One cast per summoner, lifetime — once at least one chaff spawns, {@code summonsSpawned > 0}
     * permanently disqualifies SUMMON in {@link #isSpecialUsable} (see there), so this fires at most
     * once per enemy. Counter: kill the summoner, or just outlast its single cast.
     */
    private void executeSummon(Enemy enemy, int playerColumn, int playerRow) {
        enemy.state = EnemyState.ATTACKING;
        enemy.triggerTelegraph();
        EnemyType summonType = summonTypeFor(enemy.type);
        int spawned = 0;
        // Spawn into the EXACT tiles telegraphed at commit — re-validating each, since the player or an
        // ally may have stepped onto a marked cell during their turn (that cell simply spawns nothing,
        // the same "reposition dodges it" fairness the other specials honour).
        for (int targetIndex = 0; targetIndex < enemy.summonTargetCount; targetIndex++) {
            if (countLiveEnemies() >= EnemyConstants.SUMMON_ROOM_LIVE_CAP) break;
            int neighbourColumn = enemy.summonTargetColumns[targetIndex];
            int neighbourRow    = enemy.summonTargetRows[targetIndex];
            if (!isPassableForEnemy(neighbourColumn, neighbourRow, playerColumn, playerRow)) continue;
            spawnEnemy(summonType, neighbourColumn, neighbourRow, currentDepth);
            enemy.summonsSpawned++;
            spawned++;
            if (impactEventListener != null) {
                impactEventListener.onEnemySpawned(neighbourColumn, neighbourRow, summonType.heightMultiplier());
            }
        }
        enemy.summonTargetCount = 0; // batch consumed — clears the floor telegraph until the next commit
        if (spawned > 0 && eventTextSystem != null) {
            eventTextSystem.spawnWithColor("SUMMON", EventTextSystem.COLOR_GREEN);
        }
    }

    /** The chaff type a summoner spawns. Single source of truth: EnemyType owns it so the summon
     *  TP pricing (order 5) matches what actually spawns. */
    private static EnemyType summonTypeFor(EnemyType type) {
        return type.summonType();
    }

    /**
     * AREA_STRIKE: a telegraphed slam that hits the player if they are still within the strike radius
     * (Chebyshev square) of the enemy. The player who read the icon and stepped out of the marked band
     * during their turn takes nothing — reposition is the counter.
     */
    private void executeAreaStrike(Enemy enemy, int playerColumn, int playerRow, Player player) {
        enemy.state = EnemyState.ATTACKING;
        enemy.triggerAttackAnim();
        if (enemyAttackListener != null) enemyAttackListener.onMeleeAttack(enemy);
        boolean inBlast = GameMath.chebyshevDistanceTiles(
                enemy.tileColumn, enemy.tileRow, playerColumn, playerRow) <= EnemyConstants.AREA_STRIKE_RADIUS_TILES;
        if (inBlast) {
            player.applyDirectionalDamage(areaStrikeDamage(enemy),
                    enemy.worldCenterX(), enemy.worldCenterY());
        }
    }

    // -------------------------------------------------------------------------
    // COMMIT step — decide the NEXT action from the resolved board (writes plan, never acts)
    // -------------------------------------------------------------------------

    /** Routes each archetype to its plan-computation logic (mirrors the old act-dispatch branching). */
    private void computeNextPlan(Enemy enemy, int playerColumn, int playerRow, Player player) {
        PlannedAction plan = enemy.plannedAction;
        // Plague Hulk low-HP finisher (.claude/agents/ideas/plague-hulk-self-destruct.txt) pre-empts
        // EVERY other branch, including the scripted move-set below: once primed it is STICKY — the
        // Hulk never reverts to attacking, moving, or Block-defending, it only ever re-telegraphs its
        // countdown until it detonates or is killed first.
        if (enemy.type == EnemyType.PLAGUE_HULK && shouldPrimeOrContinueSelfDestruct(enemy)) {
            commitSelfDestruct(enemy, plan);
            return;
        }
        // Scripted archetypes consult their SPECIAL move-set first (order-5): on its cadence turn an
        // enemy may commit a telegraphed buff/debuff/summon/area move in place of a plain attack/move,
        // producing the readable pattern the player learns to outplay. Off-cadence (or when no ability's
        // preconditions hold) it falls through to the normal archetype behaviour below.
        if (tryCommitSpecial(enemy, plan, playerColumn, playerRow, player)) {
            return;
        }
        if (!enemy.type.isRanged()) {
            // Melee archetypes branch to their tactical VERB (balance idea 4, Pillar 2):
            //   SHELL_BRUTE — CHARGER: telegraphed rush; VOID_SHROUD — FLANKER: attack the blind
            //   side; everything else uses the shared greedy melee behaviour.
            if (enemy.type == EnemyType.SHELL_BRUTE) {
                computeChargerPlan(enemy, plan, playerColumn, playerRow);
            } else if (enemy.type == EnemyType.VOID_SHROUD) {
                computeFlankerPlan(enemy, plan, playerColumn, playerRow, player);
            } else if (enemy.type == EnemyType.VERDANT_SPIRESOWER) {
                computeSowerPlan(enemy, plan, playerColumn, playerRow);
            } else {
                computeMeleePlan(enemy, plan, playerColumn, playerRow);
            }
        } else if (enemy.type == EnemyType.AURIC_SENTINEL) {
            computeSentinelPlan(enemy, plan, playerColumn, playerRow);
        } else if (enemy.type == EnemyType.RIMESHELL_LANCER) {
            computeLancerPlan(enemy, plan, playerColumn, playerRow);
        } else {
            computeRangedPlan(enemy, plan, playerColumn, playerRow);
        }
    }

    /** Shared greedy melee plan: strike when cardinally adjacent, otherwise close the gap. */
    private void computeMeleePlan(Enemy enemy, PlannedAction plan, int playerColumn, int playerRow) {
        if (isCardinallyAdjacent(enemy, playerColumn, playerRow)) {
            plan.setAttack(IntentVerb.ATTACK_MELEE, playerColumn, playerRow, enemy.scaledAttackDamage());
        } else if (shouldDefend(enemy, playerColumn, playerRow)) {
            commitDefend(enemy, plan);
        } else if (enemy.shouldMoveThisTurn()) {
            plan.set(IntentVerb.MOVE, playerColumn, playerRow); // goal = the player's tile
        } else {
            plan.set(IntentVerb.WAIT, enemy.tileColumn, enemy.tileRow);
        }
    }

    /**
     * CHARGER plan (Shell Brute, Pillar 2): when a clear cardinal lane opens within charge range the
     * brute commits a WIND_UP — a readable rim-flash telegraph shown for the whole player turn — then
     * rushes down that lane next turn (R5). Sidestepping off the lane is the counter. Adjacent with no
     * room, it commits a normal melee hit instead.
     */
    private void computeChargerPlan(Enemy enemy, PlannedAction plan, int playerColumn, int playerRow) {
        if (isCardinallyAdjacent(enemy, playerColumn, playerRow)) {
            // No room to build up a charge — commit a normal-strength hit.
            plan.setAttack(IntentVerb.ATTACK_MELEE, playerColumn, playerRow, enemy.scaledAttackDamage());
            return;
        }
        if (canBeginCharge(enemy, playerColumn, playerRow)) {
            enemy.chargeDirectionColumn = Integer.signum(playerColumn - enemy.tileColumn);
            enemy.chargeDirectionRow    = Integer.signum(playerRow - enemy.tileRow);
            int chargeDamage = Math.max(1, Math.round(
                    enemy.scaledAttackDamage() * EnemyConstants.SHELL_BRUTE_CHARGE_DAMAGE_MULTIPLIER));
            plan.setWindUp(playerColumn, playerRow, chargeDamage, 1);
            enemy.state = EnemyState.WINDING_UP;
            enemy.triggerTelegraph();   // rim flash telegraph, visible for the whole player turn
            return;
        }
        // No charge lane and not adjacent — a BRUISER guardian braces on cadence (or when low), which
        // is the read-and-adapt beat that makes the Shell Brute more than a straight-line rusher.
        if (shouldDefend(enemy, playerColumn, playerRow)) {
            commitDefend(enemy, plan);
        } else if (enemy.shouldMoveThisTurn()) {
            plan.set(IntentVerb.MOVE, playerColumn, playerRow);
        } else {
            plan.set(IntentVerb.WAIT, enemy.tileColumn, enemy.tileRow);
        }
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
     * the player, a wall, or another enemy. The rush travels blind down the LOCKED lane (the row
     * for a horizontal charge, the column for a vertical one) captured at commit time — it does not
     * home in on the player's live position. A player who sidesteps off that lane during their turn
     * leaves the lane empty and the rush passes through, whiffing entirely (R5 sidestep counter):
     * before this fix, "connected" was decided purely by final Manhattan distance to the player's
     * live tile, which could false-positive when the player stepped exactly one tile perpendicular
     * (ending cardinally adjacent from the side even though they left the charge lane). Returns true
     * when the rush stayed on the lane and ended adjacent to the player — that "reached" case is NOT a
     * guaranteed hit: {@code SHELL_BRUTE_CHARGE_STOP_SHORT_CHANCE} of the time the brute over-commits and
     * halts next to them dealing no damage (a punishable opening), otherwise it lands the full charge
     * multiplier. Returns false only on a true lane whiff, which stuns the brute (executeWindUp).
     */
    private boolean performCharge(Enemy enemy, int playerColumn, int playerRow, Player player) {
        int stepColumn = enemy.chargeDirectionColumn;
        int stepRow    = enemy.chargeDirectionRow;
        int lockedLaneRow    = enemy.tileRow;    // fixed axis for a horizontal charge (stepRow == 0)
        int lockedLaneColumn = enemy.tileColumn; // fixed axis for a vertical charge (stepColumn == 0)
        enemy.state = EnemyState.ATTACKING;
        enemy.triggerAttackAnim();

        int maxRushTiles = EnemyConstants.SHELL_BRUTE_CHARGE_TRIGGER_MAX_TILES;
        for (int step = 0; step < maxRushTiles; step++) {
            boolean playerOnLane = (stepRow == 0) ? (playerRow == lockedLaneRow)
                                                   : (playerColumn == lockedLaneColumn);
            if (playerOnLane && GameMath.manhattanDistanceTiles(enemy.tileColumn, enemy.tileRow,
                    playerColumn, playerRow) == 1) {
                break; // already in melee range along the locked lane — stop and strike
            }
            int nextColumn = enemy.tileColumn + stepColumn;
            int nextRow    = enemy.tileRow    + stepRow;
            if (nextColumn == playerColumn && nextRow == playerRow) break; // can't share the player's tile
            if (!isPassableForEnemy(nextColumn, nextRow, playerColumn, playerRow)) break;
            commitMove(enemy, nextColumn, nextRow);
        }

        boolean playerOnLane = (stepRow == 0) ? (playerRow == lockedLaneRow)
                                               : (playerColumn == lockedLaneColumn);
        boolean reachedPlayer = playerOnLane && GameMath.manhattanDistanceTiles(
                enemy.tileColumn, enemy.tileRow, playerColumn, playerRow) == 1;
        if (!reachedPlayer) {
            return false; // rushed into an empty lane (player sidestepped) — whiff → punishable recovery
        }
        // The rush reached the player. Most of the time (SHELL_BRUTE_CHARGE_STOP_SHORT_CHANCE) the brute
        // over-commits and screeches to a halt right next to them WITHOUT landing the blow, handing the
        // player a guaranteed swing before it can strike again next turn (design feedback: a charge
        // should usually leave the brute punishable, not just delete HP). The rest of the time it lands
        // the full telegraphed charge hit. Either way it ends adjacent, so there is NO whiff-recovery —
        // executeWindUp only stuns the brute when the rush missed the player entirely (returns false above).
        if (effectRandom.nextFloat() < EnemyConstants.SHELL_BRUTE_CHARGE_STOP_SHORT_CHANCE) {
            enemy.triggerTelegraph(); // brief flinch/flash sells the abrupt stop
            if (eventTextSystem != null) {
                eventTextSystem.spawnWithColor("STAGGERED", EventTextSystem.COLOR_GREY);
            }
        } else {
            int chargeDamage = Math.max(1, Math.round(
                    enemy.scaledAttackDamage() * EnemyConstants.SHELL_BRUTE_CHARGE_DAMAGE_MULTIPLIER));
            player.applyDirectionalDamage(chargeDamage, enemy.worldCenterX(), enemy.worldCenterY());
            if (enemyAttackListener != null) enemyAttackListener.onMeleeAttack(enemy);
        }
        return true; // reached the player — adjacent now, will commit a normal melee next turn
    }

    /** True when the enemy is exactly one tile away from the player along a cardinal (melee reach). */
    private static boolean isCardinallyAdjacent(Enemy enemy, int playerColumn, int playerRow) {
        return GameMath.manhattanDistanceTiles(
                enemy.tileColumn, enemy.tileRow, playerColumn, playerRow) == 1;
    }

    // -------------------------------------------------------------------------
    // SELF_DESTRUCT — Plague Hulk low-HP finisher (.claude/agents/ideas/plague-hulk-self-destruct.txt)
    // -------------------------------------------------------------------------

    /**
     * True once a Plague Hulk should be (or already is) primed. STICKY by design: once
     * {@code selfDestructPrimed} is set this always returns true regardless of any later HP change
     * (a heal, though none currently exist, would not un-prime it), so the countdown always runs to
     * completion once started.
     */
    private static boolean shouldPrimeOrContinueSelfDestruct(Enemy enemy) {
        return enemy.selfDestructPrimed
                || enemy.health <= Math.round(enemy.maxHealth * EnemyConstants.PLAGUE_HULK_SELF_DESTRUCT_HP_PERCENT);
    }

    /**
     * Commits (or re-commits) the SELF_DESTRUCT special. First entry primes the full brace countdown;
     * every later commit just re-telegraphs the CURRENT countdown (decremented by executeSelfDestruct
     * on the turns in between) so the player always sees an accurate "turns until detonation" number.
     * Grants no Block — a primed Hulk is meant to be fully burstable, never a shield to chip through.
     */
    private void commitSelfDestruct(Enemy enemy, PlannedAction plan) {
        if (!enemy.selfDestructPrimed) {
            enemy.selfDestructPrimed         = true;
            enemy.selfDestructTurnsRemaining = EnemyConstants.PLAGUE_HULK_SELF_DESTRUCT_BRACE_TURNS;
        }
        enemy.state = EnemyState.SELF_DESTRUCTING;
        enemy.triggerTelegraph();
        plan.setSpecial(SpecialAbility.SELF_DESTRUCT, enemy.tileColumn, enemy.tileRow,
                selfDestructBlastDamage(enemy));
    }

    /**
     * SELF_DESTRUCT execute: while {@code selfDestructTurnsRemaining > 1} the Hulk just counts down
     * (no attack, no move, no Block). On the final primed turn (countdown == 1) it detonates — a
     * wall-stopped cross blast against the player, then removes itself with
     * {@code selfDestructed = true} so the death hazard hook (World) knows to leave the MASSIVE toxic
     * cloud rather than the ordinary minimal one. The kill is deferred to a post-loop pass (see
     * {@link #pendingExecuteDeaths}) because this method runs from inside phaseBExecute's own
     * by-index iteration over {@code enemies}.
     */
    private void executeSelfDestruct(Enemy enemy, int playerColumn, int playerRow, Player player) {
        enemy.state = EnemyState.SELF_DESTRUCTING;
        enemy.triggerTelegraph();
        if (enemy.selfDestructTurnsRemaining > 1) {
            enemy.selfDestructTurnsRemaining--;
            return;
        }
        int blastDamage = selfDestructBlastDamage(enemy);
        applySelfDestructBlast(enemy, blastDamage, playerColumn, playerRow, player);
        enemy.selfDestructed = true;
        enemy.health         = 0; // dead as of this instant — isAlive() must reflect it immediately
        if (impactEventListener != null) {
            impactEventListener.onEnemyKilled(enemy.worldCenterX(), enemy.worldCenterY(),
                    enemy.type.heightMultiplier(), blastDamage);
        }
        if (pendingExecuteDeathCount < MAX_PENDING_EXECUTE_DEATHS) {
            pendingExecuteDeaths[pendingExecuteDeathCount++] = enemy;
        }
    }

    /** Detonation damage: scaled attack times the blast multiplier, hard-capped, minimum 1. */
    private static int selfDestructBlastDamage(Enemy enemy) {
        int scaled = Math.round(enemy.scaledAttackDamage()
                * EnemyConstants.PLAGUE_HULK_SELF_DESTRUCT_BLAST_DAMAGE_MULTIPLIER);
        return Math.min(EnemyConstants.PLAGUE_HULK_SELF_DESTRUCT_BLAST_DAMAGE_MAX, Math.max(1, scaled));
    }

    /**
     * Applies the direct detonation hit if the player is standing on the enemy's own tile or any
     * cardinal arm out to {@code PLAGUE_HULK_SELF_DESTRUCT_BLAST_RADIUS_TILES} tiles — each arm stops
     * at the first wall (the blast doesn't wrap corners), mirroring ExplosiveBarrelManager's cross
     * pattern. Routed through applyDirectionalDamage so a GUARDing player still gets their facing-arc
     * reduction, with the Hulk's tile as the lane origin.
     */
    private void applySelfDestructBlast(Enemy enemy, int blastDamage,
                                        int playerColumn, int playerRow, Player player) {
        boolean playerHit = enemy.tileColumn == playerColumn && enemy.tileRow == playerRow;
        for (int directionIndex = 0; directionIndex < 4 && !playerHit; directionIndex++) {
            int stepColumn = STEP_COLUMNS[directionIndex];
            int stepRow    = STEP_ROWS[directionIndex];
            for (int distance = 1; distance <= EnemyConstants.PLAGUE_HULK_SELF_DESTRUCT_BLAST_RADIUS_TILES;
                    distance++) {
                int armColumn = enemy.tileColumn + stepColumn * distance;
                int armRow    = enemy.tileRow    + stepRow * distance;
                if (Level.isWall(level.getCell(armColumn, armRow))) break; // arm stops at the first wall
                if (armColumn == playerColumn && armRow == playerRow) {
                    playerHit = true;
                    break;
                }
            }
        }
        if (playerHit) {
            player.applyDirectionalDamage(blastDamage, enemy.worldCenterX(), enemy.worldCenterY());
        }
    }

    /**
     * FLANKER plan (Void Shroud, Pillar 2): commits a melee strike when adjacent — carrying the
     * blind-side bonus in its predicted-damage number if it currently sits behind the player's
     * facing — otherwise commits a MOVE toward the tile directly behind the player. The executor
     * re-checks the blind side live, so "rotate to face it / keep your back covered" stays the
     * counterplay. The move goal is the flank tile; executeMove falls back to closing directly
     * when that route is blocked.
     */
    private void computeFlankerPlan(Enemy enemy, PlannedAction plan, int playerColumn, int playerRow, Player player) {
        if (isCardinallyAdjacent(enemy, playerColumn, playerRow)) {
            int damage = enemy.scaledAttackDamage();
            if (isBehindPlayerFacing(enemy, playerColumn, playerRow, player)) {
                damage = Math.max(1, Math.round(
                        damage * EnemyConstants.VOID_SHROUD_FLANK_DAMAGE_MULTIPLIER));
            }
            plan.setAttack(IntentVerb.ATTACK_MELEE, playerColumn, playerRow, damage);
        } else if (shouldDefend(enemy, playerColumn, playerRow)) {
            commitDefend(enemy, plan);
        } else if (enemy.shouldMoveThisTurn()) {
            int faceColumn  = Math.round(player.directionX);
            int faceRow     = Math.round(player.directionY);
            int flankColumn = playerColumn - faceColumn; // tile directly behind the player
            int flankRow    = playerRow    - faceRow;
            plan.setMoveToTile(flankColumn, flankRow);
        } else {
            plan.set(IntentVerb.WAIT, enemy.tileColumn, enemy.tileRow);
        }
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

    // -------------------------------------------------------------------------
    // SOWER plan (Verdant Spiresower, elemental-golem-verdant-spiresower) — COMMIT side.
    // The first enemy that EDITS THE LEVEL: it plants solid, sight-blocking crystal SPIRES that heal it
    // while they stand. Sowing is a bespoke WIND_UP -> sow hook (the Shell Brute charge precedent), so it
    // introduces no SpecialAbility verb. The heal is priced as an armour pool; the spire TERRAIN is unpriced.
    // -------------------------------------------------------------------------

    /**
     * SOWER plan preference order (per the design doc): sow (cadence due, under cap, at least one legal
     * tile) > melee attack if adjacent > brace if hurting > step toward the player PREFERRING cover (a tile
     * with a living spire between it and the player) > wait. Sowing commits a WIND_UP telegraphed a full
     * player turn ahead, and the chosen target tiles are marked (see the floor telegraph) so a spire never
     * appears where the player did not see it coming.
     */
    private void computeSowerPlan(Enemy enemy, PlannedAction plan, int playerColumn, int playerRow) {
        if (canSowThisTurn(enemy) && selectSowTargets(enemy, playerColumn, playerRow) > 0) {
            // Predicted damage 0 — a sow is terrain creation, not an attack; the WIND_UP frame telegraphs it.
            plan.setWindUp(enemy.tileColumn, enemy.tileRow, 0, 1);
            enemy.state = EnemyState.WINDING_UP;
            enemy.triggerTelegraph();   // one full player turn of warning
            return;
        }
        enemy.sowTargetCount = 0; // not sowing this turn — clear any stale floor telegraph
        if (isCardinallyAdjacent(enemy, playerColumn, playerRow)) {
            plan.setAttack(IntentVerb.ATTACK_MELEE, playerColumn, playerRow, enemy.scaledAttackDamage());
        } else if (shouldDefend(enemy, playerColumn, playerRow)) {
            commitDefend(enemy, plan);
        } else if (enemy.shouldMoveThisTurn()) {
            plan.set(IntentVerb.MOVE, playerColumn, playerRow); // executeMove steps it toward the player, cover-preferring
        } else {
            plan.set(IntentVerb.WAIT, enemy.tileColumn, enemy.tileRow);
        }
    }

    /** True when this sower may begin a sow this turn: it has a spire seam, it is on cadence, and it is under its cap. */
    private boolean canSowThisTurn(Enemy enemy) {
        if (spireHitTarget == null) return false;
        if (enemy.turnCounter % EnemyConstants.SPIRESOWER_SOW_CADENCE_TURNS != 0) return false;
        return spireHitTarget.liveSpireCountForOwner(enemy.getId()) < EnemyConstants.SPIRESOWER_MAX_LIVE_SPIRES;
    }

    /**
     * Chooses up to {@code SPIRESOWER_SPIRES_PER_SOW} sow target tiles (bounded also by the remaining cap
     * headroom) and stores them on the enemy so the floor telegraph and {@link #executeSow} agree on the
     * exact cells. A candidate must be within the sow radius, walkable floor (not wall/prop/door/existing
     * spire), not the player's or any enemy's tile, and — the NON-OPTIONAL rule — must not seal the player
     * off from a level exit (the SpireManager flood-fill guard, treating already-chosen candidates as solid
     * too). Selection is deterministic (the seeded {@code effectRandom} stream, never Math.random). Returns
     * the number of tiles chosen (0 = no legal sow this turn, fall through to normal behaviour).
     */
    private int selectSowTargets(Enemy enemy, int playerColumn, int playerRow) {
        int radius = EnemyConstants.SPIRESOWER_SOW_RADIUS_TILES;
        int candidateCount = 0;
        for (int rowOffset = -radius; rowOffset <= radius; rowOffset++) {
            for (int columnOffset = -radius; columnOffset <= radius; columnOffset++) {
                if (columnOffset == 0 && rowOffset == 0) continue; // never under itself
                int candidateColumn = enemy.tileColumn + columnOffset;
                int candidateRow    = enemy.tileRow    + rowOffset;
                if (!isLegalSowTile(candidateColumn, candidateRow, playerColumn, playerRow)) continue;
                sowCandidateColumns[candidateCount] = candidateColumn;
                sowCandidateRows[candidateCount]    = candidateRow;
                candidateCount++;
            }
        }

        int capHeadroom = EnemyConstants.SPIRESOWER_MAX_LIVE_SPIRES
                - spireHitTarget.liveSpireCountForOwner(enemy.getId());
        int wanted = Math.min(EnemyConstants.SPIRESOWER_SPIRES_PER_SOW, Math.max(0, capHeadroom));

        int chosen = 0;
        int remaining = candidateCount;
        while (chosen < wanted && remaining > 0) {
            int pickIndex = wiggleRandom.nextInt(remaining);
            int candidateColumn = sowCandidateColumns[pickIndex];
            int candidateRow    = sowCandidateRows[pickIndex];
            // Reject a candidate that would seal the player off from an exit, treating the tiles already
            // chosen THIS sow as solid too, so two spires never combine to trap the player.
            boolean seals = spireHitTarget.wouldSealExit(playerColumn, playerRow,
                    candidateColumn, candidateRow, enemy.sowTargetColumns, enemy.sowTargetRows, chosen);
            if (!seals) {
                enemy.sowTargetColumns[chosen] = candidateColumn;
                enemy.sowTargetRows[chosen]    = candidateRow;
                chosen++;
            }
            // Remove the picked candidate from the pool (swap with the last live entry) either way.
            remaining--;
            sowCandidateColumns[pickIndex] = sowCandidateColumns[remaining];
            sowCandidateRows[pickIndex]    = sowCandidateRows[remaining];
        }
        enemy.sowTargetCount = chosen;
        return chosen;
    }

    /** True when a tile is a legal sow target BEFORE the reachability guard: in bounds, walkable, unoccupied. */
    private boolean isLegalSowTile(int tileColumn, int tileRow, int playerColumn, int playerRow) {
        if (tileColumn < 0 || tileColumn >= level.getWidth())  return false;
        if (tileRow    < 0 || tileRow    >= level.getHeight()) return false;
        if (level.isBlockedAt(tileColumn, tileRow, doorManager)) return false; // wall/prop/door/existing spire
        if (tileColumn == playerColumn && tileRow == playerRow) return false;
        if (occupancy[tileColumn][tileRow]) return false; // an enemy stands here
        return true;
    }

    /**
     * Executes a committed sow (from {@link #executeWindUp}): stamps each pre-selected spire tile, RE-
     * validating it at execution time exactly as {@code executeSummon} does — the player or an ally may have
     * stepped onto a marked cell during their turn, and that cell simply grows nothing (the same "reposition
     * dodges it" fairness the other committed actions honour). Bounded by the per-sower live cap.
     */
    private void executeSow(Enemy enemy, int playerColumn, int playerRow) {
        enemy.state = EnemyState.ATTACKING;
        enemy.triggerTelegraph();
        if (spireHitTarget != null) {
            for (int targetIndex = 0; targetIndex < enemy.sowTargetCount; targetIndex++) {
                if (spireHitTarget.liveSpireCountForOwner(enemy.getId())
                        >= EnemyConstants.SPIRESOWER_MAX_LIVE_SPIRES) break;
                int targetColumn = enemy.sowTargetColumns[targetIndex];
                int targetRow    = enemy.sowTargetRows[targetIndex];
                if (level.isBlockedAt(targetColumn, targetRow, doorManager)) continue;
                if (targetColumn == playerColumn && targetRow == playerRow)  continue;
                if (occupancy[targetColumn][targetRow])                      continue;
                spireHitTarget.sowSpire(targetColumn, targetRow, enemy.getId(),
                        EnemyConstants.SPIRESOWER_SPIRE_LIFETIME_TURNS);
            }
        }
        enemy.sowTargetCount = 0; // batch consumed — clears the floor telegraph until the next sow
    }

    /**
     * End-of-turn REGEN for a Verdant Spiresower: it regains {@code SPIRESOWER_REGEN_PER_SPIRE} HP per
     * LIVING spire within {@code SPIRESOWER_LEY_RANGE_TILES}, capped at maxHealth, and caches the count for
     * the renderer's eye glow. Called for every acting sower — including a stun-cancelled turn — because the
     * ley-lines drain on the world's clock, not on the enemy's success. A no-op for every other archetype
     * and for a corpse. This is the "shoot the golem and it heals" half of the whole decision.
     */
    private void advanceSowerRegen(Enemy enemy) {
        if (enemy.type != EnemyType.VERDANT_SPIRESOWER) return;
        if (!enemy.isAlive()) return;
        if (spireHitTarget == null) { enemy.livingSpireCount = 0; return; }
        int living = spireHitTarget.countLivingSpiresNear(
                enemy.tileColumn, enemy.tileRow, EnemyConstants.SPIRESOWER_LEY_RANGE_TILES);
        enemy.livingSpireCount = living;
        if (living <= 0) return;
        int heal = living * EnemyConstants.SPIRESOWER_REGEN_PER_SPIRE;
        enemy.health = Math.min(enemy.maxHealth, enemy.health + heal);
    }

    /**
     * True when the enemy has a clear, unobstructed cardinal firing line within range to the player.
     * Shared validation for all ranged planners: checks range, line-of-sight, and blocking.
     */
    private boolean canFireCardinalLine(Enemy enemy, int playerColumn, int playerRow, int rangeLimit) {
        int distance = GameMath.chebyshevDistanceTiles(
                enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
        return distance <= rangeLimit
                && isSameCardinalLine(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)
                && hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)
                && !hasEnemyBlockingShot(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
    }

    /**
     * RANGED plan (Eye Tyrant, Mire Wraith, Acid Drone). One committed verb per turn:
     *   - too close and not melee-pinned  → retreat (MOVE flee); no shot this turn.
     *   - a clear cardinal firing line now → commit ATTACK_RANGED with its predicted damage.
     *   - otherwise                        → advance (MOVE) to gain a line, or WAIT if move-gated.
     *
     * Melee-pin: once the player is cardinally adjacent the kiter can no longer simply back-pedal
     * out of every swing (that would make a melee build unable to ever connect in open space). When
     * pinned it holds and commits a point-blank shot instead, so the player trades blows turn-for-turn.
     */
    private void computeRangedPlan(Enemy enemy, PlannedAction plan, int playerColumn, int playerRow) {
        int rangeLimit  = enemy.type.attackRangeTiles();
        int distance    = GameMath.chebyshevDistanceTiles(
                enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
        boolean meleePinned = isCardinallyAdjacent(enemy, playerColumn, playerRow);
        boolean canFireNow  = canFireCardinalLine(enemy, playerColumn, playerRow, rangeLimit);

        if (distance < EnemyConstants.RANGED_KITE_MIN_TILES && !meleePinned) {
            plan.setFlee(playerColumn, playerRow);       // too close — reposition, no shot this turn
        } else if (canFireNow) {
            plan.setAttack(IntentVerb.ATTACK_RANGED, playerColumn, playerRow, enemy.scaledAttackDamage());
        } else if (shouldDefend(enemy, playerColumn, playerRow)) {
            commitDefend(enemy, plan);                   // can't fire and hurting — brace instead of shuffling
        } else if (enemy.shouldMoveThisTurn()) {
            plan.set(IntentVerb.MOVE, playerColumn, playerRow); // advance to gain a firing line
        } else {
            plan.set(IntentVerb.WAIT, enemy.tileColumn, enemy.tileRow);
        }
    }

    /**
     * SENTINEL plan (Auric Sentinel, elemental-golem-auric-sentinel) — a ranged planner whose whole
     * shape is driven by the shard ring, so the enemy's AGGRESSION LEVEL is itself a readout of its
     * shard count. Preference order:
     *   - shard available + a clear cardinal firing line → commit ATTACK_RANGED (it stands and shoots).
     *   - SHARDLESS                                       → retreat toward maximum range and regrow;
     *                                                       it cannot attack at all, and it visibly
     *                                                       stops trying, so "it is disarmed right now"
     *                                                       is legible from across the room.
     *   - armed but no line                               → brace if it is hurting, else step toward
     *                                                       cardinal alignment, else wait.
     *
     * <p>It deliberately does NOT kite at close range while armed — unlike {@link #computeRangedPlan},
     * a full ring means it holds its ground and fires point-blank. Kiting is the tell for "empty".
     * Every shot obeys the mandatory cardinal-line constraint; a launched shard only ever travels a
     * true row or column, and never re-targets after commit.
     */
    private void computeSentinelPlan(Enemy enemy, PlannedAction plan, int playerColumn, int playerRow) {
        int distance = GameMath.chebyshevDistanceTiles(
                enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
        int rangeLimit = enemy.type.attackRangeTiles();

        if (!enemy.hasShard()) {
            // Fully defanged: back off to maximum range and let the ring regrow. Kiting only, never
            // fleeing the encounter — a departing enemy is a behaviour the AI has no precedent for.
            if (distance <= rangeLimit && enemy.shouldMoveThisTurn()) {
                plan.setFlee(playerColumn, playerRow);
            } else {
                plan.set(IntentVerb.WAIT, enemy.tileColumn, enemy.tileRow);
            }
            return;
        }

        boolean canFireNow = canFireCardinalLine(enemy, playerColumn, playerRow, rangeLimit);
        if (canFireNow) {
            plan.setAttack(IntentVerb.ATTACK_RANGED, playerColumn, playerRow, enemy.scaledAttackDamage());
        } else if (shouldDefend(enemy, playerColumn, playerRow)) {
            commitDefend(enemy, plan);
        } else if (enemy.shouldMoveThisTurn()) {
            plan.set(IntentVerb.MOVE, playerColumn, playerRow); // steer toward a cardinal lane
        } else {
            plan.set(IntentVerb.WAIT, enemy.tileColumn, enemy.tileRow);
        }
    }

    /**
     * LANCER plan (Rimeshell Lancer, elemental-golem-rimeshell-lancer) — a slow ranged SOLDIER whose
     * whole design is one visible trade: it is the toughest non-elite WHILE SEALED, but the ice shell
     * must OPEN for the molten lance to fire. Preference order (per the design doc):
     *   - off cooldown + a clear cardinal firing line → commit a WIND_UP charge: LOCK the lane, OPEN the
     *     shell (drop the live flat armour to 0 for the whole player turn — the punish window), telegraph.
     *     The beam itself fires a turn later down the committed lane (executeLanceBeam via executeWindUp).
     *   - otherwise (on cooldown, or no clean lane)     → behave as an ordinary slow ranged unit: brace if
     *     hurting, else step toward cardinal alignment, else wait. It NEVER kites and NEVER diagonal-fires.
     *
     * <p>The mandatory cardinal-line constraint is honoured at both ends: the charge only commits when the
     * player shares a true row or column ({@link #canFireCardinalLine}), and the beam never re-targets
     * after commit — a sidestep off the locked lane wastes the whole shot.
     */
    private void computeLancerPlan(Enemy enemy, PlannedAction plan, int playerColumn, int playerRow) {
        int rangeLimit = enemy.type.attackRangeTiles();
        if (enemy.lanceCooldownTurns <= 0
                && canFireCardinalLine(enemy, playerColumn, playerRow, rangeLimit)) {
            // LOCK the lane at commit time — the beam fires straight down this direction next turn no
            // matter where the player then stands. Signum of the (cardinal) gap is the unit lane vector.
            enemy.lanceDirectionColumn = Integer.signum(playerColumn - enemy.tileColumn);
            enemy.lanceDirectionRow    = Integer.signum(playerRow    - enemy.tileRow);
            enemy.openShell();          // THE PUNISH WINDOW opens now, for the whole of the coming player turn
            plan.setWindUp(playerColumn, playerRow, enemy.scaledAttackDamage(), 1);
            enemy.state = EnemyState.WINDING_UP;
            enemy.triggerTelegraph();   // full player turn of warning, drawn in the WIND_UP intent frame
            return;
        }
        // Sealed and out of position (or on cooldown): a slow ranged unit that steps toward alignment.
        if (shouldDefend(enemy, playerColumn, playerRow)) {
            commitDefend(enemy, plan);
        } else if (enemy.shouldMoveThisTurn()) {
            plan.set(IntentVerb.MOVE, playerColumn, playerRow); // steer toward a cardinal lane
        } else {
            plan.set(IntentVerb.WAIT, enemy.tileColumn, enemy.tileRow);
        }
    }

    // -------------------------------------------------------------------------
    // SPECIAL move-set selection (strategy-combat-order-5) — COMMIT side
    // -------------------------------------------------------------------------

    /**
     * Tries to commit a SPECIAL ability this turn. Returns true (and writes the plan) only when the
     * archetype has a move-set, this is one of its cadence turns, and at least one ability's
     * preconditions hold. The no-repeat {@link MoveHistory} then picks the least-recently-used usable
     * ability, so a multi-ability elite rotates its script rather than spamming one move.
     */
    private boolean tryCommitSpecial(Enemy enemy, PlannedAction plan,
                                     int playerColumn, int playerRow, Player player) {
        SpecialAbility[] moveSet = enemy.type.moveSet();
        int cadence = enemy.type.specialAbilityCadenceTurns();
        if (moveSet.length == 0 || cadence <= 0) return false;
        if (enemy.turnCounter % cadence != 0)    return false;

        SpecialAbility chosen = chooseSpecial(enemy, moveSet, playerColumn, playerRow);
        if (chosen == null) return false;

        commitSpecial(enemy, plan, chosen, playerColumn, playerRow);
        enemy.moveHistory.record(chosen);
        return true;
    }

    /**
     * Picks the usable ability with the FEWEST recent uses (no-repeat), breaking ties randomly so a
     * two-ability set alternates and a single-ability set is never blocked from acting. Returns null
     * when no ability's preconditions currently hold (the enemy falls back to normal behaviour).
     */
    private SpecialAbility chooseSpecial(Enemy enemy, SpecialAbility[] moveSet,
                                         int playerColumn, int playerRow) {
        SpecialAbility best = null;
        int bestRecentUses  = Integer.MAX_VALUE;
        int tieCount        = 0;
        for (int index = 0; index < moveSet.length; index++) {
            SpecialAbility candidate = moveSet[index];
            if (!isSpecialUsable(candidate, enemy, playerColumn, playerRow)) continue;
            int recentUses = enemy.moveHistory.recentUses(candidate);
            if (recentUses < bestRecentUses) {
                bestRecentUses = recentUses;
                best           = candidate;
                tieCount       = 1;
            } else if (recentUses == bestRecentUses) {
                // Reservoir tie-break: each equally-stale ability has an equal chance to be chosen.
                tieCount++;
                if (effectRandom.nextInt(tieCount) == 0) best = candidate;
            }
        }
        return best;
    }

    /** True when the given ability's preconditions currently hold for this enemy (weight() == 0 filter). */
    private boolean isSpecialUsable(SpecialAbility ability, Enemy enemy, int playerColumn, int playerRow) {
        switch (ability) {
            case BUFF_SELF:
                // No point re-buffing while EMPOWERED is still active.
                return !enemy.getActiveEffects().get(StatusType.EMPOWERED).isActive();
            case DEBUFF_PLAYER:
                // A ranged control move: needs a clear cardinal firing line, like a shot.
                return GameMath.chebyshevDistanceTiles(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)
                            <= enemy.type.attackRangeTiles()
                        && isSameCardinalLine(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)
                        && hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
            case SUMMON:
                // One cast per summoner, lifetime: once it has actually spawned at least one chaff
                // (summonsSpawned > 0, set in executeSummon), it never offers SUMMON again — killing
                // the summoner permanently stops the flood instead of just pausing it for a cooldown.
                return enemy.summonsSpawned == 0
                        && countLiveEnemies() < EnemyConstants.SUMMON_ROOM_LIVE_CAP
                        && hasEmptyAdjacentTile(enemy, playerColumn, playerRow);
            case AREA_STRIKE:
                // Only telegraph the slam when the player is close enough that it can plausibly land
                // even after they take a step — one tile of grace beyond the strike radius.
                return GameMath.chebyshevDistanceTiles(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)
                        <= EnemyConstants.AREA_STRIKE_RADIUS_TILES + 1;
            default:
                return false;
        }
    }

    /** Writes the SPECIAL plan: focus tile + predicted-damage number (AREA_STRIKE only) for order-2. */
    private void commitSpecial(Enemy enemy, PlannedAction plan, SpecialAbility ability,
                               int playerColumn, int playerRow) {
        int targetColumn = ability.targetsSelf() ? enemy.tileColumn : playerColumn;
        int targetRow    = ability.targetsSelf() ? enemy.tileRow    : playerRow;
        int predictedDamage = ability.dealsDamage() ? areaStrikeDamage(enemy) : 0;
        plan.setSpecial(ability, targetColumn, targetRow, predictedDamage);
        if (ability == SpecialAbility.SUMMON) {
            // Choose the exact spawn cells NOW so the floor telegraph is honest — the same tiles the
            // player sees marked this turn are the ones executeSummon spawns into next turn.
            selectSummonTargets(enemy, playerColumn, playerRow);
        } else {
            enemy.summonTargetCount = 0; // switching away from a summon plan clears any stale telegraph
        }
    }

    /**
     * Pre-selects a batch of 2-3 empty cardinal-neighbour tiles for a committed SUMMON and stores them
     * on the enemy so the floor telegraph (EnemyRenderer) and executeSummon agree on the exact cells.
     * The batch size is randomised in {@code [SUMMON_COUNT_MIN, SUMMON_COUNT_MAX]} but clamped by both
     * the available passable tiles and the remaining per-room live-enemy headroom, so a telegraph never
     * promises more spawns than the encounter budget allows.
     */
    private void selectSummonTargets(Enemy enemy, int playerColumn, int playerRow) {
        int requested = EnemyConstants.SUMMON_COUNT_MIN
                + effectRandom.nextInt(EnemyConstants.SUMMON_COUNT_MAX - EnemyConstants.SUMMON_COUNT_MIN + 1);
        int count = 0;
        int liveEnemies = countLiveEnemies();
        for (int directionIndex = 0; directionIndex < 4 && count < requested; directionIndex++) {
            if (liveEnemies + count >= EnemyConstants.SUMMON_ROOM_LIVE_CAP) break;
            int neighbourColumn = enemy.tileColumn + STEP_COLUMNS[directionIndex];
            int neighbourRow    = enemy.tileRow    + STEP_ROWS[directionIndex];
            if (!isPassableForEnemy(neighbourColumn, neighbourRow, playerColumn, playerRow)) continue;
            enemy.summonTargetColumns[count] = neighbourColumn;
            enemy.summonTargetRows[count]    = neighbourRow;
            count++;
        }
        enemy.summonTargetCount = count;
    }

    /** AREA_STRIKE slam damage: the enemy's scaled attack times the slam multiplier, minimum 1. */
    private static int areaStrikeDamage(Enemy enemy) {
        return Math.max(1, Math.round(
                enemy.scaledAttackDamage() * EnemyConstants.AREA_STRIKE_DAMAGE_MULTIPLIER));
    }

    /** True when at least one cardinal neighbour tile is a legal spawn tile (empty, walkable, not the player). */
    private boolean hasEmptyAdjacentTile(Enemy enemy, int playerColumn, int playerRow) {
        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            int neighbourColumn = enemy.tileColumn + STEP_COLUMNS[directionIndex];
            int neighbourRow    = enemy.tileRow    + STEP_ROWS[directionIndex];
            if (isPassableForEnemy(neighbourColumn, neighbourRow, playerColumn, playerRow)) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // DEFEND decision (strategy-combat-order-3) — shared by every archetype that can brace
    // -------------------------------------------------------------------------

    /**
     * True when this enemy should commit DEFEND this turn instead of repositioning or waiting.
     * Called from the COMMIT step AFTER the attack branches, so an enemy that can land a hit always
     * prefers attacking — bracing only ever replaces a MOVE/WAIT, never a productive attack, so it
     * never stalls a fight. Two reasons an enemy braces (both tunable in BalanceConfig):
     *   1. TURTLE  — it is at or below {@code DEFEND_HP_THRESHOLD_FRACTION} HP and the player is
     *      positioned to hurt it next turn (cardinally adjacent, or sharing its line with clear LOS).
     *   2. GUARDIAN — a BRUISER on its fixed {@code DEFEND_BRUISER_CADENCE_TURNS} rhythm, regardless
     *      of HP, to give elites the read-and-adapt beat.
     * CHAFF and BOSS roles never brace (chaff should die fast; bosses run their own AI).
     */
    private boolean shouldDefend(Enemy enemy, int playerColumn, int playerRow) {
        EnemyRole role = enemy.type.role();
        if (role == EnemyRole.CHAFF || role == EnemyRole.BOSS) return false;
        // Never brace more than a couple of turns in a row. A low-HP, cornered enemy that can neither
        // land a hit nor safely reposition would otherwise commit DEFEND every single turn and just
        // stand there shielding — which reads to the player as a broken enemy "doing nothing". After
        // the cap it is forced to re-engage (attack/move) for at least one turn (design feedback).
        if (enemy.consecutiveDefendTurns >= BalanceConfig.DEFEND_MAX_CONSECUTIVE_TURNS) return false;
        if (role == EnemyRole.BRUISER
                && enemy.turnCounter % BalanceConfig.DEFEND_BRUISER_CADENCE_TURNS == 0) {
            return true;
        }
        boolean lowHp = enemy.health <= Math.round(enemy.maxHealth * BalanceConfig.DEFEND_HP_THRESHOLD_FRACTION);
        if (!lowHp) return false;
        boolean threatened = isCardinallyAdjacent(enemy, playerColumn, playerRow)
                || (isSameCardinalLine(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)
                    && hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow));
        return threatened;
    }

    /**
     * Writes a DEFEND plan carrying the Block the enemy will gain next turn. The goal tile is the
     * enemy's own tile (it holds position while bracing). blockGain is role-based and depth-scaled;
     * order-2 renders this exact number inside the blue shield intent icon.
     */
    private void commitDefend(Enemy enemy, PlannedAction plan) {
        int blockGain = GameMath.defendBlockGain(
                defendBlockGainBase(enemy.type.role()),
                GameBalance.enemyHealthScaleForDepth(enemy.dungeonLevel),
                BalanceConfig.BLOCK_MAX);
        plan.setDefend(enemy.tileColumn, enemy.tileRow, blockGain);
    }

    /** Depth-1 base Block for a bracing role; 0 for roles that never DEFEND (CHAFF/BOSS). */
    private static int defendBlockGainBase(EnemyRole role) {
        switch (role) {
            case MINI_ELITE: return BalanceConfig.DEFEND_BLOCK_GAIN_MINI_ELITE;
            case BRUISER:    return BalanceConfig.DEFEND_BLOCK_GAIN_BRUISER;
            case SOLDIER:    return BalanceConfig.DEFEND_BLOCK_GAIN_SOLDIER;
            default:         return 0;
        }
    }

    /**
     * Fires the Block-specific feedback when a shot is eaten by an enemy's shield (order-3): a
     * "BLOCKED N" floater teaching the player the hit was absorbed, plus blue shield sparks (and a
     * shatter ring when the buffer broke) routed through the impact system. Purely cosmetic.
     */
    private void spawnBlockFeedback(Enemy enemy, int blockAbsorbed, boolean shattered) {
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor("BLOCKED " + blockAbsorbed, EventTextSystem.COLOR_BLUE);
        }
        if (impactEventListener != null) {
            impactEventListener.onBlockAbsorbed(enemy.tileColumn, enemy.tileRow,
                    enemy.type.heightMultiplier(), blockAbsorbed, shattered);
        }
    }

    /**
     * Fires the SHARD-specific feedback when an orbiting shard nullifies a hit
     * (elemental-golem-auric-sentinel): an "ABSORBED" gold floater plus gold sparks and a ring pulse.
     * The caller deliberately skips the ordinary hit flash and damage number so this reads as a
     * DIFFERENT event from a normal hit — the enemy's health did not move, and the player must see
     * that instantly. The first time it happens the player is told what to do about it. Cosmetic only.
     */
    private void spawnShardAbsorbFeedback(Enemy enemy) {
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor("ABSORBED", EventTextSystem.COLOR_GOLD);
        }
        if (impactEventListener != null) {
            impactEventListener.onShardShattered(enemy.tileColumn, enemy.tileRow,
                    enemy.type.heightMultiplier(), 1);
        }
        showCombatTipOnce("shard_absorb",
                "ABSORBED — each shard eats ONE hit. Strip them with rapid fire.");
    }

    /**
     * Fires the CRUST-specific feedback when a cooled shell breaks
     * (.claude/agents/ideas/elemental-golem-cinderforge-colossus.txt): a "CRUST SHATTERED" floater plus
     * an orange shard burst, sized by how much shell broke. Unlike a shard absorb this does NOT suppress
     * the ordinary hit flash and damage number — the hit really did land, and on a full shell it landed
     * HARDER than normal, so the player should see a big number. The floater's job is only to explain
     * why. Cosmetic only.
     *
     * @param stacksBroken how many crust stacks the hit broke at once
     * @param wasFullCrust true when the shell was fully cooled, so this hit also earned the damage bonus
     */
    private void spawnCrustShatterFeedback(Enemy enemy, int stacksBroken, boolean wasFullCrust) {
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor("CRUST SHATTERED", EventTextSystem.COLOR_GOLD);
        }
        if (impactEventListener != null) {
            impactEventListener.onCrustShattered(enemy.tileColumn, enemy.tileRow,
                    enemy.type.heightMultiplier(), stacksBroken, wasFullCrust);
        }
        showCombatTipOnce("crust_shatter",
                "IT HARDENS WHEN YOU STOP SHOOTING — keep the pressure on and it never gets to cool.");
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
     * Greedy step toward the player identical to {@link #stepToward}, except that among the equally-good
     * (minimum-distance) candidate steps it BREAKS TIES toward a tile that keeps a living spire between the
     * sower and the player — the one-line scoring tweak the Verdant Spiresower design asks for. Cover never
     * overrides getting closer; it only decides between two steps that close the gap equally. Falls back to
     * the shared wiggle when fully stuck, exactly like {@link #stepToward}.
     */
    private void stepTowardPreferringCover(Enemy enemy, int playerColumn, int playerRow) {
        int bestColumn   = enemy.tileColumn;
        int bestRow      = enemy.tileRow;
        int bestDistance = Integer.MAX_VALUE;
        boolean bestHasCover = false;
        boolean moved        = false;

        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            int targetColumn = enemy.tileColumn + STEP_COLUMNS[directionIndex];
            int targetRow    = enemy.tileRow    + STEP_ROWS[directionIndex];
            if (!isPassableForEnemy(targetColumn, targetRow, playerColumn, playerRow)) continue;
            int distance = GameMath.manhattanDistanceTiles(targetColumn, targetRow, playerColumn, playerRow);
            boolean hasCover = spireBetween(targetColumn, targetRow, playerColumn, playerRow);
            boolean better = distance < bestDistance
                    || (distance == bestDistance && hasCover && !bestHasCover);
            if (better) {
                bestDistance = distance;
                bestColumn   = targetColumn;
                bestRow      = targetRow;
                bestHasCover = hasCover;
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
            enemy.stuckTurns = (bestDistance >= previousManhattan) ? enemy.stuckTurns + 1 : 0;
            commitMove(enemy, bestColumn, bestRow);
        } else {
            enemy.stuckTurns++;
        }
    }

    /**
     * True when a living spire sits on the straight cardinal segment between a tile and the player — i.e.
     * the tile would put a spire "in the way" as cover. Only meaningful when the tile shares the player's
     * row or column; a diagonal offset has no single covering line, so it returns false. No-op (false)
     * without a spire seam.
     */
    private boolean spireBetween(int fromColumn, int fromRow, int toColumn, int toRow) {
        if (spireHitTarget == null) return false;
        if (fromColumn == toColumn) {
            int step = Integer.signum(toRow - fromRow);
            for (int row = fromRow + step; row != toRow; row += step) {
                if (spireHitTarget.isSpireAt(fromColumn, row)) return true;
            }
        } else if (fromRow == toRow) {
            int step = Integer.signum(toColumn - fromColumn);
            for (int column = fromColumn + step; column != toColumn; column += step) {
                if (spireHitTarget.isSpireAt(column, fromRow)) return true;
            }
        }
        return false;
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
        // Track implicit facing = the cardinal step just taken (order-6 backstab/flank). A one-tile
        // enemy step is always a single cardinal, so signum yields a clean unit facing vector.
        int deltaColumn = targetColumn - enemy.tileColumn;
        int deltaRow    = targetRow    - enemy.tileRow;
        if (deltaColumn != 0 || deltaRow != 0) {
            enemy.facingColumn = Integer.signum(deltaColumn);
            enemy.facingRow    = Integer.signum(deltaRow);
        }
        enemy.tileColumn = targetColumn;
        enemy.tileRow    = targetRow;
        occupancy[targetColumn][targetRow] = true;
    }

    /**
     * Points the enemy's implicit facing (order-6) at a target tile along the dominant cardinal axis.
     * Called when an enemy attacks the player so a marine that circles behind an attacking enemy still
     * earns the backstab bonus. No-op when the target is on the enemy's own tile.
     */
    private static void faceEnemyToward(Enemy enemy, int targetColumn, int targetRow) {
        int deltaColumn = targetColumn - enemy.tileColumn;
        int deltaRow    = targetRow    - enemy.tileRow;
        if (deltaColumn == 0 && deltaRow == 0) return;
        if (Math.abs(deltaColumn) >= Math.abs(deltaRow)) {
            enemy.facingColumn = Integer.signum(deltaColumn);
            enemy.facingRow    = 0;
        } else {
            enemy.facingColumn = 0;
            enemy.facingRow    = Integer.signum(deltaRow);
        }
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
                            || Level.isColumn(cell)
                            || Level.isSpire(cell);   // spires are opaque — they cost ranged allies their lanes too
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
        int xpAwarded = depthScaledXpReward(enemy.type);
        if (killXpListener    != null) killXpListener.onEnemyKilledForXp(xpAwarded);
        if (killEventListener != null) killEventListener.onEnemyKilled(enemy.nameTag, xpAwarded);
        if (killCreditListener != null) killCreditListener.onEnemyKilledForCredits(depthScaledCreditReward(enemy.type), currentDepth);
        // Fire the impact death burst BEFORE killEnemy() clears the tile, so a burn/poison
        // DoT kill gets the same explosion + flash + shake as a weapon kill instead of the
        // enemy silently vanishing at the end of the turn.
        if (impactEventListener != null) {
            impactEventListener.onEnemyKilled(enemy.worldCenterX(), enemy.worldCenterY(),
                    enemy.type.heightMultiplier(), 0);
        }
        killEnemy(enemy, false);
    }

    private void killEnemy(Enemy enemy, boolean isMeleeKill) {
        occupancy[enemy.tileColumn][enemy.tileRow] = false;
        // COLLAPSE (elemental-golem-auric-sentinel): any shards still in orbit fall out of the ring and
        // shatter on the floor with the body. Fired before the tile is cleared so the burst plays where
        // the enemy actually stood. No-op for every archetype without shards, and for an empty ring.
        if (enemy.shardCount > 0 && impactEventListener != null) {
            impactEventListener.onShardShattered(enemy.tileColumn, enemy.tileRow,
                    enemy.type.heightMultiplier(), enemy.shardCount);
        }
        enemy.shardCount = 0;
        // COLLAPSE (elemental-golem-cinderforge-colossus): a magma construct goes out as embers rather
        // than as the generic death burst, so a Colossus dying reads as a furnace being extinguished.
        // The last fire tile it leaves under itself is placed by the death HAZARD hook (World), which is
        // where every other area-denial death effect already lives.
        if (enemy.type.leavesTrailFireTurns() > 0 && impactEventListener != null) {
            impactEventListener.onEmberCollapse(enemy.tileColumn, enemy.tileRow,
                    enemy.type.heightMultiplier());
        }
        // SHATTER (elemental-golem-rimeshell-lancer): a Lancer goes out in cold blue-white shards with one
        // hot orange core flash as the furnace dies — the ice/fire contrast that is its whole identity,
        // distinct from the generic death burst. Keyed on baseArmor() so it stays data-driven (today only
        // the Lancer has a shell); a no-op for every other archetype.
        if (enemy.type.baseArmor() > 0 && impactEventListener != null) {
            impactEventListener.onFrostShatter(enemy.tileColumn, enemy.tileRow,
                    enemy.type.heightMultiplier());
        }
        // WITHERING (elemental-golem-verdant-spiresower): a dying Spiresower's spires all shatter and their
        // tiles are restored, so the room visibly returns to normal — its own reward, and it guarantees no
        // solid tile is ever leaked by the sower's death. A no-op for every other archetype (owner id has
        // no spires). Fired before the tile is cleared / the enemy is removed, so the shatter bursts play
        // where the spires actually stood.
        if (enemy.type == EnemyType.VERDANT_SPIRESOWER && spireHitTarget != null) {
            spireHitTarget.shatterSpiresOf(enemy.getId());
        }
        char currentCell = level.getCell(enemy.tileColumn, enemy.tileRow);
        if (!Level.isStairsDown(currentCell)
                && !Level.isMedicalPickup(currentCell)
                && !Level.isArmourPickup(currentCell)
                && !Level.isKeycardPickup(currentCell)
                && !Level.isAmmoPickup(currentCell)) {
            // ORDER 6 ammo lifeline: a summoned add killed while the player is out of ammo in the
            // sealed boss arena ALWAYS drops matching ammo, replacing the normal roll (Fairness F5).
            char guaranteedAmmo = guaranteedSummonerAmmoDrop(enemy);
            char drop;
            if (guaranteedAmmo != 0) {
                drop = guaranteedAmmo;
            } else {
                // NEVER-SOFTLOCK (new-game-balancr order 3, part D): if the player's remaining potential
                // damage has fallen catastrophically below the remaining floor demand, force this drop to
                // be ammo for an equipped weapon (once per floor). Keeps "wasted all my ammo" deaths as
                // fighting retreats, never empty-inventory softlocks. Evaluated BEFORE the normal roll.
                char emergencyAmmo = emergencySupplyDrop(enemy);
                if (emergencyAmmo != 0) {
                    drop = emergencyAmmo;
                    emergencySupplyGrantedThisFloor = true;
                    if (emergencySupplyListener != null) emergencySupplyListener.onEmergencySupplyGranted();
                } else {
                    drop = rollEnemyDrop(enemy.type, isMeleeKill);
                }
            }
            level.setCell(enemy.tileColumn, enemy.tileRow, drop);
            if (dropPlacedListener != null) {
                dropPlacedListener.onDropPlaced(enemy.tileColumn, enemy.tileRow, drop);
            }
        }
        // Area-denial death hook (Pillar 2/3): e.g. a Plague Hulk leaves a toxic cloud here.
        if (enemyDeathHazardListener != null) {
            enemyDeathHazardListener.onEnemyDied(enemy.type, enemy.tileColumn, enemy.tileRow, enemy.selfDestructed);
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
            case AURIC_SENTINEL:   return '8'; // cells  — crystal golem, energy ranged
            case CINDERFORGE_COLOSSUS: return '7'; // shells — heavy melee golem anchor
            case RIMESHELL_LANCER: return '8'; // cells  — crystal golem, molten energy lance
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

    /**
     * ORDER 6 ammo lifeline (boss-fight-mobile-overseer): returns the ammo pickup char a summoned add
     * must ALWAYS drop, or 0 when the guarantee does not apply (normal loot rules stand). The guarantee
     * fires only when ALL of these hold, so it is purely an empty-safety net — never an ammo piñata:
     *   • the dead enemy was spawned by the boss's SUMMON tactic ({@code spawnedByBossSummon}),
     *   • a boss fight is still active (the arena is sealed — the only truly unwinnable case), and
     *   • the player has no usable ammo across every equipped ranged weapon.
     * The chosen char matches a weapon the player actually carries so the drop is never dead weight.
     */
    private char guaranteedSummonerAmmoDrop(Enemy enemy) {
        if (!enemy.spawnedByBossSummon) return 0;
        if (loadout == null) return 0;
        if (!isBossFightActive()) return 0;
        if (!loadout.hasNoUsableAmmo()) return 0;
        return preferredAmmoCharForEmptyLoadout();
    }

    /**
     * NEVER-SOFTLOCK emergency ammo lifeline (new-game-balancr order 3, part D). Returns the ammo pickup
     * char this kill must drop to keep the floor crossable, or 0 when the guarantee does not apply
     * (normal loot rules stand). Fires only when ALL hold, so it is a catastrophe floor far below the
     * scarcity band — never an ammo piñata for a hoarder:
     *   • it has NOT already fired this floor (once-per-floor cap; the EnemyManager is rebuilt per floor),
     *   • the player carries at least one equipped ranged weapon (melee-only never triggers — fist is the
     *     true backstop), and
     *   • the player's TOTAL remaining potential damage (all reserves * per-shot damage + one melee swing)
     *     has fallen below the remaining floor demand * EMERGENCY_SUPPLY_FRACTION (GameMath predicate).
     * The chosen char matches a weapon the player actually carries so the drop is never dead weight. The
     * enemy being killed is EXCLUDED from the remaining demand (it is already dead for this purpose).
     */
    private char emergencySupplyDrop(Enemy dyingEnemy) {
        if (emergencySupplyGrantedThisFloor) return 0;
        if (loadout == null) return 0;
        char ammoChar = preferredAmmoCharForEmptyLoadout();
        if (ammoChar == 0) return 0; // melee-only loadout — melee is the backstop, no lifeline needed
        float totalPotentialDamage = playerPotentialDamage();
        float remainingFloorDemand = remainingFloorDemand(dyingEnemy);
        if (!GameMath.emergencySupplyTriggers(totalPotentialDamage, remainingFloorDemand,
                BalanceConfig.EMERGENCY_SUPPLY_FRACTION, false)) {
            return 0;
        }
        return ammoChar;
    }

    /**
     * The player's total remaining potential ranged damage: for every equipped ranged weapon, its
     * reserve ammo times its per-shot damage, plus one melee swing (the always-available backstop). A
     * conservative UPPER bound — weapons sharing an ammo type double-count it, so the lifeline fires
     * LESS often, never more (a hoarder is never fed).
     */
    private float playerPotentialDamage() {
        float total = BalanceConfig.MELEE_FIST_DAMAGE; // one always-available melee swing
        if (loadout == null) return total;
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            Weapon weapon = loadout.getSlot(slotIndex);
            if (weapon == null || weapon instanceof MeleeWeapon) continue;
            total += (float) weapon.getReserveAmmo() * weapon.getEffectiveDamage();
        }
        return total;
    }

    /** Remaining floor demand = current HP left across all alive enemies, minus the one being killed. */
    private float remainingFloorDemand(Enemy dyingEnemy) {
        float demand = 0f;
        for (int index = 0; index < enemies.size(); index++) {
            Enemy candidate = enemies.get(index);
            if (candidate == dyingEnemy) continue;
            if (candidate.isAlive()) demand += candidate.health;
        }
        return demand;
    }

    /** True while a live boss is present in the roster — the arena is still sealed. */
    private boolean isBossFightActive() {
        for (int index = 0; index < enemies.size(); index++) {
            Enemy candidate = enemies.get(index);
            if (candidate instanceof Boss && candidate.isAlive()) return true;
        }
        return false;
    }

    /**
     * Picks the ammo pickup char for the guaranteed lifeline drop: the active weapon's ammo type when
     * it is a ranged weapon that uses ammo, otherwise the first equipped ranged weapon's type. Mirrors
     * the melee-drop matching intent (drop what the player can actually load). Returns 0 only for a
     * melee-only loadout, which never triggers the guarantee.
     */
    private char preferredAmmoCharForEmptyLoadout() {
        char activeChar = ammoPickupCharForWeapon(loadout.active());
        if (activeChar != 0) return activeChar;
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            char candidate = ammoPickupCharForWeapon(loadout.getSlot(slotIndex));
            if (candidate != 0) return candidate;
        }
        return 0;
    }

    /** Ammo pickup char for a ranged weapon that draws from a reserve; 0 for null/melee/infinite-ammo weapons. */
    private static char ammoPickupCharForWeapon(Weapon weapon) {
        if (weapon == null || weapon instanceof MeleeWeapon) return 0;
        AmmoType ammoType = weapon.getAmmoType();
        return ammoType != null ? ammoType.getPickupTileChar() : 0;
    }
}
