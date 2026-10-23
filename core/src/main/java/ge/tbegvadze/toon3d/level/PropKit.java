package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.LevelPalettes;
import ge.tbegvadze.toon3d.tileset.SymbolBudget;
import ge.tbegvadze.toon3d.tileset.TilesetRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A PROP KIT (level-generation-overhaul R16): the ordered rows a {@link RoomDresser} places a room's furniture
 * from. Row 1 of a special room's kit is its SIGNATURE: always present and placed before anything else. A
 * DENSE kit (server racks, a salvage heap) makes its room TIGHT: a 0.40 fill cap and a lane band of half-width
 * 0, at most one per floor. Kits use only existing symbols; every symbol renders its legacy sprite, which the
 * kit pins in the floor's palette (R29). Immutable. Headless — no LibGDX.
 */
public final class PropKit {

    /** The regular-room kits (R23) and the bespoke dressings, by id. */
    public static final String ID_OFFICE      = "office";
    public static final String ID_STOREROOM   = "storeroom";
    public static final String ID_MAINTENANCE = "maintenance";
    public static final String ID_AFTERMATH   = "aftermath";
    public static final String ID_BARE        = "bare";

    private final String           id;
    private final List<PropKitRow> rows;
    private final boolean          dense;
    private final boolean          hasSignature;

    private PropKit(String id, List<PropKitRow> rows, boolean dense, boolean hasSignature) {
        this.id           = id;
        this.rows         = Collections.unmodifiableList(new ArrayList<>(rows));
        this.dense        = dense;
        this.hasSignature = hasSignature;
    }

    /** Starts a kit whose first row is its SIGNATURE (a special room's kit). */
    public static Builder signature(String id) {
        return new Builder(id, true);
    }

    /** Starts a kit with no signature (a regular-room or bespoke dressing). */
    public static Builder plain(String id) {
        return new Builder(id, false);
    }

    public String           id()    { return id; }
    public List<PropKitRow> rows()  { return rows; }
    public boolean          dense() { return dense; }

    /** The signature row, or null for a kit without one. */
    public PropKitRow signatureRow() {
        return hasSignature && !rows.isEmpty() ? rows.get(0) : null;
    }

    /** The signature symbol, or null for a kit without one. */
    public Character signatureSymbol() {
        PropKitRow signature = signatureRow();
        return signature == null ? null : signature.primarySymbol();
    }

    /** Whether any row of the kit can place a terminal 'T' (R20). */
    public boolean placesTerminals() {
        for (PropKitRow row : rows) {
            for (char symbol : row.symbols) if (symbol == 'T') return true;
        }
        return false;
    }

    /**
     * Every FLEXIBLE symbol the kit can place, mapped to the sprite it must render (its legacy sprite; R29). A
     * fixed symbol ('P', '@') always renders its own picture and needs no pin.
     */
    public Map<Character, String> spriteDemand() {
        TilesetRegistries.bootstrap();
        Map<Character, String> demand = new LinkedHashMap<>();
        for (PropKitRow row : rows) {
            for (char symbol : row.symbols) {
                if (!SymbolBudget.standard().isFlexible(symbol)) continue;
                String sprite = LevelPalettes.legacy().spriteIdOf(symbol);
                if (sprite != null) demand.put(symbol, sprite);
            }
        }
        return demand;
    }

    /** Fluent kit builder. */
    public static final class Builder {
        private final String           id;
        private final boolean          hasSignature;
        private final List<PropKitRow> rows = new ArrayList<>();
        private boolean                dense;

        private Builder(String id, boolean hasSignature) {
            this.id           = id;
            this.hasSignature = hasSignature;
        }

        public Builder row(PropKitRow row) {
            rows.add(row);
            return this;
        }

        /** Marks the kit DENSE (a TIGHT room, R21). */
        public Builder dense() {
            this.dense = true;
            return this;
        }

        public PropKit build() {
            if (hasSignature) {
                if (rows.isEmpty()) throw new IllegalStateException("signature kit " + id + " has no rows");
                PropKitRow signature = rows.get(0);
                if (signature.chance < 1f || signature.minCount < 1) {
                    throw new IllegalStateException("signature row of " + id + " must be certain (chance 1, count >= 1)");
                }
            }
            return new PropKit(id, rows, dense, hasSignature);
        }
    }
}
