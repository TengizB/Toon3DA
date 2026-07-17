package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.EnvironmentSpriteRegistry;
import ge.tbegvadze.toon3d.tileset.LevelPalettes;
import ge.tbegvadze.toon3d.tileset.SymbolBudget;
import ge.tbegvadze.toon3d.tileset.TileCategory;
import ge.tbegvadze.toon3d.tileset.TilesetRegistries;
import ge.tbegvadze.toon3d.util.LevelGenConstants;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for the order-5 {@link RoomBlueprintRegistry} + {@link RoomBlueprints} catalog: every existing
 * {@code LevelGenerator.RoomType} is registered exactly once with the right tier; the SIGNATURE rooms'
 * {@link RoomBlueprint#symbolDemand()} names real, correctly-categorised sprites that match today's legacy
 * palette; GENERIC rooms claim nothing; and the eligibility/weight gates mirror the generator's constants.
 */
class RoomBlueprintRegistryTest {

    private static RoomBlueprintRegistry freshRegistry() {
        RoomBlueprintRegistry registry = new RoomBlueprintRegistry();
        RoomBlueprints.registerAll(registry);
        return registry;
    }

    @Test
    void registryHoldsEveryV1RoomPlusTheRegistrationOnlyAcceptanceRoom() {
        RoomBlueprintRegistry registry = freshRegistry();
        // 13 blueprints: the 12 v1 rooms (order-8 added SALVAGE_BAY; LARGE was removed — it is now a
        // size MODIFIER, not a blueprint) + order-9's SUPPLY_CACHE, which is added by REGISTRATION ALONE
        // and deliberately carries NO RoomType constant (the seam fix maps its id to the generic
        // STANDARD tag). So a blueprint no longer needs a 1:1 RoomType — the registry has one MORE entry
        // than the enum has values, all ids distinct (LinkedHashMap keys).
        assertEquals(13, registry.size());
        assertEquals(LevelGenerator.RoomType.values().length + 1, registry.size(),
                "SUPPLY_CACHE registers without a RoomType constant");
        assertEquals(4, registry.allGeneric().size(),
                "ENTRANCE/STANDARD/SALVAGE_BAY/SUPPLY_CACHE are GENERIC");
        assertEquals(9, registry.allSignature().size(), "the nine specialty rooms are SIGNATURE");
    }

    @Test
    void bootstrapIsIdempotentAndSharedRegistryIsPopulated() {
        RoomBlueprints.bootstrap();
        RoomBlueprints.bootstrap();
        assertTrue(RoomBlueprints.isBootstrapped());
        assertEquals(13, RoomBlueprints.rooms().size());
    }

    @Test
    void duplicateIdThrows() {
        RoomBlueprintRegistry registry = freshRegistry();
        RoomBlueprint anyExisting = registry.get(RoomBlueprints.ID_STANDARD);
        assertThrows(IllegalStateException.class, () -> registry.register(anyExisting));
    }

    @Test
    void unknownIdThrows() {
        RoomBlueprintRegistry registry = freshRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.get("no_such_room"));
    }

    @Test
    void genericRoomsClaimNoSprites() {
        RoomBlueprintRegistry registry = freshRegistry();
        for (RoomBlueprint blueprint : registry.allGeneric()) {
            assertTrue(blueprint.symbolDemand().requiredSprites().isEmpty(),
                    blueprint.id() + " (GENERIC) must claim no sprites — its look is allocator-driven");
        }
    }

    @Test
    void signatureDemandsNameRealSpritesInTheCorrectCategoryMatchingLegacyPalette() {
        TilesetRegistries.bootstrap();
        EnvironmentSpriteRegistry sprites = TilesetRegistries.sprites();
        SymbolBudget budget = SymbolBudget.standard();
        RoomBlueprintRegistry registry = freshRegistry();

        for (RoomBlueprint blueprint : registry.allSignature()) {
            Map<Character, String> demand = blueprint.symbolDemand().requiredSprites();
            assertFalse(demand.isEmpty(), blueprint.id() + " (SIGNATURE) must claim at least one sprite");
            for (Map.Entry<Character, String> entry : demand.entrySet()) {
                char symbol = entry.getKey();
                String spriteId = entry.getValue();

                // The claimed symbol is a FLEXIBLE environment symbol (never a FIXED gameplay symbol).
                assertTrue(budget.isFlexible(symbol),
                        blueprint.id() + " claims non-flexible symbol '" + symbol + "'");

                // The sprite id is registered and its category matches the symbol's flexible category.
                assertTrue(sprites.contains(spriteId),
                        blueprint.id() + " claims unregistered sprite id '" + spriteId + "'");
                TileCategory symbolCategory = budget.categoryOfFlexibleSymbol(symbol);
                assertEquals(symbolCategory, sprites.get(spriteId).category(),
                        blueprint.id() + " symbol '" + symbol + "' → '" + spriteId + "' category mismatch");

                // The claimed sprite is exactly today's legacy look for that symbol (reserves the room's
                // signature appearance; guards against a wrong sprite id).
                assertEquals(spriteId, LevelPalettes.legacy().spriteIdOf(symbol),
                        blueprint.id() + " symbol '" + symbol + "' must claim its legacy sprite");
            }
        }
    }

    @Test
    void eligibilityGatesMirrorTheGeneratorConstants() {
        RoomBlueprintRegistry registry = freshRegistry();

        // A tiny 3x3 candidate is eligible only for the size-gateless rooms (ENTRANCE/STANDARD/SERVER).
        RoomContext tiny = new RoomContext(3, 3, 1, 0);
        List<RoomBlueprint> tinySelectable = registry.selectable(tiny);
        assertTrue(containsId(tinySelectable, RoomBlueprints.ID_ENTRANCE));
        assertTrue(containsId(tinySelectable, RoomBlueprints.ID_STANDARD));
        assertTrue(containsId(tinySelectable, RoomBlueprints.ID_SERVER_ROOM));
        assertFalse(containsId(tinySelectable, RoomBlueprints.ID_MEDICAL_BAY), "3x3 too small for medical bay");

        // Medical bay turns eligible exactly at its minimum footprint.
        RoomContext medicalFit = new RoomContext(
                LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_WIDTH,
                LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_HEIGHT, 1, 0);
        assertTrue(registry.get(RoomBlueprints.ID_MEDICAL_BAY).eligible(medicalFit));
        // ...but not once its per-level cap of 1 is already spent.
        RoomContext medicalCapped = new RoomContext(
                LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_WIDTH,
                LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_HEIGHT, 1, 1);
        assertFalse(registry.get(RoomBlueprints.ID_MEDICAL_BAY).eligible(medicalCapped));

        // Stellar observatory is depth-gated: a big near-square room below MIN_DEPTH is ineligible.
        int bigInterior = LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MIN_INTERIOR;
        RoomBlueprint observatory = registry.get(RoomBlueprints.ID_STELLAR_OBSERVATORY);
        assertFalse(observatory.eligible(new RoomContext(bigInterior, bigInterior,
                LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MIN_DEPTH - 1, 0)), "below min depth");
        assertTrue(observatory.eligible(new RoomContext(bigInterior, bigInterior,
                LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MIN_DEPTH, 0)), "at min depth + near-square");
    }

    @Test
    void selectionWeightsAreTheGeneratorChances() {
        RoomBlueprintRegistry registry = freshRegistry();
        RoomContext any = new RoomContext(16, 16, 5, 0);
        assertEquals(LevelGenConstants.LEVEL_GEN_SERVER_ROOM_CHANCE,
                registry.get(RoomBlueprints.ID_SERVER_ROOM).selectionWeight(any));
        assertEquals(LevelGenConstants.LEVEL_GEN_ARMORY_CHANCE,
                registry.get(RoomBlueprints.ID_ARMORY).selectionWeight(any));
        assertEquals(LevelGenConstants.LEVEL_GEN_POWERPLANT_CHANCE,
                registry.get(RoomBlueprints.ID_POWER_PLANT).selectionWeight(any));
        assertEquals(LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_CHANCE,
                registry.get(RoomBlueprints.ID_STELLAR_OBSERVATORY).selectionWeight(any));
        assertEquals(LevelGenConstants.LEVEL_GEN_ROOM_ENTRANCE_SELECTION_WEIGHT,
                registry.get(RoomBlueprints.ID_ENTRANCE).selectionWeight(any));
    }

    private static boolean containsId(List<RoomBlueprint> blueprints, String id) {
        for (RoomBlueprint blueprint : blueprints) {
            if (blueprint.id().equals(id)) return true;
        }
        return false;
    }
}
