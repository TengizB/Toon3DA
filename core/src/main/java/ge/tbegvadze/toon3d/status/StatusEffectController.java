package ge.tbegvadze.toon3d.status;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.render.EventTextSystem;
import ge.tbegvadze.toon3d.render.ImpactEffectSystem;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.List;

/**
 * Owns the apply/tick lifecycle for all status effects.
 *
 * apply() is the single entry point for every combat source (weapon, enemy attack,
 * environment) that wants to inflict a status effect.  tickAll() is called once per
 * world turn (from StatusEffectSubscriber, before enemy AI runs) and:
 *   1. Ticks player effects — DoT damage, stun flag, expiry.
 *   2. Ticks enemy effects — DoT damage, stun flag, expiry; dead enemies are queued
 *      for processDoTKill after the loop completes (safe from ConcurrentModification).
 *
 * Zero allocation inside tickAll() or apply() — all StatusEffect instances are
 * pre-allocated in the host's EnumMap and only their fields are mutated.
 */
public final class StatusEffectController {

    private static final StatusType[] STATUS_TYPES = StatusType.values();
    private static final int MAX_PENDING_DOT_DEATHS = 64;

    private final Enemy[] pendingDoTDeaths = new Enemy[MAX_PENDING_DOT_DEATHS];
    private int pendingDoTDeathCount = 0;

    /**
     * Damage ticked into ENEMIES by effects no one applied — fire and toxic hazard tiles and the Plague Hulk's
     * cloud carry a null source. Observation only (the balance simulator's HEAVY REPORT reads it).
     */
    private int hazardDamageToEnemies = 0;

    /** Running total of hazard DoT damage ticked into enemies (see the field note). */
    public int getHazardDamageToEnemies() {
        return hazardDamageToEnemies;
    }

    private EventTextSystem eventTextSystem;
    // Nullable — wired by World. When present, each enemy DoT tick spawns a small colored
    // spark puff so burning/poison/bleed reads as an active, ticking affliction.
    private ImpactEffectSystem impactEffectSystem;

    public void setEventTextSystem(EventTextSystem system) {
        this.eventTextSystem = system;
    }

