package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.progression.KillXpListener;
import ge.tbegvadze.toon3d.render.EventTextSystem;
import ge.tbegvadze.toon3d.status.StatusEffectController;
import ge.tbegvadze.toon3d.status.StatusType;
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
 *   STAGGER_ROUNDS     (ON_HIT)    — applies STUNNED via StatusEffectController on proc.
 *   REND               (ON_HIT)    — applies BLEED DoT; refreshes duration on repeat hits.
 *   INCENDIARY         (ON_HIT)    — applies BURNING DoT; refreshes if already burning.
 *   KINETIC_SLAM       (ON_HIT)    — melee: chance to knock enemy back; wall bonus damage on block.
 *   CLEAVE             (ON_HIT)    — melee: deals fraction of damage to both flanking tiles.
 *   BURST_FIRE         (ON_FIRE)   — signals Weapon to fire extra shots via pendingBurstExtra.
 *   JUDGMENT           (ON_FIRE)   — every JUDGMENT_COOLDOWN_FIRES activations, fires a full-pierce lance.
 *   LIFESTEAL          (ON_HIT)    — heals player for a fraction of damage dealt.
 *   HEMORRHAGE_HARVEST (ON_KILL)   — heals player for a fixed amount on each kill.
 *   VAMPIRIC_CRIT      (ON_CRIT)   — heals player when a critical hit lands.
 *   HELLFIRE_NOVA      (ON_CRIT)   — Chebyshev-radius AoE explosion on every crit.
 *   ADRENAL_SURGE      (ON_KILL)   — chance to buff next attack's damage on kill.
 *   SALVAGE_STRIKE     (ON_KILL)   — melee: chance to override drop tile with ammo pickup.
 *   SCHOLARS_EDGE      (ON_KILL)   — melee: awards bonus XP equal to a fraction of base reward.
 *   SCAVENGER_ROUNDS   (ON_KILL)   — gun: chance to refund ammo to reserve on kill.
 *   FIELD_MEDIC_ROUNDS (ON_KILL)   — universal: chance to drop a medkit on the killed enemy's tile.
 *   CREDIT_FANG        (ON_KILL)   — universal: awards bonus credits to PlayerStats on kill.
 *   RHYTHM             (ON_HIT)    — consecutive hits on the same target ramp damage per stack.
 *   STATIC_DISCHARGE   (ON_KILL)   — AoE splash to all four orthogonally adjacent enemies on kill.
 *   POINT_BLANK        (ON_HIT)    — bonus damage when target is within POINT_BLANK_MAX_DISTANCE tiles.
 *   MARKSMANS_PATIENCE (ON_HIT)    — bonus damage scaling with tile distance beyond minimum range.
 *   OPENING_SALVO      (ON_HIT)    — bonus damage on the first hit against a full-HP target.
 *   RESONANT_ROUNDS    (ON_HIT)    — bonus flat damage as a fraction of the target's max HP.
 *   BULWARK_ROUNDS     (ON_RELOAD) — grants temporary armor after reloading.
 *   SECOND_WIND        (PASSIVE)   — boosts damage when player HP is critically low.
 *   BERSERKERS_OATH    (PASSIVE)   — melee legendary: kill-streak stacks for damage/HP regen.
 *   SOULFORGE          (ON_KILL)   — gun/melee legendary: weapon levels up every N kills.
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

    /**
     * Injected by World so Scholar's Edge can award bonus XP directly to PlayerProgress
     * without AbilityResolver holding a direct reference to the progression layer.
     */
    private KillXpListener killXpListener = null;

    /**
     * Injected by World so Scavenger Rounds can refund ammo directly to the player's
     * ammo reserve on a kill proc.  Null until World calls setPlayerInventory().
     */
    private Inventory playerInventory = null;

    /**
     * Injected by World so STAGGER_ROUNDS, REND, and INCENDIARY abilities can apply
     * status effects through the shared controller.  Null until World calls
     * setStatusEffectController().
     */
    private StatusEffectController statusEffectController = null;

    /**
     * Re-entrancy guard for CLEAVE: prevents the recursive onHit() calls for flanking
     * targets from re-triggering another Cleave sweep (infinite recursion prevention).
     */
    private boolean cleaveInProgress = false;

    /**
     * Re-entrancy guard for STATIC_DISCHARGE: prevents secondary kill procs from
     * chaining another discharge (infinite recursion prevention).
     */
    private boolean resolvingDischarge = false;

    public AbilityResolver(EnemyManager enemyManager, EventTextSystem eventTextSystem,
                           Player player, long runSeed) {
        this.enemyManager    = enemyManager;
        this.eventTextSystem = eventTextSystem;
        this.player          = player;
        this.abilityRandom   = new Random(runSeed);
    }

    /**
     * Injects the XP listener so Scholar's Edge can award bonus XP on melee kills.
     * Called once by World after AbilityResolver is constructed.
     */
    public void setKillXpListener(KillXpListener listener) {
        this.killXpListener = listener;
    }

    /**
     * Injects the player's shared item inventory so Scavenger Rounds can refund ammo
     * directly to the reserve on a kill proc.
     * Called once by World after both the inventory and resolver are constructed.
     */
    public void setPlayerInventory(Inventory inventory) {
        this.playerInventory = inventory;
    }

    /**
     * Injects the status effect controller so STAGGER_ROUNDS, REND, and INCENDIARY
     * can apply STUNNED, BLEED, and BURNING through the shared lifecycle manager.
     * Called once by World after both the controller and resolver are constructed.
     */
    public void setStatusEffectController(StatusEffectController controller) {
        this.statusEffectController = controller;
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

        // ── BERSERKERS_OATH (melee only) ──────────────────────────────────────
        // Resets stacks if the previous activation had no kill; then applies the current
        // stack bonus on top of whatever fireCycleMultiplier was already set above.
        if (weapon.hasAbility(WeaponAbility.BERSERKERS_OATH) && weapon.isMelee()) {
            if (!weapon.wasBerserkerKilledThisFire() && weapon.getBerserkerStacks() > 0) {
                weapon.resetBerserkerStacks();
            }
            weapon.setBerserkerKilledThisFire(false);
            int stacks = weapon.getBerserkerStacks();
            if (stacks > 0) {
                float stackBonus = stacks * GameBalance.BERSERKER_DAMAGE_PER_STACK;
                weapon.setFireCycleMultiplier(weapon.getFireCycleMultiplier() * (1.0f + stackBonus));
            }
        }

        // ── JUDGMENT (gun only, legendary) ────────────────────────────────────
        // Every JUDGMENT_COOLDOWN_FIRES activations, the shot becomes a full-pierce lance.
        // Signals Weapon.fire() via setJudgmentActive(true) to use marchJudgmentLance instead
        // of marchShot. The lance bypasses the accuracy roll and hits every enemy in the line.
        if (weapon.hasAbility(WeaponAbility.JUDGMENT) && !weapon.isMelee()) {
            if (weapon.consumeJudgment()) {
                weapon.setJudgmentActive(true);
                if (eventTextSystem != null) {
                    eventTextSystem.spawnWithColor("JUDGMENT!", EventTextSystem.COLOR_GOLD);
                }
            }
        }
    }

    /**
     * Called once per enemy hit, after marchShot() confirms a hit and after the base
     * damage has already been applied via {@code EnemyManager.applyDamageTo()}.
     *
     * Evaluates ON_HIT abilities in order: CRITICAL_STRIKE → EXECUTIONER →
     * STAGGER_ROUNDS → LIFESTEAL → KINETIC_SLAM → CLEAVE → REND → INCENDIARY →
     * RHYTHM → POINT_BLANK → MARKSMANS_PATIENCE → OPENING_SALVO → RESONANT_ROUNDS.
     * Returns true if a critical hit was rolled.
     *
     * @param weapon           the weapon that fired the shot
     * @param hitEnemyObject   the Object returned by EnemyHitTarget.enemyAt() (non-null)
     * @param damageDealt      the base damage amount that was applied before this call
     * @param facingStepColumn player facing direction X (−1, 0, or 1); used for melee AoE
     * @param facingStepRow    player facing direction Y (−1, 0, or 1); used for melee AoE
     * @param targetWasFullHp  true if the target was at full HP before primary damage was applied;
     *                         captured by the caller before applyDamageTo() for OPENING_SALVO
     * @param hitDistance      tile distance from the player to the hit enemy; used by
     *                         POINT_BLANK and MARKSMANS_PATIENCE
     * @return true if CRITICAL_STRIKE triggered a crit this hit; false otherwise
     */
    public boolean onHit(Weapon weapon, Object hitEnemyObject, int damageDealt,
                         int facingStepColumn, int facingStepRow,
                         boolean targetWasFullHp, int hitDistance) {
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
        if (weapon.hasAbility(WeaponAbility.STAGGER_ROUNDS) && hitEnemy.isAlive()
                && statusEffectController != null) {
            float staggerChance = weapon.abilityMagnitude(WeaponAbility.STAGGER_ROUNDS);
            if (abilityRandom.nextFloat() <= staggerChance) {
                statusEffectController.apply(hitEnemy, StatusType.STUNNED,
                        GameBalance.STAGGER_STUN_DURATION, 0, null);
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

        // ── KINETIC_SLAM ────────────────────────────────────────────────────
        // Melee only: rolls a chance to knock the enemy one tile away from the player.
        // If the push is blocked by a wall, deals bonus wall-impact damage instead.
        if (weapon.hasAbility(WeaponAbility.KINETIC_SLAM) && weapon.isMelee()
                && hitEnemy.isAlive()) {
            float knockbackChance = weapon.abilityMagnitude(WeaponAbility.KINETIC_SLAM);
            if (abilityRandom.nextFloat() <= knockbackChance) {
                int     targetColumn = hitEnemy.tileColumn + facingStepColumn;
                int     targetRow    = hitEnemy.tileRow    + facingStepRow;
                boolean pushed       = enemyManager.tryPushEnemy(hitEnemyObject, targetColumn, targetRow);
                if (!pushed && GameBalance.KINETIC_SLAM_WALL_BONUS_DAMAGE > 0) {
                    enemyManager.applyDamageTo(hitEnemyObject, GameBalance.KINETIC_SLAM_WALL_BONUS_DAMAGE);
                }
                if (eventTextSystem != null) {
                    eventTextSystem.spawnWithColor("SLAM!", EventTextSystem.COLOR_WHITE);
                }
            }
        }

        // ── CLEAVE ──────────────────────────────────────────────────────────
        // Melee only: deals a fraction of hit damage to enemies flanking the target
        // (the two tiles 90° from the facing direction adjacent to the target tile).
        // cleaveInProgress prevents the recursive onHit() calls from triggering another sweep.
        if (weapon.hasAbility(WeaponAbility.CLEAVE) && weapon.isMelee() && !cleaveInProgress) {
            float cleaveFraction   = weapon.abilityMagnitude(WeaponAbility.CLEAVE);
            int   cleaveDamage     = Math.max(1, Math.round(damageDealt * cleaveFraction));
            int   leftFlankColumn  = hitEnemy.tileColumn + (-facingStepRow);
            int   leftFlankRow     = hitEnemy.tileRow    + facingStepColumn;
            int   rightFlankColumn = hitEnemy.tileColumn + facingStepRow;
            int   rightFlankRow    = hitEnemy.tileRow    + (-facingStepColumn);
            Object leftEnemyObj  = enemyManager.enemyAt(leftFlankColumn, leftFlankRow);
            Object rightEnemyObj = enemyManager.enemyAt(rightFlankColumn, rightFlankRow);
            boolean cleaved = leftEnemyObj != null || rightEnemyObj != null;
            cleaveInProgress = true;
            try {
                if (leftEnemyObj instanceof Enemy) {
                    Enemy leftEnemy         = (Enemy) leftEnemyObj;
                    boolean leftWasFullHp   = leftEnemy.health >= leftEnemy.maxHealth;
                    enemyManager.applyDamageTo(leftEnemyObj, cleaveDamage);
                    onHit(weapon, leftEnemyObj, cleaveDamage, facingStepColumn, facingStepRow,
                            leftWasFullHp, hitDistance);
                    if (!leftEnemy.isAlive()) {
                        onKill(weapon, leftEnemyObj);
                    }
                }
                if (rightEnemyObj instanceof Enemy) {
                    Enemy rightEnemy        = (Enemy) rightEnemyObj;
                    boolean rightWasFullHp  = rightEnemy.health >= rightEnemy.maxHealth;
                    enemyManager.applyDamageTo(rightEnemyObj, cleaveDamage);
                    onHit(weapon, rightEnemyObj, cleaveDamage, facingStepColumn, facingStepRow,
                            rightWasFullHp, hitDistance);
                    if (!rightEnemy.isAlive()) {
                        onKill(weapon, rightEnemyObj);
                    }
                }
            } finally {
                cleaveInProgress = false;
            }
            if (cleaved && eventTextSystem != null) {
                eventTextSystem.spawnWithColor("CLEAVE!", EventTextSystem.COLOR_WHITE);
            }
        }

        // ── REND (BLEED DoT) ───────────────────────────────────────────────
        if (weapon.hasAbility(WeaponAbility.REND) && hitEnemy.isAlive()
                && statusEffectController != null) {
            int bleedDamage = Math.max(1, Math.round(weapon.abilityMagnitude(WeaponAbility.REND)));
            statusEffectController.apply(hitEnemy, StatusType.BLEED,
                    GameBalance.REND_DURATION_TURNS, bleedDamage, null);
            if (eventTextSystem != null) {
                eventTextSystem.spawnWithColor("BLEED!", EventTextSystem.COLOR_RED);
            }
        }

        // ── INCENDIARY (BURN DoT) ──────────────────────────────────────────
        if (weapon.hasAbility(WeaponAbility.INCENDIARY) && hitEnemy.isAlive()
                && statusEffectController != null) {
            int burnDamage = Math.max(1, Math.round(weapon.abilityMagnitude(WeaponAbility.INCENDIARY)));
            statusEffectController.apply(hitEnemy, StatusType.BURNING,
                    GameBalance.INCENDIARY_BURN_DURATION, burnDamage, null);
            if (eventTextSystem != null) {
                eventTextSystem.spawnWithColor("BURN!", EventTextSystem.COLOR_RED);
            }
        }

        // ── RHYTHM (Heat-Up ramp) ────────────────────────────────────────────
        // Consecutive hits on the SAME enemy ramp damage. updateRhythm handles target
        // switch detection: if the enemy ID differs from last, stacks reset to 1.
        if (weapon.hasAbility(WeaponAbility.RHYTHM) && hitEnemy.isAlive()) {
            weapon.updateRhythm(hitEnemy.getId());
            int stacks = weapon.getRhythmStacks();
            if (stacks > 1) {
                float rampPerHit = weapon.abilityMagnitude(WeaponAbility.RHYTHM);
                float totalRamp  = rampPerHit * (stacks - 1);
                int   rampDamage = Math.round(damageDealt * totalRamp);
                if (rampDamage > 0) {
                    enemyManager.applyDamageTo(hitEnemy, rampDamage);
                }
            }
        }

        // ── POINT_BLANK ────────────────────────────────────────────────────
        // Bonus damage only when the enemy is at or within POINT_BLANK_MAX_DISTANCE tiles.
        if (weapon.hasAbility(WeaponAbility.POINT_BLANK) && hitEnemy.isAlive()
                && hitDistance <= GameBalance.POINT_BLANK_MAX_DISTANCE) {
            float bonus     = weapon.abilityMagnitude(WeaponAbility.POINT_BLANK);
            int bonusDamage = Math.round(damageDealt * bonus);
            if (bonusDamage > 0) {
                enemyManager.applyDamageTo(hitEnemy, bonusDamage);
            }
        }

        // ── MARKSMAN'S PATIENCE ───────────────────────────────────────────
        // Bonus damage scaling with tile distance beyond MARKSMAN_MIN_DISTANCE.
        if (weapon.hasAbility(WeaponAbility.MARKSMANS_PATIENCE) && hitEnemy.isAlive()) {
            int bonusTiles = Math.max(0, hitDistance - GameBalance.MARKSMAN_MIN_DISTANCE);
            if (bonusTiles > 0) {
                float perTileBonus = weapon.abilityMagnitude(WeaponAbility.MARKSMANS_PATIENCE);
                float totalBonus   = Math.min(perTileBonus * bonusTiles,
                                              GameBalance.MARKSMAN_TOTAL_BONUS_CAP);
                int bonusDamage    = Math.round(damageDealt * totalBonus);
                if (bonusDamage > 0) {
                    enemyManager.applyDamageTo(hitEnemy, bonusDamage);
                }
            }
        }

        // ── OPENING SALVO ────────────────────────────────────────────────
        // Bonus damage on the first hit against a full-HP target.
        // targetWasFullHp was captured BEFORE primary damage was applied.
        if (weapon.hasAbility(WeaponAbility.OPENING_SALVO) && hitEnemy.isAlive()
                && targetWasFullHp) {
            float bonus     = weapon.abilityMagnitude(WeaponAbility.OPENING_SALVO);
            int bonusDamage = Math.round(damageDealt * bonus);
            if (bonusDamage > 0) {
                enemyManager.applyDamageTo(hitEnemy, bonusDamage);
            }
            if (eventTextSystem != null) {
                eventTextSystem.spawnWithColor("OPENING!", EventTextSystem.COLOR_WHITE);
            }
        }

        // ── RESONANT ROUNDS ───────────────────────────────────────────────
        // Bonus flat damage as a fraction of the TARGET's max HP — scales against elites.
        if (weapon.hasAbility(WeaponAbility.RESONANT_ROUNDS) && hitEnemy.isAlive()) {
            float pct          = weapon.abilityMagnitude(WeaponAbility.RESONANT_ROUNDS);
            int resonantDamage = Math.max(1, Math.round(hitEnemy.maxHealth * pct));
            enemyManager.applyDamageTo(hitEnemy, resonantDamage);
        }

        return wasCrit;
    }

    /**
     * Called when {@link #onHit} returned true (a critical hit was rolled).
     * Handles ON_CRIT abilities: VAMPIRIC_CRIT heals the player on each crit;
     * HELLFIRE_NOVA deals Chebyshev-radius AoE damage around the crit target.
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

        // ── HELLFIRE_NOVA ─────────────────────────────────────────────────
        // On a crit, deal AoE damage to all enemies within a Chebyshev radius of the
        // crit target. Nova hits do NOT chain into further onCrit() calls to prevent
        // infinite recursion. novaDamage is clamped to at least 1 so it always has impact.
        if (weapon.hasAbility(WeaponAbility.HELLFIRE_NOVA) && hitEnemyObject instanceof Enemy) {
            Enemy hitEnemy      = (Enemy) hitEnemyObject;
            int   novaDamage    = Math.max(1, Math.round(critDamage * GameBalance.HELLFIRE_NOVA_DAMAGE_FRACTION));
            int   epicenterColumn = hitEnemy.tileColumn;
            int   epicenterRow    = hitEnemy.tileRow;
            int   radius          = GameBalance.HELLFIRE_NOVA_RADIUS;
            boolean anyNovaHit    = false;

            for (Enemy candidate : enemyManager.getEnemies()) {
                if (candidate == hitEnemy || !candidate.isAlive()) continue;
                int distanceColumn = Math.abs(candidate.tileColumn - epicenterColumn);
                int distanceRow    = Math.abs(candidate.tileRow    - epicenterRow);
                if (distanceColumn <= radius && distanceRow <= radius) {
                    enemyManager.applyDamageTo(candidate, novaDamage);
                    anyNovaHit = true;
                }
            }
            if (anyNovaHit && eventTextSystem != null) {
                eventTextSystem.spawnWithColor("NOVA!", EventTextSystem.COLOR_GOLD);
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
        // ── RHYTHM target reset ────────────────────────────────────────────
        // When the current Rhythm target dies, reset so the next target starts fresh.
        if (weapon.hasAbility(WeaponAbility.RHYTHM) && killedEnemyObject instanceof Enemy) {
            weapon.resetRhythmIfTarget(((Enemy) killedEnemyObject).getId());
        }

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

        // ── SALVAGE_STRIKE (melee only) ────────────────────────────────────
        // Overrides the drop tile on the killed enemy's cell with an ammo pickup.
        if (weapon.hasAbility(WeaponAbility.SALVAGE_STRIKE) && weapon.isMelee()
                && killedEnemyObject instanceof Enemy) {
            Enemy killedEnemy = (Enemy) killedEnemyObject;
            float dropChance  = weapon.abilityMagnitude(WeaponAbility.SALVAGE_STRIKE);
            if (dropChance >= 1.0f || abilityRandom.nextFloat() <= dropChance) {
                enemyManager.overrideDropAt(killedEnemy.tileColumn, killedEnemy.tileRow,
                        GameBalance.SALVAGE_AMMO_DROP_CHAR);
                if (eventTextSystem != null) {
                    eventTextSystem.spawnWithColor("SALVAGE!", EventTextSystem.COLOR_GREEN);
                }
            }
        }

        // ── SCHOLARS_EDGE (melee only) ─────────────────────────────────────
        // Awards bonus XP equal to a fraction of the killed enemy's base XP reward.
        if (weapon.hasAbility(WeaponAbility.SCHOLARS_EDGE) && weapon.isMelee()
                && killedEnemyObject instanceof Enemy) {
            Enemy killedEnemy = (Enemy) killedEnemyObject;
            float xpBonus     = weapon.abilityMagnitude(WeaponAbility.SCHOLARS_EDGE);
            int   bonusXp     = Math.max(1, Math.round(killedEnemy.type.baseXpReward() * xpBonus));
            if (killXpListener != null) {
                killXpListener.onEnemyKilledForXp(bonusXp);
            }
            if (eventTextSystem != null) {
                eventTextSystem.spawnWithColor("+" + bonusXp + "XP", EventTextSystem.COLOR_GREEN);
            }
        }

        // ── BERSERKERS_OATH (melee only) ────────────────────────────────────
        // Records the kill flag and increments the streak counter. HP regen scales with stacks.
        // The stack damage bonus is applied in onFire() on the next activation.
        if (weapon.hasAbility(WeaponAbility.BERSERKERS_OATH) && weapon.isMelee()) {
            weapon.setBerserkerKilledThisFire(true);
            weapon.incrementBerserkerStacks();
            int stacks = weapon.getBerserkerStacks();
            int hpTick = stacks * GameBalance.BERSERKER_HP_TICK_PER_STACK;
            if (player != null && hpTick > 0) {
                player.applyHealing(hpTick);
            }
            if (stacks == GameBalance.BERSERKER_MAX_STACKS && eventTextSystem != null) {
                eventTextSystem.spawnWithColor("BERSERK!", EventTextSystem.COLOR_RED);
            }
        }

        // ── SCAVENGER_ROUNDS (gun only) ────────────────────────────────────
        // On kill, rolls a chance to refund ammo directly to the player's reserve for this
        // weapon's ammo type. Refund goes to reserve — never directly into the clip.
        if (weapon.hasAbility(WeaponAbility.SCAVENGER_ROUNDS) && !weapon.isMelee()
                && playerInventory != null && weapon.getAmmoType() != null) {
            float refundChance = weapon.abilityMagnitude(WeaponAbility.SCAVENGER_ROUNDS);
            if (abilityRandom.nextFloat() <= refundChance) {
                int refundAmount = (weapon.getWeaponLevel() >= GameBalance.SCAVENGER_HIGH_LEVEL_THRESHOLD)
                        ? GameBalance.SCAVENGER_REFUND_HIGH_LEVEL
                        : GameBalance.SCAVENGER_REFUND_BASE;
                playerInventory.addAmmo(weapon.getAmmoType(), refundAmount);
                if (eventTextSystem != null) {
                    eventTextSystem.spawnWithColor("+" + refundAmount + " AMMO", EventTextSystem.COLOR_GREEN);
                }
            }
        }

        // ── FIELD_MEDIC_ROUNDS (universal) ────────────────────────────────
        // On kill, rolls a chance to override the drop at the dead enemy's tile with a
        // small medkit pickup. Works for both melee and ranged kills.
        if (weapon.hasAbility(WeaponAbility.FIELD_MEDIC_ROUNDS)
                && killedEnemyObject instanceof Enemy) {
            Enemy killedEnemy = (Enemy) killedEnemyObject;
            float dropChance  = weapon.abilityMagnitude(WeaponAbility.FIELD_MEDIC_ROUNDS);
            if (abilityRandom.nextFloat() <= dropChance) {
                enemyManager.overrideDropAt(killedEnemy.tileColumn, killedEnemy.tileRow,
                        GameBalance.FIELD_MEDIC_DROP_CHAR);
                if (eventTextSystem != null) {
                    eventTextSystem.spawnWithColor("MEDKIT DROP!", EventTextSystem.COLOR_GREEN);
                }
            }
        }

        // ── CREDIT_FANG (universal) ────────────────────────────────────────
        // On kill, awards bonus credits to the player's credit balance (spendable in the
        // shop when order-11 is implemented). No HUD display; only the event text "+N CR".
        if (weapon.hasAbility(WeaponAbility.CREDIT_FANG)
                && player != null && player.getPlayerStats() != null) {
            int creditsEarned = Math.round(weapon.abilityMagnitude(WeaponAbility.CREDIT_FANG));
            if (creditsEarned > 0) {
                player.getPlayerStats().addCredits(creditsEarned);
                if (eventTextSystem != null) {
                    eventTextSystem.spawnWithColor("+" + creditsEarned + " CR", EventTextSystem.COLOR_GREEN);
                }
            }
        }

        // ── STATIC DISCHARGE ──────────────────────────────────────────────
        // On kill, deals AoE splash to all four orthogonally adjacent enemies.
        // resolvingDischarge prevents secondary kills from triggering another chain.
        if (weapon.hasAbility(WeaponAbility.STATIC_DISCHARGE) && !resolvingDischarge
                && killedEnemyObject instanceof Enemy) {
            Enemy killedEnemy = (Enemy) killedEnemyObject;
            int splashDamage  = Math.round(weapon.abilityMagnitude(WeaponAbility.STATIC_DISCHARGE));
            int deadColumn    = killedEnemy.tileColumn;
            int deadRow       = killedEnemy.tileRow;
            int[][] offsets   = { {0, 1}, {0, -1}, {1, 0}, {-1, 0} };
            boolean anyHit    = false;
            resolvingDischarge = true;
            try {
                for (int[] offset : offsets) {
                    Object adjacentObj = enemyManager.enemyAt(deadColumn + offset[0],
                                                               deadRow    + offset[1]);
                    if (adjacentObj instanceof Enemy) {
                        Enemy adjacent = (Enemy) adjacentObj;
                        enemyManager.applyDamageTo(adjacentObj, splashDamage);
                        anyHit = true;
                        if (!adjacent.isAlive()) {
                            onKill(weapon, adjacentObj);
                        }
                    }
                }
            } finally {
                resolvingDischarge = false;
            }
            if (anyHit && eventTextSystem != null) {
                eventTextSystem.spawnWithColor("DISCHARGE!", EventTextSystem.COLOR_WHITE);
            }
        }

        // ── SOULFORGE (legendary, universal) ──────────────────────────────
        // Checked last so all other ON_KILL effects resolve before the weapon levels up.
        // The level-up flag is surfaced via consumeSoulforgeAscend() to avoid storing a
        // reference to EventTextSystem inside Weapon (which has no render dependency).
        if (weapon.hasAbility(WeaponAbility.SOULFORGE)) {
            weapon.incrementSoulforgeKill();
            if (weapon.consumeSoulforgeAscend() && eventTextSystem != null) {
                eventTextSystem.spawnWithColor(
                        weapon.getDisplayName() + " ASCENDS! Lv" + weapon.getWeaponLevel(),
                        EventTextSystem.COLOR_GOLD);
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
