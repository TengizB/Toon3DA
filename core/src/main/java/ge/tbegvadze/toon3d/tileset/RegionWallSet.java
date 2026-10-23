package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.util.TilesetConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One region's wall set (generated-wall-art order 2; guide section 6): the sprite ids of every wall face
 * per (group, slot) — BASE carries its variants in order — the four doors, the two columns, the floor and
 * ceiling surfaces, and the region's breach growth (rule P7). Immutable.
 *
 * <p>The ids follow the STABLE convention of guide section 12 (wall_&lt;letter&gt;_&lt;group&gt;_&lt;slot&gt;[_&lt;variant&gt;],
 * door_&lt;letter&gt;_&lt;kind&gt;, column_&lt;letter&gt;_&lt;kind&gt;), so a set row only names its id and letter; the
 * render-side atlas cross-checks every id against the shipped manifest when it loads (rule R7: a manifest
 * that lacks one falls back to set A).
 *
 * <p>An ALIAS row ({@link #alias}) borrows another set wholesale — how regions B-E use set A until their
 * own sets ship (decision D8); {@link RegionWallSetRegistry#resolve} follows it. Headless — no LibGDX.
 */
public final class RegionWallSet {

    private final String id;
    private final char   letter;
    private final String borrowsId;
    private final float  breachBase;
    private final float  breachStep;
    private final Map<WallGroup, Map<WallSlot, List<String>>> spriteIds;
    private final List<String> doorIds;
    private final List<String> columnIds;

    private RegionWallSet(String id, char letter, String borrowsId, float breachBase, float breachStep) {
        this.id         = Objects.requireNonNull(id, "id");
        this.letter     = letter;
        this.borrowsId  = borrowsId;
        this.breachBase = breachBase;
        this.breachStep = breachStep;
        Map<WallGroup, Map<WallSlot, List<String>>> walls = new EnumMap<>(WallGroup.class);
        List<String> doors   = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        if (borrowsId == null) {
            for (WallGroup group : WallGroup.values()) {
                Map<WallSlot, List<String>> slots = new EnumMap<>(WallSlot.class);
                for (WallSlot slot : group.slots()) {
                    List<String> variants = new ArrayList<>();
                    // The HALL BASE (the default wall of most floors and rooms) wears the MEDICAL base faces.
                    WallGroup faceGroup = (group == WallGroup.HALL && slot == WallSlot.BASE) ? WallGroup.MEDICAL : group;
                    String stem = "wall_" + letter + "_" + faceGroup.idToken() + "_" + slot.idToken();
                    if (slot == WallSlot.BASE) {
                        for (int variant = 0; variant < faceGroup.baseVariantCount(); variant++) {
                            variants.add(stem + "_" + variant);
                        }
                    } else {
                        variants.add(stem);
                    }
                    slots.put(slot, Collections.unmodifiableList(variants));
                }
                walls.put(group, Collections.unmodifiableMap(slots));
            }
            for (String token : TilesetConstants.WALL_SET_DOOR_KIND_TOKENS.split(",")) {
                doors.add("door_" + letter + "_" + token.trim());
            }
            for (String token : TilesetConstants.WALL_SET_COLUMN_KIND_TOKENS.split(",")) {
                columns.add("column_" + letter + "_" + token.trim());
            }
        }
        this.spriteIds = Collections.unmodifiableMap(walls);
        this.doorIds   = Collections.unmodifiableList(doors);
        this.columnIds = Collections.unmodifiableList(columns);
    }

    /** A real set whose files ship as {@code <id>.*} with ids built from {@code letter}. */
    public static RegionWallSet of(String id, char letter, float breachBase, float breachStep) {
        return new RegionWallSet(id, letter, null, breachBase, breachStep);
    }

    /** An alias row: {@code id} borrows {@code borrowsId} wholesale ("borrows": "outer_facility"). */
    public static RegionWallSet alias(String id, String borrowsId) {
        return new RegionWallSet(id, '\0', Objects.requireNonNull(borrowsId, "borrowsId"), 0f, 0f);
    }

    public String id() {
        return id;
    }

    public char letter() {
        return letter;
    }

    public boolean isAlias() {
        return borrowsId != null;
    }

    /** The set this alias borrows, or null for a real set. */
    public String borrowsId() {
        return borrowsId;
    }

    /** P7: hall breach density on the region's first floor. */
    public float breachBase() {
        return breachBase;
    }

    /** P7: hall breach density added per floor into the region. */
    public float breachStep() {
        return breachStep;
    }

    /**
     * The sprite ids for (group, slot), BASE variants in order, following the R3 fallback when this set
     * lacks the slot: ACCENT / BREACH -&gt; BASE of the same group, anything else -&gt; HALL BASE. Never empty
     * for a real set (HALL BASE always exists).
     */
    public List<String> resolve(WallGroup group, WallSlot slot) {
        if (isAlias()) throw new IllegalStateException("resolve the alias " + id + " through the registry first");
        WallGroup currentGroup = group;
        WallSlot  currentSlot  = slot;
        while (true) {
            Map<WallSlot, List<String>> slots = spriteIds.get(currentGroup);
            List<String> ids = slots != null ? slots.get(currentSlot) : null;
            if (ids != null && !ids.isEmpty()) return ids;
            if (currentSlot == WallSlot.BASE) {
                if (currentGroup == WallGroup.HALL) {
                    throw new IllegalStateException("region wall set " + id + " has no HALL BASE");
                }
                currentGroup = WallGroup.HALL;
            } else {
                if (!currentSlot.fallbackKeepsGroup()) currentGroup = WallGroup.HALL;
                currentSlot = currentSlot.fallbackSlot();
            }
        }
    }

    /** Every wall face id of the set (manifest order: HALL first, then the families), for the atlas check. */
    public List<String> allWallIds() {
        List<String> all = new ArrayList<>();
        for (Map<WallSlot, List<String>> slots : spriteIds.values()) {
            for (List<String> ids : slots.values()) all.addAll(ids);
        }
        return all;
    }

    /** door_&lt;letter&gt;_plain / red / yellow / blue, in {@link TilesetConstants#WALL_SET_DOOR_SYMBOLS} order. */
    public List<String> doorIds() {
        return doorIds;
    }

    /** The door id for a door symbol (d R Y B); a non-door symbol gets the plain door. */
    public String doorIdFor(char doorSymbol) {
        int index = TilesetConstants.WALL_SET_DOOR_SYMBOLS.indexOf(doorSymbol);
        return doorIds.get(Math.max(0, index));
    }

    /** column_&lt;letter&gt;_round / square. */
    public List<String> columnIds() {
        return columnIds;
    }

    /** Shipped file name of the manifest under {@link TilesetConstants#WALL_SET_ASSET_DIRECTORY}. */
    public String manifestFileName() {
        return id + ".walls.json";
    }

    public String floorFileName() {
        return id + ".floor.png";
    }

    public String ceilingFileName() {
        return id + ".ceiling.png";
    }
}
