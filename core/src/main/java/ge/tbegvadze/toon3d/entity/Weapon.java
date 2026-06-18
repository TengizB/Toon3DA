package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.entity.AbilityResolver;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.render.EventTextSystem;
import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * Abstract base for every weapon in the game.
 *
 * Stat fields (final, set per weapon type):
 *   damage                — base damage at point-blank (distance 0/1)
 *   clipSize              — shots before a forced reload
 *   reloadTime            — game TICKS required to reload (move or fire; rotations excluded)
 *   damageDropCoefficient — fraction of damage removed per tile of distance (linear)
 *   range                 — maximum tiles a shot travels before dying
 *
 * Runtime state fields:
 *   shotsInClip           — remaining shots this clip (starts at clipSize)
 *   ticksRemaining        — reload countdown in movement ticks
 *   visualState           — drives HUD texture selection each frame
 *   fireFlashTimerSeconds — real-time countdown for the muzzle-flash pose
 *
 * Turn vs real-time separation — the heart of the system:
 *   update(deltaTime)  — called every frame; advances only real-time state (flash timer).
 *   onTick()           — called once per game tick (move or fire); advances reload counter.
 *   These two must never decrement each other's counters.
 *
 * Ammo economy:
 *   ammoType      — declared in each subclass constructor; determines which inventory stack
 *                   the weapon draws from on reload. null = infinite ammo (test/fallback).
 *   ammoInventory — injected by World via setAmmoInventory(); null = infinite ammo fallback.
 *   When both are non-null, onTick() calls ammoInventory.spend(ammoType.getItemType(), clipSize)
 *   when a reload completes. If the stack is empty the clip stays at 0 and canFire() returns false.
 */
public abstract class Weapon implements WeaponProfile {

    protected final String displayName;
    protected final int    damage;
    protected final int    clipSize;
    protected final int    reloadTime;
    protected final float  damageDropCoefficient;
    protected final int    range;

    protected int               shotsInClip;
    protected int               ticksRemaining;
    protected WeaponVisualState visualState                = WeaponVisualState.NORMAL;
    protected float             fireFlashTimerSeconds      = 0f;
    protected float             normalToReloadTimerSeconds = 0f;
    // Incremented on every actual shot (including burst bullets) so the HUD renderer can
    // detect each new flash cycle and reset its animation timer independently.
    protected int               flashCycleCount            = 0;

    // Declared by each subclass constructor; null = infinite ammo (test fallback only).
    private final AmmoType ammoType;
    // Injected by World after construction; null until wired.
    private Inventory ammoInventory = null;

    private EventTextSystem eventTextSystem;

    // ── Weapon level / tier / ability fields ─────────────────────────────────
    private int              weaponLevel  = 1;
    private WeaponTier       tier         = WeaponTier.COMMON;
    private float            baseAccuracy = 1.0f;
    private AbilityInstance[] abilities   = new AbilityInstance[0];

    // ── AbilityResolver wiring ───────────────────────────────────────────────
    /**
     * Injected by World after construction via {@link #setAbilityResolver}.
     * Null until World provides a resolver; all resolver calls are guarded with null-checks
     * so weapons remain functional before injection (unit tests, start room, etc.).
     */
    private AbilityResolver abilityResolver = null;

    /**
     * Number of extra burst shots still pending for this fire activation.
     * Set to (burstCount - 1) by AbilityResolver.onFire() for BURST_FIRE weapons before
     * the base shot fires; decremented by fire() until zero.
     * Zero at all times for non-BURST_FIRE weapons.
     */
    private int pendingBurstExtra = 0;

    /**
     * The enemy Object returned by EnemyHitTarget.enemyAt() for the most recent hit
     * confirmed by marchShot().  Cleared to null at the start of each fire() invocation.
     * Subclasses set this via {@link #setLastHitEnemy} inside marchShot() when they
     * confirm an enemy hit.
     *
     * Stored as Object (not Enemy) to avoid importing enemy.Enemy in this class, which
     * would create a circular package dependency (entity → enemy → entity via EnemyHitTarget).
     */
    private Object lastHitEnemy = null;

