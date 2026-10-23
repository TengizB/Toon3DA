package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.RoomSymbolDemand;
import ge.tbegvadze.toon3d.tileset.WallGroup;

/**
 * A registered, self-describing room (the ROOM extensibility layer; level-generation-overhaul contract). A blueprint
 * says what a room IS and how it is FURNISHED, and the generators read nothing else about it (R32 — never a switch
 * over room types):
 * <ul>
 *   <li>{@link #id()} / {@link #kind()} — stable identity + SIGNATURE vs GENERIC tier.</li>
 *   <li>{@link #supplyStance()} — its declared relationship to floor supply (K12 / C-ROOM).</li>
 *   <li>{@link #wallGroup()} — the wall family the LevelFinisher dresses its shell with.</li>
 *   <li>{@link #symbolDemand()} — the sprites it claims: its architecture's plus its kit's (R29).</li>
 *   <li>{@link #build(RoomBuildContext)} — its ARCHITECTURE only (floor lighting, rotunda, alcove, pillar slots).</li>
 *   <li>{@link #sizeWindow()}, {@link #kit()}, {@link #relaxedKit()}, {@link #regularKits()},
 *       {@link #signatureSymbol()} — its size window and prop kit (R4, R16).</li>
 * </ul>
 * Every blueprint but the entrance and the standard room is a SPECIAL room in the equal-chance pool
 * ({@link SpecialRoomPlanner}): no eligibility gate, no weight, no depth input. Headless — no LibGDX.
 */
public interface RoomBlueprint {

    /**
     * Stable string id (e.g. {@code "medical_bay"}, {@code "cryo_chamber"}, {@code "standard"}). Used as
     * the registry key and MUST NOT be renamed once shipped (seed/reproducibility contract).
     */
    String id();

    /** SIGNATURE (fixed identity + look) or GENERIC (allocator-driven filler). */
    RoomKind kind();

    /** The room's declared relationship to floor supply (K12 / C-ROOM) — required, no default (E3). */
    RoomSupplyStance supplyStance();

    /**
     * The wall family this room is built from (generated-wall-art order 2, rule P2): the LevelFinisher
     * stamps its region set's room BASE on the perimeter and its ACCENT on 1-3 tiles. HALL = no room set.
     */
    WallGroup wallGroup();

    /**
     * The sprite ids this room CLAIMS when placed: its architecture's symbols plus every flexible symbol its kit can
     * stamp (R29). The {@code SymbolAllocator} reserves them so per-level variety cannot repaint the room.
     */
    RoomSymbolDemand symbolDemand();

    /**
     * Stamps this room's ARCHITECTURE (floor lighting, the observatory rotunda, the lab's alcove wall, pillar slots)
     * through {@code context} and exports its FEATURE slots. Never places a prop — the dresser does, from the kit.
     */
    void build(RoomBuildContext context);

    // --- level-generation-overhaul: what a room IS (its size window) and how it is FURNISHED (its kit) ---

    /** The interior size window a room of this blueprint is stamped within (R4); null = the regular window. */
    default RoomSizeWindow sizeWindow() {
        return null;
    }

    /** The prop kit the room is dressed from (R16; row 1 = SIGNATURE); null = no kit (entrance). */
    default PropKit kit() {
        return null;
    }

    /** A DENSE kit's RELAXED variant, used when the floor's tight-room budget is spent (R21); null = none. */
    default PropKit relaxedKit() {
        return null;
    }

    /** The regular-room kits a REGULAR room draws one of uniformly (R23); empty for every other blueprint. */
    default java.util.List<PropKit> regularKits() {
        return java.util.Collections.emptyList();
    }

    /** The symbol that must be on a placed room of this blueprint (its kit's signature), or null. */
    default Character signatureSymbol() {
        PropKit roomKit = kit();
        return roomKit == null ? null : roomKit.signatureSymbol();
    }
}
