package ge.tbegvadze.toon3d.lwjgl3;

import java.util.Locale;

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

import ge.tbegvadze.toon3d.narrative.BootCardCatalog;
import ge.tbegvadze.toon3d.narrative.BootCardSystem;
import ge.tbegvadze.toon3d.narrative.BootCardVariant;
import ge.tbegvadze.toon3d.narrative.InMemoryStoryProgressStore;
import ge.tbegvadze.toon3d.narrative.StoryProgress;
import ge.tbegvadze.toon3d.narrative.StoryRegion;
import ge.tbegvadze.toon3d.narrative.StoryStrings;
import ge.tbegvadze.toon3d.render.BootCardRenderer;
import ge.tbegvadze.toon3d.render.StoryStringsLoader;
import ge.tbegvadze.toon3d.util.Constants;

/**
 * Story UI order-3 — the REPRINT / BOOT CARD definition-of-done screen (desktop dev tooling only;
 * the game ships on Android).  Drives the REAL {@link BootCardSystem} and {@link BootCardRenderer}
 * through a scripted sequence of reprints so the whole channel can be watched end to end:
 *
 * <ol>
 *   <li>one reprint per story region, in order, over a shared {@link StoryProgress} — so ORA's arc
 *       from cheerfully counting your deaths to quietly keeping things off the log plays out as one
 *       continuous descent, and the instance counter visibly climbs across all of them;</li>
 *   <li>then each endgame subversion in turn: OBEY's cold promotion (which still continues), and
 *       FREE and KILL, which print the inverted card and draw NO CONTINUE — the "no reprint" state
 *       is the thing to check here, because that absence is the ending.</li>
 * </ol>
 *
 * <p>MERGE is not in the script for the best possible reason: it presents no card at all, so there
 * is nothing to look at.  The caption calls that out rather than showing a blank beat.
 *
 * <p>Interactive: click CONTINUE to advance a card by hand, or click anywhere else to skip the
 * status-line reveal — the same two gestures {@code World} wires to touch.  The script also advances
 * on its own so a headless capture run terminates.
 *
 * <p>Run via {@link BootCardShowcaseLauncher} ({@code ./gradlew lwjgl3:bootCardShowcase}).  Pass
 * {@code -DbootCard.screenshotDirectory=<dir>} to write one PNG per card and exit — how this screen
 * is verified on a headless box.
 */
public final class BootCardShowcase extends ApplicationAdapter {

    /** Seconds each card is left up before the showcase taps CONTINUE for the player. */
    private static final float BEAT_SPACING_SECONDS = 4.5f;
    /** How long after a card appears a screenshot is taken (past the fade and the full reveal). */
    private static final float SCREENSHOT_OFFSET_SECONDS = 2.5f;

    /** One scripted beat: the story region to be in, and the card variant to present. */
    private static final StoryRegion[] SCRIPT_REGIONS = {
        StoryRegion.HABITATION_RINGS, StoryRegion.HARVESTING_GALLERIES, StoryRegion.RELIQUARY,
        StoryRegion.WOUND, StoryRegion.CORE,
        StoryRegion.CORE, StoryRegion.CORE, StoryRegion.CORE,
    };
    private static final BootCardVariant[] SCRIPT_VARIANTS = {
        BootCardVariant.REPRINT, BootCardVariant.REPRINT, BootCardVariant.REPRINT,
        BootCardVariant.REPRINT, BootCardVariant.REPRINT,
        BootCardVariant.ENDING_OBEY, BootCardVariant.ENDING_FREE, BootCardVariant.ENDING_KILL,
    };

    private OrthographicCamera camera;
    private FitViewport        viewport;
    private ShapeRenderer      backdrop;
    private SpriteBatch        captionBatch;
    private BitmapFont         captionFont;

    private BootCardSystem   bootCardSystem;
    private BootCardRenderer bootCardRenderer;
    private StoryProgress    progress;

    private float  cardElapsedSeconds;
    private int    scriptIndex = -1;
    private int    screenshotsTaken;
    private String screenshotDirectory;
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

        // In-memory store: the showcase always starts a clean story at instance zero, so the
        // counter climbing through the script is visible from the first card.
        progress = new StoryProgress(new InMemoryStoryProgressStore());
        StoryStrings strings = StoryStringsLoader.load();
        bootCardSystem = new BootCardSystem(BootCardCatalog.defaultRegistry(), strings, progress,
                                            20250801L);
        bootCardRenderer = new BootCardRenderer();
        bootCardRenderer.setBootCardSystem(bootCardSystem);
        bootCardRenderer.setStrings(strings);

