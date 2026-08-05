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
 * <p>Owns one {@link Disposable} — the {@link StoryPanelRenderer} primitive — and disposes it in
 * {@link #dispose()}.  The speaker stings are NOT owned here: {@link StoryAudio} is a single
 * world-owned instance shared by every story channel (order-7 Part D), injected via
 * {@link #setStoryAudio(StoryAudio)} and disposed by the world.
 */
public final class StoryBarkRenderer implements Renderable, Disposable {

    private final StoryPanelRenderer panelRenderer;
    private BarkSystem               barkSystem;
    private StoryAudio               storyAudio;

    public StoryBarkRenderer() {
        this.panelRenderer = new StoryPanelRenderer();
    }

    /**
     * Binds the world's shared story audio.  Null (or never set) means silence — the panel says
     * everything the sting says, so a missing sound loses nothing.
     */
    public void setStoryAudio(StoryAudio storyAudio) {
        this.storyAudio = storyAudio;
    }

    /** Binds the headless bark brain this renderer reads from.  Null hides the layer entirely. */
    /**
     * Applies the order-6 accessibility settings (text size, reduced motion) to the shared story
     * panel this renderer draws with.  Call from the update path when a setting changes.
     */
    public void applyAccessibilitySettings(float bodyTextScale, boolean reduceMotion) {
        panelRenderer.applyAccessibilitySettings(bodyTextScale, reduceMotion);
    }

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
        if (appeared != null && storyAudio != null) {
            storyAudio.playSpeakerSting(appeared);
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
                barkSystem.getActiveElapsedSeconds(),
                true);   // a bark never auto-dismisses, so it always shows its close X
    }

    /**
     * True when the given world point is inside the bark's close-X hit rect — grown past the drawn
     * glyph by {@code STORY_BARK_CLOSE_TOUCH_PADDING} so it stays a comfortable thumb target.
     */
    public static boolean isInsideCloseButton(float worldX, float worldY) {
        float halfExtent = StoryUiConstants.STORY_BARK_CLOSE_GLYPH_SIZE / 2f
                + StoryUiConstants.STORY_BARK_CLOSE_TOUCH_PADDING;
        return Math.abs(worldX - StoryUiConstants.STORY_BARK_CLOSE_CENTER_X) <= halfExtent
            && Math.abs(worldY - StoryUiConstants.STORY_BARK_CLOSE_CENTER_Y) <= halfExtent;
    }

    /** True when the given world point is inside the bark panel itself (where a swipe may start). */
    public static boolean isInsidePanel(float worldX, float worldY) {
        return worldX >= StoryUiConstants.STORY_BARK_X
            && worldX <= StoryUiConstants.STORY_BARK_X + StoryUiConstants.STORY_BARK_WIDTH
            && worldY >= StoryUiConstants.STORY_BARK_Y
            && worldY <= StoryUiConstants.STORY_BARK_TOP_Y;
    }

    @Override
    public void dispose() {
        panelRenderer.dispose();   // storyAudio is world-owned and disposed there
    }
}
