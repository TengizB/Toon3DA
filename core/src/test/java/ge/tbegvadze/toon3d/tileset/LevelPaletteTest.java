package ge.tbegvadze.toon3d.tileset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Order-3 tests: the LEGACY {@link LevelPalette} reproduces today's exact global symbol → sprite mapping
 * and non-environment symbols (doors, pickups, ammo, hazards, enemy spawns, start, exit) resolve to null.
 * The {@code Level} default-to-legacy wiring is covered in the level package (package-private
 * constructor). Headless — no LibGDX render calls.
 */
class LevelPaletteTest {

    @Test
    void legacyMapsAccentWallToItsHistoricSprite() {
        LevelPalette legacy = LevelPalettes.legacy();
        // Spot-checks across the categories, one per category, matching the historic 1:1 table.
        assertEquals("wall_cryo", legacy.spriteIdOf('Z'));
        assertEquals("wall_plain", legacy.spriteIdOf('x'));
        assertEquals("wall_medical", legacy.spriteIdOf('M'));
        assertEquals("column_round", legacy.spriteIdOf('P'));
        assertEquals("prop_locker", legacy.spriteIdOf('L'));
        assertEquals("prop_vendor", legacy.spriteIdOf('@'));
        assertEquals("decal_blood", legacy.spriteIdOf('.'));
        assertEquals("decal_corpse", legacy.spriteIdOf('m'));
    }

    @Test
    void generatedWithBaseWallVariesOnlyTheBaseWall() {
        // The palette every non-dungeon generator uses: identical to legacy EXCEPT the bulk wall symbol
        // 'x', which becomes a seed-chosen base wall. This is how ALL generators (caverns, corridors, boss
        // arenas, special rooms, the staging room, ...) get base-wall variety without changing their other
        // art. Hand-crafted levels keep legacy (covered above), so they never vary.
        LevelPalette legacy = LevelPalettes.legacy();
        SymbolBudget budget = SymbolBudget.standard();
        java.util.Set<String> baseWalls = new java.util.HashSet<>(budget.baseWallSpriteIds());
        java.util.Set<String> observed = new java.util.HashSet<>();

        for (long seed = 0; seed < 200; seed++) {
            LevelPalette generated = LevelPalettes.generatedWithBaseWall(seed);
            // 'x' resolves to a registered base wall in the WALL category.
            assertTrue(baseWalls.contains(generated.spriteIdOf('x')),
                    "generated 'x' is not a base wall at seed " + seed + ": " + generated.spriteIdOf('x'));
            assertEquals(TileCategory.WALL, generated.categoryOf('x'));
            observed.add(generated.spriteIdOf('x'));
            // Deterministic: same seed => same base wall.
            assertEquals(generated.spriteIdOf('x'),
                    LevelPalettes.generatedWithBaseWall(seed).spriteIdOf('x'));
            // Every OTHER environment symbol matches legacy exactly (accents/props/decals untouched).
            for (char symbol = 32; symbol <= 126; symbol++) {
                if (symbol == 'x') {
                    continue;
                }
                assertEquals(legacy.spriteIdOf(symbol), generated.spriteIdOf(symbol),
                        "symbol '" + symbol + "' drifted from legacy at seed " + seed);
            }
        }
        // Across seeds the base wall actually varies (the alternates are used, not just the default).
        assertTrue(observed.size() > 1, "base wall never varied across 200 seeds: " + observed);
        assertTrue(baseWalls.containsAll(observed), "an unexpected base wall was chosen: " + observed);
        // Legacy itself is unchanged — hand levels keep the default plain wall.
        assertEquals("wall_plain", legacy.spriteIdOf('x'));
    }

