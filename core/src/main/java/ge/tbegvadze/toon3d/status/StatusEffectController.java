package ge.tbegvadze.toon3d.status;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.render.EventTextSystem;
import ge.tbegvadze.toon3d.util.EffectConstants;

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

    private EventTextSystem eventTextSystem;

    public void setEventTextSystem(EventTextSystem system) {
        this.eventTextSystem = system;
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
                    existing.stacks         = Math.min(existing.stacks + 1, EffectConstants.POISON_MAX_STACKS);
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

            effect.remainingTurns--;
            if (effect.remainingTurns <= 0) {
                effect.reset();
                if (eventTextSystem != null && type == StatusType.STUNNED) {
                    eventTextSystem.spawnWithColor("STUN CLEARED", EventTextSystem.COLOR_WHITE);
                }
            }
        }
    }

    private void tickEnemies(EnemyManager enemyManager) {
        pendingDoTDeathCount = 0;
        List<Enemy> enemies  = enemyManager.getEnemies();

        for (int enemyIndex = 0; enemyIndex < enemies.size(); enemyIndex++) {
            Enemy enemy = enemies.get(enemyIndex);
            if (!enemy.isAlive()) continue;
            tickEnemy(enemy);
        }

        for (int deathIndex = 0; deathIndex < pendingDoTDeathCount; deathIndex++) {
            enemyManager.processDoTKill(pendingDoTDeaths[deathIndex]);
            pendingDoTDeaths[deathIndex] = null;
        }
    }

    private void tickEnemy(Enemy enemy) {
        for (StatusType type : STATUS_TYPES) {
            StatusEffect effect = enemy.getActiveEffects().get(type);
            if (!effect.isActive()) continue;

            applyEnemyTickEffect(enemy, effect, type);

            if (!enemy.isAlive()) {
                if (pendingDoTDeathCount < MAX_PENDING_DOT_DEATHS) {
                    pendingDoTDeaths[pendingDoTDeathCount++] = enemy;
                }
                clearAllEffects(enemy);
                return;
            }

            effect.remainingTurns--;
            if (effect.remainingTurns <= 0) {
                effect.reset();
            }
        }
    }

    private static void applyPlayerTickEffect(Player player, StatusEffect effect, StatusType type) {
        switch (type) {
            case BURNING:
                player.applyDoTDamage(effect.magnitude);
                break;
            case POISONED:
                player.applyDoTDamage(effect.magnitude * effect.stacks);
                break;
            case STUNNED:
                player.setNextActionStunned(true);
                break;
            case BLINDED:
            case SLOWED:
            case EMPOWERED:
                // These effects work by the renderer/controller reading the active EnumMap;
                // no per-tick action is needed beyond keeping the remaining turns count.
                break;
        }
    }

    private static void applyEnemyTickEffect(Enemy enemy, StatusEffect effect, StatusType type) {
        switch (type) {
            case BURNING:
                enemy.applyDoTDamage(effect.magnitude);
                break;
            case POISONED:
                enemy.applyDoTDamage(effect.magnitude * effect.stacks);
                break;
            case STUNNED:
                enemy.skipNextAction = true;
                break;
            case BLINDED:
            case SLOWED:
            case EMPOWERED:
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
     * Derivation: one-to-one mapping from design spec (roguelike_order_8):
     *   Poison   → STACK_MAGNITUDE (each application adds +1 stack)
     *   Burning  → REFRESH_DURATION (re-application resets to the longer timer, no stack)
     *   Empowered→ REFRESH_DURATION (re-stim refreshes, does not stack to ×2.25)
     *   All control effects → REPLACE_IF_LONGER (keep whichever lasts longer)
     * Edge cases: default branch covers any future type added before this switch is updated.
     */
    private static StackMode stackModeFor(StatusType type) {
        switch (type) {
            case POISONED:   return StackMode.STACK_MAGNITUDE;
            case BURNING:
            case EMPOWERED:  return StackMode.REFRESH_DURATION;
            case STUNNED:
            case BLINDED:
            case SLOWED:
            default:         return StackMode.REPLACE_IF_LONGER;
        }
    }
}
