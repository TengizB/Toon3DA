package ge.tbegvadze.toon3d.tileset;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Order-6 support: {@link LevelPalette#distinctSpriteIds()} is what the render layer realizes per level, so
 * its de-duplication IS the "subset per level" memory story. These headless tests prove that several
 * symbols sharing one sprite id collapse to a single realized id (one texture, not one per symbol), and
 * that the legacy palette's distinct set is the whole catalog. Uses the package-private palette builder.
 */
class LevelPaletteDistinctSpriteIdsTest {

    @Test
    void sharedSpriteIdCollapsesToOne() {
        // Three accent-wall symbols all bound to the SAME sprite id -> one distinct id -> one texture.
        LevelPalette palette = LevelPalette.builder()
                .bind('c', TileCategory.WALL, "wall_plain")
                .bind('v', TileCategory.WALL, "wall_plain")
                .bind('t', TileCategory.WALL, "wall_plain")
                .bind('L', TileCategory.SOLID_PROP, "prop_locker")
                .build();
        Set<String> distinct = palette.distinctSpriteIds();
        assertEquals(Set.of("wall_plain", "prop_locker"), distinct);
        assertEquals(2, distinct.size());
    }

    @Test
    void distinctSetIsFarSmallerThanTheWholeCatalog() {
        TilesetRegistries.bootstrap(); // ensure the catalog is populated before comparing sizes
        // A small hand palette realizes a small subset, never the whole library.
        LevelPalette palette = LevelPalette.builder()
                .bind('x', TileCategory.WALL, "wall_plain")
                .bind('Z', TileCategory.WALL, "wall_cryo")
                .bind('P', TileCategory.COLUMN, "column_round")
                .build();
        assertEquals(3, palette.distinctSpriteIds().size());
        assertTrue(palette.distinctSpriteIds().size() < TilesetRegistries.sprites().size());
    }

    @Test
    void legacyDistinctSetIsTheWholeCatalogExceptVarietyOnlyArt() {
        // Narrowed by order-8: the catalog now carries variety-only art (e.g. "column_square") that no
        // legacy symbol maps to, so the legacy distinct set is the whole catalog MINUS that set.
        LevelPalette legacy = LevelPalettes.legacy();
        java.util.Set<String> expected =
                new java.util.LinkedHashSet<>(TilesetRegistries.sprites().ids());
        expected.removeAll(TilesetRegistries.VARIETY_ONLY_SPRITE_IDS);
        assertEquals(expected, legacy.distinctSpriteIds());
    }
}
