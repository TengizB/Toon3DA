package ge.tbegvadze.toon3d.entity;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.progression.PlayerStats;
import ge.tbegvadze.toon3d.render.Renderable;
import ge.tbegvadze.toon3d.status.StatusEffect;
import ge.tbegvadze.toon3d.status.StatusHost;
import ge.tbegvadze.toon3d.status.StatusResistance;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.ItemConstants;
import ge.tbegvadze.toon3d.util.ProgressionConstants;

import java.util.EnumMap;

public class Player implements Renderable, Disposable, StatusHost {

    public float positionX;
    public float positionY;
    public float directionX; // unit direction vector — always length 1
    public float directionY;
    public float fieldOfViewRadians;

    private int health;
    private int maxHealth;
    private int armor;
    private int maxArmor;

    private final ShapeRenderer shapes;
    private PlayerDamageListener damageListener;
    private GuardHitListener     guardHitListener;

    /**
     * True while the marine is braced in the directional GUARD stance (strategy-combat-order-4).
     * Set/cleared exclusively by PlayerController (owns the guard lifecycle); read here by
     * {@link #applyDirectionalDamage} and by the shield-arc HUD overlay. Persists across the enemy
     * turn it buys and until the player's next action.
     */
    private boolean guarding = false;

    /**
     * Stat system — injected by World after construction.
     * Null until wired; all stat checks guard for null and treat a missing stats
     * object as if every effective stat value is zero (no bonuses, no dodge).
     */
    private PlayerStats playerStats;

    // Status effect storage — pre-allocated at construction, never replaced
    private final EnumMap<StatusType, StatusEffect> activeStatusEffects;
    private final StatusResistance statusResistance = StatusResistance.defaultResistance();

    /**
     * Set true by StatusEffectController when STUNNED ticks.
     * PlayerController reads and clears this on the next pollInput() call with any input.
     */
    private boolean isNextActionStunned = false;

    public Player(float positionX, float positionY, float directionX, float directionY) {
        this.positionX          = positionX;
        this.positionY          = positionY;
        this.directionX         = directionX;
        this.directionY         = directionY;
        this.fieldOfViewRadians = Constants.PLAYER_FIELD_OF_VIEW_RADIANS;
        this.shapes             = new ShapeRenderer();
        this.maxHealth          = ItemConstants.PLAYER_MAX_HEALTH;
        this.health             = this.maxHealth;
        this.maxArmor           = ItemConstants.PLAYER_MAX_ARMOR;
        this.armor              = 0;
        this.activeStatusEffects = buildEffectsMap();
    }

    private static EnumMap<StatusType, StatusEffect> buildEffectsMap() {
        EnumMap<StatusType, StatusEffect> map = new EnumMap<>(StatusType.class);
        for (StatusType type : StatusType.values()) {
            map.put(type, new StatusEffect(type));
        }
        return map;
    }

    // -------------------------------------------------------------------------
    // StatusHost implementation
    // -------------------------------------------------------------------------

    @Override
    public EnumMap<StatusType, StatusEffect> getActiveEffects() { return activeStatusEffects; }

    @Override
    public StatusResistance getStatusResistance() { return statusResistance; }

    /**
     * Damage-over-time entry point: skips the AGILITY dodge roll (DoT is unavoidable)
     * but otherwise runs the same pipeline as {@link #applyDamage}:
     *   (a) Bulwark temp armor consumed first (armor protects against all damage types).
     *   (b) Regular armour absorption.
     *   (c) TOUGHNESS flat reduction, floored at TGH_MIN_DAMAGE.
     *   (d) Subtract from health.
     * Used exclusively by StatusEffectController for Burning / Poison tick damage.
     */
    @Override
    public void applyDoTDamage(int amount) {
        if (ProgressionConstants.debug) return;

        // (a) Bulwark Rounds temp armor — armor protects against DoT the same as direct hits.
        int remainingDamage = amount;
        if (playerStats != null) {
            int tempAbsorbed = playerStats.consumeTempArmor(remainingDamage);
            remainingDamage -= tempAbsorbed;
        }
        if (remainingDamage <= 0) return;

        // (b) Regular armour absorption.
        int armorAbsorbed  = GameMath.armorAbsorb(remainingDamage, armor, ItemConstants.ARMOUR_ABSORB_FRACTION);
        armor              = Math.max(0, armor - armorAbsorbed);
        int hpBoundDamage  = remainingDamage - armorAbsorbed;

        // (c) TOUGHNESS flat reduction.
        if (playerStats != null) {
            int flatReduction = playerStats.getFlatDamageReduction();
            hpBoundDamage = Math.max(GameBalance.TGH_MIN_DAMAGE, hpBoundDamage - flatReduction);
        }

        // (d) Apply to health.
        health = Math.max(0, health - hpBoundDamage);
        if (damageListener != null && hpBoundDamage > 0) {
            damageListener.onPlayerDamaged(hpBoundDamage);
        }
    }

