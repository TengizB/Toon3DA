package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.level.RoomBlueprint;
import ge.tbegvadze.toon3d.level.RoomBlueprintRegistry;
import ge.tbegvadze.toon3d.level.RoomBlueprints;
import ge.tbegvadze.toon3d.level.RoomContext;
import ge.tbegvadze.toon3d.util.LevelGenConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * order-9 EXTENSIBILITY ACCEPTANCE — proves the two recipes end-to-end with the real additions made in
 * order-9 (docs/environment-tileset-system.txt §9), beyond the registry-integrity tests elsewhere:
 *
 * <ul>
 *   <li>RECIPE A (walls, since generated-wall-art order 2) — a region wall set is ONE registry row (a real
 *       set, or an alias that borrows one). This test proves a row added by registration alone reaches a
 *       floor's palette: every wall symbol and the column resolve to the set it names.</li>
 *   <li>RECIPE B — "supply_cache" was added by registration alone (one RoomBlueprint, no RoomType
 *       constant, no bespoke generator helper). This test proves it can be SELECTED: it is registered,
 *       eligible on an ordinary room, and carries a positive selection weight, so it competes in the
 *       generator's seeded roulette.</li>
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
    void recipeB_supplyCacheIsRegisteredEligibleAndSelectable() {
        RoomBlueprints.bootstrap();
        RoomBlueprintRegistry registry = RoomBlueprints.rooms();

        // Registered by RECIPE B (one blueprint) and classified GENERIC.
        assertTrue(registry.contains(RoomBlueprints.ID_SUPPLY_CACHE));
        RoomBlueprint supplyCache = registry.get(RoomBlueprints.ID_SUPPLY_CACHE);

        // A room comfortably above the cache's minimum, none placed yet: eligible with a positive weight,
        // so it competes in the generator's seeded roulette (selectBlueprint) — i.e. it CAN be selected.
        RoomContext ordinaryRoom = new RoomContext(
                LevelGenConstants.LEVEL_GEN_SUPPLY_CACHE_MIN_WIDTH + 2,
                LevelGenConstants.LEVEL_GEN_SUPPLY_CACHE_MIN_HEIGHT + 2,
                3, 0);
        assertTrue(supplyCache.eligible(ordinaryRoom), "supply_cache should be eligible on an ordinary room");
        assertTrue(supplyCache.selectionWeight(ordinaryRoom) > 0f, "supply_cache needs a positive weight");

        // EQUAL-CHANCE removed all per-level room caps: supply_cache stays eligible even with instances
        // already placed (only its size gate still applies), so a floor may roll it more than once.
        RoomContext manyPlaced = new RoomContext(
                LevelGenConstants.LEVEL_GEN_SUPPLY_CACHE_MIN_WIDTH + 2,
                LevelGenConstants.LEVEL_GEN_SUPPLY_CACHE_MIN_HEIGHT + 2,
                3, LevelGenConstants.LEVEL_GEN_SUPPLY_CACHE_MAX);
        assertTrue(supplyCache.eligible(manyPlaced), "supply_cache is no longer capped per level");

        // It claims no specific sprite (GENERIC): its look is fully allocator-driven from free symbols.
        assertTrue(supplyCache.symbolDemand().requiredSprites().isEmpty(),
                "supply_cache (GENERIC) must reserve no sprite");
    }

}
