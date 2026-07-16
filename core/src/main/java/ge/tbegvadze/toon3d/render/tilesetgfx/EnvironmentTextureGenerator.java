package ge.tbegvadze.toon3d.render.tilesetgfx;

import com.badlogic.gdx.graphics.Texture;

/**
 * Produces the {@link Texture} for ONE environment sprite id (symbol/sprite-reuse order-6). This is the
 * boundary where a stable sprite id ("wall_cryo", "prop_locker", "decal_blood") becomes pixels: the
 * BODY of each generator is the existing procedural generation code that already draws that art —
 * {@code WallRenderer.generate*WallTexture()} / {@code PropRenderer.generate*Texture()} — so nothing
 * about the art changes; order-6 only moves WHERE the texture lives and WHEN it is freed.
 *
 * <p>Functional interface: a generator is usually a method reference to one of those procedural
 * routines. {@link #generate()} MUST run on the GL thread (it creates a GL {@link Texture}); the game
 * only ever calls it at level load, never inside {@code render()}, honouring the no-render-loop-
 * allocation rule.
 */
@FunctionalInterface
public interface EnvironmentTextureGenerator {

    /**
     * Builds the texture for this sprite. A fresh {@link Texture} each call — the caller
     * ({@link EnvironmentTextureSet}) owns it and is responsible for disposing it on level teardown.
     */
    Texture generate();
}
