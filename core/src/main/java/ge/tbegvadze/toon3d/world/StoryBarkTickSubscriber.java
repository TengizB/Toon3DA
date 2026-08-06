package ge.tbegvadze.toon3d.world;

import java.util.List;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyFamily;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.enemy.EnemyState;
import ge.tbegvadze.toon3d.narrative.BarkSystem;
import ge.tbegvadze.toon3d.narrative.BarkTrigger;
import ge.tbegvadze.toon3d.narrative.ControlHint;
import ge.tbegvadze.toon3d.narrative.ExchangeSystem;
import ge.tbegvadze.toon3d.narrative.ExchangeTrigger;
import ge.tbegvadze.toon3d.narrative.IntroBeat;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Turns raw turn events into STORY MOMENTS for the bark layer (Story UI order-2).  Subscribed to
 * the floor's {@link TickEventBus} like every other per-floor system, and rebuilt with the floor, so
 * its per-floor memory (which tiles have been walked) resets exactly when the floor does.
 *
 * <p>The moments here are the ones only the turn stream can see:
 * <ul>
 *   <li><b>First sight of an enemy FAMILY</b> — the tick after any enemy of a family stops being
 *       DORMANT, i.e. it noticed the player and is coming.  One-shot per family (the persistent
 *       seen-flag lives in the narrative layer); a local per-floor filter keeps the repeat asks
 *       cheap.  The same instant is when the FIRE button first matters, so it also carries the
 *       {@link ControlHint#FIRE} tutorial line (order-5).</li>
 *   <li><b>Backtracking</b> — re-stepping tiles already walked on this floor,
 *       {@code STORY_BARK_BACKTRACK_STEPS} in a row.  Pure flavour, dropped under any pressure.</li>
 *   <li><b>Idle</b> — no player action for {@code STORY_BARK_IDLE_SECONDS}.  The timer is advanced
 *       by {@link #update(float)} from the PLAYING phase only, and reset by every tick.</li>
 *   <li><b>A quiet moment</b> (order-5) — {@code STORY_QUIET_MOMENT_MIN_STEPS} fresh tiles walked on
 *       this floor with nothing awake on it.  This is where the teaching EXCHANGE opens, and the
 *       "nothing awake" half is the whole point: a player meets their first blocking choice with no
 *       enemy in the room and nothing at stake.  Deliberately NOT the idle timer — a player who has
 *       put the phone down must never come back to a modal they did not open.</li>
 * </ul>
 *
 * <p>Allocates nothing per tick: the visited-tile grid is sized once per floor and the family filter
 * is a fixed boolean array.  All requests are advisory — {@link BarkSystem} and
 * {@link ExchangeSystem} decide whether anything actually happens.
 */
public final class StoryBarkTickSubscriber implements TickSubscriber {

    private final BarkSystem     barkSystem;
    private final ExchangeSystem exchangeSystem;
    private final EnemyManager   enemyManager;

    /** Tiles already stepped on during this floor, indexed [tileColumn][tileRow]. */
    private final boolean[][] visitedTiles;
    private final int         visitedColumnCount;
    private final int         visitedRowCount;
    /** Families already asked about on this floor — the narrative layer owns the permanent flag. */
    private final boolean[]   familyAlreadyAsked = new boolean[EnemyFamily.values().length];

    private int     consecutiveRevisitedSteps;
    private float   secondsSinceLastAction;
    private int     lastTileColumn = -1;
    private int     lastTileRow    = -1;
    /** Fresh tiles walked on this floor — the "has the player actually explored" half of quiet. */
    private int     freshTilesWalked;
    /** Set once this floor has offered its quiet moment, so the ask is made at most once per floor. */
    private boolean quietMomentOffered;

    public StoryBarkTickSubscriber(BarkSystem barkSystem, ExchangeSystem exchangeSystem,
                                   EnemyManager enemyManager, int levelWidth, int levelHeight) {
        if (barkSystem == null) throw new IllegalArgumentException("barkSystem must not be null");
        this.barkSystem         = barkSystem;
        this.exchangeSystem     = exchangeSystem;
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
        detectQuietMoment();
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
            freshTilesWalked++;
        }
    }

    /**
     * Asks for a first-appearance line for any family that has woken up on this floor — and, the
     * very first time anything anywhere wakes up, for the FIRE tutorial line, because that is the
     * exact moment the button starts mattering (order-5).  The hint is one-shot in the narrative
     * layer, so asking on every floor's first wake-up costs nothing after the first run.
     */
    private void detectNewEnemyFamily() {
        if (enemyManager == null) return;
        List<Enemy> enemies = enemyManager.getEnemies();
        for (int enemyIndex = 0; enemyIndex < enemies.size(); enemyIndex++) {
            Enemy enemy = enemies.get(enemyIndex);
            if (enemy.health <= 0 || enemy.state == EnemyState.DORMANT) continue;
            barkSystem.request(BarkTrigger.CONTROL_HINT, ControlHint.FIRE.getSubjectKey());
            EnemyFamily family = enemy.type.family();
            if (family == null || familyAlreadyAsked[family.ordinal()]) continue;
            familyAlreadyAsked[family.ordinal()] = true;
            barkSystem.request(BarkTrigger.ENEMY_FAMILY_FIRST_SEEN, family.name());
        }
    }

    /**
     * Offers the floor's QUIET MOMENT once the player has explored a while with nothing awake behind
     * them (order-5).  Both halves matter: the step count keeps it out of the first corridor, and
     * "nothing awake" is what makes it safe to stop the world — which is the entire reason the
     * teaching exchange lives here rather than on the idle timer.
     *
     * <p>Advisory, like everything else in this class: the exchange layer's own region gate and
     * one-shot flag decide whether any conversation actually exists for this moment, and after the
     * player's first run there never is one again.
     */
    private void detectQuietMoment() {
        if (quietMomentOffered)                                              return;
        if (freshTilesWalked < StoryUiConstants.STORY_QUIET_MOMENT_MIN_STEPS) return;
        if (isAnythingAwake())                                               return;
        quietMomentOffered = true;
        // The same stretch of quiet carries ORA's one line about the number on the wake card
        // (narrative-rework order-2 D). It is deliberately not said ON that card: a counter
        // explains itself badly on the screen it appears on, and this is the first moment the
        // player has nothing else to do. One-shot in the narrative layer, so it is asked once
        // per floor and answered once ever.
        barkSystem.request(IntroBeat.REPRINT_COUNTER.getTrigger(),
                           IntroBeat.REPRINT_COUNTER.getSubjectKey());
        if (exchangeSystem != null) exchangeSystem.request(ExchangeTrigger.QUIET_MOMENT);
    }

    /** True while any living enemy on this floor has noticed the player. */
    private boolean isAnythingAwake() {
        if (enemyManager == null) return false;
        List<Enemy> enemies = enemyManager.getEnemies();
        for (int enemyIndex = 0; enemyIndex < enemies.size(); enemyIndex++) {
            Enemy enemy = enemies.get(enemyIndex);
            if (enemy.health > 0 && enemy.state != EnemyState.DORMANT) return true;
        }
        return false;
    }
}