    /**
     * The raw damage amount passed to EnemyHitTarget.applyDamageTo() for the most recent
     * confirmed enemy hit.  Cleared to zero at the start of each fire() invocation.
     * Subclasses set this via {@link #setLastHitEnemy} alongside the enemy reference.
     */
    private int lastHitDamage = 0;

    // Cached effective stats — recomputed by recomputeEffectiveStats().
    private int   effectiveDamage;
    private float effectiveAccuracy;
    private int   effectiveClipSize;
    private int   effectiveReloadTicks;
    private int   effectiveRange;

    /**
     * Ranged damage multiplier sourced from the player's MARKSMANSHIP stat.
     * Defaults to 1.0 (no bonus) until World injects a non-trivial value via
     * {@link #setRangedDamageMultiplier}.  Updated whenever PlayerStats changes
     * (perk award, equipment change) — never queried per frame, only at fire time.
     */
    private float rangedDamageMultiplier = 1.0f;

    /**
     * Player accuracy multiplier sourced from the player's accuracy stat.
     * Defaults to 1.0 (no modification) until World injects a value via
     * {@link #setPlayerAccuracyMultiplier}.
     */
    private float playerAccuracyMultiplier = 1.0f;

    /**
     * Transient per-fire-activation damage multiplier set by AbilityResolver.onFire().
     * Reset to 1.0f at the start of every fire() call so stale values never bleed forward.
     * Used by SECOND_WIND (passive HP-threshold boost) and ADRENAL_SURGE (kill-proc buff).
     * Applied inside damageAtDistance() so all weapon subclasses pick it up without changes.
     */
    private float fireCycleMultiplier = 1.0f;

    // Facing direction stored at fire() entry; forwarded to onHit() for melee AoE abilities.
    private int lastFacingStepColumn = 0;
    private int lastFacingStepRow    = 0;

    // Per-weapon Rhythm / Heat-Up ramp state — consecutive-hit counter on same target.
    private int rhythmStacks   = 0;
    private int rhythmTargetId = -1;

    // Whether the last target reported via setLastHitEnemy() was at full HP before the hit.
    private boolean lastTargetWasFullHp = false;

    // Player tile position stored at fire() entry for distance-based ability checks.
    private int lastPlayerTileColumn = 0;
    private int lastPlayerTileRow    = 0;

    // Berserker's Oath kill-streak state — per-weapon, persists across levels.
    private int     berserkerStacks         = 0;
    private boolean berserkerKilledThisFire = false;

    // Per-weapon RNG for hit-chance rolls — not seeded from run seed, which is acceptable
    // because miss results are cosmetic feedback and do not affect long-term balance.
    private final java.util.Random random = new java.util.Random();

    protected Weapon(String displayName, int damage, int clipSize, int reloadTime,
                     float damageDropCoefficient, int range, AmmoType ammoType) {
        this.displayName           = displayName;
        this.damage                = damage;
        this.clipSize              = clipSize;
        this.reloadTime            = reloadTime;
        this.damageDropCoefficient = damageDropCoefficient;
        this.range                 = range;
        this.ammoType              = ammoType;
        this.shotsInClip           = clipSize;
        // Subclass constructors call setBaseAccuracy() after super(); recomputeEffectiveStats()
        // is called there so effective stats are valid before any call to fire(). When no
        // subclass calls setBaseAccuracy() (e.g. in tests), this ensures non-zero defaults.
        recomputeEffectiveStats();
    }

    /** Sets the level-1 accuracy for this weapon type. Call from subclass constructors. */
    protected void setBaseAccuracy(float accuracy) {
        this.baseAccuracy = accuracy;
        recomputeEffectiveStats();
    }