    public void setImpactEffectSystem(ImpactEffectSystem system) {
        this.impactEffectSystem = system;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Applies a status effect to a host, obeying resist/immunity rules and stack modes.
     *
     * @param host      Player or Enemy receiving the effect
     * @param type      which status type
     * @param turns     base duration in world turns (before resist scaling)
     * @param magnitude base potency (fire damage/turn, poison damage/stack, bonus % for Empowered)
     * @param source    who inflicted it — used for XP attribution if DoT delivers the kill
     */
    public void apply(StatusHost host, StatusType type, int turns, int magnitude, Object source) {
        if (type == StatusType.POISONED) {
            // Poison has no timer of its own: it is a stack count that decays by 1 a turn (order 4, H10).
            applyPoisonStacks(host, 1, magnitude, source);
            return;
        }
        StatusResistance resistance = host.getStatusResistance();
        if (resistance.isImmune(type)) {
            return;
        }

        int effectiveDuration  = Math.max(1, Math.round(turns * resistance.durationMultiplier(type)));
        int effectiveMagnitude = Math.round(magnitude * resistance.damageMultiplier(type));

        StatusEffect existing = host.getActiveEffects().get(type);
        StackMode stackMode   = stackModeFor(type);

        if (!existing.isActive()) {
            existing.remainingTurns = effectiveDuration;
            existing.magnitude      = effectiveMagnitude;
            existing.stacks         = 1;
            existing.source         = source;
        } else {
            switch (stackMode) {
                case STACK_MAGNITUDE:
                    existing.stacks         = Math.min(existing.stacks + 1, stackCapFor(type));
                    existing.remainingTurns = Math.max(existing.remainingTurns, effectiveDuration);
                    break;
                case REFRESH_DURATION:
                    existing.remainingTurns = Math.max(existing.remainingTurns, effectiveDuration);
                    existing.magnitude      = Math.max(existing.magnitude, effectiveMagnitude);
                    break;
                case REPLACE_IF_LONGER:
                    if (effectiveDuration > existing.remainingTurns) {
                        existing.remainingTurns = effectiveDuration;
                        existing.magnitude      = effectiveMagnitude;
                        existing.source         = source;
                    }
                    break;
            }
        }
    }

    /**
     * Applies one STACK of a stacking DoT (balance-overhaul order 3, W3 — the Incinerator burn). The rule,
     * chosen as the simplest correct one (no per-stack timers, zero allocation):
     *   - first application: stacks = 1, magnitude = the per-stack value, timer = duration;
     *   - each further application: stacks + 1 (capped at {@code maxStacks}), the SHARED timer refreshed to
     *     the longer of the two, the per-stack magnitude the larger of the two, the source re-attributed;
     *   - a tick deals magnitude x stacks; all stacks expire together (POISON is the exception: it
     *     decays by one stack a turn, see {@link #applyPoisonStacks}).
     * Immunity and resistance scale duration and magnitude exactly as {@link #apply} does. A plain
     * {@link #apply} on an already-stacked effect only refreshes it (REFRESH_DURATION) — the stack count
     * grows only through this method.
     */
    public void applyStacking(StatusHost host, StatusType type, int turns, int magnitudePerStack,
                              int maxStacks, Object source) {
        StatusResistance resistance = host.getStatusResistance();
        if (resistance.isImmune(type)) {
            return;
        }
        int effectiveDuration  = Math.max(1, Math.round(turns * resistance.durationMultiplier(type)));
        int effectiveMagnitude = Math.round(magnitudePerStack * resistance.damageMultiplier(type));
        StatusEffect existing = host.getActiveEffects().get(type);
        if (!existing.isActive()) {
            existing.remainingTurns = effectiveDuration;
            existing.magnitude      = effectiveMagnitude;
            existing.stacks         = 1;
        } else {
            existing.stacks         = Math.min(Math.max(1, existing.stacks) + 1, Math.max(1, maxStacks));
            existing.remainingTurns = Math.max(existing.remainingTurns, effectiveDuration);
            existing.magnitude      = Math.max(existing.magnitude, effectiveMagnitude);
        }
        existing.source = source;
    }

    /**
     * WILDFIRE's spread (balance-overhaul order 5, D5): adds a dead burning enemy's {@code stacks} to the host's
     * BURNING ({@link GameMath#wildfireSpreadStacks}, capped at {@code maxStacks}), resets the shared timer to
     * {@code turns} (a full player burn) and keeps the larger per-stack magnitude, re-attributed to
     * {@code source}. Immunity blocks it; resistance scales duration and magnitude exactly as
     * {@link #applyStacking} does.
     */
    public void spreadBurnStacks(StatusHost host, int stacks, int magnitudePerStack, int turns, int maxStacks,
                                 Object source) {
        StatusResistance resistance = host.getStatusResistance();
        if (resistance.isImmune(StatusType.BURNING) || stacks <= 0) {
            return;
        }
        int effectiveDuration  = Math.max(1, Math.round(turns * resistance.durationMultiplier(StatusType.BURNING)));
        int effectiveMagnitude = Math.round(magnitudePerStack * resistance.damageMultiplier(StatusType.BURNING));
        StatusEffect existing  = host.getActiveEffects().get(StatusType.BURNING);
        int previousStacks     = existing.isActive() ? Math.max(1, existing.stacks) : 0;
        existing.stacks         = GameMath.wildfireSpreadStacks(previousStacks, stacks, maxStacks);
        existing.remainingTurns = effectiveDuration;
        existing.magnitude      = previousStacks == 0 ? effectiveMagnitude : Math.max(existing.magnitude, effectiveMagnitude);
        existing.source         = source;
    }

    /**
     * Applies {@code stacksToAdd} POISON stacks (balance-overhaul order 4, H10). The per-stack magnitude is
     * resolved HERE from the host's max HP ({@link GameMath#statusTickDamage}, POISON_TICK_FRACTION) so it
     * never goes stale with depth, and stored in the effect so the tick and the HUD keep reading
     * {@code magnitude x stacks}. Stacks add up to POISON_MAX_STACKS and DECAY by 1 after each tick;
     * {@code remainingTurns} is kept equal to the stack count, which is exactly how many ticks are left
     * (the HUD's turn count stays truthful). Immunity blocks it; resistance scales the per-stack damage.
     */
    public void applyPoisonStacks(StatusHost host, int stacksToAdd, Object source) {
        applyPoisonStacks(host, stacksToAdd,
                GameMath.statusTickDamage(EffectConstants.POISON_TICK_FRACTION, host.getStatusMaxHealth(), 1),
                source);
    }

    private void applyPoisonStacks(StatusHost host, int stacksToAdd, int magnitudePerStack, Object source) {
        StatusResistance resistance = host.getStatusResistance();
        if (resistance.isImmune(StatusType.POISONED) || stacksToAdd <= 0) {
            return;
        }
        int effectiveMagnitude = Math.round(magnitudePerStack * resistance.damageMultiplier(StatusType.POISONED));
        StatusEffect existing  = host.getActiveEffects().get(StatusType.POISONED);
        int previousStacks     = existing.isActive() ? Math.max(1, existing.stacks) : 0;
        existing.stacks         = Math.min(previousStacks + stacksToAdd, EffectConstants.POISON_MAX_STACKS);
        existing.magnitude      = previousStacks == 0 ? effectiveMagnitude : Math.max(existing.magnitude, effectiveMagnitude);
        existing.remainingTurns = existing.stacks;
        existing.source         = source;
    }

    /**
     * Applies a BURNING status whose per-turn damage is BURN_TICK_FRACTION of the host's max HP (status /
     * hazard fire; weapon burns keep their own R-ROLE-fitted magnitudes through {@link #apply}).
     * {@code damageMultiplier} lets the caller scale it (the enemy-side fire-tile multiplier and matchup).
     */
    public void applyFractionBurn(StatusHost host, int turns, float damageMultiplier, Object source) {
        int perTurn = Math.max(1, Math.round(
                GameMath.statusTickDamage(EffectConstants.BURN_TICK_FRACTION, host.getStatusMaxHealth(), 1)
                        * damageMultiplier));
        apply(host, StatusType.BURNING, turns, perTurn, source);
    }

    /**
     * Ticks all active status effects on the player and all living enemies.
     * Called once per world turn from StatusEffectSubscriber, before enemy AI (EnemyTurnSubscriber).
     */
    public void tickAll(Player player, EnemyManager enemyManager) {
        tickPlayer(player);
        tickEnemies(enemyManager);
    }

    /** Clears all player-held status effects. Called on level transition (fresh floor). */
    public void clearPlayerEffects(Player player) {
        for (StatusType type : STATUS_TYPES) {
            player.getActiveEffects().get(type).reset();
        }
    }

    // -------------------------------------------------------------------------
    // Internal tick logic
    // -------------------------------------------------------------------------

    private void tickPlayer(Player player) {
        for (StatusType type : STATUS_TYPES) {
            StatusEffect effect = player.getActiveEffects().get(type);
            if (!effect.isActive()) continue;

            applyPlayerTickEffect(player, effect, type);

            if (!advanceEffectTimer(effect)
                    && eventTextSystem != null && type == StatusType.STUNNED) {
                eventTextSystem.spawnWithColor("STUN CLEARED", EventTextSystem.COLOR_WHITE);
            }
        }
    }

    private void tickEnemies(EnemyManager enemyManager) {
        pendingDoTDeathCount = 0;
        List<Enemy> enemies  = enemyManager.getEnemies();

        for (int enemyIndex = 0; enemyIndex < enemies.size(); enemyIndex++) {
            Enemy enemy = enemies.get(enemyIndex);
            if (!enemy.isAlive()) continue;
            // Block decay (strategy-combat-order-3) shares the status phase for deterministic ordering:
            // an enemy's Block, gained on its previous defend turn, expires here one turn later — after
            // the player's reacting turn has already resolved its damage against the active shield.
            enemy.decayBlock();
            tickEnemy(enemy);
        }

        for (int deathIndex = 0; deathIndex < pendingDoTDeathCount; deathIndex++) {
            Enemy dead = pendingDoTDeaths[deathIndex];
            enemyManager.processDoTKill(dead);
            // Cleared only AFTER the kill is processed, so the death sees the burn that killed it (WILDFIRE
            // spreads a dying enemy's stacks — balance-overhaul order 5).
            clearAllEffects(dead);
            pendingDoTDeaths[deathIndex] = null;
        }
    }

    private void tickEnemy(Enemy enemy) {
        for (StatusType type : STATUS_TYPES) {
            StatusEffect effect = enemy.getActiveEffects().get(type);
            if (!effect.isActive()) continue;

            if (effect.source == null) {
                if (type == StatusType.BURNING)       hazardDamageToEnemies += effect.magnitude * Math.max(1, effect.stacks);
                else if (type == StatusType.POISONED) hazardDamageToEnemies += effect.magnitude * effect.stacks;
            }
            applyEnemyTickEffect(enemy, effect, type);

            if (!enemy.isAlive()) {
                // Killing tick: the death burst (fired by processDoTKill) carries the feedback,
                // so no tick puff is spawned at the corpse.
                if (pendingDoTDeathCount < MAX_PENDING_DOT_DEATHS) {
                    pendingDoTDeaths[pendingDoTDeathCount++] = enemy;
                } else {
                    clearAllEffects(enemy);   // overflow: never processed as a kill, so clear it here as before
                }
                return;
            }

            spawnDoTTickFeedback(enemy, type);

            advanceEffectTimer(effect);
        }
    }

    /**
     * Advances one effect's clock after its tick. POISON decays by ONE STACK (order 4, H10) and keeps
     * remainingTurns equal to the stacks left; everything else loses one turn. Resets the effect at zero.
     *
     * @return true while the effect is still active
     */
    private static boolean advanceEffectTimer(StatusEffect effect) {
        if (effect.type == StatusType.POISONED) {
            effect.stacks--;
            effect.remainingTurns = effect.stacks;
        } else {
            effect.remainingTurns--;
        }
        if (effect.remainingTurns <= 0) {
            effect.reset();
            return false;
        }
        return true;
    }

    /**
     * Spawns a small colored spark puff at the enemy for damage-over-time types (Burning,
     * Poison, Bleed) so the affliction visibly ticks each world turn. No-op for control
     * effects (Stun/Blind/Slow/Empowered) and when no ImpactEffectSystem is wired.
     * Projection uses the current player state pushed by World earlier this frame.
     */
    private void spawnDoTTickFeedback(Enemy enemy, StatusType type) {
        if (impactEffectSystem == null) return;
        float red, green, blue;
        switch (type) {
            case BURNING:  red = EffectConstants.DOT_FIRE_R;   green = EffectConstants.DOT_FIRE_G;   blue = EffectConstants.DOT_FIRE_B;   break;
            case POISONED: red = EffectConstants.DOT_POISON_R; green = EffectConstants.DOT_POISON_G; blue = EffectConstants.DOT_POISON_B; break;
            case BLEED:    red = EffectConstants.DOT_BLEED_R;  green = EffectConstants.DOT_BLEED_G;  blue = EffectConstants.DOT_BLEED_B;  break;
            default:       return;
        }
        impactEffectSystem.spawnColoredSparks(enemy.tileColumn, enemy.tileRow, enemy.type.heightMultiplier(),
                red, green, blue,
                EffectConstants.DOT_TICK_SPARK_COUNT,
                EffectConstants.DOT_TICK_SPEED_MIN, EffectConstants.DOT_TICK_SPEED_MAX,
                EffectConstants.DOT_TICK_LIFE_SECONDS);
    }

    private static void applyPlayerTickEffect(Player player, StatusEffect effect, StatusType type) {
        switch (type) {
            case BURNING:
                player.applyDoTDamage(effect.magnitude);
                break;
            case POISONED:
                player.applyDoTDamage(effect.magnitude * effect.stacks);
                break;
            case BLEED:
                player.applyDoTDamage(effect.magnitude);
                break;
            case STUNNED:
                player.setNextActionStunned(true);
                break;
            case BLINDED:
            case SLOWED:
            case EMPOWERED:
            case VULNERABLE:
            case WEAK:
            case EXPOSED:
                // These effects work by the renderer/controller/mitigation-pipeline reading the active
                // EnumMap; no per-tick action is needed beyond keeping the remaining turns count. The
                // order-6 powers (VULNERABLE/WEAK/EXPOSED) are passive multipliers/flags exactly like
                // EMPOWERED — they deal no tick damage of their own.
                break;
        }
    }

    private static void applyEnemyTickEffect(Enemy enemy, StatusEffect effect, StatusType type) {
        switch (type) {
            case BURNING:
                // Stacked burns (Incinerator, order 3 W3) tick magnitude x stacks; any other burn has 1.
                enemy.applyDoTDamage(effect.magnitude * Math.max(1, effect.stacks));
                break;
            case POISONED:
                enemy.applyDoTDamage(effect.magnitude * effect.stacks);
                break;
            case BLEED:
                enemy.applyDoTDamage(effect.magnitude);
                break;
            case STUNNED:
                enemy.skipNextAction = true;
                break;
            case BLINDED:
            case SLOWED:
            case EMPOWERED:
            case VULNERABLE:
            case WEAK:
            case EXPOSED:
                // Passive order-6 powers — read by the mitigation pipeline, no per-tick action.
                break;
        }
    }

    private static void clearAllEffects(StatusHost host) {
        for (StatusType type : STATUS_TYPES) {
            host.getActiveEffects().get(type).reset();
        }
    }

    /*
     * Formula: stackModeFor
     * Derivation: one-to-one mapping from design spec (roguelike_order_8 + strategy-combat-order-6):
     *   Poison     → STACK_MAGNITUDE (each application adds +1 stack)
     *   Vulnerable → STACK_MAGNITUDE (order-6: each mark adds a stack up to VULNERABLE_MAX_STACKS so
     *                a setup build can layer marks, but a small cap keeps it off a boss's neck)
     *   Burning    → REFRESH_DURATION (re-application resets to the longer timer, no stack; the
     *                Incinerator adds stacks through applyStacking — balance-overhaul order 3, W3)
     *   Empowered  → REFRESH_DURATION (re-stim refreshes, does not stack to ×2.25)
     *   Weak       → REFRESH_DURATION (order-6: re-application refreshes the debuff window)
     *   Exposed    → REFRESH_DURATION (order-6: a one-hit flag; re-applying keeps it armed longer)
     *   All control effects → REPLACE_IF_LONGER (keep whichever lasts longer)
     * Edge cases: default branch covers any future type added before this switch is updated.
     */
    private static StackMode stackModeFor(StatusType type) {
        switch (type) {
            case POISONED:
            case VULNERABLE: return StackMode.STACK_MAGNITUDE;
            case BLEED:
            case BURNING:
            case EMPOWERED:
            case WEAK:
            case EXPOSED:    return StackMode.REFRESH_DURATION;
            case STUNNED:
            case BLINDED:
            case SLOWED:
            default:         return StackMode.REPLACE_IF_LONGER;
        }
    }

    /*
     * Formula: stackCapFor
     * Derivation: the per-type ceiling for STACK_MAGNITUDE effects. Poison keeps its own generous
     *   cap; Vulnerable gets the tight VULNERABLE_MAX_STACKS (order-6 balance contract) so a stacking
     *   setup can multiply a chaff's TTK down by a turn without trivializing a boss's eHP.
     * Edge cases: any non-stacking type returns 1 (the default) — harmless, since the STACK_MAGNITUDE
     *   branch is the only caller and only STACK_MAGNITUDE types ever reach it.
     */
    private static int stackCapFor(StatusType type) {
        switch (type) {
            case POISONED:   return EffectConstants.POISON_MAX_STACKS;
            case VULNERABLE: return EffectConstants.VULNERABLE_MAX_STACKS;
            default:         return 1;
        }
    }
}
