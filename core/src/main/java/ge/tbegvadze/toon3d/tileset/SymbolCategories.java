package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.util.TilesetConstants;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ONE source of truth for "which fixed {@link TileCategory} does an environment symbol belong to."
 * This is the seam the symbol/sprite-reuse migration (order-7) uses to back {@code Level}'s category
 * predicates ({@code isWall}/{@code isColumn}/{@code isPropSolid}/{@code isPropDecal}) with data instead of
 * hand-maintained char lists.
 *
 * <p>A symbol's CATEGORY is fixed and symbol-intrinsic — it never depends on which sprite a level's
 * {@link LevelPalette} assigns to it. A wall symbol is ALWAYS a wall regardless of the region-set picture
 * that fills it; a FIXED symbol keeps its permanent meaning. So category membership can be resolved
 * STATICALLY and PALETTE-FREE: this class derives it entirely from the {@link SymbolBudget} partition
 * (order-2), the wall slot table ({@link WallSlotTable}: every wall symbol is a WALL and 'P' the COLUMN,
 * generated-wall-art order 2) plus, for the fixed-sprite exception ('@' → "prop_vendor"), the category
 * recorded on its sprite definition in the {@link EnvironmentSpriteRegistry} (order-1).
 *
 * <p>Because category is symbol-intrinsic, {@link #categoryOf(char)} takes NO palette — it is safe to call
 * from hot movement / AI / collision code that has no render dependency. Only the SPRITE that fills the
 * category needs a palette (that resolution lives in the renderers, order-7). This separation is the whole
 * reason "category != sprite."
 *
 * <p>Headless — no LibGDX. Adding a flexible symbol to a category, or a new fixed-sprite exception, is a
 * data edit in {@code TilesetConstants} / {@code TilesetRegistries}; the predicates pick it up with no code
 * change.
 */
public final class SymbolCategories {

    private SymbolCategories() {}

    // Lazy, thread-safe holder (JLS class-init locking). Built once from the budget + registry; every
    // categoryOf() call is then a lock-free map read with no allocation — cheap enough for the hot
    // movement / render predicate calls that consult it per tile.
    private static final class Holder {
        static final Map<Character, TileCategory> BY_SYMBOL = build();
    }

    /**
     * The fixed category the given symbol belongs to, or {@code null} if the symbol is not an environment
     * symbol (doors, pickups, keycards, ammo, hazards, enemy spawns, player-start, exit — handled by the
     * existing dedicated paths, not the tileset categories). Never varies per level.
     */
    public static TileCategory categoryOf(char symbol) {
        return Holder.BY_SYMBOL.get(symbol);
    }

    private static Map<Character, TileCategory> build() {
        // Idempotent; guarantees the shared registry holds the inventory before we read a category off the
        // fixed-sprite exceptions' definitions.
        TilesetRegistries.bootstrap();
        EnvironmentSpriteRegistry registry = TilesetRegistries.sprites();
        SymbolBudget budget = SymbolBudget.standard();

        Map<Character, TileCategory> map = new HashMap<>();
        // Walls and the column (generated-wall-art order 2): every symbol of the slot table is a WALL and 'P'
        // the COLUMN, whatever picture the floor's region set gives them.
        String wallSymbols = WallSlotTable.symbols();
        for (int index = 0; index < wallSymbols.length(); index++) {
            map.put(wallSymbols.charAt(index), TileCategory.WALL);
        }
        map.put(TilesetConstants.WALL_SET_COLUMN_SYMBOL, TileCategory.COLUMN);
        // FLEXIBLE symbols carry their category directly in the budget (decoration props, pure decals).
        for (Map.Entry<TileCategory, List<Character>> entry : budget.flexibleSlots().entrySet()) {
            for (Character symbol : entry.getValue()) {
                map.put(symbol, entry.getKey());
            }
        }
        // FIXED sprite symbols ('@') take the category recorded on their sprite definition.
        for (Map.Entry<Character, String> entry : budget.fixedAssignments().entrySet()) {
            map.put(entry.getKey(), registry.get(entry.getValue()).category());
        }
        return Collections.unmodifiableMap(map);
    }
}