    /**
     * Applies a rolled weapon profile (level, tier, abilities) and resets the clip.
     * Called once at spawn time by WeaponRoller. Must not be called per frame.
     */
    public void configureRoll(int level, WeaponTier weaponTier, AbilityInstance[] weaponAbilities) {
        this.weaponLevel = level;
        this.tier        = weaponTier;
        this.abilities   = weaponAbilities;
        recomputeEffectiveStats();
        this.shotsInClip = effectiveClipSize;
    }

    /** Increments weapon level by 1 (Soulforge ability use only). No-op at max level. */
    void incrementWeaponLevel() {
        if (weaponLevel < WeaponConstants.MAX_WEAPON_LEVEL) {
            weaponLevel++;
            recomputeEffectiveStats();
        }
    }

    private void recomputeEffectiveStats() {
        effectiveDamage      = GameMath.weaponScaledDamage(damage, weaponLevel);
        effectiveAccuracy    = GameMath.weaponScaledAccuracy(baseAccuracy, weaponLevel);
        effectiveClipSize    = GameMath.weaponScaledClipSize(clipSize, weaponLevel);
        effectiveReloadTicks = GameMath.weaponScaledReloadTicks(reloadTime, weaponLevel);
        effectiveRange       = GameMath.weaponScaledRange(range, weaponLevel, isMelee());
        if (hasAbility(WeaponAbility.EXTENDED_MAG)) {
            int bonus = abilityCount(WeaponAbility.EXTENDED_MAG);
            effectiveClipSize = Math.min(effectiveClipSize + bonus, WeaponConstants.WEAPON_CLIP_HARD_CAP);
        }
        if (!isMelee() && hasAbility(WeaponAbility.QUICK_HANDS)) {
            effectiveReloadTicks = Math.max(WeaponConstants.WEAPON_RELOAD_MIN_TICKS,
                                            effectiveReloadTicks - 1);
        }
    }

    // ── WeaponProfile — identity ──────────────────────────────────────────────
    @Override public WeaponTier getTier()        { return tier; }
    @Override public int        getWeaponLevel() { return weaponLevel; }

    // ── WeaponProfile — effective stats ──────────────────────────────────────
    @Override public int   getEffectiveDamage()      { return effectiveDamage; }
    @Override public float getEffectiveAccuracy()    { return effectiveAccuracy; }
    @Override public int   getEffectiveClipSize()    { return effectiveClipSize; }
    @Override public int   getEffectiveReloadTicks() { return effectiveReloadTicks; }
    @Override public int   getEffectiveRange()       { return effectiveRange; }

    // ── WeaponProfile — abilities ─────────────────────────────────────────────
    @Override public int             getAbilityCount()              { return abilities.length; }
    @Override public AbilityInstance getAbility(int index)          { return abilities[index]; }
    @Override public boolean         hasAbility(WeaponAbility ability) {
        for (AbilityInstance instance : abilities) {
            if (instance.ability == ability) return true;
        }
        return false;
    }
    @Override public float abilityMagnitude(WeaponAbility ability) {
        for (AbilityInstance instance : abilities) {
            if (instance.ability == ability) return instance.magnitude;
        }
        return 0f;
    }
    @Override public int abilityCount(WeaponAbility ability) {
        for (AbilityInstance instance : abilities) {
            if (instance.ability == ability) return instance.countValue;
        }
        return 0;
    }

    /**
     * Injects the AbilityResolver so this weapon can trigger ability effects on fire and hit.
     * Called once by World after EnemyManager and EventTextSystem are both ready.
     * Safe to call with null (disables ability resolution without changing game logic).
     */
    public void setAbilityResolver(AbilityResolver resolver) {
        this.abilityResolver = resolver;
    }

    /**
     * Sets the number of extra burst shots to fire after the base shot completes.
     * Called by AbilityResolver.onFire() when BURST_FIRE is active.
     * A value of 0 means no extra shots (normal single-shot behaviour).
     */
    public void setPendingBurstExtra(int count) {
        this.pendingBurstExtra = Math.max(0, count);
    }

