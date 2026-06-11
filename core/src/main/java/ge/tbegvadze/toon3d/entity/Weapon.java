package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.render.EventTextSystem;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;

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
public abstract class Weapon {

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

    /**
     * Ranged damage multiplier sourced from the player's MARKSMANSHIP stat.
     * Defaults to 1.0 (no bonus) until World injects a non-trivial value via
     * {@link #setRangedDamageMultiplier}.  Updated whenever PlayerStats changes
     * (perk award, equipment change) — never queried per frame, only at fire time.
     */
    private float rangedDamageMultiplier = 1.0f;

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
                    normalToReloadTimerSeconds = Constants.NORMAL_TO_RELOAD_DELAY_SECONDS;
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
                    int roundsLoaded = ammoInventory.spend(ammoType.getItemType(), clipSize);
                    shotsInClip = roundsLoaded;
                    if (roundsLoaded == 0) {
                        if (eventTextSystem != null) eventTextSystem.spawn("OUT OF AMMO!");
                    } else {
                        if (eventTextSystem != null) eventTextSystem.spawn("Ready!");
                    }
                } else {
                    // ammoInventory not yet injected — infinite ammo fallback for tests.
                    shotsInClip = clipSize;
                    if (eventTextSystem != null) eventTextSystem.spawn("Ready!");
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
     * @param playerTileColumn  column of the tile the player occupies
     * @param playerTileRow     row    of the tile the player occupies
     * @param facingStepColumn  facing direction in X: Math.round(directionX), ∈ {-1, 0, 1}
     * @param facingStepRow     facing direction in Y: Math.round(directionY), ∈ {-1, 0, 1}
     * @param level             current level for wall collision queries
     * @param enemyHitTarget    enemy query interface; null if no enemies exist yet
     * @param barrelHitTarget   barrel detonation interface; null disables barrel hits
     * @param doorBlocksQuery   door state query; null disables door blocking (shots pass through)
     * @return outcome of the shot
     */
    public final FireResult fire(int playerTileColumn, int playerTileRow,
                                 int facingStepColumn, int facingStepRow,
                                 Level level, EnemyHitTarget enemyHitTarget,
                                 BarrelHitTarget barrelHitTarget, DoorBlocksQuery doorBlocksQuery) {
        shotsInClip--;
        visualState           = WeaponVisualState.FIRING;
        fireFlashTimerSeconds = Constants.FIRE_FLASH_DURATION;
        flashCycleCount++;
        return marchShot(playerTileColumn, playerTileRow, facingStepColumn, facingStepRow,
                         level, enemyHitTarget, barrelHitTarget, doorBlocksQuery);
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

    /** Path to the texture shown when the weapon is idle and ready. */
    public abstract String getNormalTexturePath();
    /** Path to the texture shown during the muzzle-flash pose. */
    public abstract String getFireTexturePath();
    /** Path to the texture shown while the weapon is reloading. */
    public abstract String getReloadTexturePath();

    /**
     * Effective damage at a given tile distance.
     * Uses the linear drop formula from GameMath and rounds to the nearest integer
     * so displayed and applied damage is always a whole number.
     */
    public int damageAtDistance(int distanceTiles) {
        float multiplier = GameMath.damageDropMultiplier(damageDropCoefficient,
                distanceTiles, Constants.DAMAGE_MIN_MULTIPLIER);
        return Math.round(damage * multiplier);
    }

    public WeaponVisualState getVisualState()      { return visualState; }
    public int               getShotsInClip()     { return shotsInClip; }
    public int               getClipSize()        { return clipSize; }
    public String            getDisplayName()     { return displayName; }
    public int               getFlashCycleCount() { return flashCycleCount; }
    /** The ammo type this weapon draws from; null if ammo is infinite. */
    public AmmoType          getAmmoType()        { return ammoType; }
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
