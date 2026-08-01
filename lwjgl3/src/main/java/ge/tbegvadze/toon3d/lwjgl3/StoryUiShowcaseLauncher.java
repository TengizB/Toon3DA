package ge.tbegvadze.toon3d.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import ge.tbegvadze.toon3d.util.Constants;

/**
 * Desktop dev launcher for the Story UI order-1 speaker showcase ({@link StoryUiShowcase}) —
 * the definition-of-done test screen.  Development testing only; the game ships on Android.
 *
 * Run with:  ./gradlew lwjgl3:storyUiShowcase   (or launch this main directly from an IDE).
 */
public final class StoryUiShowcaseLauncher {

    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return;
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new StoryUiShowcase(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Toon3DA — Story UI Showcase");
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        // 16:9 window matching the 1280x720 world so the letterboxed layout reads true.
        configuration.setWindowedMode((int) Constants.WORLD_WIDTH, (int) Constants.WORLD_HEIGHT);
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}