    /**
     * Reports the enemy hit and damage dealt by the most recent marchShot() pass.
     * Subclasses must call this inside marchShot() immediately before each
     * EnemyHitTarget.applyDamageTo() call to keep fire() informed.
     *
     * The {@code wasFullHp} flag must be checked BEFORE calling
     * EnemyHitTarget.applyDamageTo() so OPENING_SALVO has accurate pre-damage state.
     *
     * @param hitEnemyObject  the Object returned by EnemyHitTarget.enemyAt()
     * @param damageApplied   the amount passed to EnemyHitTarget.applyDamageTo()
     * @param wasFullHp       true if the target was at full HP before this hit was applied
     */
    protected void setLastHitEnemy(Object hitEnemyObject, int damageApplied, boolean wasFullHp) {
        this.lastHitEnemy        = hitEnemyObject;
        this.lastHitDamage       = damageApplied;
        this.lastTargetWasFullHp = wasFullHp;
    }

    /**
     * Convenience overload that always clears the full-HP flag.
     * Used when the caller does not need OPENING_SALVO tracking
     * (e.g. clearing after a per-hit dispatch in penetrating weapons).
     *
     * @param hitEnemyObject  the Object returned by EnemyHitTarget.enemyAt()
     * @param damageApplied   the amount passed to EnemyHitTarget.applyDamageTo()
     */
    protected void setLastHitEnemy(Object hitEnemyObject, int damageApplied) {
        setLastHitEnemy(hitEnemyObject, damageApplied, false);
    }

    public void setEventTextSystem(EventTextSystem system) {
        this.eventTextSystem = system;
    }

    /** Injects the shared item inventory so this weapon can spend ammo on reload. */
    public void setAmmoInventory(Inventory inventory) {
        this.ammoInventory = inventory;
    }

    /** Lets subclasses emit event text without exposing the private eventTextSystem field. */
    protected void spawnEventText(String text) {
        if (eventTextSystem != null) {
            eventTextSystem.spawn(text);
        }
    }

    /** Spawns a grey "MISS" event text. Used by subclasses for per-pellet miss feedback. */
    protected void spawnMissText() {
        if (eventTextSystem != null) {
            eventTextSystem.showMiss();
        }
    }

    /**
     * Sets the fire-cycle damage multiplier for the current fire activation.
     * Called by AbilityResolver.onFire() for SECOND_WIND and ADRENAL_SURGE.
     * The value is applied inside {@link #damageAtDistance} and reset to 1.0f at the
     * start of the next {@link #fire} call, so it cannot bleed across activations.
     *
     * @param multiplier the combined multiplier, e.g. 1.30f for +30% damage this activation
     */
    public void setFireCycleMultiplier(float multiplier) {
        this.fireCycleMultiplier = multiplier;
    }

    /**
     * Returns the current fire-cycle damage multiplier.
     * 1.0f when no sustain ability is active this activation.
     */
    public float getFireCycleMultiplier() {
        return fireCycleMultiplier;
    }

    /** Resets fireCycleMultiplier to 1.0f. Called by MeleeWeapon.fire() at activation start. */
    protected void resetFireCycleMultiplier() {
        this.fireCycleMultiplier = 1.0f;
    }

    /** Clears the stale hit record so a miss never fires ability callbacks on a previous target. */
    protected void clearLastHit() {
        this.lastHitEnemy        = null;
        this.lastHitDamage       = 0;
        this.lastTargetWasFullHp = false;
    }

    /** Stores the facing direction for this fire activation; forwarded to onHit() for melee AoE. */
    protected void setFacingStep(int facingStepColumn, int facingStepRow) {
        this.lastFacingStepColumn = facingStepColumn;
        this.lastFacingStepRow    = facingStepRow;
    }

    /** Triggers AbilityResolver.onFire() for this weapon. No-op when resolver is null. */
    protected void invokeAbilityOnFire() {
        if (abilityResolver != null) {
            abilityResolver.onFire(this);
        }
    }

    // ── Rhythm / Heat-Up accessors ────────────────────────────────────────────

