package ge.tbegvadze.toon3d.render.tilesetgfx;

import ge.tbegvadze.toon3d.render.PropRenderer;
import ge.tbegvadze.toon3d.render.WallRenderer;
import ge.tbegvadze.toon3d.tileset.TilesetRegistries;

import java.util.Set;
import java.util.TreeSet;

/**
 * The one place the render-side {@link TextureGeneratorRegistry} is populated (symbol/sprite-reuse
 * order-6) — the render-layer twin of {@code TilesetRegistries.bootstrap()} and the sibling of
 * {@code RouteIconBootstrap}. It maps EVERY v1 sprite id from the art catalog to the procedural code that
 * draws it: the wall + column generators live with the pixel code in {@code WallRenderer}, the solid-prop
 * + floor-decal generators in {@code PropRenderer}, and each renderer contributes its own generators via
 * {@code registerTextureGenerators(registry)} so the mapping sits next to the routine it names.
 *
 * <p>Holds the process-wide generator singleton (mirroring how {@code TilesetRegistries} holds the sprite
 * registry). {@link #bootstrap()} is idempotent — safe across a death→new-run rebuild — and asserts the
 * generator id set EQUALS the sprite catalog id set, so a sprite added without a generator (or a
 * generator without a sprite) fails loudly at startup instead of blowing up mid-level when a palette first
 * references the orphan.
 *
 * <p>How to add environment art (once order-9's recipe lands): register the sprite in
 * {@code TilesetRegistries} AND its generator in the matching renderer's {@code registerTextureGenerators}
 * — the startup assertion enforces you did both.
 */
public final class TilesetGfxBootstrap {

    private static final TextureGeneratorRegistry GENERATORS = new TextureGeneratorRegistry();
    private static boolean bootstrapped = false;

    private TilesetGfxBootstrap() {}

    /** The shared texture-generator registry the render layer realizes per-level textures from. */
    public static TextureGeneratorRegistry generators() {
        return GENERATORS;
    }

    /** Whether {@link #bootstrap()} has already run. */
    public static boolean isBootstrapped() {
        return bootstrapped;
    }

    /**
     * Registers every v1 sprite id's procedural generator into the shared registry and verifies it stays
     * in lockstep with the art catalog. Idempotent: the second call is a no-op, so a repeated
     * {@code World.create()} (e.g. after a death-screen restart) is safe. Invoked once from the
     * {@code World.create()} path, next to {@code TilesetRegistries.bootstrap()}.
     */
    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        // The catalog must exist before we can cross-check ids against it (idempotent).
        TilesetRegistries.bootstrap();
        register(GENERATORS);
        assertGeneratorsMatchCatalog(GENERATORS);
        bootstrapped = true;
    }

    /**
     * Registers the full v1 generator set into the given registry. Exposed (package-private) so tests can
     * populate a fresh registry without touching the shared singleton. WallRenderer contributes the walls
     * + round column; PropRenderer contributes the solid props + floor decals.
     */
    static void register(TextureGeneratorRegistry registry) {
        WallRenderer.registerTextureGenerators(registry);
        PropRenderer.registerTextureGenerators(registry);
    }

    /**
     * Fails loudly if the generator id set and the sprite-catalog id set differ in either direction. Keeps
     * the render-side companion honest: every {@code EnvironmentSpriteDefinition} must have a matching
     * texture generator and vice-versa.
     */
    static void assertGeneratorsMatchCatalog(TextureGeneratorRegistry registry) {
        Set<String> catalogIds   = TilesetRegistries.sprites().ids();
        Set<String> generatorIds = registry.ids();
        if (!catalogIds.equals(generatorIds)) {
            Set<String> spritesWithoutGenerator = new TreeSet<>(catalogIds);
            spritesWithoutGenerator.removeAll(generatorIds);
            Set<String> generatorsWithoutSprite = new TreeSet<>(generatorIds);
            generatorsWithoutSprite.removeAll(catalogIds);
            throw new IllegalStateException(
                    "Tileset texture-generator set does not match the sprite catalog. "
                            + "Sprites missing a generator: " + spritesWithoutGenerator + "; "
                            + "generators missing a sprite: " + generatorsWithoutSprite);
        }
    }
}
