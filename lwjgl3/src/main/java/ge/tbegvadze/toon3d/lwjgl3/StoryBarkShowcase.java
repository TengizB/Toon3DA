package ge.tbegvadze.toon3d.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import ge.tbegvadze.toon3d.narrative.BarkCatalog;
import ge.tbegvadze.toon3d.narrative.BarkSystem;
import ge.tbegvadze.toon3d.narrative.BarkTrigger;
import ge.tbegvadze.toon3d.narrative.StoryProgress;
import ge.tbegvadze.toon3d.narrative.StoryRegion;
import ge.tbegvadze.toon3d.render.StoryAudio;
import ge.tbegvadze.toon3d.render.StoryBarkRenderer;
import ge.tbegvadze.toon3d.render.StoryStringsLoader;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.HudConstants;
import ge.tbegvadze.toon3d.util.StoryUiConstants;
import ge.tbegvadze.toon3d.util.TouchConstants;

/**
 * Story UI order-2 — the BARK LAYER definition-of-done screen (desktop dev tooling only; the game
 * ships on Android).  Drives the REAL {@link BarkSystem} and {@link StoryBarkRenderer} over a split
 * bright/dark backdrop with faux HUD, weapon-sprite and thumb-cluster markers, replaying a scripted
 * sequence of gameplay MOMENTS so the whole channel can be watched end to end:
 *
 * <ol>
 *   <li>a region-entry beat (mandatory, one-shot) followed immediately by the Organization's gate
 *       order — proving the queue spaces them instead of stacking two panels;</li>
 *   <li>a kill line and a low-health line at each story region, so the tone shift from
 *       corporate-cheerful ORA to frightened ally is visible as one continuous run;</li>
 *   <li>a deep-strata milestone, which says NOTHING in Region 1 (the planet is silent up there) and
 *       whispers/addresses further down.</li>
 * </ol>
 *
 * Run via {@link StoryBarkShowcaseLauncher} ({@code ./gradlew lwjgl3:storyBarkShowcase}).  Pass
 * {@code -DstoryBark.screenshotDirectory=<dir>} to write one PNG per scripted beat and exit — how
 * this screen is verified on a headless box.
 */
public final class StoryBarkShowcase extends ApplicationAdapter {

    /** Seconds between scripted moments — comfortably past the bark rate limit. */
    private static final float BEAT_SPACING_SECONDS = StoryUiConstants.STORY_BARK_MIN_INTERVAL_SECONDS + 4f;
    /** How long after a moment fires a screenshot is taken (past the fade-in, fully opaque). */
    private static final float SCREENSHOT_OFFSET_SECONDS = 1.0f;
    /**
     * A bark now waits for the PLAYER, so the showcase plays one: it "reads" each line for this long
     * and then dismisses it, which is what lets the scripted sequence advance on its own.
     */
    private static final float SIMULATED_READ_SECONDS = 3.0f;

    /** One scripted moment: the story region to be in, and the trigger to fire. */
    private static final StoryRegion[] SCRIPT_REGIONS = {
        StoryRegion.HABITATION_RINGS, StoryRegion.HABITATION_RINGS, StoryRegion.HABITATION_RINGS,
        StoryRegion.HARVESTING_GALLERIES, StoryRegion.HARVESTING_GALLERIES,
        StoryRegion.RELIQUARY,
        StoryRegion.WOUND, StoryRegion.WOUND,
    };
    private static final BarkTrigger[] SCRIPT_TRIGGERS = {
        BarkTrigger.REGION_ENTERED, BarkTrigger.REGION_GATE_ORDER, BarkTrigger.KILL,
        BarkTrigger.DEEP_STRATA, BarkTrigger.ENEMY_FAMILY_FIRST_SEEN,
        BarkTrigger.KILL,
        BarkTrigger.LOW_HEALTH, BarkTrigger.DEEP_STRATA,
    };

    private OrthographicCamera camera;
    private FitViewport        viewport;
    private ShapeRenderer      backdrop;
    private SpriteBatch        captionBatch;
    private BitmapFont         captionFont;

