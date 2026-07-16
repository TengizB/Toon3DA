package ge.tbegvadze.toon3d.level;

/**
 * The two tiers of {@link RoomBlueprint} (symbol/sprite-reuse order-5).
 *
 * <ul>
 *   <li>{@link #SIGNATURE} — a room with a FIXED identity: its architecture AND its look are part of
 *       who it is (MEDICAL_BAY, ARMORY, CRYO_CHAMBER, SERVER_ROOM, POWER_PLANT, COMMAND_CENTER,
 *       CONTAINMENT_BLOCK, RESEARCH_LAB, STELLAR_OBSERVATORY). A signature room CLAIMS a specific set
 *       of sprites when placed (its {@link RoomBlueprint#symbolDemand()}), so it always reads the
 *       same. Absent signature rooms free their claimed symbols for other rooms and generic variety.</li>
 *   <li>{@link #GENERIC} — a variety-filler room whose look is allocator-driven, not fixed
 *       (ENTRANCE, STANDARD, LARGE). It declares no required sprites; the {@code SymbolAllocator}
 *       (order-4) paints its walls/props with per-level variety.</li>
 * </ul>
 *
 * <p>Mirrors the SIGNATURE-vs-GENERIC distinction the master series design draws between
 * fixed-identity signature rooms and allocator-driven filler. Headless — no LibGDX.
 */
public enum RoomKind {
    SIGNATURE,
    GENERIC
}