    // -------------------------------------------------------------------------
    // Status effect helpers — read by PlayerController and renderers
    // -------------------------------------------------------------------------

    /** Called by StatusEffectController each turn the STUNNED effect is active. */
    public void setNextActionStunned(boolean stunned) { this.isNextActionStunned = stunned; }

    /**
     * Returns true when the player's next action should be blocked by a stun.
     * PlayerController calls this and clears it when any input is consumed.
     */
    public boolean hasActiveStun() { return isNextActionStunned; }

    /** Clears the stun flag after it has been consumed by PlayerController. */
    public void clearStunFlag() { isNextActionStunned = false; }

    /**
     * Returns the effective field-of-view radians used by every world renderer.
     *
     * <p>Historically BLINDED narrowed the raycast FOV to a tunnel, but shrinking the FOV zooms the
     * DDA projection: at 30° vs the default 90° every wall stripe is ~3× taller, so the whole view
     * visibly STRETCHES and reads as broken rather than "blinded". Blindness now communicates purely
     * through the heavy dark screen-edge vignette (StatusEffectVignetteRenderer,
     * {@link EffectConstants#STATUS_BLIND_VIGNETTE_ALPHA}) — the geometry stays undistorted, so the
     * player still loses peripheral vision without the FOV warp. World.render() keeps calling this so
     * any future non-distorting FOV modifier has a single hook.
     */
    public float getEffectiveFovRadians() {
        return fieldOfViewRadians;
    }

    /**
     * Returns the action-duration slow multiplier.
     * 1.0 normally; SLOW_FACTOR (2.0) while SLOWED is active.
     * PlayerController multiplies its move/rotate duration by this value.
     */
    public float getSlowMultiplier() {
        StatusEffect slowEffect = activeStatusEffects.get(StatusType.SLOWED);
        return (slowEffect != null && slowEffect.isActive()) ? EffectConstants.SLOW_FACTOR : 1.0f;
    }

    /**
     * Returns the empowered outgoing-damage multiplier.
     * 1.0 normally; scales with EMPOWERED_DAMAGE_PERCENT while the buff is active.
     */
    public float getEmpoweredDamageMultiplier() {
        StatusEffect empoweredEffect = activeStatusEffects.get(StatusType.EMPOWERED);
        if (empoweredEffect != null && empoweredEffect.isActive()) {
            return 1.0f + empoweredEffect.getMagnitude() / 100.0f;
        }
        return 1.0f;
    }

    /**
     * Returns the WEAK outgoing-damage multiplier (strategy-combat-order-6): {@code 1 - P/100} while a
     * WEAK debuff (e.g. an enemy's DEBUFF_PLAYER) is active, else 1.0. EnemyManager folds this into
     * every weapon hit the player lands, so a Weakened marine visibly deals less — the symmetric
     * counterpart to Weakening an enemy. Floored at 0 by {@link GameMath#weakDamageMultiplier}.
     */
    public float getWeakDamageMultiplier() {
        StatusEffect weakEffect = activeStatusEffects.get(StatusType.WEAK);
        return GameMath.weakDamageMultiplier(weakEffect != null && weakEffect.isActive(),
                EffectConstants.WEAK_DAMAGE_PERCENT);
    }

    public void setPlayerDamageListener(PlayerDamageListener listener) {
        this.damageListener = listener;
    }

    /** Wires the directional GUARDED/FLANKED feedback fired by {@link #applyDirectionalDamage}. */
    public void setGuardHitListener(GuardHitListener listener) {
        this.guardHitListener = listener;
    }

    /**
     * Enters or leaves the directional GUARD stance (strategy-combat-order-4).
     * Called only by PlayerController, which owns the guard lifecycle.
     */
    public void setGuarding(boolean guarding) { this.guarding = guarding; }

    /** True while braced in the GUARD stance — read by the shield-arc HUD overlay. */
    public boolean isGuarding() { return guarding; }