    /**
     * Resets the Rhythm consecutive-hit streak to zero and clears the tracked target.
     * Call on miss, weapon switch, or when the current Rhythm target dies.
     */
    public void resetRhythm() {
        rhythmStacks   = 0;
        rhythmTargetId = -1;
    }

    /**
     * Updates the Rhythm streak for a hit against the given enemy ID.
     * If the ID matches the current streak target the stack count increments (capped at
     * {@link GameBalance#RHYTHM_MAX_STACKS}). If the ID differs the streak resets to 1
     * and the new enemy becomes the tracked target.
     */
    public void updateRhythm(int enemyId) {
        if (enemyId == rhythmTargetId) {
            rhythmStacks = Math.min(rhythmStacks + 1, GameBalance.RHYTHM_MAX_STACKS);
        } else {
            rhythmStacks   = 1;
            rhythmTargetId = enemyId;
        }
    }

    /** Returns the current consecutive-hit stack count for the Rhythm ability. */
    public int getRhythmStacks() { return rhythmStacks; }

    /**
     * Resets the Rhythm streak if the killed enemy was the current streak target.
     * Called from AbilityResolver.onKill() so the next target always starts at stack 1.
     */
    public void resetRhythmIfTarget(int enemyId) {
        if (enemyId == rhythmTargetId) {
            resetRhythm();
        }
    }

    // ── Berserker's Oath accessors ────────────────────────────────────────────
    public int     getBerserkerStacks()         { return berserkerStacks; }
    public boolean wasBerserkerKilledThisFire() { return berserkerKilledThisFire; }
    public void    setBerserkerKilledThisFire(boolean value) { berserkerKilledThisFire = value; }
    public void    resetBerserkerStacks()       { berserkerStacks = 0; }

    public void incrementBerserkerStacks() {
        berserkerStacks = Math.min(berserkerStacks + 1, GameBalance.BERSERKER_MAX_STACKS);
    }

    /**
     * Sets the ranged damage multiplier derived from the player's MARKSMANSHIP stat.
     * Called by World whenever PlayerStats change (run start, perk award, equipment swap).
     * Must not be called per frame — only on discrete stat-change events.
     *
     * @param multiplier value from {@code PlayerStats.getRangedDamageMultiplier()};
     *                   pass 1.0f to disable the bonus (default).
     */
    public void setRangedDamageMultiplier(float multiplier) {
        this.rangedDamageMultiplier = multiplier;
    }

    /**
     * Returns the currently stored ranged damage multiplier.
     * Subclasses may read this in custom {@code marchShot} implementations.
     */
    protected float getRangedDamageMultiplier() {
        return rangedDamageMultiplier;
    }

    /**
     * Sets the player accuracy multiplier derived from the player's accuracy stat.
     * Called by World whenever PlayerStats change (run start, perk award, equipment swap).
     * Must not be called per frame — only on discrete stat-change events.
     *
     * @param multiplier value from {@code PlayerStats.getAccuracyMultiplier()};
     *                   pass 1.0f to disable any bonus or penalty (default).
     */
    public void setPlayerAccuracyMultiplier(float multiplier) {
        this.playerAccuracyMultiplier = multiplier;
    }

    /**
     * Rolls the hit chance for a single shot using the weapon's effective accuracy and the
     * player's accuracy multiplier. Returns true if the shot hits.
     * Melee weapons (baseAccuracy = 1.0) always short-circuit to true via GameMath.
     * Protected so per-pellet weapons (Chaingun) can call it from their own fire logic.
     */
    protected boolean rollHitChance() {
        return GameMath.resolveHitChance(getEffectiveAccuracy(), playerAccuracyMultiplier, random);
    }

    /**
     * Returns true for weapons that roll accuracy independently per pellet/bullet rather than
     * once for the whole activation. Defaults to false (single roll per fire() call).
     * Override in Chaingun (burst fire: each bullet rolls independently).
     */
    protected boolean isPerPelletAccuracy() {
        return false;
    }

    /** True only when the weapon can accept a fire command right now. */
    public boolean canFire() {
        return visualState == WeaponVisualState.NORMAL && shotsInClip > 0;
    }

