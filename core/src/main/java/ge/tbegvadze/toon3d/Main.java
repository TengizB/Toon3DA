package ge.tbegvadze.toon3d;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import ge.tbegvadze.toon3d.world.World;

import static ge.tbegvadze.toon3d.util.Constants.WORLD_HEIGHT;
import static ge.tbegvadze.toon3d.util.Constants.WORLD_WIDTH;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private OrthographicCamera camera;
    private FitViewport viewport;
    private World world;

    @Override
    public void create() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        world = new World(System.currentTimeMillis());
        world.initTouchControls(viewport);
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0);
        // camera.update() is called once here because the camera is static — position,
        // zoom, and direction never change after create(). FitViewport.update() in
        // resize() also calls camera.update() internally. If the camera is ever made
        // dynamic (pan, zoom, shake), move camera.update() into render() instead.
        camera.update();
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        // INVARIANT: apply() must be called every frame.
        // FitViewport.apply() calls glViewport() and (when centerCamera=true) glScissor()
        // to restrict drawing to the letterboxed play area. Without it, any path that
        // disposes and recreates the World (e.g. death → new run) leaves the GL viewport
        // pointing at the raw screen dimensions, which stretches every subsequent draw
        // and breaks touch unprojection (FitViewport.unproject uses its stored offset/size,
        // not the GL viewport, so the two diverge). resize() alone is not enough: it fires
        // only on OS-triggered size events, not on in-frame state resets.
        viewport.apply();
        world.update(Gdx.graphics.getDeltaTime());
        if (world.isResetRequested()) {
            world.dispose();
            world = new World(System.currentTimeMillis());
            world.initTouchControls(viewport);
            return;  // New world renders on the next frame
        }
        world.render(camera);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true); // true = keep (0,0) at bottom-left
    }

    /**
     * The OS took the screen — a call, a notification, the player switching apps (Story UI order-7
     * Part E). On Android this also fires just before the process may be killed.
     *
     * <p>The game is turn-based, so the simulation is already frozen between actions and needs no
     * pausing of its own; what the world does here is drop transient touch state, so a finger that
     * never got to send its release cannot commit an action when the app comes back.
     */
    @Override
    public void pause() {
        if (world != null) world.onApplicationPause();
    }

    /** Back in the foreground: re-push the story renderers' cached text scales and drop stale touch. */
    @Override
    public void resume() {
        if (world != null) world.onApplicationResume();
    }

    @Override
    public void dispose() {
        world.dispose();
    }
}