    private BarkSystem        barkSystem;
    private StoryBarkRenderer barkRenderer;
    private StoryAudio        storyAudio;
    private StoryProgress     progress;

    private float  elapsedSeconds;
    private int    scriptIndex;
    private int    screenshotsTaken;
    private String screenshotDirectory;
    /** How long the current line has been "read" before the showcase closes it for the player. */
    private float  readingSeconds;
    /** Pre-allocated unprojection target — never recreated per frame. */
    private final Vector2 touchWorldPosition = new Vector2();

    @Override
    public void create() {
        camera   = new OrthographicCamera();
        viewport = new FitViewport(Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT, camera);
        camera.position.set(Constants.WORLD_WIDTH / 2f, Constants.WORLD_HEIGHT / 2f, 0f);
        camera.update();

        backdrop     = new ShapeRenderer();
        captionBatch = new SpriteBatch();
        captionFont  = new BitmapFont();

        progress     = new StoryProgress();   // in-memory: the showcase always starts a clean story
        barkSystem   = new BarkSystem(BarkCatalog.defaultRegistry(), StoryStringsLoader.load(),
                                      progress, 20250801L);
        storyAudio   = new StoryAudio();   // the showcase owns its own, as World does
        barkRenderer = new StoryBarkRenderer();
        barkRenderer.setStoryAudio(storyAudio);
        barkRenderer.setBarkSystem(barkSystem);

        screenshotDirectory = System.getProperty("storyBark.screenshotDirectory");
    }

    @Override
    public void render() {
        float deltaTime = Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f);
        elapsedSeconds += deltaTime;

        advanceScript();
        barkSystem.update(deltaTime);
        barkRenderer.playPendingSpeakerSting();
        handleDismissal(deltaTime);

        ScreenUtils.clear(0.04f, 0.04f, 0.05f, 1f);
        viewport.apply();
        camera.update();
        drawBackdrop();
        drawCaption();
        barkRenderer.render(camera);

