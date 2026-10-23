package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.EnvironmentSpriteRegistry;
import ge.tbegvadze.toon3d.tileset.LevelPalettes;
import ge.tbegvadze.toon3d.tileset.SymbolBudget;
import ge.tbegvadze.toon3d.tileset.TileCategory;
import ge.tbegvadze.toon3d.tileset.TilesetRegistries;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for the {@link RoomBlueprintRegistry} + {@link RoomBlueprints} catalog: every room is registered exactly
 * once with the right tier; every special room's {@link RoomBlueprint#symbolDemand()} names real, correctly-categorised
 * sprites that match the legacy palette; the entrance and the standard room claim nothing; and (since
 * level-generation-overhaul, which replaced the eligibility gates and weight tiers) every special room carries a size
 * window and a signature kit, and the special pool is every room but the entrance and the standard room.
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
        // 15 blueprints: ENTRANCE + STANDARD + the thirteen special rooms (level-generation-overhaul R1). There is
        // no RoomType enum any more: a row's behaviour is its window, kit and architecture.
        assertEquals(15, registry.size());
        assertEquals(4, registry.allGeneric().size(),
                "ENTRANCE/STANDARD/SALVAGE_BAY/SUPPLY_CACHE are GENERIC");
        assertEquals(11, registry.allSignature().size(), "the eleven specialty rooms are SIGNATURE");
    }

    @Test
    void bootstrapIsIdempotentAndSharedRegistryIsPopulated() {
        RoomBlueprints.bootstrap();
        RoomBlueprints.bootstrap();
        assertTrue(RoomBlueprints.isBootstrapped());
        assertEquals(15, RoomBlueprints.rooms().size());
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
    void entranceAndStandardClaimNoSprites() {
        // A regular room's kit is chosen per room, so the dresser pins exactly what it placed (R29); the entrance
        // is bare (R22). Every other row — the GENERIC salvage bay and supply cache included — claims its kit.
        RoomBlueprintRegistry registry = freshRegistry();
        for (String id : new String[]{ RoomBlueprints.ID_ENTRANCE, RoomBlueprints.ID_STANDARD }) {
            assertTrue(registry.get(id).symbolDemand().requiredSprites().isEmpty(), id + " must claim no sprites");
        }
    }

    @Test
    void signatureDemandsNameRealSpritesInTheCorrectCategoryMatchingLegacyPalette() {
        TilesetRegistries.bootstrap();
        EnvironmentSpriteRegistry sprites = TilesetRegistries.sprites();
        SymbolBudget budget = SymbolBudget.standard();
        RoomBlueprintRegistry registry = freshRegistry();

        for (RoomBlueprint blueprint : SpecialRoomPlanner.specialPool(registry)) {
            Map<Character, String> demand = blueprint.symbolDemand().requiredSprites();
            assertFalse(demand.isEmpty(), blueprint.id() + " (special) must claim at least one sprite");
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
    void everySpecialRoomHasASizeWindowAndASignatureKit() {
        RoomBlueprintRegistry registry = freshRegistry();
        for (RoomBlueprint blueprint : SpecialRoomPlanner.specialPool(registry)) {
            RoomSizeWindow window = blueprint.sizeWindow();
            assertTrue(window != null, blueprint.id() + " needs a size window (R4)");
            assertTrue(window.minWidth <= window.maxWidth && window.minHeight <= window.maxHeight,
                    blueprint.id() + " window min must not exceed max");
            assertTrue(blueprint.kit() != null && blueprint.signatureSymbol() != null,
                    blueprint.id() + " needs a kit whose row 1 is its SIGNATURE (R16)");
        }
        assertTrue(registry.get(RoomBlueprints.ID_ENTRANCE).kit() == null, "the entrance is bare (R22)");
        assertEquals(5, registry.get(RoomBlueprints.ID_STANDARD).regularKits().size(),
                "the standard row carries the five regular kits (R23)");
        // The stellar observatory is the one SQUARE window.
        assertTrue(registry.get(RoomBlueprints.ID_STELLAR_OBSERVATORY).sizeWindow().square);
    }

    @Test
    void theSpecialPoolIsEveryRoomButTheEntranceAndTheStandardRoom() {
        // R1: no depth input, no weight, no cap — every registered special is in the equal-chance pool.
        RoomBlueprintRegistry registry = freshRegistry();
        List<RoomBlueprint> pool = SpecialRoomPlanner.specialPool(registry);
        assertEquals(13, pool.size());
        assertFalse(containsId(pool, RoomBlueprints.ID_ENTRANCE));
        assertFalse(containsId(pool, RoomBlueprints.ID_STANDARD));
        Set<String> expected = new HashSet<>(Arrays.asList(RoomBlueprints.ID_SERVER_ROOM, RoomBlueprints.ID_MEDICAL_BAY,
                RoomBlueprints.ID_ARMORY, RoomBlueprints.ID_CRYO_CHAMBER, RoomBlueprints.ID_POWER_PLANT,
                RoomBlueprints.ID_COMMAND_CENTER, RoomBlueprints.ID_CONTAINMENT_BLOCK, RoomBlueprints.ID_RESEARCH_LAB,
                RoomBlueprints.ID_STELLAR_OBSERVATORY, RoomBlueprints.ID_GORE_NEST, RoomBlueprints.ID_ATMOSPHERIC_PLANT,
                RoomBlueprints.ID_SALVAGE_BAY, RoomBlueprints.ID_SUPPLY_CACHE));
        for (RoomBlueprint blueprint : pool) assertTrue(expected.contains(blueprint.id()), blueprint.id());
    }

    private static boolean containsId(List<RoomBlueprint> blueprints, String id) {
        for (RoomBlueprint blueprint : blueprints) {
            if (blueprint.id().equals(id)) return true;
        }
        return false;
    }
}