        screenshotDirectory = System.getProperty("bootCard.screenshotDirectory");
        advanceScript();
    }

    @Override
    public void render() {
        float deltaTime = Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f);
        cardElapsedSeconds += deltaTime;

        bootCardSystem.update(deltaTime);
        handleInput();
        autoAdvance();

        ScreenUtils.clear(0.06f, 0.05f, 0.05f, 1f);
        viewport.apply();
        camera.update();
        drawBackdrop();
        bootCardRenderer.render(camera);
        drawCaption();

        captureScriptedScreenshot();
    }

    /**
     * The same two gestures {@code World} wires to touch: CONTINUE proceeds, anything else completes
     * the status-line reveal.  A terminal card refuses the first one, which is exactly the state
     * worth poking at by hand on this screen.
     */
    private void handleInput() {
        if (!Gdx.input.justTouched()) return;
        touchWorldPosition.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(touchWorldPosition);
        if (BootCardRenderer.isInsideContinueButton(touchWorldPosition.x, touchWorldPosition.y)) {
            if (bootCardSystem.requestContinue()) advanceScript();
        } else {
            bootCardSystem.skipReveal();
        }
    }

    /** Moves the script on by itself so an unattended (or headless) run still finishes. */
    private void autoAdvance() {
        if (cardElapsedSeconds < BEAT_SPACING_SECONDS) return;
        advanceScript();
    }

    /** Presents the next scripted card, deepening the story region first so the pool matches. */
    private void advanceScript() {
        scriptIndex++;
        cardElapsedSeconds = 0f;
        if (scriptIndex >= SCRIPT_VARIANTS.length) {
            if (screenshotDirectory == null) scriptIndex = 0;   // loop for an interactive session
            else return;                                        // a capture run is done
        }
        progress.reachRegion(SCRIPT_REGIONS[scriptIndex]);
        bootCardSystem.present(SCRIPT_VARIANTS[scriptIndex]);
    }

    /**
     * Writes one PNG per scripted card when a screenshot directory is set, then quits — the headless
     * verification path.  Never runs in an interactive session.
     */
    private void captureScriptedScreenshot() {
        if (screenshotDirectory == null) return;
        if (screenshotsTaken >= SCRIPT_VARIANTS.length) {
            Gdx.app.exit();
            return;
        }
        if (screenshotsTaken != scriptIndex || cardElapsedSeconds < SCREENSHOT_OFFSET_SECONDS) return;

        Pixmap frame = Pixmap.createFromFrameBuffer(0, 0,
                Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        String fileName = String.format("boot-%02d-%s.png", screenshotsTaken,
                SCRIPT_VARIANTS[screenshotsTaken].name().toLowerCase(Locale.ROOT));
        PixmapIO.writePNG(Gdx.files.absolute(screenshotDirectory + "/" + fileName), frame, -1, true);
        frame.dispose();
        screenshotsTaken++;
    }

    /**
     * A stand-in for the frozen 3D view the card pauses over.  Deliberately bright on one side: the
     * card's own full-bleed background must black it out completely, whatever was behind it.
     */
    private void drawBackdrop() {
        backdrop.setProjectionMatrix(camera.combined);
        backdrop.begin(ShapeRenderer.ShapeType.Filled);
        backdrop.setColor(0.16f, 0.17f, 0.20f, 1f);
        backdrop.rect(0f, 0f, Constants.WORLD_WIDTH / 2f, Constants.WORLD_HEIGHT);
        backdrop.setColor(0.78f, 0.76f, 0.70f, 1f);
        backdrop.rect(Constants.WORLD_WIDTH / 2f, 0f, Constants.WORLD_WIDTH / 2f, Constants.WORLD_HEIGHT);
        backdrop.end();
    }

    /** Debug caption: which variant is up, the story region gating ORA, and the continue state. */
    private void drawCaption() {
        captionBatch.setProjectionMatrix(camera.combined);
        captionBatch.begin();
        captionFont.setColor(Color.LIGHT_GRAY);
        BootCardVariant variant = bootCardSystem.getActiveVariant();
        captionFont.draw(captionBatch,
                "VARIANT: " + (variant == null ? "none (MERGE shows no card at all)" : variant.name())
                        + "   REGION: " + progress.getDeepestRegion().getDisplayName()
                        + "   REPRINTS: " + progress.getReprintCount()
                        + (bootCardSystem.isTerminal() ? "   [TERMINAL - no reprint]" : ""),
                20f, 40f);
        captionBatch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        bootCardRenderer.dispose();
        backdrop.dispose();
        captionBatch.dispose();
        captionFont.dispose();
    }
}
