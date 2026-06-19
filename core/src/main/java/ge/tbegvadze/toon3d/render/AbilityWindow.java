package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.Disposable;

/** Popup for weapon ability detail. Stub — full implementation deferred to Part 5. */
final class AbilityWindow implements Disposable {

    boolean isOpen()                                          { return false; }
    boolean containsPoint(float worldX, float worldY)        { return false; }
    boolean handleTouch(float worldX, float worldY)          { return false; }
    void    render(OrthographicCamera camera, float clock)   {}
    void    open()                                            {}
    void    close()                                           {}

    @Override
    public void dispose() {}
}
