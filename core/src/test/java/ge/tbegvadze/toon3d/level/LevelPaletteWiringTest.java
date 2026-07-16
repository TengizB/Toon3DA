package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.LevelPalette;
import ge.tbegvadze.toon3d.tileset.LevelPalettes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Order-3 wiring: a {@code Level} built through the legacy-defaulting constructor carries the shared
 * {@link LevelPalettes#legacy()} palette, and an explicit palette is carried through unchanged. Lives in
 * the {@code level} package because the {@code Level} constructor is package-private. Headless.
 */
class LevelPaletteWiringTest {

    @Test
    void defaultsToLegacyWhenNoPaletteGiven() {
        Level level = new Level(new char[][]{{'x'}}, new ArrayList<>(), new ArrayList<>());
        assertSame(LevelPalettes.legacy(), level.getPalette());
    }

    @Test
    void carriesExplicitPalette() {
        LevelPalette explicit = LevelPalettes.legacy();
        Level level = new Level(new char[][]{{'x'}}, new ArrayList<>(), new ArrayList<>(), explicit);
        assertSame(explicit, level.getPalette());
    }
}
