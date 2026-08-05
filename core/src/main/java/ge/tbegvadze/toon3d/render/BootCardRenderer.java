package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.narrative.BootCardSystem;
import ge.tbegvadze.toon3d.narrative.Speaker;
import ge.tbegvadze.toon3d.narrative.StoryStrings;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Draws the REPRINT / BOOT CARD (Story UI order-3) — the full-screen card the player meets every
 * time they are printed after a death, and on the very first run too.  It pauses the world, so it
 * is a modal in the same family as {@code DeathOverlayRenderer}: a dark full-bleed background and
 * three stacked, generously-spaced elements over it —
 *
 * <ol>
 *   <li>the SYSTEM status lines, printing in one after another like a terminal waking up;</li>
 *   <li>the quiet instance counter, never celebrated and never annotated;</li>
 *   <li>ORA's region-appropriate wake-up line — the story beat.</li>
 * </ol>
 *
 * ...plus one large CONTINUE tap target.  The endgame FREE/KILL variant draws NO button: the card
 * that has framed every run says no checkpoint is authorized, and then nothing reloads.
 *
 * <p>This class is presentation ONLY.  Which card, which line, what the counter reads, how much has
 * revealed and whether continuing is allowed are all decided headlessly by {@link BootCardSystem};
 * the renderer just asks it.  The two panels reuse the order-1 {@link StoryPanelRenderer} primitive,
 * so the boot card speaks the same visual language as every other story panel — the SYSTEM block
 * carries the System chip, ORA's block carries hers.
 *
 * <p>READABILITY: big text, high contrast, no flashing.  This screen appears on a player who has
 * just died, so it stays calm and is fast to dismiss.
 *
 * <p>Nothing is allocated on the draw path: the lines arrive pre-resolved and pre-wrapped from the
 * system, the button label is measured once in {@link #setStrings}, and the scratch {@link Color} and
 * {@link GlyphLayout} are pre-allocated.  Owns a {@link StoryPanelRenderer}, a {@link ShapeRenderer},
 * a {@link SpriteBatch} and a {@link BitmapFont} — all disposed in {@link #dispose()}.
 */
public final class BootCardRenderer implements Renderable, Disposable {

    private final StoryPanelRenderer panelRenderer;
    private final ShapeRenderer      shapes;
    private final SpriteBatch        batch;
    private final BitmapFont         font;
    private final GlyphLayout        layout;
    private final Color              scratchColor = new Color();

    private BootCardSystem bootCardSystem;

    /** Resolved SYSTEM chip name, so identity never rests on colour alone (order-1 accessibility). */
    private String systemSpeakerName = "";
    /** Resolved CONTINUE label + its pre-measured centred position, so render() measures nothing. */
    private String continueLabel = "";
    private float  continueLabelX;
    /** TOP y of the label's glyph box — {@code BitmapFont.draw} anchors a line by its top, not its baseline. */
    private float  continueLabelTopY;

    public BootCardRenderer() {
        this.panelRenderer = new StoryPanelRenderer();
        this.shapes        = new ShapeRenderer();
        this.batch         = new SpriteBatch();
        this.font          = new BitmapFont();
        this.font.getData().markupEnabled = false;
        this.layout        = new GlyphLayout();
    }

    /** Binds the headless brain this renderer reads from.  Null hides the card entirely. */
    /**
     * Applies the order-6 accessibility settings (text size, reduced motion) to the shared story
     * panel this renderer draws with.  Call from the update path when a setting changes.
     */
    public void applyAccessibilitySettings(float bodyTextScale, boolean reduceMotion) {
        panelRenderer.applyAccessibilitySettings(bodyTextScale, reduceMotion);
    }

    public void setBootCardSystem(BootCardSystem bootCardSystem) {
        this.bootCardSystem = bootCardSystem;
    }

    /**
     * Resolves the CONTINUE button label (localisation rule: never a literal here) and measures it
     * ONCE.  Call at construction time, not per frame.
     */
    public void setStrings(StoryStrings strings) {
        if (strings == null) return;
        systemSpeakerName = strings.get(Speaker.SYSTEM.getNameStringId());
        continueLabel     = strings.get(StoryUiConstants.STORY_BOOT_CONTINUE_LABEL_ID);
        font.getData().setScale(StoryUiConstants.STORY_BOOT_CONTINUE_TEXT_SIZE);
        layout.setText(font, continueLabel);
        continueLabelX = StoryUiConstants.STORY_BOOT_CONTINUE_X
                + (StoryUiConstants.STORY_BOOT_CONTINUE_WIDTH - layout.width) / 2f;
        // Vertically centre the glyph box inside the button plate.  BitmapFont.draw anchors a line
        // by its TOP (StoryPanelRenderer names the same parameter textTopY), so for a box of height
        // h inside a plate spanning [Y, Y+H]:
        //     top    = Y + (H + h) / 2
        //     bottom = top - h = Y + (H - h) / 2      <- equal margins above and below
        // Using (H - h) / 2 as the TOP instead would sit the label half a glyph box too low.
        continueLabelTopY = StoryUiConstants.STORY_BOOT_CONTINUE_Y
                + (StoryUiConstants.STORY_BOOT_CONTINUE_HEIGHT + layout.height) / 2f;
        font.getData().setScale(1f);
    }

    @Override
    public void render(OrthographicCamera camera) {
        if (bootCardSystem == null || !bootCardSystem.hasActiveCard()) return;
        float fade = MathUtils.clamp(bootCardSystem.getVisibleFraction(), 0f, 1f);

        drawBackdropAndButton(camera, fade);

        // 1. SYSTEM status lines — revealed progressively, so the card wakes up rather than pops.
        panelRenderer.drawPanel(camera, Speaker.SYSTEM,
                systemSpeakerName,
                bootCardSystem.getSystemLines(),
                bootCardSystem.getRevealedSystemLineCount(),
                StoryUiConstants.STORY_BOOT_PANEL_X, StoryUiConstants.STORY_BOOT_SYSTEM_Y,
                StoryUiConstants.STORY_BOOT_PANEL_WIDTH, StoryUiConstants.STORY_BOOT_SYSTEM_PANEL_HEIGHT,
                fade, bootCardSystem.getElapsedSeconds());

        // 2 + 3. The counter and ORA both wait for the machine to finish talking.
        boolean revealComplete = bootCardSystem.isRevealComplete();
        if (revealComplete) {
            drawInstanceCounter(camera, fade);
            if (bootCardSystem.getWakeLineCount() > 0) {
                panelRenderer.drawPanel(camera, Speaker.AI,
                        bootCardSystem.getWakeSpeakerName(),
                        bootCardSystem.getWakeLines(), bootCardSystem.getWakeLineCount(),
                        StoryUiConstants.STORY_BOOT_PANEL_X, StoryUiConstants.STORY_BOOT_WAKE_Y,
                        StoryUiConstants.STORY_BOOT_PANEL_WIDTH,
                        StoryUiConstants.STORY_BOOT_WAKE_PANEL_HEIGHT,
                        fade, bootCardSystem.getElapsedSeconds());
            }
        }
    }

    /**
     * Pass 1 — the dark full-bleed background and, when this card offers a way onward, the CONTINUE
     * button's plate.  Both are Filled shapes, so they share one ShapeRenderer batch.
     */
    private void drawBackdropAndButton(OrthographicCamera camera, float fade) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        shapes.setColor(StoryUiConstants.STORY_BOOT_BACKDROP_R, StoryUiConstants.STORY_BOOT_BACKDROP_G,
                        StoryUiConstants.STORY_BOOT_BACKDROP_B,
                        StoryUiConstants.STORY_BOOT_BACKDROP_ALPHA * fade);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        // NO CONTINUE on a terminal ending card. The button the player has tapped a hundred times
        // simply is not there, and that absence is the ending (story/06-endings.md).
        if (bootCardSystem.isContinueAllowed()) {
            drawContinuePlate(fade);
        }

        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** The CONTINUE plate: an AI-amber border with a dim fill inset, matching the panel language. */
    private void drawContinuePlate(float fade) {
        int   speakerIndex = Speaker.AI.ordinal();
        float accentRed    = StoryUiConstants.SPEAKER_ACCENT_R[speakerIndex];
        float accentGreen  = StoryUiConstants.SPEAKER_ACCENT_G[speakerIndex];
        float accentBlue   = StoryUiConstants.SPEAKER_ACCENT_B[speakerIndex];
        float border       = StoryUiConstants.STORY_BOOT_CONTINUE_BORDER;

        shapes.setColor(accentRed, accentGreen, accentBlue, fade);
        shapes.rect(StoryUiConstants.STORY_BOOT_CONTINUE_X, StoryUiConstants.STORY_BOOT_CONTINUE_Y,
                    StoryUiConstants.STORY_BOOT_CONTINUE_WIDTH,
                    StoryUiConstants.STORY_BOOT_CONTINUE_HEIGHT);
        shapes.setColor(accentRed * StoryUiConstants.STORY_BOOT_CONTINUE_FILL_ALPHA,
                        accentGreen * StoryUiConstants.STORY_BOOT_CONTINUE_FILL_ALPHA,
                        accentBlue * StoryUiConstants.STORY_BOOT_CONTINUE_FILL_ALPHA,
                        fade);
        shapes.rect(StoryUiConstants.STORY_BOOT_CONTINUE_X + border,
                    StoryUiConstants.STORY_BOOT_CONTINUE_Y + border,
                    StoryUiConstants.STORY_BOOT_CONTINUE_WIDTH  - border * 2f,
                    StoryUiConstants.STORY_BOOT_CONTINUE_HEIGHT - border * 2f);
    }

    /**
     * Pass 2 — the instance counter and the CONTINUE label.  Both are plain text in one batch; the
     * counter is deliberately small and dim, because the number climbing is the horror and
     * annotating it would spoil it.
     */
    private void drawInstanceCounter(OrthographicCamera camera, float fade) {
        String counterText = bootCardSystem.getInstanceCounterText();
        boolean drawsButtonLabel = bootCardSystem.isContinueAllowed() && !continueLabel.isEmpty();
        if (counterText == null && !drawsButtonLabel) return;

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        if (counterText != null) {
            font.getData().setScale(StoryUiConstants.STORY_BOOT_COUNTER_TEXT_SIZE);
            scratchColor.set(StoryUiConstants.STORY_BOOT_COUNTER_R,
                             StoryUiConstants.STORY_BOOT_COUNTER_G,
                             StoryUiConstants.STORY_BOOT_COUNTER_B, fade);
            font.setColor(scratchColor);
            // Left-aligned with the panels' content gutter so the stack reads as one card. The
            // gutter is the same geometry StoryPanelRenderer draws its body text from.
            float contentLeft = StoryUiConstants.STORY_BOOT_PANEL_X
                    + StoryUiConstants.STORY_PANEL_PADDING
                    + StoryUiConstants.STORY_CHIP_ACCENT_BAR_WIDTH
                    + StoryUiConstants.STORY_CHIP_ELEMENT_GAP;
            font.draw(batch, counterText, contentLeft, StoryUiConstants.STORY_BOOT_COUNTER_TOP_Y);
        }

        if (drawsButtonLabel) {
            font.getData().setScale(StoryUiConstants.STORY_BOOT_CONTINUE_TEXT_SIZE);
            scratchColor.set(StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                             StoryUiConstants.STORY_BODY_B, fade);
            font.setColor(scratchColor);
            font.draw(batch, continueLabel, continueLabelX, continueLabelTopY);
        }

        // Restore shared font state for other renderers.
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /**
     * True when the given world point is inside the CONTINUE tap target.  Large by design — this is
     * a thumb target on a phone, and the player has just died, so it must be effortless to hit.
     */
    public static boolean isInsideContinueButton(float worldX, float worldY) {
        return worldX >= StoryUiConstants.STORY_BOOT_CONTINUE_X
            && worldX <= StoryUiConstants.STORY_BOOT_CONTINUE_X + StoryUiConstants.STORY_BOOT_CONTINUE_WIDTH
            && worldY >= StoryUiConstants.STORY_BOOT_CONTINUE_Y
            && worldY <= StoryUiConstants.STORY_BOOT_CONTINUE_Y + StoryUiConstants.STORY_BOOT_CONTINUE_HEIGHT;
    }

    @Override
    public void dispose() {
        panelRenderer.dispose();
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
