package ge.tbegvadze.toon3d.tileset;

/**
 * The role a wall face plays inside its group (generated-wall-art order 2; guide section 6.2). A wall
 * symbol names a (group, slot) pair through {@link WallSlotTable}; the floor's {@link RegionWallSet}
 * names the picture. Per-constant data, never a switch:
 * <ul>
 *   <li>{@code idToken} — the slot's part of the stable sprite id (wall_&lt;letter&gt;_&lt;group&gt;_&lt;slot&gt;);</li>
 *   <li>{@code focal} — a focal wall is spaced by placement rule P9 (never adjacent, &gt;= 2 apart);</li>
 *   <li>{@code fallbackSlot} / {@code fallbackKeepsGroup} — rule R3: a slot a set lacks falls back to
 *       ACCENT -&gt; BASE and BREACH -&gt; BASE in the same group, every other slot to HALL BASE. BASE itself
 *       has no fallback slot; a missing room BASE falls back to HALL BASE.</li>
 * </ul>
 * Headless — no LibGDX.
 */
public enum WallSlot {
    BASE     ("base",      false, null, false),
    STRUCTURE("structure", false, BASE, false),
    LIGHT    ("light",     true,  BASE, false),
    SERVICE  ("service",   false, BASE, false),
    VENT     ("vent",      false, BASE, false),
    MARKER   ("marker",    true,  BASE, false),
    BREACH   ("breach",    false, BASE, true),
    ACCENT   ("accent",    true,  BASE, true);

    private final String   idToken;
    private final boolean  focal;
    private final WallSlot fallbackSlot;
    private final boolean  fallbackKeepsGroup;

    WallSlot(String idToken, boolean focal, WallSlot fallbackSlot, boolean fallbackKeepsGroup) {
        this.idToken            = idToken;
        this.focal              = focal;
        this.fallbackSlot       = fallbackSlot;
        this.fallbackKeepsGroup = fallbackKeepsGroup;
    }

    public String idToken() {
        return idToken;
    }

    public boolean isFocal() {
        return focal;
    }

    /** The slot to try when a set lacks this one; null for BASE (the end of the chain). */
    public WallSlot fallbackSlot() {
        return fallbackSlot;
    }

    /** True when the fallback stays in the same group (ACCENT, BREACH); false = HALL. */
    public boolean fallbackKeepsGroup() {
        return fallbackKeepsGroup;
    }
}