    /**
     * Directional damage entry point for enemy attacks (strategy-combat-order-4).
     * When the player is GUARDING, scales the incoming amount by the facing-arc multiplier
     * (front = big reduction; side/back = full damage) BEFORE the normal
     * {@link #applyDamage} pipeline runs, and fires the GUARDED/FLANKED feedback. When not
     * guarding this is a plain pass-through to {@link #applyDamage}.
     *
     * @param amount          raw incoming damage (already scaled for depth/charge by the caller).
     * @param attackerWorldX  attacker (or ranged lane origin) X in world units.
     * @param attackerWorldY  attacker (or ranged lane origin) Y in world units.
     */
    public void applyDirectionalDamage(int amount, float attackerWorldX, float attackerWorldY) {
        if (!guarding) {
            applyDamage(amount);
            return;
        }
        float multiplier = GameMath.guardFacingMultiplier(
                directionX, directionY, positionX, positionY, attackerWorldX, attackerWorldY,
                GameBalance.GUARD_FRONT_MULTIPLIER, GameBalance.GUARD_SIDE_MULTIPLIER,
                GameBalance.GUARD_BACK_MULTIPLIER,
                GameBalance.GUARD_FRONT_HALF_ANGLE_DEGREES, GameBalance.GUARD_BACK_HALF_ANGLE_DEGREES);
        int guardedDamage = Math.max(0, Math.round(amount * multiplier));
        // A reduced hit means it landed in the protected front arc; side/back hits pass at full damage.
        boolean frontArc = guardedDamage < amount;
        if (guardHitListener != null) {
            guardHitListener.onGuardedHit(frontArc, guardedDamage, attackerWorldX, attackerWorldY);
        }
        applyDamage(guardedDamage);
    }

    /**
     * Injects the stat system so damage resolution can consult dodge chance and
     * flat damage reduction.  Call once from World after both objects are created.
     * Applies the initial TOUGHNESS max-health bonus immediately so the player's
     * HP pool reflects the difficulty-seeded stat from turn one.
     * Persistent across level rebuilds — do not re-inject on every floor.
     */
    public void setPlayerStats(PlayerStats stats) {
        this.playerStats = stats;
        if (stats != null) {
            int bonus = stats.getMaxHealthBonus();
            if (bonus > 0) {
                // Raise the pool without auto-healing (matches spec: "does NOT auto-heal").
                maxHealth += bonus;
            }
        }
    }

    /** Returns the injected stat system, or null if not yet wired. */
    public PlayerStats getPlayerStats() {
        return playerStats;
    }

    /**
     * Applies incoming damage through the full resolution pipeline:
     *   (a) AGILITY dodge roll     — on success damage = 0, pipeline ends.
     *   (b) Bulwark temp armor     — consumed before regular armor; early-exit if absorbed all.
     *   (c) Regular armour pool    — fraction soaked by AR pool.
     *   (d) TOUGHNESS flat reduction — shaves N off the HP-bound remainder,
     *       floored at TGH_MIN_DAMAGE so chip damage still threatens turtles.
     *   (e) Subtract from health.
     *
     * Strict pipeline order matches the spec in roguelike_order_6_player_stats_and_attributes.txt.
     */
    public void applyDamage(int amount) {
        if (ProgressionConstants.debug) return;

        // (a) AGILITY dodge roll — checked before armour or toughness.
        if (playerStats != null) {
            float dodgeChance = playerStats.getDodgeChance();
            if (dodgeChance > 0f && MathUtils.random() < dodgeChance) {
                // Dodge: entire hit negated; listener not called (no HP change).
                return;
            }
        }

        // (b) Bulwark Rounds temp armor — consumed before the regular armor pool.
        int remainingDamage = amount;
        if (playerStats != null) {
            int tempAbsorbed = playerStats.consumeTempArmor(remainingDamage);
            remainingDamage -= tempAbsorbed;
        }
        // Temp armor absorbed everything — no HP impact, no regular armor drain.
        if (remainingDamage <= 0) return;

        // (c) Regular armour absorption.
        int armorAbsorbed = GameMath.armorAbsorb(remainingDamage, armor, ItemConstants.ARMOUR_ABSORB_FRACTION);
        armor = Math.max(0, armor - armorAbsorbed);
        int hpBoundDamage = remainingDamage - armorAbsorbed;

        // (d) TOUGHNESS flat reduction applied to the HP-bound remainder.
        if (playerStats != null) {
            int flatReduction = playerStats.getFlatDamageReduction();
            hpBoundDamage = Math.max(GameBalance.TGH_MIN_DAMAGE, hpBoundDamage - flatReduction);
        }

        // (e) Apply to health.
        health = Math.max(0, health - hpBoundDamage);
        if (damageListener != null && hpBoundDamage > 0) {
            damageListener.onPlayerDamaged(hpBoundDamage);
        }
    }

