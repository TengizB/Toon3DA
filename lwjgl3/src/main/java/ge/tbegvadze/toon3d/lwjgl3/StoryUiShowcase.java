package ge.tbegvadze.toon3d.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import java.util.List;
import java.util.Locale;

import ge.tbegvadze.toon3d.narrative.Speaker;
import ge.tbegvadze.toon3d.narrative.StoryLine;
import ge.tbegvadze.toon3d.narrative.StorySampleLines;
import ge.tbegvadze.toon3d.narrative.StoryStrings;
import ge.tbegvadze.toon3d.narrative.StoryText;
import ge.tbegvadze.toon3d.render.StoryPanelRenderer;
import ge.tbegvadze.toon3d.render.StorySpeakerStings;
import ge.tbegvadze.toon3d.render.StoryStringsLoader;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.HudConstants;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Story UI order-1 — the DEFINITION-OF-DONE test screen (desktop dev tooling only; the game
 * ships on Android).  Renders one sample line per speaker over a split bright/dark backdrop
 * (standing in for the 3D view) with faux HUD + thumb-cluster markers, proving every voice is
 * instantly distinct, readable on any backdrop, and never overlapping the HUD or touch
 * controls.  Each speaker's sting plays once, staggered, on start-up.
 *
 * Run via {@link StoryUiShowcaseLauncher}.  All display text is resolved through
 * {@link StoryStrings} (loaded from the properties asset, falling back to defaults) and
 * pre-wrapped once here — the draw path allocates nothing.
 */
public final class StoryUiShowcase extends ApplicationAdapter {

    // 2x2 grid cells for the four speaker panels, sitting entirely in the clear upper band
    // (above both the HUD and the touch clusters — see StoryUiConstants.STORY_SAFE_BAND_BOTTOM_Y).
    private static final float CELL_WIDTH   = 560f;
    private static final float CELL_HEIGHT  = StoryUiConstants.STORY_BARK_HEIGHT;
    private static final float COLUMN_GAP   = 40f;
    private static final float ROW_GAP      = 22f;
    private static final float TOP_ROW_TOP_Y = 700f;
    private static final float STING_STAGGER_SECONDS = 0.5f;

    private OrthographicCamera camera;
    private FitViewport        viewport;
    private ShapeRenderer      backdrop;
    private SpriteBatch        captionBatch;
    private BitmapFont         captionFont;

    private StoryPanelRenderer panelRenderer;
    private StorySpeakerStings stings;
    private StoryStrings       strings;

    // Pre-resolved / pre-wrapped panel content (no per-frame allocation).
    private Speaker[]  speakers;
    private String[]   speakerNames;
    private String[][] bodyLines;
    private int[]      bodyLineCounts;
    private float[]    cellX;
    private float[]    cellBottomY;

    private float elapsedSeconds;
    private int   stingsPlayed;

    @Override
    public void create() {
        camera   = new OrthographicCamera();
        viewport = new FitViewport(Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT, camera);
        camera.position.set(Constants.WORLD_WIDTH / 2f, Constants.WORLD_HEIGHT / 2f, 0f);
        camera.update();

        backdrop      = new ShapeRenderer();
        captionBatch  = new SpriteBatch();
        captionFont   = new BitmapFont();
        panelRenderer = new StoryPanelRenderer();
        stings        = new StorySpeakerStings();
        // Order-2 added StoryStringsLoader as the ONE place the string asset is read (with the
        // built-in defaults as its fallback); the showcase shares it with the game.
        strings       = StoryStringsLoader.load();

        prepareSamplePanels();
    }

