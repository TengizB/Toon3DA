package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyAttackListener;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;

import static ge.tbegvadze.toon3d.util.Constants.CELL_SIZE;
import static ge.tbegvadze.toon3d.util.RenderConstants.PROP_BEHIND_PLAYER_EPSILON_TILES;
import static ge.tbegvadze.toon3d.util.Constants.WALL_PROJECTION_SCREEN_HEIGHT;
import static ge.tbegvadze.toon3d.util.Constants.WALL_PROJECTION_SCREEN_WIDTH;

/**
 * Manages and renders traveling projectiles for ranged enemy attacks.
 * ACID_DRONE and MIRE_WRAITH fire glowing glob projectiles that lerp from the
 * enemy tile toward the player over ENEMY_PROJECTILE_TRAVEL_SECONDS.
 * EYE_TYRANT fires an instant beam — handled separately in EnemyRenderer.
 *
 * Zero allocations after construction: fixed-size pool, pre-allocated structs.
 * Implements EnemyAttackListener so EnemyManager can call it without depending on
 * the render package.
 */
public final class EnemyAttackEffectSystem implements EnemyAttackListener, Disposable {

    private static final class EnemyProjectile {
        boolean   active;
        float     fromWorldX, fromWorldY;
        float     toWorldX,   toWorldY;
        float     elapsedSeconds;
        float     lifeSeconds;
        EnemyType type;
    }

    private final EnemyProjectile[] projectiles;
    private final WallRenderer       wallRenderer;
    private final ShapeRenderer      shapeRenderer;

    private float playerWorldX = 0f;
    private float playerWorldY = 0f;
    private float directionX   = 1f;
    private float directionY   = 0f;
    private float planeX       = 0f;
    private float planeY       = 1f;

    public EnemyAttackEffectSystem(WallRenderer wallRenderer) {
        this.wallRenderer  = wallRenderer;
        this.projectiles   = new EnemyProjectile[EffectConstants.ENEMY_PROJECTILE_POOL_SIZE];
        for (int index = 0; index < projectiles.length; index++) {
            projectiles[index] = new EnemyProjectile();
        }
        this.shapeRenderer = new ShapeRenderer();
    }

    public void setPlayerState(float worldX, float worldY,
                               float playerDirectionX, float playerDirectionY,
                               float fieldOfViewRadians) {
        this.playerWorldX = worldX;
        this.playerWorldY = worldY;
        this.directionX   = playerDirectionX;
        this.directionY   = playerDirectionY;
        float planeScale  = (float) Math.tan(fieldOfViewRadians / 2.0);
        this.planeX       = GameMath.cameraPlaneX(playerDirectionY, planeScale);
        this.planeY       = GameMath.cameraPlaneY(playerDirectionX, planeScale);
    }

    @Override
    public void onMeleeAttack(Enemy enemy) {
        // Melee lunge is handled purely in EnemyRenderer via attackAnimStrength — no projectile needed.
    }

    @Override
    public void onRangedAttack(Enemy enemy, int playerColumn, int playerRow) {
        if (enemy.type == EnemyType.EYE_TYRANT) {
            // EYE_TYRANT fires an instant beam drawn by EnemyRenderer — no pool entry needed.
            return;
        }
        int freeSlot = findFreeOrOldestSlot();
        EnemyProjectile projectile   = projectiles[freeSlot];
        projectile.active            = true;
        projectile.fromWorldX        = enemy.worldCenterX();
        projectile.fromWorldY        = enemy.worldCenterY();
        projectile.toWorldX          = playerColumn * CELL_SIZE + CELL_SIZE / 2f;
        projectile.toWorldY          = playerRow    * CELL_SIZE + CELL_SIZE / 2f;
        projectile.elapsedSeconds    = 0f;
        projectile.lifeSeconds       = EffectConstants.ENEMY_PROJECTILE_TRAVEL_SECONDS;
        projectile.type              = enemy.type;
    }

    public void update(float deltaTime) {
        for (int index = 0; index < projectiles.length; index++) {
            EnemyProjectile projectile = projectiles[index];
            if (!projectile.active) continue;
            projectile.elapsedSeconds += deltaTime;
            if (projectile.elapsedSeconds >= projectile.lifeSeconds) {
                projectile.active = false;
            }
        }
    }

    public void render(OrthographicCamera camera) {
        boolean anyActive = false;
        for (int index = 0; index < projectiles.length; index++) {
            if (projectiles[index].active) { anyActive = true; break; }
        }
        if (!anyActive) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int index = 0; index < projectiles.length; index++) {
            EnemyProjectile projectile = projectiles[index];
            if (!projectile.active) continue;

            float interpolationFactor = projectile.elapsedSeconds / projectile.lifeSeconds;
            float currentWorldX = GameMath.lerp(projectile.fromWorldX, projectile.toWorldX, interpolationFactor);
            float currentWorldY = GameMath.lerp(projectile.fromWorldY, projectile.toWorldY, interpolationFactor);

            float tileOffsetX = (currentWorldX - playerWorldX) / CELL_SIZE;
            float tileOffsetY = (currentWorldY - playerWorldY) / CELL_SIZE;
            float depth = GameMath.spriteDepth(tileOffsetX, tileOffsetY, directionX, directionY);
            if (depth <= PROP_BEHIND_PLAYER_EPSILON_TILES) continue;

            float screenColumn = GameMath.spriteScreenColumnCenter(
                    tileOffsetX, tileOffsetY, directionX, directionY,
                    planeX, planeY, WALL_PROJECTION_SCREEN_WIDTH);

            int screenColumnInt = Math.max(0, Math.min(WALL_PROJECTION_SCREEN_WIDTH - 1, (int) screenColumn));
            if (depth >= wallRenderer.getZBufferUnchecked(screenColumnInt)) continue;

            float projectedSize = EffectConstants.ENEMY_PROJECTILE_BASE_SIZE / depth;
            // Fade out as the projectile approaches the player (t → 1)
            float alpha = 1f - interpolationFactor * interpolationFactor;
            float screenY = WALL_PROJECTION_SCREEN_HEIGHT / 2f;

            switch (projectile.type) {
                case ACID_DRONE:
                    shapeRenderer.setColor(EffectConstants.ACID_DRONE_PROJECTILE_R,
                                          EffectConstants.ACID_DRONE_PROJECTILE_G,
                                          EffectConstants.ACID_DRONE_PROJECTILE_B, alpha);
                    break;
                case MIRE_WRAITH:
                    shapeRenderer.setColor(EffectConstants.MIRE_WRAITH_PROJECTILE_R,
                                          EffectConstants.MIRE_WRAITH_PROJECTILE_G,
                                          EffectConstants.MIRE_WRAITH_PROJECTILE_B, alpha);
                    break;
                default:
                    shapeRenderer.setColor(1f, 1f, 1f, alpha);
                    break;
            }
            float halfSize = projectedSize / 2f;
            shapeRenderer.rect(screenColumn - halfSize, screenY - halfSize, projectedSize, projectedSize);
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }

    private int findFreeOrOldestSlot() {
        for (int index = 0; index < projectiles.length; index++) {
            if (!projectiles[index].active) return index;
        }
        int   oldestIndex   = 0;
        float maxElapsed    = -1f;
        for (int index = 0; index < projectiles.length; index++) {
            if (projectiles[index].elapsedSeconds > maxElapsed) {
                maxElapsed  = projectiles[index].elapsedSeconds;
                oldestIndex = index;
            }
        }
        return oldestIndex;
    }
}
