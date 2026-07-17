package ge.tbegvadze.toon3d.render.tilesetgfx;

import ge.tbegvadze.toon3d.tileset.LevelPalette;
import ge.tbegvadze.toon3d.tileset.LevelPalettes;
import ge.tbegvadze.toon3d.tileset.TilesetRegistries;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Order-6 tests for the render-side texture-generator layer. Headless: they register generators (method
 * references / lambdas — never INVOKED, so no GL context is needed) and inspect the id sets. The actual
 * pixel realization ({@link EnvironmentTextureSet#realize}) needs a GL context and is verified in-game.
 *
 * <p>The key guarantee proven here is the startup invariant: the texture-generator id set EQUALS the
 * sprite-catalog id set (no sprite without a generator, no generator without a sprite), and the LEGACY
 * palette references the WHOLE catalog — so a legacy level realizes exactly today's full wall/prop set,
 * the "no visual change in order-6" contract.
 */
class TextureGeneratorRegistryTest {

    @Test
    void generatorIdSetMatchesSpriteCatalogExactly() {
        TilesetRegistries.bootstrap();
        TextureGeneratorRegistry generators = new TextureGeneratorRegistry();
        TilesetGfxBootstrap.register(generators);

        // Every registered sprite has a generator and vice-versa — the startup assertion's contract.
        assertEquals(TilesetRegistries.sprites().ids(), generators.ids());
        assertDoesNotThrow(() -> TilesetGfxBootstrap.assertGeneratorsMatchCatalog(generators));
        assertEquals(TilesetRegistries.sprites().size(), generators.size());
    }

    @Test
    void sharedBootstrapIsIdempotentAndPassesTheAssertion() {
        assertDoesNotThrow(TilesetGfxBootstrap::bootstrap);
        assertDoesNotThrow(TilesetGfxBootstrap::bootstrap); // second call is a no-op, must not re-throw
        assertTrue(TilesetGfxBootstrap.isBootstrapped());
        assertEquals(TilesetRegistries.sprites().ids(), TilesetGfxBootstrap.generators().ids());
    }

    @Test
    void legacyPaletteReferencesTheWholeCatalogExceptVarietyOnlyArt() {
        // order-6 "no visual change" contract, NARROWED by order-8: a legacy level's palette references
        // every registered sprite exactly once EXCEPT the variety-only additions that no legacy symbol
        // maps to (e.g. "column_square" — REQUIREMENT PROOF 2, an alternative COLUMN sprite reached only
        // through the allocator). Legacy still realizes today's full historic wall/prop set.
        LevelPalette legacy = LevelPalettes.legacy();
        Set<String> referenced = legacy.distinctSpriteIds();
        Set<String> expected = new java.util.LinkedHashSet<>(TilesetRegistries.sprites().ids());
        expected.removeAll(TilesetRegistries.VARIETY_ONLY_SPRITE_IDS);
        assertEquals(expected, referenced);
    }

    @Test
    void registryRejectsDuplicateAndUnknownIds() {
        TextureGeneratorRegistry registry = new TextureGeneratorRegistry();
        // Generators are lambdas that never run here (returning null would only matter if invoked).
        registry.register("sprite_a", () -> null);
        assertTrue(registry.has("sprite_a"));
        assertFalse(registry.has("sprite_missing"));
        // Duplicate id fails loudly (route-registry discipline).
        assertThrows(IllegalStateException.class, () -> registry.register("sprite_a", () -> null));
        // generate() on an unregistered id throws BEFORE invoking any generator, so no GL needed.
        assertThrows(IllegalArgumentException.class, () -> registry.generate("sprite_missing"));
    }

    @Test
    void idsPreserveRegistrationOrder() {
        TextureGeneratorRegistry registry = new TextureGeneratorRegistry();
        registry.register("first", () -> null);
        registry.register("second", () -> null);
        registry.register("third", () -> null);
        assertEquals(List.of("first", "second", "third"), new ArrayList<>(registry.ids()));
    }
}