    /**
     * Manually starts a reload when the weapon is idle, the clip is not full, and the reserve
     * (if tracked) is not empty. Returns true if a reload was started.
     */
    public boolean requestManualReload() {
        if (visualState != WeaponVisualState.NORMAL) return false;
        if (shotsInClip >= clipSize) return false;
        if (ammoInventory != null && ammoType != null
                && ammoInventory.countOf(ammoType.getItemType()) == 0) return false;
        visualState    = WeaponVisualState.RELOADING;
        ticksRemaining = reloadTime;
        if (eventTextSystem != null) eventTextSystem.spawn("Reloading...");
        return true;
    }

    /**
     * Advances real-time state each frame (muzzle-flash and normal-hold timers).
     * FIRING → NORMAL (clip still has shots) or NORMAL-hold (clip empty).
     * After NORMAL-hold elapses → RELOADING.
     */
    public void update(float deltaTime) {
        if (visualState == WeaponVisualState.FIRING) {
            fireFlashTimerSeconds -= deltaTime;
            if (fireFlashTimerSeconds <= 0f) {
                visualState = WeaponVisualState.NORMAL;
                if (shotsInClip == 0) {
                    normalToReloadTimerSeconds = WeaponConstants.NORMAL_TO_RELOAD_DELAY_SECONDS;
                }
            }
        } else if (visualState == WeaponVisualState.NORMAL && normalToReloadTimerSeconds > 0f) {
            normalToReloadTimerSeconds -= deltaTime;
            if (normalToReloadTimerSeconds <= 0f) {
                normalToReloadTimerSeconds = 0f;
                visualState                = WeaponVisualState.RELOADING;
                ticksRemaining             = reloadTime;
                if (eventTextSystem != null) eventTextSystem.spawn("Reloading...");
            }
        }
    }

    /**
     * Called once per game tick (any tick-causing action: tile step OR weapon fire).
     * Rotations and door interactions do NOT trigger this.
     * Decrements the reload counter; when reload completes, spends rounds from the ammo
     * inventory (if wired). If the inventory stack is empty the clip stays at 0 and
     * canFire() will return false until the player picks up matching ammo.
     */
    public void onTick() {
        if (visualState == WeaponVisualState.RELOADING) {
            ticksRemaining--;
            if (ticksRemaining <= 0) {
                if (ammoInventory != null && ammoType != null) {
                    int bulletsNeeded = getEffectiveClipSize() - shotsInClip;
                    int roundsLoaded  = ammoInventory.spend(ammoType.getItemType(), bulletsNeeded);
                    shotsInClip += roundsLoaded;
                    if (shotsInClip == 0) {
                        if (eventTextSystem != null) eventTextSystem.spawn("OUT OF AMMO!");
                    } else {
                        if (eventTextSystem != null) eventTextSystem.spawn("Ready!");
                    }
                } else {
                    // ammoInventory not yet injected — infinite ammo fallback for tests.
                    shotsInClip = clipSize;
                    if (eventTextSystem != null) eventTextSystem.spawn("Ready!");
                }
                // Notify resolver so ON_RELOAD abilities (BULWARK_ROUNDS) can fire.
                // Only triggers when rounds were actually chambered (shotsInClip > 0).
                if (abilityResolver != null && shotsInClip > 0) {
                    abilityResolver.onReload(this);
                }
                visualState = WeaponVisualState.NORMAL;
            }
        }
    }