    /** Resolves, styles (uppercase for the Organization) and wraps each sample line once. */
    private void prepareSamplePanels() {
        StoryLine[] samples = StorySampleLines.ALL;
        int count = samples.length;
        speakers       = new Speaker[count];
        speakerNames   = new String[count];
        bodyLines      = new String[count][];
        bodyLineCounts = new int[count];
        cellX          = new float[count];
        cellBottomY    = new float[count];

        for (int sampleIndex = 0; sampleIndex < count; sampleIndex++) {
            Speaker speaker = samples[sampleIndex].getSpeaker();
            speakers[sampleIndex]     = speaker;
            speakerNames[sampleIndex] = strings.get(speaker.getNameStringId());

            String bodyText = strings.get(samples[sampleIndex].getTextStringId());
            // Presentation transform: the Organization reads in ALL-CAPS (its TypeStyle).
            if (speaker.getTypeStyle().isUpperCase()) {
                bodyText = bodyText.toUpperCase(Locale.ROOT);
            }
            List<String> wrapped = StoryText.wrapToMaxChars(bodyText, StoryUiConstants.STORY_LINE_MAX_CHARS);
            bodyLines[sampleIndex]      = wrapped.toArray(new String[0]);
            bodyLineCounts[sampleIndex] = wrapped.size();

            // 2x2 grid placement (AI, PLANET on the top row; ORG, SYSTEM on the bottom row).
            int column = sampleIndex % 2;
            int row    = sampleIndex / 2;
            float gridWidth = CELL_WIDTH * 2f + COLUMN_GAP;
            float gridLeft  = (Constants.WORLD_WIDTH - gridWidth) / 2f;
            cellX[sampleIndex]       = gridLeft + column * (CELL_WIDTH + COLUMN_GAP);
            cellBottomY[sampleIndex] = TOP_ROW_TOP_Y - CELL_HEIGHT - row * (CELL_HEIGHT + ROW_GAP);
        }
    }

    @Override
    public void render() {
        float deltaTime = Gdx.graphics.getDeltaTime();
        elapsedSeconds += deltaTime;
        playStaggeredStings();

        ScreenUtils.clear(0f, 0f, 0f, 1f);
        viewport.apply();

        drawBackdropAndSafeZones();

        for (int panelIndex = 0; panelIndex < speakers.length; panelIndex++) {
            panelRenderer.drawPanel(camera, speakers[panelIndex], speakerNames[panelIndex],
                    bodyLines[panelIndex], bodyLineCounts[panelIndex],
                    cellX[panelIndex], cellBottomY[panelIndex], CELL_WIDTH, CELL_HEIGHT,
                    1f, elapsedSeconds);
        }

        drawCaption();
    }

    /** Plays each speaker's sting once, staggered, so they can be told apart on start-up. */
    private void playStaggeredStings() {
        while (stingsPlayed < speakers.length
                && elapsedSeconds >= (stingsPlayed + 1) * STING_STAGGER_SECONDS) {
            stings.play(speakers[stingsPlayed]);
            stingsPlayed++;
        }
    }

    /** Split bright/dark backdrop (stands in for the 3D view) + faux HUD & touch-cluster markers. */
    private void drawBackdropAndSafeZones() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        backdrop.setProjectionMatrix(camera.combined);
        backdrop.begin(ShapeRenderer.ShapeType.Filled);

        // Left half bright, right half dark — proves the panel reads over both.
        backdrop.setColor(0.66f, 0.66f, 0.70f, 1f);
        backdrop.rect(0f, 0f, Constants.WORLD_WIDTH / 2f, Constants.WORLD_HEIGHT);
        backdrop.setColor(0.10f, 0.10f, 0.13f, 1f);
        backdrop.rect(Constants.WORLD_WIDTH / 2f, 0f, Constants.WORLD_WIDTH / 2f, Constants.WORLD_HEIGHT);

        // Faux HUD band (bottom) — story UI must never overlap this.
        backdrop.setColor(0.20f, 0.05f, 0.05f, 0.55f);
        backdrop.rect(0f, 0f, Constants.WORLD_WIDTH, HudConstants.HUD_HEIGHT);

        // Faux thumb clusters (lower-left / lower-right) — story UI must clear these too.
        backdrop.setColor(0.05f, 0.20f, 0.05f, 0.55f);
        float clusterSize = StoryUiConstants.STORY_TOUCH_CLUSTER_HALF_EXTENT * 2f;
        backdrop.rect(StoryUiConstants.STORY_LEFT_CLUSTER_RIGHT_X - clusterSize,
                StoryUiConstants.STORY_TOUCH_CLUSTER_TOP_Y - clusterSize, clusterSize, clusterSize);
        backdrop.rect(StoryUiConstants.STORY_RIGHT_CLUSTER_LEFT_X,
                StoryUiConstants.STORY_TOUCH_CLUSTER_TOP_Y - clusterSize, clusterSize, clusterSize);

        backdrop.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawCaption() {
        captionBatch.setProjectionMatrix(camera.combined);
        captionBatch.begin();
        captionFont.setColor(Color.WHITE);
        captionFont.draw(captionBatch,
                "STORY UI ORDER-1  -  SPEAKER SHOWCASE   (bright half | dark half; red = HUD, green = touch zones)",
                20f, 30f);
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
        panelRenderer.dispose();
        stings.dispose();
    }
}
