package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.OrthographicCamera;

/** Implemented by anything that draws itself to the world each frame. */
public interface Renderable {
    void render(OrthographicCamera camera);
}