    /**
     * Initiates a shot: transitions to FIRING, starts the flash timer, decrements the
     * clip, and delegates tile-space marching to marchShot().
     * Callers MUST check canFire() before calling; this method does not re-check.
     *
     * Ability integration:
     *   1. AbilityResolver.onFire() is called BEFORE the accuracy roll so ON_FIRE
     *      abilities (e.g. BURST_FIRE) can set pendingBurstExtra before the base shot.
     *   2. After marchShot(), if lastHitEnemy was set by the subclass, onHit() is called.
     *      If onHit() returns true (crit), onCrit() is chained.
     *      If the enemy died after ability damage, onKill() is chained.
     *   3. pendingBurstExtra extra shots are fired sequentially after the base shot,
     *      each with its own accuracy roll (BURST_FIRE weapons spray unpredictably).
     *
     * @param playerTileColumn  column of the tile the player occupies
     * @param playerTileRow     row    of the tile the player occupies
     * @param facingStepColumn  facing direction in X: Math.round(directionX), ∈ {-1, 0, 1}
     * @param facingStepRow     facing direction in Y: Math.round(directionY), ∈ {-1, 0, 1}
     * @param level             current level for wall collision queries
     * @param enemyHitTarget    enemy query interface; null if no enemies exist yet
     * @param barrelHitTarget   barrel detonation interface; null disables barrel hits
     * @param doorBlocksQuery   door state query; null disables door blocking (shots pass through)
     * @return outcome of the base shot (burst extra outcomes are discarded)
     */
    public FireResult fire(int playerTileColumn, int playerTileRow,
                                 int facingStepColumn, int facingStepRow,
                                 Level level, EnemyHitTarget enemyHitTarget,
                                 BarrelHitTarget barrelHitTarget, DoorBlocksQuery doorBlocksQuery) {
        // Clear per-activation state so stale data cannot bleed forward.
        lastHitEnemy             = null;
        lastHitDamage            = 0;
        lastTargetWasFullHp      = false;
        fireCycleMultiplier      = 1.0f;
        this.lastFacingStepColumn    = facingStepColumn;
        this.lastFacingStepRow       = facingStepRow;
        this.lastPlayerTileColumn    = playerTileColumn;
        this.lastPlayerTileRow       = playerTileRow;

        // ON_FIRE abilities (e.g. BURST_FIRE, SECOND_WIND, ADRENAL_SURGE) run before the
        // accuracy roll so they can set fireCycleMultiplier and pendingBurstExtra first.
        if (abilityResolver != null) {
            abilityResolver.onFire(this);
        }

        shotsInClip--;
        visualState           = WeaponVisualState.FIRING;
        fireFlashTimerSeconds = WeaponConstants.FIRE_FLASH_DURATION;
        flashCycleCount++;

        FireResult baseResult;
        if (!isPerPelletAccuracy() && !rollHitChance()) {
            if (eventTextSystem != null) eventTextSystem.showMiss();
            baseResult = FireResult.MISSED;
        } else {
            baseResult = marchShot(playerTileColumn, playerTileRow, facingStepColumn, facingStepRow,
                                   level, enemyHitTarget, barrelHitTarget, doorBlocksQuery);
            // Notify resolver of the hit (and chain crit/kill if applicable).
            dispatchHitCallbacks(baseResult);
        }

        // BURST_FIRE: fire extra shots, each with its own accuracy roll.
        // Shots are capped by remaining clip; pendingBurstExtra is reset to 0 after use.
        while (pendingBurstExtra > 0 && shotsInClip > 0) {
            pendingBurstExtra--;
            lastHitEnemy  = null;
            lastHitDamage = 0;
            shotsInClip--;
            flashCycleCount++;
            if (!isPerPelletAccuracy() && !rollHitChance()) {
                if (eventTextSystem != null) eventTextSystem.showMiss();
            } else {
                // Extra burst shots also trigger hit/crit/kill callbacks.
                FireResult burstResult = marchShot(playerTileColumn, playerTileRow,
                                                   facingStepColumn, facingStepRow,
                                                   level, enemyHitTarget, barrelHitTarget,
                                                   doorBlocksQuery);
                dispatchHitCallbacks(burstResult);
            }
        }
        // Reset any remaining burst count (e.g. clip ran out mid-burst).
        pendingBurstExtra = 0;

        return baseResult;
    }

