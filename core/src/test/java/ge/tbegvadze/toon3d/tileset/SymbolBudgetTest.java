package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.level.Level;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Partition-integrity tests for the symbol budget (order-2): the fixed and flexible sets are disjoint,
 * every gameplay symbol (doors, pickups, ammo, hazards, enemy spawns, start, exit) resolves to FIXED,
 * 'x' is fixed and bound to "wall_plain", and every flexible symbol maps to a real order-1 category
 * whose sprite pool actually contains it. These guard the SAFETY BOUNDARY the allocator relies on.
 * Headless — no LibGDX.
 */
class SymbolBudgetTest {

    private static final char FIRST_PRINTABLE = 32;
    private static final char LAST_PRINTABLE  = 126;

    private final SymbolBudget budget = SymbolBudget.standard();

    @Test
    void plainWallIsFixedAndBoundToWallPlain() {
        assertSame(SymbolClass.FIXED, budget.symbolClassOf('x'));
        assertTrue(budget.isFixed('x'));
        assertFalse(budget.isFlexible('x'));
        assertEquals("wall_plain", budget.fixedSpriteId('x'));
        assertNull(budget.categoryOfFlexibleSymbol('x'));
    }

    @Test
    void shopIsAFixedSpriteExceptionBoundToVendor() {
        // '@' lives in the SOLID_PROP category but carries hard gameplay meaning (World.placeMachine),
        // so it must stay FIXED and never be repainted by the allocator.
        assertSame(SymbolClass.FIXED, budget.symbolClassOf('@'));
        assertEquals("prop_vendor", budget.fixedSpriteId('@'));
        assertFalse(budget.flexibleSlots().get(TileCategory.SOLID_PROP).contains('@'));
    }

    @Test
    void fixedAndFlexibleSetsAreDisjoint() {
        Set<Character> fixedSymbols = budget.fixedAssignments().keySet();
        for (List<Character> categorySymbols : budget.flexibleSlots().values()) {
            for (Character symbol : categorySymbols) {
                assertFalse(fixedSymbols.contains(symbol),
                        "symbol '" + symbol + "' is both fixed and flexible");
            }
        }
    }

    @Test
    void noSymbolAppearsInTwoFlexibleCategories() {
        Set<Character> seen = new HashSet<>();
        for (List<Character> categorySymbols : budget.flexibleSlots().values()) {
            for (Character symbol : categorySymbols) {
                assertTrue(seen.add(symbol), "symbol '" + symbol + "' listed in two flexible categories");
            }
        }
    }

    @Test
    void flexibleSymbolResolvesToItsDeclaredCategory() {
        for (Map.Entry<TileCategory, List<Character>> entry : budget.flexibleSlots().entrySet()) {
            for (Character symbol : entry.getValue()) {
                assertSame(SymbolClass.FLEXIBLE, budget.symbolClassOf(symbol));
                assertSame(entry.getKey(), budget.categoryOfFlexibleSymbol(symbol),
                        "wrong category for flexible '" + symbol + "'");
            }
        }
    }

    @Test
    void everyFlexibleSymbolMapsToACategoryPresentInTheSpriteRegistry() {
        EnvironmentSpriteRegistry registry = new EnvironmentSpriteRegistry();
        TilesetRegistries.registerAll(registry);
        for (TileCategory category : budget.flexibleSlots().keySet()) {
            // order-1 registered at least one sprite for each flexible category, so the allocator
            // always has a pool to choose from.
            assertFalse(registry.allInCategory(category).isEmpty(),
                    "flexible category " + category + " has no sprites in the registry");
        }
    }

