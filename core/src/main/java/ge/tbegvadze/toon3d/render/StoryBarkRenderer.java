package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.narrative.BarkSystem;
import ge.tbegvadze.toon3d.narrative.Speaker;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Draws the BARK — the non-blocking one-liner channel (Story UI order-2) — as the order-1 story
 * panel at the fixed bark anchor ({@code StoryUiConstants.STORY_BARK_*}).  Same spot every time so
 * the eye learns where to look; clear of the HUD band, the thumb touch clusters, the weapon sprite
 * and the mini-map (guarded by a test).
 *
 * <p>This class is presentation ONLY.  Every decision — which line, when, for how long, and the
 * fade fraction — is made headlessly by {@link BarkSystem}; the renderer just asks it what is on
 * screen.  Nothing is allocated on the draw path: the body lines arrive pre-resolved and pre-wrapped
 * from the system, and the panel primitive owns its own reusable batch/shapes/font.
 *
 * <p>Owns two {@link Disposable}s — the shared {@link StoryPanelRenderer} primitive and the
 * per-speaker {@link StorySpeakerStings} — and disposes both in {@link #dispose()}.
 */
public final class StoryBarkRenderer implements Renderable, Disposable {

    private final StoryPanelRenderer panelRenderer;
    private final StorySpeakerStings speakerStings;
    private BarkSystem               barkSystem;

    public StoryBarkRenderer() {
        this.panelRenderer = new StoryPanelRenderer();
        this.speakerStings = new StorySpeakerStings();
    }

    /** Binds the headless bark brain this renderer reads from.  Null hides the layer entirely. */
    public void setBarkSystem(BarkSystem barkSystem) {
        this.barkSystem = barkSystem;
    }

    /**
     * Plays the speaker sting for a bark that just appeared, if any.  Call from the update path
     * (never from render) so audio is a simulation side effect, not a draw-time one.
     */
    public void playPendingSpeakerSting() {
        if (barkSystem == null) return;
        Speaker appeared = barkSystem.consumeJustAppearedSpeaker();
        if (appeared != null) {
            speakerStings.play(appeared);
        }
    }

    @Override
    public void render(OrthographicCamera camera) {
        if (barkSystem == null || !barkSystem.hasActiveBark()) return;
        panelRenderer.drawPanel(camera,
                barkSystem.getActiveSpeaker(),
                barkSystem.getActiveSpeakerName(),
                barkSystem.getActiveLines(),
                barkSystem.getActiveLineCount(),
                StoryUiConstants.STORY_BARK_X, StoryUiConstants.STORY_BARK_Y,
                StoryUiConstants.STORY_BARK_WIDTH, StoryUiConstants.STORY_BARK_HEIGHT,
                barkSystem.getVisibleFraction(),
                barkSystem.getActiveElapsedSeconds());
    }

    @Override
    public void dispose() {
        panelRenderer.dispose();
        speakerStings.dispose();
    }
}
