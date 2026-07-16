package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Guards the order-7 seam: {@link SymbolCategories} is the ONE source of truth {@code Level}'s category
 * predicates now delegate to, so its category assignment MUST agree with those predicates for every symbol
 * a level file can use. Iterating the printable ASCII range and cross-checking is a drift guard — if the
 * budget partition and the predicates ever disagree, a wall could stop rendering (or start blocking) and
 * this test fails loudly. Headless — no LibGDX render calls.
 */
class SymbolCategoriesTest {

    private static final char FIRST_PRINTABLE = 32;
    private static final char LAST_PRINTABLE  = 126;

    @Test
    void categoryOfAgreesWithLevelPredicatesAcrossPrintableRange() {
        for (char symbol = FIRST_PRINTABLE; symbol <= LAST_PRINTABLE; symbol++) {
            TileCategory category = SymbolCategories.categoryOf(symbol);

            assertEquals(category == TileCategory.WALL, Level.isWall(symbol),
                    "WALL disagreement for '" + symbol + "'");
            assertEquals(category == TileCategory.COLUMN, Level.isColumn(symbol),
                    "COLUMN disagreement for '" + symbol + "'");
            assertEquals(category == TileCategory.SOLID_PROP, Level.isPropSolid(symbol),
                    "SOLID_PROP disagreement for '" + symbol + "'");

            // FLOOR_DECAL is the "pure" decal subset of isPropDecal (the fixed gameplay decals — hazards,
            // pickups, stairs — are NOT tileset sprites and carry no category).
            boolean pureDecal = Level.isPropDecal(symbol)
                    && !Level.isKeycardPickup(symbol)
                    && !Level.isMedicalPickup(symbol)
                    && !Level.isArmourPickup(symbol)
                    && !Level.isAmmoPickup(symbol)
                    && !Level.isHazardDecal(symbol)
                    && !Level.isStairsDown(symbol);
            assertEquals(category == TileCategory.FLOOR_DECAL, pureDecal,
                    "FLOOR_DECAL disagreement for '" + symbol + "'");
        }
    }

    @Test
    void fixedSpriteExceptionsResolveToTheirDefinitionCategory() {
        // 'x' -> "wall_plain" (WALL) and '@' -> "prop_vendor" (SOLID_PROP): FIXED-sprite symbols whose
        // category flows from the registered sprite definition, not a flexible slot.
        assertEquals(TileCategory.WALL, SymbolCategories.categoryOf('x'));
        assertEquals(TileCategory.SOLID_PROP, SymbolCategories.categoryOf('@'));
    }

    @Test
    void nonEnvironmentSymbolsHaveNoCategory() {
        // Doors, keycards, ammo, hazards, enemy spawns, start, exit, plain floor — not tileset categories.
        for (char symbol : new char[]{' ', 'd', 'R', 'Y', 'B', 'r', 'y', 'b', '+', 'H', 'a', 'A',
                                      '6', '7', '8', '9', '0', 'i', 'q', 'p', '>', '1', '2', '3', '4', '5'}) {
            assertNull(SymbolCategories.categoryOf(symbol), "expected no category for '" + symbol + "'");
        }
    }
}
