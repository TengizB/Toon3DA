package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.level.RoomBlueprint;
import ge.tbegvadze.toon3d.level.RoomBlueprintRegistry;
import ge.tbegvadze.toon3d.level.RoomBlueprints;
import ge.tbegvadze.toon3d.level.SpecialRoomPlanner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * order-9 EXTENSIBILITY ACCEPTANCE — proves the two recipes end-to-end with the real additions made in
 * order-9 (docs/environment-tileset-system.txt §9), beyond the registry-integrity tests elsewhere:
 *
 * <ul>
 *   <li>RECIPE A (walls, since generated-wall-art order 2) — a region wall set is ONE registry row (a real
 *       set, or an alias that borrows one). This test proves a row added by registration alone reaches a
 *       floor's palette: every wall symbol and the column resolve to the set it names.</li>
 *   <li>RECIPE B — "supply_cache" was added by registration alone (one RoomBlueprint row). This test proves
 *       that row alone puts it in the equal-chance special pool every combat floor draws from, with its size
 *       window and kit, claiming exactly its kit's sprites (level-generation-overhaul).</li>
 * </ul>
 *
 * Headless — no LibGDX render calls (the allocator and blueprints are pure/headless).
 */
class TilesetExtensibilityAcceptanceTest {

    @Test
    void recipeA_aRegionSetRowAloneReachesAFloorPalette() {
        // One alias row, registered alone in a fresh registry beside set A, is enough for a floor of that set
        // to resolve every wall symbol and 'P' — no allocator, generator or renderer edit.
        RegionWallSetRegistry registry = new RegionWallSetRegistry();
        RegionWallSets.registerAll(registry);
        registry.register(RegionWallSet.alias("test_annex", "outer_facility"));
        registry.validate();
        RegionWallSet set = registry.resolve("test_annex");
        LevelPalette palette = RegionPaletteBuilder.build(set, LevelPalettes.legacy());
        for (char symbol : WallSlotTable.symbols().toCharArray()) {
            assertTrue(set.allWallIds().contains(palette.spriteIdOf(symbol)),
                    "'" + symbol + "' did not resolve inside the registered set");
        }
        assertTrue(set.columnIds().contains(palette.spriteIdOf('P')));
    }

    @Test
    void recipeA_handLevelsReadRegionSetA() {
        // Hand-crafted levels use the LEGACY palette, whose walls come from set A through the slot table.
        LevelPalette legacy = LevelPalettes.legacy();
        RegionWallSet setA = RegionWallSets.registry().resolve("outer_facility");
        for (char symbol : WallSlotTable.symbols().toCharArray()) {
            assertTrue(setA.allWallIds().contains(legacy.spriteIdOf(symbol)),
                    "hand-level wall '" + symbol + "' is not a set-A face");
        }
        for (String spriteId : legacy.distinctSpriteIds()) {
            assertTrue(spriteId.startsWith("wall_a_") || spriteId.startsWith("column_a_")
                    || spriteId.startsWith("prop_") || spriteId.startsWith("decal_"),
                    "an old wall sprite survived: " + spriteId);
        }
    }

    @Test
    void recipeB_supplyCacheIsRegisteredAndInTheSpecialPool() {
        RoomBlueprints.bootstrap();
        RoomBlueprintRegistry registry = RoomBlueprints.rooms();

        // Registered by RECIPE B (one row) — which, since level-generation-overhaul, is all it takes to join the
        // equal-chance special pool every combat floor draws from (no eligibility gate, weight or cap).
        assertTrue(registry.contains(RoomBlueprints.ID_SUPPLY_CACHE));
        RoomBlueprint supplyCache = registry.get(RoomBlueprints.ID_SUPPLY_CACHE);
        assertTrue(SpecialRoomPlanner.specialPool(registry).contains(supplyCache),
                "supply_cache must be in the special pool");

        // The row carries its size window and its kit, and claims exactly what its kit can stamp (R29).
        assertTrue(supplyCache.sizeWindow() != null, "supply_cache needs a size window");
        assertTrue(supplyCache.kit() != null && supplyCache.signatureSymbol() != null,
                "supply_cache needs a signature kit");
        assertEquals(supplyCache.kit().spriteDemand(), supplyCache.symbolDemand().requiredSprites(),
                "supply_cache claims exactly its kit's sprites");
    }

}
