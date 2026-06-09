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

    @Override
    public void dispose() {
        world.dispose();
    }
}
