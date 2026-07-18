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
 *   <li>RECIPE A — "wall_hex_plate" was added by registration alone (one sprite definition + one texture
 *       generator, marked variety-only). This test proves it actually REACHES a generated level: the
 *       allocator's per-level wall variety binds it to some flexible WALL symbol across seeds.</li>
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
    void recipeA_hexPlateWallReachesAGeneratedPaletteAcrossSeeds() {
        EnvironmentSpriteRegistry registry = productionRegistry();
        SymbolBudget budget = SymbolBudget.standard();

        // It is a real WALL sprite, variety-only (no legacy symbol binds it), so it can only appear via
        // the allocator's variety fill — never on a hand level.
        assertTrue(registry.contains(TilesetRegistries.SPRITE_ID_WALL_HEX_PLATE));
        assertTrue(TilesetRegistries.VARIETY_ONLY_SPRITE_IDS.contains(
                TilesetRegistries.SPRITE_ID_WALL_HEX_PLATE));

        // With NO rooms reserving anything, every flexible WALL symbol falls to variety, so the hex-plate
        // sprite is eligible to be chosen. Over a spread of seeds it must land on some symbol at least once.
        boolean appeared = false;
        for (long seed = 1; seed <= 400 && !appeared; seed++) {
            LevelPalette palette = SymbolAllocator.allocate(
                    SymbolAllocationRequest.builder(seed, budget, registry).depth(3).build());
            for (char wallSymbol : budget.flexibleSlots().get(TileCategory.WALL)) {
                if (TilesetRegistries.SPRITE_ID_WALL_HEX_PLATE.equals(palette.spriteIdOf(wallSymbol))) {
                    appeared = true;
                    break;
                }
            }
        }
        assertTrue(appeared,
                "wall_hex_plate (RECIPE A) never appeared on any generated palette across 400 seeds");
    }

    @Test
    void recipeA_hexPlateWallNeverAppearsOnHandLevels() {
        // Hand-crafted levels use the LEGACY palette; a variety-only sprite must never be bound there.
        LevelPalette legacy = LevelPalettes.legacy();
        assertFalse(legacy.distinctSpriteIds().contains(TilesetRegistries.SPRITE_ID_WALL_HEX_PLATE),
                "variety-only wall_hex_plate must not appear on legacy (hand) levels");
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

    private static EnvironmentSpriteRegistry productionRegistry() {
        TilesetRegistries.bootstrap();
        return TilesetRegistries.sprites();
    }
}