    @Test
    void generatedBaseWallSpriteIsAlwaysRealizableAndPlainIsNotAssumed() {
        // Regression guard for the startup crash: WallRenderer defaults its wall array to the level's BASE
        // WALL sprite, which MUST be in the palette's realized sprite set. It must NOT assume "wall_plain"
        // is realized — once 'x' varies to an alternate base wall, "wall_plain" is no longer referenced by
        // the level and EnvironmentTextureSet.textureFor("wall_plain") would throw (the crash). This test
        // proves (a) the base-wall sprite is always among distinctSpriteIds (safe to realize), and (b) on
        // many seeds "wall_plain" is genuinely absent — so hardcoding it as the default is a bug.
        int seedsWherePlainAbsent = 0;
        for (long seed = 0; seed < 300; seed++) {
            LevelPalette generated = LevelPalettes.generatedWithBaseWall(seed);
            String baseWallSprite = generated.spriteIdOf('x');
            assertNotNull(baseWallSprite);
            assertTrue(generated.distinctSpriteIds().contains(baseWallSprite),
                    "base wall " + baseWallSprite + " not realizable at seed " + seed);
            if (!"wall_plain".equals(baseWallSprite)
                    && !generated.distinctSpriteIds().contains("wall_plain")) {
                seedsWherePlainAbsent++;
            }
        }
        assertTrue(seedsWherePlainAbsent > 0,
                "expected some generated levels to omit wall_plain from the realized set");
        // The legacy (hand-level) palette always keeps wall_plain realizable.
        assertTrue(LevelPalettes.legacy().distinctSpriteIds().contains("wall_plain"));
    }

    @Test
    void legacyBindsEachSymbolToItsCategory() {
        LevelPalette legacy = LevelPalettes.legacy();
        assertEquals(TileCategory.WALL, legacy.categoryOf('Z'));
        assertEquals(TileCategory.COLUMN, legacy.categoryOf('P'));
        assertEquals(TileCategory.SOLID_PROP, legacy.categoryOf('L'));
        assertEquals(TileCategory.SOLID_PROP, legacy.categoryOf('@'));
        assertEquals(TileCategory.FLOOR_DECAL, legacy.categoryOf('.'));
    }

    @Test
    void legacyBindsEveryRegisteredEnvironmentSprite() {
        // The palette must cover the whole art catalog: every sprite id in the registry appears as some
        // symbol's binding, so no sprite is stranded and every symbol resolves to a real registered id.
        LevelPalette legacy = LevelPalettes.legacy();
        EnvironmentSpriteRegistry registry = TilesetRegistries.sprites();

        int boundSymbols = 0;
        java.util.Set<String> boundSpriteIds = new java.util.HashSet<>();
        for (char candidate = 32; candidate <= 126; candidate++) {
            String spriteId = legacy.spriteIdOf(candidate);
            if (spriteId == null) {
                continue;
            }
            boundSymbols++;
            assertTrue(registry.contains(spriteId),
                    "legacy bound '" + candidate + "' to unregistered sprite " + spriteId);
            boundSpriteIds.add(spriteId);
        }
        // One symbol per sprite (a 1:1 legacy table), covering the full registry EXCEPT the order-8
        // variety-only art (e.g. "column_square") that no legacy symbol maps to.
        int legacyCatalogSize = registry.size() - TilesetRegistries.VARIETY_ONLY_SPRITE_IDS.size();
        assertEquals(legacyCatalogSize, boundSymbols);
        assertEquals(legacyCatalogSize, boundSpriteIds.size());
        for (String varietyOnlyId : TilesetRegistries.VARIETY_ONLY_SPRITE_IDS) {
            assertFalse(boundSpriteIds.contains(varietyOnlyId),
                    "legacy palette must not bind variety-only sprite " + varietyOnlyId);
        }
    }

    @Test
    void hasBindingTracksEnvironmentSymbolsOnly() {
        LevelPalette legacy = LevelPalettes.legacy();
        assertTrue(legacy.hasBinding('Z'));
        assertTrue(legacy.hasBinding('P'));
        assertFalse(legacy.hasBinding('d'));   // door
        assertFalse(legacy.hasBinding('r'));   // keycard pickup
    }

    @Test
    void nonEnvironmentSymbolsResolveToNull() {
        LevelPalette legacy = LevelPalettes.legacy();
        // Doors, pickups, ammo, hazards, enemy spawns, start, and exit are FIXED gameplay symbols
        // handled outside the palette — they have no category and no sprite here.
        char[] nonEnvironment = {'d', 'R', 'Y', 'B', 'r', 'y', 'b', '+', 'H', 'a', 'A',
                '6', '7', '8', '9', '0', 'i', 'q', '1', '2', '3', '4', '5', 'p', '>', ' '};
        for (char symbol : nonEnvironment) {
            assertNull(legacy.spriteIdOf(symbol), "expected no sprite for '" + symbol + "'");
            assertNull(legacy.categoryOf(symbol), "expected no category for '" + symbol + "'");
            assertFalse(legacy.hasBinding(symbol), "expected no binding for '" + symbol + "'");
        }
    }

    @Test
    void legacyIsMemoisedSingleton() {
        assertSame(LevelPalettes.legacy(), LevelPalettes.legacy());
    }
}
