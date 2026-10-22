package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.util.TilesetConstants;

import java.util.EnumMap;
import java.util.Map;

/**
 * THE SYMBOL -&gt; (group, slot) table (generated-wall-art order 2): the 21 wall symbols, parsed once from
 * {@link TilesetConstants#WALL_SLOT_TABLE}. A wall symbol names a SLOT; the floor's region set names the
 * picture. Both directions are O(1): symbol -&gt; group/slot through char-indexed arrays, (group, slot) -&gt;
 * symbol through an enum map. Immutable after class init. Headless — no LibGDX.
 */
public final class WallSlotTable {

    private static final int ASCII_SIZE = 128;
    private static final WallGroup[] GROUP_BY_SYMBOL = new WallGroup[ASCII_SIZE];
    private static final WallSlot[]  SLOT_BY_SYMBOL  = new WallSlot[ASCII_SIZE];
    private static final Map<WallGroup, Map<WallSlot, Character>> SYMBOL_BY_SLOT = new EnumMap<>(WallGroup.class);
    private static final String SYMBOLS;

    static {
        StringBuilder symbols = new StringBuilder();
        for (String entry : TilesetConstants.WALL_SLOT_TABLE.split(";")) {
            char symbol = entry.charAt(0);
            String[] tokens = entry.substring(1).trim().split(" ");
            WallGroup group = WallGroup.valueOf(tokens[0]);
            WallSlot  slot  = WallSlot.valueOf(tokens[1]);
            if (symbol >= ASCII_SIZE || GROUP_BY_SYMBOL[symbol] != null) {
                throw new IllegalStateException("WALL_SLOT_TABLE: bad or duplicate symbol '" + symbol + "'");
            }
            Map<WallSlot, Character> slots = SYMBOL_BY_SLOT.computeIfAbsent(group, unused -> new EnumMap<>(WallSlot.class));
            if (slots.put(slot, symbol) != null) {
                throw new IllegalStateException("WALL_SLOT_TABLE: two symbols for " + group + " " + slot);
            }
            GROUP_BY_SYMBOL[symbol] = group;
            SLOT_BY_SYMBOL[symbol]  = slot;
            symbols.append(symbol);
        }
        SYMBOLS = symbols.toString();
    }

    private WallSlotTable() {}

    /** Every wall symbol, in table order. */
    public static String symbols() {
        return SYMBOLS;
    }

    public static boolean isWallSymbol(char symbol) {
        return symbol < ASCII_SIZE && GROUP_BY_SYMBOL[symbol] != null;
    }

    /** The symbol's group, or null when it is not a wall symbol. */
    public static WallGroup groupOf(char symbol) {
        return symbol < ASCII_SIZE ? GROUP_BY_SYMBOL[symbol] : null;
    }

    /** The symbol's slot, or null when it is not a wall symbol. */
    public static WallSlot slotOf(char symbol) {
        return symbol < ASCII_SIZE ? SLOT_BY_SYMBOL[symbol] : null;
    }

    /**
     * The symbol that names (group, slot), following {@link WallSlot#fallbackSlot()} when the table has
     * no symbol for it (e.g. MEDICAL STRUCTURE -&gt; MEDICAL BASE, HALL ACCENT -&gt; HALL BASE).
     */
    public static char symbolFor(WallGroup group, WallSlot slot) {
        WallGroup currentGroup = group;
        WallSlot  currentSlot  = slot;
        while (true) {
            Map<WallSlot, Character> slots = SYMBOL_BY_SLOT.get(currentGroup);
            Character symbol = slots != null ? slots.get(currentSlot) : null;
            if (symbol != null) return symbol;
            if (currentSlot == WallSlot.BASE) {
                currentGroup = WallGroup.HALL;
            } else {
                currentSlot = WallSlot.BASE;
            }
        }
    }
}
