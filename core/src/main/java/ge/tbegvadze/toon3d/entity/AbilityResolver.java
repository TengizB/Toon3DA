package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.render.EventTextSystem;
import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.WeaponConstants;

import java.util.Random;

/**
 * Stateless ability effect dispatcher.
 *
 * Injected once by World and reused across all ticks. Call the appropriate onX() method
 * from Weapon.fire() at the right moment in the hit pipeline. The resolver reads
 * pre-scaled magnitudes from AbilityInstance (stored on the WeaponProfile) — it never
 * recomputes the BASE/PER_LEVEL/CAP formula at runtime.
 *
 * Abilities handled here:
 *   CRITICAL_STRIKE (ON_HIT)   — rolls a crit and deals bonus damage.
 *   EXECUTIONER     (ON_HIT)   — bonus damage when target is below EXECUTIONER_THRESHOLD HP%.
 *   STAGGER_ROUNDS  (ON_HIT)   — rolls a chance to set enemy.skipNextAction = true.
 *   BURST_FIRE      (ON_FIRE)  — signals Weapon to fire extra shots via pendingBurstExtra.
 *
 * Passive abilities (ARMOR_PIERCE, OVERPENETRATION) are NOT handled here; they are
 * applied inline inside each weapon's marchShot() implementation.
 *
 * Sustain abilities (LIFESTEAL, HEMORRHAGE_HARVEST, ADRENAL_SURGE, etc.) are stubs
 * in this order; they will be wired in weapon-system-order-5.
 */
public final class AbilityResolver {

    private final EnemyManager    enemyManager;
    private final EventTextSystem eventTextSystem;

    /**
     * Own RNG — seeded at construction from the world run seed so ability rolls are
     * reproducible when replaying the same seed.  Do NOT use LibGDX MathUtils.random —
     * LibGDX's global RNG is shared with rendering code and would make rolls non-reproducible.
     */
    private final Random abilityRandom;

    public AbilityResolver(EnemyManager enemyManager, EventTextSystem eventTextSystem, long runSeed) {
        this.enemyManager    = enemyManager;
        this.eventTextSystem = eventTextSystem;
        this.abilityRandom   = new Random(runSeed);
    }

    // ── Entry points called from Weapon.fire() ────────────────────────────────

    /**
     * Called once per fire activation, BEFORE the accuracy roll.
     * Handles ON_FIRE abilities such as BURST_FIRE.
     *
     * @param weapon the weapon being fired (read-only for ability queries; mutable for setPendingBurstExtra)
     */
    public void onFire(Weapon weapon) {
        if (weapon.hasAbility(WeaponAbility.BURST_FIRE)) {
            int burstCount = weapon.abilityCount(WeaponAbility.BURST_FIRE);
            // The base shot fires unconditionally; only the extra shots are queued here.
            // burstCount - 1 so total shots = 1 (base) + pendingBurstExtra.
            int extraShots = Math.max(0, burstCount - 1);
            weapon.setPendingBurstExtra(extraShots);
        }
    }

