package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.math.MathUtils;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * Casts RAY_COUNT rays distributed evenly around a full 360-degree circle using the DDA
 * algorithm. Results are written into pre-allocated RayCastResult objects — no heap allocation
 * occurs during castRays().
 */
public final class RayCaster {

    private final RayCastResult[] results;

    public RayCaster() {
        results = new RayCastResult[Constants.RAY_COUNT];
        for (int resultIndex = 0; resultIndex < Constants.RAY_COUNT; resultIndex++) {
            results[resultIndex] = new RayCastResult();
        }
    }

    /**
     * Casts RAY_COUNT rays spread evenly across the player's field of view (centred on the
     * player's facing direction) and returns the pre-allocated result array. The same array is
     * overwritten on every call — do not hold a reference across frames.
     */
    public RayCastResult[] castRays(float playerWorldX, float playerWorldY,
                                    float playerDirectionX, float playerDirectionY,
                                    float fieldOfViewRadians, Level level) {
        float playerTileX            = playerWorldX / Constants.CELL_SIZE;
        float playerTileY            = playerWorldY / Constants.CELL_SIZE;
        float halfFieldOfViewRadians = fieldOfViewRadians / 2f;

        for (int rayIndex = 0; rayIndex < Constants.RAY_COUNT; rayIndex++) {
            // Spread rays evenly from -FOV/2 to +FOV/2 relative to the facing direction.
            float angleOffsetRadians = (Constants.RAY_COUNT > 1)
                    ? -halfFieldOfViewRadians + rayIndex * (fieldOfViewRadians / (Constants.RAY_COUNT - 1))
                    : 0f;
            float rayDirectionX = GameMath.rotateVectorX(playerDirectionX, playerDirectionY, angleOffsetRadians);
            float rayDirectionY = GameMath.rotateVectorY(playerDirectionX, playerDirectionY, angleOffsetRadians);
            castSingleRay(playerTileX, playerTileY, rayDirectionX, rayDirectionY, level, results[rayIndex]);
        }
        return results;
    }

    private static void castSingleRay(
            float playerTileX, float playerTileY,
            float rayDirectionX, float rayDirectionY,
            Level level,
            RayCastResult result) {

        float deltaDistanceX = GameMath.ddaDeltaDistanceX(rayDirectionX);
        float deltaDistanceY = GameMath.ddaDeltaDistanceY(rayDirectionY);

        int tileColumn = MathUtils.floor(playerTileX);
        int tileRow    = MathUtils.floor(playerTileY);

        float sideDistanceX = GameMath.ddaInitialSideDistanceX(playerTileX, tileColumn, rayDirectionX, deltaDistanceX);
        float sideDistanceY = GameMath.ddaInitialSideDistanceY(playerTileY, tileRow,    rayDirectionY, deltaDistanceY);

        int stepColumn = (rayDirectionX < 0f) ? -1 : 1;
        int stepRow    = (rayDirectionY < 0f) ? -1 : 1;

        boolean hitWall              = false;
        boolean crossedVerticalLine  = false;

        while (true) {
            if (sideDistanceX < sideDistanceY) {
                sideDistanceX      += deltaDistanceX;
                tileColumn         += stepColumn;
                crossedVerticalLine = true;
            } else {
                sideDistanceY      += deltaDistanceY;
                tileRow            += stepRow;
                crossedVerticalLine = false;
            }

            // Distance to the grid line we just crossed (one deltaDist step back from current sideDist)
            float perpWallDistance = crossedVerticalLine
                    ? sideDistanceX - deltaDistanceX
                    : sideDistanceY - deltaDistanceY;

            if (perpWallDistance >= Constants.RAY_MAX_LENGTH_CELLS) break;

            if (Level.isWall(level.getCell(tileColumn, tileRow))) {
                hitWall = true;
                break;
            }
        }

        float travelDistance = hitWall
                ? (crossedVerticalLine ? sideDistanceX - deltaDistanceX : sideDistanceY - deltaDistanceY)
                : Constants.RAY_MAX_LENGTH_CELLS;

        result.hitWall  = hitWall;
        result.endTileX = playerTileX + rayDirectionX * travelDistance;
        result.endTileY = playerTileY + rayDirectionY * travelDistance;
    }
}
