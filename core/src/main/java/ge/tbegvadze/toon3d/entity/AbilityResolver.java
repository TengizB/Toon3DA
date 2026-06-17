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
 *   CRITICAL_STRIKE    (ON_HIT)    — rolls a crit and deals bonus damage.
 *   EXECUTIONER        (ON_HIT)    — bonus damage when target is below EXECUTIONER_THRESHOLD HP%.
 *   STAGGER_ROUNDS     (ON_HIT)    — rolls a chance to set enemy.skipNextAction = true.
 *   BURST_FIRE         (ON_FIRE)   — signals Weapon to fire extra shots via pendingBurstExtra.
 *   LIFESTEAL          (ON_HIT)    — heals player for a fraction of damage dealt.
 *   HEMORRHAGE_HARVEST (ON_KILL)   — heals player for a fixed amount on each kill.
 *   VAMPIRIC_CRIT      (ON_CRIT)   — heals player when a critical hit lands.
 *   ADRENAL_SURGE      (ON_KILL)   — chance to buff next attack's damage on kill.
 *   BULWARK_ROUNDS     (ON_RELOAD) — grants temporary armor after reloading.
 *   SECOND_WIND        (PASSIVE)   — boosts damage when player HP is critically low.
 *
 * Passive abilities (ARMOR_PIERCE, OVERPENETRATION) are NOT handled here; they are
 * applied inline inside each weapon's marchShot() implementation.
 */
public final class AbilityResolver {

    private final EnemyManager    enemyManager;
    private final EventTextSystem eventTextSystem;
    private final Player          player;

    /**
     * Own RNG — seeded at construction from the world run seed so ability rolls are
     * reproducible when replaying the same seed.  Do NOT use LibGDX MathUtils.random —
     * LibGDX's global RNG is shared with rendering code and would make rolls non-reproducible.
     */
    private final Random abilityRandom;

    public AbilityResolver(EnemyManager enemyManager, EventTextSystem eventTextSystem,
                           Player player, long runSeed) {
        this.enemyManager    = enemyManager;
        this.eventTextSystem = eventTextSystem;
        this.player          = player;
        this.abilityRandom   = new Random(runSeed);
    }

    // ── Entry points called from Weapon.fire() / Weapon.onTick() ─────────────

    /**
     * Called once per fire activation, BEFORE the accuracy roll.
     * Handles ON_FIRE abilities (BURST_FIRE) and builds the fire-cycle damage multiplier
     * from PASSIVE (SECOND_WIND) and pending buff (ADRENAL_SURGE) sources.
     *
     * @param weapon the weapon being fired
     */
    public void onFire(Weapon weapon) {
        // ── BURST_FIRE ────────────────────────────────────────────────────
        if (weapon.hasAbility(WeaponAbility.BURST_FIRE)) {
            int burstCount = weapon.abilityCount(WeaponAbility.BURST_FIRE);
            // burstCount − 1: the base shot fires unconditionally, only extras are queued.
            int extraShots = Math.max(0, burstCount - 1);
            weapon.setPendingBurstExtra(extraShots);
        }

        // Build the combined fire-cycle multiplier from passive / pending buff sources.
        float fireCycleMultiplier = 1.0f;

        // ── SECOND_WIND (PASSIVE) ─────────────────────────────────────────
        // Damage bonus active whenever HP is at or below the threshold — no state required.
        if (weapon.hasAbility(WeaponAbility.SECOND_WIND) && player != null) {
            if (player.getHealthFraction() <= GameBalance.SECOND_WIND_HP_THRESHOLD) {
                float bonus = weapon.abilityMagnitude(WeaponAbility.SECOND_WIND);
                fireCycleMultiplier *= (1.0f + bonus);
            }
        }

        // ── ADRENAL_SURGE pending buff ────────────────────────────────────
        // Consume the pending kill-proc buff (returns 1.0 if none is active).
        if (player != null && player.getPlayerStats() != null) {
            fireCycleMultiplier *= player.getPlayerStats().pollAdrenalSurgeMultiplier();
        }

        if (fireCycleMultiplier != 1.0f) {
            weapon.setFireCycleMultiplier(fireCycleMultiplier);
        }
    }