    /**
     * Called once per enemy hit, after marchShot() confirms a hit and after the base
     * damage has already been applied via {@code EnemyManager.applyDamageTo()}.
     *
     * Accepts the hit enemy as {@code Object} to avoid a circular package dependency
     * (Weapon.java in {@code entity} must not import {@code enemy.Enemy}).  The cast to
     * {@code Enemy} is safe because EnemyManager.applyDamageTo() already performs the
     * same cast internally, and only Enemy instances are ever returned by enemyAt().
     *
     * Evaluates ON_HIT abilities in order: CRITICAL_STRIKE first, then EXECUTIONER,
     * then STAGGER_ROUNDS.  Returns true if a critical hit was rolled (so the caller
     * can chain to {@link #onCrit}).
     *
     * @param weapon         the weapon that fired the shot
     * @param hitEnemyObject the Object returned by EnemyHitTarget.enemyAt() (non-null)
     * @param damageDealt    the base damage amount that was applied before this call
     * @return true if CRITICAL_STRIKE triggered a crit this hit; false otherwise
     */
    public boolean onHit(Weapon weapon, Object hitEnemyObject, int damageDealt) {
        Enemy hitEnemy = (Enemy) hitEnemyObject;
        boolean wasCrit = false;

        // ── CRITICAL_STRIKE ────────────────────────────────────────────────
        if (weapon.hasAbility(WeaponAbility.CRITICAL_STRIKE)) {
            float critChance = weapon.abilityMagnitude(WeaponAbility.CRITICAL_STRIKE);
            if (abilityRandom.nextFloat() <= critChance) {
                // Bonus portion only: total crit damage = base + bonus.
                // bonus = (CRIT_MULTIPLIER - 1) * base, so total = CRIT_MULTIPLIER * base.
                int critBonus = Math.round(damageDealt * (WeaponConstants.CRIT_DAMAGE_MULTIPLIER - 1f));
                if (critBonus > 0) {
                    enemyManager.applyDamageTo(hitEnemy, critBonus);
                }
                if (eventTextSystem != null) {
                    eventTextSystem.spawnWithColor("CRIT!", EventTextSystem.COLOR_RED);
                }
                wasCrit = true;
            }
        }

        // ── EXECUTIONER ────────────────────────────────────────────────────
        // Check AFTER the crit bonus has been applied so the HP fraction reflects
        // the most up-to-date state (crit may have already pushed enemy below threshold).
        if (weapon.hasAbility(WeaponAbility.EXECUTIONER) && hitEnemy.isAlive()) {
            float hpFraction = (float) hitEnemy.health / hitEnemy.maxHealth;
            if (hpFraction <= GameBalance.EXECUTIONER_THRESHOLD) {
                float bonusMagnitude    = weapon.abilityMagnitude(WeaponAbility.EXECUTIONER);
                int   executionerDamage = Math.round(damageDealt * bonusMagnitude);
                if (executionerDamage > 0) {
                    enemyManager.applyDamageTo(hitEnemy, executionerDamage);
                }
                if (eventTextSystem != null) {
                    eventTextSystem.spawnWithColor("EXECUTE!", EventTextSystem.COLOR_RED);
                }
            }
        }

        // ── STAGGER_ROUNDS ─────────────────────────────────────────────────
        if (weapon.hasAbility(WeaponAbility.STAGGER_ROUNDS) && hitEnemy.isAlive()) {
            float staggerChance = weapon.abilityMagnitude(WeaponAbility.STAGGER_ROUNDS);
            if (abilityRandom.nextFloat() <= staggerChance) {
                hitEnemy.skipNextAction = true;
                if (eventTextSystem != null) {
                    eventTextSystem.spawnWithColor("STAGGER!", EventTextSystem.COLOR_WHITE);
                }
            }
        }

        return wasCrit;
    }

    /**
     * Called when {@link #onHit} returned true (a critical hit was rolled).
     * Handles ON_CRIT abilities such as VAMPIRIC_CRIT and HELLFIRE_NOVA.
     * Stub in this order — wired in weapon-system-order-5.
     *
     * @param weapon         the weapon that scored the crit
     * @param hitEnemyObject the enemy that was critically hit (Object to avoid circular import)
     * @param critDamage     total crit damage delivered (base + bonus)
     */
    public void onCrit(Weapon weapon, Object hitEnemyObject, int critDamage) {
        // Stub — ON_CRIT sustain abilities wired in weapon-system-order-5.
    }

    /**
     * Called when the enemy hit by this weapon dies (kill confirmed).
     * Handles ON_KILL abilities such as HEMORRHAGE_HARVEST, ADRENAL_SURGE, SOULFORGE, etc.
     * Stub in this order — wired in weapon-system-order-5.
     *
     * @param weapon            the weapon that scored the kill
     * @param killedEnemyObject the enemy instance that just died (Object to avoid circular import)
     */
    public void onKill(Weapon weapon, Object killedEnemyObject) {
        // Stub — ON_KILL sustain abilities wired in weapon-system-order-5.
    }

    /**
     * Called when the weapon's reload completes (shotsInClip refilled).
     * Handles ON_RELOAD abilities such as BULWARK_ROUNDS.
     * Stub in this order — wired in weapon-system-order-5.
     *
     * @param weapon the weapon that just finished reloading
     */
    public void onReload(Weapon weapon) {
        // Stub — ON_RELOAD abilities wired in weapon-system-order-5.
    }

    /**
     * Returns true if the given enemy object is still alive.
     * Used by Weapon.fire() to test for kill after the resolver's onHit() returns.
     * Accepts Object to avoid circular package dependency in Weapon.java.
     *
     * @param enemyObject the Object returned by EnemyHitTarget.enemyAt()
     * @return true if the enemy's health is still above zero
     */
    public boolean isEnemyAlive(Object enemyObject) {
        return ((Enemy) enemyObject).isAlive();
    }
}