        captureScriptedScreenshot();
    }

    /**
     * Closes the bark on screen the way a player would.  Clicking the X dismisses it immediately
     * (so the close affordance can be tried by hand on desktop); otherwise the showcase "reads" the
     * line for {@link #SIMULATED_READ_SECONDS} and then closes it, which is what keeps the scripted
     * sequence moving in a headless capture run.
     */
    private void handleDismissal(float deltaTime) {
        if (!barkSystem.hasActiveBark() || barkSystem.isActiveBarkDismissed()) {
            readingSeconds = 0f;
            return;
        }
        if (Gdx.input.justTouched()) {
            touchWorldPosition.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(touchWorldPosition);
            if (StoryBarkRenderer.isInsideCloseButton(touchWorldPosition.x, touchWorldPosition.y)) {
                barkSystem.dismissActiveBark();
                return;
            }
        }
        readingSeconds += deltaTime;
        if (readingSeconds >= SIMULATED_READ_SECONDS) {
            readingSeconds = 0f;
            barkSystem.dismissActiveBark();
        }
    }

    /** Fires the next scripted moment once its slot comes round. */
    private void advanceScript() {
        if (scriptIndex >= SCRIPT_TRIGGERS.length) return;
        if (elapsedSeconds < scriptIndex * BEAT_SPACING_SECONDS) return;

        progress.reachRegion(SCRIPT_REGIONS[scriptIndex]);
        BarkTrigger trigger = SCRIPT_TRIGGERS[scriptIndex];
        String subjectKey = trigger == BarkTrigger.ENEMY_FAMILY_FIRST_SEEN ? "INSECT" : null;
        barkSystem.request(trigger, subjectKey);
        scriptIndex++;
    }

    /**
     * Writes one PNG per scripted moment when a screenshot directory is set, then quits — the
     * headless verification path.  Never runs in an interactive session.
     */
    private void captureScriptedScreenshot() {
        if (screenshotDirectory == null) return;
        float dueAt = (screenshotsTaken * BEAT_SPACING_SECONDS) + SCREENSHOT_OFFSET_SECONDS;
        if (screenshotsTaken >= SCRIPT_TRIGGERS.length) {
            Gdx.app.exit();
            return;
        }
        if (elapsedSeconds < dueAt) return;

        Pixmap frame = Pixmap.createFromFrameBuffer(0, 0,
                Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        String fileName = String.format("bark-%02d-%s.png", screenshotsTaken,
                SCRIPT_TRIGGERS[screenshotsTaken].name().toLowerCase(java.util.Locale.ROOT));
        PixmapIO.writePNG(Gdx.files.absolute(screenshotDirectory + "/" + fileName), frame, -1, true);
        frame.dispose();
        screenshotsTaken++;
    }

    /**
     * A stand-in for the 3D view plus every element the bark must never cover: the HUD band, the two
     * thumb clusters, the weapon sprite footprint and the mini-map.  Half the screen is bright so
     * panel contrast can be judged against the worst case.
     */
    private void drawBackdrop() {
        backdrop.setProjectionMatrix(camera.combined);
        backdrop.begin(ShapeRenderer.ShapeType.Filled);

        backdrop.setColor(0.16f, 0.17f, 0.20f, 1f);
        backdrop.rect(0f, 0f, Constants.WORLD_WIDTH / 2f, Constants.WORLD_HEIGHT);
        backdrop.setColor(0.78f, 0.76f, 0.70f, 1f);
        backdrop.rect(Constants.WORLD_WIDTH / 2f, 0f, Constants.WORLD_WIDTH / 2f, Constants.WORLD_HEIGHT);

        // Mini-map footprint (top-left).
        backdrop.setColor(0.30f, 0.34f, 0.40f, 1f);
        backdrop.rect(Constants.MINI_MAP_ORIGIN_X, Constants.MINI_MAP_ORIGIN_Y,
                      Constants.MINI_MAP_WORLD_SIZE, Constants.MINI_MAP_WORLD_SIZE);

        // HUD band.
        backdrop.setColor(0.10f, 0.11f, 0.13f, 1f);
        backdrop.rect(0f, 0f, Constants.WORLD_WIDTH, HudConstants.HUD_HEIGHT);

        // Thumb clusters.
        float clusterSize = StoryUiConstants.STORY_TOUCH_CLUSTER_HALF_EXTENT * 2f;
        backdrop.setColor(0.24f, 0.26f, 0.32f, 1f);
        backdrop.rect(TouchConstants.TOUCH_GRID_LEFT_CENTER_X - StoryUiConstants.STORY_TOUCH_CLUSTER_HALF_EXTENT,
                      TouchConstants.TOUCH_GRID_BASE_Y - StoryUiConstants.STORY_TOUCH_CLUSTER_HALF_EXTENT,
                      clusterSize, clusterSize);
        backdrop.rect(TouchConstants.TOUCH_GRID_CENTER_X - StoryUiConstants.STORY_TOUCH_CLUSTER_HALF_EXTENT,
                      TouchConstants.TOUCH_GRID_BASE_Y - StoryUiConstants.STORY_TOUCH_CLUSTER_HALF_EXTENT,
                      clusterSize, clusterSize);

        backdrop.end();
    }

    /** Debug caption: the current story region and what the bark layer is doing. */
    private void drawCaption() {
        captionBatch.setProjectionMatrix(camera.combined);
        captionBatch.begin();
        captionFont.setColor(Color.LIGHT_GRAY);
        captionFont.draw(captionBatch,
                "STORY REGION: " + progress.getDeepestRegion().getDisplayName()
                        + "   queued: " + barkSystem.getQueuedCount()
                        + "   active: " + (barkSystem.getActiveBarkId() == null
                                            ? "-" : barkSystem.getActiveBarkId()),
                20f, HudConstants.HUD_HEIGHT + 40f);
        captionBatch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        backdrop.dispose();
        captionBatch.dispose();
        captionFont.dispose();
        barkRenderer.dispose();
        storyAudio.dispose();
    }
}