    /**
     * Called once per enemy hit, after marchShot() confirms a hit and after the base
     * damage has already been applied via {@code EnemyManager.applyDamageTo()}.
     *
     * Evaluates ON_HIT abilities in order: CRITICAL_STRIKE → EXECUTIONER →
     * STAGGER_ROUNDS → LIFESTEAL.  Returns true if a critical hit was rolled.
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
        // Check AFTER crit bonus so HP fraction reflects the post-crit state.
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

        // ── LIFESTEAL ──────────────────────────────────────────────────────
        // Heals player for a fraction of the base damage dealt this hit.
        // Only shows event text when the heal is large enough to matter.
        if (weapon.hasAbility(WeaponAbility.LIFESTEAL) && player != null) {
            float stealFraction = weapon.abilityMagnitude(WeaponAbility.LIFESTEAL);
            int   healAmount    = Math.max(1, Math.round(damageDealt * stealFraction));
            player.applyHealing(healAmount);
            if (healAmount >= GameBalance.LIFESTEAL_TEXT_THRESHOLD && eventTextSystem != null) {
                eventTextSystem.spawnWithColor("+" + healAmount + " HP", EventTextSystem.COLOR_GREEN);
            }
        }

        return wasCrit;
    }

    /**
     * Called when {@link #onHit} returned true (a critical hit was rolled).
     * Handles ON_CRIT abilities: VAMPIRIC_CRIT heals the player on each crit.
     *
     * @param weapon         the weapon that scored the crit
     * @param hitEnemyObject the enemy that was critically hit (Object to avoid circular import)
     * @param critDamage     total crit damage delivered (base + bonus)
     */
    public void onCrit(Weapon weapon, Object hitEnemyObject, int critDamage) {
        // ── VAMPIRIC_CRIT ─────────────────────────────────────────────────
        if (weapon.hasAbility(WeaponAbility.VAMPIRIC_CRIT) && player != null) {
            int healAmount = Math.round(weapon.abilityMagnitude(WeaponAbility.VAMPIRIC_CRIT));
            if (healAmount > 0) {
                player.applyHealing(healAmount);
                if (eventTextSystem != null) {
                    eventTextSystem.spawnWithColor("CRIT HEAL +" + healAmount, EventTextSystem.COLOR_GREEN);
                }
            }
        }
    }

    /**
     * Called when the enemy hit by this weapon dies (kill confirmed after all hit callbacks).
     * Handles ON_KILL abilities: HEMORRHAGE_HARVEST heals the player; ADRENAL_SURGE
     * rolls a proc chance and stores a pending damage buff for the next fire activation.
     *
     * @param weapon            the weapon that scored the kill
     * @param killedEnemyObject the enemy instance that just died (Object to avoid circular import)
     */
    public void onKill(Weapon weapon, Object killedEnemyObject) {
        // ── HEMORRHAGE_HARVEST ─────────────────────────────────────────────
        if (weapon.hasAbility(WeaponAbility.HEMORRHAGE_HARVEST) && player != null) {
            int healAmount = Math.round(weapon.abilityMagnitude(WeaponAbility.HEMORRHAGE_HARVEST));
            if (healAmount > 0) {
                player.applyHealing(healAmount);
                if (eventTextSystem != null) {
                    eventTextSystem.spawnWithColor("+" + healAmount + " HP", EventTextSystem.COLOR_GREEN);
                }
            }
        }

        // ── ADRENAL_SURGE ─────────────────────────────────────────────────
        // Rolls against the pre-scaled chance; on success stores a one-shot damage buff
        // in PlayerStats that onFire() will consume on the next fire activation.
        if (weapon.hasAbility(WeaponAbility.ADRENAL_SURGE) && player != null
                && player.getPlayerStats() != null) {
            float surgeChance = weapon.abilityMagnitude(WeaponAbility.ADRENAL_SURGE);
            if (abilityRandom.nextFloat() <= surgeChance) {
                player.getPlayerStats().applyAdrenalSurge(GameBalance.ADRENAL_SURGE_DAMAGE_BONUS);
                if (eventTextSystem != null) {
                    eventTextSystem.spawnWithColor("SURGE!", EventTextSystem.COLOR_WHITE);
                }
            }
        }
    }

    /**
     * Called when the weapon's reload completes and at least one round was chambered.
     * Handles ON_RELOAD abilities: BULWARK_ROUNDS grants temporary armor for
     * {@link GameBalance#BULWARK_ARMOR_DURATION} player-action turns.
     *
     * @param weapon the weapon that just finished reloading
     */
    public void onReload(Weapon weapon) {
        // ── BULWARK_ROUNDS ─────────────────────────────────────────────────
        if (weapon.hasAbility(WeaponAbility.BULWARK_ROUNDS) && player != null
                && player.getPlayerStats() != null) {
            int armorGain = Math.round(weapon.abilityMagnitude(WeaponAbility.BULWARK_ROUNDS));
            if (armorGain > 0) {
                player.getPlayerStats().addTempArmor(armorGain, GameBalance.BULWARK_ARMOR_DURATION);
                if (eventTextSystem != null) {
                    eventTextSystem.spawnWithColor("BULWARK +" + armorGain, EventTextSystem.COLOR_WHITE);
                }
            }
        }
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
