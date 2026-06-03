package ge.tbegvadze.toon3d.entity;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.render.Renderable;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;

public class Player implements Renderable, Disposable {

    public float positionX;
    public float positionY;
    public float directionX; // unit direction vector — always length 1
    public float directionY;
    public float fieldOfViewRadians;

    private int health;
    private final int maxHealth;
    private int armor;
    private final int maxArmor;

    private final ShapeRenderer shapes;
    private PlayerDamageListener damageListener;

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

    public void applyDamage(int amount) {
        int armorAbsorbed = GameMath.armorAbsorb(amount, armor, Constants.ARMOUR_ABSORB_FRACTION);
        armor  = Math.max(0, armor  - armorAbsorbed);
        int netDamage = amount - armorAbsorbed;
        health = Math.max(0, health - netDamage);
        if (damageListener != null && netDamage > 0) {
            damageListener.onPlayerDamaged(netDamage);
        }
    }

    public void applyHealing(int amount) {
        health = Math.min(maxHealth, health + amount);
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