    public void applyHealing(int amount) {
        health = Math.min(maxHealth, health + amount);
    }

    /**
     * Permanently adjusts the player's maximum HP by a signed delta (level-up upgrade cards).
     * A positive delta also heals the player by that amount so the bonus HP is not wasted; a
     * negative delta (a trade-off card's cost) lowers the cap and clamps current HP down to it,
     * never below 1 — a max-HP cut must never directly kill the player.
     */
    public void adjustMaxHealth(int delta) {
        maxHealth = Math.max(1, maxHealth + delta);
        if (delta > 0) {
            health = Math.min(maxHealth, health + delta);
        } else {
            health = Math.max(1, Math.min(health, maxHealth));
        }
    }

    /**
     * Permanently adjusts the player's maximum armour pool by a signed delta (upgrade cards).
     * A positive delta also grants that much armour immediately; a negative delta (a trade-off
     * card's cost) lowers the cap and clamps the current armour pool down to it (floored at 0).
     */
    public void adjustMaxArmor(int delta) {
        maxArmor = Math.max(0, maxArmor + delta);
        if (delta > 0) {
            armor = Math.min(maxArmor, armor + delta);
        } else {
            armor = Math.min(armor, maxArmor);
        }
    }

    public boolean isDead()  { return health <= 0; }

    @Override
    public boolean isAlive() { return health > 0; }

    public int getHealth()    { return health; }
    public int getMaxHealth() { return maxHealth; }

    public int getArmor()    { return armor; }
    public int getMaxArmor() { return maxArmor; }

    public void applyArmor(int amount) {
        armor = Math.max(0, Math.min(maxArmor, armor + amount));
    }

    public float getHealthFraction() { return maxHealth == 0 ? 0f : (float) health / maxHealth; }
    public float getArmorFraction()  { return maxArmor  == 0 ? 0f : (float) armor  / maxArmor;  }

    @Override
    public void render(OrthographicCamera camera) {
        float dotX = Constants.MINI_MAP_ORIGIN_X + Constants.MINI_MAP_CENTER_X;
        float dotY = Constants.MINI_MAP_ORIGIN_Y + Constants.MINI_MAP_CENTER_Y;

        shapes.setProjectionMatrix(camera.combined);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Color.GREEN);
        shapes.circle(dotX, dotY, Constants.MINI_MAP_PLAYER_DOT_RADIUS);

        // Facing wedge — a large filled triangle pointing in directionX/directionY.
        // Replaces the old ~3px facing line, which was unreadable on a phone screen.
        float wedgeTipX = GameMath.facingWedgeTipX(dotX, directionX, Constants.MINI_MAP_FACING_WEDGE_LENGTH);
        float wedgeTipY = GameMath.facingWedgeTipY(dotY, directionY, Constants.MINI_MAP_FACING_WEDGE_LENGTH);
        float wedgeBaseLeftX = GameMath.facingWedgeBaseLeftX(dotX, directionX, directionY,
                Constants.MINI_MAP_FACING_WEDGE_BACK, Constants.MINI_MAP_FACING_WEDGE_HALF_WIDTH);
        float wedgeBaseLeftY = GameMath.facingWedgeBaseLeftY(dotY, directionX, directionY,
                Constants.MINI_MAP_FACING_WEDGE_BACK, Constants.MINI_MAP_FACING_WEDGE_HALF_WIDTH);
        float wedgeBaseRightX = GameMath.facingWedgeBaseRightX(dotX, directionX, directionY,
                Constants.MINI_MAP_FACING_WEDGE_BACK, Constants.MINI_MAP_FACING_WEDGE_HALF_WIDTH);
        float wedgeBaseRightY = GameMath.facingWedgeBaseRightY(dotY, directionX, directionY,
                Constants.MINI_MAP_FACING_WEDGE_BACK, Constants.MINI_MAP_FACING_WEDGE_HALF_WIDTH);
        shapes.setColor(Color.GREEN);
        shapes.triangle(wedgeTipX, wedgeTipY, wedgeBaseLeftX, wedgeBaseLeftY, wedgeBaseRightX, wedgeBaseRightY);
        shapes.end();
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
