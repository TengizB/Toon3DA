package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import ge.tbegvadze.toon3d.narrative.StoryStrings;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * The one place the externalised story string table is loaded from assets (localisation rule,
 * order-7 Part C).  Lives on the render side because it touches LibGDX file IO, so the narrative
 * package stays headless.
 *
 * <p>Reads {@link StoryUiConstants#STORY_STRINGS_ASSET_PATH} and falls back to
 * {@link StoryStrings#defaults()} whenever the asset is missing or unreadable — the game must never
 * fail to boot over a text file, and a missing individual id still resolves to a visible
 * {@code !id!} marker rather than blank.
 */
public final class StoryStringsLoader {

    private StoryStringsLoader() {}

    /** Loads the shipped table, or the built-in defaults when it cannot be read. */
    public static StoryStrings load() {
        try {
            if (Gdx.files != null
                    && Gdx.files.internal(StoryUiConstants.STORY_STRINGS_ASSET_PATH).exists()) {
                return StoryStrings.fromProperties(
                        Gdx.files.internal(StoryUiConstants.STORY_STRINGS_ASSET_PATH)
                                 .readString("UTF-8"));
            }
        } catch (Exception loadFailure) {
            if (Gdx.app != null) {
                Gdx.app.error("StoryStrings", "falling back to built-in defaults: "
                        + loadFailure.getMessage());
            }
        }
        return StoryStrings.defaults();
    }
}