    /**
     * Chains onHit → onCrit → onKill resolver callbacks for the last marchShot() pass.
     * No-op when abilityResolver is null, lastHitEnemy is null, or the result was not a hit.
     * Called once for the base shot and once for each extra burst shot.
     */
    protected void dispatchHitCallbacks(FireResult result) {
        if (abilityResolver == null || lastHitEnemy == null) return;
        // Only dispatch for actual enemy hits (distanceTiles >= 0 and not a wall stop).
        if (result.stoppedByWall || result.distanceTiles < 0) return;

        boolean wasCrit = abilityResolver.onHit(this, lastHitEnemy, lastHitDamage,
                lastFacingStepColumn, lastFacingStepRow);
        int critBonusDamage = wasCrit
                ? Math.round(lastHitDamage * (WeaponConstants.CRIT_DAMAGE_MULTIPLIER - 1f))
                : 0;
        if (wasCrit) {
            abilityResolver.onCrit(this, lastHitEnemy, lastHitDamage + critBonusDamage);
        }
        if (!abilityResolver.isEnemyAlive(lastHitEnemy)) {
            abilityResolver.onKill(this, lastHitEnemy);
        }
    }

    /**
     * Subclass hook: march the shot through the level and return the outcome.
     * Called by fire() after state has already been updated — do NOT call directly.
     * Each weapon defines its own spread pattern, penetration behaviour, etc.
     * enemyHitTarget, barrelHitTarget, and doorBlocksQuery may be null; subclasses must guard.
     */
    protected abstract FireResult marchShot(int playerTileColumn, int playerTileRow,
                                             int facingStepColumn, int facingStepRow,
                                             Level level, EnemyHitTarget enemyHitTarget,
                                             BarrelHitTarget barrelHitTarget,
                                             DoorBlocksQuery doorBlocksQuery);

    /** Returns true for melee weapons; false for all ranged weapons. */
    @Override
    public abstract boolean isMelee();

    /** Path to the texture shown when the weapon is idle and ready. */
    public abstract String getNormalTexturePath();
    /** Path to the texture shown during the muzzle-flash pose. */
    public abstract String getFireTexturePath();
    /** Path to the texture shown while the weapon is reloading. */
    public abstract String getReloadTexturePath();

    /**
     * Effective damage at a given tile distance, scaled by the current fire-cycle multiplier.
     * The distance-drop formula is applied first, then fireCycleMultiplier is multiplied in.
     * fireCycleMultiplier is set by AbilityResolver.onFire() for SECOND_WIND and ADRENAL_SURGE;
     * it defaults to 1.0f and is reset at the start of each fire() call.
     */
    public int damageAtDistance(int distanceTiles) {
        float dropMultiplier = GameMath.damageDropMultiplier(damageDropCoefficient,
                distanceTiles, WeaponConstants.DAMAGE_MIN_MULTIPLIER);
        return Math.round(damage * dropMultiplier * fireCycleMultiplier);
    }

    public WeaponVisualState getVisualState()             { return visualState; }
    public int               getShotsInClip()            { return shotsInClip; }
    public int               getClipSize()               { return clipSize; }
    public String            getDisplayName()            { return displayName; }
    public int               getFlashCycleCount()        { return flashCycleCount; }
    public int               getDamage()                 { return damage; }
    public int               getReloadTime()             { return reloadTime; }
    public float             getDamageDropCoefficient()  { return damageDropCoefficient; }
    public int               getRange()                  { return range; }
    /** The ammo type this weapon draws from; null if ammo is infinite. */
    public AmmoType          getAmmoType()               { return ammoType; }
    /** Current reserve for this weapon's ammo type; -1 if no reserve is tracked. */
    public int               getReserveAmmo() {
        if (ammoInventory == null || ammoType == null) return -1;
        return ammoInventory.countOf(ammoType.getItemType());
    }

    /** Short ammo string for the HUD readout, e.g. "SHELLS 1/1" or "RELOAD". */
    public String hudAmmoString() {
        if (visualState == WeaponVisualState.RELOADING) return "RELOAD";
        return "SHELLS " + shotsInClip + "/" + clipSize;
    }
}
