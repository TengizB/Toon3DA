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
    void legacyMapsWallsThroughSetAAndPropsToTheirHistoricSprites() {
        LevelPalette legacy = LevelPalettes.legacy();
        // generated-wall-art order 2: a wall symbol names a SLOT; hand levels read set A through the slot
        // table (variant 0 — the renderer picks the BASE variant per face). Props and decals keep the
        // historic 1:1 table.
        assertEquals("outer_facility", legacy.wallSetId());
        assertEquals("wall_a_containment_base_0", legacy.spriteIdOf('Z'));
        assertEquals("wall_a_hall_base_0", legacy.spriteIdOf('x'));
        assertEquals("wall_a_medical_base_0", legacy.spriteIdOf('M'));
        assertEquals("wall_a_hall_light", legacy.spriteIdOf('S'));
        assertEquals("column_a_round", legacy.spriteIdOf('P'));
        assertEquals("prop_locker", legacy.spriteIdOf('L'));
        assertEquals("prop_vendor", legacy.spriteIdOf('@'));
        assertEquals("decal_blood", legacy.spriteIdOf('.'));
        assertEquals("decal_corpse", legacy.spriteIdOf('m'));
    }

    @Test
    void regionPaletteRebindsOnlyTheWallsAndTheColumn() {
        // The palette every generated floor gets from the LevelFinisher: the base palette's props and decals
        // untouched, every wall symbol and 'P' rebound to the floor's REAL region set (an alias resolves to
        // the set it borrows), and the set recorded on the palette.
        LevelPalette legacy = LevelPalettes.legacy();
        RegionWallSetRegistry sets = RegionWallSets.registry();
        for (String setId : sets.ids()) {
            RegionWallSet set = sets.resolve(setId);
            LevelPalette region = RegionPaletteBuilder.build(set, legacy);
            assertEquals(set.id(), region.wallSetId());
            for (char symbol = 32; symbol <= 126; symbol++) {
                if (WallSlotTable.isWallSymbol(symbol) || symbol == 'P') continue;
                assertEquals(legacy.spriteIdOf(symbol), region.spriteIdOf(symbol),
                        "symbol '" + symbol + "' drifted from the base palette for set " + setId);
            }
        }
    }

    @Test
    void regionPaletteBindsEveryWallSymbolToAFaceOfItsOwnSet() {
        // The guard "every wall symbol resolves to a registered sprite", per region set (series rule W10):
        // the renderer realizes faces from the floor's atlas, so a wall bound outside the set would be a
        // missing wall. Every wall symbol is a WALL bound to one of the set's wall ids; 'P' to a set column.
        RegionWallSetRegistry sets = RegionWallSets.registry();
        for (String setId : sets.ids()) {
            RegionWallSet set = sets.resolve(setId);
            LevelPalette region = RegionPaletteBuilder.build(set, LevelPalettes.legacy());
            for (char symbol : WallSlotTable.symbols().toCharArray()) {
                String spriteId = region.spriteIdOf(symbol);
                assertNotNull(spriteId, setId + " leaves '" + symbol + "' unbound");
                assertEquals(TileCategory.WALL, region.categoryOf(symbol));
                assertTrue(set.allWallIds().contains(spriteId),
                        setId + " binds '" + symbol + "' to " + spriteId + ", outside its set");
            }
            assertEquals(TileCategory.COLUMN, region.categoryOf('P'));
            assertTrue(set.columnIds().contains(region.spriteIdOf('P')));
        }
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
        // symbol's binding, so no sprite is stranded and every symbol resolves to a real registered id. Walls
        // and 'P' resolve to region set A instead (generated-wall-art order 2).
        LevelPalette legacy = LevelPalettes.legacy();
        EnvironmentSpriteRegistry registry = TilesetRegistries.sprites();

        int boundSymbols = 0;
        java.util.Set<String> boundSpriteIds = new java.util.HashSet<>();
        for (char candidate = 32; candidate <= 126; candidate++) {
            String spriteId = legacy.spriteIdOf(candidate);
            if (spriteId == null) {
                continue;
            }
            if (WallSlotTable.isWallSymbol(candidate) || candidate == 'P') {
                assertTrue(legacy.spriteIdOf(candidate).startsWith("wall_a_")
                        || legacy.spriteIdOf(candidate).startsWith("column_a_"));
                continue;
            }
            boundSymbols++;
            assertTrue(registry.contains(spriteId),
                    "legacy bound '" + candidate + "' to unregistered sprite " + spriteId);
            boundSpriteIds.add(spriteId);
        }
        // One symbol per sprite (a 1:1 legacy table), covering the full registry EXCEPT the order-8
        // variety-only art that no legacy symbol maps to (none since walls left the catalog).
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
