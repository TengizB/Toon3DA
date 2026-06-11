package ge.tbegvadze.toon3d.entity;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.progression.PlayerStats;
import ge.tbegvadze.toon3d.render.Renderable;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;

public class Player implements Renderable, Disposable {

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

    /**
     * Stat system — injected by World after construction.
     * Null until wired; all stat checks guard for null and treat a missing stats
     * object as if every effective stat value is zero (no bonuses, no dodge).
     */
    private PlayerStats playerStats;

    public Player(float positionX, float positionY, float directionX, float directionY) {
        this.positionX          = positionX;
        this.positionY          = positionY;
        this.directionX         = directionX;
        this.directionY         = directionY;
        this.fieldOfViewRadians = Constants.PLAYER_FIELD_OF_VIEW_RADIANS;
        this.shapes             = new ShapeRenderer();
        this.maxHealth          = Constants.PLAYER_MAX_HEALTH;
        this.health             = this.maxHealth;
        this.maxArmor           = Constants.PLAYER_MAX_ARMOR;
        this.armor              = 0;
    }

    public void setPlayerDamageListener(PlayerDamageListener listener) {
        this.damageListener = listener;
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
     *   (a) AGILITY dodge roll  — on success damage = 0, pipeline ends.
     *   (b) Armour absorption   — fraction soaked by AR pool.
     *   (c) TOUGHNESS flat reduction — shaves N off the HP-bound remainder,
     *       floored at TGH_MIN_DAMAGE so chip damage still threatens turtles.
     *   (d) Subtract from health.
     *
     * Strict pipeline order matches the spec in roguelike_order_6_player_stats_and_attributes.txt.
     */
    public void applyDamage(int amount) {
        if (Constants.debug) return;

        // (a) AGILITY dodge roll — checked before armour or toughness.
        if (playerStats != null) {
            float dodgeChance = playerStats.getDodgeChance();
            if (dodgeChance > 0f && MathUtils.random() < dodgeChance) {
                // Dodge: entire hit negated; listener not called (no HP change).
                return;
            }
        }

        // (b) Armour absorption.
        int armorAbsorbed = GameMath.armorAbsorb(amount, armor, Constants.ARMOUR_ABSORB_FRACTION);
        armor = Math.max(0, armor - armorAbsorbed);
        int hpBoundDamage = amount - armorAbsorbed;

        // (c) TOUGHNESS flat reduction applied to the HP-bound remainder.
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

    public void applyHealing(int amount) {
        health = Math.min(maxHealth, health + amount);
    }

    /**
     * Permanently increases the player's maximum HP by the given amount and immediately
     * heals the player by the same amount so the bonus HP is not wasted.
     * Called when the player picks the HP_BOOST level-up reward.
     */
    public void increaseMaxHealth(int amount) {
        maxHealth += amount;
        health     = Math.min(maxHealth, health + amount);
    }

    /**
     * Permanently increases the player's maximum armour pool by the given amount and
     * immediately grants the bonus armour.
     * Called when the player picks the ARMOR_BOOST level-up reward.
     */
    public void increaseMaxArmor(int amount) {
        maxArmor += amount;
        armor     = Math.min(maxArmor, armor + amount);
    }

    public boolean isDead() {
        return health <= 0;
    }

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
        shapes.circle(dotX, dotY, Constants.MINI_MAP_PLAYER_RADIUS);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.RED);
        shapes.line(
            dotX,
            dotY,
            dotX + directionX * Constants.MINI_MAP_PLAYER_RADIUS,
            dotY + directionY * Constants.MINI_MAP_PLAYER_RADIUS
        );
        shapes.end();
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
