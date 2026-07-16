package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.util.TilesetConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The authoritative, immutable answer to "which symbols may the per-level allocator reassign, and which
 * must it never touch." This is the SAFETY BOUNDARY of the symbol/sprite-reuse series (order-2): get it
 * wrong and a level could repaint the exit '&gt;' or a keycard 'r' as a wall, breaking gameplay. It is
 * isolated here as one small, heavily-tested config so the allocator (order-4) can trust it completely.
 *
 * <p>SymbolBudget stores STRUCTURE only — which char is which {@link SymbolClass} and, for flexible
 * symbols, which {@link TileCategory} it belongs to. It does NOT decide which sprite fills a flexible
 * slot (that is the allocator's job). The only sprites named here are the FIXED ones bound permanently
 * (e.g. 'x' → "wall_plain", the shop '@' → "prop_vendor").
 *
 * <p>FAIL-SAFE RULE: any char not explicitly listed as flexible is {@link SymbolClass#FIXED}. Doors,
 * pickups, ammo, hazards, enemy spawns, player-start, exit — none of them appear in the flexible tables,
 * so they all resolve to FIXED without being enumerated. Headless — no LibGDX; pure maps + enums.
 *
 * <p>Build the canonical v1 partition with {@link #standard()}; a fresh instance is constructed straight
 * from {@link TilesetConstants}, so it is safe to build repeatedly and cheap to build in tests.
 */
public final class SymbolBudget {

    // Fixed symbols that map to an environment sprite, char -> permanently-bound sprite id. Fixed
    // NON-sprite symbols (doors, pickups, lighting, enemies) are simply absent — the budget only
    // concerns things that map to environment sprites.
    private final Map<Character, String> fixedAssignments;

    // The reassignable symbols per category (WALL -> [c,v,t,...], COLUMN -> ['P'], ...).
    private final Map<TileCategory, List<Character>> flexibleSlots;

    // Fast reverse lookup char -> its flexible category, built once from flexibleSlots.
    private final Map<Character, TileCategory> flexibleCategoryByChar;

    private SymbolBudget(Map<Character, String> fixedAssignments,
                         Map<TileCategory, List<Character>> flexibleSlots) {
        this.fixedAssignments = Collections.unmodifiableMap(new LinkedHashMap<>(fixedAssignments));

        Map<TileCategory, List<Character>> copiedSlots = new EnumMap<>(TileCategory.class);
        Map<Character, TileCategory> reverseLookup = new LinkedHashMap<>();
        for (Map.Entry<TileCategory, List<Character>> entry : flexibleSlots.entrySet()) {
            TileCategory category = entry.getKey();
            List<Character> symbols = Collections.unmodifiableList(new ArrayList<>(entry.getValue()));
            copiedSlots.put(category, symbols);
            for (Character symbol : symbols) {
                reverseLookup.put(symbol, category);
            }
        }
        this.flexibleSlots          = Collections.unmodifiableMap(copiedSlots);
        this.flexibleCategoryByChar = Collections.unmodifiableMap(reverseLookup);
    }

    /**
     * Builds the canonical v1 partition from {@link TilesetConstants}: 'x' fixed to "wall_plain", the
     * shop '@' fixed to "prop_vendor", and the accent walls / column 'P' / decoration props / decals as
     * flexible category slots. This is the single source the allocator consumes.
     */
    public static SymbolBudget standard() {
        Map<Character, String> fixed = new LinkedHashMap<>();
        fixed.put(TilesetConstants.FIXED_WALL_SYMBOL.charAt(0), TilesetConstants.FIXED_WALL_SPRITE_ID);
        putFixedSpriteExceptions(fixed);

        Map<TileCategory, List<Character>> flexible = new EnumMap<>(TileCategory.class);
        flexible.put(TileCategory.WALL,       toCharList(TilesetConstants.FLEXIBLE_WALL_SYMBOLS));
        flexible.put(TileCategory.COLUMN,     toCharList(TilesetConstants.FLEXIBLE_COLUMN_SYMBOLS));
        flexible.put(TileCategory.SOLID_PROP, toCharList(TilesetConstants.FLEXIBLE_SOLIDPROP_SYMBOLS));
        flexible.put(TileCategory.FLOOR_DECAL, toCharList(TilesetConstants.FLEXIBLE_DECAL_SYMBOLS));

        return new SymbolBudget(fixed, flexible);
    }

    /**
     * The FIXED symbols that map to a permanently-bound environment sprite (char -> sprite id).
     * Unmodifiable; iteration order is insertion order for reproducibility.
     */
    public Map<Character, String> fixedAssignments() {
        return fixedAssignments;
    }

    /**
     * The reassignable symbols per category (the allocator's per-category slot list). Unmodifiable;
     * each list is itself unmodifiable and in declaration order.
     */
    public Map<TileCategory, List<Character>> flexibleSlots() {
        return flexibleSlots;
    }

    /**
     * The {@link SymbolClass} of a symbol. FLEXIBLE only for a symbol explicitly declared in the
     * flexible tables; every other char — including doors, pickups, hazards, enemy spawns, the plain
     * wall, and any unknown char — is FIXED. Never null.
     */
    public SymbolClass symbolClassOf(char symbol) {
        return flexibleCategoryByChar.containsKey(symbol) ? SymbolClass.FLEXIBLE : SymbolClass.FIXED;
    }

    /**
     * The category a FLEXIBLE symbol belongs to, or {@code null} if the symbol is not flexible. Guard
     * with {@link #symbolClassOf(char)} first when a definite category is required.
     */
    public TileCategory categoryOfFlexibleSymbol(char symbol) {
        return flexibleCategoryByChar.get(symbol);
    }

    /** Convenience: whether the symbol may be reassigned per level. */
    public boolean isFlexible(char symbol) {
        return symbolClassOf(symbol) == SymbolClass.FLEXIBLE;
    }

    /** Convenience: whether the symbol keeps a permanent meaning AND sprite. */
    public boolean isFixed(char symbol) {
        return symbolClassOf(symbol) == SymbolClass.FIXED;
    }

    /**
     * The permanently-bound sprite id for a FIXED symbol that maps to an environment sprite (e.g. 'x' →
     * "wall_plain"), or {@code null} for a FIXED symbol with no sprite (doors, pickups, …) or any
     * flexible symbol.
     */
    public String fixedSpriteId(char symbol) {
        return fixedAssignments.get(symbol);
    }

    // Parses the index-aligned FIXED_SPRITE_SYMBOLS / FIXED_SPRITE_IDS tables into the fixed map. Each
    // symbol char pairs with the sprite id at the same position in the comma-separated id list.
    private static void putFixedSpriteExceptions(Map<Character, String> fixed) {
        String symbols = TilesetConstants.FIXED_SPRITE_SYMBOLS;
        if (symbols.isEmpty()) {
            return;
        }
        String[] spriteIds = TilesetConstants.FIXED_SPRITE_IDS.split(",");
        if (spriteIds.length != symbols.length()) {
            throw new IllegalStateException(
                    "TilesetConstants.FIXED_SPRITE_SYMBOLS / FIXED_SPRITE_IDS length mismatch: "
                            + symbols.length() + " symbols vs " + spriteIds.length + " ids");
        }
        for (int index = 0; index < symbols.length(); index++) {
            fixed.put(symbols.charAt(index), spriteIds[index].trim());
        }
    }

    // Expands a char-list string constant into a List<Character> preserving order.
    private static List<Character> toCharList(String symbols) {
        List<Character> characters = new ArrayList<>(symbols.length());
        for (int index = 0; index < symbols.length(); index++) {
            characters.add(symbols.charAt(index));
        }
        return characters;
    }
}
