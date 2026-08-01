package ge.tbegvadze.toon3d.world;

import java.util.List;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyFamily;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.enemy.EnemyState;
import ge.tbegvadze.toon3d.narrative.BarkSystem;
import ge.tbegvadze.toon3d.narrative.BarkTrigger;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Turns raw turn events into STORY MOMENTS for the bark layer (Story UI order-2).  Subscribed to
 * the floor's {@link TickEventBus} like every other per-floor system, and rebuilt with the floor, so
 * its per-floor memory (which tiles have been walked) resets exactly when the floor does.
 *
 * <p>Three moments live here because they are the ones only the turn stream can see:
 * <ul>
 *   <li><b>First sight of an enemy FAMILY</b> — the tick after any enemy of a family stops being
 *       DORMANT, i.e. it noticed the player and is coming.  One-shot per family (the persistent
 *       seen-flag lives in the narrative layer); a local per-floor filter keeps the repeat asks
 *       cheap.</li>
 *   <li><b>Backtracking</b> — re-stepping tiles already walked on this floor,
 *       {@code STORY_BARK_BACKTRACK_STEPS} in a row.  Pure flavour, dropped under any pressure.</li>
 *   <li><b>Idle</b> — no player action for {@code STORY_BARK_IDLE_SECONDS}.  The timer is advanced
 *       by {@link #update(float)} from the PLAYING phase only, and reset by every tick.</li>
 * </ul>
 *
 * <p>Allocates nothing per tick: the visited-tile grid is sized once per floor and the family filter
 * is a fixed boolean array.  All requests are advisory — {@link BarkSystem} decides whether anything
 * is actually said.
 */
public final class StoryBarkTickSubscriber implements TickSubscriber {

    private final BarkSystem   barkSystem;
    private final EnemyManager enemyManager;

    /** Tiles already stepped on during this floor, indexed [tileColumn][tileRow]. */
    private final boolean[][] visitedTiles;
    private final int         visitedColumnCount;
    private final int         visitedRowCount;
    /** Families already asked about on this floor — the narrative layer owns the permanent flag. */
    private final boolean[]   familyAlreadyAsked = new boolean[EnemyFamily.values().length];

    private int   consecutiveRevisitedSteps;
    private float secondsSinceLastAction;
    private int   lastTileColumn = -1;
    private int   lastTileRow    = -1;

    public StoryBarkTickSubscriber(BarkSystem barkSystem, EnemyManager enemyManager,
                                   int levelWidth, int levelHeight) {
        if (barkSystem == null) throw new IllegalArgumentException("barkSystem must not be null");
        this.barkSystem         = barkSystem;
        this.enemyManager       = enemyManager;
        this.visitedColumnCount = Math.max(1, levelWidth);
        this.visitedRowCount    = Math.max(1, levelHeight);
        this.visitedTiles       = new boolean[visitedColumnCount][visitedRowCount];
    }

    @Override
    public void onTick(TickContext context) {
        secondsSinceLastAction = 0f;
        trackBacktracking(context.getPlayerTileColumn(), context.getPlayerTileRow());
        detectNewEnemyFamily();
    }

    /**
     * Advances the idle clock.  Call once per frame from the PLAYING phase only, so time spent in
     * an overlay (inventory, level-up, the nav console) never counts as the player standing still.
     */
    public void update(float deltaTime) {
        secondsSinceLastAction += deltaTime;
        if (secondsSinceLastAction >= StoryUiConstants.STORY_BARK_IDLE_SECONDS) {
            secondsSinceLastAction = 0f;
            barkSystem.request(BarkTrigger.IDLE);
        }
    }

    /**
     * Counts consecutive steps onto already-walked tiles.  A step onto fresh ground resets the
     * counter, so ordinary room-clearing never trips it — only genuine circling does.
     */
    private void trackBacktracking(int tileColumn, int tileRow) {
        if (tileColumn < 0 || tileRow < 0
                || tileColumn >= visitedColumnCount || tileRow >= visitedRowCount) {
            return;
        }
        boolean movedToANewTile = tileColumn != lastTileColumn || tileRow != lastTileRow;
        lastTileColumn = tileColumn;
        lastTileRow    = tileRow;
        if (!movedToANewTile) return;   // rotating / firing in place is not backtracking

        if (visitedTiles[tileColumn][tileRow]) {
            consecutiveRevisitedSteps++;
            if (consecutiveRevisitedSteps >= StoryUiConstants.STORY_BARK_BACKTRACK_STEPS) {
                consecutiveRevisitedSteps = 0;
                barkSystem.request(BarkTrigger.BACKTRACK);
            }
        } else {
            visitedTiles[tileColumn][tileRow] = true;
            consecutiveRevisitedSteps = 0;
        }
    }

    /** Asks for a first-appearance line for any family that has woken up on this floor. */
    private void detectNewEnemyFamily() {
        if (enemyManager == null) return;
        List<Enemy> enemies = enemyManager.getEnemies();
        for (int enemyIndex = 0; enemyIndex < enemies.size(); enemyIndex++) {
            Enemy enemy = enemies.get(enemyIndex);
            if (enemy.health <= 0 || enemy.state == EnemyState.DORMANT) continue;
            EnemyFamily family = enemy.type.family();
            if (family == null || familyAlreadyAsked[family.ordinal()]) continue;
            familyAlreadyAsked[family.ordinal()] = true;
            barkSystem.request(BarkTrigger.ENEMY_FAMILY_FIRST_SEEN, family.name());
        }
    }
}
