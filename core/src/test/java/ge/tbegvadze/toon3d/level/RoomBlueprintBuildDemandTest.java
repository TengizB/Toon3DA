package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.SymbolBudget;
import org.junit.jupiter.api.Test;

import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Drift guard between what a {@link RoomBlueprint} DECLARES ({@link RoomBlueprint#symbolDemand()}) and what it
 * actually STAMPS. Since level-generation-overhaul a room's demand is its ARCHITECTURE plus its PROP KIT (R29), so
 * this runs the shared isolation build ({@link RoomBlueprints#symbolsStampedInIsolation} — the blueprint's
 * {@code build()} plus the {@link RoomDresser} with its kit, one synthetic socket) across many seeds for every
 * special room and collects every FLEXIBLE environment symbol written into the room. Over enough seeds that set
 * must equal the blueprint's declared demand exactly — a missing symbol means the demand over-declares, an extra
 * symbol means it under-declares.
 */
class RoomBlueprintBuildDemandTest {

    private static final int SEED_COUNT = 400;

    @Test
    void everySpecialBlueprintStampsExactlyItsDeclaredDemand() {
        SymbolBudget budget = SymbolBudget.standard();
        RoomBlueprintRegistry registry = new RoomBlueprintRegistry();
        RoomBlueprints.registerAll(registry);

        for (RoomBlueprint blueprint : SpecialRoomPlanner.specialPool(registry)) {
            TreeSet<Character> stampedFlexibleSymbols = new TreeSet<>();
            for (long seed = 1; seed <= SEED_COUNT; seed++) {
                for (char symbol : RoomBlueprints.symbolsStampedInIsolation(blueprint, seed)) {
                    if (budget.isFlexible(symbol)) stampedFlexibleSymbols.add(symbol);
                }
            }
            TreeSet<Character> declared = new TreeSet<>(blueprint.symbolDemand().requiredSprites().keySet());
            assertEquals(declared, stampedFlexibleSymbols,
                    blueprint.id() + ": its architecture + kit must stamp exactly its declared demand");
        }
    }
}