    @Test
    void everyWallSymbolIsPartitionedAndFlexibleWallsAreWalls() {
        // Every char Level treats as a wall is either the fixed plain wall or a flexible WALL slot,
        // and every flexible WALL symbol really is a wall to Level. No wall char is left unclassified.
        for (char candidate = FIRST_PRINTABLE; candidate <= LAST_PRINTABLE; candidate++) {
            if (!Level.isWall(candidate)) {
                continue;
            }
            if (candidate == 'x') {
                assertSame(SymbolClass.FIXED, budget.symbolClassOf(candidate));
            } else {
                assertSame(SymbolClass.FLEXIBLE, budget.symbolClassOf(candidate));
                assertSame(TileCategory.WALL, budget.categoryOfFlexibleSymbol(candidate));
            }
        }
        for (Character wallSymbol : budget.flexibleSlots().get(TileCategory.WALL)) {
            assertTrue(Level.isWall(wallSymbol), "flexible WALL symbol '" + wallSymbol + "' is not a wall");
        }
    }

    @Test
    void columnSymbolIsFlexible() {
        assertSame(SymbolClass.FLEXIBLE, budget.symbolClassOf('P'));
        assertSame(TileCategory.COLUMN, budget.categoryOfFlexibleSymbol('P'));
        assertTrue(Level.isColumn('P'));
    }

    @Test
    void everyGameplaySymbolResolvesToFixed() {
        // Doors, keycards, medical/armour/ammo pickups, hazards, enemy spawns, player-start, and exit
        // must never be reassignable. They are absent from the flexible tables, so the fail-safe
        // default must land them all on FIXED without being enumerated in the budget.
        for (char candidate = FIRST_PRINTABLE; candidate <= LAST_PRINTABLE; candidate++) {
            char cell = candidate;
            boolean gameplayFixed =
                    Level.isDoor(cell)
                    || Level.isKeycardPickup(cell)
                    || Level.isMedicalPickup(cell)
                    || Level.isArmourPickup(cell)
                    || Level.isAmmoPickup(cell)
                    || Level.isHazardDecal(cell)
                    || Level.isStairsDown(cell)
                    || Level.isShop(cell)
                    || isEnemySpawn(cell)
                    || cell == 'p';   // player start
            if (gameplayFixed) {
                assertSame(SymbolClass.FIXED, budget.symbolClassOf(cell),
                        "gameplay symbol '" + cell + "' must be FIXED");
                assertNull(budget.categoryOfFlexibleSymbol(cell),
                        "gameplay symbol '" + cell + "' must have no flexible category");
            }
        }
    }

    @Test
    void unknownCharDefaultsToFixed() {
        // A char in no table at all (fail-safe): unknown symbols are never reassigned.
        assertSame(SymbolClass.FIXED, budget.symbolClassOf('{'));
        assertNull(budget.fixedSpriteId('{'));
        assertNull(budget.categoryOfFlexibleSymbol('{'));
    }

    @Test
    void flexibleSlotsAndFixedAssignmentsAreUnmodifiable() {
        assertThrowsUnsupported(() -> budget.fixedAssignments().put('Z', "x"));
        assertThrowsUnsupported(() -> budget.flexibleSlots().put(TileCategory.WALL, null));
        assertThrowsUnsupported(() -> budget.flexibleSlots().get(TileCategory.WALL).add('z'));
    }

    @Test
    void allFourFlexibleCategoriesArePopulated() {
        assertNotNull(budget.flexibleSlots().get(TileCategory.WALL));
        assertFalse(budget.flexibleSlots().get(TileCategory.WALL).isEmpty());
        assertFalse(budget.flexibleSlots().get(TileCategory.COLUMN).isEmpty());
        assertFalse(budget.flexibleSlots().get(TileCategory.SOLID_PROP).isEmpty());
        assertFalse(budget.flexibleSlots().get(TileCategory.FLOOR_DECAL).isEmpty());
    }

    // Enemy-spawn markers are extracted by LevelLoader and are not exposed by a Level predicate; the
    // canonical list from docs/tile-symbols.txt is mirrored here so the gameplay-FIXED sweep covers them.
    private static boolean isEnemySpawn(char cell) {
        return "12345!$^~zKV*n".indexOf(cell) >= 0;
    }

    private static void assertThrowsUnsupported(Runnable action) {
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("expected UnsupportedOperationException");
    }
}
